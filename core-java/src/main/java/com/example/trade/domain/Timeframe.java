package com.example.trade.domain;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "5m" / "1h" / "1d" 形式のタイムフレーム。表記は Binance の interval と同じ。 */
public record Timeframe(String code, Duration duration) {

    private static final Pattern FORMAT = Pattern.compile("(\\d+)([mhdw])");

    public static Timeframe parse(String code) {
        Matcher m = FORMAT.matcher(code);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported timeframe: " + code);
        }
        long n = Long.parseLong(m.group(1));
        Duration d = switch (m.group(2)) {
            case "m" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "d" -> Duration.ofDays(n);
            case "w" -> Duration.ofDays(7 * n);
            default -> throw new IllegalStateException();
        };
        return new Timeframe(code, d);
    }

    public long millis() {
        return duration.toMillis();
    }

    @Override
    public String toString() {
        return code;
    }
}
