package com.cleanyourtv.rates;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full rates screen and entry point to the optional Home overlay settings. */
public final class RatesActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView usd, eur, status, gold, goldStatus;
    private Button refresh;
    private SharedPreferences cache;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cache = getSharedPreferences("rates", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(64), dp(32), dp(64), dp(24));
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 22, 31));
        root.addView(text("CURRENCY RATES", 16, 0xff76dec3));
        TextView heading = text("Your daily rupee check", 32, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        LinearLayout cards = new LinearLayout(this);
        cards.setPadding(0, dp(22), 0, dp(18));
        usd = card(cards, "US DOLLAR  /  USD");
        eur = card(cards, "EURO  /  EUR");
        gold = card(cards, "22K GOLD / CHENNAI", "Indian rupees for 8 grams");
        gold.setTextSize(30);
        root.addView(cards);
        status = text("Fetching latest rates…", 16, 0xffc0ccd5);
        root.addView(status);
        root.addView(text("Currencylayer · Provider quote time in IST · Bank rates may differ", 13, 0xff91a6b5));
        goldStatus = text("Gold: fetching dated quote…", 12, 0xff91a6b5);
        root.addView(goldStatus);
        refresh = new Button(this);
        refresh.setText("Refresh rates");
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(dp(200), dp(52));
        button.topMargin = dp(16);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(refresh, button);
        Button overlay = new Button(this);
        overlay.setText("Home overlay settings");
        LinearLayout.LayoutParams overlayLayout = new LinearLayout.LayoutParams(dp(270), dp(52));
        overlayLayout.topMargin = dp(16);
        overlayLayout.setMarginStart(dp(16));
        actions.addView(overlay, overlayLayout);
        overlay.setOnClickListener(v -> {
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (android.content.ActivityNotFoundException error) {
                android.widget.Toast.makeText(this, "Open TV Settings > Accessibility", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(actions);
        refresh.setOnClickListener(v -> load());
        setContentView(root);
        refresh.requestFocus();
        showCache();
        showGold(false);
        load();
    }

    private TextView card(LinearLayout row, String label) {
        return card(row, label, "Indian rupees for 1 unit");
    }

    private TextView card(LinearLayout row, String label, String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff142e39);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(140), 1);
        layout.setMarginEnd(dp(16));
        row.addView(box, layout);
        box.addView(text(label, 15, 0xff76dec3));
        TextView value = text("—", 36, Color.WHITE);
        value.setTypeface(null, Typeface.BOLD);
        box.addView(value);
        box.addView(text(caption, 14, 0xff91a6b5));
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void showCache() {
        String raw = cache.getString("currencylayer_result", null);
        if (raw == null) return;
        try { render(new RatesData(raw), "Saved · "); }
        catch (Exception ignored) { cache.edit().remove("currencylayer_result").apply(); }
    }

    private void render(RatesData data, String prefix) {
        usd.setText(String.format(Locale.US, "₹ %,.2f", data.usd));
        eur.setText(String.format(Locale.US, "₹ %,.2f", data.eur));
        status.setText(prefix + "Quote time: " + data.date + " IST");
    }

    private void showGold(boolean failed) {
        try {
            GoldData data = new GoldData(cache.getString("gold_result", ""));
            gold.setText("₹ " + java.text.NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(data.eightGrams));
            goldStatus.setText("Gold: " + data.date + " · Golden Chennai · Base metal quote; charges extra"
                + (failed ? " · Saved" : ""));
        } catch (Exception ignored) {
            goldStatus.setText(failed ? "Gold unavailable. Try Refresh." : "Gold: fetching dated quote…");
        }
    }

    private void load() {
        refresh.setEnabled(false);
        status.setText("Updating…" + (cache.contains("currencylayer_result") ? " Showing saved rates." : ""));
        worker.execute(() -> {
            String result = null;
            try { result = RatesData.fetch(this); }
            catch (Exception ignored) { /* Keep saved rates when offline. */ }
            String goldRaw = null;
            try { goldRaw = GoldData.fetch(); } catch (Exception ignored) { }
            final String response = result, goldResponse = goldRaw;
            runOnUiThread(() -> {
                if (destroyed) return;
                boolean success = false;
                if (response != null) {
                    try {
                        render(new RatesData(response), "Updated · ");
                        cache.edit().putString("currencylayer_result", response)
                            .putLong("currencylayer_fetched_at", System.currentTimeMillis()).apply();
                        success = true;
                    } catch (Exception ignored) { /* Invalid responses never overwrite the cache. */ }
                }
                if (!success) {
                    showCache();
                    status.setText(cache.contains("currencylayer_result")
                        ? status.getText() + " · Update unavailable"
                        : "Rates unavailable. Check your connection and try Refresh.");
                }
                if (goldResponse != null) cache.edit().putString("gold_result", goldResponse)
                    .putLong("gold_fetched_at", System.currentTimeMillis()).apply();
                showGold(goldResponse == null);
                refresh.setEnabled(true);
                refresh.requestFocus();
            });
        });
    }

    @Override public void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        super.onDestroy();
    }
}
