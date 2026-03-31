package com.oma.chat;

import com.getcapacitor.BridgeActivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(OMACallsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

@CapacitorPlugin(name = "OMACalls")
class OMACallsPlugin extends Plugin {
    private static final String CHANNEL_ID = "call_channel_v4";

    @PluginMethod
    public void showIncomingCall(PluginCall call) {
        String callerName = call.getString("name", "Someone");
        String callerId = call.getString("id", "0");
        
        Context context = getContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createCallChannel(notificationManager);

        // Intent to open app when Answer is clicked
        Intent answerIntent = new Intent(context, MainActivity.class);
        answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        answerIntent.putExtra("action", "accept");
        answerIntent.putExtra("callerId", callerId);
        PendingIntent answerPendingIntent = PendingIntent.getActivity(context, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent to decline call
        Intent declineIntent = new Intent(context, MainActivity.class);
        declineIntent.putExtra("action", "decline");
        declineIntent.putExtra("callerId", callerId);
        PendingIntent declinePendingIntent = PendingIntent.getActivity(context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Main intent to open app on tap
        Intent fullScreenIntent = new Intent(context, MainActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Person caller = new Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Incoming Call")
                .setContentText(callerName + " is calling you...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/raw/calling"))
                .setStyle(new NotificationCompat.CallStyle()
                        .forIncomingCall(caller, declinePendingIntent, answerPendingIntent));

        notificationManager.notify(101, builder.build());
        call.resolve();
    }

    private void createCallChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OMA Calls", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            channel.setSound(Uri.parse("android.resource://" + getContext().getPackageName() + "/raw/calling"), audioAttributes);
            
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
