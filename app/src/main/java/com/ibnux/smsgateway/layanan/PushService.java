package com.ibnux.smsgateway.layanan;

/**
 * Created by Ibnu Maksum 2020
 */

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.ibnux.smsgateway.Aplikasi;
import com.ibnux.smsgateway.Fungsi;
import com.ibnux.smsgateway.ObjectBox;
import com.ibnux.smsgateway.data.LogLine;
import com.ibnux.smsgateway.data.ScheduledSMS;

import io.objectbox.Box;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;

public class PushService extends FirebaseMessagingService {
    private static final long DISPLAY_TIME = 2000;
    private String TAG = "SMSin";
    private static Box<LogLine> logBox;

    private static Box<ScheduledSMS> scheduledSMSBox;

    private CompositeDisposable disposable = new CompositeDisposable();

    BroadcastReceiver deliveredReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String msg = null;
            switch (getResultCode())
            {
                case Activity.RESULT_OK:
                    msg = "success";
                    break;
                case Activity.RESULT_CANCELED:
                    msg = "failed";
                    break;
            }
            if(msg!=null) {
                writeLog("DELIVERED: "+msg + " : " + arg1.getStringExtra("number"),arg0);
                SmsListener.sendPOST(getSharedPreferences("pref",0).getString("urlPost",null),
                        arg1.getStringExtra("number"), msg,"delivered",arg0);
                Intent i = new Intent("MainActivity");
                i.putExtra("newMessage","newMessage");
                LocalBroadcastManager.getInstance(Aplikasi.app).sendBroadcast(i);
            }
        }
    };

    BroadcastReceiver sentReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String msg = null;
            switch (getResultCode())
            {
                case Activity.RESULT_OK:
                    msg = "success";
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    msg = "Generic failure";
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    msg = "No service";
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    msg = "Null PDU";
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    msg = "Radio off";
                    break;
            }
            if(msg!=null) {
                Calendar cal = Calendar.getInstance();
                writeLog("SENT: "+msg + " : " + arg1.getStringExtra("number"),arg0);
                SmsListener.sendPOST(getSharedPreferences("pref",0).getString("urlPost",null),
                        arg1.getStringExtra("number"), msg,"sent",arg0);
                Intent i = new Intent("MainActivity");
                i.putExtra("newMessage","newMessage");
                LocalBroadcastManager.getInstance(Aplikasi.app).sendBroadcast(i);
            }
        }
    };

    @Override
    public void onCreate() {
        registerReceiver(sentReceiver, new IntentFilter(Fungsi.SENT));
        registerReceiver(deliveredReceiver, new IntentFilter(Fungsi.DELIVERED));
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(sentReceiver);
        unregisterReceiver(deliveredReceiver);
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Fungsi.log(TAG, "From: " + remoteMessage.getFrom());

        if (remoteMessage.getData()!=null && remoteMessage.getData().size() > 0) {
            String schedule = remoteMessage.getData().get("scheduleTime");
            String to = remoteMessage.getData().get("to");
            String message = remoteMessage.getData().get("message");
            String secret = remoteMessage.getData().get("secret");
            String time  = "0";
            if(remoteMessage.getData().containsKey("time")) {
                time = remoteMessage.getData().get("time");
            }
            SharedPreferences sp = getSharedPreferences("pref",0);
            String scrt =  sp.getString("secret","");
            Fungsi.log("Local Secret "+scrt+"\n"+
                    "received Secret "+secret+"\n"+
                    "Time "+time+"\n"+
                    "To "+to+"\n"+
                    "Message "+message);

            if(to.contains(",")) {
                String[] recipients = to.split(",");

                for(String recipient: recipients)  {
//                    if(schedule != null) {
//                        saveToScheduledMessage(
//                                recipient,
//                                message,
//                                secret,
//                                scrt,
//                                sp,
//                                time,
//                                schedule
//                        );
//                    } else {
                        sendSmsNow(
                                recipient,
                                message,
                                secret,
                                scrt,
                                sp,
                                time
                        );
//                    }
                }

            } else {
                // add schedule
                if(schedule != null) {
                    saveToScheduledMessage(
                            to,
                            message,
                            secret,
                            scrt,
                            sp,
                            time,
                            schedule
                    );
                } else {
                    sendSmsNow(
                            to,
                            message,
                            secret,
                            scrt,
                            sp,
                            time
                    );
                }
            }
        }else{
            if(remoteMessage.getData()!=null) {
                writeLog("ERROR: NODATA : "+remoteMessage.getData().toString(),this);
            }else{
                writeLog("ERROR: NODATA : push received without data ",this);
            }
        }
    }



    private void sendSmsNow(String to, String message, String secret, String scrt, SharedPreferences sp, String time) {
        if(!TextUtils.isEmpty(to) && !TextUtils.isEmpty(message) && !TextUtils.isEmpty(secret)){

            //cek dulu secret vs secret, jika oke, berarti tidak diHash, no expired
            if(scrt.equals(secret)){
                if(sp.getBoolean("gateway_on",true)) {
                    Fungsi.sendSMS(to, message, this);
                    writeLog("SEND SUCCESS: " + to + " " + message, this);
                }else{
                    writeLog("GATEWAY OFF: " + to + " " + message, this);
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
                            Fungsi.sendSMS(to, message, this);
                            writeLog("SEND SUCCESS: " + to + " " + message, this);
                        }else{
                            writeLog("GATEWAY OFF: " + to + " " + message, this);
                        }
                    } else {
                        writeLog("ERROR: SECRET INVALID : " + to + " " + message,this);
                    }
                } else {
                    writeLog("ERROR: TIMEOUT : " + current + " - " + senttime + " : " +expired + " > " + timeout+ " " + to + " " + message,this);
                }
            }
        }else{
            writeLog("ERROR: TO MESSAGE AND SECRET REQUIRED : " + to + " " + message,this);
        }
    }

    public boolean saveSmsOnDB(String to, String message, String secret, String scrt, SharedPreferences sp, String time, String schedule) {
        boolean isSending = scheduledSMSBox.count() > 0;
        String[] recipients =  to.split(",");

        ScheduledSMS scheduledSMS = new ScheduledSMS();
        scheduledSMS.scheduledDate = schedule;
        scheduledSMS.message = message;
        scheduledSMS.time = time;
        scheduledSMS.to = to;
        scheduledSMS.secret = secret;

        if(scheduledSMSBox == null) {
            scheduledSMSBox = ObjectBox.get().boxFor(ScheduledSMS.class);
        }

        Log.e("PushService","current id: "+scheduledSMS.id);

        long id = scheduledSMSBox.put(scheduledSMS);

        return isSending;
    }

    private void saveToScheduledMessage(String to, String message, String secret, String scrt, SharedPreferences sp, String time, String schedule)  {
        ScheduledSMS scheduledSMS = new ScheduledSMS();
        scheduledSMS.scheduledDate = schedule;
        scheduledSMS.message = message;
        scheduledSMS.time = time;
        scheduledSMS.to = to;
        scheduledSMS.secret = secret;

        if(scheduledSMSBox == null) {
            scheduledSMSBox = ObjectBox.get().boxFor(ScheduledSMS.class);
        }

        Log.e("PushService","current id: "+scheduledSMS.id);

        long id = scheduledSMSBox.put(scheduledSMS);
        Log.e("PushService","saved id: "+id);
        Log.e("PushService","contains id: "+scheduledSMSBox.contains(id)+" current id:"+ scheduledSMSBox.get(id).scheduledDate);

        if(!scheduledSMS.scheduledDate.isEmpty()) {
            try {
                @SuppressLint("SimpleDateFormat") SimpleDateFormat formater=new SimpleDateFormat("yyyy-mm-dd hh:mm:ss aa");
                Date date6=formater.parse(scheduledSMS.scheduledDate);
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Singapore"));

                calendar.setTimeInMillis(date6.getTime());

                long currentTime = calendar.getTimeInMillis();

                Calendar now = Calendar.getInstance();

                if(currentTime > now.getTimeInMillis()) {
                    sendSmsNow(
                            to,
                            message,
                            secret,
                            scrt,
                            sp,
                            time
                    );
                } else {
                    startAlarm(id, currentTime);
                }

            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void startAlarm(Long id, Long scheduled) {
        Log.e("PUSHSERViCE",""+scheduled);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent myIntent;
        PendingIntent pendingIntent;

        myIntent = new Intent(this, AlarmNotificationReceiver.class);
        myIntent.putExtra("SMS", id);
        pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            manager.set(AlarmManager.RTC_WAKEUP, scheduled, pendingIntent);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, scheduled, pendingIntent);
        }
    }

    static public void writeLog(String message, Context cx){
        if(logBox==null){
            logBox = ObjectBox.get().boxFor(LogLine.class);
        }
        LogLine ll = new LogLine();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        ll.time = cal.getTimeInMillis();
        ll.date = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + " " +
                cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);
        ll.message = message;
        logBox.put(ll);
        Intent i = new Intent("MainActivity");
        i.putExtra("newMessage","newMessage");
        if(cx==null) cx = Aplikasi.app;
        LocalBroadcastManager.getInstance(cx).sendBroadcast(i);
    }

    @Override
    public void onNewToken(String s) {
        Fungsi.log("onNewToken "+s);
        getSharedPreferences("pref",0).edit().putString("token",s).apply();
        Intent i = new Intent("MainActivity");
        i.putExtra("newToken","newToken");
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        super.onNewToken(s);
    }
}
