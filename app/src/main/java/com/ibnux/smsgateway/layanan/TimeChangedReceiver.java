package com.ibnux.smsgateway.layanan;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ibnux.smsgateway.ObjectBox;
import com.ibnux.smsgateway.data.ScheduledSMS;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.objectbox.Box;

public class TimeChangedReceiver extends BroadcastReceiver {

    private static Box<ScheduledSMS> scheduledSMSBox;


    @Override
    public void onReceive(Context context, Intent intent) {
        if(scheduledSMSBox == null) {
            scheduledSMSBox = ObjectBox.get().boxFor(ScheduledSMS.class);
        }

        List<ScheduledSMS> scheduledSMSList = scheduledSMSBox.getAll();

        for(int i = 0; i <= scheduledSMSList.size(); i++ ) {
            ScheduledSMS scheduledSMS = scheduledSMSList.get(i);
            //2022-09-17 20:11:01
            if(!scheduledSMS.scheduledDate.isEmpty()) {
                try {
                    @SuppressLint("SimpleDateFormat") SimpleDateFormat formater=new SimpleDateFormat("yyyy-dd-mm HH:mm:ss");
                    Date date6=formater.parse(scheduledSMS.scheduledDate);
                    startAlarm(scheduledSMS.id, date6.getTime(), context);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void startAlarm(Long id, Long scheduled, Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent myIntent;
        PendingIntent pendingIntent;

        myIntent = new Intent(context, AlarmNotificationReceiver.class);
        myIntent.putExtra("SMS", id);
        pendingIntent = PendingIntent.getBroadcast(context, 0, myIntent, 0);

        manager.set(AlarmManager.RTC, scheduled, pendingIntent);
    }
}
