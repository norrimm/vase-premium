package com.example.trade.indicator;

import java.util.Arrays;

/** 移動平均。値が計算できない位置は NaN。 */
public final class MovingAverage {

    private MovingAverage() {
    }

    /** 単純移動平均。i 番目は v[i-n+1..i] の平均。 */
    public static double[] sma(double[] v, int n) {
        double[] out = nans(v.length);
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            sum += v[i];
            if (i >= n) {
                sum -= v[i - n];
            }
            if (i >= n - 1) {
                out[i] = sum / n;
            }
        }
        return out;
    }

    /**
     * 指数移動平均（α = 2 / (n + 1)）。最初の n 本の単純平均を初期値にする。
     * 先頭の NaN は読み飛ばすので、MACD のように途中から値が始まる系列にも使える。
     */
    public static double[] ema(double[] v, int n) {
        double[] out = nans(v.length);
        int start = 0;
        while (start < v.length && Double.isNaN(v[start])) {
            start++;
        }
        int seed = start + n - 1;
        if (seed >= v.length) {
            return out;
        }
        double sum = 0;
        for (int i = start; i <= seed; i++) {
            sum += v[i];
        }
        out[seed] = sum / n;
        double alpha = 2.0 / (n + 1);
        for (int i = seed + 1; i < v.length; i++) {
            out[i] = alpha * v[i] + (1 - alpha) * out[i - 1];
        }
        return out;
    }

    static double[] nans(int length) {
        double[] out = new double[length];
        Arrays.fill(out, Double.NaN);
        return out;
    }
}
