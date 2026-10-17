"""1 本の確定足について反発確率を出し、エントリー・利確・損切りの目安を付ける。"""

from __future__ import annotations

import sqlite3
from dataclasses import asdict, dataclass

import pandas as pd

from app.config import Settings, timeframe_millis
from app.db import queries
from app.features.builder import build_features
from app.models.pipeline import WARMUP_BARS
from app.models.registry import ModelBundle


class BarNotFound(LookupError):
    """指定の足（または指標）が DB にまだない。"""


@dataclass
class Analysis:
    probability: float
    signal: str
    entry_price: float
    take_profit: float
    stop_loss: float
    risk_reward: float | None
    confidence: str
    model_version: str

    def to_dict(self) -> dict:
        return asdict(self)


def decide(probability: float, close: float, atr: float | None, s: Settings, version: str) -> Analysis:
    """確率と足の値から BUY / HOLD・利確・損切り・信頼度を決める。

    利確 = 教師ラベルと同じ +label_target_pct%、損切り = ATR(14) × stop_atr_mult 下。
    """
    take_profit = close * (1 + s.label_target_pct / 100)
    stop_loss = close - s.stop_atr_mult * atr if atr is not None and atr > 0 else close * 0.99
    risk = close - stop_loss
    rr = (take_profit - close) / risk if risk > 0 else None
    buy = probability >= s.probability_threshold
    if buy and probability >= s.confidence_a:
        confidence = "A"
    elif buy:
        confidence = "B"
    else:
        confidence = "C"
    return Analysis(
        probability=round(float(probability), 4),
        signal="BUY" if buy else "HOLD",
        entry_price=close,
        take_profit=round(take_profit, 2),
        stop_loss=round(stop_loss, 2),
        risk_reward=None if rr is None else round(rr, 2),
        confidence=confidence,
        model_version=version,
    )


def predict_frame(bundle: ModelBundle, df: pd.DataFrame) -> pd.Series:
    """build_features() 済みの DataFrame の各行の反発確率。"""
    return pd.Series(bundle.model.predict_proba(df[bundle.features])[:, 1], index=df.index)


def analyze(conn: sqlite3.Connection, s: Settings, bundle: ModelBundle,
            symbol: str, timeframe: str, open_time: int) -> Analysis:
    step = timeframe_millis(timeframe)
    market = queries.load_market(conn, symbol, timeframe, open_time - WARMUP_BARS * step, open_time)
    if market.empty or int(market["open_time"].iloc[-1]) != open_time:
        raise BarNotFound(f"{symbol} {timeframe} open_time={open_time} の足が DB にありません")
    row = build_features(market, step).iloc[[-1]]
    if pd.isna(row["rsi14"].iloc[0]):
        raise BarNotFound(f"{symbol} {timeframe} open_time={open_time} の指標が未計算です")
    prob = float(predict_frame(bundle, row).iloc[0])
    atr = row["atr14"].iloc[0]
    return decide(prob, float(row["close"].iloc[0]), None if pd.isna(atr) else float(atr), s, bundle.version)
