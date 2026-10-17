"""POST /train: DB の足で再学習し、推論に使うモデルを差し替える。"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.api.periods import resolve_period
from app.db.session import connect
from app.models import pipeline

router = APIRouter()


class TrainRequest(BaseModel):
    # 省略時は DB の最新から TRAIN_DAYS 日分
    from_date: str | None = None
    to_date: str | None = None


@router.post("/train")
def post_train(request: Request, req: TrainRequest | None = None):
    ctx = request.app.state.ctx
    req = req or TrainRequest()
    if not ctx.heavy_lock.acquire(blocking=False):
        raise HTTPException(409, "学習かバックテストを実行中です")
    try:
        with connect(ctx.settings.db_path) as conn:
            from_ms, to_ms = resolve_period(conn, ctx.settings, req.from_date, req.to_date,
                                            default_days=ctx.settings.train_days)
            bundle = pipeline.train_and_save(conn, ctx.settings, from_ms, to_ms)
    except ValueError as e:
        raise HTTPException(400, str(e)) from e
    finally:
        ctx.heavy_lock.release()
    ctx.bundle = bundle
    return {"model_version": bundle.version, **{k: v for k, v in bundle.meta.items() if k != "features"}}
