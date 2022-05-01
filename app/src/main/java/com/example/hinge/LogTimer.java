package com.example.hinge;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.time.Duration;
import java.time.Instant;

public class LogTimer {
    Instant initTime;
    Instant previousTime;
    Instant now;

    public LogTimer() {
        this.initTime = Instant.now();
        this.previousTime = initTime;
        this.now = initTime;
    }

    public Instant mark() {
        this.previousTime = this.now;
        this.now = Instant.now();
        return this.now;
    }

    public Duration getTimeSinceInit() {
        return Duration.between(this.initTime, this.now);
    }

    public Duration getTimeSincePreviousMark() {
        return Duration.between(this.previousTime, this.now);
    }

    public String toString() {
        mark();
        String value = "";
        if(this.getTimeSinceInit().getSeconds() > 0)
            value += this.getTimeSinceInit().getSeconds() + "S ";
        value += this.getTimeSinceInit().getNano() / 1000 + "us : ";
        if(this.getTimeSinceInit().getSeconds() > 0)
            value += this.getTimeSincePreviousMark().getSeconds() + "S ";
        value += this.getTimeSincePreviousMark().getNano() / 1000 + "us";
        //String value = this.getTimeSinceInit() + ":" + this.getTimeSincePreviousMark();
        return value;
    }
}
