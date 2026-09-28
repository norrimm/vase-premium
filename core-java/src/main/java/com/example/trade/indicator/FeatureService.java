package com.example.trade.indicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.domain.Timeframe;
import com.example.trade.repository.CandleRepository;
import com.example.trade.repository.FeatureRepository;

/** 足の追加に合わせて features テーブルを最新にする。 */
@Service
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    /**
     * 再計算の際に、対象より前に読み込む本数。EMA / RSI / ATR は過去の影響が指数的に薄れるので、
     * 500 本あれば全履歴から計算した値との差は浮動小数点の誤差程度になる。
     */
    static final int WARMUP = 500;

    private final CandleRepository candles;
    private final FeatureRepository features;
    private final IndicatorCalculator calculator;

    public FeatureService(CandleRepository candles, FeatureRepository features, IndicatorCalculator calculator) {
        this.candles = candles;
        this.features = features;
        this.calculator = calculator;
    }

    /**
     * changedFrom 以降の足と、まだ指標が無い足の指標を計算して保存する。
     *
     * @return 計算し直した足と指標（古い順）
     */
    public synchronized List<MarketSnapshot> update(String symbol, Timeframe tf, long changedFrom) {
        long notYetComputed = features.findLatestOpenTime(symbol, tf)
                .map(t -> t + tf.millis())
                .or(() -> candles.findEarliestOpenTime(symbol, tf))
                .orElse(Long.MAX_VALUE);
        long from = Math.min(changedFrom, notYetComputed);

        List<Candle> loaded = candles.findFrom(symbol, tf, from - WARMUP * tf.millis());
        List<Feature> computed = calculator.calculate(loaded);

        Map<Long, Candle> byOpenTime = loaded.stream()
                .collect(Collectors.toMap(Candle::openTime, Function.identity()));
        List<Feature> changed = new ArrayList<>();
        List<MarketSnapshot> snapshots = new ArrayList<>();
        for (Feature f : computed) {
            if (f.openTime() >= from) {
                changed.add(f);
                snapshots.add(new MarketSnapshot(byOpenTime.get(f.openTime()), f));
            }
        }
        if (!changed.isEmpty()) {
            features.upsertAll(changed);
            log.debug("指標を {} 本計算しました", changed.size());
        }
        return snapshots;
    }
}
