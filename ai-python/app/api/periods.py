"""日付指定（YYYY-MM-DD, JST）→ epoch ms の期間。"""

from __future__ import annotations

import sqlite3

import pandas as pd

from app.config import Settings
from app.db import queries

DAY_MS = 86_400_000


def resolve_period(conn: sqlite3.Connection, s: Settings, from_date: str | None, to_date: str | None,
                   default_days: int) -> tuple[int, int]:
    """from_date 00:00 JST 〜 to_date 23:59 JST。省略した側は DB の範囲から決める。"""
    rng = queries.time_range(conn, s.symbol, s.timeframe)
    if rng is None:
        raise ValueError("DB に指標が計算済みの足がありません")
    to_ms = _jst_ms(to_date) + DAY_MS - 1 if to_date else rng[1]
    from_ms = _jst_ms(from_date) if from_date else to_ms - default_days * DAY_MS
    from_ms, to_ms = max(from_ms, rng[0]), min(to_ms, rng[1])
    if from_ms >= to_ms:
        raise ValueError("期間に足がありません")
    return from_ms, to_ms


def _jst_ms(date: str) -> int:
    return pd.Timestamp(date, tz="Asia/Tokyo").value // 1_000_000
