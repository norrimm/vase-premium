# vase-premium

BTC/USDT の反発シグナルを検知して Discord に通知するプロジェクト。全体構成・起動方法は [`README.md`](README.md) を参照。

- `core-java/` — 取得・指標計算・ルール検知・通知（[README](core-java/README.md)）
- `ai-python/` — 反発確率の推論・学習・バックテスト（[README](ai-python/README.md)）
- `資料置き場/` — 要件定義などの設計資料（MD）
  - `精度改善ログ.md` — `accuracy-cycle`/`accuracy-review` の実行結果（合格・不合格・要修正すべて）を積み上げる履歴
- `更新ログ/log.md` — コミットのたびに自動生成される履歴（**手で編集しない**。下記フック参照）

## 開発の基本ルール

### コミット

- タイトルは日本語の短い要約（`feat:` 等のプレフィックスは付けない）。例: `ウォークフォワード・バックテストを追加し、利確幅を 1.5% に変更`
- 本文は `-` 箇条書きで「何を変えたか」を書く。**数値を変える・実験的な変更のときは、結果の数値（取引数・PF・最大DD・通算損益など）を本文に残す**のがこのリポジトリの流儀（`git log 3a5bf60` 参照）。あとから「なぜこの値にしたか」が追えるようにするため
- コミット後、`.githooks/post-commit` フックが自動で `更新ログ/log.md` に追記し、`ログ更新: <subject>` というコミットを追加で作る（無限ループ防止のため `ログ更新:` で始まるコミット自身では発火しない）。これは**自動なので手を加えない**。フックが無効な環境では `git config core.hooksPath .githooks` を実行する
- 1 コミット1変更が基本（機能追加と無関係な調整を混ぜない）

### ブランチ

- `main` は初期状態のみ。実際の開発は `feature-XXXX`（Jira チケット番号）ブランチで行う

### テスト

- `ai-python`: `.\.venv\Scripts\python -m pytest`
- `core-java`: `.\gradlew.bat test`
- 機能を変更したら対応するテストも必ず実行する（コミット前に通すこと）

### ドキュメント

- `core-java/`・`ai-python/` 配下を変更したら、それぞれの `README.md`（環境変数表・使い方）も一緒に更新する
- 全体構成（Binance → core-java → ai-python → Discord）を変えたらルート `README.md` の構成図も更新する

### 秘匿情報・生成物

- `.env`・`data/`・`logs/`・`*.db`・`ai-python/artifacts/*.pkl`・`ai-python/reports/*.csv` は `.gitignore` 対象。コミットしない
- 新しい環境変数を追加したら `.env.example` と対応する README の表に追記する

### Jira

- サイト: `ai-trade-lab.atlassian.net`、プロジェクト: `SB`（Signal Bot）
- チケットと紐づく作業をしたら、作業内容が分かるように意識する（コミットメッセージとチケットの対応が追えること）

## 精度改善の進め方

反発確率モデルの精度を上げる改修（特徴量・パラメータ・ルールの変更）は、思いつきで変えて one-shot でコミットしない。
`accuracy-cycle` Skill（実験の実行）→ `accuracy-review` Skill（結果の合否判定）の順で必ず通す。
