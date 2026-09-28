package com.example.trade.indicator;

/** MACD = EMA(fast) - EMA(slow)、シグナル = MACD の EMA(signal)、ヒストグラム = MACD - シグナル。 */
public final class Macd {

    public record Result(double[] macd, double[] signal, double[] hist) {
    }

    private Macd() {
    }

    public static Result compute(double[] close, int fast, int slow, int signalPeriod) {
        double[] emaFast = MovingAverage.ema(close, fast);
        double[] emaSlow = MovingAverage.ema(close, slow);
        double[] macd = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            macd[i] = emaFast[i] - emaSlow[i];
        }
        double[] signal = MovingAverage.ema(macd, signalPeriod);
        double[] hist = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            hist[i] = macd[i] - signal[i];
        }
        return new Result(macd, signal, hist);
    }
}
