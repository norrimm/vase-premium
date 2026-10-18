# vase-premium

BTC/USDT の 5 分足を監視して、押し目・反発の買い目が来たら Discord に通知する（`資料置き場/MVP要件定義・構成案（AIトレードシグナル通知）.md`）。

| ディレクトリ | 役割 | 機能要件 |
| --- | --- | --- |
| [`core-java/`](core-java/README.md) | 足の取得・指標計算・ルール検知・通知・障害通知 | F-01〜F-04, F-06〜F-08, F-10 |
| [`ai-python/`](ai-python/README.md) | 反発確率の推論 API・学習・バックテスト | F-05, F-09 |

## 構成図

```
Binance
  │ REST バックフィル（起動時） / WebSocket（確定足をリアルタイム受信）
  ▼
core-java（指標計算・ルール検知） ── candles/features/signals を保存 ──▶ trade.db（SQLite）
  │                                                                          ▲
  │ 候補検知時 POST /analyze {symbol, timeframe, open_time, rule_hit}        │ 読み取り専用で
  ▼                                                                          │ 特徴量を作って推論
ai-python（学習・バックテストも兼ねる） ◀─────────────────────────────────────┘
  │
  │ {probability, signal, entry/take_profit/stop_loss, ...}
  ▼
core-java（通知可否判定：AI 判定 / Webhook 未設定 / クールダウン などを確認）
  │ Discord Webhook
  ▼
Discord（シグナル通知／障害通知）
```

起動の流れ: `.env.example` を `.env` にコピー → core-java を起動して足を貯める → ai-python で学習して API を起動 → core-java が AI を使って通知する。
