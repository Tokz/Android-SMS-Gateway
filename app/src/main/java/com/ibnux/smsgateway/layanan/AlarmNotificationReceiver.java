package com.ibnux.smsgateway.layanan;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.ibnux.smsgateway.Fungsi;
import com.ibnux.smsgateway.ObjectBox;
import com.ibnux.smsgateway.R;
import com.ibnux.smsgateway.data.ScheduledSMS;

import io.objectbox.Box;

public class AlarmNotificationReceiver extends BroadcastReceiver {

    private static Box<ScheduledSMS> scheduledSMSBox;

    @Override
    public void onReceive(Context context, Intent intent) {

        long smsId = intent.getLongExtra("SMS", 0);

        if(scheduledSMSBox == null) {
            scheduledSMSBox = ObjectBox.get().boxFor(ScheduledSMS.class);
        }

        ScheduledSMS scheduledSMS = scheduledSMSBox.get(smsId);

        Fungsi.sendSMS(scheduledSMS.to, scheduledSMS.message, context);

        scheduledSMSBox.remove(smsId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Androidcoding.in")
                .setContentText("Alarm Testing Task")
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND)
                .setContentInfo("Info");

        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1,builder.build());
    }
}
