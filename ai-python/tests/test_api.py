from fastapi.testclient import TestClient

from app import main
from app.db.session import connect
from app.models import pipeline
from tests.conftest import STEP, T0


def _client(settings, monkeypatch):
    monkeypatch.setattr(main.Settings, "load", staticmethod(lambda: settings))
    return TestClient(main.app)


def test_health_without_model(settings, monkeypatch):
    with _client(settings, monkeypatch) as client:
        assert client.get("/health").json()["status"] == "no_model"
        r = client.post("/analyze", json={"symbol": "BTCUSDT", "timeframe": "5m", "open_time": T0})
        assert r.status_code == 503


def test_analyze(settings, monkeypatch):
    with connect(settings.db_path) as conn:
        pipeline.train_and_save(conn, settings, T0, T0 + 5000 * STEP)
    with _client(settings, monkeypatch) as client:
        assert client.get("/health").json()["status"] == "ok"
        t = T0 + 5500 * STEP
        r = client.post("/analyze", json={"symbol": "BTCUSDT", "timeframe": "5m", "open_time": t,
                                          "rule_hit": "rsi_low,volume_spike,sma20_deviation"})
        assert r.status_code == 200, r.text
        body = r.json()
        assert 0 <= body["probability"] <= 1
        assert body["signal"] in ("BUY", "HOLD")
        assert body["take_profit"] > body["entry_price"] > body["stop_loss"]
        assert body["model_version"].startswith("lgbm_")

        missing = client.post("/analyze", json={"symbol": "BTCUSDT", "timeframe": "5m",
                                                "open_time": T0 + 99_999 * STEP})
        assert missing.status_code == 404


def test_train_endpoint_swaps_model(settings, monkeypatch):
    with _client(settings, monkeypatch) as client:
        r = client.post("/train", json={})
        assert r.status_code == 200, r.text
        assert client.get("/health").json()["model_version"] == r.json()["model_version"]
