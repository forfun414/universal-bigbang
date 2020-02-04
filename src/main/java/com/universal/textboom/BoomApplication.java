package com.universal.textboom;

import android.app.Application;

import com.universal.textboom.util.ConfigUtils;

public class BoomApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ConfigUtils.init(this);
    }
}
