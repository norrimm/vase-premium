# vase-premium

BTC/USDT の 5 分足を監視して、押し目・反発の買い目が来たら Discord に通知する（`資料置き場/MVP要件定義・構成案（AIトレードシグナル通知）.md`）。

| ディレクトリ | 役割 | 機能要件 |
| --- | --- | --- |
| [`core-java/`](core-java/README.md) | 足の取得・指標計算・ルール検知・通知・障害通知 | F-01〜F-04, F-06〜F-08, F-10 |
| [`ai-python/`](ai-python/README.md) | 反発確率の推論 API・学習・バックテスト | F-05, F-09 |

起動の流れ: `.env.example` を `.env` にコピー → core-java を起動して足を貯める → ai-python で学習して API を起動 → core-java が AI を使って通知する。
