package com.example.trade.domain;

/** 確定足とその指標の組。ルール評価の入力になる。 */
public record MarketSnapshot(Candle candle, Feature feature) {
}
