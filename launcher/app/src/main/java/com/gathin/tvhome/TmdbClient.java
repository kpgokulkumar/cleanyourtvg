package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.net.ssl.HttpsURLConnection;

/** Shared bounded HTTPS transport. Tokens never appear in URLs or logs. */
final class TmdbClient {
    static String token(Context context) throws Exception {
        File config = new File(context.getFilesDir(), "tmdb.json");
        if (!config.isFile() || config.length() > 8192) return null;
        JSONObject data = new JSONObject(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
        String token = data.optString("token").trim();
        if (!data.optBoolean("enabled") || token.isEmpty() || token.contains("\n") || token.contains("\r")) return null;
        return token;
    }
    static JSONObject get(Context context, String path) throws Exception {
        String token = token(context);
        if (token == null) throw new IOException();
        return new JSONObject(new String(request("https://api.themoviedb.org/3" + path, token, 1_000_000), StandardCharsets.UTF_8));
    }
    static byte[] request(String address, String token, int max) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !("api.themoviedb.org".equals(url.getHost())
                || "image.tmdb.org".equals(url.getHost()))) throw new IOException();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", token != null ? "application/json" : "image/jpeg");
        if (token != null && "api.themoviedb.org".equals(url.getHost())) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (connection.getResponseCode() != 200) throw new IOException();
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > max) throw new IOException();
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

}
