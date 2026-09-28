package com.example.trade.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;
import com.fasterxml.jackson.databind.ObjectMapper;

class BinanceParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Timeframe m5 = Timeframe.parse("5m");

    @Test
    void parsesRestKlines() throws Exception {
        String body = """
                [[1790609400000,"83068.00000000","83082.01000000","83020.00000000","83048.00000000",
                  "7.57438000",1790609699999,"629109.75",3106,"4.30","357243.80","0"]]
                """;

        List<Candle> candles = BinanceRestClient.parseKlines(mapper.readTree(body), "BTCUSDT", m5);

        assertThat(candles).containsExactly(new Candle("BTCUSDT", m5, 1790609400000L,
                83068.0, 83082.01, 83020.0, 83048.0, 7.57438));
    }

    @Test
    void parsesClosedKlineEvent() throws Exception {
        Optional<Candle> candle = BinanceWebSocketClient.parseClosedKline(mapper.readTree(klineEvent(true)), m5);

        assertThat(candle).contains(new Candle("BTCUSDT", m5, 1790609400000L,
                83068.0, 83082.01, 83020.0, 83048.0, 7.57438));
    }

    @Test
    void ignoresUnclosedKlineEvent() throws Exception {
        assertThat(BinanceWebSocketClient.parseClosedKline(mapper.readTree(klineEvent(false)), m5)).isEmpty();
    }

    private static String klineEvent(boolean closed) {
        return """
                {"e":"kline","E":1790609700001,"s":"BTCUSDT","k":{
                  "t":1790609400000,"T":1790609699999,"s":"BTCUSDT","i":"5m",
                  "o":"83068.00","c":"83048.00","h":"83082.01","l":"83020.00","v":"7.57438",
                  "x":%s}}
                """.formatted(closed);
    }
}
