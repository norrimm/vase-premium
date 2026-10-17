"""テスト用の SQLite（core-java と同じスキーマ）と合成データ。"""

from __future__ import annotations

import sqlite3
from dataclasses import replace
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from app.config import Settings

REPO = Path(__file__).resolve().parents[2]
SCHEMA = REPO / "core-java" / "src" / "main" / "resources" / "schema.sql"
STEP = 300_000
T0 = 1_759_276_800_000  # 2025-10-01 00:00 UTC


def make_candles(n: int, seed: int = 0) -> pd.DataFrame:
    """ランダムウォークの 5 分足。"""
    rng = np.random.default_rng(seed)
    close = 60_000 * np.exp(np.cumsum(rng.normal(0, 0.002, n)))
    open_ = np.concatenate([[close[0]], close[:-1]])
    spread = np.abs(rng.normal(0, 0.0015, n)) * close
    return pd.DataFrame({
        "open_time": T0 + np.arange(n) * STEP,
        "open": open_,
        "high": np.maximum(open_, close) + spread,
        "low": np.minimum(open_, close) - spread,
        "close": close,
        "volume": rng.gamma(2.0, 50.0, n),
    })


def add_indicators(c: pd.DataFrame) -> pd.DataFrame:
    """core-java の IndicatorCalculator と同じ定義の指標（テスト用の簡易実装）。"""
    close, vol = c["close"], c["volume"]
    delta = close.diff()
    gain = delta.clip(lower=0).ewm(alpha=1 / 14, adjust=False).mean()
    loss = (-delta.clip(upper=0)).ewm(alpha=1 / 14, adjust=False).mean()
    f = pd.DataFrame({"open_time": c["open_time"]})
    f["rsi14"] = 100 - 100 / (1 + gain / loss)
    ema12, ema26 = close.ewm(span=12, adjust=False).mean(), close.ewm(span=26, adjust=False).mean()
    f["macd"] = ema12 - ema26
    f["macd_signal"] = f["macd"].ewm(span=9, adjust=False).mean()
    f["macd_hist"] = f["macd"] - f["macd_signal"]
    f["sma20"] = close.rolling(20).mean()
    f["sma50"] = close.rolling(50).mean()
    f["dev_sma20"] = (close / f["sma20"] - 1) * 100
    f["vol_ma20"] = vol.rolling(20).mean()
    f["vol_ratio"] = vol / f["vol_ma20"]
    tr = pd.concat([c["high"] - c["low"], (c["high"] - close.shift()).abs(),
                    (c["low"] - close.shift()).abs()], axis=1).max(axis=1)
    f["atr14"] = tr.ewm(alpha=1 / 14, adjust=False).mean()
    f.iloc[:50, 1:] = np.nan
    return f


def write_db(path: Path, candles: pd.DataFrame) -> None:
    conn = sqlite3.connect(path)
    conn.executescript(SCHEMA.read_text(encoding="utf-8"))
    c = candles.assign(symbol="BTCUSDT", timeframe="5m")
    c.to_sql("candles", conn, if_exists="append", index=False)
    add_indicators(candles).assign(symbol="BTCUSDT", timeframe="5m").to_sql(
        "features", conn, if_exists="append", index=False)
    conn.commit()
    conn.close()


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    db = tmp_path / "trade.db"
    write_db(db, make_candles(6000))
    rules = tmp_path / "rules.yml"
    rules.write_text(
        "rules:\n  buy-dip:\n    rsi-below: 40\n    require-close-above-sma50: false\n"
        "    sma20-deviation-at-most: -0.3\n    volume-ratio-above: 1.0\n", encoding="utf-8")
    return replace(Settings.load(), db_path=db, artifacts_dir=tmp_path / "artifacts",
                   reports_dir=tmp_path / "reports", rules_path=rules, model_version="",
                   symbol="BTCUSDT", timeframe="5m", train_days=365, probability_threshold=0.5)
