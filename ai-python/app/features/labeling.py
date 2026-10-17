"""教師ラベル: その足の終値で買って、horizon 本先までに +target_pct% 以上上昇したか。"""

from __future__ import annotations

import numpy as np
import pandas as pd


def future_max_high(df: pd.DataFrame, horizon: int, step_ms: int) -> pd.Series:
    """各足について、次の足から horizon 本先までの高値の最大値。

    先の足が horizon 本そろっていない（データの末尾、または途中に欠損がある）足は NaN。
    """
    high = df["high"].reset_index(drop=True)
    open_time = df["open_time"].reset_index(drop=True)
    # 逆順にして rolling を取ると「この足から horizon 本先まで」の最大値になる
    fwd_max = high[::-1].rolling(horizon, min_periods=horizon).max()[::-1]
    result = fwd_max.shift(-1)
    complete = (open_time.shift(-horizon) - open_time) == horizon * step_ms
    return result.where(complete).set_axis(df.index)


def make_labels(df: pd.DataFrame, horizon: int, target_pct: float, step_ms: int) -> pd.Series:
    """1 / 0 のラベル。判定できない足は NaN。"""
    fmax = future_max_high(df, horizon, step_ms)
    target = df["close"] * (1 + target_pct / 100)
    label = (fmax >= target).astype(float)
    return label.where(fmax.notna(), np.nan)
