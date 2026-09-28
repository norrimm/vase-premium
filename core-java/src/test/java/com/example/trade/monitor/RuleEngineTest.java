package com.example.trade.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.trade.config.RuleProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.domain.Timeframe;
import com.example.trade.monitor.RuleEngine.Evaluation;

class RuleEngineTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final RuleProperties DEFAULTS = new RuleProperties(
            new RuleProperties.BuyDip(35, true, -1.5, 1.5));

    @Test
    void hitsWhenAllConditionsHold() {
        Evaluation e = new RuleEngine(DEFAULTS).evaluate(snapshot(100, 28.4, 98.0, -1.8, 1.9));

        assertThat(e.hit()).isTrue();
        assertThat(e.ruleHit()).isEqualTo("rsi_low,above_sma50,sma20_deviation,volume_spike");
        assertThat(e.describe()).contains("○rsi_low(RSI=28.40 < 35.00)");
    }

    @Test
    void missesWhenOneConditionFails() {
        // 出来高が平均の 1.2 倍しかない
        Evaluation e = new RuleEngine(DEFAULTS).evaluate(snapshot(100, 28.4, 98.0, -1.8, 1.2));

        assertThat(e.hit()).isFalse();
        assertThat(e.ruleHit()).isEqualTo("rsi_low,above_sma50,sma20_deviation");
        assertThat(e.describe()).contains("×volume_spike(出来高比=1.20倍 > 1.50倍)");
    }

    @Test
    void missesWhileIndicatorsAreWarmingUp() {
        Evaluation e = new RuleEngine(DEFAULTS).evaluate(snapshot(100, null, null, null, null));

        assertThat(e.hit()).isFalse();
        assertThat(e.ruleHit()).isEmpty();
    }

    @Test
    void sma50ConditionCanBeDisabled() {
        RuleProperties props = new RuleProperties(new RuleProperties.BuyDip(35, false, -1.5, 1.5));

        // 終値が SMA50 より下でも成立する
        Evaluation e = new RuleEngine(props).evaluate(snapshot(100, 28.4, 105.0, -1.8, 1.9));

        assertThat(e.hit()).isTrue();
        assertThat(e.ruleHit()).doesNotContain("above_sma50");
    }

    private static MarketSnapshot snapshot(double close, Double rsi, Double sma50, Double devSma20, Double volRatio) {
        Candle c = new Candle("BTCUSDT", M5, 0, close, close, close, close, 1);
        Feature f = new Feature("BTCUSDT", M5, 0, rsi, null, null, null, null, sma50, devSma20, null, volRatio, null);
        return new MarketSnapshot(c, f);
    }
}
