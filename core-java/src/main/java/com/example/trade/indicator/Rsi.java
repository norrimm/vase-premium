package com.example.trade.indicator;

/** RSI（Wilder の平滑化）。値が計算できない位置は NaN。 */
public final class Rsi {

    private Rsi() {
    }

    public static double[] compute(double[] close, int n) {
        double[] out = MovingAverage.nans(close.length);
        if (close.length <= n) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= n; i++) {
            double change = close[i] - close[i - 1];
            gain += Math.max(change, 0);
            loss += Math.max(-change, 0);
        }
        double avgGain = gain / n;
        double avgLoss = loss / n;
        out[n] = rsi(avgGain, avgLoss);
        for (int i = n + 1; i < close.length; i++) {
            double change = close[i] - close[i - 1];
            avgGain = (avgGain * (n - 1) + Math.max(change, 0)) / n;
            avgLoss = (avgLoss * (n - 1) + Math.max(-change, 0)) / n;
            out[i] = rsi(avgGain, avgLoss);
        }
        return out;
    }

    private static double rsi(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return avgGain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + avgGain / avgLoss);
    }
}
