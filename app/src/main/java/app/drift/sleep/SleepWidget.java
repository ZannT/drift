package app.drift.sleep;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: one tap plays (using the most recent settings) or stops. */
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

        PendingIntent pi = PendingIntent.getForegroundService(c, 1,
                new Intent(c, SleepService.class).setAction(SleepService.ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(me, v);
    }
}
