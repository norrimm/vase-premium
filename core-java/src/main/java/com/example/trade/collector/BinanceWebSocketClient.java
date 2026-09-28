package com.example.trade.collector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.trade.config.MarketProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Binance の kline ストリーム（wss://.../ws/{symbol}@kline_{interval}）を購読する。
 *
 * <ul>
 *   <li>切断・エラー時は指数バックオフ（1, 2, 4 ... 最大 60 秒）で再接続する</li>
 *   <li>streamIdleTimeout の間メッセージが来なければ無言切断とみなして張り直す</li>
 *   <li>Binance は 24 時間で接続を切るが、これも通常の切断として再接続される</li>
 * </ul>
 * 切断中に取りこぼした足は REST の定期同期（CandleBackfillService）が埋める。
 */
@Component
public class BinanceWebSocketClient implements CandleStream {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MarketProperties.Binance props;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-ws");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean reconnectPending = new AtomicBoolean();
    private volatile boolean running;
    private volatile WebSocket webSocket;
    private volatile long lastMessageAt;

    private URI uri;
    private Timeframe tf;
    private Consumer<Candle> onClosedCandle;

    public BinanceWebSocketClient(HttpClient http, ObjectMapper mapper, Clock clock, MarketProperties market) {
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
        this.props = market.binance();
    }

    @Override
    public synchronized void start(String symbol, Timeframe tf, Consumer<Candle> onClosedCandle) {
        if (running) {
            throw new IllegalStateException("already started");
        }
        this.uri = URI.create(props.wsBaseUrl() + "/ws/" + symbol.toLowerCase(Locale.ROOT) + "@kline_" + tf.code());
        this.tf = tf;
        this.onClosedCandle = onClosedCandle;
        this.running = true;
        connect();
        long checkEvery = Math.max(1, props.streamIdleTimeout().toSeconds() / 3);
        scheduler.scheduleAtFixedRate(this::checkIdle, checkEvery, checkEvery, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void stop() {
        running = false;
        scheduler.shutdownNow();
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    private void connect() {
        // 古い接続からの遅れて届くコールバックを無視するため、接続ごとに世代番号を振る
        int gen = generation.incrementAndGet();
        lastMessageAt = clock.millis();
        log.info("WebSocket 接続中: {}", uri);
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new Listener(gen))
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.warn("WebSocket 接続失敗: {}", err.toString());
                        scheduleReconnect(gen);
                    } else if (gen == generation.get()) {
                        webSocket = ws;
                        log.info("WebSocket 接続完了");
                    } else {
                        ws.abort();
                    }
                });
    }

    private void scheduleReconnect(int gen) {
        if (!running || gen != generation.get() || !reconnectPending.compareAndSet(false, true)) {
            return;
        }
        int failures = consecutiveFailures.getAndIncrement();
        long delay = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(failures, 6));
        log.info("{} 秒後に WebSocket を再接続します", delay);
        scheduler.schedule(() -> {
            reconnectPending.set(false);
            if (running) {
                connect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void checkIdle() {
        if (reconnectPending.get()) {
            return;
        }
        long idle = clock.millis() - lastMessageAt;
        if (idle > props.streamIdleTimeout().toMillis()) {
            log.warn("WebSocket から {} 秒メッセージが来ていないため張り直します", idle / 1000);
            WebSocket ws = webSocket;
            webSocket = null;
            if (ws != null) {
                ws.abort();
            }
            scheduleReconnect(generation.get());
        }
    }

    private void handleMessage(String text) {
        lastMessageAt = clock.millis();
        consecutiveFailures.set(0);
        try {
            parseClosedKline(mapper.readTree(text), tf).ifPresent(onClosedCandle);
        } catch (Exception e) {
            log.error("kline メッセージの処理に失敗しました: {}", text, e);
        }
    }

    /** kline イベントのうち確定足（k.x == true）だけを Candle にする。 */
    static Optional<Candle> parseClosedKline(JsonNode root, Timeframe tf) {
        JsonNode k = root.get("k");
        if (!"kline".equals(root.path("e").asText()) || k == null || !k.path("x").asBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new Candle(
                k.get("s").asText(), tf,
                k.get("t").asLong(),
                k.get("o").asDouble(),
                k.get("h").asDouble(),
                k.get("l").asDouble(),
                k.get("c").asDouble(),
                k.get("v").asDouble()));
    }

    private final class Listener implements WebSocket.Listener {

        private final int gen;
        private final StringBuilder buffer = new StringBuilder();

        Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                if (gen == generation.get()) {
                    handleMessage(text);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
            // Pong は JDK が自動で返す。ping も生存確認として扱う
            if (gen == generation.get()) {
                lastMessageAt = clock.millis();
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            if (gen == generation.get()) {
                log.warn("WebSocket が切断されました (code={}, reason={})", statusCode, reason);
                scheduleReconnect(gen);
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (gen == generation.get()) {
                log.warn("WebSocket エラー: {}", error.toString());
                scheduleReconnect(gen);
            }
        }
    }
}
