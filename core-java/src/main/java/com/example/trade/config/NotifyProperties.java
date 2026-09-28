package com.example.trade.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知の設定。application.yml の notify.* にバインドされる。
 *
 * @param cooldown 同一銘柄・同一方向のシグナルを再通知しない時間
 * @param maxDelay 足の確定からこれ以上たった候補は通知しない
 * @param discord  Discord Webhook の設定
 */
@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(
        Duration cooldown,
        Duration maxDelay,
        Discord discord) {

    /**
     * @param webhookUrl        Webhook URL。空なら通知しない（環境変数 DISCORD_WEBHOOK_URL で渡す）
     * @param maxRetries        送信失敗時の最大リトライ回数
     * @param sendTestOnStartup true なら起動後最初の評価結果をテスト通知として送る
     */
    public record Discord(
            String webhookUrl,
            int maxRetries,
            boolean sendTestOnStartup) {

        public boolean enabled() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }
}
