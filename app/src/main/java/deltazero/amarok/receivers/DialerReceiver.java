package deltazero.amarok.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import deltazero.amarok.R;
import deltazero.amarok.ui.MainActivity;

public class DialerReceiver extends BroadcastReceiver {

    private static final String TAG = "DialerReceiver";
    private static final String CHANNEL_ID = "SECRET_CODE_CHANNEL";
    private static final int NOTIFICATION_ID = 2;
    private static final long NOTIFICATION_TIMEOUT_MS = 60_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);

        // Since Android 10 an app in the background may not open a screen, and the start above is
        // dropped without an error, which is what left the secret code doing nothing. Apps allowed
        // to draw over others are exempt. Otherwise offer a notification to tap, which always may.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)) {
            Log.i(TAG, "Background start may be blocked. Posting a notification to open Amarok.");
            postOpenNotification(context, launchIntent);
        }
    }

    private static void postOpenNotification(Context context, Intent launchIntent) {
        var notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.secret_code_channel_name), NotificationManager.IMPORTANCE_HIGH));

        var notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.secret_code_notification_title))
                .setSmallIcon(R.drawable.ic_open)
                .setContentIntent(PendingIntent.getActivity(context, 0, launchIntent,
                        PendingIntent.FLAG_IMMUTABLE))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
                .build();

        try {
            notificationManager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission denied.", e);
        }
    }

    /**
     * Remove the notification once Amarok is open, whichever way it got there.
     */
    public static void cancelOpenNotification(Context context) {
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
