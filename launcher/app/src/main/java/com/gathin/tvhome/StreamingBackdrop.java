package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Optional personal-fork feature: the focused dock app's newest titles from TMDB, full screen behind Home, with the
 * title and synopsis at the bottom left. Network work happens only after a dock app has held focus for a moment,
 * only over HTTPS to TMDB's two hosts (TmdbClient, network_security_config), and the cache stays small.
 */
final class StreamingBackdrop {
    /** Seconds each title stays before the next one fades in. */
    private static final long SLIDE_MS = 14_000L;
    private static final long FADE_MS = 900L;
    /** Focus has to rest this long before anything is loaded, so passing over the dock costs nothing. */
    private static final long SETTLE_MS = 400L;

    private final Activity activity;
    private final String token;
    private final File cache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
    private final FrameLayout art;
    /** Two layers: the new title fades in over the old one instead of the screen dipping to black. */
    private final ImageView[] layers = new ImageView[2];
    private int front;
    private final LinearLayout details;
    private final TextView eyebrow, title, synopsis;
    private final LinearLayout progress;
    private int generation;
    private String focused;
    private boolean closed;
    private Runnable pending;
    private int slide;
    private Runnable rotate;

    static StreamingBackdrop create(Activity activity, FrameLayout root, boolean lounge) {
        try {
            File imported = new File(activity.getExternalFilesDir(null), "tmdb-import.json");
            if (imported.isFile()) {
                // Shared storage on Android 9 is writable by other apps: take the file once, then delete it.
                if (imported.length() > 8192) {
                    Files.delete(imported.toPath());
                    return null;
                }
                byte[] bytes = Files.readAllBytes(imported.toPath());
                new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                try (java.io.FileOutputStream out = activity.openFileOutput("tmdb.json", 0)) {
                    out.write(bytes);
                }
                Files.delete(imported.toPath());
            }
            String token = TmdbClient.token(activity);
            return token == null ? null : new StreamingBackdrop(activity, root, token, lounge);
        } catch (Exception e) {
            // Never log config contents, tokens or authenticated URLs.
            return null;
        }
    }

    private StreamingBackdrop(Activity activity, FrameLayout root, String token, boolean lounge) {
        this.activity = activity;
        this.token = token;
        cache = new File(activity.getCacheDir(), "tmdb");
        worker.allowCoreThreadTimeOut(true);

        art = new FrameLayout(activity);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < layers.length; i++) {
            ImageView layer = new ImageView(activity);
            layer.setScaleType(ImageView.ScaleType.CENTER_CROP);
            layer.setAlpha(0f);
            art.addView(layer, new FrameLayout.LayoutParams(-1, -1));
            layers[i] = layer;
        }
        // One shade for every edge that carries text: the clock at the top, the details at the bottom left and the
        // controls at the bottom right. Drawn once; the artwork moves under it.
        art.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, 0x8C000000, 0x00000000), new FrameLayout.LayoutParams(-1, dp(190), Gravity.TOP));
        art.addView(shade(GradientDrawable.Orientation.BOTTOM_TOP, 0xE6000000, 0x00000000), new FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM));
        art.addView(shade(GradientDrawable.Orientation.LEFT_RIGHT, 0xB3000000, 0x00000000), new FrameLayout.LayoutParams(dp(560), -1, Gravity.START));
        root.addView(art, root.indexOfChild(root.findViewById(R.id.heroArt)) + 1, new FrameLayout.LayoutParams(-1, -1));

        details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        eyebrow = text(R.string.font_medium, 10, R.color.ink_secondary);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setAllCaps(true);
        eyebrow.setSingleLine(true);
        title = text(R.string.font_light, 30, R.color.ink_primary);
        title.setLetterSpacing(-0.01f);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        synopsis = text(R.string.font_regular, 13, R.color.ink_secondary);
        synopsis.setLineSpacing(dp(3), 1f);
        synopsis.setMaxLines(2);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        progress = new LinearLayout(activity);
        progress.setOrientation(LinearLayout.HORIZONTAL);

        details.addView(eyebrow);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        details.addView(title, titleLp);
        LinearLayout.LayoutParams synopsisLp = new LinearLayout.LayoutParams(-1, -2);
        synopsisLp.topMargin = dp(8);
        details.addView(synopsis, synopsisLp);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-2, dp(3));
        progressLp.topMargin = dp(16);
        details.addView(progress, progressLp);

        FrameLayout.LayoutParams lp;
        if (lounge) {
            // Bottom left, on the same baseline as the dock row at the bottom right.
            lp = new FrameLayout.LayoutParams(dp(460), -2, Gravity.BOTTOM | Gravity.START);
            lp.bottomMargin = px(R.dimen.edge_bottom);
        } else {
            lp = new FrameLayout.LayoutParams(dp(420), -2, Gravity.TOP | Gravity.START);
            lp.topMargin = dp(166);
        }
        lp.setMarginStart(px(R.dimen.edge_side));
        root.addView(details, root.indexOfChild(art) + 1, lp);
        hideViews();
    }

    /** A gradient from {@code dark} at one edge to {@code clear}, half strength at its middle. */
    private View shade(GradientDrawable.Orientation from, int dark, int clear) {
        View v = new View(activity);
        v.setBackground(new GradientDrawable(from, new int[]{dark, (dark & 0x00FFFFFF) | ((dark >>> 25) << 24), clear}));
        return v;
    }

    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }

    private int px(int dimen) { return activity.getResources().getDimensionPixelSize(dimen); }

    private TextView text(int font, int size, int color) {
        TextView v = new TextView(activity);
        v.setTypeface(UiFont.get(activity, font));
        v.setTextSize(size);
        v.setTextColor(activity.getColor(color));
        v.setIncludeFontPadding(false);
        v.setShadowLayer(dp(8), 0, 0, 0x66000000);
        // Whole words only: Android 9 hyphenates by default, which breaks a synopsis into "mur-der".
        v.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
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
        if ("com.google.android.youtube.tv".equals(pkg)) {
            pending = () -> worker.execute(() -> loadWallpaper(request));
            ui.postDelayed(pending, SETTLE_MS);
            return;
        }
        String provider = StreamingPolicy.provider(pkg);
        if (provider == null) {
            hideViews();
            return;
        }
        pending = () -> worker.execute(() -> load(pkg, provider, request));
        ui.postDelayed(pending, SETTLE_MS);
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
        for (ImageView layer : layers) {
            layer.animate().cancel();
            layer.setAlpha(0f);
            // Do not recycle a bitmap while RenderThread may still reference the previous frame.
            layer.setImageDrawable(null);
        }
    }

    /** Fades the new picture in over the old one, then lets the old one go. */
    private void present(Bitmap bitmap) {
        ImageView incoming = layers[1 - front];
        ImageView outgoing = layers[front];
        front = 1 - front;
        incoming.animate().cancel();
        incoming.setImageBitmap(bitmap);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        // The shades stay above both layers.
        for (int i = art.getChildCount() - 1; i >= 0; i--) {
            View child = art.getChildAt(i);
            if (child != layers[0] && child != layers[1]) child.bringToFront();
        }
        art.setVisibility(View.VISIBLE);
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            if (layers[front] == incoming) {
                outgoing.setAlpha(0f);
                outgoing.setImageDrawable(null);
            }
        }).start();
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
            details.setVisibility(View.GONE);
            present(bitmap);
        });
    }

    private void load(String pkg, String provider, int request) {
        try {
            if (!cache.isDirectory() && !cache.mkdirs()) return;
            File catalog = new File(cache, pkg + "-shelf.json");
            JSONObject list = read(catalog);
            long now = System.currentTimeMillis();
            if (list == null || !StreamingPolicy.fresh(list.optLong("saved"), now, StreamingPolicy.FRESH_MS)) {
                try {
                    list = fetchShelf(provider);
                    list.put("saved", now);
                    write(catalog, list.toString().getBytes(StandardCharsets.UTF_8));
                    StreamingPolicy.prune(cache.listFiles(), StreamingPolicy.CACHE_BYTES);
                } catch (Exception e) {
                    if (list == null || !StreamingPolicy.fresh(list.optLong("saved"), now, StreamingPolicy.OFFLINE_MS)) return;
                }
            }
            JSONArray items = list.getJSONArray("items");
            int index = slide % items.length();
            JSONObject info = items.getJSONObject(index);
            String path = info.getString("backdrop_path");
            if (!StreamingPolicy.imagePath(path)) throw new IOException();
            File file = new File(cache, pkg + "-" + StreamingPolicy.IMAGE_SIZE + "-" + index + ".jpg");
            if (!file.isFile() || file.lastModified() < catalog.lastModified()) {
                write(file, request(StreamingPolicy.imageUrl(path), false, StreamingPolicy.IMAGE_BYTES));
            }
            Bitmap bitmap = decode(file);
            if (bitmap == null) return;
            int count = items.length();
            ui.post(() -> {
                if (closed || request != generation || !pkg.equals(focused)) return;
                eyebrow.setText(StreamingPolicy.eyebrow(BrandName.of(pkg, provider), info.optString("kind"), info.optString("year")));
                title.setText(info.optString("title"));
                synopsis.setText(info.optString("overview"));
                showProgress(index, count);
                details.setVisibility(View.VISIBLE);
                present(bitmap);
                scheduleNext(pkg, provider, request, count);
            });
        } catch (Exception e) {
            // A missing or offline item must not freeze the rest of the cached selection.
            ui.post(() -> {
                if (!closed && request == generation && pkg.equals(focused)) scheduleNext(pkg, provider, request, 2);
            });
        }
    }

    private void scheduleNext(String pkg, String provider, int request, int count) {
        if (count < 2) return;
        rotate = () -> { slide++; worker.execute(() -> load(pkg, provider, request)); };
        ui.postDelayed(rotate, SLIDE_MS);
    }

    /** One short bar per title, the current one long and bright: where you are in the rotation, without numbers. */
    private void showProgress(int index, int count) {
        progress.removeAllViews();
        if (count < 2) return;
        for (int i = 0; i < count; i++) {
            View bar = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(2));
            shape.setColor(i == index ? 0xE6FFFFFF : 0x40FFFFFF);
            bar.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == index ? 18 : 6), dp(3));
            if (i > 0) lp.setMarginStart(dp(5));
            progress.addView(bar, lp);
        }
    }

    private JSONObject fetchShelf(String provider) throws Exception {
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
                    // Keep only what is shown; nothing else from the response is stored.
                    String year = item.optString(kind == 0 ? "release_date" : "first_air_date");
                    selections[kind].put(new JSONObject()
                            .put("title", item.optString(kind == 0 ? "title" : "name"))
                            .put("overview", item.optString("overview"))
                            .put("backdrop_path", item.optString("backdrop_path"))
                            .put("year", year.length() >= 4 ? year.substring(0, 4) : "")
                            .put("kind", kind == 0 ? "Film" : "Series"));
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
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    void credits() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.tmdb_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        content.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(24)));
        TextView text = text(R.string.font_regular, 16, R.color.ink_primary);
        text.setText(R.string.streaming_credits);
        content.addView(text);
        new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.streaming_credits_title).setView(content)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    /** The service's name as people say it, for the eyebrow: the dock's mark name when there is one. */
    private static final class BrandName {
        static String of(String pkg, String provider) {
            BrandMarks.Mark mark = BrandMarks.of(pkg);
            return (mark != null ? mark.name : provider).toUpperCase(Locale.ROOT);
        }
    }
}
