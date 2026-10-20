# 更新ログ

## 2026-09-05 16:46:01 - maakishii (085272f)

**コミットメッセージ:** 資料置き場に名前変更、要件定義MDを2件追加

**変更ファイル:**
```
A	.githooks/post-commit
D	ぷっしゅするよん/ChatGPT_Image_202673_21_05_59.png
A	資料置き場/AIトレードシグナル通知システム 要件定義書.md
A	資料置き場/ChatGPT_Image_202673_21_05_59.png
A	資料置き場/MVP要件定義・構成案（AIトレードシグナル通知）.md
```

## 2026-09-29 01:09:09 - ryuta (61d7e24)

**コミットメッセージ:** Binance の 5 分足取得（REST バックフィル + WebSocket）を追加

**変更ファイル:**
```
A	.env.example
A	.gitignore
A	core-java/README.md
A	core-java/build.gradle.kts
A	core-java/gradle.properties
A	core-java/gradle/wrapper/gradle-wrapper.jar
A	core-java/gradle/wrapper/gradle-wrapper.properties
A	core-java/gradlew
A	core-java/gradlew.bat
A	core-java/settings.gradle.kts
A	core-java/src/main/java/com/example/trade/TradeApplication.java
A	core-java/src/main/java/com/example/trade/collector/BinanceRestClient.java
A	core-java/src/main/java/com/example/trade/collector/BinanceWebSocketClient.java
A	core-java/src/main/java/com/example/trade/collector/CandleBackfillService.java
A	core-java/src/main/java/com/example/trade/collector/CandleStream.java
A	core-java/src/main/java/com/example/trade/collector/CollectorRunner.java
A	core-java/src/main/java/com/example/trade/collector/ExchangeClient.java
A	core-java/src/main/java/com/example/trade/collector/ExchangeException.java
A	core-java/src/main/java/com/example/trade/config/AppConfig.java
A	core-java/src/main/java/com/example/trade/config/MarketProperties.java
A	core-java/src/main/java/com/example/trade/domain/Candle.java
A	core-java/src/main/java/com/example/trade/domain/Timeframe.java
A	core-java/src/main/java/com/example/trade/repository/CandleRepository.java
A	core-java/src/main/resources/application.yml
A	core-java/src/main/resources/schema.sql
A	core-java/src/test/java/com/example/trade/collector/BinanceParsingTest.java
A	core-java/src/test/java/com/example/trade/collector/CandleBackfillServiceTest.java
A	core-java/src/test/java/com/example/trade/repository/CandleRepositoryTest.java
A	core-java/src/test/java/com/example/trade/repository/TestDatabase.java
```

## 2026-09-29 01:43:23 - ryuta (b70ad16)

**コミットメッセージ:** 指標計算と検知ルール評価を追加（Step 2）

**変更ファイル:**
```
M	core-java/README.md
M	core-java/src/main/java/com/example/trade/collector/CandleBackfillService.java
A	core-java/src/main/java/com/example/trade/collector/CandlesStoredEvent.java
M	core-java/src/main/java/com/example/trade/collector/CollectorRunner.java
A	core-java/src/main/java/com/example/trade/config/RuleProperties.java
A	core-java/src/main/java/com/example/trade/domain/Feature.java
A	core-java/src/main/java/com/example/trade/domain/MarketSnapshot.java
A	core-java/src/main/java/com/example/trade/indicator/Atr.java
A	core-java/src/main/java/com/example/trade/indicator/FeatureService.java
A	core-java/src/main/java/com/example/trade/indicator/IndicatorCalculator.java
A	core-java/src/main/java/com/example/trade/indicator/Macd.java
A	core-java/src/main/java/com/example/trade/indicator/MovingAverage.java
A	core-java/src/main/java/com/example/trade/indicator/Rsi.java
A	core-java/src/main/java/com/example/trade/monitor/CandleClosedListener.java
A	core-java/src/main/java/com/example/trade/monitor/Rule.java
A	core-java/src/main/java/com/example/trade/monitor/RuleEngine.java
M	core-java/src/main/java/com/example/trade/repository/CandleRepository.java
A	core-java/src/main/java/com/example/trade/repository/FeatureRepository.java
M	core-java/src/main/resources/application.yml
A	core-java/src/main/resources/rules.yml
M	core-java/src/main/resources/schema.sql
M	core-java/src/test/java/com/example/trade/collector/CandleBackfillServiceTest.java
A	core-java/src/test/java/com/example/trade/indicator/FeatureServiceTest.java
A	core-java/src/test/java/com/example/trade/indicator/IndicatorTest.java
A	core-java/src/test/java/com/example/trade/monitor/RuleEngineTest.java
```

## 2026-09-29 02:00:10 - ryuta (f7861b2)

**コミットメッセージ:** ルール一致時の Discord 通知を追加（Step 3）

**変更ファイル:**
```
M	.env.example
M	core-java/README.md
A	core-java/src/main/java/com/example/trade/config/NotifyProperties.java
A	core-java/src/main/java/com/example/trade/domain/Signal.java
M	core-java/src/main/java/com/example/trade/monitor/CandleClosedListener.java
A	core-java/src/main/java/com/example/trade/notify/DiscordNotifier.java
A	core-java/src/main/java/com/example/trade/notify/DuplicateSuppressor.java
A	core-java/src/main/java/com/example/trade/notify/MessageBuilder.java
A	core-java/src/main/java/com/example/trade/notify/NotifyException.java
A	core-java/src/main/java/com/example/trade/notify/SignalNotifier.java
A	core-java/src/main/java/com/example/trade/repository/NotificationRepository.java
A	core-java/src/main/java/com/example/trade/repository/SignalRepository.java
M	core-java/src/main/resources/application.yml
M	core-java/src/main/resources/schema.sql
A	core-java/src/test/java/com/example/trade/notify/MessageBuilderTest.java
A	core-java/src/test/java/com/example/trade/notify/SignalNotifierTest.java
```

## 2026-10-17 16:05:52 - ryuta (7c1c82c)

**コミットメッセージ:** AI 推論・バックテスト・障害通知を追加（F-05 / F-09 / F-10）

**変更ファイル:**
```
M	.env.example
M	README.md
A	ai-python/.gitignore
A	ai-python/README.md
A	ai-python/app/__init__.py
A	ai-python/app/api/__init__.py
A	ai-python/app/api/analyze.py
A	ai-python/app/api/backtest.py
A	ai-python/app/api/periods.py
A	ai-python/app/api/train.py
A	ai-python/app/backtest/__init__.py
A	ai-python/app/backtest/engine.py
A	ai-python/app/backtest/metrics.py
A	ai-python/app/backtest/report.py
A	ai-python/app/backtest/rules.py
A	ai-python/app/config.py
A	ai-python/app/db/__init__.py
A	ai-python/app/db/queries.py
A	ai-python/app/db/session.py
A	ai-python/app/features/__init__.py
A	ai-python/app/features/builder.py
A	ai-python/app/features/labeling.py
A	ai-python/app/features/technical.py
A	ai-python/app/main.py
A	ai-python/app/models/__init__.py
A	ai-python/app/models/lgbm.py
A	ai-python/app/models/pipeline.py
A	ai-python/app/models/predictor.py
A	ai-python/app/models/registry.py
A	ai-python/artifacts/.gitkeep
A	ai-python/pyproject.toml
A	ai-python/reports/.gitkeep
A	ai-python/scripts/run_backtest.py
A	ai-python/scripts/train.py
A	ai-python/tests/__init__.py
A	ai-python/tests/conftest.py
A	ai-python/tests/test_api.py
A	ai-python/tests/test_backtest.py
A	ai-python/tests/test_features.py
M	core-java/README.md
A	core-java/src/main/java/com/example/trade/ai/AiClient.java
A	core-java/src/main/java/com/example/trade/ai/AiException.java
A	core-java/src/main/java/com/example/trade/ai/AnalyzeRequest.java
A	core-java/src/main/java/com/example/trade/ai/AnalyzeResponse.java
M	core-java/src/main/java/com/example/trade/collector/BinanceWebSocketClient.java
M	core-java/src/main/java/com/example/trade/collector/CandleStream.java
M	core-java/src/main/java/com/example/trade/collector/CollectorRunner.java
A	core-java/src/main/java/com/example/trade/config/AiProperties.java
M	core-java/src/main/java/com/example/trade/config/NotifyProperties.java
M	core-java/src/main/java/com/example/trade/monitor/CandleClosedListener.java
A	core-java/src/main/java/com/example/trade/notify/AlertNotifier.java
A	core-java/src/main/java/com/example/trade/notify/AlertSource.java
M	core-java/src/main/java/com/example/trade/notify/MessageBuilder.java
M	core-java/src/main/java/com/example/trade/notify/SignalNotifier.java
A	core-java/src/main/java/com/example/trade/repository/SystemLogRepository.java
M	core-java/src/main/resources/application.yml
M	core-java/src/main/resources/schema.sql
A	core-java/src/test/java/com/example/trade/notify/AlertNotifierTest.java
M	core-java/src/test/java/com/example/trade/notify/SignalNotifierTest.java
```

## 2026-10-17 16:12:15 - ryuta (f25be30)

**コミットメッセージ:** AI の閾値の既定値を 0.5 に変更

**変更ファイル:**
```
M	.env.example
M	ai-python/README.md
M	ai-python/app/config.py
```

## 2026-10-17 16:35:55 - ryuta (3a5bf60)

**コミットメッセージ:** ウォークフォワード・バックテストを追加し、利確幅を 1.5% に変更

**変更ファイル:**
```
M	.env.example
M	ai-python/README.md
A	ai-python/app/backtest/walkforward.py
A	ai-python/scripts/walk_forward.py
M	ai-python/tests/test_backtest.py
```

## 2026-10-18 22:53:27 - maakishii (f75c2da)

**コミットメッセージ:** ルートREADMEに構成図を追加

**変更ファイル:**
```
M	README.md
```

## 2026-10-18 23:11:02 - maakishii (e8c3db2)

**コミットメッセージ:** 開発プロセスをSkills化（精度改善サイクル＋レビューゲート）

**変更ファイル:**
```
A	.claude/skills/accuracy-cycle/SKILL.md
A	.claude/skills/accuracy-review/SKILL.md
A	CLAUDE.md
```

## 2026-10-18 23:11:54 - maakishii (ffbc29c)

**コミットメッセージ:** accuracy-reviewのコードレビュー観点を作り込み

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:14:25 - maakishii (1ca5724)

**コミットメッセージ:** accuracy-reviewの指摘を確認・提案形＋根拠付きに変更

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:16:19 - maakishii (eeaae6f)

**コミットメッセージ:** accuracy-reviewの指摘に優先度（必須/推奨/任意）を追加

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:18:33 - maakishii (e3548df)

**コミットメッセージ:** accuracy-reviewの指摘パターンを4種に拡張、検出有無の明示・継続改善を追加

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:25:23 - maakishii (baa00e4)

**コミットメッセージ:** accuracy-reviewに観点を追加: 失敗の握りつぶし・変更範囲・テスト弱体化の検出

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:27:29 - maakishii (f175a28)

**コミットメッセージ:** accuracy-reviewに観点を追加: 数値計算・冪等性・テスト容易性・性能

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-18 23:31:47 - maakishii (64bfe52)

**コミットメッセージ:** accuracy-cycleのドライランで見つけたギャップを修正

**変更ファイル:**
```
M	.claude/skills/accuracy-cycle/SKILL.md
```

## 2026-10-18 23:34:02 - maakishii (1cabc18)

**コミットメッセージ:** 精度改善の実行結果を資料置き場/精度改善ログ.mdに蓄積するよう変更

**変更ファイル:**
```
M	.claude/skills/accuracy-cycle/SKILL.md
M	CLAUDE.md
```

## 2026-10-18 23:35:58 - maakishii (4067ad6)

**コミットメッセージ:** accuracy-reviewを『レビューして』単独呼び出しでも使えるように対応

**変更ファイル:**
```
M	.claude/skills/accuracy-review/SKILL.md
```

## 2026-10-19 00:16:26 - ryuta (2d2fd0f)

**コミットメッセージ:** 精度改善ログ: SMA20 乖離の条件を緩める仮説（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-19 00:25:34 - ryuta (1cdfe59)

**コミットメッセージ:** 精度改善ログ: ルールを外して AI 単独にする仮説（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-19 00:47:20 - ryuta (9b6805c)

**コミットメッセージ:** core-java と ai-python を Windows サービスとして常時起動できるようにする

**変更ファイル:**
```
M	README.md
A	scripts/service.ps1
```

## 2026-10-19 01:06:07 - ryuta (ffa6e07)

**コミットメッセージ:** 15 分足の収集（検証用）も Windows サービスとして常時起動する

**変更ファイル:**
```
M	README.md
M	scripts/service.ps1
```

## 2026-10-19 01:17:19 - ryuta (b674fd6)

**コミットメッセージ:** 精度改善ログ: ETH・SOL に現行設定を使う仮説（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-19 09:16:33 - ryuta (95045ab)

**コミットメッセージ:** 精度改善ログ: ニュース（GDELT）を特徴量に加える事前分析（不採用）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-20 23:51:41 - ryuta (2b21edd)

**コミットメッセージ:** 精度改善ログ: 深層学習（系列モデル）を LightGBM と組み合わせる仮説（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-20 23:59:27 - ryuta (27b1ff4)

**コミットメッセージ:** 精度改善ログ: BUY の件数をそろえて系列モデルとの平均の順位付けを比べる診断（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

## 2026-10-21 00:02:14 - ryuta (437aaa3)

**コミットメッセージ:** 精度改善ログ: BTC 15 分足に現行設定を使う仮説（不合格）を記録

**変更ファイル:**
```
M	"\350\263\207\346\226\231\347\275\256\343\201\215\345\240\264/\347\262\276\345\272\246\346\224\271\345\226\204\343\203\255\343\202\260.md"
```

