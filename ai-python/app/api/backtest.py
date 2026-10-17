"""POST /backtest: 期間を指定してバックテストし、成績を返して CSV に保存する（F-09）。"""

from __future__ import annotations

import math

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.api.periods import resolve_period
from app.backtest import engine, report
from app.db.session import connect

router = APIRouter()


class BacktestRequest(BaseModel):
    # 省略時は DB の最新から 180 日分
    from_date: str | None = None
    to_date: str | None = None
    # 指定がなければ検証期間より前のデータで学習し直す
    model_version: str | None = None
    threshold: float | None = None


@router.post("/backtest")
def post_backtest(request: Request, req: BacktestRequest | None = None):
    ctx = request.app.state.ctx
    req = req or BacktestRequest()
    if not ctx.heavy_lock.acquire(blocking=False):
        raise HTTPException(409, "学習かバックテストを実行中です")
    try:
        with connect(ctx.settings.db_path) as conn:
            from_ms, to_ms = resolve_period(conn, ctx.settings, req.from_date, req.to_date, default_days=180)
            bundle = engine.load_model(ctx.settings, req.model_version) if req.model_version else None
            result = engine.run(conn, ctx.settings, from_ms, to_ms, bundle=bundle, threshold=req.threshold)
    except (ValueError, FileNotFoundError) as e:
        raise HTTPException(400, str(e)) from e
    finally:
        ctx.heavy_lock.release()
    paths = report.write_csv(result, ctx.settings.reports_dir)
    summary = [{k: _json_safe(v) for k, v in row.items()} for row in result.summary.to_dict("records")]
    return {"summary": summary, "csv": {k: str(p) for k, p in paths.items()}}


def _json_safe(v):
    # PF が無限大（負けなし）や NaN は JSON にできない
    if isinstance(v, float) and (math.isinf(v) or math.isnan(v)):
        return None if math.isnan(v) else "inf"
    return v
