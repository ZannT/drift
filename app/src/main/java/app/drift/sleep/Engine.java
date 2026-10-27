package app.drift.sleep;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.os.SystemClock;

/**
 * Audio thread: renders the current sound block by block into a large, power-saving AudioTrack buffer.
 * Handles glides, volume, sound switching (crossfade), start fade-in, and the end-of-timer fade-out.
 */
final class Engine implements Runnable {
    interface Listener { void onFinished(); }

    private static final int BLOCK = 1024;

    private final Listener listener;
    private final Object lock = new Object();
    private volatile Settings cfg;
    private volatile long deadline;   // elapsedRealtime ms; 0 = no timer
    private volatile boolean stopRequested, paused;
    volatile float currentValue;
    private Thread thread;

    Engine(Settings s, long deadline, Listener listener) {
        this.cfg = s;
        this.deadline = deadline;
        this.listener = listener;
    }

    void start() {
        thread = new Thread(this, "drift-audio");
        thread.start();
    }

    void update(Settings s) { cfg = s; }

    void setDeadline(long d) { deadline = d; }

    long deadline() { return deadline; }

    void stop() {
        stopRequested = true;
        setPaused(false);
    }

    void setPaused(boolean p) {
        synchronized (lock) {
            paused = p;
            lock.notifyAll();
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        int sr = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        if (sr < 8000 || sr > 192000) sr = 48000;
        int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        int bufBytes = Math.max(minBuf * 4, sr * 8 / 2); // about half a second: fewer CPU wake-ups
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                    .build();
        } catch (Exception e) {
            listener.onFinished();
            return;
        }

        float[] buf = new float[BLOCK * 2];
        Settings s = cfg;
        int soundNow = s.sound;
        Gens.Gen gen = Gens.create(soundNow, sr);
        Glide glide = new Glide(s.mode);
        float dt = BLOCK / (float) sr;
        float xf = 0f;                         // crossfade/stop gain
        float xfStep = 1f / (0.6f * sr);
        float startFade = 0f, startStep = 1f / (8f * sr); // gentle 8-second fade-in
        float gain = 0f;
        boolean finished = false;

        track.play();
        while (true) {
            if (paused && !stopRequested) {
                track.pause();
                synchronized (lock) {
                    while (paused && !stopRequested) {
                        try { lock.wait(); } catch (InterruptedException ignored) { }
                    }
                }
                if (stopRequested) break;
                track.play();
            }

            s = cfg;
            boolean switching = s.sound != soundNow;

            float u = glide.step(dt, s.intervalSec(), s.mode);
            float value = Sounds.valueAt(soundNow, s.lo[soundNow], s.hi[soundNow], u);
            currentValue = value;
            gen.render(buf, BLOCK, value);

            // target gain: perceptual volume curve x timer fade
            float vol = s.volume / 100f;
            float target = vol * vol;
            long d = deadline;
            if (d > 0) {
                long rem = d - SystemClock.elapsedRealtime();
                if (rem <= 0) { finished = true; stopRequested = true; }
                long fade = s.fadeMs();
                if (fade > 0 && rem < fade) {
                    float k = Math.max(0f, rem / (float) fade);
                    target *= k * k;
                }
            }

            float xfTarget = (switching || stopRequested) ? 0f : 1f;
            float gStep = (target - gain) / BLOCK;
            for (int i = 0; i < BLOCK; i++) {
                gain += gStep;
                if (xf < xfTarget) xf = Math.min(xfTarget, xf + xfStep);
                else if (xf > xfTarget) xf = Math.max(xfTarget, xf - xfStep);
                if (startFade < 1f) startFade = Math.min(1f, startFade + startStep);
                float g = gain * xf * startFade;
                buf[2 * i] = limit(buf[2 * i] * g);
                buf[2 * i + 1] = limit(buf[2 * i + 1] * g);
            }
            track.write(buf, 0, BLOCK * 2, AudioTrack.WRITE_BLOCKING);

            if (stopRequested && xf <= 0f) break;
            if (switching && xf <= 0f) {
                soundNow = s.sound;
                gen = Gens.create(soundNow, sr);
                glide.reset(s.mode);
            }
        }

        try {
            track.stop();
        } catch (IllegalStateException ignored) { }
        track.release();
        if (finished) listener.onFinished();
    }

    /** Transparent below 0.8, then rounds peaks off smoothly so nothing ever clips harshly. */
    private static float limit(float x) {
        float a = Math.abs(x);
        if (a <= 0.8f) return x;
        float over = a - 0.8f;
        float y = 0.8f + over / (1f + over * 5f);
        return x < 0 ? -y : y;
    }
}
