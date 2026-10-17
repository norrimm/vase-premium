import numpy as np
import pandas as pd

from app.features.builder import LAGS, build_features, feature_columns
from app.features.labeling import future_max_high, make_labels
from tests.conftest import STEP, T0, add_indicators, make_candles


def _market(n=200):
    c = make_candles(n)
    return c.merge(add_indicators(c), on="open_time")


def test_lags_are_previous_rows():
    df = build_features(_market(), STEP)
    assert df["rsi14_lag1"].iloc[100] == df["rsi14"].iloc[99]
    assert df["rsi14_lag5"].iloc[100] == df["rsi14"].iloc[95]
    assert len(feature_columns()) == 10 * (LAGS + 1)


def test_lags_are_nan_across_gap():
    m = _market().drop(index=97).reset_index(drop=True)  # 97 本目が欠損
    df = build_features(m, STEP).set_index("open_time")
    t = T0 + 100 * STEP
    assert np.isnan(df.loc[t, "rsi14_lag3"])  # 3 本前 = 欠損した足
    assert not np.isnan(df.loc[t, "rsi14_lag2"])


def test_future_max_high_looks_only_ahead():
    df = pd.DataFrame({
        "open_time": T0 + np.arange(6) * STEP,
        "high": [10, 1, 2, 3, 4, 5.0],
        "close": [1.0] * 6,
    })
    fmax = future_max_high(df, horizon=2, step_ms=STEP)
    assert fmax.tolist()[:4] == [2, 3, 4, 5]
    assert fmax.iloc[4:].isna().all()  # 2 本先までそろっていない


def test_label_threshold():
    df = pd.DataFrame({
        "open_time": T0 + np.arange(4) * STEP,
        "high": [100, 100.7, 100.9, 100],
        "close": [100.0, 100, 100, 100],
    })
    labels = make_labels(df, horizon=1, target_pct=0.8, step_ms=STEP)
    assert labels.tolist()[:3] == [0.0, 1.0, 0.0]
    assert np.isnan(labels.iloc[3])
