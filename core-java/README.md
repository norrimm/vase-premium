# core-java（データ取得・監視）

MVP 要件定義の F-01〜F-04 にあたる部分。取引所（今は Binance）から足を取得して SQLite に貯め、
確定足ごとに指標を計算して検知ルールで評価し、結果をログに出す。

```
起動
 ├─ REST でバックフィル（過去 BACKFILL_DAYS 日分、1000 本ずつ）
 ├─ WebSocket で確定足をリアルタイム受信 → candles に保存
 └─ 60 秒ごとに REST で欠損を補完（WebSocket が切れていた間の取りこぼし対策）

足が保存されるたびに
 ├─ 指標を計算 → features に保存（RSI14 / MACD 12,26,9 / SMA20・50 / SMA20 乖離率 / 出来高MA20 比 / ATR14）
 └─ 最新の確定足を検知ルールで評価 → ログに「候補検知」または「評価」を 1 行
    （起動時にまとめて入った過去の足は、一致件数と直近 10 件だけを出す）
```

ログの例:

```
候補検知 [rsi_low,above_sma50,sma20_deviation,volume_spike] BTCUSDT 5m 2026-09-05 15:30 JST 終値=... | ○rsi_low(RSI=28.40 < 35.00) ○above_sma50(...) ...
評価 BTCUSDT 5m 2026-09-29 01:05 JST 終値=83298.01 | ×rsi_low(RSI=48.12 < 35.00) ○above_sma50(...) ...
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
検知ルールの閾値は `src/main/resources/rules.yml`。

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
| `indicator/IndicatorCalculator` | 足の列から指標を計算（`Rsi` `Macd` `MovingAverage` `Atr`） |
| `indicator/FeatureService` | 新しい足と未計算の足の指標を計算して `features` に保存 |
| `monitor/RuleEngine` | `rules.yml` の条件で評価。条件ごとの成否と値を返す |
| `monitor/CandleClosedListener` | 足の保存イベントを受けて指標更新 → 評価 → ログ |
| `repository/FeatureRepository` | `features` テーブルへの upsert |

## 注意

- 指標の差分計算では対象の 500 本前から読み直す。EMA / RSI / ATR は過去の影響が指数的に消えるので、全履歴から計算した値と実質同じになる（`FeatureServiceTest` で確認）。
- 出来高MA(20) は当該足を含む 20 本の平均。
- 日本語を含むパスでテストを動かすために、`gradle.properties` で Gradle を OS の文字コードで動かしている（ソースのコンパイルは UTF-8 固定）。消すとテストが `ClassNotFoundException` で落ちる。
