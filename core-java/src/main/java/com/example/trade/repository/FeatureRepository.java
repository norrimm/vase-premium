package com.example.trade.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.trade.domain.Feature;
import com.example.trade.domain.Timeframe;

@Repository
public class FeatureRepository {

    private static final String UPSERT = """
            INSERT INTO features (symbol, timeframe, open_time, rsi14, macd, macd_signal, macd_hist,
                                  sma20, sma50, dev_sma20, vol_ma20, vol_ratio, atr14)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, timeframe, open_time) DO UPDATE SET
              rsi14 = excluded.rsi14, macd = excluded.macd, macd_signal = excluded.macd_signal,
              macd_hist = excluded.macd_hist, sma20 = excluded.sma20, sma50 = excluded.sma50,
              dev_sma20 = excluded.dev_sma20, vol_ma20 = excluded.vol_ma20,
              vol_ratio = excluded.vol_ratio, atr14 = excluded.atr14
            """;

    private final JdbcTemplate jdbc;

    public FeatureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsertAll(List<Feature> features) {
        jdbc.batchUpdate(UPSERT, features, 500, (ps, f) -> {
            ps.setString(1, f.symbol());
            ps.setString(2, f.timeframe().code());
            ps.setLong(3, f.openTime());
            setNullable(ps, 4, f.rsi14());
            setNullable(ps, 5, f.macd());
            setNullable(ps, 6, f.macdSignal());
            setNullable(ps, 7, f.macdHist());
            setNullable(ps, 8, f.sma20());
            setNullable(ps, 9, f.sma50());
            setNullable(ps, 10, f.devSma20());
            setNullable(ps, 11, f.volMa20());
            setNullable(ps, 12, f.volRatio());
            setNullable(ps, 13, f.atr14());
        });
    }

    public Optional<Long> findLatestOpenTime(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT MAX(open_time) FROM features WHERE symbol = ? AND timeframe = ?",
                Long.class, symbol, tf.code());
        return Optional.ofNullable(v);
    }

    public long count(String symbol, Timeframe tf) {
        Long v = jdbc.queryForObject(
                "SELECT COUNT(*) FROM features WHERE symbol = ? AND timeframe = ?",
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
