package com.cleanyourtv.rates;

import org.json.JSONObject;
import java.math.BigDecimal;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A dated Chennai 22K quote. Never substitute international spot or a different purity. */
final class GoldData {
    static final String SOURCE = "https://rates.goldenchennai.com/gold-rate/chennai-gold-rate-today/";
    final BigDecimal perGram, eightGrams;
    final String date;

    GoldData(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        if (!"Chennai".equals(data.getString("city")) || data.getInt("karat") != 22)
            throw new IllegalArgumentException("Wrong gold market");
        perGram = new BigDecimal(data.getString("perGram"));
        if (perGram.signum() <= 0 || perGram.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Invalid gold rate");
        eightGrams = perGram.multiply(BigDecimal.valueOf(8));
        date = data.getString("date");
        LocalDate.parse(date);
    }

    static String parsePage(String html) throws Exception {
        Matcher section = Pattern.compile("<div\\s+id=[\"']zb[\"'][^>]*>(.*?)</table>", Pattern.DOTALL).matcher(html);
        if (!section.find()) throw new IllegalArgumentException("Gold table missing");
        String table = section.group(1);
        Matcher date = Pattern.compile("<p\\s+id=[\"']zh[\"'][^>]*>(\\d{2}-[A-Za-z]{3}-\\d{4}),").matcher(table);
        if (!date.find()) throw new IllegalArgumentException("Quote date missing");
        String day = LocalDate.parse(date.group(1), DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT)).toString();
        Matcher headers = Pattern.compile("<thead[^>]*>(.*?)</thead>", Pattern.DOTALL).matcher(table);
        if (!headers.find()) throw new IllegalArgumentException("Gold headings missing");
        String header = headers.group(1).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (!header.matches("Qty 22K Gold Rate 18K Gold Rate 24K Gold Rate"))
            throw new IllegalArgumentException("Gold column order changed");
        Matcher row = Pattern.compile("<tr[^>]*>\\s*<td[^>]*>1g</td>\\s*<td[^>]*>\\s*(?:₹|&#8377;|&#x20[Bb]9;)\\s*([0-9,]+\\.[0-9]{2})\\s*</td>", Pattern.DOTALL).matcher(table);
        if (!row.find()) throw new IllegalArgumentException("22K per-gram quote missing");
        String raw = new JSONObject().put("city", "Chennai").put("karat", 22)
            .put("date", day).put("perGram", row.group(1).replace(",", "")).toString();
        new GoldData(raw);
        return raw;
    }

    static String fetch() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(SOURCE).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            if (connection.getResponseCode() != 200) throw new Exception("Gold source unavailable");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 512 * 1024) throw new Exception("Gold page too large");
                }
                return parsePage(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }
}
