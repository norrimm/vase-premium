"""学習済みモデルの保存と読み込み。

artifacts/lgbm_YYYYMMDD_NN.pkl にモデル、同名の .json に学習条件と CV の成績を置く。
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib

_VERSION = re.compile(r"^lgbm_(\d{8})_(\d{2})$")


@dataclass
class ModelBundle:
    version: str
    model: object
    meta: dict

    @property
    def features(self) -> list[str]:
        return self.meta["features"]


def save(artifacts_dir: Path, model: object, meta: dict, now: datetime | None = None) -> ModelBundle:
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    day = (now or datetime.now(timezone.utc)).strftime("%Y%m%d")
    seq = 1 + max((int(m.group(2)) for v in list_versions(artifacts_dir)
                   if (m := _VERSION.match(v)) and m.group(1) == day), default=0)
    version = f"lgbm_{day}_{seq:02d}"
    meta = {**meta, "model_version": version}
    joblib.dump(model, artifacts_dir / f"{version}.pkl")
    (artifacts_dir / f"{version}.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return ModelBundle(version, model, meta)


def list_versions(artifacts_dir: Path) -> list[str]:
    """保存済みのバージョン。古い順。"""
    if not artifacts_dir.is_dir():
        return []
    return sorted(p.stem for p in artifacts_dir.glob("lgbm_*.pkl") if _VERSION.match(p.stem))


def load(artifacts_dir: Path, version: str = "") -> ModelBundle | None:
    """version が空なら最新を読む。1 つもなければ None。"""
    if not version:
        versions = list_versions(artifacts_dir)
        if not versions:
            return None
        version = versions[-1]
    pkl = artifacts_dir / f"{version}.pkl"
    if not pkl.is_file():
        raise FileNotFoundError(f"モデルが見つかりません: {pkl}")
    meta = json.loads((artifacts_dir / f"{version}.json").read_text(encoding="utf-8"))
    return ModelBundle(version, joblib.load(pkl), meta)
