package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * home_layout "lounge": Home gives the screen to the artwork. At the bottom right, one column as wide as the
 * Currency Rates card: a row of quiet round controls (Inputs, network, planner, picture, settings) over a row of
 * round dock apps, and one caption naming whatever has focus. Down from the apps opens the app library.
 *
 * The two rows line up on the same centres, so Up and Down go to the button straight above or below; a control
 * that is hidden on this TV (no inputs, no backlight) simply leaves its column empty.
 */
final class LoungeHome {
    static final String LAYOUT = "lounge";
    /** A focused control's icon, as on the top bar's pills (drawable/pill_text). */
    private static final int PILL_INK = 0xFF111111;

    /** A key on Home brings the cluster back to full strength; after this long without one it steps back. */
    private static final long QUIET_AFTER_MS = 12_000L;
    private static final float QUIET_ALPHA = 0.42f;

    interface Host {
        void openLibrary();
        void onAppFocused(String pkg);
    }

    private final Activity activity;
    private final Host host;
    private final float density;
    private final boolean motion;
    private final LinearLayout cluster;
    private final FrameLayout controlRow;
    private final FrameLayout appRow;
    private final TextView controlCaption;
    private final TextView appCaption;
    private final List<View> controls = new ArrayList<>();
    private final List<View> apps = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private final Runnable quiet = this::stepBack;
    private int selected;
    private boolean quietNow;

    LoungeHome(Activity activity, FrameLayout root, View[] controlViews, List<String> dockApps,
               boolean motion, Host host) {
        this.activity = activity;
        this.host = host;
        this.motion = motion;
        density = activity.getResources().getDisplayMetrics().density;
        int width = px(R.dimen.cluster_width);

        cluster = new LinearLayout(activity);
        cluster.setOrientation(LinearLayout.VERTICAL);
        cluster.setClipChildren(false);
        cluster.setClipToPadding(false);

        controlCaption = caption();
        controlCaption.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cluster.addView(controlCaption, new LinearLayout.LayoutParams(width, dp(22)));

        controlRow = row();
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(width, px(R.dimen.control_size));
        controlsLp.topMargin = dp(6);
        cluster.addView(controlRow, controlsLp);

        appRow = row();
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(width, px(R.dimen.app_size));
        appsLp.topMargin = px(R.dimen.cluster_row_gap);
        cluster.addView(appRow, appsLp);

        appCaption = caption();
        appCaption.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(width, dp(22));
        captionLp.topMargin = dp(8);
        cluster.addView(appCaption, captionLp);

        for (View control : controlViews) adoptControl(control);
        for (String pkg : dockApps) addApp(pkg);

        FrameLayout.LayoutParams anchor = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        anchor.rightMargin = px(R.dimen.edge_side);
        // The caption hangs in the bottom margin, so the app row's lower edge sits on the artwork details' baseline.
        anchor.bottomMargin = px(R.dimen.edge_bottom) - dp(30);
        root.addView(cluster, anchor);
        cluster.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeColumns());
    }

    // ---- building ----------------------------------------------------------------------------------

    private FrameLayout row() {
        FrameLayout row = new FrameLayout(activity);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private TextView caption() {
        TextView caption = new TextView(activity);
        caption.setTypeface(UiFont.get(activity, R.string.font_medium));
        caption.setTextSize(11);
        caption.setLetterSpacing(0.18f);
        // setSingleLine replaces the transformation method, so capitals have to come after it.
        caption.setSingleLine(true);
        caption.setAllCaps(true);
        caption.setTextColor(activity.getColor(R.color.ink_primary));
        caption.setShadowLayer(dp(6), 0, 0, 0x99000000);
        caption.setAlpha(0f);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return caption;
    }

    /** Moves one of Home's top-bar buttons into the control row and gives it the quiet round look. */
    private void adoptControl(View control) {
        ((ViewGroup) control.getParent()).removeView(control);
        if (control instanceof TextView) {
            TextView button = (TextView) control;
            if (button.getText().length() > 0) button.setContentDescription(button.getText());
            button.setText(null);
            button.setCompoundDrawablePadding(0);
            int pad = (px(R.dimen.control_size) - dp(22)) / 2;
            button.setPaddingRelative(pad, 0, pad, 0);
            Drawable[] icons = button.getCompoundDrawablesRelative();
            if (icons[0] != null) {
                icons[0].mutate().setBounds(0, 0, dp(22), dp(22));
                button.setCompoundDrawablesRelative(icons[0], null, null, null);
            }
            button.setCompoundDrawableTintList(tint(PILL_INK, Color.WHITE));
        }
        control.setBackground(disc());
        int size = px(R.dimen.control_size);
        controlRow.addView(control, new FrameLayout.LayoutParams(size, size));
        controls.add(control);
        int column = controls.size() - 1;
        chainFocus(control, (v, focused) -> {
            if (focused) {
                show(controlCaption, describe(v), v);
                hide(appCaption);
            } else if (!anyFocused(controls)) {
                hide(controlCaption);
            }
        });
        control.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return isArrow(key);
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focusApp(nearest(apps, v));
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return step(controls, column, -1);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return step(controls, column, 1);
                default:
                    return false;
            }
        });
    }

    private void addApp(String pkg) {
        PackageManager pm = activity.getPackageManager();
        BrandMarks.Mark mark = BrandMarks.of(pkg);
        String label = mark != null ? mark.name : label(pm, pkg);
        if (label == null) return;   // not installed: no empty disc in the row

        ImageView button = new ImageView(activity);
        button.setFocusable(true);
        button.setClickable(true);
        button.setContentDescription(label);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = px(R.dimen.app_size);
        if (mark != null) {
            int pad = (size - dp(22)) / 2;
            button.setPadding(pad, pad, pad, pad);
            button.setImageResource(mark.glyph);
            button.setImageTintList(tint(Color.BLACK, Color.WHITE));
        } else {
            int pad = (size - dp(28)) / 2;
            button.setPadding(pad, pad, pad, pad);
            try {
                button.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException e) {
                return;
            }
        }
        button.setBackground(disc());
        appRow.addView(button, new FrameLayout.LayoutParams(size, size));
        apps.add(button);
        packages.add(pkg);
        int column = apps.size() - 1;

        chainFocus(button, (v, focused) -> {
            if (focused) {
                selected = column;
                show(appCaption, label, v);
                hide(controlCaption);
                host.onAppFocused(pkg);
            } else if (!anyFocused(apps)) {
                hide(appCaption);
            }
        });
        button.setOnClickListener(v -> launch(pkg, label));
        button.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return isArrow(key);
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    View above = nearestView(controls, v);
                    if (above != null) above.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.openLibrary();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return step(apps, column, -1);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return step(apps, column, 1);
                default:
                    return false;
            }
        });
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** A round button: dark glass at rest, the white focus disc when focused. */
    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    /** Both rows share one pill: dark glass with a faint rim at rest, near-white when focused. */
    private StateListDrawable disc() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xF5FFFFFF, 0));
        states.addState(new int[]{}, shape(0x991C1B22, 0x26FFFFFF));
        return states;
    }

    private static ColorStateList tint(int focused, int rest) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, rest});
    }

    /**
     * Spreads each row's buttons over the full width, so both rows share the same column centres. A hidden
     * control keeps its column, so the rest stay over the same apps.
     */
    private void placeColumns() {
        int width = cluster.getWidth();
        if (width == 0) return;
        int columns = Math.max(controls.size(), apps.size());
        float slot = px(R.dimen.app_size);
        place(controls, columns, width, slot);
        place(apps, columns, width, slot);
    }

    private static void place(List<View> row, int columns, int width, float slot) {
        for (int i = 0; i < row.size(); i++) {
            View v = row.get(i);
            float centre = LoungeLayout.centre(i, columns, width, slot);
            v.setTranslationX(centre - v.getLayoutParams().width / 2f);
        }
    }

    // ---- focus --------------------------------------------------------------------------------------

    /** Focus a dock app: index -1 is the one focused last. */
    void focusApp(int index) {
        if (apps.isEmpty()) {
            View first = firstVisible(controls);
            if (first != null) first.requestFocus();
            return;
        }
        selected = index < 0 ? Math.min(selected, apps.size() - 1) : Math.min(index, apps.size() - 1);
        View target = apps.get(selected);
        if (target.isFocused()) host.onAppFocused(packages.get(selected));
        else target.requestFocus();
    }

    /** The dock app focused last, for the backdrop. */
    String selectedPackage() {
        return packages.isEmpty() ? null : packages.get(Math.min(selected, packages.size() - 1));
    }

    boolean hasFocus() {
        return cluster.hasFocus();
    }

    /** A menu is opening over the cluster: its caption would show through under the menu's lower edge. */
    void hideCaptions() {
        hide(controlCaption);
        hide(appCaption);
    }

    private boolean step(List<View> row, int from, int direction) {
        for (int i = from + direction; i >= 0 && i < row.size(); i += direction) {
            if (row.get(i).getVisibility() == View.VISIBLE) {
                row.get(i).requestFocus();
                return true;
            }
        }
        return true;   // the row's end: stay put instead of letting focus jump out of the cluster
    }

    private int nearest(List<View> row, View from) {
        View v = nearestView(row, from);
        return v == null ? -1 : row.indexOf(v);
    }

    private static View nearestView(List<View> row, View from) {
        float x = from.getTranslationX() + from.getWidth() / 2f;
        View best = null;
        float bestDistance = Float.MAX_VALUE;
        for (View v : row) {
            if (v.getVisibility() != View.VISIBLE) continue;
            float d = Math.abs(v.getTranslationX() + v.getWidth() / 2f - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = v;
            }
        }
        return best;
    }

    private static View firstVisible(List<View> row) {
        for (View v : row) if (v.getVisibility() == View.VISIBLE) return v;
        return null;
    }

    private static boolean anyFocused(List<View> row) {
        for (View v : row) if (v.isFocused()) return true;
        return false;
    }

    private static boolean isArrow(int key) {
        return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /** Adds a focus listener after any the view already has (the festive icons animate on focus). */
    private static void chainFocus(View view, View.OnFocusChangeListener next) {
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            next.onFocusChange(v, focused);
        });
    }

    private static CharSequence describe(View control) {
        CharSequence d = control.getContentDescription();
        return d == null ? "" : d;
    }

    // ---- caption ------------------------------------------------------------------------------------

    /** Names the focused button, centred under (or over) it, kept inside the column. */
    private void show(TextView caption, CharSequence text, View under) {
        caption.setText(text);
        caption.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float half = caption.getMeasuredWidth() / 2f;
        float column = cluster.getWidth();
        float centre = under.getTranslationX() + under.getLayoutParams().width / 2f;
        centre = Math.max(half, Math.min(column - half, centre));
        float shift = centre - column / 2f;
        caption.animate().cancel();
        if (caption.getAlpha() == 0f || !motion) {
            caption.setTranslationX(shift);
        } else {
            caption.animate().translationX(shift).setDuration(160);
        }
        caption.animate().alpha(1f).setDuration(motion ? 160 : 0).start();
        wake();
    }

    private void hide(TextView caption) {
        caption.animate().cancel();
        caption.animate().alpha(0f).setDuration(motion ? 120 : 0).start();
    }

    // ---- quiet --------------------------------------------------------------------------------------

    /** Any key: the cluster comes back to full strength and the quiet countdown restarts. */
    void wake() {
        cluster.removeCallbacks(quiet);
        cluster.postDelayed(quiet, QUIET_AFTER_MS);
        if (!quietNow) return;
        quietNow = false;
        cluster.animate().cancel();
        cluster.animate().alpha(1f).setDuration(motion ? 200 : 0).start();
    }

    /** Left alone, the controls step back so the artwork carries the screen. */
    private void stepBack() {
        if (!cluster.isAttachedToWindow()) return;
        quietNow = true;
        cluster.animate().cancel();
        cluster.animate().alpha(QUIET_ALPHA).setDuration(motion ? 900 : 0).start();
    }

    void pause() {
        cluster.removeCallbacks(quiet);
    }

    // ---- launching ----------------------------------------------------------------------------------

    private void launch(String pkg, String label) {
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        try {
            if (launch == null) throw new ActivityNotFoundException();
            LastOpened.record(activity, pkg);
            activity.startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label), Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private int px(int dimen) {
        return activity.getResources().getDimensionPixelSize(dimen);
    }
}
