package app.drift.sleep;

/** Tiny xorshift random generator: fast, allocation-free, good enough for audio noise. */
final class Rng {
    private int s;

    Rng(int seed) { s = seed == 0 ? 0x9E3779B9 : seed; }

    /** Uniform in [-1, 1). */
    float next() {
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        return s * (1.0f / 2147483648f);
    }

    /** Uniform in [0, 1). */
    float uni() { return next() * 0.5f + 0.5f; }
}
