-- 確定足（MVP 要件定義 4. データモデル）
CREATE TABLE IF NOT EXISTS candles (
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,   -- epoch ms
  open        REAL, high REAL, low REAL, close REAL, volume REAL,
  PRIMARY KEY (symbol, timeframe, open_time)
);
