package com.example.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;

class CandleRepositoryTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final long STEP = M5.millis();

    @TempDir
    Path dir;

    private SingleConnectionDataSource ds;
    private CandleRepository repository;

    @BeforeEach
    void setUp() {
        ds = TestDatabase.create(dir);
        repository = new CandleRepository(new JdbcTemplate(ds));
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    @Test
    void upsertOverwritesSameOpenTime() {
        repository.upsert(candle(0, 100));
        repository.upsert(candle(0, 200));

        assertThat(repository.count("BTCUSDT", M5)).isEqualTo(1);
    }

    @Test
    void findsFirstGap() {
        repository.upsertAll(List.of(candle(0, 1), candle(STEP, 1), candle(3 * STEP, 1)));

        assertThat(repository.findFirstMissingOpenTime("BTCUSDT", M5, 0)).contains(2 * STEP);
        assertThat(repository.findFirstMissingOpenTime("BTCUSDT", M5, 3 * STEP)).contains(4 * STEP);
        assertThat(repository.findFirstMissingOpenTime("BTCUSDT", M5, 5 * STEP)).isEmpty();
        assertThat(repository.findLatestOpenTime("BTCUSDT", M5)).contains(3 * STEP);
    }

    private static Candle candle(long openTime, double close) {
        return new Candle("BTCUSDT", M5, openTime, 1, 2, 0.5, close, 10);
    }
}
