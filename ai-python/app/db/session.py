"""SQLite への接続。書き込みは core-java の担当なので、こちらは読み取り専用で開く。"""

from __future__ import annotations

import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path


@contextmanager
def connect(db_path: Path) -> Iterator[sqlite3.Connection]:
    """with connect(path) as conn: の形で使う。抜けると閉じる。"""
    if not db_path.is_file():
        raise FileNotFoundError(f"DB が見つかりません: {db_path}（core-java を先に起動して足を取得してください）")
    # mode=ro: core-java が WAL で書き込み中でも読める。こちらからは書かない
    conn = sqlite3.connect(f"{db_path.as_uri()}?mode=ro", uri=True, timeout=5)
    try:
        yield conn
    finally:
        conn.close()
