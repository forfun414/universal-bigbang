package com.universal.textboom.floating;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.universal.textboom.BoomAccessibilityService;
import com.universal.textboom.BoomStartingController;
import com.universal.textboom.R;
import com.universal.textboom.util.LogUtils;

import java.util.ArrayList;


public class FloatingService extends Service {
    public static String TAG = "FloatingService";

    private static final int ID_NOTIFICATION = 1001;
    private static final int FLOAT_ICON_HIDE_TIMEOUT = 1500;
    private static final int FLOAT_ICON_FIRST_HIDE_TIMEOUT = 3000;
    private static final int FLOAT_FULL_SCREEN_TIMEOUT = 3000;

    private static final int MSG_FLOAT_ICON_HIDE = 1001;
    private static final int MSG_FLOAT_ICON_DISMISS = 1002;
    private static final int MSG_FLOAT_ICON_SHOW = 1003;
    private static int mFloatSizeInDp = 44;
    private int mLastFloatY = 0;
    private int mLastFloatX = 0;

    private static int mDisplayWidth;
    private static boolean mHidden = true;
    private static boolean mAllowBoom = true;
    private static boolean mFirstFloat = true;

    private MyTouchListener mTouchListener;

    private WindowManager mWindowManager;
    private ImageView mFloatImage;

    private FrameLayout mFloatDecor;
    private FrameLayout mTransparentLayer;

    boolean mHasDoubleClicked = false;
    long lastPressTime;
    ArrayList<String> list;
    private Handler mHandler;

    private final IBinder mBinder = new LocalBinder();
    private Thread mScreenMonitorThread;
    private FullScreenMonitor mScreenMonitor;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public FloatingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FloatingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.d(TAG, "onBind");
        mScreenMonitor.setReadyState(true);

        return mBinder;
    }

    private void removeTransparentView() {
        if (mTransparentLayer != null) {
            mWindowManager.removeView(mTransparentLayer);
            mTransparentLayer = null;
        }
    }

    private void addTransparentView() {
        final WindowManager.LayoutParams transParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);//UNKNOWN

        transParams.gravity = Gravity.TOP | Gravity.LEFT;
        transParams.x = 0;
        transParams.y = 0;
        LayoutInflater transInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTransparentLayer = (FrameLayout) transInflater.inflate(R.layout.transparent_layer, null);
        mWindowManager.addView(mTransparentLayer, transParams);

        mBoomController = new BoomStartingController(getApplicationContext(), mTransparentLayer);
    }

    private void addFloatView() {
        LogUtils.d(TAG, "addFloatView mFloatDecor= " + mFloatDecor + " pid " + android.os.Process.myPid());

        if (mFloatDecor == null) {
            int size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mFloatSizeInDp, getResources().getDisplayMetrics()) + 0.5f);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);//UNKNOWN

            params.gravity = Gravity.TOP | Gravity.LEFT;


            if (mLastFloatY != 0) {
                params.x = mLastFloatX;
                params.y = mLastFloatY;
            } else {
                params.x = 0;
                params.y = 100;
            }


            LayoutInflater Inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFloatDecor = (FrameLayout) Inflater.inflate(R.layout.floating_decor, null);
            mWindowManager.addView(mFloatDecor, params);

            mFloatImage = (ImageView) mFloatDecor.findViewById(R.id.float_image);
            mFloatImage.setImageResource(R.drawable.smartisan_boom_circle);//float_icon smartisan_boom_circle
            mFloatImage.setVisibility(View.VISIBLE);

            mTouchListener = new MyTouchListener(params);
            mFloatDecor.setOnTouchListener(mTouchListener);

            mFloatDecor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    LogUtils.d(TAG, "onClick: ");
                    //initiatePopupWindow(mFloatDecor);
                }
            });

            LogUtils.d(TAG, "addFloatView 22 " + mFloatDecor);
            if (mFirstFloat) {
                lightingCount = 0;
                startLighteningAnimation();
                mFirstFloat = false;
            } else {
                mHidden = false;
                Message msg = mHandler.obtainMessage(MSG_FLOAT_ICON_HIDE);
                mHandler.sendMessage(msg);
            }
            //doesn't work, comment now
            /*LogUtils.d(TAG, "onSystemUiVisibilityChange before " );
            //monitor for fullscreen
            mFloatDecor.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            LogUtils.d(TAG, "onSystemUiVisibilityChange " +  visibility);

                            int diff = mLastSystemUiVis ^ visibility;
                            mLastSystemUiVis = visibility;
                            if ((diff & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                                    && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
                                dissmissFloating();
                            }
                        }
                    });
            LogUtils.d(TAG, "onSystemUiVisibilityChange after ");
            */
        }
    }

    private void removeFloatView() {
        mHidden = true;
        LogUtils.d(TAG, "removeFloatView " + mFloatDecor);
        if (mFloatDecor != null) {
            mLastFloatX = mTouchListener.paramsF.x;
            mLastFloatY = mTouchListener.paramsF.y;

            mWindowManager.removeView(mFloatDecor);
            mFloatDecor.setOnTouchListener(null);

            mLighteningAnimator = null;
            mTouchListener = null;
            mFloatDecor = null;
            mFloatImage = null;
        }
    }

    private void addScreenMonitor() {
        if (mScreenMonitor == null) {
            mScreenMonitor = new FullScreenMonitor();
            mScreenMonitorThread = new Thread(mScreenMonitor, "fsmonitor");
            mScreenMonitorThread.start();
        }
    }

    private void removeScreenMonitor() {
        if (mScreenMonitor != null) {
            mScreenMonitor.setStop();
            mScreenMonitor = null;
            mScreenMonitorThread = null;
        }
    }

    class FullScreenMonitor implements Runnable{
        private volatile boolean isStopped = false;
        private volatile boolean monitorReady = false;

        private int triggerCount = 0;
        private final int triggerNumber = 5;

        public void setReadyState(boolean ready){
            LogUtils.d(TAG, "FullScreenMonitor setReadyState " + ready);
            monitorReady = ready;
        }

        public void setStop(){
            LogUtils.d(TAG, "FullScreenMonitor stop");
            isStopped = true;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            int[] top = new int[2];
            boolean recordIsFullScreen = false;
            boolean isFullScreen = false;
            boolean forceHide = false;
            WindowManager.LayoutParams transParams = (WindowManager.LayoutParams) mTransparentLayer.getLayoutParams();

            while( !isStopped ) {
                LogUtils.d(TAG, "FullScreenMonitor loop");

                //when mTransparentLayer are create in oncreate, view is not draw yet, so getLocationOnScreen may get 0 on the first time
                //add a state check monitorReady here
                if (monitorReady) {
                    mTransparentLayer.getLocationOnScreen(top);
                    LogUtils.d(TAG, " Thread pppppp: top[0] " + top[0] + " top[1]: " + top[1] + ", xx " + transParams.x + ", yy " + transParams.y);

                    //todo should check wether it's in landscape, skiip now, not big problem
                        /*int orientation = mWindowManager.getDefaultDisplay().getOrientation();
                        forceHide = orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270;
                        if (forceHide) {
                            Message msg = mHandler.obtainMessage(MSG_FLOAT_ICON_DISMISS);
                            LogUtils.d(TAG, "force hide when in landscape");
                            mHandler.sendMessage(msg);
                        }
                       */


                    if (top[1] - transParams.y == 0) {// || top[0] - transParams.x == 0
                        isFullScreen = true;
                    } else {
                        isFullScreen = false;
                    }
                    LogUtils.d(TAG, "check FullScreen = " + isFullScreen);

                    if (isFullScreen != recordIsFullScreen) {
                        triggerCount++;
                        if (triggerCount == triggerNumber) {
                            Message msg;
                            if (isFullScreen) {
                                msg = mHandler.obtainMessage(MSG_FLOAT_ICON_DISMISS);
                            } else {
                                msg = mHandler.obtainMessage(MSG_FLOAT_ICON_SHOW);
                            }
                            LogUtils.d(TAG, "change visibility bz now FullScreen = " + isFullScreen);
                            mHandler.sendMessage(msg);

                            recordIsFullScreen = isFullScreen;
                            triggerCount = 0;
                        }
                    } else {
                        triggerCount = 0;
                    }
                }

                try {
                    Thread.sleep(FLOAT_FULL_SCREEN_TIMEOUT);
                } catch (InterruptedException e) {
                    LogUtils.d(TAG, "Thread ppppp exception " + e);
                    e.printStackTrace();
                }
            }

            LogUtils.d(TAG, "FullScreenMonitor thread exit");
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplayWidth = mWindowManager.getDefaultDisplay().getWidth();
        mHandler = new MyHandler();
        //if have notificaion, clear
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(ID_NOTIFICATION);
        LogUtils.d(TAG, "oncreate");

        addTransparentView();
        addScreenMonitor();
        addFloatView();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.d(TAG, "onStartCommand");

        addFloatView();
        // keep it alive.
        return START_STICKY;
    }



    //can't dissmiss bz if dismissed, getLocationOnScreen will not change and then will not trigger show action
    //todo with other callback method, then don't need to set alpha
    private void dissmissFloating() {
        mFloatDecor.setVisibility(View.INVISIBLE);
        if (isFullScreen() || isRuningAppInDismissList()) {
        }
    }

    private void showFloating() {
        mFloatDecor.setVisibility(View.VISIBLE);
    }

    private boolean isRuningAppInDismissList() {
        boolean res = false;
        return res;
    }

    private int mLastSystemUiVis = 0;

    private boolean isFullScreen() {
        boolean res = false;
        return res;
    }

    private void setFloatingAlpha(float alpha) {
        mFloatDecor.setAlpha(alpha);
    }

    private BoomStartingController mBoomController;
    public void startBoom(Context context, int x, int y, String text) {
        //todo support image boom
        mBoomController.startBoom(x, y, text);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int width = mWindowManager.getDefaultDisplay().getWidth();
        int height = mWindowManager.getDefaultDisplay().getHeight();
        LogUtils.d(TAG, "onConfigurationChanged !!!!!! " + newConfig + " neww:" + width + " newh:" + height);

        if (width != mDisplayWidth && mTouchListener != null) {
            int newX =  mTouchListener.paramsF.y;
            int newY =  mTouchListener.paramsF.x;
            LogUtils.d(TAG, "swap x and y new x:" + mTouchListener.paramsF.x + " y:" + mTouchListener.paramsF.y);

            mDisplayWidth = width;
            mTouchListener.paramsF.x = newX;
            mTouchListener.paramsF.y = newY;
            mWindowManager.updateViewLayout(mFloatDecor, mTouchListener.paramsF);
        }

    }

    private AnimatorSet mLighteningAnimator;
    private static final float LIGHTING_START_SCALE = 1f;
    private static final float LIGHTING_END_SCALE = 0.4f;
    private static int lightingCount = 0;

    private void startLighteningAnimation() {
        if (mLighteningAnimator == null) {
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mFloatDecor, View.ALPHA, 0, 1f);
            alphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (animation.isRunning()) {
                        //LogUtils.d(TAG, "startLoopAnimation onAnimationUpdate !!!!!! ");
                    }
                }
            });
            alphaAnimator.setDuration(100);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(mFloatDecor, View.SCALE_X, LIGHTING_START_SCALE, LIGHTING_END_SCALE);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(mFloatDecor, View.SCALE_Y, LIGHTING_START_SCALE, LIGHTING_END_SCALE);
            scaleX.setDuration(600);
            scaleY.setDuration(600);

            mLighteningAnimator = new AnimatorSet();
            mLighteningAnimator.setStartDelay(100);
            mLighteningAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
            mLighteningAnimator.playTogether(alphaAnimator, scaleX, scaleY);
            mLighteningAnimator.addListener(new Animator.AnimatorListener() {
                private boolean mIsCanceled = false;

                @Override
                public void onAnimationStart(Animator animation) {
                    mIsCanceled = false;
                    LogUtils.d(TAG, "mLighteningAnimator onAnimationStart ");
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    LogUtils.d(TAG, "mLighteningAnimator onAnimationEnd ");
                    if (mIsCanceled) {
                        return;
                    }

                    /*
                    mCircleView.bringToFront();
                    mCircleView.setVisibility(View.VISIBLE);
                    mCircleView.setTranslationX(mDownX - mCircleView.getMeasuredWidth() / 2);
                    mCircleView.setTranslationY(mDownY - mCircleView.getMeasuredHeight() / 2);
                    startCircleAnimation();*/

                    if (lightingCount++ <=2)
                        startLighteningAnimation();
                    else {
                        mFloatDecor.setScaleX(LIGHTING_START_SCALE);
                        mFloatDecor.setScaleY(LIGHTING_START_SCALE);

                        mHidden = false;

                        //when first create, hide the icon for a longer time? todo
                        Message msg = mHandler.obtainMessage(MSG_FLOAT_ICON_HIDE);
                        mHandler.sendMessage(msg);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mIsCanceled = true;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }
        LogUtils.d(TAG, "mLighteningAnimator start ");
        mLighteningAnimator.start();
    }

    private class MyTouchListener implements View.OnTouchListener {
        public WindowManager.LayoutParams paramsF;
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private int lastUpX;

        MyTouchListener(WindowManager.LayoutParams params) {
            paramsF = params;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mHandler.hasMessages(MSG_FLOAT_ICON_HIDE))
                        mHandler.removeMessages(MSG_FLOAT_ICON_HIDE);

                    // Get current time in nano seconds.
                    long pressTime = System.currentTimeMillis();

                    initialX = paramsF.x;
                    initialY = paramsF.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    LogUtils.d(TAG, "ACTION_down: " + event.getRawX());

                    mFloatImage.setImageResource(R.drawable.smartisan_boom_circle);
                    mFloatImage.setBackgroundResource(R.drawable.smartisan_boom_loop);

                    // If double click...
                    if (pressTime - lastPressTime <= 300) {
                        LogUtils.d(TAG, "ACTION_down:  double click now:" + pressTime + " last: " + lastPressTime);

                        FloatingService.this.createNotification();
                        removeFloatView();
                        mHasDoubleClicked = true;
                    } else {
                        mHasDoubleClicked = false;
                    }

                    lastPressTime = pressTime;
                    break;

                case MotionEvent.ACTION_UP:
                    if (mHandler.hasMessages(MSG_FLOAT_ICON_HIDE))
                        mHandler.removeMessages(MSG_FLOAT_ICON_HIDE);

                    int rawX = (int) event.getRawX();
                    lastUpX = rawX;
                    LogUtils.d(TAG, "ACTION_UP with rawx " + rawX);

                    //perform click to trigger accessibility and then boom
                    //mFloatDecor.performClick();

                    //remove message already enquened
                    if (mHandler.hasMessages(MSG_FLOAT_ICON_HIDE))
                        mHandler.removeMessages(MSG_FLOAT_ICON_HIDE);


                    //double check to hide the icon incase of accessibiliby is not worked
                    Message msg = mHandler.obtainMessage(MSG_FLOAT_ICON_HIDE);
                    mHandler.sendMessageDelayed(msg, FLOAT_ICON_HIDE_TIMEOUT);

                    break;

                case MotionEvent.ACTION_MOVE:
                    mHidden = false;
                    paramsF.x = initialX + (int) (event.getRawX() - initialTouchX);
                    paramsF.y = initialY + (int) (event.getRawY() - initialTouchY);
                    mWindowManager.updateViewLayout(mFloatDecor, paramsF);
                    //LogUtils.d(TAG, "ACTION_move: ");

                    break;
            }
            return false;
        }
    }


    public void hideFloating() {
        if (mHandler.hasMessages(MSG_FLOAT_ICON_HIDE))
            mHandler.removeMessages(MSG_FLOAT_ICON_HIDE);

        Message msg = mHandler.obtainMessage(MSG_FLOAT_ICON_HIDE);
        mHandler.sendMessage(msg);
    }

    public static boolean isHiding() {
        return mHidden;
    }

    private class MyHandler extends Handler {
        MyHandler() {
            super();
        }
        MyHandler(Handler handler) {
            super(handler.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_FLOAT_ICON_HIDE:
                    if (mFloatDecor != null && mHidden == false) {
                        mDisplayWidth = mWindowManager.getDefaultDisplay().getWidth();

                        LogUtils.d(TAG, "handlemessage screen orientation changed " + " new" + mDisplayWidth + ",height " + mWindowManager.getDefaultDisplay().getHeight());

                        mHidden = true;
                        final int rawX = mTouchListener.lastUpX;

                        LogUtils.d(TAG, "handlemessage and hide icon rawX: " + rawX + " mDisplayWidth " + mDisplayWidth + " iconwidth " + mTouchListener.paramsF.width);
                        ValueAnimator hidingAnm = new ValueAnimator();
                        hidingAnm.setFloatValues(0f, 1f);
                        hidingAnm.setDuration(200);
                        hidingAnm.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                Float value = (Float) animation.getAnimatedValue();

                                if (rawX >= mDisplayWidth / 2) {
                                    //move to right
                                    int maxMoveX = mDisplayWidth - mTouchListener.paramsF.width - rawX;
                                    mTouchListener.paramsF.x = (int) (rawX + value * maxMoveX);
                                } else {
                                    mTouchListener.paramsF.x = (int) (rawX - value * rawX);
                                }
                                //LogUtils.d(TAG, "update to new x: " + mTouchListener.paramsF.x);
                                mWindowManager.updateViewLayout(mFloatDecor, mTouchListener.paramsF);
                            }
                        });
                        hidingAnm.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (rawX >= mDisplayWidth / 2) {
                                    mFloatImage.setImageResource(R.drawable.float_icon_hide_right);
                                    mTouchListener.paramsF.x = mDisplayWidth - mTouchListener.paramsF.width;
                                    LogUtils.d(TAG, "hide to right new x: " + mTouchListener.paramsF.x);
                                } else {
                                    mFloatImage.setImageResource(R.drawable.float_icon_hide_left);
                                    mTouchListener.paramsF.x = 0;
                                    LogUtils.d(TAG, "hide to left new x: " + mTouchListener.paramsF.x);
                                }
                                mFloatImage.setBackgroundResource(0);
                                mWindowManager.updateViewLayout(mFloatDecor, mTouchListener.paramsF);
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {

                            }
                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }
                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });

                        hidingAnm.start();
                    }
                    break;
                case MSG_FLOAT_ICON_DISMISS:
                    createNotification();
                    removeFloatView();
                    break;
                case MSG_FLOAT_ICON_SHOW:
                    NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(ID_NOTIFICATION);
                    addFloatView();
                    break;
            }
        }
    }

    private void initiatePopupWindow(View anchor) {
        try {
            ListPopupWindow popup = new ListPopupWindow(this);
            popup.setAnchorView(anchor);
            popup.setWidth((int) (mDisplayWidth/(1.5)));

            list = new ArrayList();
            for(int i=0 ; i<3 ; ++i) {
                list.add("Bang" + i);
            }

            popup.setAdapter(new CustomAdapter(getApplicationContext(), R.layout.floating_popup_row, list));
            popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View view, int position, long id3) {
                    //Log.w("tag", "package : "+apps.get(position).pname.toString());
                }
            });
            popup.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createNotification(){
        Intent notificationIntent = new Intent(getApplicationContext(), FloatingService.class);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.icon_bigbang).setTicker(getString(R.string.bang_notify_text))
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getString(R.string.bang_notify_title))
                .setContentText(getString(R.string.bang_notify_text))
                .setOngoing(true)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(ID_NOTIFICATION, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtils.d(TAG, "onDestroy");

        removeFloatView();
        removeScreenMonitor();
        removeTransparentView();
    }

}