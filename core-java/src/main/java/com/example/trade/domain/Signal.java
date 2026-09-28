package com.example.trade.domain;

/**
 * 検知したシグナル 1 件（signals テーブルの 1 行）。AI を入れるまでは probability 以降は null。
 *
 * @param id        保存前は null
 * @param ruleHit   一致した条件名（カンマ区切り）
 * @param signal    BUY / HOLD
 * @param createdAt epoch ms
 */
public record Signal(
        Long id,
        String symbol,
        Timeframe timeframe,
        long openTime,
        String ruleHit,
        Double probability,
        String signal,
        Double entryPrice,
        Double takeProfit,
        Double stopLoss,
        Double riskReward,
        String confidence,
        long createdAt) {

    public static final String BUY = "BUY";

    public Signal withId(long newId) {
        return new Signal(newId, symbol, timeframe, openTime, ruleHit, probability, signal, entryPrice,
                takeProfit, stopLoss, riskReward, confidence, createdAt);
    }
}
