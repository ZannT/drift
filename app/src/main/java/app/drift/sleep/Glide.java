package app.drift.sleep;

/**
 * Moves a position in [0,1] smoothly from one target to the next.
 * Each move takes one full "change every" interval, eased with a cosine curve,
 * so the sound is always gliding and never jumps.
 */
final class Glide {
    private static final float[] WAVE_STEPS = {0f, 1f / 3, 2f / 3, 1f, 2f / 3, 1f / 3};

    private final Rng rng = new Rng(0x51ED270B);
    private int mode;
    private int waveStep;
    private float from, to, pos, t, dur;

    Glide(int mode) { reset(mode); }

    void reset(int mode) {
        this.mode = mode;
        waveStep = 0;
        pos = mode == Sounds.SETTLE ? 1f : mode == Sounds.WAVE ? 0f : 0.5f;
        from = to = pos;
        t = dur = 0;
    }

    float position() { return pos; }

    /** Advance by dt seconds and return the current position. */
    float step(float dt, float intervalSec, int newMode) {
        if (newMode != mode) {
            mode = newMode;
            from = to = pos;
            t = dur = 0;
            if (mode == Sounds.WAVE) waveStep = Math.round(pos * 3); // join the wave near where we are
        }
        t += dt;
        if (t >= dur) {
            from = to;
            to = next();
            t = 0;
            dur = Math.max(1f, intervalSec);
        } else if (dur > intervalSec * 1.5f && t < intervalSec) {
            dur = intervalSec; // user shortened the interval a lot; don't make them wait
        }
        float k = Math.min(1f, t / dur);
        float e = (float) (0.5 - 0.5 * Math.cos(Math.PI * k));
        pos = from + (to - from) * e;
        return pos;
    }

    private float next() {
        switch (mode) {
            case Sounds.WAVE:
                waveStep = (waveStep + 1) % WAVE_STEPS.length;
                return WAVE_STEPS[waveStep];
            case Sounds.SETTLE: {
                float n = pos * 0.72f + rng.uni() * 0.06f - 0.03f;
                return Math.max(0f, Math.min(1f, n));
            }
            default: {
                float r = rng.uni();
                for (int i = 0; i < 8 && Math.abs(r - pos) < 0.15f; i++) r = rng.uni();
                return r;
            }
        }
    }
}
