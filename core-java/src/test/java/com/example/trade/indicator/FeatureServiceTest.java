package com.example.trade.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.domain.Timeframe;
import com.example.trade.repository.CandleRepository;
import com.example.trade.repository.FeatureRepository;
import com.example.trade.repository.TestDatabase;

class FeatureServiceTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final long STEP = M5.millis();

    @TempDir
    Path dir;

    private SingleConnectionDataSource ds;
    private CandleRepository candles;
    private FeatureRepository features;
    private FeatureService service;

    @BeforeEach
    void setUp() {
        ds = TestDatabase.create(dir);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        candles = new CandleRepository(jdbc);
        features = new FeatureRepository(jdbc);
        service = new FeatureService(candles, features, new IndicatorCalculator());
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    @Test
    void computesAllMissingFeatures() {
        candles.upsertAll(series(0, 100));

        List<MarketSnapshot> snapshots = service.update("BTCUSDT", M5, Long.MAX_VALUE);

        assertThat(snapshots).hasSize(100);
        assertThat(features.count("BTCUSDT", M5)).isEqualTo(100);
        assertThat(service.update("BTCUSDT", M5, Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void incrementalUpdateMatchesFullHistory() {
        List<Candle> all = series(0, 2000);
        candles.upsertAll(all.subList(0, 1999));
        service.update("BTCUSDT", M5, Long.MAX_VALUE);

        Candle last = all.get(1999);
        candles.upsert(last);
        List<MarketSnapshot> snapshots = service.update("BTCUSDT", M5, last.openTime());

        assertThat(snapshots).hasSize(1);
        Feature incremental = snapshots.get(0).feature();
        Feature full = new IndicatorCalculator().calculate(all).get(1999);
        assertThat(incremental.rsi14()).isCloseTo(full.rsi14(), within(1e-9));
        assertThat(incremental.macdSignal()).isCloseTo(full.macdSignal(), within(1e-9));
        assertThat(incremental.atr14()).isCloseTo(full.atr14(), within(1e-9));
    }

    /** ゆるやかに波打つ価格の足を n 本作る。 */
    private static List<Candle> series(int start, int n) {
        List<Candle> list = new ArrayList<>();
        for (int i = start; i < start + n; i++) {
            double c = 100 + 10 * Math.sin(i / 7.0) + i * 0.01;
            list.add(new Candle("BTCUSDT", M5, i * STEP, c - 0.5, c + 1, c - 1, c, 10 + i % 5));
        }
        return list;
    }
}
