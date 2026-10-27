package app.drift.sleep;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: one tap plays (most recent settings) or stops; a double tap opens the app. */
public class SleepWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        refresh(c);
    }

    static void refresh(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        ComponentName me = new ComponentName(c, SleepWidget.class);
        if (mgr.getAppWidgetIds(me).length == 0) return;

        boolean playing = SleepService.isPlaying();
        Settings s = Settings.load(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.widget_btn, playing ? "STOP" : "PLAY");
        v.setTextColor(R.id.widget_btn, playing ? 0xFFD27800 : 0xFFA01800);
        v.setTextViewText(R.id.widget_sub, Sounds.NAMES[s.sound]
                + (s.timer == 0 ? "" : " · " + Sounds.fmtTimer(s.timer)));

        // Taps go to an invisible activity that tells a single tap (play/stop) from a double tap (open app).
        PendingIntent pi = PendingIntent.getActivity(c, 1,
                new Intent(c, WidgetTap.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(me, v);
    }
}
