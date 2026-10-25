package com.cleanyourtv.rates;

import org.json.JSONObject;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class RatesData {
    final double usd, eur;
    final String date;

    RatesData(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        if (!data.getBoolean("success") || !"USD".equals(data.getString("source")))
            throw new IllegalArgumentException("Currencylayer quote unavailable");
        JSONObject quotes = data.getJSONObject("quotes");
        usd = quotes.getDouble("USDINR");
        double usdEur = quotes.getDouble("USDEUR");
        eur = usd / usdEur;
        if (!Double.isFinite(usd) || !Double.isFinite(usdEur) || !Double.isFinite(eur)
                || usd <= 0 || usdEur <= 0 || eur <= 0) throw new IllegalArgumentException("Invalid rates");
        long timestamp = data.getLong("timestamp");
        if (timestamp <= 0 || timestamp > System.currentTimeMillis() / 1000 + 300)
            throw new IllegalArgumentException("Invalid quote timestamp");
        date = java.time.Instant.ofEpochSecond(timestamp).atZone(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm", java.util.Locale.ENGLISH));
    }

    private static JSONObject config(android.content.Context context) throws Exception {
        try (java.io.FileInputStream input = context.openFileInput("currencylayer.json");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, length);
                if (bytes.size() > 4096) throw new IllegalArgumentException("Invalid currency configuration");
            }
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
    }

    static long interval(android.content.Context context) {
        try {
            int minutes = config(context).getInt("refreshMinutes");
            if (minutes >= 1 && minutes <= 1440) return minutes * 60 * 1000L;
        } catch (Exception ignored) { }
        return 24 * 60 * 60 * 1000L;
    }

    static String fetch(android.content.Context context) throws Exception {
        String key = config(context).getString("accessKey");
        if (!key.matches("[A-Za-z0-9]{16,64}")) throw new IllegalArgumentException("Currencylayer key missing");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(
            "https://api.currencylayer.com/live?currencies=INR,EUR&access_key=" + key).openConnection();
        // Never forward credentials to a redirect target or log this request URL.
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            if (connection.getResponseCode() != 200) throw new Exception("Server unavailable");
            try (InputStream stream = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    bytes.write(buffer, 0, length);
                    if (bytes.size() > 16384) throw new Exception("Response too large");
                }
                String raw = bytes.toString(StandardCharsets.UTF_8.name());
                new RatesData(raw);
                return raw;
            }
        } finally { connection.disconnect(); }
    }
}
