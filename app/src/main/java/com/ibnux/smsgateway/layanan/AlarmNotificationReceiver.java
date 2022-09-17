package com.ibnux.smsgateway.layanan;

import static com.ibnux.smsgateway.layanan.PushService.writeLog;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

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

        SharedPreferences sp = context.getSharedPreferences("pref",0);
        String scrt =  sp.getString("secret","");
        Fungsi.log("Local Secret "+scrt+"\n"+
                "received Secret "+scheduledSMS.secret+"\n"+
                "Time "+scheduledSMS.time+"\n"+
                "To "+scheduledSMS.to+"\n"+
                "Message "+scheduledSMS.message);

        sendSmsNow(scheduledSMS.to, scheduledSMS.message,scheduledSMS.secret, scrt, sp, scheduledSMS.time, context);

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

    private void sendSmsNow(String to, String message, String secret, String scrt, SharedPreferences sp, String time, Context context) {
        if(!TextUtils.isEmpty(to) && !TextUtils.isEmpty(message) && !TextUtils.isEmpty(secret)){

            //cek dulu secret vs secret, jika oke, berarti tidak diHash, no expired
            if(scrt.equals(secret)){
                if(sp.getBoolean("gateway_on",true)) {
                    Fungsi.sendSMS(to, message, context);
                    writeLog("SEND SUCCESS: " + to + " " + message, context);
                }else{
                    writeLog("GATEWAY OFF: " + to + " " + message, context);
                }
            }else {
                int expired = sp.getInt("expired", 3600);
                if(TextUtils.isEmpty(time)) time = "0";
                long current = System.currentTimeMillis()/1000L;
                long senttime = Long.parseLong(time);
                long timeout = current - senttime;
                Fungsi.log(current + " - " + senttime + " : " +expired + " > " + timeout);
                if (timeout < expired) {
                    //hash dulu
                    // ngikutin https://github.com/YOURLS/YOURLS/wiki/PasswordlessAPI
                    scrt = Fungsi.md5(scrt.trim() + "" + time.trim());
                    Fungsi.log("MD5 : " + scrt);
                    if (scrt.toLowerCase().equals(secret.toLowerCase())) {
                        if(sp.getBoolean("gateway_on",true)) {
                            Fungsi.sendSMS(to, message, context);
                            writeLog("SEND SUCCESS: " + to + " " + message, context);
                        }else{
                            writeLog("GATEWAY OFF: " + to + " " + message, context);
                        }
                    } else {
                        writeLog("ERROR: SECRET INVALID : " + to + " " + message,context);
                    }
                } else {
                    writeLog("ERROR: TIMEOUT : " + current + " - " + senttime + " : " +expired + " > " + timeout+ " " + to + " " + message,context);
                }
            }
        }else{
            writeLog("ERROR: TO MESSAGE AND SECRET REQUIRED : " + to + " " + message,context);
        }
    }
}
