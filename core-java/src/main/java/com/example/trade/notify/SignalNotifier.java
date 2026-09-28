package com.example.trade.notify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.trade.config.NotifyProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Signal;
import com.example.trade.monitor.RuleEngine.Evaluation;
import com.example.trade.repository.NotificationRepository;
import com.example.trade.repository.SignalRepository;

/**
 * ルールに一致した足をシグナルとして保存し、条件がそろえば Discord に通知する（F-06〜F-08）。
 * AI を入れるまでは、ルール一致 = BUY として扱う。
 */
@Service
public class SignalNotifier {

    private static final Logger log = LoggerFactory.getLogger(SignalNotifier.class);
    private static final String CHANNEL = "discord";
    private static final DateTimeFormatter JST = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Tokyo"));

    private final SignalRepository signals;
    private final NotificationRepository notifications;
    private final DuplicateSuppressor suppressor;
    private final MessageBuilder messages;
    private final DiscordNotifier discord;
    private final Duration maxDelay;
    private final Clock clock;

    public SignalNotifier(SignalRepository signals, NotificationRepository notifications,
            DuplicateSuppressor suppressor, MessageBuilder messages, DiscordNotifier discord,
            NotifyProperties props, Clock clock) {
        this.signals = signals;
        this.notifications = notifications;
        this.suppressor = suppressor;
        this.messages = messages;
        this.discord = discord;
        this.maxDelay = props.maxDelay();
        this.clock = clock;
    }

    /** ルールに一致した確定足を受け取る。 */
    public void onCandidate(Evaluation e) {
        Candle c = e.snapshot().candle();
        long now = clock.millis();
        Signal saved = signals.insertIfAbsent(new Signal(null, c.symbol(), c.timeframe(), c.openTime(),
                e.ruleHit(), null, Signal.BUY, c.close(), null, null, null, null, now));
        if (saved == null) {
            log.info("この足のシグナルは登録済みのため通知しません");
            return;
        }
        log.info("シグナル保存 id={} {} {}", saved.id(), saved.signal(), e.describe());

        if (!discord.enabled()) {
            log.info("DISCORD_WEBHOOK_URL が未設定のため通知しません (signal id={})", saved.id());
            return;
        }
        long delay = now - c.closeTime();
        if (delay > maxDelay.toMillis()) {
            log.warn("足の確定から {} 秒たっているため通知しません (signal id={})", delay / 1000, saved.id());
            return;
        }
        var blocking = suppressor.blockingSentAt(c.symbol(), saved.signal(), now);
        if (blocking.isPresent()) {
            log.info("重複抑止: {} {} は {} JST に通知済み（{} 分以内）のため通知しません (signal id={})",
                    c.symbol(), saved.signal(), JST.format(Instant.ofEpochMilli(blocking.get())),
                    suppressor.cooldown().toMinutes(), saved.id());
            return;
        }
        deliver(saved.id(), messages.buyDip(e).toString());
    }

    /** 接続確認用のテスト通知。重複抑止の対象外で、シグナルとしても保存しない。 */
    public void sendTest(Evaluation e) {
        if (!discord.enabled()) {
            log.warn("テスト通知を指定されましたが DISCORD_WEBHOOK_URL が未設定です");
            return;
        }
        deliver(null, messages.test(e).toString());
    }

    private void deliver(Long signalId, String payload) {
        try {
            discord.send(payload);
            notifications.insert(signalId, CHANNEL, NotificationRepository.SENT, clock.millis(), payload);
            log.info("Discord に通知しました (signal id={})", signalId);
        } catch (NotifyException e) {
            notifications.insert(signalId, CHANNEL, NotificationRepository.FAILED, clock.millis(), payload);
            log.error("Discord への通知に失敗しました (signal id={}): {}", signalId, e.getMessage());
        }
    }
}
