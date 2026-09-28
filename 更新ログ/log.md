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

