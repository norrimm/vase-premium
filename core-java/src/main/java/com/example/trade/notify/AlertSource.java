package com.example.trade.notify;

/** 障害通知の発生箇所。code は system_log.source に入る。 */
public enum AlertSource {

    REST_SYNC("rest_sync", "足データ取得（REST）"),
    STREAM("stream", "リアルタイム受信（WebSocket）"),
    INDICATOR("indicator", "指標計算・ルール評価"),
    AI("ai", "AI 推論");

    private final String code;
    private final String label;

    AlertSource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
