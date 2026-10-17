"""学習して artifacts/ にモデルを保存する。

    python scripts/train.py                       # DB の最新から TRAIN_DAYS 日分
    python scripts/train.py --from 2025-10-01 --to 2026-09-30

起動中の API サーバーは再起動（または POST /train）で新しいモデルに切り替わる。
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.api.periods import resolve_period  # noqa: E402
from app.config import Settings  # noqa: E402
from app.db.session import connect  # noqa: E402
from app.models import pipeline  # noqa: E402


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--from", dest="from_date", help="学習期間の開始日 YYYY-MM-DD（JST）")
    p.add_argument("--to", dest="to_date", help="学習期間の終了日 YYYY-MM-DD（JST）")
    args = p.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    s = Settings.load()
    with connect(s.db_path) as conn:
        from_ms, to_ms = resolve_period(conn, s, args.from_date, args.to_date, default_days=s.train_days)
        bundle = pipeline.train_and_save(conn, s, from_ms, to_ms)

    meta = {k: v for k, v in bundle.meta.items() if k not in ("features", "params")}
    print(json.dumps(meta, ensure_ascii=False, indent=2))
    print(f"保存しました: {s.artifacts_dir / (bundle.version + '.pkl')}")


if __name__ == "__main__":
    main()
