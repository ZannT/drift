package app.drift.sleep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/** Foreground service that owns the audio engine, the timer, audio focus and the notification. */
public class SleepService extends Service implements Engine.Listener, AudioManager.OnAudioFocusChangeListener {
    static final String ACTION_PLAY = "app.drift.sleep.PLAY";
    static final String ACTION_STOP = "app.drift.sleep.STOP";
    static final String ACTION_TOGGLE = "app.drift.sleep.TOGGLE";
    static final String ACTION_UPDATE = "app.drift.sleep.UPDATE";
    static final String ACTION_RESTART_TIMER = "app.drift.sleep.TIMER";

    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 1;

    private static volatile SleepService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Engine engine;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private boolean noisyRegistered;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Earbuds disconnected or put back in the case: stop rather than switch to the phone speaker.
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) shutdown();
        }
    };

    static boolean isPlaying() {
        SleepService s = instance;
        return s != null && s.engine != null;
    }

    /** Milliseconds left on the timer, or -1 when there is no timer or nothing is playing. */
    static long remainingMs() {
        SleepService s = instance;
        if (s == null || s.engine == null || s.engine.deadline() == 0) return -1;
        return Math.max(0, s.engine.deadline() - SystemClock.elapsedRealtime());
    }

    static float currentValue() {
        SleepService s = instance;
        return s == null || s.engine == null ? 0 : s.engine.currentValue;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        audio = getSystemService(AudioManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Settings s = Settings.load(this);
        // Always enter the foreground first; Android requires it for every startForegroundService call.
        startForeground(NOTIF_ID, buildNotification(s), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) action = engine != null ? ACTION_STOP : ACTION_PLAY;

        if (ACTION_PLAY.equals(action)) {
            if (engine == null) begin(s);
            else engine.update(s);
        } else if (ACTION_UPDATE.equals(action) && engine != null) {
            engine.update(s);
            refreshNotification(s);
        } else if (ACTION_RESTART_TIMER.equals(action) && engine != null) {
            engine.update(s);
            engine.setDeadline(deadlineFor(s));
            holdWakeLock(s);
            refreshNotification(s);
        } else {
            shutdown();
        }
        return START_NOT_STICKY;
    }

    private static long deadlineFor(Settings s) {
        return s.timer == 0 ? 0 : SystemClock.elapsedRealtime() + s.timerMs();
    }

    private void begin(Settings s) {
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(this, main)
                .build();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            shutdown(); // e.g. during a phone call
            return;
        }
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED);
        noisyRegistered = true;
        holdWakeLock(s);
        engine = new Engine(s, deadlineFor(s), this);
        engine.start();
        refreshNotification(s);
        SleepWidget.refresh(this);
    }

    private void holdWakeLock(Settings s) {
        if (wake == null) {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "drift:playback");
            wake.setReferenceCounted(false);
        }
        long ms = s.timer == 0 ? 12 * 3_600_000L : s.timerMs() + 120_000L;
        wake.acquire(ms);
    }

    private void shutdown() {
        if (engine != null) {
            engine.stop(); // fades out over ~0.6 s, then releases the audio track
            engine = null;
        }
        if (noisyRegistered) {
            unregisterReceiver(noisy);
            noisyRegistered = false;
        }
        if (focus != null) {
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
        if (wake != null && wake.isHeld()) {
            // keep the CPU up briefly so the fade-out can finish
            wake.acquire(2_000);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        SleepWidget.refresh(this);
    }

    @Override
    public void onFinished() {
        main.post(this::shutdown); // timer ran out
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (engine == null) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                shutdown();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                engine.setPaused(true);  // phone call, alarm, etc.
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                engine.setPaused(false);
                break;
            default:
                break; // ducking for short notification sounds is handled by the system
        }
    }

    private void refreshNotification(Settings s) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(s));
    }

    private Notification buildNotification(Settings s) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, SleepService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = Sounds.NAMES[s.sound] + (s.timer == 0 ? "" : " · " + Sounds.fmtTimer(s.timer));
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Drift")
                .setContentText(text)
                .setColor(0xFFA01800)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat), "Stop", stop).build())
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    @Override
    public void onDestroy() {
        if (engine != null) shutdown();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
