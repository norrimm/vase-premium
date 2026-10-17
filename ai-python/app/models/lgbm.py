"""LightGBM の二値分類（反発するか）。時系列分割でクロスバリデーションしてから全期間で学習する。"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier
from sklearn.metrics import log_loss, roc_auc_score
from sklearn.model_selection import TimeSeriesSplit

PARAMS = dict(
    n_estimators=300,
    learning_rate=0.03,
    num_leaves=31,
    min_child_samples=100,
    subsample=0.8,
    subsample_freq=1,
    colsample_bytree=0.8,
    reg_lambda=1.0,
    verbose=-1,
)


@dataclass
class TrainResult:
    model: LGBMClassifier
    folds: list[dict] = field(default_factory=list)
    rows: int = 0
    positive_rate: float = 0.0

    def summary(self) -> dict:
        def mean(key: str) -> float | None:
            vals = [f[key] for f in self.folds if f.get(key) is not None]
            return float(np.mean(vals)) if vals else None
        return {
            "rows": self.rows,
            "positive_rate": self.positive_rate,
            "cv_auc": mean("auc"),
            "cv_logloss": mean("logloss"),
            "cv_folds": self.folds,
        }


def train(x: pd.DataFrame, y: pd.Series, gap: int, n_splits: int = 5, seed: int = 42) -> TrainResult:
    """x, y は時系列順に並んでいること（シャッフルしない）。

    gap: 学習側の末尾と検証側の先頭の間に空ける行数。ラベルは gap 本先の値動きを見ているので、
    空けないと検証期間の値動きが学習側のラベルに漏れる。
    """
    folds = []
    for i, (tr, va) in enumerate(TimeSeriesSplit(n_splits=n_splits, gap=gap).split(x)):
        m = LGBMClassifier(**PARAMS, random_state=seed)
        m.fit(x.iloc[tr], y.iloc[tr])
        p = m.predict_proba(x.iloc[va])[:, 1]
        yv = y.iloc[va]
        two_classes = yv.nunique() == 2
        folds.append({
            "fold": i,
            "train_rows": len(tr),
            "valid_rows": len(va),
            "valid_positive_rate": float(yv.mean()),
            "auc": float(roc_auc_score(yv, p)) if two_classes else None,
            "logloss": float(log_loss(yv, p, labels=[0, 1])),
        })

    model = LGBMClassifier(**PARAMS, random_state=seed)
    model.fit(x, y)
    return TrainResult(model=model, folds=folds, rows=len(y), positive_rate=float(y.mean()))
