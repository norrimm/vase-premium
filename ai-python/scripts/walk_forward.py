"""ウォークフォワード・バックテスト。長期間を区間に分け、区間ごとに直前のデータだけで学習し直して検証する。

    python scripts/walk_forward.py                                  # DB の最新から 1 年 × 5 区間
    python scripts/walk_forward.py --to 2026-10-16 --folds 5 --fold-days 365
    python scripts/walk_forward.py --thresholds 0.5,0.6,0.7 --stop-mults 1.0,1.5,2.0 --targets 0.8,1.2

各区間の学習期間は直前 TRAIN_DAYS 日。DB には「最初の区間の開始 − TRAIN_DAYS 日」からの足が必要
（core-java の BACKFILL_DAYS を増やして起動すると過去にさかのぼって取得する）。
結果は reports/walkforward_*_{by_fold,total,trades}.csv。
"""

from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pandas as pd  # noqa: E402

from app.api.periods import resolve_period  # noqa: E402
from app.backtest import walkforward  # noqa: E402
from app.config import Settings  # noqa: E402
from app.db.session import connect  # noqa: E402


def _floats(v: str) -> list[float]:
    return [float(x) for x in v.split(",")]


def _ints(v: str) -> list[int]:
    return [int(x) for x in v.split(",")]


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--to", dest="to_date", help="最後の区間の終了日 YYYY-MM-DD（JST）。省略時は DB の最新")
    p.add_argument("--folds", type=int, default=5, help="区間の数（既定 5）")
    p.add_argument("--fold-days", type=int, default=365, help="1 区間の日数（既定 365）")
    p.add_argument("--thresholds", type=_floats, help="AI の閾値（カンマ区切り。省略時は PROBABILITY_THRESHOLD）")
    p.add_argument("--stop-mults", type=_floats, help="損切り幅 ATR の倍数（省略時は STOP_ATR_MULT）")
    p.add_argument("--targets", type=_floats, help="利確幅 %%（省略時は LABEL_TARGET_PCT）")
    p.add_argument("--horizons", type=_ints, help="最大保有本数（省略時は LABEL_HORIZON）")
    args = p.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    s = Settings.load()
    with connect(s.db_path) as conn:
        _, to_ms = resolve_period(conn, s, None, args.to_date, default_days=1)
        result = walkforward.run(
            conn, s, to_ms, args.folds, args.fold_days,
            thresholds=args.thresholds or [s.probability_threshold],
            stop_mults=args.stop_mults or [s.stop_atr_mult],
            targets=args.targets or [s.label_target_pct],
            horizons=args.horizons or [s.label_horizon])

    s.reports_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    paths = {name: s.reports_dir / f"walkforward_{stamp}_{name}.csv" for name in ("by_fold", "total", "trades")}
    for name, path in paths.items():
        getattr(result, name).to_csv(path, index=False, encoding="utf-8-sig")

    cols = ["target_pct", "horizon", "stop_mult", "strategy", "threshold", "trades", "win_rate", "profit_factor",
            "max_drawdown_pct", "total_return_pct", "winning_folds", "folds"]
    with pd.option_context("display.width", 200, "display.max_rows", 500, "display.float_format", "{:.3f}".format):
        print(result.total.sort_values("total_return_pct", ascending=False)[cols].to_string(index=False))
    for name, path in paths.items():
        print(f"{name}: {path}")


if __name__ == "__main__":
    main()
