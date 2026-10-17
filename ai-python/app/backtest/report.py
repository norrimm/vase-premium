"""バックテスト結果を CSV に書き出す。"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from app.backtest.engine import BacktestResult


def write_csv(result: BacktestResult, reports_dir: Path, now: datetime | None = None) -> dict[str, Path]:
    """reports/backtest_YYYYMMDD_HHMMSS_{summary,trades}.csv を書いてパスを返す。
    Excel で文字化けしないよう BOM 付き UTF-8 で書く。"""
    reports_dir.mkdir(parents=True, exist_ok=True)
    stamp = (now or datetime.now()).strftime("%Y%m%d_%H%M%S")
    paths = {
        "summary": reports_dir / f"backtest_{stamp}_summary.csv",
        "trades": reports_dir / f"backtest_{stamp}_trades.csv",
    }
    result.summary.to_csv(paths["summary"], index=False, encoding="utf-8-sig")
    result.trades.to_csv(paths["trades"], index=False, encoding="utf-8-sig")
    return paths
