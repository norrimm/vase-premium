package com.example.trade.domain;

/** 確定足 1 本。openTime は epoch ms（足の開始時刻）。 */
public record Candle(
        String symbol,
        Timeframe timeframe,
        long openTime,
        double open,
        double high,
        double low,
        double close,
        double volume) {

    /** 足が確定する時刻（次の足の開始時刻）。 */
    public long closeTime() {
        return openTime + timeframe.millis();
    }
}
