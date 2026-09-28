package com.example.trade.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.trade.domain.Candle;
import com.example.trade.domain.Timeframe;

@Repository
public class CandleRepository {

    private static final String UPSERT = """
            INSERT INTO candles (symbol, timeframe, open_time, open, high, low, close, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, timeframe, open_time) DO UPDATE SET
              open = excluded.open, high = excluded.high, low = excluded.low,
              close = excluded.close, volume = excluded.volume
            """;

    private final JdbcTemplate jdbc;

    public CandleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(Candle c) {
        upsertAll(List.of(c));
    }

    @Transactional
    public void upsertAll(List<Candle> candles) {
        jdbc.batchUpdate(UPSERT, candles, 500, (ps, c) -> {
            ps.setString(1, c.symbol());
            ps.setString(2, c.timeframe().code());
            ps.setLong(3, c.openTime());
            ps.setDouble(4, c.open());
            ps.setDouble(5, c.high());
            ps.setDouble(6, c.low());
            ps.setDouble(7, c.close());
            ps.setDouble(8, c.volume());
        });
    }

    /** fromOpenTime 以降の足を古い順に返す。 */
    public List<Candle> findFrom(String symbol, Timeframe tf, long fromOpenTime) {
        return jdbc.query("""
                SELECT open_time, open, high, low, close, volume FROM candles
                WHERE symbol = ? AND timeframe = ? AND open_time >= ?
                ORDER BY open_time
                """, (rs, i) -> new Candle(symbol, tf,
                        rs.getLong("open_time"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getDouble("volume")),
                symbol, tf.code(), fromOpenTime);
    }

    public Optional<Long> findEarliestOpenTime(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT MIN(open_time) FROM candles WHERE symbol = ? AND timeframe = ?",
                Long.class, symbol, tf.code());
        return Optional.ofNullable(v);
    }

    public Optional<Long> findLatestOpenTime(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT MAX(open_time) FROM candles WHERE symbol = ? AND timeframe = ?",
                Long.class, symbol, tf.code());
        return Optional.ofNullable(v);
    }

    /**
     * since 以降で「次の足が無い」最初の足について、その次の足の open_time を返す。
     * 途中に欠損があれば欠損の先頭、欠損が無ければ最新足の次の足になる。
     * since 以降に足が 1 本も無ければ空。
     */
    public Optional<Long> findFirstMissingOpenTime(String symbol, Timeframe tf, long since) {
        Long v = jdbc.queryForObject("""
                SELECT MIN(c.open_time) + ?
                FROM candles c
                WHERE c.symbol = ? AND c.timeframe = ? AND c.open_time >= ?
                  AND NOT EXISTS (
                    SELECT 1 FROM candles n
                    WHERE n.symbol = c.symbol AND n.timeframe = c.timeframe
                      AND n.open_time = c.open_time + ?)
                """, Long.class, tf.millis(), symbol, tf.code(), since, tf.millis());
        return Optional.ofNullable(v);
    }

    public long count(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT COUNT(*) FROM candles WHERE symbol = ? AND timeframe = ?",
                Long.class, symbol, tf.code());
        return v == null ? 0 : v;
    }
}
