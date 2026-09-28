package com.example.trade.monitor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.trade.config.RuleProperties;
import com.example.trade.domain.Feature;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.monitor.Rule.RuleCheck;

/** rules.yml の条件で確定足を評価する（F-04）。 */
@Component
public class RuleEngine {

    private static final DateTimeFormatter JST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Tokyo"));

    private final List<Rule> rules;

    public RuleEngine(RuleProperties props) {
        this.rules = buyDipRules(props.buyDip());
    }

    public Evaluation evaluate(MarketSnapshot snapshot) {
        return new Evaluation(snapshot, rules.stream().map(r -> r.check(snapshot)).toList());
    }

    private static List<Rule> buyDipRules(RuleProperties.BuyDip p) {
        List<Rule> rules = new ArrayList<>();
        rules.add(s -> {
            Double rsi = s.feature().rsi14();
            return new RuleCheck("rsi_low", rsi != null && rsi < p.rsiBelow(),
                    "RSI=" + fmt(rsi) + " < " + fmt(p.rsiBelow()));
        });
        if (p.requireCloseAboveSma50()) {
            rules.add(s -> {
                Double sma50 = s.feature().sma50();
                double close = s.candle().close();
                return new RuleCheck("above_sma50", sma50 != null && close > sma50,
                        "終値=" + fmt(close) + " > SMA50=" + fmt(sma50));
            });
        }
        rules.add(s -> {
            Double dev = s.feature().devSma20();
            return new RuleCheck("sma20_deviation", dev != null && dev <= p.sma20DeviationAtMost(),
                    "SMA20乖離=" + fmt(dev) + "% <= " + fmt(p.sma20DeviationAtMost()) + "%");
        });
        rules.add(s -> {
            Double ratio = s.feature().volRatio();
            return new RuleCheck("volume_spike", ratio != null && ratio > p.volumeRatioAbove(),
                    "出来高比=" + fmt(ratio) + "倍 > " + fmt(p.volumeRatioAbove()) + "倍");
        });
        return List.copyOf(rules);
    }

    private static String fmt(Double v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.2f", v);
    }

    /** 1 本分の評価結果。 */
    public record Evaluation(MarketSnapshot snapshot, List<RuleCheck> checks) {

        /** すべての条件が成立したか。 */
        public boolean hit() {
            return checks.stream().allMatch(RuleCheck::passed);
        }

        /** 成立した条件名のカンマ区切り（signals.rule_hit 用）。 */
        public String ruleHit() {
            return checks.stream().filter(RuleCheck::passed).map(RuleCheck::name)
                    .collect(Collectors.joining(","));
        }

        /** ログ用の 1 行表現。例: "BTCUSDT 5m 2026-09-29 01:00 JST 終値=83298.01 | ○rsi_low(RSI=28.40 < 35.00) ×..." */
        public String describe() {
            Feature f = snapshot.feature();
            String checksText = checks.stream()
                    .map(c -> (c.passed() ? "○" : "×") + c.name() + "(" + c.detail() + ")")
                    .collect(Collectors.joining(" "));
            return f.symbol() + " " + f.timeframe() + " " + JST.format(Instant.ofEpochMilli(f.openTime()))
                    + " JST 終値=" + fmt(snapshot.candle().close()) + " | " + checksText;
        }
    }
}
