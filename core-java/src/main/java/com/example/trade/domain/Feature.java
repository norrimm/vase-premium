package com.example.trade.domain;

/**
 * 確定足 1 本に対する計算済み指標。計算に必要な本数がそろわない間の値は null。
 *
 * @param devSma20 終値の SMA(20) からの乖離率（%）。-1.5 なら SMA(20) より 1.5% 下
 * @param volRatio 出来高 ÷ 出来高 MA(20)
 */
public record Feature(
        String symbol,
        Timeframe timeframe,
        long openTime,
        Double rsi14,
        Double macd,
        Double macdSignal,
        Double macdHist,
        Double sma20,
        Double sma50,
        Double devSma20,
        Double volMa20,
        Double volRatio,
        Double atr14) {
}
