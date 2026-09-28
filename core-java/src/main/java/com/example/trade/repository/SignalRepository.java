package com.example.trade.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.trade.domain.Signal;
import com.example.trade.domain.Timeframe;

@Repository
public class SignalRepository {

    private static final String INSERT = """
            INSERT INTO signals (symbol, timeframe, open_time, rule_hit, probability, signal,
                                 entry_price, take_profit, stop_loss, risk_reward, confidence, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, timeframe, open_time) DO NOTHING
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public SignalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 保存して id 付きで返す。同じ足のシグナルが既にあれば保存せず null を返す
     * （再起動や REST 同期で同じ足を再評価したときに二重登録・二重通知しないため）。
     */
    public Signal insertIfAbsent(Signal s) {
        Long id = jdbc.query(INSERT, ps -> {
            ps.setString(1, s.symbol());
            ps.setString(2, s.timeframe().code());
            ps.setLong(3, s.openTime());
            ps.setString(4, s.ruleHit());
            setNullable(ps, 5, s.probability());
            ps.setString(6, s.signal());
            setNullable(ps, 7, s.entryPrice());
            setNullable(ps, 8, s.takeProfit());
            setNullable(ps, 9, s.stopLoss());
            setNullable(ps, 10, s.riskReward());
            ps.setString(11, s.confidence());
            ps.setLong(12, s.createdAt());
        }, rs -> rs.next() ? rs.getLong(1) : null);
        return id == null ? null : s.withId(id);
    }

    public long count(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT COUNT(*) FROM signals WHERE symbol = ? AND timeframe = ?",
                Long.class, symbol, tf.code());
        return v == null ? 0 : v;
    }

    private static void setNullable(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }
}
