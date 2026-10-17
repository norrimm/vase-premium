package com.example.trade.notify;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.trade.ai.AnalyzeResponse;
import com.example.trade.domain.Candle;
import com.example.trade.domain.Feature;
import com.example.trade.monitor.RuleEngine.Evaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Discord Webhook に送る JSON（Embed 1 通）を組み立てる（MVP 要件定義 6. Discord 通知フォーマット）。 */
@Component
public class MessageBuilder {

    static final int COLOR_BUY = 0x2ecc71;
    static final int COLOR_TEST = 0x95a5a6;
    static final int COLOR_ERROR = 0xe74c3c;
    static final int COLOR_RECOVERED = 0x3498db;

    private static final DateTimeFormatter JST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Tokyo"));
    private static final List<String> QUOTES = List.of("USDT", "USDC", "FDUSD", "BUSD", "JPY", "BTC", "ETH");

    private final ObjectMapper mapper;

    public MessageBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 押し目買い候補の通知。
     *
     * @param ai        AI の推論結果。AI を使っていない・推論に失敗したときは null
     * @param aiEnabled AI を使う設定か（null の理由が「失敗」か「未使用」かの表示に使う）
     */
    public ObjectNode buyDip(Evaluation e, AnalyzeResponse ai, boolean aiEnabled) {
        ObjectNode payload = build(e, "🟢 押し目買い候補", COLOR_BUY);
        ObjectNode embed = (ObjectNode) payload.get("embeds").get(0);
        ArrayNode fields = (ArrayNode) embed.get("fields");
        if (ai != null) {
            double entry = ai.entryPrice();
            String quote = quoteOf(e.snapshot().candle().symbol());
            // 現在価格の直後に AI の判定、「一致した条件」の手前に利確・損切りを並べる（要件定義 6. の順）
            fields.insert(1, newField("AI反発確率",
                    String.format(Locale.ROOT, "%d%%", Math.round(ai.probability() * 100))));
            fields.insert(2, newField("信頼度", ai.confidence()));
            int at = fields.size() - 1;
            fields.insert(at++, newField("利確目標", String.format(Locale.ROOT, "%,.2f %s (%+.1f%%)",
                    ai.takeProfit(), quote, (ai.takeProfit() / entry - 1) * 100)));
            fields.insert(at++, newField("損切り目安", String.format(Locale.ROOT, "%,.2f %s (%+.1f%%)",
                    ai.stopLoss(), quote, (ai.stopLoss() / entry - 1) * 100)));
            fields.insert(at, newField("リスクリワード", fmt("%.1f", ai.riskReward())));
            ObjectNode footer = (ObjectNode) embed.get("footer");
            footer.put("text", footer.get("text").asText() + " ・ " + ai.modelVersion());
        } else if (aiEnabled) {
            fields.insert(1, newField("AI反発確率", "推論失敗（ルール一致のみで通知）"));
        }
        return payload;
    }

    /** 接続確認用。ルール一致に関係なく、評価した足の内容をそのまま送る。 */
    public ObjectNode test(Evaluation e) {
        return build(e, "🧪 テスト通知" + (e.hit() ? "（条件一致）" : "（条件不一致）"), COLOR_TEST);
    }

    /** 障害通知（F-10）。 */
    public ObjectNode alert(AlertSource source, String message, int failures, long since) {
        ObjectNode embed = mapper.createObjectNode();
        embed.put("title", "🔴 障害 — " + source.label());
        embed.put("color", COLOR_ERROR);
        embed.put("description", truncate(message, 1000));
        ArrayNode fields = embed.putArray("fields");
        field(fields, "発生", JST.format(Instant.ofEpochMilli(since)) + " JST");
        field(fields, "回数", failures + " 回");
        embed.put("timestamp", Instant.ofEpochMilli(since).toString());
        return wrap(embed);
    }

    /** 障害からの復旧通知。 */
    public ObjectNode recovered(AlertSource source, int failures, long since, long now) {
        ObjectNode embed = mapper.createObjectNode();
        embed.put("title", "🔵 復旧 — " + source.label());
        embed.put("color", COLOR_RECOVERED);
        ArrayNode fields = embed.putArray("fields");
        field(fields, "障害の発生", JST.format(Instant.ofEpochMilli(since)) + " JST");
        field(fields, "継続時間", Duration.ofMillis(now - since).toMinutes() + " 分");
        field(fields, "失敗回数", failures + " 回");
        embed.put("timestamp", Instant.ofEpochMilli(now).toString());
        return wrap(embed);
    }

    private ObjectNode wrap(ObjectNode embed) {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("embeds").add(embed);
        return payload;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private ObjectNode build(Evaluation e, String headline, int color) {
        Candle c = e.snapshot().candle();
        Feature f = e.snapshot().feature();

        ObjectNode embed = mapper.createObjectNode();
        embed.put("title", headline + " — " + displaySymbol(c.symbol()) + " (" + c.timeframe().code() + ")");
        embed.put("color", color);
        ArrayNode fields = embed.putArray("fields");
        field(fields, "現在価格", String.format(Locale.ROOT, "%,.2f %s", c.close(), quoteOf(c.symbol())));
        field(fields, "RSI(14)", fmt("%.1f", f.rsi14()));
        field(fields, "MACD", macd(f));
        field(fields, "SMA20乖離", fmt("%+.2f%%", f.devSma20()));
        field(fields, "出来高", f.volRatio() == null ? "-" : fmt("平均比 %.1f倍", f.volRatio()));
        field(fields, "ATR(14)", fmt("%,.2f", f.atr14()));
        ObjectNode rules = field(fields, "一致した条件", e.ruleHit().isEmpty() ? "なし" : e.ruleHit());
        rules.put("inline", false);
        embed.putObject("footer").put("text", "足 " + JST.format(Instant.ofEpochMilli(c.openTime())) + " JST");
        embed.put("timestamp", Instant.ofEpochMilli(c.closeTime()).toString());
        return wrap(embed);
    }

    private static String macd(Feature f) {
        if (f.macdHist() == null) {
            return "-";
        }
        return (f.macdHist() >= 0 ? "シグナル線の上" : "シグナル線の下") + fmt(" (%+.2f)", f.macdHist());
    }

    private ObjectNode field(ArrayNode fields, String name, String value) {
        ObjectNode field = newField(name, value);
        fields.add(field);
        return field;
    }

    private ObjectNode newField(String name, String value) {
        ObjectNode field = mapper.createObjectNode();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", true);
        return field;
    }

    /** BTCUSDT → BTC/USDT。知らない決済通貨ならそのまま返す。 */
    static String displaySymbol(String symbol) {
        String quote = quoteOf(symbol);
        return quote.isEmpty() ? symbol : symbol.substring(0, symbol.length() - quote.length()) + "/" + quote;
    }

    private static String quoteOf(String symbol) {
        return QUOTES.stream()
                .filter(q -> symbol.endsWith(q) && symbol.length() > q.length())
                .findFirst().orElse("");
    }

    private static String fmt(String format, Double v) {
        return v == null ? "-" : String.format(Locale.ROOT, format, v);
    }
}
