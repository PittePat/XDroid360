package com.udarmolota.xdroid360;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * "Achievement unlocked" over the running game: a small banner at the top of the screen for a few
 * seconds (touches go through to the game). Several unlocks at once show one after the other.
 * Main thread only.
 */
final class AchievementToast {
    private static final long SHOW_MS = 4500, FADE_MS = 300;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Runnable> QUEUE = new ArrayDeque<>();
    private static boolean showing;

    private AchievementToast() {}

    static void show(Activity activity, XdbfAchievements.Entry entry, String language) {
        QUEUE.add(() -> display(activity, entry, language));
        if (!showing) next();
    }

    private static void next() {
        Runnable r = QUEUE.poll();
        showing = r != null;
        if (r != null) r.run();
    }

    private static void display(Activity activity, XdbfAchievements.Entry entry, String language) {
        if (activity.isFinishing()) {
            QUEUE.clear();
            showing = false;
            return;
        }
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(10 * dp);
        row.setPadding(pad, pad, Math.round(18 * dp), pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202020);
        bg.setCornerRadius(28 * dp);
        bg.setStroke(Math.round(1.5f * dp), 0xFFB8A15C);
        row.setBackground(bg);
        row.setElevation(8 * dp);

        ImageView icon = new ImageView(activity);
        if (entry.icon != null) icon.setImageBitmap(BitmapFactory.decodeByteArray(entry.icon, 0, entry.icon.length));
        int size = Math.round(48 * dp);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Math.round(12 * dp), 0, 0, 0);
        texts.addView(label(activity, activity.getString(R.string.achievement_unlocked), 11, 0xFFB8A15C, false));
        texts.addView(label(activity, Achievements.text(entry.name, language), 16, Color.WHITE, true));
        texts.addView(label(activity, activity.getString(R.string.achievement_points, entry.points), 12, 0xFFCCCCCC, false));
        row.addView(texts);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(16 * dp);
        row.setAlpha(0f);
        activity.addContentView(row, lp);
        row.animate().alpha(1f).setDuration(FADE_MS).start();
        HANDLER.postDelayed(() -> row.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (row.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) row.getParent()).removeView(row);
            next();
        }).start(), SHOW_MS);
    }

    private static TextView label(Activity activity, String text, int sp, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxWidth(Math.round(240 * activity.getResources().getDisplayMetrics().density));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setVisibility(View.VISIBLE);
        return t;
    }
}
