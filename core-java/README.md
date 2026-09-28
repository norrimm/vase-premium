# core-java（データ取得）

MVP 要件定義の F-01 / F-02 にあたる部分。取引所（今は Binance）から足を取得して SQLite に貯める。

```
起動
 ├─ REST でバックフィル（過去 BACKFILL_DAYS 日分、1000 本ずつ）
 ├─ WebSocket で確定足をリアルタイム受信 → candles に保存
 └─ 60 秒ごとに REST で欠損を補完（WebSocket が切れていた間の取りこぼし対策）
```

## 起動

JDK 21 が必要（`winget install EclipseAdoptium.Temurin.21.JDK`）。

```powershell
cd core-java
.\gradlew.bat bootRun      # 起動
.\gradlew.bat test         # テスト
```

公開マーケットデータだけを使うので、API キーは不要。

## 設定（環境変数）

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `SYMBOL` | `BTCUSDT` | 監視する銘柄 |
| `TIMEFRAME` | `5m` | 足の種類（`1m` `15m` `1h` `1d` など Binance の interval 表記） |
| `BACKFILL_DAYS` | `365` | 起動時にさかのぼる日数 |
| `DB_PATH` | `../data/trade.db` | SQLite ファイル |
| `LOG_PATH` | `../logs` | ログ出力先 |

REST・WebSocket の接続先やリトライ回数などは `src/main/resources/application.yml` の `market.*`。

## 構成

| クラス | 役割 |
| --- | --- |
| `collector/ExchangeClient` | REST で足を取る口。取引所を増やすときはこれを実装する |
| `collector/CandleStream` | リアルタイム配信の口。確定足だけを通知する |
| `collector/BinanceRestClient` | `GET /api/v3/klines`。429 / 418 / 5xx は指数バックオフでリトライ |
| `collector/BinanceWebSocketClient` | `<symbol>@kline_<interval>` を購読。切断・無通信時に自動再接続 |
| `collector/CandleBackfillService` | DB を「現在まで欠損なし」にする。途中の欠損も検出して埋める |
| `collector/CollectorRunner` | 起動順の制御 |
| `repository/CandleRepository` | `candles` テーブルへの upsert と欠損検出 |

## 注意

- 日本語を含むパスでテストを動かすために、`gradle.properties` で Gradle を OS の文字コードで動かしている（ソースのコンパイルは UTF-8 固定）。消すとテストが `ClassNotFoundException` で落ちる。
