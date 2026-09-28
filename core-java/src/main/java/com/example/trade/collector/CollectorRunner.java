package com.example.trade.collector;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.example.trade.config.MarketProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.example.trade.repository.CandleRepository;

import jakarta.annotation.PreDestroy;

/**
 * データ取得の起動順を制御する（F-01 / F-02）。
 * <ol>
 *   <li>REST でバックフィル（過去 backfillDays 日分）</li>
 *   <li>WebSocket で確定足をリアルタイム受信</li>
 *   <li>REST で定期的に欠損を補完（WebSocket 切断中の取りこぼし対策）</li>
 * </ol>
 */
@Component
public class CollectorRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final CandleBackfillService backfill;
    private final CandleStream stream;
    private final CandleRepository repository;
    private final TaskScheduler scheduler;
    private final MarketProperties props;

    public CollectorRunner(CandleBackfillService backfill, CandleStream stream, CandleRepository repository,
            TaskScheduler scheduler, MarketProperties props) {
        this.backfill = backfill;
        this.stream = stream;
        this.repository = repository;
        this.scheduler = scheduler;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Timeframe tf = props.parsedTimeframe();
        log.info("{} {} の取得を開始します (バックフィル {} 日)", props.symbol(), tf, props.backfillDays());
        syncSafely();

        stream.start(props.symbol(), tf, this::onClosedCandle);
        scheduler.scheduleWithFixedDelay(this::syncSafely,
                Instant.now().plus(props.syncInterval()), props.syncInterval());
    }

    @PreDestroy
    public void stop() {
        stream.stop();
    }

    private void onClosedCandle(Candle c) {
        repository.upsert(c);
        log.info("確定足 {} {} {} O={} H={} L={} C={} V={}", c.symbol(), c.timeframe(),
                Instant.ofEpochMilli(c.openTime()), c.open(), c.high(), c.low(), c.close(), c.volume());
    }

    private void syncSafely() {
        try {
            backfill.syncToNow();
        } catch (Exception e) {
            // 次回の定期同期で再試行される
            log.error("REST 同期に失敗しました", e);
        }
    }
}
