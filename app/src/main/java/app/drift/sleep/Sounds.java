package app.drift.sleep;

/** Static descriptions of every sound and the parameter each one varies. No Android imports. */
final class Sounds {
    static final int WHITE = 0, PINK = 1, BROWN = 2, BREATH = 3, BINAURAL = 4,
            DRONE = 5, RHYTHM = 6, RAIN = 7, BASS = 8, COUNT = 9;

    static final String[] NAMES = {
            "White", "Pink", "Brown", "Breathing", "Binaural",
            "Soft drone", "Soft rhythm", "Gentle rain", "Deep bass"};

    static final String[] DESCS = {
            "Even hiss across all frequencies. Masks sudden noises well.",
            "Softer than white; energy falls with pitch. Best-studied for sleep.",
            "Deep rumble, like a distant waterfall or wind.",
            "Noise that swells and settles like slow breathing. Breathe along if you like.",
            "A slightly different tone in each ear; your brain hears a slow beat. Earbuds required.",
            "Warm, slowly shifting chord of pure tones.",
            "Slow, soft hand-drum pulse over a quiet bed.",
            "Synthesized rain: a soft wash with scattered drops.",
            "Very low tones that swell and fade slowly."};

    static final String[] PARAM = {
            "Tone", "Tone", "Tone", "Breaths per minute", "Beat frequency",
            "Drone pitch", "Tempo", "Rain intensity", "Bass pitch"};

    static final String[] UNIT = {"Hz", "Hz", "Hz", "/min", "Hz", "Hz", "BPM", "drops/s", "Hz"};

    static final float[] MIN = {800, 300, 80, 3, 0.5f, 55, 36, 2, 28};
    static final float[] MAX = {16000, 10000, 1500, 12, 14, 220, 96, 60, 80};
    static final float[] DEF_LO = {3000, 1200, 250, 5, 2, 82, 48, 8, 36};
    static final float[] DEF_HI = {9000, 4000, 600, 7, 6, 131, 64, 25, 52};
    static final boolean[] LOG = {true, true, true, false, true, true, false, true, true};

    /** How often the sound moves to a new value, in seconds. */
    static final int[] INTERVALS = {5, 7, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300,
            420, 600, 900, 1200, 1500, 1800};
    static final int[] FADES = {0, 60, 300, 900, 1800};
    static final String[] FADE_LABELS = {"Off", "1 min", "5 min", "15 min", "30 min"};
    static final int TIMER_STEPS = 40; // x 15 minutes = 10 hours

    static final int RANDOM = 0, WAVE = 1, SETTLE = 2;
    static final String[] MODES = {"Random", "Wave", "Settle"};
    static final String[] MODE_DESCS = {
            "Drifts to a random point in your range each time.",
            "Sweeps smoothly up and down through your range.",
            "Starts at the top of your range and eases toward the bottom."};

    static float toNorm(int s, float v) {
        if (LOG[s]) return (float) (Math.log(v / MIN[s]) / Math.log(MAX[s] / MIN[s]));
        return (v - MIN[s]) / (MAX[s] - MIN[s]);
    }

    static float fromNorm(int s, float n) {
        n = Math.max(0f, Math.min(1f, n));
        if (LOG[s]) return (float) (MIN[s] * Math.pow(MAX[s] / MIN[s], n));
        return MIN[s] + (MAX[s] - MIN[s]) * n;
    }

    /** Position u in [0,1] between lo and hi, measured in the parameter's natural scale. */
    static float valueAt(int s, float lo, float hi, float u) {
        float a = toNorm(s, lo), b = toNorm(s, hi);
        return fromNorm(s, a + (b - a) * u);
    }

    static String fmt(float v) {
        if (v < 10) return String.format(java.util.Locale.US, "%.1f", v);
        return String.valueOf(Math.round(v));
    }

    static String fmtInterval(int sec) {
        if (sec < 60) return sec + " sec";
        if (sec % 60 == 0) return (sec / 60) + " min";
        return String.format(java.util.Locale.US, "%.1f min", sec / 60f);
    }

    static String fmtDuration(long ms) {
        long m = (ms + 59_999) / 60_000;
        if (m < 60) return m + "m";
        return (m / 60) + "h " + (m % 60) + "m";
    }

    static String fmtTimer(int steps) {
        if (steps == 0) return "No timer";
        int m = steps * 15;
        if (m < 60) return m + " min";
        return (m / 60) + "h" + (m % 60 == 0 ? "" : " " + (m % 60) + "m");
    }

    private Sounds() {}
}
