package com.cleanyourtv.rates;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** An opt-in, non-interactive overlay, bound by Android only while enabled in Accessibility. */
public final class HomeOverlayService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private LinearLayout panel;
    private TextView usdValue, eurValue, date, goldValue, goldDate;
    private SharedPreferences cache;
    private boolean home, fetching, destroyed;
    private long nextAttempt;
    private final Runnable tick = this::update;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { hide(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener cacheChanged = (prefs, key) -> {
        if (panel != null && ("currencylayer_result".equals(key) || "gold_result".equals(key))) { render(false); renderGold(false); }
    };

    @Override protected void onServiceConnected() {
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        cache = getSharedPreferences("rates", MODE_PRIVATE);
        cache.registerOnSharedPreferenceChangeListener(cacheChanged);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        // Wait for the next genuine Home event rather than guessing the foreground app.
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (HomeVisibility.isHome(pkg, cls)) show();
        else hide();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    // Scale only vertical spacing and type; retain the panel's width and Home anchor.
    private int compact(int value) {
        return Math.round(value * 0.75f * getResources().getDisplayMetrics().density);
    }

    private void show() {
        if (windows == null || destroyed) return;
        home = true;
        if (panel == null) {
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(20), compact(17), dp(20), compact(14));
            panel.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0x991c1b22);
            background.setCornerRadius(dp(24));
            background.setStroke(dp(1), 0x26ffffff);
            panel.setBackground(background);

            LinearLayout quotes = new LinearLayout(this);
            quotes.setGravity(Gravity.CENTER_VERTICAL);
            usdValue = quote(quotes, "USD / INR");
            android.view.View divider = new android.view.View(this);
            divider.setBackgroundColor(0x26ffffff);
            LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(dp(1), compact(37));
            rule.setMargins(dp(14), 0, dp(17), 0);
            quotes.addView(divider, rule);
            eurValue = quote(quotes, "EUR / INR");
            panel.addView(quotes);
            date = line("UPDATING RATES", 9, 0x99ffffff);
            date.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            footer.topMargin = compact(11);
            panel.addView(date, footer);
            android.view.View separator = new android.view.View(this);
            separator.setBackgroundColor(0x26ffffff);
            LinearLayout.LayoutParams separatorLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            separatorLayout.setMargins(0, compact(14), 0, compact(14));
            panel.addView(separator, separatorLayout);
            TextView goldLabel = line("22K GOLD · CHENNAI · 8G", 10, 0xb3ffffff);
            goldLabel.setLetterSpacing(0.08f);
            panel.addView(goldLabel);
            goldValue = line("—", 25, 0xf2ffffff);
            goldValue.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            goldValue.setFontFeatureSettings("tnum");
            LinearLayout.LayoutParams goldAmount = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            goldAmount.topMargin = compact(4);
            panel.addView(goldValue, goldAmount);
            goldDate = line("UPDATING GOLD", 9, 0x99ffffff);
            goldDate.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams goldFooter = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            goldFooter.topMargin = compact(8);
            panel.addView(goldDate, goldFooter);
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.RIGHT;
            layout.x = dp(56);
            layout.y = dp(116);
            try { windows.addView(panel, layout); }
            catch (RuntimeException error) { panel = null; home = false; return; }
        }
        render(false);
        renderGold(false);
        update();
    }

    private TextView quote(LinearLayout row, String label) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView currency = line(label, 10, 0xb3ffffff);
        currency.setLetterSpacing(0.12f);
        column.addView(currency);
        TextView value = line("—", 25, 0xf2ffffff);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setSingleLine(true);
        value.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams amount = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        amount.topMargin = compact(3);
        column.addView(value, amount);
        return value;
    }

    private TextView line(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size * 0.75f);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private void render(boolean failed) {
        if (panel == null) return;
        try {
            RatesData data = new RatesData(cache.getString("currencylayer_result", ""));
            usdValue.setText(String.format(Locale.US, "%,.2f", data.usd));
            eurValue.setText(String.format(Locale.US, "%,.2f", data.eur));
            date.setText(data.date.toUpperCase(Locale.ENGLISH) + " IST · " + (failed ? "SAVED" : "CURRENCYLAYER"));
        } catch (Exception ignored) {
            usdValue.setText("—");
            eurValue.setText("—");
            date.setText(failed ? "RATES UNAVAILABLE" : "UPDATING RATES");
        }
    }

    private void renderGold(boolean failed) {
        if (panel == null) return;
        try {
            GoldData data = new GoldData(cache.getString("gold_result", ""));
            goldValue.setText("₹" + java.text.NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(data.eightGrams));
            String day = java.time.LocalDate.parse(data.date).format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
            goldDate.setText(day + " · GOLDEN CHENNAI" + (failed ? " · SAVED" : ""));
        } catch (Exception ignored) {
            goldValue.setText("—");
            goldDate.setText(failed ? "GOLD UNAVAILABLE" : "UPDATING GOLD");
        }
    }

    private boolean due(String key, long now) {
        long fetched = cache.getLong(key, 0);
        long interval = "currencylayer_fetched_at".equals(key) ? RatesData.interval(this) : 6 * 60 * 60 * 1000L;
        return now < fetched || now - fetched >= interval;
    }

    private void update() {
        main.removeCallbacks(tick);
        if (!home || destroyed) return;
        long now = System.currentTimeMillis();
        boolean fetchFx = due("currencylayer_fetched_at", now), fetchGold = due("gold_fetched_at", now);
        if (fetching || now < nextAttempt || (!fetchFx && !fetchGold)) {
            main.postDelayed(tick, Math.min(RatesData.interval(this), 60 * 60 * 1000L));
            return;
        }
        fetching = true;
        worker.execute(() -> {
            String raw = null, goldRaw = null;
            if (fetchFx) try { raw = RatesData.fetch(this); } catch (Exception ignored) { }
            if (fetchGold) try { goldRaw = GoldData.fetch(); } catch (Exception ignored) { }
            final String result = raw, goldResult = goldRaw;
            main.post(() -> {
                fetching = false;
                if (destroyed) return;
                SharedPreferences.Editor editor = cache.edit();
                if (result != null) editor.putString("currencylayer_result", result).putLong("currencylayer_fetched_at", System.currentTimeMillis());
                if (goldResult != null) editor.putString("gold_result", goldResult).putLong("gold_fetched_at", System.currentTimeMillis());
                editor.apply();
                boolean failed = (fetchFx && result == null) || (fetchGold && goldResult == null);
                nextAttempt = failed ? System.currentTimeMillis() + 15 * 60 * 1000L : 0;
                render(fetchFx && result == null);
                renderGold(fetchGold && goldResult == null);
                if (home) main.postDelayed(tick, failed ? 15 * 60 * 1000L : Math.min(RatesData.interval(this), 6 * 60 * 60 * 1000L));
            });
        });
    }

    private void hide() {
        home = false;
        main.removeCallbacks(tick);
        if (panel != null) {
            try { windows.removeViewImmediate(panel); } catch (RuntimeException ignored) { }
            panel = null;
        }
    }

    @Override public void onInterrupt() { hide(); }
    @Override public boolean onUnbind(Intent intent) { hide(); return super.onUnbind(intent); }
    @Override public void onDestroy() {
        destroyed = true;
        hide();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        if (cache != null) cache.unregisterOnSharedPreferenceChangeListener(cacheChanged);
        try { unregisterReceiver(screenOff); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }
}
