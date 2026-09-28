package com.example.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 検知ルールの閾値。rules.yml の rules.* にバインドされる。 */
@ConfigurationProperties(prefix = "rules")
public record RuleProperties(BuyDip buyDip) {

    /**
     * 押し目買い候補の条件。すべて満たしたときに候補とする。
     *
     * @param rsiBelow               RSI(14) がこの値より小さい
     * @param requireCloseAboveSma50 true なら終値が SMA(50) より上であること
     * @param sma20DeviationAtMost   SMA(20) 乖離率（%）がこの値以下
     * @param volumeRatioAbove       出来高 ÷ 出来高MA(20) がこの値より大きい
     */
    public record BuyDip(
            double rsiBelow,
            boolean requireCloseAboveSma50,
            double sma20DeviationAtMost,
            double volumeRatioAbove) {
    }
}
