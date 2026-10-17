"""candles と features を結合して読む。"""

from __future__ import annotations

import sqlite3

import pandas as pd

_MARKET_SQL = """
SELECT c.open_time, c.open, c.high, c.low, c.close, c.volume,
       f.rsi14, f.macd, f.macd_signal, f.macd_hist, f.sma20, f.sma50, f.dev_sma20,
       f.vol_ma20, f.vol_ratio, f.atr14
FROM candles c
LEFT JOIN features f
  ON f.symbol = c.symbol AND f.timeframe = c.timeframe AND f.open_time = c.open_time
WHERE c.symbol = ? AND c.timeframe = ? AND c.open_time >= ? AND c.open_time <= ?
ORDER BY c.open_time
"""


def load_market(conn: sqlite3.Connection, symbol: str, timeframe: str,
                from_ms: int, to_ms: int) -> pd.DataFrame:
    """[from_ms, to_ms] の確定足と指標。open_time 昇順。"""
    return pd.read_sql_query(_MARKET_SQL, conn, params=(symbol, timeframe, from_ms, to_ms))


def time_range(conn: sqlite3.Connection, symbol: str, timeframe: str) -> tuple[int, int] | None:
    """指標が計算済みの足の最古・最新の open_time。"""
    row = conn.execute(
        "SELECT MIN(open_time), MAX(open_time) FROM features WHERE symbol = ? AND timeframe = ?",
        (symbol, timeframe),
    ).fetchone()
    return None if row[0] is None else (int(row[0]), int(row[1]))
