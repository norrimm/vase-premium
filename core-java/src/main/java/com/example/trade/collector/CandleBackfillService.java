package com.example.trade.collector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.trade.config.MarketProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.example.trade.repository.CandleRepository;

/**
 * REST で DB の足を「現在まで欠損なし」の状態にする。
 * 初回は backfillDays 日分をさかのぼって取得し、以降は欠損と最新足の続きだけを取得する。
 * 何度呼んでも同じ結果になる（upsert）ので、起動時・定期同期・再接続後のどこから呼んでもよい。
 */
@Service
public class CandleBackfillService {

    private static final Logger log = LoggerFactory.getLogger(CandleBackfillService.class);

    /** 前回同期以降の欠損チェックで、念のため余分にさかのぼる時間。 */
    private static final Duration RECHECK_MARGIN = Duration.ofHours(1);

    private final ExchangeClient client;
    private final CandleRepository repository;
    private final MarketProperties props;

    private Long lastSyncedAt;

    public CandleBackfillService(ExchangeClient client, CandleRepository repository,
            MarketProperties props) {
        this.client = client;
        this.repository = repository;
        this.props = props;
    }

    /** @return 保存した確定足の本数 */
    public synchronized int syncToNow() {
        String symbol = props.symbol();
        Timeframe tf = props.parsedTimeframe();
        long now = client.serverTime();
        long windowStart = alignDown(now - Duration.ofDays(props.backfillDays()).toMillis(), tf);

        long from;
        boolean firstSync = lastSyncedAt == null;
        if (firstSync && repository.findEarliestOpenTime(symbol, tf).map(t -> t > windowStart).orElse(true)) {
            // DB が空、または保持期間の先頭が欠けている（BACKFILL_DAYS を増やした等）
            from = windowStart;
        } else {
            // 初回は保持期間全体、2 回目以降は前回同期の少し前から欠損を探す
            long checkFrom = firstSync
                    ? windowStart
                    : Math.max(windowStart, lastSyncedAt - RECHECK_MARGIN.toMillis());
            from = repository.findFirstMissingOpenTime(symbol, tf, checkFrom)
                    .or(() -> repository.findLatestOpenTime(symbol, tf).map(t -> t + tf.millis()))
                    .orElse(windowStart);
            from = Math.max(from, windowStart);
        }

        int saved = 0;
        int pages = 0;
        // from の足が確定しているときだけ取りに行く
        while (from + tf.millis() <= now) {
            List<Candle> page = client.fetchCandles(symbol, tf, from, client.maxLimit());
            List<Candle> closed = page.stream().filter(c -> c.closeTime() <= now).toList();
            if (!closed.isEmpty()) {
                repository.upsertAll(closed);
                saved += closed.size();
            }
            pages++;
            if (pages % 10 == 0) {
                log.info("バックフィル中: {} 本保存 (最新 {})", saved, Instant.ofEpochMilli(from));
            }
            if (page.size() < client.maxLimit()) {
                break;
            }
            from = page.get(page.size() - 1).openTime() + tf.millis();
            pause();
        }
        lastSyncedAt = now;
        if (saved > 0) {
            log.info("{} {} {} を {} 本同期しました (DB 合計 {} 本)",
                    client.name(), symbol, tf, saved, repository.count(symbol, tf));
        }
        return saved;
    }

    private static long alignDown(long epochMillis, Timeframe tf) {
        return epochMillis - Math.floorMod(epochMillis, tf.millis());
    }

    private void pause() {
        try {
            Thread.sleep(props.binance().requestPause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeException("バックフィル中に割り込まれました", e);
        }
    }
}
