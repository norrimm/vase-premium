package com.example.trade.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.example.trade.config.MarketProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.example.trade.repository.CandleRepository;
import com.example.trade.repository.TestDatabase;

class CandleBackfillServiceTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final long STEP = M5.millis();
    /** 5 分足の境界から 2 分過ぎた時刻（最新足は未確定）。 */
    private static final long NOW = Instant.parse("2026-09-29T00:02:00Z").toEpochMilli();
    private static final long CURRENT_OPEN = NOW - Duration.ofMinutes(2).toMillis();

    @TempDir
    Path dir;

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private CandleRepository repository;
    private FakeExchange exchange;
    private CandleBackfillService service;

    @BeforeEach
    void setUp() {
        ds = TestDatabase.create(dir);
        jdbc = new JdbcTemplate(ds);
        repository = new CandleRepository(jdbc);
        exchange = new FakeExchange(3);
        MarketProperties props = new MarketProperties("BTCUSDT", "5m", 1, Duration.ofSeconds(60),
                new MarketProperties.Binance("", "", Duration.ZERO, 0, Duration.ofSeconds(90)));
        service = new CandleBackfillService(exchange, repository, props, event -> { });
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    @Test
    void backfillsWholeWindowExceptUnclosedCandle() {
        int saved = service.syncToNow();

        long oneDayOfCandles = Duration.ofDays(1).toMillis() / STEP;
        assertThat(saved).isEqualTo(oneDayOfCandles);
        assertThat(repository.findLatestOpenTime("BTCUSDT", M5)).contains(CURRENT_OPEN - STEP);
        // ページ上限 3 本ずつ取得している
        assertThat(exchange.requests).hasSize((int) (oneDayOfCandles / 3));
    }

    @Test
    void secondSyncFetchesNothingWhenUpToDate() {
        service.syncToNow();
        exchange.requests.clear();

        assertThat(service.syncToNow()).isZero();
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void refillsGapInTheMiddle() {
        service.syncToNow();
        long base = CURRENT_OPEN - 10 * STEP;
        // WebSocket 切断中に 3 本取りこぼし、再接続後の確定足だけが入った状態
        jdbc
                .update("DELETE FROM candles WHERE open_time BETWEEN ? AND ?", base + 2 * STEP, base + 4 * STEP);
        exchange.requests.clear();

        assertThat(service.syncToNow()).isPositive();

        assertThat(exchange.requests.get(0)).isEqualTo(base + 2 * STEP);
        assertThat(repository.findFirstMissingOpenTime("BTCUSDT", M5, 0)).contains(CURRENT_OPEN);
    }

    @Test
    void backfillsHeadOfWindowWhenMissing() {
        long base = CURRENT_OPEN - 10 * STEP;
        repository.upsertAll(List.of(candle(base), candle(base + STEP)));

        service.syncToNow();

        long windowStart = CURRENT_OPEN - Duration.ofDays(1).toMillis();
        assertThat(exchange.requests.get(0)).isEqualTo(windowStart);
        assertThat(repository.findEarliestOpenTime("BTCUSDT", M5)).contains(windowStart);
    }

    private static Candle candle(long openTime) {
        return new Candle("BTCUSDT", M5, openTime, 1, 2, 0.5, 1.5, 10);
    }

    /** 1 ページ pageSize 本、NOW 時点の未確定足まで返す取引所。 */
    private static final class FakeExchange implements ExchangeClient {

        final int pageSize;
        final List<Long> requests = new ArrayList<>();

        FakeExchange(int pageSize) {
            this.pageSize = pageSize;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public long serverTime() {
            return NOW;
        }

        @Override
        public int maxLimit() {
            return pageSize;
        }

        @Override
        public List<Candle> fetchCandles(String symbol, Timeframe tf, long startTime, int limit) {
            requests.add(startTime);
            List<Candle> page = new ArrayList<>();
            for (long t = startTime; t <= CURRENT_OPEN && page.size() < limit; t += STEP) {
                page.add(candle(t));
            }
            return page;
        }
    }
}
