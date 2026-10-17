"""POST /analyze: 1 本の確定足の反発確率とシグナルを返す（F-05）。

特徴量は DB から直接読む。core-java が渡すのは「どの足を見ればいいか」だけ。
"""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.db.session import connect
from app.models.predictor import BarNotFound, analyze

router = APIRouter()
log = logging.getLogger(__name__)


class AnalyzeRequest(BaseModel):
    symbol: str
    timeframe: str
    open_time: int
    rule_hit: str | None = None


class AnalyzeResponse(BaseModel):
    probability: float
    signal: str
    entry_price: float
    take_profit: float
    stop_loss: float
    risk_reward: float | None
    confidence: str
    model_version: str


@router.post("/analyze", response_model=AnalyzeResponse)
def post_analyze(req: AnalyzeRequest, request: Request):
    ctx = request.app.state.ctx
    bundle = ctx.bundle
    if bundle is None:
        raise HTTPException(503, "学習済みモデルがありません")
    if (req.symbol, req.timeframe) != (bundle.meta["symbol"], bundle.meta["timeframe"]):
        raise HTTPException(400, f"モデル {bundle.version} は {bundle.meta['symbol']} "
                                 f"{bundle.meta['timeframe']} 用です")
    started = time.perf_counter()
    try:
        with connect(ctx.settings.db_path) as conn:
            result = analyze(conn, ctx.settings, bundle, req.symbol, req.timeframe, req.open_time)
    except BarNotFound as e:
        raise HTTPException(404, str(e)) from e
    log.info("推論 %s %s open_time=%d rule_hit=%s → p=%.3f %s %s (%.0f ms)",
             req.symbol, req.timeframe, req.open_time, req.rule_hit, result.probability,
             result.signal, result.confidence, (time.perf_counter() - started) * 1000)
    return result.to_dict()
