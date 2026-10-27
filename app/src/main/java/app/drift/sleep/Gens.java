package app.drift.sleep;

/**
 * All sound generators. Everything is synthesized here in real time; there are no recordings.
 * No Android imports, so these can be tested on a desktop JVM.
 */
final class Gens {
    private static final double TAU = 2 * Math.PI;

    abstract static class Gen {
        final int sr;
        final float inv;

        Gen(int sr) {
            this.sr = sr;
            inv = 1f / sr;
        }

        /** Overwrite b with n interleaved stereo frames. p is the current value of the varied parameter. */
        abstract void render(float[] b, int n, float p);
    }

    static Gen create(int sound, int sr) {
        switch (sound) {
            case Sounds.WHITE: return new Noise(sr, Sounds.WHITE);
            case Sounds.PINK: return new Noise(sr, Sounds.PINK);
            case Sounds.BREATH: return new Breath(sr);
            case Sounds.BINAURAL: return new Binaural(sr);
            case Sounds.DRONE: return new Drone(sr);
            case Sounds.RHYTHM: return new Rhythm(sr);
            case Sounds.RAIN: return new Rain(sr);
            case Sounds.BASS: return new Bass(sr);
            default: return new Noise(sr, Sounds.BROWN);
        }
    }

    /** One-pole low-pass coefficient for cutoff fc. */
    static float lp(float fc, int sr) {
        return (float) (1 - Math.exp(-TAU * fc / sr));
    }

    /** Slow loudness normaliser so every setting plays at a similar level. */
    static final class Agc {
        final int sr;
        final float target, tau, maxGain;
        float ms = -1, g = 1;

        Agc(int sr, float target, float tauSec, float maxGain) {
            this.sr = sr;
            this.target = target;
            this.tau = tauSec;
            this.maxGain = maxGain;
        }

        void apply(float[] b, int n) {
            double sum = 0;
            for (int i = 0; i < 2 * n; i++) sum += b[i] * b[i];
            float m = (float) (sum / (2 * n));
            boolean first = ms < 0;
            if (first) ms = m;
            else ms += (float) (1 - Math.exp(-n / (sr * tau))) * (m - ms);
            float tg = Math.min(maxGain, target / (float) Math.sqrt(ms + 1e-12));
            float g0 = first ? tg : g;
            float step = (tg - g0) / n;
            for (int i = 0; i < n; i++) {
                float gg = g0 + step * (i + 1);
                b[2 * i] *= gg;
                b[2 * i + 1] *= gg;
            }
            g = tg;
        }
    }

    /** Pink (Paul Kellet) and brown noise sources, one state per channel. */
    static final class ColorSrc {
        final float[][] pk = new float[2][3];
        final float[] br = new float[2];

        float pink(int ch, float w) {
            float[] k = pk[ch];
            k[0] = 0.99765f * k[0] + w * 0.0990460f;
            k[1] = 0.96300f * k[1] + w * 0.2965164f;
            k[2] = 0.57000f * k[2] + w * 1.0526913f;
            return (k[0] + k[1] + k[2] + w * 0.1848f) * 0.2f;
        }

        float brown(int ch, float w) {
            float s = br[ch] * 0.9985f + w * 0.05f;
            br[ch] = s;
            return s;
        }
    }

    // ---------------------------------------------------------------- noise colors

    static final class Noise extends Gen {
        final int color;
        final Rng rl = new Rng(0x1234567), rr = new Rng(0x7654321);
        final ColorSrc c = new ColorSrc();
        final Agc agc;
        float l1, l2, r1, r2;

        Noise(int sr, int color) {
            super(sr);
            this.color = color;
            agc = new Agc(sr, 0.17f, 3f, 40f);
        }

        private float src(int ch, float w) {
            if (color == Sounds.WHITE) return w;
            if (color == Sounds.PINK) return c.pink(ch, w);
            return c.brown(ch, w);
        }

        @Override
        void render(float[] b, int n, float p) {
            float a = lp(p, sr);
            for (int i = 0; i < n; i++) {
                float l = src(0, rl.next()), r = src(1, rr.next());
                l1 += a * (l - l1);
                l2 += a * (l1 - l2);
                r1 += a * (r - r1);
                r2 += a * (r1 - r2);
                b[2 * i] = l2;
                b[2 * i + 1] = r2;
            }
            agc.apply(b, n);
        }
    }

    // ---------------------------------------------------------------- breathing

    static final class Breath extends Gen {
        final Rng rl = new Rng(0x2468ACE), rr = new Rng(0x13579BD);
        final ColorSrc c = new ColorSrc();
        final Agc agc;
        float l1, l2, r1, r2;
        double ph;
        float[] env = new float[0];

        Breath(int sr) {
            super(sr);
            agc = new Agc(sr, 0.17f, 12f, 40f);
        }

        @Override
        void render(float[] b, int n, float p) {
            if (env.length < n) env = new float[n];
            double inc = p / 60.0 / sr;
            float k = (float) (TAU / sr);
            for (int i = 0; i < n; i++) {
                ph += inc;
                if (ph >= 1) ph -= 1;
                float e;
                if (ph < 0.4) e = (float) (0.5 - 0.5 * Math.cos(Math.PI * ph / 0.4));   // in-breath
                else e = (float) (0.5 + 0.5 * Math.cos(Math.PI * (ph - 0.4) / 0.6)); // longer out-breath
                env[i] = e;
                float x = k * (300 + 2500 * e);
                float a = x * (1 - 0.5f * x);
                float l = c.pink(0, rl.next()), r = c.pink(1, rr.next());
                l1 += a * (l - l1);
                l2 += a * (l1 - l2);
                r1 += a * (r - r1);
                r2 += a * (r1 - r2);
                b[2 * i] = l2;
                b[2 * i + 1] = r2;
            }
            agc.apply(b, n);
            for (int i = 0; i < n; i++) {
                float g = 0.15f + 0.85f * env[i];
                b[2 * i] *= g;
                b[2 * i + 1] *= g;
            }
        }
    }

    // ---------------------------------------------------------------- binaural beats

    static final class Binaural extends Gen {
        static final float CARRIER = 200f;
        final Rng rl = new Rng(0x0BADF00D), rr = new Rng(0x0DEFACED);
        final ColorSrc c = new ColorSrc();
        double pl, pr;

        Binaural(int sr) { super(sr); }

        @Override
        void render(float[] b, int n, float p) {
            double il = CARRIER / (double) sr, ir = (CARRIER + p) / (double) sr;
            for (int i = 0; i < n; i++) {
                pl += il;
                if (pl >= 1) pl -= 1;
                pr += ir;
                if (pr >= 1) pr -= 1;
                b[2 * i] = (float) Math.sin(TAU * pl) * 0.25f + c.brown(0, rl.next()) * 0.06f;
                b[2 * i + 1] = (float) Math.sin(TAU * pr) * 0.25f + c.brown(1, rr.next()) * 0.06f;
            }
        }
    }

    // ---------------------------------------------------------------- tonal drones

    /** A stack of slowly breathing sine partials, slightly detuned between ears. */
    static class Partials extends Gen {
        final float[] ratio, amp, lfoRate;
        final double[] phL, phR, lfo;
        final float detune, depth, harm, out;

        Partials(int sr, float[] ratio, float[] amp, float[] lfoRate,
                 float detune, float depth, float harm, float out) {
            super(sr);
            this.ratio = ratio;
            this.amp = amp;
            this.lfoRate = lfoRate;
            this.detune = detune;
            this.depth = depth;
            this.harm = harm;
            this.out = out;
            phL = new double[ratio.length];
            phR = new double[ratio.length];
            lfo = new double[ratio.length];
            for (int v = 0; v < ratio.length; v++) lfo[v] = v * 0.37; // stagger the swells
        }

        @Override
        void render(float[] b, int n, float p) {
            for (int i = 0; i < 2 * n; i++) b[i] = 0;
            for (int v = 0; v < ratio.length; v++) {
                double f = p * ratio[v];
                double il = f * (1 - detune) / sr, ir = f * (1 + detune) / sr, ilfo = lfoRate[v] / (double) sr;
                double a = phL[v], c = phR[v], m = lfo[v];
                float av = amp[v] * out;
                for (int i = 0; i < n; i++) {
                    a += il;
                    if (a >= 1) a -= 1;
                    c += ir;
                    if (c >= 1) c -= 1;
                    m += ilfo;
                    if (m >= 1) m -= 1;
                    float g = av * (float) (1 - depth * (0.5 + 0.5 * Math.cos(TAU * m)));
                    double sa = Math.sin(TAU * a), sc = Math.sin(TAU * c);
                    b[2 * i] += g * (float) (sa + harm * Math.sin(2 * TAU * a));
                    b[2 * i + 1] += g * (float) (sc + harm * Math.sin(2 * TAU * c));
                }
                phL[v] = a;
                phR[v] = c;
                lfo[v] = m;
            }
        }
    }

    static final class Drone extends Partials {
        Drone(int sr) {
            super(sr,
                    new float[]{1f, 1.5f, 2f, 3f, 4f},
                    new float[]{0.30f, 0.22f, 0.18f, 0.10f, 0.06f},
                    new float[]{0.031f, 0.047f, 0.059f, 0.073f, 0.089f},
                    0.0012f, 0.5f, 0.12f, 0.62f);
        }
    }

    static final class Bass extends Gen {
        final Partials tones;
        final Rng rl = new Rng(0x00C0FFEE), rr = new Rng(0x00FACADE);
        final ColorSrc c = new ColorSrc();
        float l1, r1;

        Bass(int sr) {
            super(sr);
            tones = new Partials(sr,
                    new float[]{1f, 2f, 3f},
                    new float[]{0.55f, 0.25f, 0.08f},
                    new float[]{0.05f, 0.037f, 0.061f},
                    0.003f, 0.35f, 0.0f, 0.5f);
        }

        @Override
        void render(float[] b, int n, float p) {
            tones.render(b, n, p);
            float a = lp(120, sr);
            for (int i = 0; i < n; i++) {
                l1 += a * (c.brown(0, rl.next()) - l1);
                r1 += a * (c.brown(1, rr.next()) - r1);
                b[2 * i] += l1 * 0.12f;
                b[2 * i + 1] += r1 * 0.12f;
            }
        }
    }

    // ---------------------------------------------------------------- soft rhythm

    /** A slow, soft hand-drum pulse: low "doum" tones and very quiet high "tek" taps. */
    static final class Rhythm extends Gen {
        // 8 steps per bar (eighth notes). 2 = doum, 3 = soft doum, 1 = tek, 0 = rest.
        static final int[] PATTERN = {2, 0, 0, 1, 3, 0, 1, 0};
        static final int VOICES = 4;
        final Rng rng = new Rng(0x7EC7EC7);
        final Rng nl = new Rng(0x1A2B3C4), nr = new Rng(0x4C3B2A1);
        final ColorSrc c = new ColorSrc();
        final Agc bedAgc;
        float bl1, br1;
        double stepPh = 1; // trigger the first step immediately
        int step = -1;
        // doum voices
        final float[] dT = new float[VOICES], dAmp = new float[VOICES], dF = new float[VOICES], dPan = new float[VOICES];
        final double[] dPh = new double[VOICES], dPh2 = new double[VOICES];
        final boolean[] dOn = new boolean[VOICES];
        // tek voices
        final float[] tT = new float[VOICES], tAmp = new float[VOICES], tPan = new float[VOICES], tH = new float[VOICES], tL = new float[VOICES];
        final boolean[] tOn = new boolean[VOICES];
        float[] bed = new float[0];

        Rhythm(int sr) {
            super(sr);
            bedAgc = new Agc(sr, 0.02f, 3f, 40f);
        }

        private void trigger(int kind) {
            float vel = 0.85f + 0.3f * rng.uni();
            if (kind == 1) {
                if (rng.uni() < 0.2f) return; // occasionally skip a tek so it never feels mechanical
                for (int v = 0; v < VOICES; v++) if (!tOn[v]) {
                    tOn[v] = true; tT[v] = 0; tAmp[v] = 0.3f * vel; tPan[v] = 0.35f + 0.3f * rng.uni();
                    tH[v] = 0; tL[v] = 0;
                    return;
                }
            } else {
                for (int v = 0; v < VOICES; v++) if (!dOn[v]) {
                    dOn[v] = true; dT[v] = 0; dPh[v] = 0; dPh2[v] = 0;
                    dAmp[v] = (kind == 2 ? 0.85f : 0.5f) * vel;
                    dF[v] = kind == 2 ? 62f : 70f;
                    dPan[v] = kind == 2 ? 0.5f : 0.42f;
                    return;
                }
            }
        }

        @Override
        void render(float[] b, int n, float p) {
            // quiet pink bed
            if (bed.length < 2 * n) bed = new float[2 * n];
            float ab = lp(600, sr);
            for (int i = 0; i < n; i++) {
                bl1 += ab * (c.pink(0, nl.next()) - bl1);
                br1 += ab * (c.pink(1, nr.next()) - br1);
                bed[2 * i] = bl1;
                bed[2 * i + 1] = br1;
            }
            bedAgc.apply(bed, n);

            double stepInc = (p / 60.0 * 2.0) / sr; // eighth notes
            float hpA = lp(1500, sr), lpA = lp(4000, sr);
            for (int i = 0; i < n; i++) {
                stepPh += stepInc;
                if (stepPh >= 1) {
                    stepPh -= Math.floor(stepPh);
                    step = (step + 1) % PATTERN.length;
                    if (PATTERN[step] != 0) trigger(PATTERN[step]);
                }
                float L = bed[2 * i], R = bed[2 * i + 1];
                for (int v = 0; v < VOICES; v++) {
                    if (dOn[v]) {
                        float t = dT[v];
                        float f = dF[v] * (1 + 0.5f * (float) Math.exp(-t / 0.03f));
                        dPh[v] += f * inv;
                        dPh2[v] += f * 1.6f * inv;
                        float env = (1 - (float) Math.exp(-t / 0.003f)) * (float) Math.exp(-t / 0.38f);
                        float y = env * dAmp[v] * (float) (Math.sin(TAU * dPh[v])
                                + 0.15 * Math.exp(-t / 0.12f) * Math.sin(TAU * dPh2[v]));
                        L += y * (1 - dPan[v]) * 2 * 0.5f;
                        R += y * dPan[v] * 2 * 0.5f;
                        dT[v] = t + inv;
                        if (t > 2.5f) dOn[v] = false;
                    }
                    if (tOn[v]) {
                        float t = tT[v];
                        float w = nl.next();
                        tH[v] += hpA * (w - tH[v]);        // tH tracks lows; w - tH is high-passed
                        tL[v] += lpA * ((w - tH[v]) - tL[v]);
                        float env = (1 - (float) Math.exp(-t / 0.001f)) * (float) Math.exp(-t / 0.02f);
                        float y = tL[v] * env * tAmp[v];
                        L += y * (1 - tPan[v]);
                        R += y * tPan[v];
                        tT[v] = t + inv;
                        if (t > 0.2f) tOn[v] = false;
                    }
                }
                b[2 * i] = L;
                b[2 * i + 1] = R;
            }
        }
    }

    // ---------------------------------------------------------------- rain

    static final class Rain extends Gen {
        static final int DROPS = 32;
        final Rng rl = new Rng(0x5EED5EED), rr = new Rng(0x0DDBA11), rng = new Rng(0x00D20B5);
        final ColorSrc c = new ColorSrc();
        final Agc agc;
        float l1, l2, r1, r2, dl, dr;
        final float[] t = new float[DROPS], f = new float[DROPS], tau = new float[DROPS],
                amp = new float[DROPS], pan = new float[DROPS];
        final double[] ph = new double[DROPS];
        final boolean[] on = new boolean[DROPS];

        Rain(int sr) {
            super(sr);
            agc = new Agc(sr, 0.15f, 3f, 40f);
        }

        @Override
        void render(float[] b, int n, float p) {
            float a = lp(2500, sr);
            for (int i = 0; i < n; i++) {
                float l = c.pink(0, rl.next()), r = c.pink(1, rr.next());
                l1 += a * (l - l1);
                l2 += a * (l1 - l2);
                r1 += a * (r - r1);
                r2 += a * (r1 - r2);
                b[2 * i] = l2;
                b[2 * i + 1] = r2;
            }
            agc.apply(b, n);

            float k = Math.max(0f, Math.min(1f, (p - Sounds.MIN[Sounds.RAIN]) / (Sounds.MAX[Sounds.RAIN] - Sounds.MIN[Sounds.RAIN])));
            float bedGain = 0.75f + 0.35f * k;
            float prob = p * inv;
            float da = lp(5000, sr);
            for (int i = 0; i < n; i++) {
                if (rng.uni() < prob) {
                    for (int d = 0; d < DROPS; d++) if (!on[d]) {
                        on[d] = true; t[d] = 0; ph[d] = 0;
                        f[d] = 1500f * (float) Math.pow(3.0, rng.uni());
                        tau[d] = 0.012f + 0.035f * rng.uni();
                        float q = rng.uni();
                        amp[d] = 0.02f + 0.10f * q * q;
                        pan[d] = rng.uni();
                        break;
                    }
                }
                float sl = 0, sr2 = 0;
                for (int d = 0; d < DROPS; d++) {
                    if (!on[d]) continue;
                    float tt = t[d];
                    float freq = f[d] * (1 + tt / tau[d] * 0.4f); // small upward chirp, like a bubble
                    ph[d] += freq * inv;
                    float env = (1 - (float) Math.exp(-tt / 0.0008f)) * (float) Math.exp(-tt / tau[d]);
                    float y = (float) Math.sin(TAU * ph[d]) * env * amp[d];
                    sl += y * (1 - pan[d]);
                    sr2 += y * pan[d];
                    t[d] = tt + inv;
                    if (tt > tau[d] * 7) on[d] = false;
                }
                dl += da * (sl - dl);
                dr += da * (sr2 - dr);
                b[2 * i] = b[2 * i] * bedGain + dl;
                b[2 * i + 1] = b[2 * i + 1] * bedGain + dr;
            }
        }
    }

    private Gens() {}
}
