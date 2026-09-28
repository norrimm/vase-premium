package com.example.trade.notify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageBuilderTest {

    @Test
    void splitsQuoteCurrency() {
        assertThat(MessageBuilder.displaySymbol("BTCUSDT")).isEqualTo("BTC/USDT");
        assertThat(MessageBuilder.displaySymbol("ETHBTC")).isEqualTo("ETH/BTC");
        assertThat(MessageBuilder.displaySymbol("BTCJPY")).isEqualTo("BTC/JPY");
        assertThat(MessageBuilder.displaySymbol("XYZ")).isEqualTo("XYZ");
    }
}
