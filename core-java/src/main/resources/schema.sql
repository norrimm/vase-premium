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
