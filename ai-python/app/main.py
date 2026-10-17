"""FastAPI エントリ。core-java から POST /analyze で呼ばれる（MVP 要件定義 5.）。

起動: uvicorn app.main:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import analyze, backtest, train
from app.config import Settings
from app.models import registry

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger(__name__)


class State:
    """プロセス全体で共有する設定と読み込み済みモデル。/train で差し替わる。"""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.bundle = registry.load(settings.artifacts_dir, settings.model_version)
        # 学習・バックテストは重いので同時に 1 つだけ
        self.heavy_lock = threading.Lock()


@asynccontextmanager
async def lifespan(app: FastAPI):
    state = State(Settings.load())
    if state.bundle is None:
        log.warning("学習済みモデルがありません。POST /train か scripts/train.py で作成してください")
    else:
        log.info("モデル %s を読み込みました", state.bundle.version)
    app.state.ctx = state
    yield


app = FastAPI(title="ai-python", lifespan=lifespan)
app.include_router(analyze.router)
app.include_router(train.router)
app.include_router(backtest.router)


@app.get("/health")
def health():
    s: State = app.state.ctx
    return {
        "status": "ok" if s.bundle is not None else "no_model",
        "model_version": s.bundle.version if s.bundle else None,
        "probability_threshold": s.settings.probability_threshold,
    }
