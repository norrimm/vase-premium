package com.example.trade.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI サービス（ai-python）の設定。application.yml の ai.* にバインドされる。
 *
 * @param serviceUrl ai-python のベース URL。空なら AI を使わず、ルール一致だけで通知する
 * @param timeout    /analyze の応答待ち時間（F-05: 3 秒以内）
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String serviceUrl, Duration timeout) {

    public boolean enabled() {
        return serviceUrl != null && !serviceUrl.isBlank();
    }
}
