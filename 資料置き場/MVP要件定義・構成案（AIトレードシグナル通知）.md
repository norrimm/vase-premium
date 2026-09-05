# MVP要件定義・構成案

フル要件（01〜08）のうち、**「BTC/USDT の 5 分足を監視して、押し目・反発の買い目が来たら Discord に通知する」** という一本の動線だけを最短で動かすための版。

**MVP のゴール**\: 24時間動き続け、条件に合致したら 1 分以内に Discord へシグナルが飛ぶ。その通知の精度をバックテストで数値として説明できる。

Table of Contents

* * *

## 1\. スコープ

### やること

| \# | 内容 |
| --- | --- |
| 1 | Binance から BTC/USDT の 5 分足を取得（REST 定期取得 \+ WebSocket） |
| 2 | SQLite に OHLCV を保存 |
| 3 | RSI / MACD / 移動平均乖離 / 出来高比 を計算 |
| 4 | ルール条件で「候補」を検知 |
| 5 | Python に投げて AI 反発確率を取得 |
| 6 | 閾値超えなら Discord Webhook に通知 |
| 7 | 通知履歴を DB に保存（重複通知の抑止） |
| 8 | CLI でバックテストを回して勝率 / PF を出す |

### やらないこと（MVP では捨てる）

- 自動売買・発注（**最優先で捨てる。資金リスクを持たない**）
- 複数銘柄・複数タイムフレーム（BTC/USDT・5 分足のみ）
- 株式 / ニュース / SNS / オンチェーンデータ
- PostgreSQL 移行、Discord Bot コマンド、チャート画像添付
- Web UI・ダッシュボード
- LSTM / Transformer、ハイパーパラメータ自動探索

* * *

## 2\. 機能要件

| ID | 機能 | 内容 | 完了条件 |
| --- | --- | --- | --- |
| F\-01 | 足データ取得 | Binance REST で 5 分足を取得。起動時に過去 1 年分をバックフィル | DB に欠損なく蓄積される |
| F\-02 | リアルタイム受信 | WebSocket で kline を購読、確定足のみ DB へ | 切断時に自動再接続する |
| F\-03 | 指標計算 | RSI(14) / MACD(12,26,9) / SMA(20,50) 乖離率 / 出来高 MA(20) 比 / ATR(14) | 確定足ごとに 1 行生成 |
| F\-04 | 条件検知 | 下記「検知ルール」に一致した足を候補として抽出 | ログに検知理由が残る |
| F\-05 | AI 推論 | Python へ HTTP リクエスト、反発確率とシグナルを受け取る | 3 秒以内に応答 |
| F\-06 | 通知 | 確率が閾値以上なら Discord Webhook に送信 | Discord にメッセージが届く |
| F\-07 | 重複抑止 | 同一銘柄・同一方向は 60 分以内に再通知しない | 連投されない |
| F\-08 | 履歴保存 | シグナルと通知を DB に保存 | 後から検証できる |
| F\-09 | バックテスト | 過去データでルール \+ AI を再現、成績を出力 | 勝率 / PF / 最大 DD が CSV に出る |
| F\-10 | 障害通知 | 取得失敗・API エラー・推論失敗を Discord に通知 | エラー時に気づける |

### 検知ルール（MVP の初期値）

以下を**すべて**満たした確定足を候補とする。

```
RSI(14) < 35
かつ 終値 > SMA(50)                （中期は上昇トレンド内）
かつ 終値 が SMA(20) から -1.5% 以上乖離   （短期の押し目）
かつ 出来高 > 出来高MA(20) × 1.5
```

数値は設定ファイルで変更できるようにする（ハードコードしない）。

### AI モデル（MVP の初期値）

- モデル: **LightGBM（二値分類）** 1 本のみ
- 教師ラベル: 検知足から **12 本先（\= 1 時間後）までに \+0.8% 以上上昇したか**
- 特徴量: F\-03 の指標 \+ 直近 5 本分のラグ値
- 学習: 過去 1 年分、時系列分割のクロスバリデーション（シャッフル禁止）
- 出力: 反発確率 0〜1、および閾値による BUY / HOLD

> **注意**\: 過去データでの成績は将来の成績を保証しません。MVP は「通知が届くこと」と「成績を測れること」が目的で、収益性の検証はその後の話です。

* * *

## 3\. 非機能要件

| 項目 | MVP の水準 |
| --- | --- |
| 稼働 | 24 時間常駐。落ちたら自動再起動（systemd / Docker restart） |
| 遅延 | 足確定から通知まで 60 秒以内 |
| 対象 | 1 銘柄 / 1 タイムフレーム |
| DB | SQLite（1 ファイル）。日次で DB ファイルをコピーバックアップ |
| ログ | 標準出力 \+ ファイル。取得・検知・推論・通知の 4 種を必ず記録 |
| 秘匿情報 | API キー・Webhook URL は環境変数。**コードにも Git にも置かない** |
| 実行環境 | ローカル PC または VPS 1 台、Docker Compose で起動 |

* * *

## 4\. データモデル（SQLite）

```sql
-- 確定足
CREATE TABLE candles (
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,   -- epoch ms
  open        REAL, high REAL, low REAL, close REAL, volume REAL,
  PRIMARY KEY (symbol, timeframe, open_time)
);

-- 計算済み指標
CREATE TABLE features (
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,
  rsi14       REAL, macd REAL, macd_signal REAL, macd_hist REAL,
  sma20       REAL, sma50 REAL, dev_sma20 REAL,
  vol_ma20    REAL, vol_ratio REAL, atr14 REAL,
  PRIMARY KEY (symbol, timeframe, open_time)
);

-- 検知＋推論結果
CREATE TABLE signals (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  symbol      TEXT    NOT NULL,
  timeframe   TEXT    NOT NULL,
  open_time   INTEGER NOT NULL,
  rule_hit    TEXT,               -- 一致した条件名（カンマ区切り）
  probability REAL,               -- AI 反発確率 0-1
  signal      TEXT,               -- BUY / HOLD
  entry_price REAL, take_profit REAL, stop_loss REAL,
  risk_reward REAL,
  confidence  TEXT,               -- A / B / C
  created_at  INTEGER NOT NULL
);

-- 通知履歴（重複抑止に使う）
CREATE TABLE notifications (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  signal_id   INTEGER REFERENCES signals(id),
  channel     TEXT,               -- discord
  status      TEXT,               -- sent / failed
  sent_at     INTEGER NOT NULL,
  payload     TEXT
);

CREATE TABLE system_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  level       TEXT, source TEXT, message TEXT, created_at INTEGER
);
```

* * *

## 5\. Java ⇄ Python インターフェース

MVP では **Python 側を FastAPI の常駐サービスにして、Java から HTTP で叩く**。プロセス起動方式より速く、デバッグもしやすい。

### `POST /analyze`

リクエスト

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "open_time": 1716183000000,
  "rule_hit": "rsi_low,volume_spike,sma20_deviation"
}
```

レスポンス

```json
{
  "probability": 0.84,
  "signal": "BUY",
  "entry_price": 16240000,
  "take_profit": 16750000,
  "stop_loss": 15980000,
  "risk_reward": 3.8,
  "confidence": "A",
  "model_version": "lgbm_20260901_01"
}
```

特徴量は Python 側が DB から直接読む（Java が全特徴量を JSON で送らない）。Java が渡すのは「どの足を見ればいいか」だけ。

### その他のエンドポイント

| メソッド | パス | 用途 |
| --- | --- | --- |
| `GET` | `/health` | 死活監視 |
| `POST` | `/train` | 再学習の実行 |
| `POST` | `/backtest` | 期間指定でバックテスト実行 |

* * *

## 6\. Discord 通知フォーマット

Embed を 1 通。色は BUY \= 緑（`0x2ecc71`）、エラー \= 赤（`0xe74c3c`）。

```
🟢 押し目買い候補 — BTC/USDT (5m)

現在価格      16,240,000 円
AI反発確率    84%
信頼度        A

RSI(14)       28.4
MACD          ゴールデンクロス
SMA20乖離     -1.8%
出来高         平均比 1.9倍

利確目標      16,750,000 円 (+3.1%)
損切り目安    15,980,000 円 (-1.6%)
リスクリワード 3.8

2026-09-05 15:30 JST
```

* * *

## 7\. ディレクトリ構成案

```
trade-signal/
├── docker-compose.yml
├── .env.example              # APIキー・Webhook URL のひな形（.env は Git 管理外）
├── README.md
│
├── core-java/                # 基盤・制御
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/example/trade/
│       │   ├── TradeApplication.java
│       │   ├── config/
│       │   │   ├── AppConfig.java
│       │   │   └── RuleProperties.java      # 検知ルールの設定バインド
│       │   ├── collector/                   # F-01, F-02
│       │   │   ├── BinanceRestClient.java
│       │   │   ├── BinanceWebSocketClient.java
│       │   │   └── CandleBackfillService.java
│       │   ├── indicator/                   # F-03
│       │   │   ├── IndicatorCalculator.java
│       │   │   ├── Rsi.java
│       │   │   ├── Macd.java
│       │   │   └── MovingAverage.java
│       │   ├── monitor/                     # F-04
│       │   │   ├── RuleEngine.java
│       │   │   ├── Rule.java
│       │   │   └── CandleClosedListener.java
│       │   ├── ai/                          # F-05
│       │   │   ├── AiClient.java
│       │   │   ├── AnalyzeRequest.java
│       │   │   └── AnalyzeResponse.java
│       │   ├── notify/                      # F-06, F-07, F-10
│       │   │   ├── DiscordNotifier.java
│       │   │   ├── MessageBuilder.java
│       │   │   └── DuplicateSuppressor.java
│       │   ├── repository/                  # F-08
│       │   │   ├── CandleRepository.java
│       │   │   ├── FeatureRepository.java
│       │   │   ├── SignalRepository.java
│       │   │   └── NotificationRepository.java
│       │   └── domain/
│       │       ├── Candle.java
│       │       ├── Feature.java
│       │       └── Signal.java
│       └── resources/
│           ├── application.yml
│           ├── rules.yml                    # 閾値はここ
│           └── db/migration/V1__init.sql
│
├── ai-python/                # AIエンジン
│   ├── pyproject.toml
│   ├── app/
│   │   ├── main.py                          # FastAPI エントリ
│   │   ├── api/
│   │   │   ├── analyze.py
│   │   │   ├── train.py
│   │   │   └── backtest.py
│   │   ├── features/
│   │   │   ├── builder.py                   # DB → 特徴量DataFrame
│   │   │   ├── technical.py
│   │   │   └── labeling.py                  # 教師ラベル生成
│   │   ├── models/
│   │   │   ├── lgbm.py
│   │   │   ├── registry.py                  # バージョン管理・ロード
│   │   │   └── predictor.py
│   │   ├── backtest/
│   │   │   ├── engine.py
│   │   │   ├── metrics.py                   # 勝率 / PF / DD / Sharpe
│   │   │   └── report.py                    # CSV 出力
│   │   ├── db/
│   │   │   ├── session.py
│   │   │   └── queries.py
│   │   └── config.py
│   ├── artifacts/                           # 学習済みモデル（Git 管理外）
│   │   └── lgbm_20260901_01.pkl
│   ├── scripts/
│   │   ├── train.py
│   │   └── run_backtest.py
│   └── tests/
│
├── data/
│   └── trade.db                             # SQLite（Git 管理外）
└── docs/
    ├── requirements-full.md                 # 01〜08 のフル要件
    └── requirements-mvp.md                  # この文書
```

* * *

## 8\. 技術スタック

| レイヤ | 採用 | 理由 |
| --- | --- | --- |
| Java | Java 21 / Spring Boot 3 / Gradle | スケジューラ・HTTP・DI が揃っている |
| WebSocket | Java\-WebSocket または Spring WebSocket Client | 再接続を自前で書く量が少ない |
| Python | Python 3.11 / FastAPI / uvicorn | 型付き・軽量・自動ドキュメント |
| ML | LightGBM / pandas / numpy / scikit\-learn | 表形式データで最初に試すべき組み合わせ |
| DB | SQLite（本番移行時に PostgreSQL） | ファイル 1 つで完結 |
| 実行 | Docker Compose（java / python の 2 コンテナ） | 1 コマンドで起動 |

* * *

## 9\. 環境変数

```bash
# .env.example
BINANCE_API_KEY=
BINANCE_API_SECRET=
DISCORD_WEBHOOK_URL=
AI_SERVICE_URL=http://ai-python:8000
DB_PATH=/data/trade.db
SYMBOL=BTCUSDT
TIMEFRAME=5m
PROBABILITY_THRESHOLD=0.7
NOTIFY_COOLDOWN_MINUTES=60
```

Binance の API キーは **読み取り専用（取引権限なし）** で発行する。MVP は発注しないので、取引権限を持たせる理由がない。

* * *

## 10\. 開発ステップ

| Step | 内容 | 動く状態 |
| --- | --- | --- |
| 1 | Java: Binance REST で 5 分足を取得 → SQLite 保存 | DB に足が溜まる |
| 2 | Java: 指標計算 \+ ルール検知 → ログ出力 | 検知がログに出る |
| 3 | Java: Discord Webhook 送信（AI なし、ルールだけで通知） | **Discord に通知が届く（最初の到達点）** |
| 4 | Java: WebSocket に切り替えてリアルタイム化 | 足確定と同時に検知 |
| 5 | Python: 特徴量生成 \+ ラベル生成 \+ LightGBM 学習 | モデルファイルができる |
| 6 | Python: FastAPI `/analyze` を実装、Java から接続 | 通知に反発確率が載る |
| 7 | Python: バックテスト実装、勝率 / PF を出す | 成績を数字で語れる |
| 8 | Docker 化 \+ 常駐 \+ 障害通知 | 放置して回る |

Step 3 まででいったん「使えるもの」になる。AI はそこに後から差し込む形にしておくと、途中で止まっても手元に動くものが残る。

* * *

## 11\. 受け入れ基準（MVP 完了の定義）

- [ ] 72 時間連続稼働して落ちない（落ちても自動復帰する）
- [ ] 足確定から 60 秒以内に Discord へ通知が届く
- [ ] 同一方向のシグナルが 60 分以内に連投されない
- [ ] 取得エラー・推論エラーが Discord に通知される
- [ ] 過去 6 か月のバックテストで勝率・PF・最大ドローダウンが出力される
- [ ] `.env` を差し替えるだけで別の環境で起動できる
- [ ] API キー・Webhook URL がリポジトリに含まれていない
