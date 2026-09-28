package com.example.trade.indicator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;

/**
 * 古い順に並んだ連続する足から、足ごとの指標を計算する（F-03）。
 * EMA / RSI / ATR は過去の値を引きずるので、先頭側に十分な本数（FeatureService.WARMUP）を含めて渡すこと。
 */
@Component
public class IndicatorCalculator {

    public List<Feature> calculate(List<Candle> candles) {
        int size = candles.size();
        double[] high = new double[size];
        double[] low = new double[size];
        double[] close = new double[size];
        double[] volume = new double[size];
        for (int i = 0; i < size; i++) {
            Candle c = candles.get(i);
            high[i] = c.high();
            low[i] = c.low();
            close[i] = c.close();
            volume[i] = c.volume();
        }

        double[] rsi14 = Rsi.compute(close, 14);
        Macd.Result macd = Macd.compute(close, 12, 26, 9);
        double[] sma20 = MovingAverage.sma(close, 20);
        double[] sma50 = MovingAverage.sma(close, 50);
        double[] volMa20 = MovingAverage.sma(volume, 20);
        double[] atr14 = Atr.compute(high, low, close, 14);

        List<Feature> features = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Candle c = candles.get(i);
            features.add(new Feature(c.symbol(), c.timeframe(), c.openTime(),
                    value(rsi14[i]),
                    value(macd.macd()[i]),
                    value(macd.signal()[i]),
                    value(macd.hist()[i]),
                    value(sma20[i]),
                    value(sma50[i]),
                    value((close[i] / sma20[i] - 1) * 100),
                    value(volMa20[i]),
                    value(volume[i] / volMa20[i]),
                    value(atr14[i])));
        }
        return features;
    }

    /** NaN / 無限大（0 除算）は「値なし」として null にする。 */
    private static Double value(double v) {
        return Double.isFinite(v) ? v : null;
    }
}
