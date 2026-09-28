package com.example.trade.collector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.trade.config.MarketProperties;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Binance Spot の公開 REST API（GET /api/v3/klines）。API キー不要。
 * 429 / 418 / 5xx / 通信エラーは指数バックオフでリトライする。
 */
@Component
public class BinanceRestClient implements ExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceRestClient.class);
    private static final int MAX_LIMIT = 1000;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final MarketProperties.Binance props;

    public BinanceRestClient(HttpClient http, ObjectMapper mapper, MarketProperties market) {
        this.http = http;
        this.mapper = mapper;
        this.props = market.binance();
    }

    @Override
    public String name() {
        return "binance";
    }

    @Override
    public long serverTime() {
        URI uri = URI.create(props.restBaseUrl() + "/api/v3/time");
        try {
            return mapper.readTree(getWithRetry(uri)).get("serverTime").asLong();
        } catch (JsonProcessingException e) {
            throw new ExchangeException("Binance time のレスポンスを解析できません", e);
        }
    }

    @Override
    public int maxLimit() {
        return MAX_LIMIT;
    }

    @Override
    public List<Candle> fetchCandles(String symbol, Timeframe tf, long startTime, int limit) {
        URI uri = URI.create(props.restBaseUrl() + "/api/v3/klines"
                + "?symbol=" + symbol
                + "&interval=" + tf.code()
                + "&startTime=" + startTime
                + "&limit=" + Math.min(limit, MAX_LIMIT));
        String body = getWithRetry(uri);
        try {
            return parseKlines(mapper.readTree(body), symbol, tf);
        } catch (JsonProcessingException e) {
            throw new ExchangeException("Binance klines のレスポンスを解析できません: " + uri, e);
        }
    }

    /** klines のレスポンス（配列の配列）を Candle に変換する。 */
    static List<Candle> parseKlines(JsonNode root, String symbol, Timeframe tf) {
        List<Candle> candles = new ArrayList<>(root.size());
        for (JsonNode k : root) {
            candles.add(new Candle(symbol, tf,
                    k.get(0).asLong(),
                    k.get(1).asDouble(),
                    k.get(2).asDouble(),
                    k.get(3).asDouble(),
                    k.get(4).asDouble(),
                    k.get(5).asDouble()));
        }
        return candles;
    }

    private String getWithRetry(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        for (int attempt = 0; ; attempt++) {
            Duration wait = backoff(attempt);
            String reason;
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = res.statusCode();
                if (status == 200) {
                    return res.body();
                }
                if (status != 429 && status != 418 && status < 500) {
                    throw new ExchangeException("Binance が " + status + " を返しました: " + res.body());
                }
                // 429 = レート制限、418 = 制限を無視し続けたための一時 BAN。Retry-After に従う
                reason = "HTTP " + status;
                wait = res.headers().firstValueAsLong("Retry-After")
                        .stream().mapToObj(Duration::ofSeconds).findFirst().orElse(wait);
            } catch (IOException e) {
                reason = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExchangeException("Binance へのリクエスト中に割り込まれました", e);
            }
            if (attempt >= props.maxRetries()) {
                throw new ExchangeException("Binance へのリクエストが " + (attempt + 1) + " 回失敗しました ("
                        + reason + "): " + uri);
            }
            log.warn("Binance リクエスト失敗 ({})。{} 秒後にリトライ ({}/{})",
                    reason, wait.toSeconds(), attempt + 1, props.maxRetries());
            sleep(wait);
        }
    }

    private static Duration backoff(int attempt) {
        Duration d = Duration.ofSeconds(1L << Math.min(attempt, 6));
        return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeException("リトライ待機中に割り込まれました", e);
        }
    }
}
