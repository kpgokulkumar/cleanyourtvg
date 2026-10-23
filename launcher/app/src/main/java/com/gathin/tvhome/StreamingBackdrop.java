package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional personal-fork feature. Network work happens only after an eligible dock focus. */
final class StreamingBackdrop {
    private final Activity activity;
    private final String token;
    private final File cache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
    private final FrameLayout art;
    private final ImageView image;
    private final LinearLayout details;
    private final TextView title, description, source;
    private int generation;
    private String focused;
    private boolean closed;
    private Runnable pending;
    private int slide;
    private Runnable rotate;

    static StreamingBackdrop create(Activity activity, FrameLayout root) {
        try {
            File imported = new File(activity.getExternalFilesDir(null), "tmdb-import.json");
            if (imported.isFile()) {
                if (imported.length() > 8192) return null;
                byte[] bytes = Files.readAllBytes(imported.toPath());
                new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                try (java.io.FileOutputStream out = activity.openFileOutput("tmdb.json", 0)) {
                    out.write(bytes);
                }
                Files.delete(imported.toPath());
            }
            String token = TmdbClient.token(activity);
            return token == null ? null : new StreamingBackdrop(activity, root, token);
        } catch (Exception e) {
            // Never log config contents, tokens or authenticated URLs.
            return null;
        }
    }

    private StreamingBackdrop(Activity activity, FrameLayout root, String token) {
        this.activity = activity;
        this.token = token;
        cache = new File(activity.getCacheDir(), "tmdb");
        worker.allowCoreThreadTimeOut(true);
        art = new FrameLayout(activity);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(image, new FrameLayout.LayoutParams(-1, -1));
        root.addView(art, root.indexOfChild(root.findViewById(R.id.heroArt)) + 1,
                new FrameLayout.LayoutParams(-1, -1));
        details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        source = text(11, 0xFFB7D3DF);
        source.setLetterSpacing(0.12f);
        title = text(36, 0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description = text(15, 0xFFCBD2DC);
        description.setLineSpacing(dp(3), 1f);
        description.setPadding(0, dp(12), 0, 0);
        description.setMaxLines(3);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(source);
        details.addView(title);
        details.addView(description);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(420), -2, Gravity.TOP | Gravity.START);
        lp.setMarginStart(dp(68));
        lp.topMargin = dp(166);
        root.addView(details, lp);
        hideViews();
    }

    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView text(int size, int color) {
        TextView v = new TextView(activity);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    void focus(String pkg) {
        if (closed || (pkg != null && pkg.equals(focused))) return;
        focused = pkg;
        slide = 0;
        if (rotate != null) ui.removeCallbacks(rotate);
        generation++;
        int request = generation;
        if (pending != null) ui.removeCallbacks(pending);
        worker.getQueue().clear();
        hideViews();
        boolean local = "com.google.android.youtube.tv".equals(pkg);
        if (local) {
            pending = () -> worker.execute(() -> loadWallpaper(request));
            ui.postDelayed(pending, 400);
            return;
        }
        String provider = StreamingPolicy.provider(pkg);
        if (provider == null) return;
        pending = () -> worker.execute(() -> load(pkg, provider, request));
        ui.postDelayed(pending, 400);
    }

    void stop() {
        generation++;
        focused = null;
        if (rotate != null) ui.removeCallbacks(rotate);
        if (pending != null) ui.removeCallbacks(pending);
        worker.getQueue().clear();
        hideViews();
    }

    void close() {
        closed = true;
        stop();
        worker.shutdownNow();
    }

    private void hideViews() {
        art.setVisibility(View.GONE);
        details.setVisibility(View.GONE);
        image.setImageDrawable(null);
        // Do not recycle a bitmap while RenderThread may still reference the previous frame.
    }

    private Bitmap decode(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0
                || options.outWidth > 16384 || options.outHeight > 16384) return null;
        android.util.DisplayMetrics screen = activity.getResources().getDisplayMetrics();
        options.inSampleSize = StreamingPolicy.sampleSize(options.outWidth, options.outHeight,
                screen.widthPixels, screen.heightPixels);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    private void loadWallpaper(int request) {
        File file = new File(activity.getExternalFilesDir(null), "youtube-wallpaper.jpg");
        if (!file.isFile() || file.length() > 5_000_000) return;
        Bitmap bitmap = decode(file);
        if (bitmap == null) return;
        ui.post(() -> {
            if (closed || request != generation) return;
            image.animate().cancel();
            image.setAlpha(0f);
            image.setImageBitmap(bitmap);
            art.setVisibility(View.VISIBLE);
            image.animate().alpha(1f).setDuration(650).start();
        });
    }

    private void load(String pkg, String provider, int request) {
        try {
            cache.mkdirs();
            File catalog = new File(cache, pkg + "-shelf.json");
            JSONObject list = read(catalog);
            long now = System.currentTimeMillis();
            if (list == null || !StreamingPolicy.fresh(list.optLong("saved"), now, StreamingPolicy.FRESH_MS)) {
                try {
                    list = fetchMovie(provider);
                    list.put("saved", now);
                    write(catalog, list.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    if (list == null || !StreamingPolicy.fresh(list.optLong("saved"), now, StreamingPolicy.OFFLINE_MS)) return;
                }
            }
            JSONArray items = list.getJSONArray("items");
            int index = slide % items.length();
            JSONObject info = items.getJSONObject(index);
            File file = new File(cache, pkg + "-original-" + index + ".jpg");
            if (!file.isFile() || file.lastModified() < catalog.lastModified()) {
                byte[] bytes = request("https://image.tmdb.org/t/p/original" + info.getString("backdrop_path"), false, 32_000_000);
                write(file, bytes);
            }
            Bitmap bitmap = decode(file);
            if (bitmap == null) return;
            int count = items.length();
            ui.post(() -> {
                if (closed || request != generation || !pkg.equals(focused)) return;
                image.animate().cancel();
                image.setAlpha(0f);
                image.setImageBitmap(bitmap);
                title.setText(info.optString("title"));
                description.setText(info.optString("overview"));
                source.setText(provider.toUpperCase(java.util.Locale.ROOT) + "  /  INDIA     "
                        + info.optString("kind") + " · " + info.optString("year") + "     " + (index + 1) + " / " + count);
                art.setVisibility(View.VISIBLE);
                details.setVisibility(View.VISIBLE);
                image.animate().alpha(1f).setDuration(650).start();
                rotate = () -> { slide++; worker.execute(() -> load(pkg, provider, request)); };
                if (count > 1) ui.postDelayed(rotate, 14000);
            });
        } catch (Exception e) {
            // A missing/offline item must not freeze the rest of the cached selection.
            ui.post(() -> {
                if (!closed && request == generation && pkg.equals(focused)) {
                    rotate = () -> { slide++; worker.execute(() -> load(pkg, provider, request)); };
                    ui.postDelayed(rotate, 14000);
                }
            });
        }
    }

    private JSONObject fetchMovie(String provider) throws Exception {
        File providersFile = new File(cache, "providers.json");
        JSONObject providers = read(providersFile);
        if (providers == null || !StreamingPolicy.fresh(providersFile.lastModified(), System.currentTimeMillis(), StreamingPolicy.FRESH_MS)) {
            byte[] response = request("https://api.themoviedb.org/3/watch/providers/movie?watch_region=IN", true, 1_000_000);
            providers = new JSONObject(new String(response, StandardCharsets.UTF_8));
            write(providersFile, response);
        }
        JSONArray all = providers.getJSONArray("results");
        int id = -1;
        for (int i = 0; i < all.length(); i++) {
            JSONObject p = all.getJSONObject(i);
            if (provider.equalsIgnoreCase(p.optString("provider_name"))) id = p.getInt("provider_id");
        }
        if (id < 0) throw new IOException();
        JSONArray[] selections = {new JSONArray(), new JSONArray()};
        for (int kind = 0; kind < 2; kind++) {
            String date = kind == 0 ? "primary_release_date" : "first_air_date";
            String query = "https://api.themoviedb.org/3/discover/" + (kind == 0 ? "movie" : "tv")
                    + "?watch_region=IN&with_watch_providers=" + id
                    + "&with_watch_monetization_types=flatrate&include_adult=false&vote_count.gte=20&sort_by="
                    + date + ".desc&" + date + ".lte=" + LocalDate.now();
            try {
                JSONArray results = new JSONObject(new String(request(query, true, 1_000_000), StandardCharsets.UTF_8)).getJSONArray("results");
                for (int i = 0; i < results.length() && selections[kind].length() < 3; i++) {
                    JSONObject item = results.getJSONObject(i);
                    if (item.optBoolean("adult") || !StreamingPolicy.imagePath(item.optString("backdrop_path"))) continue;
                    item.put("title", item.optString(kind == 0 ? "title" : "name"));
                    String year = item.optString(kind == 0 ? "release_date" : "first_air_date");
                    item.put("year", year.length() >= 4 ? year.substring(0, 4) : "");
                    item.put("kind", kind == 0 ? "FILM" : "SERIES");
                    selections[kind].put(item);
                }
            } catch (IOException e) { /* Keep the other media type if one endpoint is offline. */ }
        }
        JSONArray items = new JSONArray();
        for (int i = 0; i < 3; i++) for (JSONArray selection : selections)
            if (i < selection.length()) items.put(selection.getJSONObject(i));
        if (items.length() == 0) throw new IOException();
        return new JSONObject().put("items", items);
    }

    private byte[] request(String address, boolean authenticated, int max) throws IOException {
        return TmdbClient.request(address, authenticated ? token : null, max);
    }

    private JSONObject read(File file) {
        try {
            if (!file.isFile() || file.length() > 1_000_000) return null;
            return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    private void write(File file, byte[] bytes) throws IOException {
        File temp = new File(file.getPath() + ".tmp");
        Files.write(temp.toPath(), bytes);
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    void credits() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.tmdb_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        content.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(24)));
        TextView text = text(16, 0xFFE0E4EB);
        text.setText(R.string.streaming_credits);
        content.addView(text);
        new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.streaming_credits_title).setView(content)
                .setPositiveButton(android.R.string.ok, null).show();
    }
}
