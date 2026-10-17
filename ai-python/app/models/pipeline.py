"""DB から学習データを作って学習・保存するまで（/train と scripts/train.py の共通部分）。"""

from __future__ import annotations

import logging
import sqlite3
from datetime import datetime, timezone

import pandas as pd

from app.config import Settings, timeframe_millis
from app.db import queries
from app.features.builder import build_features, feature_columns
from app.features.labeling import make_labels
from app.models import lgbm, registry
from app.models.registry import ModelBundle

log = logging.getLogger(__name__)

# 指標（SMA50 / MACD）とラグが出そろうまでの本数。これより前の足は読むだけで学習には使わない
WARMUP_BARS = 100


def load_dataset(conn: sqlite3.Connection, s: Settings, from_ms: int, to_ms: int) -> pd.DataFrame:
    """[from_ms, to_ms] の足に特徴量とラベルを付ける。ラベルが決まらない足（末尾など）は label が NaN。"""
    step = timeframe_millis(s.timeframe)
    market = queries.load_market(conn, s.symbol, s.timeframe, from_ms - WARMUP_BARS * step,
                                 to_ms + s.label_horizon * step)
    if market.empty:
        return market
    df = build_features(market, step)
    df["label"] = make_labels(df, s.label_horizon, s.label_target_pct, step)
    return df[(df["open_time"] >= from_ms) & (df["open_time"] <= to_ms)].reset_index(drop=True)


def train_and_save(conn: sqlite3.Connection, s: Settings, from_ms: int, to_ms: int) -> ModelBundle:
    """[from_ms, to_ms] の足で学習して artifacts に保存する（推論に使うモデルになる）。"""
    bundle = train(conn, s, from_ms, to_ms)
    return registry.save(s.artifacts_dir, bundle.model, bundle.meta)


def train(conn: sqlite3.Connection, s: Settings, from_ms: int, to_ms: int) -> ModelBundle:
    """[from_ms, to_ms] の足で学習する（保存はしない）。ラベルは to_ms より先の値動きも使う点に注意
    （バックテストでは to_ms を検証期間の horizon 本手前までにすること）。"""
    df = load_dataset(conn, s, from_ms, to_ms)
    cols = feature_columns()
    df = df[df["label"].notna() & df["rsi14"].notna() & df["sma50"].notna()]
    if len(df) < 1000:
        raise ValueError(f"学習に使える足が {len(df)} 本しかありません（1000 本以上必要）")

    log.info("学習開始: %d 本 (%s 〜 %s)", len(df), _iso(df["open_time"].iloc[0]), _iso(df["open_time"].iloc[-1]))
    result = lgbm.train(df[cols], df["label"].astype(int), gap=s.label_horizon)
    summary = result.summary()
    log.info("学習完了: CV AUC=%s 正例率=%.3f", _fmt(summary["cv_auc"]), summary["positive_rate"])

    meta = {
        "symbol": s.symbol,
        "timeframe": s.timeframe,
        "features": cols,
        "label": {"horizon": s.label_horizon, "target_pct": s.label_target_pct},
        "train_from": _iso(df["open_time"].iloc[0]),
        "train_to": _iso(df["open_time"].iloc[-1]),
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "params": lgbm.PARAMS,
        **summary,
    }
    version = f"bt_until_{_iso(df['open_time'].iloc[-1])[:10].replace('-', '')}"
    return ModelBundle(version, result.model, {**meta, "model_version": version})


def _iso(ms: int) -> str:
    return datetime.fromtimestamp(int(ms) / 1000, timezone.utc).isoformat(timespec="minutes")


def _fmt(v: float | None) -> str:
    return "-" if v is None else f"{v:.3f}"
