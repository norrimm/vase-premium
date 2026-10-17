# ai-python（AI エンジン）

MVP 要件定義の F-05（AI 推論）と F-09（バックテスト）にあたる部分。
core-java が貯めた SQLite の足と指標を読み、LightGBM で「1 時間以内に +0.8% 反発する確率」を出す。
DB は読み取り専用で開く（書き込みは core-java の担当）。

```
core-java ── POST /analyze {symbol, timeframe, open_time, rule_hit} ──▶ ai-python
                                                                          │ DB からその足と直前の足を読む
          ◀── {probability, signal, entry/take_profit/stop_loss, ...} ───┘ 特徴量を作って推論
```

## セットアップ

Python 3.11 が必要（`winget install Python.Python.3.11`）。

```powershell
cd ai-python
py -3.11 -m venv .venv          # py がなければ %LOCALAPPDATA%\Programs\Python\Python311\python.exe -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
.\.venv\Scripts\python -m pytest # テスト
```

## 使い方

```powershell
# 1. 学習（DB の最新から TRAIN_DAYS 日分）→ artifacts/lgbm_YYYYMMDD_NN.pkl
.\.venv\Scripts\python scripts\train.py

# 2. API サーバー起動（最新のモデルを読み込む）
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000

# 3. バックテスト（DB の最新から 180 日分）→ reports/backtest_*_summary.csv / _trades.csv
.\.venv\Scripts\python scripts\run_backtest.py
.\.venv\Scripts\python scripts\run_backtest.py --from 2026-04-01 --to 2026-09-30 --threshold 0.5
```

core-java 側は `.env` の `AI_SERVICE_URL=http://localhost:8000` で AI を使うようになる（空なら AI なしでルールだけで通知）。

| メソッド | パス | 用途 |
| --- | --- | --- |
| `POST` | `/analyze` | 1 本の足の反発確率とシグナル。モデルがなければ 503、足がなければ 404 |
| `GET` | `/health` | 死活監視。モデル未作成なら `status: no_model` |
| `POST` | `/train` | 再学習してモデルを差し替える（`{"from_date": "2025-10-01", "to_date": "2026-09-30"}` は省略可） |
| `POST` | `/backtest` | バックテストして成績を返し、CSV も保存（`from_date` `to_date` `model_version` `threshold` は省略可） |

## モデル

- 教師ラベル: その足の終値から 12 本先（1 時間）までに高値が +0.8% に届いたか
- 特徴量: F-03 の指標を価格に依存しない形（終値比の %）にしたもの 10 個 ＋ 直近 5 本分のラグ値（計 60 個）。
  足が欠けている箇所のラグは NaN にする
- 学習: 全部の確定足（ルール一致の足だけでは 1 年で数百本しかなく少なすぎるため）。
  時系列分割の 5 分割 CV（ラベルの先読み分 12 本の間隔を空ける。シャッフルなし）で成績を出してから全期間で学習する
- 出力: 反発確率 ≥ `PROBABILITY_THRESHOLD` なら BUY。信頼度は `CONFIDENCE_A`（0.8）以上で A、閾値以上で B、それ以外 C
- 利確目標 = +0.8%（ラベルと同じ）、損切り目安 = ATR(14) × `STOP_ATR_MULT`（2.0）下

## バックテスト（F-09）

- 検知ルールは core-java の `rules.yml` をそのまま読む（閾値を変えてもライブとずれない）
- `--model` を省略すると、**検証期間より前のデータだけで学習し直したモデル**を使う（未来の情報を使わない。このモデルは artifacts に保存しない）
- 売買: シグナル足の終値で買い、利確 / 損切り / 12 本後の終値のどれかで手仕舞い。同じ足で利確と損切りの両方に届いたら損切りとみなす
- 手数料は片道 `BACKTEST_FEE_PCT`（0.1%）を往復分差し引く。通知の重複抑止（60 分）も再現する
- `rule`（ルールのみ）と `rule+ai`（ルール + AI 閾値）の 2 行を出す。列: 取引数 / 勝率 / PF / 最大 DD(%) / 合計損益(%) / 平均損益(%) / ラベル的中率

## 設定（環境変数。リポジトリ直下の `.env` も読む）

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `DB_PATH` | `../data/trade.db` | SQLite（ai-python からの相対パス） |
| `SYMBOL` / `TIMEFRAME` | `BTCUSDT` / `5m` | 対象 |
| `PROBABILITY_THRESHOLD` | `0.5` | BUY とする反発確率 |
| `MODEL_VERSION` | （空） | 使うモデル。空なら artifacts の最新 |
| `TRAIN_DAYS` | `365` | 学習に使う日数 |
| `LABEL_HORIZON` / `LABEL_TARGET_PCT` | `12` / `0.8` | 教師ラベル |
| `STOP_ATR_MULT` | `2.0` | 損切り幅（ATR の倍数） |
| `CONFIDENCE_A` | `0.8` | 信頼度 A の下限 |
| `BACKTEST_FEE_PCT` | `0.1` | バックテストの片道手数料（%） |
| `RULES_PATH` | `../core-java/src/main/resources/rules.yml` | 検知ルール |

> 過去データでの成績は将来の成績を保証しません。
