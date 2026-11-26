package com.udarmolota.xdroid360;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The look of the menus shown over the running game (the achievements list, the cheats menu): a
 * dark panel with a gold edge, a serif title between gold lines, cream text and gold-edged buttons,
 * as the game's own menus. The game is paused behind it; a tap outside the panel, Back or Close
 * closes it.
 */
final class GamePanel {
    private GamePanel() {}

    /** The context for the panel's views: the launcher's theme, as the game's own isn't a Material one. */
    static Context themed(Activity activity) {
        return new ContextThemeWrapper(activity, R.style.LauncherTheme);
    }

    private static float dp(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    /**
     * The panel around content (which takes the room between the title and Close, and scrolls by
     * itself where it has to). Not shown yet. onDismiss runs when it closes, however it did.
     */
    static Dialog create(Activity activity, int titleRes, View content, Runnable onDismiss) {
        final Context ui = themed(activity);
        final float dp = dp(ui);
        final DisplayMetrics screen = ui.getResources().getDisplayMetrics();

        // The panel: dark ground, a gold edge, a serif title between gold lines (as the menus' plates).
        final LinearLayout panel = new LinearLayout(ui);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ground = new GradientDrawable();
        ground.setColor(0xF2160F0A);
        ground.setCornerRadius(6 * dp);
        ground.setStroke(Math.round(2 * dp), AchievementsView.GAME_GOLD);
        panel.setBackground(ground);

        final TextView title = new TextView(ui);
        title.setText(titleRes);
        title.setTextSize(20);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setTextColor(AchievementsView.GAME_GOLD_BRIGHT);
        title.setShadowLayer(4, 1, 2, Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Math.round(8 * dp), 0, Math.round(6 * dp));
        panel.addView(title);
        panel.addView(goldLine(ui, dp));
        panel.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(goldLine(ui, dp));

        final TextView close = new TextView(ui);
        close.setText(R.string.cheats_close);
        close.setTextSize(16);
        close.setTypeface(Typeface.SERIF);
        close.setTextColor(AchievementsView.GAME_TEXT);
        close.setShadowLayer(3, 1, 1, Color.BLACK);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
        panel.addView(close);

        // A dimmed scrim over the game; a tap outside the panel closes it. The panel keeps a margin
        // above and below, so it shrinks with the window (the soft keyboard) instead of running off it.
        final FrameLayout scrim = new FrameLayout(ui);
        scrim.setBackgroundColor(0xB0000000);
        final int width = Math.min(Math.round(620 * dp), Math.round(screen.widthPixels * 0.92f));
        final int margin = Math.round(screen.heightPixels * 0.11f);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        params.topMargin = margin;
        params.bottomMargin = margin;
        scrim.addView(panel, params);
        panel.setClickable(true); // a tap on the panel isn't a tap outside it

        final Dialog dialog = new Dialog(ui, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(scrim);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        scrim.setOnClickListener(v -> dialog.dismiss());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> onDismiss.run());
        return dialog;
    }

    static View goldLine(Context context, float dp) {
        View line = new View(context);
        line.setBackgroundColor(AchievementsView.GAME_GOLD);
        line.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1.5f * dp))));
        return line;
    }

    // ---------------------------------------------------------------- the controls

    /** A section's heading: gold, serif, bold. */
    static TextView heading(Context context, CharSequence text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.setTextColor(AchievementsView.GAME_GOLD_BRIGHT);
        t.setShadowLayer(3, 1, 1, Color.BLACK);
        t.setPadding(0, Math.round(14 * dp(context)), 0, 0);
        return t;
    }

    /** The small dim line under a heading or button that says what it does. */
    static TextView note(Context context, CharSequence text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(Typeface.SERIF);
        t.setTextColor(AchievementsView.GAME_DIM);
        t.setShadowLayer(3, 1, 1, Color.BLACK);
        t.setPadding(0, Math.round(2 * dp(context)), 0, 0);
        return t;
    }

    /** A button: a dark plate with a gold edge and cream serif text. */
    static TextView button(Context context, CharSequence text, boolean small) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setTextSize(small ? 13 : 14);
        b.setTypeface(Typeface.SERIF);
        b.setShadowLayer(3, 1, 1, Color.BLACK);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        int v = Math.round((small ? 8 : 10) * dp(context));
        b.setPadding(v, v, v, v);
        b.setMinHeight(Math.round(40 * dp(context)));
        setActive(b, false);
        return b;
    }

    /** Lights a button up (a cheat that is on) or back to plain; the edge and text turn bright gold. */
    static void setActive(TextView button, boolean active) {
        float dp = dp(button.getContext());
        GradientDrawable plate = new GradientDrawable();
        plate.setColor(active ? 0xFF3A2D18 : 0xFF241B12);
        plate.setCornerRadius(4 * dp);
        plate.setStroke(Math.round(1.5f * dp), active ? AchievementsView.GAME_GOLD_BRIGHT : AchievementsView.GAME_GOLD);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44E8CC8C), plate, null));
        button.setTextColor(active ? AchievementsView.GAME_GOLD_BRIGHT : AchievementsView.GAME_TEXT);
    }

    /** A number field: a dark inset with a thin gold edge. */
    static EditText numberField(Context context, int hintRes) {
        EditText f = new EditText(context);
        f.setHint(hintRes);
        f.setInputType(InputType.TYPE_CLASS_NUMBER);
        f.setTextSize(15);
        f.setTypeface(Typeface.SERIF);
        f.setTextColor(AchievementsView.GAME_TEXT);
        f.setHintTextColor(AchievementsView.GAME_OFF);
        f.setSingleLine(true);
        GradientDrawable inset = new GradientDrawable();
        inset.setColor(0xFF0F0A06);
        inset.setCornerRadius(4 * dp(context));
        inset.setStroke(Math.max(1, Math.round(dp(context))), AchievementsView.GAME_GOLD);
        f.setBackground(inset);
        int v = Math.round(8 * dp(context));
        f.setPadding(v, v, v, v);
        return f;
    }
}
