package com.example.trade.collector;

import java.util.OptionalLong;
import java.util.function.Consumer;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;

/**
 * 取引所のリアルタイム配信を購読し、確定足だけを通知する。
 * 切断時の再接続は実装側の責務。
 */
public interface CandleStream {

    void start(String symbol, Timeframe tf, Consumer<Candle> onClosedCandle);

    void stop();

    /** 配信が途切れていればその開始時刻（epoch ms）。受信できていれば空。障害通知の判定に使う。 */
    OptionalLong disconnectedSince();
}
