"""トレード列の成績（勝率 / PF / 最大ドローダウン など）。"""

from __future__ import annotations

import math

import pandas as pd


def summarize(returns_pct: pd.Series) -> dict:
    """1 トレードごとの損益率（%、手数料控除後）から成績を出す。

    最大ドローダウンは、毎回全額を投じて複利で回したときの資産曲線の、高値からの最大下落率（%）。
    """
    r = returns_pct.dropna().astype(float)
    n = len(r)
    if n == 0:
        return {"trades": 0, "win_rate": None, "profit_factor": None, "avg_return_pct": None,
                "total_return_pct": None, "max_drawdown_pct": None, "sharpe_per_trade": None}
    gains = r[r > 0].sum()
    losses = -r[r < 0].sum()
    equity = (1 + r / 100).cumprod()
    peak = equity.cummax().clip(lower=1.0)  # 開始時点の資産 1.0 も高値に含める
    drawdown = (equity / peak - 1) * 100
    std = r.std(ddof=1) if n > 1 else 0.0
    return {
        "trades": n,
        "win_rate": float((r > 0).mean()),
        "profit_factor": float(gains / losses) if losses > 0 else (math.inf if gains > 0 else None),
        "avg_return_pct": float(r.mean()),
        "total_return_pct": float((equity.iloc[-1] - 1) * 100),
        "max_drawdown_pct": max(0.0, float(-drawdown.min())),
        "sharpe_per_trade": float(r.mean() / std) if std > 0 else None,
    }
