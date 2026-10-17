package com.example.trade.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /analyze のレスポンス。
 *
 * @param probability 反発確率 0〜1
 * @param signal      BUY（閾値以上）/ HOLD
 * @param confidence  A / B / C
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeResponse(
        double probability,
        String signal,
        @JsonProperty("entry_price") double entryPrice,
        @JsonProperty("take_profit") double takeProfit,
        @JsonProperty("stop_loss") double stopLoss,
        @JsonProperty("risk_reward") Double riskReward,
        String confidence,
        @JsonProperty("model_version") String modelVersion) {
}
