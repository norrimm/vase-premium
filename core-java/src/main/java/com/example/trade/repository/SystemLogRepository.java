package com.example.trade.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemLogRepository {

    public static final String ERROR = "ERROR";
    public static final String INFO = "INFO";

    private final JdbcTemplate jdbc;

    public SystemLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String level, String source, String message, long createdAt) {
        jdbc.update("INSERT INTO system_log (level, source, message, created_at) VALUES (?, ?, ?, ?)",
                level, source, message, createdAt);
    }

    public long count(String level, String source) {
        Long v = jdbc.queryForObject("SELECT COUNT(*) FROM system_log WHERE level = ? AND source = ?",
                Long.class, level, source);
        return v == null ? 0 : v;
    }
}
