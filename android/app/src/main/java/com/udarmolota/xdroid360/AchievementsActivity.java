package com.udarmolota.xdroid360;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Every achievement the game has, with its points and whether it is unlocked, and the points
 * earned out of the total. Opened from the game's settings (InstanceActivity).
 */
public class AchievementsActivity extends AppCompatActivity {
    private Instance instance;
    private List<XdbfAchievements.Entry> all;
    private Map<Integer, Long> unlocked;
    private String language;
    private TextView summary;
    private LinearProgressIndicator progress;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Appearance.apply(this);
        super.onCreate(savedInstanceState);
        instance = Instance.open(this, getIntent().getStringExtra(InstanceActivity.EXTRA_ID));
        language = Achievements.languageCode(instance);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.achievements_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int pad = dp(16);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(pad, dp(8), pad, dp(12));
        summary = new TextView(this);
        summary.setTextSize(16);
        header.addView(summary);
        progress = new LinearProgressIndicator(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        header.addView(progress, plp);
        root.addView(header);

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        adapter = new Adapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // an unlock while the game ran
    }

    private void reload() {
        try {
            all = Achievements.list(instance);
        } catch (IOException e) {
            summary.setText(getString(R.string.achievements_unreadable, e.getMessage()));
            progress.setVisibility(View.GONE);
            return;
        }
        unlocked = Achievements.unlocked(instance);
        int total = Achievements.totalPoints(all), earned = Achievements.earnedPoints(all, unlocked);
        summary.setText(getString(R.string.achievements_summary, unlocked.size(), all.size(), earned, total));
        progress.setMax(Math.max(1, total));
        progress.setProgress(earned);
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
            XdbfAchievements.Entry e = all.get(position);
            boolean done = unlocked.containsKey(e.id);
            boolean hide = e.secret && !done;

            LinearLayout row = new LinearLayout(AchievementsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(16), dp(8));

            ImageView icon = new ImageView(AchievementsActivity.this);
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
            row.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

            LinearLayout texts = new LinearLayout(AchievementsActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            TextView name = new TextView(AchievementsActivity.this);
            name.setTextSize(16);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setText(hide ? getString(R.string.achievement_secret) : Achievements.text(e.name, language));
            TextView desc = new TextView(AchievementsActivity.this);
            desc.setTextSize(13);
            desc.setText(hide ? getString(R.string.achievement_secret_hint)
                    : Achievements.text(done ? e.unlocked : e.locked, language));
            TextView status = new TextView(AchievementsActivity.this);
            status.setTextSize(12);
            status.setText(done
                    ? getString(R.string.achievement_done, DateFormat.getMediumDateFormat(AchievementsActivity.this)
                            .format(new Date(unlocked.get(e.id))))
                    : getString(R.string.achievement_open));
            status.setTextColor(done ? 0xFF2E7D32 : 0xFF888888);
            texts.addView(name);
            texts.addView(desc);
            texts.addView(status);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView points = new TextView(AchievementsActivity.this);
            points.setTextSize(15);
            points.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            points.setText(String.valueOf(e.points));
            row.addView(points);
            return row;
        }
    }
}
