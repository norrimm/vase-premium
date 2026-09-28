package com.example.trade.collector;

import java.util.List;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;

/**
 * 取引所の REST API から足を取得する。取引所ごとに実装する（Binance / Bybit / Coinbase ...）。
 */
public interface ExchangeClient {

    /** 取引所の名前（ログ用）。 */
    String name();

    /**
     * 取引所のサーバー時刻（epoch ms）。足が確定したかはこの時刻で判定する。
     * PC の時計がずれていると、形成中の足を確定足として保存してしまうため。
     */
    long serverTime();

    /** 1 回のリクエストで取得できる最大本数。 */
    int maxLimit();

    /**
     * startTime（epoch ms）以降の足を古い順に最大 limit 本返す。
     * 末尾には未確定の足が含まれることがある。
     */
    List<Candle> fetchCandles(String symbol, Timeframe tf, long startTime, int limit);
}
