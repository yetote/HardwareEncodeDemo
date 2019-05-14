package com.example.hardwareencodedemo;

import android.app.Application;

import com.tencent.bugly.crashreport.CrashReport;

/**
 * @author ether QQ:503779938
 * @name HardwareEncodeDemo
 * @class name：com.example.hardwareencodedemo
 * @class describe
 * @time 2019/5/14 16:13
 * @change
 * @chang time
 * @class describe
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.initCrashReport(this,"f00addfb0f",false);
    }
}
