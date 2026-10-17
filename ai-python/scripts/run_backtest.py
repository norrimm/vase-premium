"""バックテストして勝率 / PF / 最大 DD を CSV に出す（F-09）。

    python scripts/run_backtest.py                          # DB の最新から 180 日分
    python scripts/run_backtest.py --from 2026-04-01 --to 2026-09-30
    python scripts/run_backtest.py --threshold 0.6          # AI の閾値を変えて試す
    python scripts/run_backtest.py --model lgbm_20261017_01 # 学習済みモデルを指定

--model を省略すると、検証期間より前のデータだけで学習し直してから検証する（未来の情報を使わない）。
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pandas as pd  # noqa: E402

from app.api.periods import resolve_period  # noqa: E402
from app.backtest import engine, report  # noqa: E402
from app.config import Settings  # noqa: E402
from app.db.session import connect  # noqa: E402


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--from", dest="from_date", help="検証期間の開始日 YYYY-MM-DD（JST）")
    p.add_argument("--to", dest="to_date", help="検証期間の終了日 YYYY-MM-DD（JST）")
    p.add_argument("--days", type=int, default=180, help="--from 省略時の検証日数（既定 180）")
    p.add_argument("--model", help="使うモデルのバージョン（省略時は学習し直す）")
    p.add_argument("--threshold", type=float, help="AI の閾値（省略時は PROBABILITY_THRESHOLD）")
    args = p.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    s = Settings.load()
    with connect(s.db_path) as conn:
        from_ms, to_ms = resolve_period(conn, s, args.from_date, args.to_date, default_days=args.days)
        bundle = engine.load_model(s, args.model) if args.model else None
        result = engine.run(conn, s, from_ms, to_ms, bundle=bundle, threshold=args.threshold)
    paths = report.write_csv(result, s.reports_dir)

    cols = ["strategy", "trades", "win_rate", "profit_factor", "max_drawdown_pct", "total_return_pct",
            "avg_return_pct", "label_hit_rate"]
    with pd.option_context("display.width", 200, "display.float_format", "{:.3f}".format):
        print(result.summary[cols].to_string(index=False))
    print(f"モデル: {result.model_version}")
    for name, path in paths.items():
        print(f"{name}: {path}")


if __name__ == "__main__":
    main()
