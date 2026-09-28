package com.example.trade.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.trade.domain.Timeframe;

/**
 * 監視対象と取得元の設定。application.yml の market.* にバインドされる。
 *
 * @param symbol       監視する銘柄（例: BTCUSDT）
 * @param timeframe    足の種類（例: 5m）
 * @param backfillDays 起動時にさかのぼって取得する日数
 * @param syncInterval REST で欠損を埋める定期同期の間隔
 * @param binance      Binance の接続先
 */
@ConfigurationProperties(prefix = "market")
public record MarketProperties(
        String symbol,
        String timeframe,
        int backfillDays,
        Duration syncInterval,
        Binance binance) {

    public Timeframe parsedTimeframe() {
        return Timeframe.parse(timeframe);
    }

    /**
     * @param restBaseUrl       REST API のベース URL
     * @param wsBaseUrl         WebSocket ストリームのベース URL
     * @param requestPause      バックフィル時のページ間の待ち時間（レート制限対策）
     * @param maxRetries        REST 失敗時の最大リトライ回数
     * @param streamIdleTimeout この時間メッセージが来なければ WebSocket を張り直す
     */
    public record Binance(
            String restBaseUrl,
            String wsBaseUrl,
            Duration requestPause,
            int maxRetries,
            Duration streamIdleTimeout) {
    }
}
