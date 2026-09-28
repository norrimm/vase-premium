package com.example.trade.monitor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.trade.collector.CandlesStoredEvent;
import com.example.trade.config.NotifyProperties;
import com.example.trade.domain.MarketSnapshot;
import com.example.trade.indicator.FeatureService;
import com.example.trade.monitor.RuleEngine.Evaluation;
import com.example.trade.notify.SignalNotifier;

/**
 * 足が保存されるたびに指標を更新し、最新の確定足をルールで評価してログに残す。
 * 一致したら通知に回す。起動時のバックフィルなどでまとめて入った過去の足は、
 * 件数と直近の一致だけをログに出す（通知はしない）。
 */
@Component
public class CandleClosedListener {

    private static final Logger log = LoggerFactory.getLogger(CandleClosedListener.class);
    private static final int HISTORY_HITS_TO_LOG = 10;

    private final FeatureService featureService;
    private final RuleEngine ruleEngine;
    private final SignalNotifier signalNotifier;

    private long lastEvaluatedOpenTime = Long.MIN_VALUE;
    private boolean testPending;

    public CandleClosedListener(FeatureService featureService, RuleEngine ruleEngine,
            SignalNotifier signalNotifier, NotifyProperties notifyProps) {
        this.featureService = featureService;
        this.ruleEngine = ruleEngine;
        this.signalNotifier = signalNotifier;
        this.testPending = notifyProps.discord().sendTestOnStartup();
    }

    @EventListener
    public synchronized void onCandlesStored(CandlesStoredEvent event) {
        try {
            process(event);
        } catch (RuntimeException e) {
            // 足の保存側（WebSocket / REST 同期）を止めないよう、ここで止める。次の足で再計算される
            log.error("指標の計算・ルール評価に失敗しました", e);
        }
    }

    private void process(CandlesStoredEvent event) {
        List<MarketSnapshot> snapshots = featureService.update(event.symbol(), event.timeframe(),
                event.fromOpenTime());
        if (snapshots.isEmpty()) {
            return;
        }

        List<MarketSnapshot> history = snapshots.subList(0, snapshots.size() - 1);
        if (history.size() > 1) {
            logHistory(history);
        }

        MarketSnapshot latest = snapshots.get(snapshots.size() - 1);
        if (latest.candle().openTime() > lastEvaluatedOpenTime) {
            lastEvaluatedOpenTime = latest.candle().openTime();
            Evaluation e = ruleEngine.evaluate(latest);
            if (e.hit()) {
                log.info("候補検知 [{}] {}", e.ruleHit(), e.describe());
                signalNotifier.onCandidate(e);
            } else {
                log.info("評価 {}", e.describe());
            }
            if (testPending) {
                testPending = false;
                signalNotifier.sendTest(e);
            }
        }
    }

    private void logHistory(List<MarketSnapshot> history) {
        List<Evaluation> hits = history.stream().map(ruleEngine::evaluate).filter(Evaluation::hit).toList();
        log.info("過去の足 {} 本を評価: 条件一致 {} 件", history.size(), hits.size());
        for (Evaluation e : hits.subList(Math.max(0, hits.size() - HISTORY_HITS_TO_LOG), hits.size())) {
            log.info("  過去の一致 {}", e.describe());
        }
    }
}
