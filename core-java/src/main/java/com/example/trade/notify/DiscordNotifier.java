package com.example.trade.notify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.trade.config.NotifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Discord Webhook への送信（F-06）。429 / 5xx / 通信エラーはリトライする。
 * Webhook URL はログにも例外メッセージにも出さない（URL を知っていれば誰でも投稿できるため）。
 */
@Component
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final NotifyProperties.Discord props;

    public DiscordNotifier(HttpClient http, ObjectMapper mapper, NotifyProperties notify) {
        this.http = http;
        this.mapper = mapper;
        this.props = notify.discord();
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** 送信する。リトライしても届かなければ NotifyException。 */
    public void send(String payloadJson) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.webhookUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();
        for (int attempt = 0; ; attempt++) {
            Duration wait = Duration.ofSeconds(1L << Math.min(attempt, 4));
            String reason;
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = res.statusCode();
                if (status / 100 == 2) {
                    return;
                }
                if (status != 429 && status < 500) {
                    throw new NotifyException("Discord が " + status + " を返しました: " + res.body());
                }
                reason = "HTTP " + status;
                if (status == 429) {
                    wait = retryAfter(res.body()).orElse(wait);
                }
            } catch (IOException e) {
                reason = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NotifyException("Discord への送信中に割り込まれました", e);
            }
            if (attempt >= props.maxRetries()) {
                throw new NotifyException("Discord への送信が " + (attempt + 1) + " 回失敗しました (" + reason + ")");
            }
            if (wait.compareTo(MAX_WAIT) > 0) {
                wait = MAX_WAIT;
            }
            log.warn("Discord 送信失敗 ({})。{} ms 後にリトライ ({}/{})",
                    reason, wait.toMillis(), attempt + 1, props.maxRetries());
            sleep(wait);
        }
    }

    /** 429 のレスポンス本文 {"retry_after": 秒（小数）} から待ち時間を読む。 */
    private Optional<Duration> retryAfter(String body) {
        try {
            JsonNode v = mapper.readTree(body).get("retry_after");
            return v != null && v.isNumber()
                    ? Optional.of(Duration.ofMillis((long) Math.ceil(v.asDouble() * 1000)))
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotifyException("リトライ待機中に割り込まれました", e);
        }
    }
}
