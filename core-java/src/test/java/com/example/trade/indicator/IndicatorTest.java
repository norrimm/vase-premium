package com.example.trade.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.domain.Timeframe;

/** 期待値はすべて手計算できる小さな例。 */
class IndicatorTest {

    private static final double NaN = Double.NaN;

    @Test
    void sma() {
        assertThat(MovingAverage.sma(new double[] {1, 2, 3, 4, 5}, 3))
                .containsExactly(NaN, NaN, 2, 3, 4);
    }

    @Test
    void emaSeedsWithSmaThenSmooths() {
        // α = 2 / (3 + 1) = 0.5、初期値 = (1 + 2 + 3) / 3 = 2
        assertThat(MovingAverage.ema(new double[] {1, 2, 3, 4, 5}, 3))
                .containsExactly(NaN, NaN, 2, 3, 4);
    }

    @Test
    void emaSkipsLeadingNaN() {
        assertThat(MovingAverage.ema(new double[] {NaN, 1, 2, 3, 4}, 3))
                .containsExactly(NaN, NaN, NaN, 2, 3);
    }

    @Test
    void rsiUsesWilderSmoothing() {
        // 変化 +1, -1, +1。初回 平均上昇 0.5 / 平均下落 0.5 → 50
        // 次 上昇 (0.5 + 1) / 2 = 0.75、下落 (0.5 + 0) / 2 = 0.25 → RS = 3 → 75
        assertThat(Rsi.compute(new double[] {1, 2, 1, 2}, 2))
                .containsExactly(NaN, NaN, 50, 75);
    }

    @Test
    void rsiIs100WhenOnlyRising() {
        assertThat(Rsi.compute(new double[] {1, 2, 3, 4}, 2)).containsExactly(NaN, NaN, 100, 100);
    }

    @Test
    void atrUsesTrueRange() {
        double[] high = {10, 11, 12, 11};
        double[] low = {8, 9, 9, 10};
        double[] close = {9, 10, 11, 10};
        // TR = 2, 3, 1 → 初回 (2 + 3) / 2 = 2.5 → 次 (2.5 + 1) / 2 = 1.75
        assertThat(Atr.compute(high, low, close, 2)).containsExactly(NaN, NaN, 2.5, 1.75);
    }

    @Test
    void macdSignalStartsAfterSlowPlusSignalPeriod() {
        double[] close = new double[40];
        Arrays.fill(close, 100);

        Macd.Result r = Macd.compute(close, 12, 26, 9);

        assertThat(r.macd()[24]).isNaN();
        assertThat(r.macd()[25]).isZero();
        assertThat(r.signal()[32]).isNaN();
        assertThat(r.signal()[33]).isZero();
        assertThat(r.hist()[33]).isZero();
    }

    @Test
    void calculatorLeavesWarmupRowsNull() {
        Timeframe m5 = Timeframe.parse("5m");
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double c = 100 + i;
            candles.add(new Candle("BTCUSDT", m5, i * m5.millis(), c, c + 1, c - 1, c, 10));
        }

        List<Feature> f = new IndicatorCalculator().calculate(candles);

        assertThat(f.get(13).rsi14()).isNull();
        assertThat(f.get(14).rsi14()).isEqualTo(100);
        assertThat(f.get(48).sma50()).isNull();
        assertThat(f.get(49).sma50()).isEqualTo(124.5);
        // 終値 159、SMA20 = (140 + ... + 159) / 20 = 149.5
        assertThat(f.get(59).devSma20()).isCloseTo((159 / 149.5 - 1) * 100, within(1e-9));
        assertThat(f.get(59).volRatio()).isEqualTo(1.0);
    }
}
