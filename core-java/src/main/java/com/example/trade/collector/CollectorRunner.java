package com.example.trade.collector;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.example.trade.config.MarketProperties;
import com.example.trade.config.NotifyProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.example.trade.notify.AlertNotifier;
import com.example.trade.notify.AlertSource;
import com.example.trade.repository.CandleRepository;

import jakarta.annotation.PreDestroy;

/**
 * データ取得の起動順を制御する（F-01 / F-02）。
 * <ol>
 *   <li>REST でバックフィル（過去 backfillDays 日分）</li>
 *   <li>WebSocket で確定足をリアルタイム受信</li>
 *   <li>REST で定期的に欠損を補完（WebSocket 切断中の取りこぼし対策）</li>
 *   <li>REST の失敗・WebSocket の長時間切断を障害として通知（F-10）</li>
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
    private final ApplicationEventPublisher events;
    private final AlertNotifier alerts;
    private final Duration streamDownAfter;

    public CollectorRunner(CandleBackfillService backfill, CandleStream stream, CandleRepository repository,
            TaskScheduler scheduler, MarketProperties props, ApplicationEventPublisher events,
            AlertNotifier alerts, NotifyProperties notifyProps) {
        this.backfill = backfill;
        this.stream = stream;
        this.repository = repository;
        this.scheduler = scheduler;
        this.props = props;
        this.events = events;
        this.alerts = alerts;
        this.streamDownAfter = notifyProps.alert().streamDownAfter();
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
        events.publishEvent(new CandlesStoredEvent(c.symbol(), c.timeframe(), c.openTime()));
    }

    private void syncSafely() {
        try {
            backfill.syncToNow();
            alerts.recovered(AlertSource.REST_SYNC);
        } catch (Exception e) {
            // 次回の定期同期で再試行される
            log.error("REST 同期に失敗しました", e);
            alerts.failure(AlertSource.REST_SYNC, "REST 同期に失敗しました: " + e.getMessage());
        }
        checkStream();
    }

    /** WebSocket が streamDownAfter 以上つながっていなければ障害として通知する（欠損は REST 同期が埋める）。 */
    private void checkStream() {
        OptionalLong since = stream.disconnectedSince();
        if (since.isEmpty()) {
            alerts.recovered(AlertSource.STREAM);
            return;
        }
        long downFor = System.currentTimeMillis() - since.getAsLong();
        if (downFor >= streamDownAfter.toMillis()) {
            alerts.failure(AlertSource.STREAM, "WebSocket が " + downFor / 60_000
                    + " 分つながっていません（足は REST 同期で補完中。通知が最大 " + props.syncInterval().toSeconds()
                    + " 秒遅れます）");
        }
    }
}
