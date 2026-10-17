"""過去データでルール + AI の通知を再現し、通知どおりに売買したときの成績を出す（F-09）。

売買の前提（ライブの通知内容と同じ）
- エントリー: シグナル足の終値
- 利確: +label_target_pct%（教師ラベルと同じ）
- 損切り: ATR(14) × stop_atr_mult 下
- 時間切れ: label_horizon 本後の終値で手仕舞い
- 同じ足で利確と損切りの両方に届いていたら、損切りが先だったとみなす（保守的に）
- 手数料: 片道 fee_pct% を往復分差し引く
- 通知の重複抑止（cooldown_minutes）も再現する。抑止されたシグナルは売買しない

AI のモデルは、指定がなければ検証期間より前のデータだけで学習し直す（未来の情報を使わないため）。
"""

from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass, replace

import numpy as np
import pandas as pd

from app.backtest.metrics import summarize
from app.backtest.rules import BuyDipRule
from app.config import Settings, timeframe_millis
from app.models import pipeline, registry
from app.models.predictor import decide, predict_frame
from app.models.registry import ModelBundle

log = logging.getLogger(__name__)

STRATEGY_RULE = "rule"
STRATEGY_RULE_AI = "rule+ai"


@dataclass
class BacktestResult:
    trades: pd.DataFrame
    summary: pd.DataFrame
    model_version: str
    model_meta: dict


def simulate_trade(df: pd.DataFrame, i: int, take_profit: float, stop_loss: float,
                   horizon: int) -> tuple[float, str, int]:
    """i 行目の終値で買い、(手仕舞い価格, 理由, 手仕舞いした行) を返す。df は欠損のない連続した足。"""
    last = min(i + horizon, len(df) - 1)
    for j in range(i + 1, last + 1):
        if df["low"].iat[j] <= stop_loss:
            return stop_loss, "stop", j
        if df["high"].iat[j] >= take_profit:
            return take_profit, "take_profit", j
    return float(df["close"].iat[last]), "timeout", last


def run(conn: sqlite3.Connection, s: Settings, from_ms: int, to_ms: int,
        bundle: ModelBundle | None = None, threshold: float | None = None) -> BacktestResult:
    step = timeframe_millis(s.timeframe)
    if threshold is not None:
        s = _with_threshold(s, threshold)

    if bundle is None:
        # 検証期間の先頭の horizon 本手前までで学習（ラベルが検証期間の値動きを見ないように）
        train_to = from_ms - (s.label_horizon + 1) * step
        train_from = train_to - s.train_days * 86_400_000
        log.info("バックテスト用にモデルを学習します（検証期間より前のデータのみ）")
        # 推論用のモデルと混ざらないよう artifacts には保存しない
        bundle = pipeline.train(conn, s, train_from, train_to)
    elif _overlaps(bundle, from_ms):
        log.warning("モデル %s の学習期間が検証期間と重なっています。成績は実際より良く出ます", bundle.version)

    df = pipeline.load_dataset(conn, s, from_ms, to_ms + s.label_horizon * step)
    if df.empty:
        raise ValueError("指定期間の足がありません")
    rule = BuyDipRule.load(s.rules_path)
    in_period = df["open_time"] <= to_ms
    df["rule_hit"] = rule.hits(df) & in_period
    df["probability"] = np.nan
    hit_idx = df.index[df["rule_hit"]]
    if len(hit_idx) > 0:
        df.loc[hit_idx, "probability"] = predict_frame(bundle, df.loc[hit_idx]).values

    rows = []
    for strategy in (STRATEGY_RULE, STRATEGY_RULE_AI):
        last_sent = None
        for i in hit_idx:
            prob = float(df.at[i, "probability"])
            a = decide(prob, float(df.at[i, "close"]), _nan_to_none(df.at[i, "atr14"]), s, bundle.version)
            if strategy == STRATEGY_RULE_AI and a.signal != "BUY":
                continue
            t = int(df.at[i, "open_time"])
            if last_sent is not None and t - last_sent < s.cooldown_minutes * 60_000:
                continue
            last_sent = t
            exit_price, reason, j = simulate_trade(df, i, a.take_profit, a.stop_loss, s.label_horizon)
            gross = (exit_price / a.entry_price - 1) * 100
            rows.append({
                "strategy": strategy,
                "open_time": t,
                "signal_time": _iso(t),
                "probability": a.probability,
                "confidence": a.confidence,
                "entry_price": a.entry_price,
                "take_profit": a.take_profit,
                "stop_loss": a.stop_loss,
                "exit_price": exit_price,
                "exit_reason": reason,
                "exit_time": _iso(int(df.at[j, "open_time"]) + step),
                "bars_held": j - i,
                "label": _nan_to_none(df.at[i, "label"]),
                "gross_return_pct": gross,
                "return_pct": gross - 2 * s.fee_pct,
            })
    trades = pd.DataFrame(rows)

    summary_rows = []
    for strategy in (STRATEGY_RULE, STRATEGY_RULE_AI):
        t = trades[trades["strategy"] == strategy] if not trades.empty else trades
        m = summarize(t["return_pct"] if not t.empty else pd.Series(dtype=float))
        summary_rows.append({
            "strategy": strategy,
            "period_from": _iso(from_ms),
            "period_to": _iso(to_ms),
            **m,
            # 教師ラベルどおりに +target% に届いた割合（= 通知の「当たり」率）
            "label_hit_rate": float(t["label"].mean()) if not t.empty and t["label"].notna().any() else None,
            "rule_hits": int(len(hit_idx)),
            "threshold": s.probability_threshold if strategy == STRATEGY_RULE_AI else None,
            "fee_pct_per_side": s.fee_pct,
            "model_version": bundle.version,
        })
    return BacktestResult(trades=trades, summary=pd.DataFrame(summary_rows), model_version=bundle.version,
                          model_meta=bundle.meta)


def load_model(s: Settings, version: str) -> ModelBundle:
    bundle = registry.load(s.artifacts_dir, version)
    if bundle is None:
        raise FileNotFoundError("学習済みモデルがありません")
    return bundle


def _overlaps(bundle: ModelBundle, from_ms: int) -> bool:
    train_to = bundle.meta.get("train_to")
    return train_to is not None and pd.Timestamp(train_to).value // 1_000_000 >= from_ms


def _with_threshold(s: Settings, threshold: float) -> Settings:
    return replace(s, probability_threshold=threshold)


def _nan_to_none(v):
    return None if v is None or pd.isna(v) else float(v)


def _iso(ms: int) -> str:
    return pd.Timestamp(ms, unit="ms", tz="UTC").tz_convert("Asia/Tokyo").strftime("%Y-%m-%d %H:%M")
