package com.example.trade.indicator;

/** ATR（Wilder の平滑化）。値が計算できない位置は NaN。 */
public final class Atr {

    private Atr() {
    }

    public static double[] compute(double[] high, double[] low, double[] close, int n) {
        double[] out = MovingAverage.nans(close.length);
        if (close.length <= n) {
            return out;
        }
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += trueRange(high[i], low[i], close[i - 1]);
        }
        out[n] = sum / n;
        for (int i = n + 1; i < close.length; i++) {
            out[i] = (out[i - 1] * (n - 1) + trueRange(high[i], low[i], close[i - 1])) / n;
        }
        return out;
    }

    private static double trueRange(double high, double low, double prevClose) {
        return Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
    }
}
