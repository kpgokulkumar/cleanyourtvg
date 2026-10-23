package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Personal-fork weekly viewing board. Entries live only in private preferences. */
public final class PlannerActivity extends Activity {
    private final List<PlannerEntry> entries = new ArrayList<>();
    private final List<List<View>> targets = new ArrayList<>();
    private final String[] days = new String[7];
    private static final String[] PACKAGES = {"", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar", "com.sonyliv", "com.graymatrix.did", "com.suntv.sunnxt", "com.google.android.youtube.tv"};
    private int selected;
    private TextView back;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int request;
    private AlertDialog loading;
    private boolean active;

    @Override protected void onStart() { super.onStart(); active = true; }
    @Override protected void onStop() {
        active = false; request++;
        if (loading != null) loading.dismiss();
        super.onStop();
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        selected = LocalDate.now().getDayOfWeek().getValue() - 1;
        for (int d = 0; d < 7; d++) days[d] = DayOfWeek.of(d + 1).getDisplayName(TextStyle.FULL, Locale.getDefault());
        try {
            JSONArray data = new JSONArray(getPreferences(MODE_PRIVATE).getString("entries", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                try {
                    PlannerEntry entry = new PlannerEntry(item.getString("title"), item.optString("app"));
                    entry.date = item.optString("date"); entry.media = item.optString("media");
                    entry.tmdbId = item.optInt("tmdbId");
                    if (!entry.date.isEmpty()) LocalDate.parse(entry.date);
                    entries.add(entry);
                }
                catch (RuntimeException ignored) { }
            }
        } catch (Exception e) { Toast.makeText(this, R.string.planner_read_failed, Toast.LENGTH_LONG).show(); }
        render();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        return v;
    }
    private TextView button(String value, Runnable action) {
        TextView v = text(value, 15, 0xFFFFFFFF);
        v.setId(View.generateViewId());
        v.setFocusable(true); v.setClickable(true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(10), dp(12), dp(10), dp(12));
        v.setBackgroundResource(R.drawable.settings_choice_bg);
        v.setOnClickListener(w -> action.run());
        return v;
    }
    private String appName(String pkg) {
        String[] names = getResources().getStringArray(R.array.planner_apps);
        for (int i = 0; i < PACKAGES.length; i++) if (PACKAGES[i].equals(pkg)) return names[i];
        return names[0];
    }
    private void render() {
        LinearLayout page = column();
        page.setPadding(dp(32), dp(22), dp(32), dp(20));
        page.setBackgroundColor(0xFF080E1B);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        back = button(getString(R.string.planner_back), this::finish);
        header.addView(back);
        TextView heading = text(getString(R.string.planner_title), 30, 0xFFFFFFFF);
        heading.setPadding(dp(20), 0, 0, 0);
        header.addView(heading);
        TextView undated = button(getString(R.string.release_unannounced), this::unannounced);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
        up.leftMargin = dp(20); header.addView(undated, up);
        page.addView(header);
        TextView hint = text(getString(R.string.planner_hint), 13, 0xFFADB9CC);
        hint.setPadding(0, dp(10), 0, dp(20));
        page.addView(hint);
        LinearLayout board = new LinearLayout(this);
        page.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        targets.clear();
        int today = LocalDate.now().getDayOfWeek().getValue() - 1;
        entries.sort(Comparator.comparing((PlannerEntry e) -> e.date).thenComparing(e -> e.title));
        for (int d = 0; d < 7; d++) {
            final int day = d;
            List<View> focus = new ArrayList<>(); targets.add(focus);
            LinearLayout lane = column();
            lane.setPadding(dp(5), dp(12), dp(5), dp(4));
            lane.setBackgroundColor(d == today ? 0xFF15263B : 0xFF101A29);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
            cell.setMargins(dp(3), 0, dp(3), 0); board.addView(lane, cell);
            TextView label = text(days[d], 16, d == today ? 0xFF9ED9E8 : 0xFFE0E7F0);
            label.setGravity(Gravity.CENTER); label.setPadding(0, 0, 0, dp(12)); lane.addView(label);
            TextView add = button(getString(R.string.planner_add), () -> search(day));
            lane.addView(add); focus.add(add);
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
            lane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout cards = column(); scroll.addView(cards);
            for (PlannerEntry entry : entries) if (!entry.date.isEmpty() && LocalDate.parse(entry.date).getDayOfWeek().getValue() - 1 == d) {
                TextView card = button(entry.date + "\n" + getString(R.string.release_time_unknown) + "\n\n" + entry.title
                        + (entry.app.isEmpty() ? "" : "\n\n" + appName(entry.app)), () -> actions(entry));
                card.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.topMargin = dp(8); cards.addView(card, cp); focus.add(card);
            }

        }
        for (int d = 0; d < 7; d++) for (int row = 0; row < targets.get(d).size(); row++) {
            View v = targets.get(d).get(row);
            List<View> left = targets.get(Math.max(0, d - 1)), right = targets.get(Math.min(6, d + 1));
            v.setNextFocusLeftId(left.get(Math.min(row, left.size() - 1)).getId());
            v.setNextFocusRightId(right.get(Math.min(row, right.size() - 1)).getId());
            v.setNextFocusUpId(row == 0 ? back.getId() : targets.get(d).get(row - 1).getId());
            v.setNextFocusDownId(targets.get(d).get(Math.min(row + 1, targets.get(d).size() - 1)).getId());
        }
        back.setNextFocusDownId(targets.get(selected).get(0).getId());
        setContentView(page);
        targets.get(selected).get(0).requestFocus();
    }
    private void actions(PlannerEntry entry) {
        if (!entry.date.isEmpty()) selected = LocalDate.parse(entry.date).getDayOfWeek().getValue() - 1;
        List<String> choices = new ArrayList<>();
        if (!entry.app.isEmpty()) choices.add(getString(R.string.planner_open));
        if (entry.tmdbId > 0) choices.add(getString(R.string.release_refresh));
        choices.add(getString(R.string.planner_remove));
        new AlertDialog.Builder(this).setTitle(entry.title).setItems(choices.toArray(new String[0]), (dialog, which) -> {
            if (!entry.app.isEmpty() && which == 0) {
                Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(entry.app);
                if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(entry.app);
                try {
                    if (intent == null) throw new android.content.ActivityNotFoundException();
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, R.string.planner_app_missing, Toast.LENGTH_LONG).show();
                }
            } else if (entry.tmdbId > 0 && which == choices.size() - 2) details(entry.media, entry.tmdbId, entry);
            else new AlertDialog.Builder(this).setTitle(R.string.planner_remove).setMessage(entry.title)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.planner_remove, (w, n) -> save(entry, null)).show();
        }).show();
    }
    private void unannounced() {
        List<PlannerEntry> list = new ArrayList<>();
        for (PlannerEntry entry : entries) if (entry.date.isEmpty()) list.add(entry);
        if (list.isEmpty()) { Toast.makeText(this, R.string.release_no_saved, Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).title;
        new AlertDialog.Builder(this).setTitle(R.string.release_unannounced)
                .setItems(names, (dialog, which) -> actions(list.get(which))).show();
    }
    private void lookup(String path, Consumer<JSONObject> result) {
        int generation = ++request;
        loading = new AlertDialog.Builder(this).setMessage(R.string.release_loading)
                .setNegativeButton(android.R.string.cancel, (d, n) -> request++)
                .setOnCancelListener(d -> request++).show();
        worker.execute(() -> {
            JSONObject data = null;
            try { data = TmdbClient.get(this, path); } catch (Exception ignored) { }
            JSONObject response = data;
            runOnUiThread(() -> {
                if (!active || generation != request) return;
                loading.dismiss();
                if (response == null) new AlertDialog.Builder(this).setMessage(R.string.release_failed)
                        .setPositiveButton(android.R.string.ok, null).show();
                else result.accept(response);
            });
        });
    }
    private void search(int day) {
        selected = day;
        EditText query = new EditText(this);
        query.setSingleLine(true); query.setHint(R.string.planner_name);
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.release_search)
                .setView(query).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.release_search, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = query.getText().toString().trim();
            if (value.isEmpty()) { query.setError(getString(R.string.planner_name)); return; }
            dialog.dismiss();
            lookup("/search/multi?include_adult=false&query=" + android.net.Uri.encode(value), this::results);
        }));
        dialog.show();
    }
    private void results(JSONObject data) {
        JSONArray all = data.optJSONArray("results");
        List<JSONObject> matches = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (all != null) for (int i = 0; i < all.length() && matches.size() < 12; i++) {
            JSONObject item = all.optJSONObject(i);
            if (item == null || item.optBoolean("adult") || item.optInt("id") <= 0) continue;
            String type = item.optString("media_type");
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            String name = item.optString(type.equals("movie") ? "title" : "name");
            if (name.isEmpty() || name.length() > 100) continue;
            String date = item.optString(type.equals("movie") ? "release_date" : "first_air_date");
            matches.add(item); labels.add(name + " · " + type + (date.length() >= 4 ? " · " + date.substring(0, 4) : ""));
        }
        if (matches.isEmpty()) { Toast.makeText(this, R.string.release_no_results, Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.release_search).setItems(labels.toArray(new String[0]), (d, which) -> {
            JSONObject item = matches.get(which); details(item.optString("media_type"), item.optInt("id"), null);
        }).show();
    }
    private void details(String type, int id, PlannerEntry old) {
        lookup("/" + type + "/" + id + "?append_to_response=watch/providers" + (type.equals("movie") ? ",release_dates" : ""), data -> {
            String name = data.optString(type.equals("movie") ? "title" : "name");
            String date = "";
            JSONObject releases = data.optJSONObject("release_dates");
            JSONArray countries = releases == null ? null : releases.optJSONArray("results");
            if (type.equals("movie") && countries != null) for (int i = 0; i < countries.length(); i++) {
                JSONObject country = countries.optJSONObject(i);
                if (country == null) continue;
                JSONArray dates = country.optJSONArray("release_dates");
                if (dates == null) continue;
                for (int n = 0; n < dates.length(); n++) {
                    JSONObject release = dates.optJSONObject(n);
                    if (release != null) date = ReleasePolicy.choose(date, ReleasePolicy.digitalDate(country.optString("iso_3166_1"),
                            release.optInt("type"), release.optString("release_date")), LocalDate.now().toString());
                }
            }
            String app = "";
            JSONObject providers = data.optJSONObject("watch/providers");
            JSONObject regions = providers == null ? null : providers.optJSONObject("results");
            JSONObject india = regions == null ? null : regions.optJSONObject("IN");
            JSONArray available = india == null ? null : india.optJSONArray("flatrate");
            if (available != null) for (int i = 0; i < available.length(); i++) {
                JSONObject provider = available.optJSONObject(i);
                if (provider == null) continue;
                for (String pkg : PACKAGES) if (provider.optString("provider_name").equalsIgnoreCase(StreamingPolicy.provider(pkg))) {
                    if (app.isEmpty() || getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null) app = pkg;
                }
            }
            PlannerEntry entry;
            try { entry = new PlannerEntry(name, app); }
            catch (RuntimeException e) { Toast.makeText(this, R.string.release_no_results, Toast.LENGTH_LONG).show(); return; }
            entry.date = date; entry.media = type; entry.tmdbId = id;
            String info = (date.isEmpty() ? getString(R.string.release_date_unknown) : getString(R.string.release_india_date) + " " + date)
                    + "\n" + getString(R.string.release_time_unknown) + "\n\n"
                    + (app.isEmpty() ? getString(R.string.release_app_unknown) : getString(R.string.release_app) + " " + appName(app))
                    + "\n\n" + getString(R.string.release_attribution);
            new AlertDialog.Builder(this).setTitle(name).setMessage(info)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.planner_save, (d, n) -> {
                        PlannerEntry previous = old;
                        for (PlannerEntry saved : entries) if (saved.tmdbId == id && type.equals(saved.media)) previous = saved;
                        if (save(previous, entry) && entry.date.isEmpty()) unannounced();
                    }).show();
        });
    }
    private boolean save(PlannerEntry old, PlannerEntry replacement) {
        List<PlannerEntry> updated = new ArrayList<>(entries);
        if (old != null) updated.remove(old);
        if (replacement != null) updated.add(replacement);
        JSONArray data = new JSONArray();
        try {
            for (PlannerEntry e : updated) data.put(new JSONObject().put("title", e.title).put("app", e.app).put("date", e.date).put("media", e.media).put("tmdbId", e.tmdbId));
            if (!getPreferences(MODE_PRIVATE).edit().putString("entries", data.toString()).commit()) throw new IllegalStateException();
        } catch (Exception e) { Toast.makeText(this, R.string.planner_save_failed, Toast.LENGTH_LONG).show(); return false; }
        entries.clear(); entries.addAll(updated);
        if (replacement != null && !replacement.date.isEmpty()) selected = LocalDate.parse(replacement.date).getDayOfWeek().getValue() - 1;
        render(); return true;
    }
}
