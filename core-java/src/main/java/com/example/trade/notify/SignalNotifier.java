package com.example.trade.notify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.trade.ai.AiClient;
import com.example.trade.ai.AiException;
import com.example.trade.ai.AnalyzeRequest;
import com.example.trade.ai.AnalyzeResponse;
import com.example.trade.config.NotifyProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Signal;
import com.example.trade.monitor.RuleEngine.Evaluation;
import com.example.trade.repository.NotificationRepository;
import com.example.trade.repository.SignalRepository;

/**
 * ルールに一致した足を AI に問い合わせてシグナルとして保存し、条件がそろえば Discord に通知する（F-05〜F-08）。
 * AI が BUY（反発確率が閾値以上）と判定したときだけ通知する。
 * AI を使わない設定（AI_SERVICE_URL が空）や推論失敗時は、ルール一致 = BUY として扱う。
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
    private final AiClient aiClient;
    private final AlertNotifier alerts;
    private final Duration maxDelay;
    private final Clock clock;

    public SignalNotifier(SignalRepository signals, NotificationRepository notifications,
            DuplicateSuppressor suppressor, MessageBuilder messages, DiscordNotifier discord,
            AiClient aiClient, AlertNotifier alerts, NotifyProperties props, Clock clock) {
        this.signals = signals;
        this.notifications = notifications;
        this.suppressor = suppressor;
        this.messages = messages;
        this.discord = discord;
        this.aiClient = aiClient;
        this.alerts = alerts;
        this.maxDelay = props.maxDelay();
        this.clock = clock;
    }

    /** ルールに一致した確定足を受け取る。 */
    public void onCandidate(Evaluation e) {
        Candle c = e.snapshot().candle();
        AnalyzeResponse ai = analyze(e);
        long now = clock.millis();
        Signal signal = ai == null
                ? new Signal(null, c.symbol(), c.timeframe(), c.openTime(), e.ruleHit(), null, Signal.BUY,
                        c.close(), null, null, null, null, now)
                : new Signal(null, c.symbol(), c.timeframe(), c.openTime(), e.ruleHit(), ai.probability(),
                        ai.signal(), ai.entryPrice(), ai.takeProfit(), ai.stopLoss(), ai.riskReward(),
                        ai.confidence(), now);
        Signal saved = signals.insertIfAbsent(signal);
        if (saved == null) {
            log.info("この足のシグナルは登録済みのため通知しません");
            return;
        }
        log.info("シグナル保存 id={} {} {}", saved.id(), saved.signal(), e.describe());

        if (!Signal.BUY.equals(saved.signal())) {
            log.info("AI 判定 {}（反発確率 {}%）のため通知しません (signal id={})",
                    saved.signal(), Math.round(ai.probability() * 100), saved.id());
            return;
        }
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
        deliver(saved.id(), messages.buyDip(e, ai, aiClient.enabled()).toString());
    }

    /**
     * AI に反発確率を問い合わせる。AI を使わない設定、または推論に失敗したときは null。
     * 失敗時は障害通知を出したうえで、ルール一致だけで通知を続ける（AI が落ちていてもシグナルを取りこぼさない）。
     */
    private AnalyzeResponse analyze(Evaluation e) {
        if (!aiClient.enabled()) {
            return null;
        }
        Candle c = e.snapshot().candle();
        long started = System.nanoTime();
        try {
            AnalyzeResponse ai = aiClient.analyze(
                    new AnalyzeRequest(c.symbol(), c.timeframe().code(), c.openTime(), e.ruleHit()));
            log.info("AI 推論 {} 反発確率={}% 信頼度={} ({} ms, {})", ai.signal(), Math.round(ai.probability() * 100),
                    ai.confidence(), (System.nanoTime() - started) / 1_000_000, ai.modelVersion());
            alerts.recovered(AlertSource.AI);
            return ai;
        } catch (AiException ex) {
            log.error("AI 推論に失敗しました。ルール一致だけで通知します: {}", ex.getMessage());
            alerts.failure(AlertSource.AI, "AI 推論に失敗しました（ルール一致だけで通知を継続）: " + ex.getMessage());
            return null;
        }
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
