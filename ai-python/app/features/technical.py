"""F-03 の指標を、価格水準に依存しない形にそろえる。

SMA や MACD、ATR は価格そのものの単位なので、BTC が 6 万ドルのときと 10 万ドルのときで
値の大きさが変わってしまう。終値に対する % に直してからモデルに渡す。
"""

from __future__ import annotations

import pandas as pd

# 確定足 1 本から作る特徴量（この後、直近 5 本分のラグを付ける）
BASE_FEATURES = [
    "rsi14",
    "macd_pct",
    "macd_signal_pct",
    "macd_hist_pct",
    "dev_sma20",
    "dev_sma50",
    "vol_ratio",
    "atr_pct",
    "ret_pct",
    "range_pct",
]


def add_base_features(df: pd.DataFrame) -> pd.DataFrame:
    """load_market() の結果に BASE_FEATURES の列を足して返す。"""
    out = df.copy()
    close = out["close"]
    out["macd_pct"] = out["macd"] / close * 100
    out["macd_signal_pct"] = out["macd_signal"] / close * 100
    out["macd_hist_pct"] = out["macd_hist"] / close * 100
    out["dev_sma50"] = (close / out["sma50"] - 1) * 100
    out["atr_pct"] = out["atr14"] / close * 100
    # 1 本前の終値からの変化率。直前の足が欠けていれば NaN（builder 側で連続性を見る）
    out["ret_pct"] = close.pct_change() * 100
    out["range_pct"] = (out["high"] - out["low"]) / close * 100
    return out
