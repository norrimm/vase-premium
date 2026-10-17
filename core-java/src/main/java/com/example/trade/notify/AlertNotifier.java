package com.example.trade.notify;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.trade.config.NotifyProperties;
import com.example.trade.repository.NotificationRepository;
import com.example.trade.repository.SystemLogRepository;

import jakarta.annotation.PreDestroy;

/**
 * 取得失敗・API エラー・推論失敗などの障害を Discord に通知する（F-10）。
 *
 * <ul>
 *   <li>障害はすべて system_log に残す</li>
 *   <li>Discord へは箇所ごとに最初の 1 回と、続いていれば cooldown ごとに 1 回だけ送る（連投しない）</li>
 *   <li>障害を通知した箇所が復旧したら、復旧も 1 回通知する</li>
 * </ul>
 * 送信は別スレッドで行う。呼び出し元（WebSocket 受信・定期同期）を Discord のリトライで止めないため。
 */
@Service
public class AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);
    private static final String CHANNEL = "discord";

    private final SystemLogRepository systemLog;
    private final NotificationRepository notifications;
    private final MessageBuilder messages;
    private final DiscordNotifier discord;
    private final Duration cooldown;
    private final Clock clock;
    private final Executor sender;
    private final Map<AlertSource, Incident> incidents = new EnumMap<>(AlertSource.class);

    @Autowired
    public AlertNotifier(SystemLogRepository systemLog, NotificationRepository notifications,
            MessageBuilder messages, DiscordNotifier discord, NotifyProperties props, Clock clock) {
        this(systemLog, notifications, messages, discord, props, clock, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "alert-sender");
            t.setDaemon(true);
            return t;
        }));
    }

    AlertNotifier(SystemLogRepository systemLog, NotificationRepository notifications, MessageBuilder messages,
            DiscordNotifier discord, NotifyProperties props, Clock clock, Executor sender) {
        this.systemLog = systemLog;
        this.notifications = notifications;
        this.messages = messages;
        this.discord = discord;
        this.cooldown = props.alert().cooldown();
        this.clock = clock;
        this.sender = sender;
    }

    @PreDestroy
    public void stop() {
        if (sender instanceof ExecutorService es) {
            es.shutdown();
        }
    }

    /** 障害が起きた。message はそのまま Discord に出るので、秘匿情報を含めないこと。 */
    public synchronized void failure(AlertSource source, String message) {
        long now = clock.millis();
        record(SystemLogRepository.ERROR, source, message, now);
        Incident incident = incidents.computeIfAbsent(source, s -> new Incident(now));
        incident.failures++;
        if (incident.lastAlertedAt != null && now - incident.lastAlertedAt < cooldown.toMillis()) {
            log.info("障害通知を抑止: {}（{} 回目、{} 分以内に通知済み）",
                    source.label(), incident.failures, cooldown.toMinutes());
            return;
        }
        incident.lastAlertedAt = now;
        if (discord.enabled()) {
            deliverAsync(messages.alert(source, message, incident.failures, incident.since).toString());
        }
    }

    /** 正常に処理できた。障害中だった箇所なら復旧として記録・通知する。 */
    public synchronized void recovered(AlertSource source) {
        Incident incident = incidents.remove(source);
        if (incident == null) {
            return;
        }
        long now = clock.millis();
        record(SystemLogRepository.INFO, source, "復旧（障害 " + incident.failures + " 回）", now);
        log.info("{} が復旧しました（障害 {} 回）", source.label(), incident.failures);
        if (incident.lastAlertedAt != null && discord.enabled()) {
            deliverAsync(messages.recovered(source, incident.failures, incident.since, now).toString());
        }
    }

    private void record(String level, AlertSource source, String message, long now) {
        try {
            systemLog.insert(level, source.code(), message, now);
        } catch (RuntimeException e) {
            // DB 自体の障害でも Discord への通知は続ける
            log.error("system_log への記録に失敗しました", e);
        }
    }

    private void deliverAsync(String payload) {
        sender.execute(() -> {
            try {
                discord.send(payload);
                notifications.insert(null, CHANNEL, NotificationRepository.SENT, clock.millis(), payload);
                log.info("障害通知を Discord に送りました");
            } catch (RuntimeException e) {
                log.error("障害通知を Discord に送れませんでした: {}", e.getMessage());
                try {
                    notifications.insert(null, CHANNEL, NotificationRepository.FAILED, clock.millis(), payload);
                } catch (RuntimeException ignored) {
                    // DB も使えない状態。ログには残っている
                }
            }
        });
    }

    /** 1 箇所の障害が始まってから復旧するまでの状態。 */
    private static final class Incident {
        final long since;
        int failures;
        Long lastAlertedAt;

        Incident(long since) {
            this.since = since;
        }
    }
}
