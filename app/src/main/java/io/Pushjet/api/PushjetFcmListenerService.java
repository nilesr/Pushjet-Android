package io.Pushjet.api;


import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import io.Pushjet.api.Async.PushjetRegistrationService;
import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

public class PushjetFcmListenerService extends FirebaseMessagingService {
    private static int NOTIFICATION_ID = 0;
    private static final String TAG = "PushjetGcmListeners";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        try {
            JSONObject deser_service = new JSONObject(data.get("service"));
            PushjetService srv = new PushjetService(
                    deser_service.getString("public"),
                    deser_service.getString("name"),
                    new Date(deser_service.getLong("created") * 1000)
            );
            srv.setIcon(deser_service.getString("icon"));

            PushjetMessage msg = new PushjetMessage(
                    srv,
                    data.get("message"),
                    data.get("title"),
                    Long.parseLong(data.get("timestamp"))
            );
            msg.setLevel(Integer.parseInt(data.get("level")));
            msg.setLink(data.get("link"));
            DatabaseHandler db = new DatabaseHandler(this);
            db.addMessage(msg);
            sendNotification(msg);
        } catch (JSONException e) {
            Log.e("PushjetJson", e.toString(), e);
        }
    }

    private void sendNotification(PushjetMessage msg) {
        NOTIFICATION_ID++;
        NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("my_channel_01", "Pushjet", NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(ch);

        Intent intent = new Intent(this, PushListActivity.class);
        if (msg.hasLink()) {
            try {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
            }
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_notif)
                        .setContentTitle(msg.getTitleOrName())
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg.getMessage()))
                        .setContentText(msg.getMessage())
                        .setChannelId("my_channel_01")
                        .setAutoCancel(true);

        if (msg.getService().hasIcon()) {
            try {
                Bitmap icon = msg.getService().getIconBitmap(getApplicationContext());
                Resources res = getApplicationContext().getResources();

                int nHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
                int nWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

                mBuilder.setLargeIcon(MiscUtil.scaleBitmap(icon, nWidth, nHeight));
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        setPriority(mBuilder, msg);
        mBuilder.setDefaults(Notification.DEFAULT_ALL);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public void onNewToken(@NonNull String refreshedToken) {
        // Get updated InstanceID token.
        Log.d(TAG, "FCM Token refreshed.");
        Log.d(TAG, refreshedToken);

        // call PushjetRegistrationService to update the token
        Intent intent = new Intent(this, PushjetRegistrationService.class);
        intent.putExtra(PushjetRegistrationService.PROPERTY_FCM_TOKEN, refreshedToken);
        startService(intent);
    }


    private void setPriority(NotificationCompat.Builder mBuilder, PushjetMessage msg) {
        int priority = msg.getLevel() - 3;
        if(Math.abs(priority) > 2) {
            priority = 0;
        }

        mBuilder.setPriority(priority);
    }
}
