"""DB の足と指標 → モデルに渡す特徴量の DataFrame。"""

from __future__ import annotations

import numpy as np
import pandas as pd

from app.features.technical import BASE_FEATURES, add_base_features

# 直近何本分のラグ値を特徴量に加えるか
LAGS = 5


def feature_columns() -> list[str]:
    cols = list(BASE_FEATURES)
    for k in range(1, LAGS + 1):
        cols += [f"{c}_lag{k}" for c in BASE_FEATURES]
    return cols


def build_features(market: pd.DataFrame, step_ms: int) -> pd.DataFrame:
    """足ごとに feature_columns() の列を持つ DataFrame を返す（元の列も残す）。

    足が欠けている箇所では、ずれたラグを使わないよう該当するラグを NaN にする
    （LightGBM は NaN をそのまま扱える）。
    """
    df = add_base_features(market).reset_index(drop=True)
    open_time = df["open_time"]
    # 1 本前が欠けていれば変化率は意味を持たない
    df.loc[open_time.diff() != step_ms, "ret_pct"] = np.nan

    lagged = {}
    for k in range(1, LAGS + 1):
        contiguous = (open_time - open_time.shift(k)) == k * step_ms
        for c in BASE_FEATURES:
            lagged[f"{c}_lag{k}"] = df[c].shift(k).where(contiguous)
    return pd.concat([df, pd.DataFrame(lagged, index=df.index)], axis=1)
