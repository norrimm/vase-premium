"""設定。環境変数 → リポジトリ直下の .env → 既定値 の順で決まる（core-java と同じ .env を読む）。"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

# ai-python/ の場所。相対パスの設定はここを基準に解決する
BASE_DIR = Path(__file__).resolve().parent.parent
REPO_DIR = BASE_DIR.parent


def _load_dotenv(path: Path) -> dict[str, str]:
    """KEY=VALUE 形式の .env を読む。# 始まりの行と空行は無視。"""
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def _resolve(path: str) -> Path:
    p = Path(path)
    return p if p.is_absolute() else (BASE_DIR / p).resolve()


@dataclass(frozen=True)
class Settings:
    db_path: Path
    symbol: str
    timeframe: str
    artifacts_dir: Path
    reports_dir: Path
    rules_path: Path
    # 指定があればそのモデルを使う。空なら artifacts の最新
    model_version: str
    # 反発確率がこれ以上なら BUY
    probability_threshold: float
    # 反発確率がこれ以上（かつ閾値以上）なら信頼度 A
    confidence_a: float
    # 教師ラベル: label_horizon 本先までに +label_target_pct% 以上上昇したか
    label_horizon: int
    label_target_pct: float
    # 損切り目安 = エントリー − stop_atr_mult × ATR(14)
    stop_atr_mult: float
    # 学習に使う日数
    train_days: int
    # バックテストの片道手数料（%）
    fee_pct: float
    # 同一方向のシグナルを再通知しない時間（分）。バックテストで通知を再現するのに使う
    cooldown_minutes: int

    @staticmethod
    def load() -> "Settings":
        env = {**_load_dotenv(REPO_DIR / ".env"), **os.environ}

        def get(key: str, default: str) -> str:
            v = env.get(key, "")
            return v if v != "" else default

        return Settings(
            db_path=_resolve(get("DB_PATH", "../data/trade.db")),
            symbol=get("SYMBOL", "BTCUSDT"),
            timeframe=get("TIMEFRAME", "5m"),
            artifacts_dir=_resolve(get("ARTIFACTS_DIR", "artifacts")),
            reports_dir=_resolve(get("REPORTS_DIR", "reports")),
            rules_path=_resolve(get("RULES_PATH", "../core-java/src/main/resources/rules.yml")),
            model_version=get("MODEL_VERSION", ""),
            probability_threshold=float(get("PROBABILITY_THRESHOLD", "0.7")),
            confidence_a=float(get("CONFIDENCE_A", "0.8")),
            label_horizon=int(get("LABEL_HORIZON", "12")),
            label_target_pct=float(get("LABEL_TARGET_PCT", "0.8")),
            stop_atr_mult=float(get("STOP_ATR_MULT", "2.0")),
            train_days=int(get("TRAIN_DAYS", "365")),
            fee_pct=float(get("BACKTEST_FEE_PCT", "0.1")),
            cooldown_minutes=int(get("NOTIFY_COOLDOWN_MINUTES", "60")),
        )


def timeframe_millis(code: str) -> int:
    """'5m' / '1h' / '1d' / '1w' → ミリ秒。"""
    units = {"m": 60_000, "h": 3_600_000, "d": 86_400_000, "w": 7 * 86_400_000}
    n, unit = code[:-1], code[-1]
    if unit not in units or not n.isdigit():
        raise ValueError(f"Unsupported timeframe: {code}")
    return int(n) * units[unit]
