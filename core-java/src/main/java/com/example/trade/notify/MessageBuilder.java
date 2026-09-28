package com.example.trade.notify;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

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

    private static final DateTimeFormatter JST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Tokyo"));
    private static final List<String> QUOTES = List.of("USDT", "USDC", "FDUSD", "BUSD", "JPY", "BTC", "ETH");

    private final ObjectMapper mapper;

    public MessageBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 押し目買い候補の通知。 */
    public ObjectNode buyDip(Evaluation e) {
        return build(e, "🟢 押し目買い候補", COLOR_BUY);
    }

    /** 接続確認用。ルール一致に関係なく、評価した足の内容をそのまま送る。 */
    public ObjectNode test(Evaluation e) {
        return build(e, "🧪 テスト通知" + (e.hit() ? "（条件一致）" : "（条件不一致）"), COLOR_TEST);
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

        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("embeds").add(embed);
        return payload;
    }

    private static String macd(Feature f) {
        if (f.macdHist() == null) {
            return "-";
        }
        return (f.macdHist() >= 0 ? "シグナル線の上" : "シグナル線の下") + fmt(" (%+.2f)", f.macdHist());
    }

    private ObjectNode field(ArrayNode fields, String name, String value) {
        ObjectNode field = fields.addObject();
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
