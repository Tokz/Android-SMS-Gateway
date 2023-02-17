package com.ibnux.smsgateway.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class ScheduledSMS {
    @Id
    public long id;
    public String time;
    public String to;
    public String message;
    public String scheduledDate;
    public String secret;
    public String status;
}
