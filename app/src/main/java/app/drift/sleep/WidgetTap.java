package app.drift.sleep;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Invisible activity that receives widget taps. A second tap within the window opens the app;
 * otherwise the single tap plays or stops. (Widgets have no built-in double-tap, so this is how
 * it's detected. It also means play/stop from the widget waits about a third of a second.)
 */
public class WidgetTap extends Activity {
    private static final long DOUBLE_TAP_MS = 350;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done;

    private final Runnable singleTap = () -> {
        if (done) return;
        done = true;
        startForegroundService(new Intent(this, SleepService.class).setAction(SleepService.ACTION_TOGGLE));
        quietFinish();
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        h.postDelayed(singleTap, DOUBLE_TAP_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (done) return;
        done = true;
        h.removeCallbacks(singleTap);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        quietFinish();
    }

    private void quietFinish() {
        finish();
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(singleTap);
        super.onDestroy();
    }
}
