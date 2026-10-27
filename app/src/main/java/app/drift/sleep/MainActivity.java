package app.drift.sleep;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Single screen, built in code to keep the app tiny. Red on black, amber for highlights,
 * no blue in any color (every color below has a 00 blue channel).
 */
public class MainActivity extends Activity {
    static final int BLACK = 0xFF000000, RED = 0xFFA01800, RED_DIM = 0xFF4A0C00, AMBER = 0xFFD27800;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Settings s;
    private LinearLayout col;
    private TextView playBtn, status, soundDesc, rangeLabel, intervalLabel, modeDesc, timerLabel, volLabel, dimCell;
    private SeekBar loBar, hiBar;
    private final TextView[] soundCells = new TextView[Sounds.COUNT];
    private final TextView[] modeCells = new TextView[3];
    private final TextView[] fadeCells = new TextView[Sounds.FADES.length];

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            h.postDelayed(this, 1000);
        }
    };

    private interface IntCb { void on(int progress, boolean done); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = Settings.load(this);
        setContentView(buildUi());
        applyDim();
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------------ layout

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BLACK);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        final int pad = dp(20);
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            col.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return WindowInsets.CONSUMED;
        });

        TextView title = text("Drift", 28, AMBER);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        col.addView(title);

        playBtn = cell("PLAY");
        playBtn.setTextSize(22);
        playBtn.setPadding(0, dp(18), 0, dp(18));
        playBtn.setOnClickListener(v -> togglePlay());
        addWithMargin(playBtn, dp(16));
        status = text("", 14, RED);
        status.setGravity(Gravity.CENTER);
        addWithMargin(status, dp(8));

        // Sound
        header("Sound");
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            for (int c = 0; c < 3; c++) {
                final int i = r * 3 + c;
                soundCells[i] = cell(Sounds.NAMES[i]);
                soundCells[i].setOnClickListener(v -> {
                    s.sound = i;
                    changed(false);
                    refreshAll();
                });
                row.addView(soundCells[i], weighted());
            }
            col.addView(row);
        }
        soundDesc = text("", 13, RED);
        addWithMargin(soundDesc, dp(6));

        // Range of the varied parameter
        header("Range");
        rangeLabel = text("", 15, AMBER);
        col.addView(rangeLabel);
        col.addView(text("Low", 12, RED));
        loBar = bar(1000, 0);
        col.addView(loBar);
        col.addView(text("High", 12, RED));
        hiBar = bar(1000, 0);
        col.addView(hiBar);
        listen(loBar, (p, done) -> {
            int other = hiBar.getProgress();
            if (p > other - 10) hiBar.setProgress(Math.min(1000, p + 10));
            readRange(done);
        });
        listen(hiBar, (p, done) -> {
            int other = loBar.getProgress();
            if (p < other + 10) loBar.setProgress(Math.max(0, p - 10));
            readRange(done);
        });

        // Change interval
        header("Change every");
        intervalLabel = text("", 15, AMBER);
        col.addView(intervalLabel);
        SeekBar ib = bar(Sounds.INTERVALS.length - 1, s.interval);
        col.addView(ib);
        listen(ib, (p, done) -> {
            s.interval = p;
            intervalLabel.setText(Sounds.fmtInterval(Sounds.INTERVALS[p]));
            if (done) changed(false);
        });

        // Pattern
        header("Pattern");
        LinearLayout mrow = row();
        for (int i = 0; i < 3; i++) {
            final int m = i;
            modeCells[i] = cell(Sounds.MODES[i]);
            modeCells[i].setOnClickListener(v -> {
                s.mode = m;
                changed(false);
                refreshAll();
            });
            mrow.addView(modeCells[i], weighted());
        }
        col.addView(mrow);
        modeDesc = text("", 13, RED);
        addWithMargin(modeDesc, dp(6));

        // Timer
        header("Timer");
        timerLabel = text("", 15, AMBER);
        col.addView(timerLabel);
        SeekBar tb = bar(Sounds.TIMER_STEPS, s.timer);
        col.addView(tb);
        listen(tb, (p, done) -> {
            s.timer = p;
            timerLabel.setText(Sounds.fmtTimer(p));
            if (done) changed(true);
        });
        TextView tnote = text("Changing the timer while playing restarts the countdown.", 12, RED_DIM);
        col.addView(tnote);

        header("Fade out at end");
        LinearLayout frow = row();
        for (int i = 0; i < Sounds.FADES.length; i++) {
            final int f = i;
            fadeCells[i] = cell(Sounds.FADE_LABELS[i]);
            fadeCells[i].setTextSize(13);
            fadeCells[i].setOnClickListener(v -> {
                s.fade = f;
                changed(false);
                refreshAll();
            });
            frow.addView(fadeCells[i], weighted());
        }
        col.addView(frow);

        // Volume
        header("Volume");
        volLabel = text("", 15, AMBER);
        col.addView(volLabel);
        SeekBar vb = bar(100, s.volume);
        col.addView(vb);
        listen(vb, (p, done) -> {
            s.volume = p;
            volLabel.setText(p + "%");
            changed(false); // live, so you can hear the change while dragging
        });

        header("Screen");
        dimCell = cell("");
        dimCell.setOnClickListener(v -> {
            s.dim = !s.dim;
            s.save(this);
            applyDim();
            refreshAll();
        });
        col.addView(dimCell);

        TextView note = text("Pink and brown noise have the most research support for falling asleep. "
                + "Binaural beats and rhythmic sounds have mixed, early evidence. Use what feels calming.", 12, RED_DIM);
        addWithMargin(note, dp(28));

        refreshAll();
        return sv;
    }

    // ------------------------------------------------------------------ behavior

    private void togglePlay() {
        Intent i = new Intent(this, SleepService.class);
        if (SleepService.isPlaying()) {
            i.setAction(SleepService.ACTION_STOP);
            startService(i);
        } else {
            s.save(this);
            i.setAction(SleepService.ACTION_PLAY);
            startForegroundService(i);
        }
        h.postDelayed(this::refreshStatus, 250);
    }

    /** Save and, if playing, push the change to the running sound. */
    private void changed(boolean timerChanged) {
        s.save(this);
        if (SleepService.isPlaying()) {
            startService(new Intent(this, SleepService.class)
                    .setAction(timerChanged ? SleepService.ACTION_RESTART_TIMER : SleepService.ACTION_UPDATE));
        }
        SleepWidget.refresh(this);
    }

    private void readRange(boolean done) {
        int i = s.sound;
        s.lo[i] = Sounds.fromNorm(i, loBar.getProgress() / 1000f);
        s.hi[i] = Sounds.fromNorm(i, hiBar.getProgress() / 1000f);
        updateRangeLabel();
        if (done) changed(false);
    }

    private void updateRangeLabel() {
        int i = s.sound;
        rangeLabel.setText(Sounds.PARAM[i] + ":  " + Sounds.fmt(s.lo[i]) + " – "
                + Sounds.fmt(s.hi[i]) + " " + Sounds.UNIT[i]);
    }

    private void applyDim() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = s.dim ? 0.02f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    private void refreshAll() {
        for (int i = 0; i < Sounds.COUNT; i++) style(soundCells[i], i == s.sound);
        for (int i = 0; i < 3; i++) style(modeCells[i], i == s.mode);
        for (int i = 0; i < fadeCells.length; i++) style(fadeCells[i], i == s.fade);
        soundDesc.setText(Sounds.DESCS[s.sound]);
        modeDesc.setText(Sounds.MODE_DESCS[s.mode]);
        loBar.setProgress(Math.round(Sounds.toNorm(s.sound, s.lo[s.sound]) * 1000));
        hiBar.setProgress(Math.round(Sounds.toNorm(s.sound, s.hi[s.sound]) * 1000));
        updateRangeLabel();
        intervalLabel.setText(Sounds.fmtInterval(Sounds.INTERVALS[s.interval]));
        timerLabel.setText(Sounds.fmtTimer(s.timer));
        volLabel.setText(s.volume + "%");
        dimCell.setText(s.dim ? "Dim screen: ON" : "Dim screen: OFF");
        style(dimCell, s.dim);
        refreshStatus();
    }

    private void refreshStatus() {
        boolean playing = SleepService.isPlaying();
        playBtn.setText(playing ? "STOP" : "PLAY");
        style(playBtn, playing);
        if (!playing) {
            status.setText("Stopped");
            return;
        }
        StringBuilder sb = new StringBuilder("Playing");
        long rem = SleepService.remainingMs();
        if (rem >= 0) sb.append(" · ").append(Sounds.fmtDuration(rem)).append(" left");
        sb.append(" · now ").append(Sounds.fmt(SleepService.currentValue()))
                .append(' ').append(Sounds.UNIT[s.sound]);
        status.setText(sb);
    }

    // ------------------------------------------------------------------ view helpers

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String t, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private void header(String t) {
        TextView v = text(t.toUpperCase(java.util.Locale.US), 12, RED);
        v.setLetterSpacing(0.12f);
        addWithMargin(v, dp(24));
        ((LinearLayout.LayoutParams) v.getLayoutParams()).bottomMargin = dp(6);
    }

    private void addWithMargin(View v, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        col.addView(v, lp);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private TextView cell(String t) {
        TextView v = text(t, 15, RED);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4), dp(12), dp(4), dp(12));
        style(v, false);
        return v;
    }

    private void style(TextView v, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(BLACK);
        g.setStroke(selected ? dp(2) : dp(1), selected ? AMBER : RED_DIM);
        v.setBackground(g);
        v.setTextColor(selected ? AMBER : RED);
    }

    private SeekBar bar(int max, int progress) {
        SeekBar b = new SeekBar(this);
        b.setMax(max);
        b.setProgress(progress);
        b.setThumbTintList(ColorStateList.valueOf(AMBER));
        b.setProgressTintList(ColorStateList.valueOf(AMBER));
        b.setProgressBackgroundTintList(ColorStateList.valueOf(RED_DIM));
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        return b;
    }

    private void listen(SeekBar b, IntCb cb) {
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) cb.on(p, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) { }

            @Override
            public void onStopTrackingTouch(SeekBar sb) { cb.on(sb.getProgress(), true); }
        });
    }
}
