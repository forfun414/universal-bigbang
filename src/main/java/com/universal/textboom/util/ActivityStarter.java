package com.universal.textboom.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class ActivityStarter {
    private static final String TAG = "ActivityStart";

    public static void startAccessibilitySetting(Context context) {
        Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
        context.startActivity(intent);
    }

    private static void startTextBoom(Context context, int x, int y, String text) {
        try {
            Intent intent = new Intent();
            intent.setClassName(Constant.TEXTBOOM_PKG_NAME, Constant.TEXTBOOM_PKG_NAME + ".BoomActivity");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.putExtra("boom_startx", x);
            intent.putExtra("boom_starty", y);
            int index = (text.length() > 0) ? text.length() - 1 : 0;
            intent.putExtra("boom_index", index);
            intent.putExtra("caller_pkg", context.getPackageName());
            //intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            context.startActivity(intent);
            if (context instanceof Activity) {
                // Fix Wechat override transition issue
                ((Activity) context).overridePendingTransition(0, 0);
            }
        } catch (ActivityNotFoundException e) {
            LogUtils.e(TAG, "Text boom Error e=" + e);
        }
    }

    private static void startDummyTextBoom(Context context, int x, int y, String text) {
        try {
            Intent intent = new Intent();
            intent.setClassName(Constant.TEXTBOOM_PKG_NAME, Constant.TEXTBOOM_PKG_NAME + ".BoomDummyActivity");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.putExtra("boom_startx", x);
            intent.putExtra("boom_starty", y);
            int index = (text.length() > 0) ? text.length() - 1 : 0;
            intent.putExtra("boom_index", index);
            intent.putExtra("caller_pkg", context.getPackageName());
            //intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            LogUtils.e(TAG, "Text boom Error e=" + e);
        }
    }

}
