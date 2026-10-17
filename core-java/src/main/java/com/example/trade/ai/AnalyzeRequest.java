package com.example.trade.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /analyze のリクエスト。特徴量は ai-python が DB から読むので、どの足かだけを渡す。 */
public record AnalyzeRequest(
        String symbol,
        String timeframe,
        @JsonProperty("open_time") long openTime,
        @JsonProperty("rule_hit") String ruleHit) {
}
