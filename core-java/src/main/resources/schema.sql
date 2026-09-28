-- 確定足（MVP 要件定義 4. データモデル）
CREATE TABLE IF NOT EXISTS candles (
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,   -- epoch ms
  open        REAL, high REAL, low REAL, close REAL, volume REAL,
  PRIMARY KEY (symbol, timeframe, open_time)
);

-- 計算済み指標（確定足 1 本につき 1 行）
CREATE TABLE IF NOT EXISTS features (
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,
  rsi14       REAL, macd REAL, macd_signal REAL, macd_hist REAL,
  sma20       REAL, sma50 REAL, dev_sma20 REAL,
  vol_ma20    REAL, vol_ratio REAL, atr14 REAL,
  PRIMARY KEY (symbol, timeframe, open_time)
);

-- 検知＋推論結果。AI を入れるまでは probability などは NULL
CREATE TABLE IF NOT EXISTS signals (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,
  rule_hit    TEXT,               -- 一致した条件名（カンマ区切り）
  probability REAL,               -- AI 反発確率 0-1
  signal      TEXT,               -- BUY / HOLD
  entry_price REAL, take_profit REAL, stop_loss REAL,
  risk_reward REAL,
  confidence  TEXT,               -- A / B / C
  created_at  INTEGER NOT NULL
);
-- 再起動で同じ足を二重に登録しないため
CREATE UNIQUE INDEX IF NOT EXISTS signals_candle ON signals (symbol, timeframe, open_time);

-- 通知履歴（重複抑止に使う）
CREATE TABLE IF NOT EXISTS notifications (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  signal_id   INTEGER REFERENCES signals(id),
  channel     TEXT,               -- discord
  status      TEXT,               -- sent / failed
  sent_at     INTEGER NOT NULL,
  payload     TEXT
);
