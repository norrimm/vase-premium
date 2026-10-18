---
name: accuracy-cycle
description: 反発確率モデルの精度改善（特徴量・パラメータ・ルール変更）を1イテレーションとして、ベースラインとの比較まで一貫して実行する。「精度を上げたい」「特徴量を試したい」「閾値/利確幅/損切り幅を変えたい」と言われたときに使う。
---

# accuracy-cycle

`ai-python`（推論モデル）または `core-java/src/main/resources/rules.yml`（検知ルール）を変更して、反発確率モデルの実績を改善するための1サイクル。
**このSkillの最後では必ず `accuracy-review` Skillに結果を渡し、合否判定を受けてからコミットする。** 自己判断でコミットしない。

## 前提

- `ai-python/README.md` の「モデル」「バックテスト」「設定」を読んでおく（教師ラベル・特徴量・環境変数の意味）
- `CLAUDE.md` のコミット規約・テスト規約に従う

## 手順

### 1. ベースラインを確定する

`ai-python/reports/` に直近の `walkforward_*_total.csv` があればそれをベースラインとする。なければ、変更を入れる**前**に一度 `walk_forward.py` を実行してベースラインを作る（変更後とフェアに比較できる同じ期間・同じオプションで実行すること）。

```powershell
cd ai-python
.\.venv\Scripts\python scripts\walk_forward.py --to <直近日>
```

ベースラインの `total` 行（取引数・勝率・PF・最大DD・合計損益・winning_folds）を控える。

### 2. 仮説を1つだけ立てる

「何を変えると、なぜ良くなるはずか」を一文で言える状態にする。複数の変更を同時に混ぜない（どれが効いたか分からなくなる）。
例: 「出来高MA20比が効いていない可能性があるので特徴量に出来高の変化率を追加する」「利確幅を1.5%→1.2%にすると勝率は上がるがPFは下がるか確認する」

Jira に対応チケットがあれば紐づける。

### 3. 最小の変更を実装する

- 特徴量変更: `ai-python/app/features/`
- ラベル・モデル変更: `ai-python/app/features/labeling.py`, `ai-python/app/models/`
- 利確/損切り/閾値: `.env.example` の該当変数（`LABEL_TARGET_PCT` / `STOP_ATR_MULT` / `PROBABILITY_THRESHOLD` など）
- ルール（指標の組み合わせ条件）変更: `core-java/src/main/resources/rules.yml`

core-java 側を変更した場合は `core-java` のテストも対象になる。

### 4. テストを通す

```powershell
cd ai-python
.\.venv\Scripts\python -m pytest
```

core-java を変更した場合:

```powershell
cd core-java
.\gradlew.bat test
```

落ちたら仮説の実装に戻る。ここを飛ばして次に進まない。

### 5. 同条件でバックテストを再実行する

ベースラインと**同じ期間・同じオプション**（`--to`, `--thresholds`, `--stop-mults` 等）で実行する。期間や閾値を変えて「良い組み合わせ」を後から探しに行くのは禁止（過学習・多重比較になる。`ai-python/README.md` 自身の注意書き参照）。

```powershell
.\.venv\Scripts\python scripts\walk_forward.py --to <ベースラインと同じ日>
```

### 6. 比較表を作る

ベースラインと候補を並べる。最低限この項目を出す:

| | 取引数 | 勝率 | PF | 最大DD(%) | 合計損益(%) | winning_folds |
|---|---|---|---|---|---|---|
| ベースライン | | | | | | |
| 候補 | | | | | | |

### 7. `accuracy-review` Skill に渡す

比較表・変更内容（diff）・仮説・使用したコマンドをそのまま `accuracy-review` Skill に渡し、合否判定を受ける。

- **合格** → `CLAUDE.md` の規約に沿ってコミット（比較表の数値を本文に残す）。README・`.env.example` の該当箇所も更新
- **不合格** → 変更を `git checkout -- <path>` 等で戻す。何を試して何が悪かったかを一言メモに残す（同じ失敗を繰り返さないため。`資料置き場/` に実験ログがなければ作る）
