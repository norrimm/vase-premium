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

## 常時起動（Windows サービス）

Claude やターミナルを閉じても動き続けるよう、core-java と ai-python を Windows サービスとして登録できる（[NSSM](https://nssm.cc/)。無料）。
PC 起動時に自動で立ち上がり、落ちたら 10 秒後に再起動する。ログインしていなくても動く。

```powershell
winget install NSSM.NSSM            # 初回だけ
# スクリプト実行が無効と言われたら初回だけ（自分のユーザーで、ローカルのスクリプトの実行を許可する）
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
# 以下は PowerShell を「管理者として実行」で開いて、リポジトリ直下で実行する
.\scripts\service.ps1 install       # core-java の jar をビルド → 3 つのサービスを登録 → 起動
.\scripts\service.ps1 status        # 状態と ai-python の /health（これだけは管理者でなくてよい）
.\scripts\service.ps1 update        # core-java のコードを変えたあと（停止 → 再ビルド → 起動）
.\scripts\service.ps1 restart       # 学習し直したモデルを ai-python に読み込ませるとき
.\scripts\service.ps1 stop          # / start
.\scripts\service.ps1 uninstall     # 停止して登録を消す
```

- サービス名は `vase-ai-python`（127.0.0.1:8000）と `vase-core-java`。設定はどちらも従来どおりリポジトリ直下の `.env` を読む
- 15 分足の検証用に `vase-core-java-15m` も登録する（`data/trade_15m.db` に 2200 日分の足を貯めるだけ。Discord 通知・AI は使わない。ログは `logs/core-java-15m.log`）
- `install` の前に、手で起動している `bootRun`（5 分足の本番）と `uvicorn` は止めておく（ポート 8000 と DB の取り合いになる）
- ログ: `logs/core-java.log`（アプリのログ）、`logs/*.out.log` / `*.err.log`（標準出力。10MB でローテーション）
- PC がスリープすると止まる。電源設定で「スリープしない」にしておく（`powercfg /change standby-timeout-ac 0`）
