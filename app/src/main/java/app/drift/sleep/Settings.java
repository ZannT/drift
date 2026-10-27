package app.drift.sleep;

import android.content.Context;
import android.content.SharedPreferences;

/** All user settings. The widget always plays whatever was saved here most recently. */
final class Settings {
    int sound = Sounds.BROWN;
    final float[] lo = Sounds.DEF_LO.clone();
    final float[] hi = Sounds.DEF_HI.clone();
    int interval = 5;   // index into Sounds.INTERVALS (30 sec)
    int mode = Sounds.RANDOM;
    int timer = 32;     // x 15 min = 8 hours; 0 = no timer
    int fade = 3;       // index into Sounds.FADES (15 min)
    int volume = 45;    // 0..100
    boolean dim = false; // off by default so the phone's own brightness control works

    static Settings load(Context c) {
        SharedPreferences p = prefs(c);
        Settings s = new Settings();
        s.sound = clamp(p.getInt("sound", s.sound), 0, Sounds.COUNT - 1);
        for (int i = 0; i < Sounds.COUNT; i++) {
            s.lo[i] = p.getFloat("lo" + i, s.lo[i]);
            s.hi[i] = p.getFloat("hi" + i, s.hi[i]);
        }
        s.interval = clamp(p.getInt("interval", s.interval), 0, Sounds.INTERVALS.length - 1);
        s.mode = clamp(p.getInt("mode", s.mode), 0, 2);
        s.timer = clamp(p.getInt("timer", s.timer), 0, Sounds.TIMER_STEPS);
        s.fade = clamp(p.getInt("fade", s.fade), 0, Sounds.FADES.length - 1);
        s.volume = clamp(p.getInt("volume", s.volume), 0, 100);
        s.dim = p.getBoolean("dim_lock", s.dim);
        return s;
    }

    void save(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.putInt("sound", sound);
        for (int i = 0; i < Sounds.COUNT; i++) {
            e.putFloat("lo" + i, lo[i]);
            e.putFloat("hi" + i, hi[i]);
        }
        e.putInt("interval", interval).putInt("mode", mode).putInt("timer", timer)
                .putInt("fade", fade).putInt("volume", volume).putBoolean("dim_lock", dim).apply();
    }

    float intervalSec() { return Sounds.INTERVALS[interval]; }

    long timerMs() { return timer * 15L * 60_000L; }

    long fadeMs() { return Sounds.FADES[fade] * 1000L; }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("drift", Context.MODE_PRIVATE);
    }

    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
}
