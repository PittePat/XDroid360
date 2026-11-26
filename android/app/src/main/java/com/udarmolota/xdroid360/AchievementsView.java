package com.udarmolota.xdroid360;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A game's achievements: the points earned of the total, a bar for them, and every achievement with
 * its icon, name, description, points and whether it is unlocked. In the launcher
 * (AchievementsActivity) it takes the launcher's Material look; over the running game
 * (GameActivity.showAchievements) the game's menus' look: cream serif text, gold edges, dark ground.
 */
final class AchievementsView extends LinearLayout {
    // The game's menu colours (ui/palette.h of the runtime).
    static final int GAME_TEXT = 0xFFECE2CC, GAME_DIM = 0xFFBEB29A, GAME_OFF = 0xFF786E60;
    static final int GAME_GOLD = 0xFFB0925C, GAME_GOLD_BRIGHT = 0xFFE8CC8C;

    private final Instance instance;
    private final boolean game;
    private final String language;
    private final TextView summary;
    private final ProgressBar bar;
    private final Adapter adapter = new Adapter();
    private List<XdbfAchievements.Entry> all;
    private Map<Integer, Long> unlocked;

    AchievementsView(Context context, Instance instance, boolean gameStyle) {
        super(context);
        this.instance = instance;
        this.game = gameStyle;
        this.language = Achievements.languageCode(instance);
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(VERTICAL);
        header.setPadding(dp(16), dp(gameStyle ? 4 : 8), dp(16), dp(gameStyle ? 6 : 12));
        summary = new TextView(context);
        summary.setTextSize(gameStyle ? 14 : 16);
        if (gameStyle) {
            summary.setTypeface(Typeface.SERIF);
            summary.setTextColor(GAME_TEXT);
            summary.setShadowLayer(3, 1, 1, Color.BLACK);
        }
        header.addView(summary);
        bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(ColorStateList.valueOf(gameStyle ? GAME_GOLD_BRIGHT
                : MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, GAME_GOLD)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(gameStyle ? 0xFF3A3024 : 0x33888888));
        LayoutParams barParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(gameStyle ? 6 : 8));
        barParams.topMargin = dp(gameStyle ? 4 : 8);
        header.addView(bar, barParams);
        addView(header);

        ListView list = new ListView(context);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setAdapter(adapter);
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Reads the list and what is unlocked again (an unlock while this was shown or the game ran). */
    void reload() {
        try {
            all = Achievements.list(instance);
        } catch (IOException e) {
            all = null;
            summary.setText(getContext().getString(R.string.achievements_unreadable, e.getMessage()));
            bar.setVisibility(GONE);
            adapter.notifyDataSetChanged();
            return;
        }
        unlocked = Achievements.unlocked(instance);
        int total = Achievements.totalPoints(all), earned = Achievements.earnedPoints(all, unlocked);
        summary.setText(getContext().getString(R.string.achievements_summary, unlocked.size(), all.size(), earned, total));
        bar.setVisibility(VISIBLE);
        bar.setMax(Math.max(1, total));
        bar.setProgress(earned);
        adapter.notifyDataSetChanged();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return all == null ? 0 : all.size(); }
        @Override public Object getItem(int i) { return all.get(i); }
        @Override public long getItemId(int i) { return all.get(i).id; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Context context = parent.getContext();
            XdbfAchievements.Entry e = all.get(position);
            boolean done = unlocked.containsKey(e.id);
            boolean hide = e.secret && !done;

            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(VERTICAL);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(game ? 4 : 10), dp(16), dp(game ? 4 : 10));

            ImageView icon = new ImageView(context);
            if (e.icon != null) {
                Bitmap b = BitmapFactory.decodeByteArray(e.icon, 0, e.icon.length);
                icon.setImageBitmap(b);
            }
            if (!done) { // grey and dim until it is earned
                ColorMatrix gray = new ColorMatrix();
                gray.setSaturation(0f);
                icon.setColorFilter(new ColorMatrixColorFilter(gray));
                icon.setAlpha(0.45f);
            }
            int iconSize = dp(game ? 34 : 56);
            row.addView(icon, new LayoutParams(iconSize, iconSize));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            texts.setPadding(dp(game ? 10 : 12), 0, dp(8), 0);
            TextView name = new TextView(context);
            name.setTextSize(game ? 13.5f : 16);
            name.setTypeface(game ? Typeface.create(Typeface.SERIF, Typeface.BOLD) : Typeface.DEFAULT_BOLD);
            name.setText(hide ? context.getString(R.string.achievement_secret) : Achievements.text(e.name, language));
            TextView desc = new TextView(context);
            desc.setTextSize(game ? 11 : 13);
            desc.setText(hide ? context.getString(R.string.achievement_secret_hint)
                    : Achievements.text(done ? e.unlocked : e.locked, language));
            TextView status = new TextView(context);
            status.setTextSize(game ? 10 : 12);
            if (game) { // short, beside the points: the date it was earned, or Locked
                status.setText(done ? DateFormat.getMediumDateFormat(context).format(new Date(unlocked.get(e.id)))
                        : context.getString(R.string.achievement_state_locked));
                status.setGravity(Gravity.END);
            } else {
                status.setText(done
                        ? context.getString(R.string.achievement_done, DateFormat.getMediumDateFormat(context)
                                .format(new Date(unlocked.get(e.id))))
                        : context.getString(R.string.achievement_open));
            }
            TextView points = new TextView(context);
            points.setTextSize(15);
            points.setTypeface(game ? Typeface.create(Typeface.SERIF, Typeface.BOLD) : Typeface.DEFAULT_BOLD);
            points.setText(String.valueOf(e.points));
            if (game) {
                for (TextView t : new TextView[] { name, desc, status, points }) {
                    if (t != name && t != points) t.setTypeface(Typeface.SERIF);
                    t.setShadowLayer(3, 1, 1, Color.BLACK);
                }
                name.setTextColor(done ? GAME_GOLD_BRIGHT : GAME_TEXT);
                desc.setTextColor(done ? GAME_TEXT : GAME_DIM);
                status.setTextColor(done ? GAME_GOLD : GAME_OFF);
                points.setTextColor(done ? GAME_GOLD_BRIGHT : GAME_OFF);
            } else {
                status.setTextColor(done ? 0xFF2E7D32 : 0xFF888888);
            }
            texts.addView(name);
            texts.addView(desc);
            if (!game) texts.addView(status);
            row.addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (game) { // the points with the state under them: two lines of text a row, not three
                LinearLayout right = new LinearLayout(context);
                right.setOrientation(VERTICAL);
                right.setGravity(Gravity.END);
                right.addView(points);
                right.addView(status);
                row.addView(right);
            } else {
                row.addView(points);
            }
            cell.addView(row);

            if (game) { // a thin gold line between the rows, as the menus' dividers
                View line = new View(context);
                line.setBackgroundColor(0x55B0925C);
                LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
                lp.leftMargin = dp(16);
                lp.rightMargin = dp(16);
                cell.addView(line, lp);
            }
            return cell;
        }
    }
}
