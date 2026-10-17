package com.example.trade.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import org.springframework.stereotype.Component;

import com.example.trade.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ai-python の POST /analyze を呼ぶ（F-05）。
 * 足の確定から通知までの遅延を抑えるため、リトライはせず timeout で打ち切る。
 */
@Component
public class AiClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AiProperties props;

    public AiClient(HttpClient http, ObjectMapper mapper, AiProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.props = props;
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** 推論結果を返す。応答がない・エラー・形式不正なら AiException。 */
    public AnalyzeResponse analyze(AnalyzeRequest req) {
        HttpResponse<String> res;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(stripSlash(props.serviceUrl()) + "/analyze"))
                    .timeout(props.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(req)))
                    .build();
            res = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AiException("AI サービスが " + props.timeout().toMillis() + " ms 以内に応答しませんでした", e);
        } catch (IOException e) {
            throw new AiException("AI サービスに接続できません: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("AI サービスの応答待ち中に割り込まれました", e);
        }
        if (res.statusCode() / 100 != 2) {
            throw new AiException("AI サービスが " + res.statusCode() + " を返しました: " + res.body());
        }
        try {
            return mapper.readValue(res.body(), AnalyzeResponse.class);
        } catch (IOException e) {
            throw new AiException("AI サービスの応答を読めません: " + res.body(), e);
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
