package com.example.trade.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.example.trade.config.NotifyProperties;
import com.example.trade.config.RuleProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.domain.Timeframe;
import com.example.trade.monitor.RuleEngine;
import com.example.trade.monitor.RuleEngine.Evaluation;
import com.example.trade.repository.NotificationRepository;
import com.example.trade.repository.SignalRepository;
import com.example.trade.repository.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class SignalNotifierTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final long STEP = M5.millis();
    private static final long MINUTE = 60_000;
    private static final RuleEngine RULES = new RuleEngine(
            new RuleProperties(new RuleProperties.BuyDip(35, false, -1.5, 1.5)));

    @TempDir
    Path dir;

    private SingleConnectionDataSource ds;
    private SignalRepository signals;
    private NotificationRepository notifications;
    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();
    /** 先頭から順に返すステータス。空になったら 204。 */
    private final Deque<Integer> statuses = new ArrayDeque<>();

    @BeforeEach
    void setUp() throws IOException {
        ds = TestDatabase.create(dir);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        signals = new SignalRepository(jdbc);
        notifications = new NotificationRepository(jdbc);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", ex -> {
            received.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Integer status = statuses.poll();
            if (status == null || status == 204) {
                ex.sendResponseHeaders(204, -1);
            } else {
                byte[] body = "{\"message\":\"rate limited\",\"retry_after\":0.01}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, body.length);
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ds.destroy();
    }

    @Test
    void savesSignalAndSendsEmbed() {
        long openTime = 100 * STEP;
        notifier(webhookUrl(), openTime + STEP + 5_000).onCandidate(hit(openTime));

        assertThat(signals.count("BTCUSDT", M5)).isEqualTo(1);
        assertThat(notifications.count(NotificationRepository.SENT)).isEqualTo(1);
        assertThat(received).hasSize(1);
        assertThat(received.get(0))
                .contains("押し目買い候補 — BTC/USDT (5m)")
                .contains("\"color\":" + MessageBuilder.COLOR_BUY)
                .contains("83,298.01 USDT")
                .contains("rsi_low,sma20_deviation,volume_spike");
    }

    @Test
    void suppressesSameDirectionWithinCooldown() {
        long t1 = 100 * STEP;
        notifier(webhookUrl(), t1 + STEP).onCandidate(hit(t1));
        // 55 分後の足は抑止、65 分後の足は通知
        long t2 = t1 + 55 * MINUTE;
        notifier(webhookUrl(), t2 + STEP).onCandidate(hit(t2));
        long t3 = t1 + 65 * MINUTE;
        notifier(webhookUrl(), t3 + STEP).onCandidate(hit(t3));

        assertThat(signals.count("BTCUSDT", M5)).isEqualTo(3);
        assertThat(received).hasSize(2);
    }

    @Test
    void doesNotNotifySameCandleTwice() {
        long openTime = 100 * STEP;
        // 再起動をまたいで同じ足を評価したケース（抑止時間は 0 にして、足の重複だけで止まることを見る）
        notifier(webhookUrl(), openTime + STEP, Duration.ZERO).onCandidate(hit(openTime));
        notifier(webhookUrl(), openTime + STEP + MINUTE, Duration.ZERO).onCandidate(hit(openTime));

        assertThat(signals.count("BTCUSDT", M5)).isEqualTo(1);
        assertThat(received).hasSize(1);
    }

    @Test
    void skipsStaleCandidate() {
        long openTime = 100 * STEP;
        notifier(webhookUrl(), openTime + STEP + 11 * MINUTE).onCandidate(hit(openTime));

        assertThat(signals.count("BTCUSDT", M5)).isEqualTo(1);
        assertThat(received).isEmpty();
    }

    @Test
    void onlySavesWhenWebhookIsNotConfigured() {
        long openTime = 100 * STEP;
        notifier("", openTime + STEP).onCandidate(hit(openTime));

        assertThat(signals.count("BTCUSDT", M5)).isEqualTo(1);
        assertThat(notifications.count(NotificationRepository.SENT)).isZero();
    }

    @Test
    void retriesOnRateLimit() {
        statuses.add(429);
        long openTime = 100 * STEP;
        notifier(webhookUrl(), openTime + STEP).onCandidate(hit(openTime));

        assertThat(received).hasSize(2);
        assertThat(notifications.count(NotificationRepository.SENT)).isEqualTo(1);
    }

    @Test
    void recordsFailureAndDoesNotSuppressNext() {
        statuses.add(400);
        long t1 = 100 * STEP;
        notifier(webhookUrl(), t1 + STEP).onCandidate(hit(t1));
        long t2 = t1 + STEP;
        notifier(webhookUrl(), t2 + STEP).onCandidate(hit(t2));

        assertThat(notifications.count(NotificationRepository.FAILED)).isEqualTo(1);
        assertThat(notifications.count(NotificationRepository.SENT)).isEqualTo(1);
    }

    @Test
    void testMessageIsSentEvenWithoutHit() {
        long openTime = 100 * STEP;
        Evaluation miss = RULES.evaluate(snapshot(openTime, 50.0));
        notifier(webhookUrl(), openTime + STEP).sendTest(miss);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).contains("テスト通知（条件不一致）");
        assertThat(signals.count("BTCUSDT", M5)).isZero();
    }

    private SignalNotifier notifier(String webhookUrl, long now) {
        return notifier(webhookUrl, now, Duration.ofMinutes(60));
    }

    private SignalNotifier notifier(String webhookUrl, long now, Duration cooldown) {
        NotifyProperties props = new NotifyProperties(cooldown, Duration.ofMinutes(10),
                new NotifyProperties.Discord(webhookUrl, 2, false));
        ObjectMapper mapper = new ObjectMapper();
        return new SignalNotifier(signals, notifications, new DuplicateSuppressor(notifications, props),
                new MessageBuilder(mapper), new DiscordNotifier(HttpClient.newHttpClient(), mapper, props),
                props, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
    }

    private String webhookUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
    }

    private static Evaluation hit(long openTime) {
        Evaluation e = RULES.evaluate(snapshot(openTime, 28.4));
        assertThat(e.hit()).isTrue();
        return e;
    }

    private static MarketSnapshot snapshot(long openTime, double rsi) {
        Candle c = new Candle("BTCUSDT", M5, openTime, 84000, 84100, 83000, 83298.01, 120);
        Feature f = new Feature("BTCUSDT", M5, openTime, rsi, 10.0, 12.0, -2.0, 84600.0, 84000.0, -1.8,
                60.0, 2.0, 150.0);
        return new MarketSnapshot(c, f);
    }
}
