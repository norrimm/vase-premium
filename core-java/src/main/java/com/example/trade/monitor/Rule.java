package com.example.trade.monitor;

import com.example.trade.domain.MarketSnapshot;

/** 検知条件 1 つ。指標がまだ計算できていない足では不成立を返す。 */
public interface Rule {

    RuleCheck check(MarketSnapshot snapshot);

    /**
     * 条件 1 つの判定結果。
     *
     * @param name   条件名（signals.rule_hit に入る名前。例: rsi_low）
     * @param passed 成立したか
     * @param detail 判定に使った値（例: "RSI=28.4 < 35"）
     */
    record RuleCheck(String name, boolean passed, String detail) {
    }
}
