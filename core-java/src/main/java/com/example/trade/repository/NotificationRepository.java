package com.example.trade.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    public static final String SENT = "sent";
    public static final String FAILED = "failed";

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param signalId テスト通知など、シグナルに紐づかない通知では null */
    public void insert(Long signalId, String channel, String status, long sentAt, String payload) {
        jdbc.update("INSERT INTO notifications (signal_id, channel, status, sent_at, payload) VALUES (?, ?, ?, ?, ?)",
                signalId, channel, status, sentAt, payload);
    }

    /** 同一銘柄・同一方向のシグナルを最後に送れた時刻（epoch ms）。 */
    public Optional<Long> findLastSentAt(String symbol, String signal) {
        Long v = jdbc.queryForObject("""
                SELECT MAX(n.sent_at) FROM notifications n JOIN signals s ON s.id = n.signal_id
                WHERE s.symbol = ? AND s.signal = ? AND n.status = ?
                """, Long.class, symbol, signal, SENT);
        return Optional.ofNullable(v);
    }

    public long count(String status) {
        Long v = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE status = ?", Long.class, status);
        return v == null ? 0 : v;
    }
}
