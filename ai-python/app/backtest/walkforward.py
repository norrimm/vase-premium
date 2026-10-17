"""ウォークフォワード・バックテスト（長期間の検証）。

検証期間を fold_days 日ずつの区間に分け、各区間の直前 train_days 日だけで学習したモデルでその区間を検証する
（定期的に再学習しながら運用した場合の再現）。1 つのモデルで数年分を通すと、古い相場で学習したモデルで
新しい相場を予測することになり、実運用と合わない。

利確幅（label_target_pct）・保有本数（label_horizon）は教師ラベルも変わるので組み合わせごとに学習し直す。
損切り幅（stop_atr_mult）と AI の閾値は同じモデルのまま振る。
"""

from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass, replace
from itertools import product

import pandas as pd

from app.backtest import engine
from app.backtest.metrics import summarize
from app.config import Settings, timeframe_millis
from app.models import pipeline

log = logging.getLogger(__name__)

DAY_MS = 86_400_000
# 組み合わせを表す列（集計のキー）。rule の行は閾値に依存しないので threshold が空
PARAM_COLS = ["target_pct", "horizon", "stop_mult", "strategy", "threshold"]


@dataclass
class WalkForwardResult:
    by_fold: pd.DataFrame
    total: pd.DataFrame
    trades: pd.DataFrame


def run(conn: sqlite3.Connection, s: Settings, to_ms: int, folds: int, fold_days: int,
        thresholds: list[float], stop_mults: list[float], targets: list[float],
        horizons: list[int]) -> WalkForwardResult:
    step = timeframe_millis(s.timeframe)
    trades, rows = [], []
    for k in range(folds):
        fold_to = to_ms - (folds - 1 - k) * fold_days * DAY_MS
        fold_from = fold_to - fold_days * DAY_MS + 1
        closes = pipeline.load_dataset(conn, s, fold_from, fold_to)["close"]
        if closes.empty:
            raise ValueError(f"区間 {k + 1} の足がありません")
        market_pct = (closes.iloc[-1] / closes.iloc[0] - 1) * 100
        for target, horizon in product(targets, horizons):
            st = replace(s, label_target_pct=target, label_horizon=horizon)
            train_to = fold_from - (horizon + 1) * step
            log.info("区間 %d/%d 利確 %.2f%% 保有 %d 本: 学習", k + 1, folds, target, horizon)
            bundle = pipeline.train(conn, st, train_to - st.train_days * DAY_MS, train_to)
            for mult, th in product(stop_mults, thresholds):
                r = engine.run(conn, replace(st, stop_atr_mult=mult), fold_from, fold_to,
                               bundle=bundle, threshold=th)
                params = {"fold": k + 1, "target_pct": target, "horizon": horizon, "stop_mult": mult}
                # rule は閾値に依存しないので最初の閾値のときだけ残す
                keep = (r.summary["strategy"] == engine.STRATEGY_RULE_AI) | (th == thresholds[0])
                for _, row in r.summary[keep].iterrows():
                    rows.append({**params, "market_change_pct": market_pct, "cv_auc": bundle.meta.get("cv_auc"),
                                 **row.to_dict()})
                if not r.trades.empty:
                    t = r.trades[r.trades["strategy"].isin(r.summary.loc[keep, "strategy"])]
                    trades.append(t.assign(**params, threshold=t["strategy"].map(
                        {engine.STRATEGY_RULE_AI: th}).astype(float)))

    by_fold = pd.DataFrame(rows)
    all_trades = pd.concat(trades, ignore_index=True) if trades else pd.DataFrame()
    return WalkForwardResult(by_fold=by_fold, total=_total(by_fold, all_trades), trades=all_trades)


def _total(by_fold: pd.DataFrame, trades: pd.DataFrame) -> pd.DataFrame:
    """全区間を通した成績。区間ごとの成績が何区間プラスだったかも付ける。"""
    rows = []
    for key, g in by_fold.groupby(PARAM_COLS, dropna=False, sort=False):
        params = dict(zip(PARAM_COLS, key))
        t = trades
        if not t.empty:
            mask = pd.Series(True, index=t.index)
            for c in PARAM_COLS:
                mask &= t[c].isna() if pd.isna(params[c]) else t[c] == params[c]
            t = t[mask].sort_values("open_time")
        returns = t["return_pct"] if not t.empty else pd.Series(dtype=float)
        rows.append({
            **params,
            **summarize(returns),
            "label_hit_rate": float(t["label"].mean()) if not t.empty and t["label"].notna().any() else None,
            "winning_folds": int((g["total_return_pct"].fillna(0) > 0).sum()),
            "folds": len(g),
        })
    return pd.DataFrame(rows)
