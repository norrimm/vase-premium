package com.example.trade.collector;

import com.example.trade.domain.Timeframe;

/**
 * 確定足を DB に保存したことを知らせるイベント。
 * fromOpenTime 以降の足が新しく入った（または更新された）ことを表す。
 * REST 同期で新しい足が無かったときも、同期の完了通知として現在時刻付きで発行される。
 */
public record CandlesStoredEvent(String symbol, Timeframe timeframe, long fromOpenTime) {
}
