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
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.example.trade.config.NotifyProperties;
import com.example.trade.repository.NotificationRepository;
import com.example.trade.repository.SystemLogRepository;
import com.example.trade.repository.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class AlertNotifierTest {

    private static final long MINUTE = 60_000;
    private static final long T0 = Instant.parse("2026-10-17T00:00:00Z").toEpochMilli();

    @TempDir
    Path dir;

    private SingleConnectionDataSource ds;
    private SystemLogRepository systemLog;
    private NotificationRepository notifications;
    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock(T0);

    @BeforeEach
    void setUp() throws IOException {
        ds = TestDatabase.create(dir);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        systemLog = new SystemLogRepository(jdbc);
        notifications = new NotificationRepository(jdbc);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", ex -> {
            received.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(204, -1);
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
    void alertsOnceWithinCooldownAndAgainAfter() {
        AlertNotifier alerts = notifier(webhookUrl());
        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");
        clock.advance(10 * MINUTE);
        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");
        clock.advance(25 * MINUTE);
        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");

        assertThat(received).hasSize(2);
        assertThat(received.get(0))
                .contains("障害 — 足データ取得（REST）")
                .contains("\"color\":" + MessageBuilder.COLOR_ERROR)
                .contains("HTTP 503");
        assertThat(received.get(1)).contains("3 回");
        // 抑止した分も system_log には残る
        assertThat(systemLog.count(SystemLogRepository.ERROR, "rest_sync")).isEqualTo(3);
        assertThat(notifications.count(NotificationRepository.SENT)).isEqualTo(2);
    }

    @Test
    void sourcesAreThrottledIndependently() {
        AlertNotifier alerts = notifier(webhookUrl());
        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");
        alerts.failure(AlertSource.AI, "timeout");

        assertThat(received).hasSize(2);
    }

    @Test
    void notifiesRecoveryOnlyAfterAlert() {
        AlertNotifier alerts = notifier(webhookUrl());
        alerts.recovered(AlertSource.REST_SYNC);
        assertThat(received).isEmpty();

        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");
        clock.advance(7 * MINUTE);
        alerts.recovered(AlertSource.REST_SYNC);
        alerts.recovered(AlertSource.REST_SYNC);

        assertThat(received).hasSize(2);
        assertThat(received.get(1))
                .contains("復旧 — 足データ取得（REST）")
                .contains("7 分");
        assertThat(systemLog.count(SystemLogRepository.INFO, "rest_sync")).isEqualTo(1);

        // 復旧後の障害は抑止時間に関係なく通知する
        alerts.failure(AlertSource.REST_SYNC, "HTTP 503");
        assertThat(received).hasSize(3);
    }

    @Test
    void onlyRecordsWhenWebhookIsNotConfigured() {
        AlertNotifier alerts = notifier("");
        alerts.failure(AlertSource.AI, "timeout");

        assertThat(received).isEmpty();
        assertThat(systemLog.count(SystemLogRepository.ERROR, "ai")).isEqualTo(1);
    }

    private AlertNotifier notifier(String webhookUrl) {
        NotifyProperties props = new NotifyProperties(Duration.ofMinutes(60), Duration.ofMinutes(10),
                new NotifyProperties.Discord(webhookUrl, 0, false),
                new NotifyProperties.Alert(Duration.ofMinutes(30), Duration.ofMinutes(5)));
        ObjectMapper mapper = new ObjectMapper();
        // 送信は同じスレッドで行い、テストで結果をすぐ確認できるようにする
        return new AlertNotifier(systemLog, notifications, new MessageBuilder(mapper),
                new DiscordNotifier(HttpClient.newHttpClient(), mapper, props), props, clock, Runnable::run);
    }

    private String webhookUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
    }

    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void advance(long delta) {
            millis += delta;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
