import math

import numpy as np
import pandas as pd

from app.backtest import engine, report, walkforward
from app.backtest.metrics import summarize
from app.backtest.rules import BuyDipRule
from app.db.session import connect
from app.models.predictor import decide
from tests.conftest import STEP, T0


def test_metrics():
    m = summarize(pd.Series([2.0, -1.0, 1.0, -2.0]))
    assert m["trades"] == 4
    assert m["win_rate"] == 0.5
    assert m["profit_factor"] == 1.0
    # 1.02 → 1.0098 → 1.019898 → 0.99950... 高値 1.02 からの下落
    assert math.isclose(m["max_drawdown_pct"], (1 - 1.02 * 0.99 * 1.01 * 0.98 / 1.02) * 100)


def test_metrics_without_losses():
    assert summarize(pd.Series([1.0]))["profit_factor"] == math.inf
    assert summarize(pd.Series(dtype=float))["trades"] == 0


def _bars(highs, lows, closes):
    n = len(closes)
    return pd.DataFrame({"open_time": T0 + np.arange(n) * STEP, "high": highs, "low": lows, "close": closes})


def test_trade_hits_take_profit():
    df = _bars([100, 100.5, 101.0], [100, 99.8, 100.2], [100, 100.3, 100.9])
    assert engine.simulate_trade(df, 0, 100.8, 99.0, 12) == (100.8, "take_profit", 2)


def test_stop_wins_when_both_touched_in_same_bar():
    df = _bars([100, 101.0], [100, 98.0], [100, 100])
    assert engine.simulate_trade(df, 0, 100.8, 99.0, 12) == (99.0, "stop", 1)


def test_trade_times_out():
    df = _bars([100, 100.1, 100.2, 100.3], [100, 99.9, 99.9, 99.9], [100, 100, 100.1, 100.2])
    assert engine.simulate_trade(df, 0, 100.8, 99.0, 2) == (100.1, "timeout", 2)


def test_rule_matches_core_java_conditions(tmp_path):
    p = tmp_path / "rules.yml"
    p.write_text("rules:\n  buy-dip:\n    rsi-below: 35\n    require-close-above-sma50: true\n"
                 "    sma20-deviation-at-most: -1.5\n    volume-ratio-above: 1.5\n", encoding="utf-8")
    rule = BuyDipRule.load(p)
    df = pd.DataFrame({
        "rsi14": [30, 30, 36, 30, np.nan],
        "dev_sma20": [-1.6, -1.6, -1.6, -1.5, -2],
        "vol_ratio": [1.6, 1.6, 1.6, 1.6, 2],
        "close": [101, 99, 101, 101, 101],
        "sma50": [100, 100, 100, 100, 100],
    })
    assert rule.hits(df).tolist() == [True, False, False, True, False]
    assert rule.rule_hit_names() == "rsi_low,above_sma50,sma20_deviation,volume_spike"


def test_decide(settings):
    a = decide(0.84, 100.0, 0.5, settings, "v")
    assert (a.signal, a.confidence) == ("BUY", "A")
    assert math.isclose(a.take_profit, 100.8)
    assert math.isclose(a.stop_loss, 99.0)
    assert math.isclose(a.risk_reward, 0.8)
    assert decide(0.6, 100.0, 0.5, settings, "v").confidence == "B"
    assert decide(0.3, 100.0, 0.5, settings, "v").signal == "HOLD"


def test_backtest_end_to_end(settings):
    with connect(settings.db_path) as conn:
        from_ms = T0 + 4000 * STEP
        to_ms = T0 + 5800 * STEP
        result = engine.run(conn, settings, from_ms, to_ms)
    s = result.summary.set_index("strategy")
    assert s.loc["rule", "trades"] > 0
    assert s.loc["rule+ai", "trades"] <= s.loc["rule", "trades"]
    trades = result.trades
    # 学習はバックテスト期間より前のデータだけ
    train_to = result.model_meta["train_to"]
    assert not settings.artifacts_dir.exists() or not list(settings.artifacts_dir.glob("*.pkl"))
    assert pd.Timestamp(train_to).value // 1_000_000 < from_ms
    # 重複抑止（60 分）が効いている
    rule_times = trades[trades["strategy"] == "rule"]["open_time"].diff().dropna()
    assert (rule_times >= 60 * 60_000).all()
    paths = report.write_csv(result, settings.reports_dir)
    assert pd.read_csv(paths["summary"], encoding="utf-8-sig")["strategy"].tolist() == ["rule", "rule+ai"]


def test_walk_forward_retrains_per_fold(settings):
    with connect(settings.db_path) as conn:
        to_ms = T0 + 5800 * STEP
        result = walkforward.run(conn, settings, to_ms, folds=2, fold_days=3, thresholds=[0.4, 0.6],
                                 stop_mults=[1.0, 2.0], targets=[0.8], horizons=[12])
    f = result.by_fold
    # 区間 2 × 損切り 2 ×（rule 1 行 + rule+ai 閾値 2 行）
    assert len(f) == 2 * 2 * 3
    # 区間ごとに直前のデータだけで学習し直している
    versions = f.groupby("fold")["model_version"].first()
    assert versions[1] != versions[2]
    assert len(result.total) == 2 * 3
    assert (result.total["folds"] == 2).all()
    # 通算の取引数は区間ごとの合計と一致する
    per_fold = f.groupby(["stop_mult", "strategy", "threshold"], dropna=False)["trades"].sum()
    total = result.total.set_index(["stop_mult", "strategy", "threshold"])["trades"]
    assert per_fold.sort_index().tolist() == total.sort_index().tolist()
