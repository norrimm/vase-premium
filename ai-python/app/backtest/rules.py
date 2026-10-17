"""検知ルール（core-java の RuleEngine と同じ条件）をバックテスト用に DataFrame 全体へ当てる。

閾値は core-java の rules.yml をそのまま読む。両方で同じファイルを使うので、
閾値を変えたときにライブの検知とバックテストがずれない。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pandas as pd
import yaml


@dataclass(frozen=True)
class BuyDipRule:
    rsi_below: float
    require_close_above_sma50: bool
    sma20_deviation_at_most: float
    volume_ratio_above: float

    @staticmethod
    def load(path: Path) -> "BuyDipRule":
        with path.open(encoding="utf-8") as f:
            p = yaml.safe_load(f)["rules"]["buy-dip"]
        return BuyDipRule(
            rsi_below=float(p["rsi-below"]),
            require_close_above_sma50=bool(p["require-close-above-sma50"]),
            sma20_deviation_at_most=float(p["sma20-deviation-at-most"]),
            volume_ratio_above=float(p["volume-ratio-above"]),
        )

    def hits(self, df: pd.DataFrame) -> pd.Series:
        """すべての条件を満たした足で True。指標が未計算（NaN）の足は False。"""
        hit = (df["rsi14"] < self.rsi_below) \
            & (df["dev_sma20"] <= self.sma20_deviation_at_most) \
            & (df["vol_ratio"] > self.volume_ratio_above)
        if self.require_close_above_sma50:
            hit &= df["close"] > df["sma50"]
        return hit.fillna(False).astype(bool)

    def rule_hit_names(self) -> str:
        names = ["rsi_low"]
        if self.require_close_above_sma50:
            names.append("above_sma50")
        names += ["sma20_deviation", "volume_spike"]
        return ",".join(names)
