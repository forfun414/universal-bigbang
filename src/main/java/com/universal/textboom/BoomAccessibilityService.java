package com.universal.textboom;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.universal.textboom.floating.FloatingService;
import com.universal.textboom.util.ActivityStarter;
import com.universal.textboom.util.LogUtils;
import com.universal.textboom.R;
import java.util.List;

public class BoomAccessibilityService extends AccessibilityService {
    private static String TAG = "BoomAccessibilityService";
    private static Rect mBangIconBount = new Rect();
    private static boolean foundText = false;

    public BoomAccessibilityService() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UNBIND_FLOATING_ACTION);
        registerReceiver(mUnBindReceiver, intentFilter);

        Intent intent = new Intent(this, FloatingService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    public static String UNBIND_FLOATING_ACTION = "unbind_floating_action";

    public static void sendUnbindFloatBroadcast(Context context) {
        Intent intent = new Intent();
        intent.setAction(UNBIND_FLOATING_ACTION);
        context.sendBroadcast(intent);
    }

    private BroadcastReceiver mUnBindReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtils.d(TAG, "mUnBindReceiver: " + action);
            if (action.equals(UNBIND_FLOATING_ACTION)) {
                onUnbind(null);
            }
        }
    };



    private FloatingService mService;
    private boolean mBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            FloatingService.LocalBinder binder = (FloatingService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Override
    public boolean onUnbind(Intent intent) {
        LogUtils.d(TAG, "onunbind service, accessibility is disabled");
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        return false;
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        String eventText = "";
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                if (FloatingService.isHiding())
                    return;

                foundText = false;
                eventText = "TYPE_VIEW_CLICKED";

                AccessibilityNodeInfo noteInfo = event.getSource();
                //LogUtils.d(TAG, "clicked noteInfo " + noteInfo);

                String resourceName = noteInfo.getViewIdResourceName();
                LogUtils.d(TAG, "aaagetViewIdResourceName " + resourceName);
                //if (noteInfo.findAccessibilityNodeInfosByViewId("@id/icon"))//but no id for the icon
                if (noteInfo.getPackageName().equals("com.universal.textboom") && noteInfo.getClassName().equals("android.widget.FrameLayout")  ) {//android.widget.ImageView
                    LogUtils.d(TAG, "getViewIdResourceName " + noteInfo.getViewIdResourceName());
                    noteInfo.getBoundsInScreen(mBangIconBount);
                    LogUtils.d(TAG, "FloatingService clicked and got bangicon " + mBangIconBount);
                    int centerX = (mBangIconBount.left + mBangIconBount.right)/2;
                    int centerY = (mBangIconBount.top + mBangIconBount.bottom)/2;
                    //narrow the bounds to a very small one which is the center of thr real bounds
                    mBangIconBount = new Rect(centerX, centerY, centerX + 1, centerY + 1);
                }

                AccessibilityNodeInfo root = getRootInActiveWindow();
                LogUtils.d(TAG, "findView fromroot " + root + " nodeinfo:" + noteInfo.getPackageName());
                findViewbyRect(root);

                //no text find, toast to info user
                if (!foundText) {
                    LogUtils.d(TAG, "didn't find text bz not text view");
                    Toast.makeText(BoomAccessibilityService.this, getString(R.string.a_msg_no_words), Toast.LENGTH_SHORT).show();
                    //ActivityStarter.startDummyTextBoom(this, mBangIconBount.left, mBangIconBount.top, "");//image boom
                }

                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                eventText = "TYPE_VIEW_FOCUSED";
                break;
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                eventText = "TYPE_VIEW_LONG_CLICKED";
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                eventText = "TYPE_VIEW_SELECTED";
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                eventText = "TYPE_VIEW_TEXT_CHANGED";
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                eventText = "TYPE_WINDOW_STATE_CHANGED";
                break;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                eventText = "TYPE_NOTIFICATION_STATE_CHANGED";
                break;
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END:
                eventText = "TYPE_TOUCH_EXPLORATION_GESTURE_END";
                break;
            case AccessibilityEvent.TYPE_ANNOUNCEMENT:
                eventText = "TYPE_ANNOUNCEMENT";
                break;
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                eventText = "TYPE_TOUCH_EXPLORATION_GESTURE_START";
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                eventText = "TYPE_VIEW_HOVER_ENTER";
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT:
                eventText = "TYPE_VIEW_HOVER_EXIT";
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                eventText = "TYPE_VIEW_SCROLLED";
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                eventText = "TYPE_VIEW_TEXT_SELECTION_CHANGED";
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                eventText = "TYPE_WINDOW_CONTENT_CHANGED";
                break;
        }

        //LogUtils.d(TAG, "onAccessibilityEvent " + eventText + "  ");
        event.getSource();
    }

    void findViewbyRect(AccessibilityNodeInfo parent) {
        if (parent != null) {
            int count = parent.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = parent.getChild(i);

                if (child != null) {
                    int countInChild = child.getChildCount();
                    //the leaf child, that is the the real view
                    if (countInChild == 0) {
                        Rect current = new Rect();
                        child.getBoundsInScreen(current);

                        LogUtils.d(TAG, "findViewbyRect got child " + child + " rect:" + current + ", mBang:" + mBangIconBount);
                        if (current.contains(mBangIconBount)) {//intersect
                            if (child.getClassName().toString().contains("TextView")) {
                                //LogUtils.d(TAG, "findViewbyRect got child contains " + child);
                                //LogUtils.d(TAG, " rect " + current + " icon: " + mBangIconBount);

                                String text = child.getText() != null ? child.getText().toString() : null;
                                LogUtils.d(TAG, "findViewbyRect got target " + child);
                                LogUtils.d(TAG, "startTextBoom " + text);

                                if (text != null && text.length() != 0) {
                                    foundText = true;
                                    mService.hideFloating();
                                    mService.startBoom(this, mBangIconBount.left, mBangIconBount.top, text);
                                } else {
                                    //ActivityStarter.startTextBoom(this, mBangIconBount.left, mBangIconBount.top, text);
                                    Toast.makeText(BoomAccessibilityService.this, getString(R.string.a_msg_no_words), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                //not textview, use ocr
                                //Toast.makeText(BoomAccessibilityService.this, getString(R.string.boom_image_not_ready), Toast.LENGTH_SHORT).show();
                                //ActivityStarter.startDummyTextBoom(this, mBangIconBount.left, mBangIconBount.top, ""); //image boom
                            }
                        }

                    } else {
                        findViewbyRect(child);
                    }

                }
            }
        }

    }
    //todo, List windows = getWindows(); only supported by api level 21, can change later

    @Override
    public void onInterrupt() {
        onUnbind(null);
    }
}
