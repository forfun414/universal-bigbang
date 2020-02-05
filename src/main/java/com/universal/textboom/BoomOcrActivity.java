package com.universal.textboom;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.Manifest;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.intsig.csopen.sdk.CSOcrOpenApiHandler;
import com.intsig.csopen.sdk.CSOcrResult;
import com.intsig.csopen.sdk.CSOpenAPI;
import com.intsig.csopen.sdk.CSOpenApiFactory;
import com.intsig.csopen.sdk.OCRLanguage;
import com.squareup.okhttp.Request;
import com.universal.textboom.network.OkHttpClientManager;
import com.universal.textboom.screen.ScreenCaptureController;
import com.universal.textboom.util.Constant;
import com.universal.textboom.util.LogUtils;

//import android.view.SurfaceControl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by jayce on 16-10-11.
 */
public class BoomOcrActivity extends Activity {

    private final static String TAG = "BoomOcrActivity";
    private Toast mToastStop;

    private Runnable mOcrRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sBoomCancel) {
                startOcr();
            }
        }
    };

    private static BoomOcrActivity sSelf;

    private FrameLayout mLoopAnimFrame;
    private ImageView mLoopRotateImage;
    private FrameLayout mContentFrame;

    private AnimatorSet mLoopAnimation;
    private AnimatorSet mTouchAnimation;
    private AnimatorSet mCircleAnimation;

    private boolean mAnimating = false;
    private boolean mTouchAnimating = false;
    private boolean mLoopAnimating = false;
    private boolean mCircleAnimating = false;

    private boolean mOcrResult = false;

    private static final float LOOP_SCALE_FROM = 1.15f;
    private static final float LOOP_SCALE_TO = 1.1f;
    private static final long SCALE_DURATION = 600;

    private String mOcrText = null;
    private float mTouchX;
    private float mTouchY;
    private boolean mFullscreen = false;

    static boolean sBoomCancel = false;

    private Handler mHandler;
    private String mPackage;
    private int[] mOffset;
    private static int mStatusBarHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "!!!onCreate");
        super.onCreate(savedInstanceState);
        sSelf = this;
        // Do not do ocr in landscape for now
        Configuration cf = getResources().getConfiguration();
        if (cf.orientation == cf.ORIENTATION_LANDSCAPE) {
            finish();
            return;
        }
        if (sBoomCancel) {
            finish();
            return;
        }

        int statusbarId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusbarId > 0) {
            mStatusBarHeight = getResources().getDimensionPixelSize(statusbarId);
        }
        LogUtils.d(TAG, "statusbar height " + mStatusBarHeight);

        mHandler = new Handler();

        setContentView(R.layout.boom_ocr_layout);
        mLoopAnimFrame = (FrameLayout) findViewById(R.id.anim_loop);
        mLoopAnimFrame.setVisibility(View.INVISIBLE);

        mLoopRotateImage = (ImageView) findViewById(R.id.loop_rotate);

        mContentFrame = (FrameLayout) findViewById(R.id.click_layout);
        mContentFrame.requestFocus();
        mContentFrame.setClickable(true);
        mContentFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOcr();
            }
        });

        mTouchX = getIntent().getIntExtra("boom_startx", 0);
        mTouchY = getIntent().getIntExtra("boom_starty", 0) - mStatusBarHeight;//remove statusbar heithgt!
        mFullscreen = getIntent().getBooleanExtra("boom_fullscreen", false);
        mPackage = getIntent().getStringExtra("caller_pkg");
        int offx = getIntent().getIntExtra("boom_offsetx", 0);
        int offy = getIntent().getIntExtra("boom_offsety", 0);
        mOffset = new int[]{offx, offy};
        LogUtils.d(TAG, "touchX:" + mTouchX + ", touchY:" + mTouchY + ", fullscreen:" + mFullscreen);


        mLoopAnimFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                LogUtils.d(TAG, "onLayoutChange left: " + left + ", top:" + top + ", right:" + right + ", bottom " + bottom);

                if (!mAnimating) {
                    LogUtils.d(TAG, "onLayoutChange not mAnimating left: " + left + ", top:" + top + ", right:" + right + ", bottom " + bottom);

                    final int l = left;
                    final int t = top;
                    final int r = right;
                    final int b = bottom;
                    mLoopAnimFrame.post(new Runnable() {
                        @Override
                        public void run() {
                            mLoopAnimFrame.setTranslationX(mTouchX - (r - l) / 2f);
                            mLoopAnimFrame.setTranslationY(mTouchY - (b - t) / 2f);
                            startTouchBoomAnimation();
                        }
                    });
                }
            }
        });
    }

    public static final long OCR_DELAY = 300;

    protected void getOrcResult() {
        Map<String, String> params = new HashMap<>(2);
        params.put("apikey", Constant.OCR_KEY);
        params.put("isOverlayRequired", "false");
        //params.put("url", "http://i.imgur.com/fwxooMv.png");
        params.put("language", "chs");//Chinese(Simplified)=chs

        File image = new File("ss");//OCR_IMAGE_PATH

        OkHttpClientManager.getInstance().postImage(Constant.OCR_URL, params,
                new OkHttpClientManager.ResultCallback() {
                    @Override
                    public void onError(Request request, Exception e) {
                        e.printStackTrace();
                        LogUtils.d(TAG, "OkHttpClientManager onError :");

                        Toast.makeText(BoomOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                        stopOcr();
                        return;
                    }

                    @Override
                    public void onResponse(String response) {
                        LogUtils.d(TAG, "OkHttpClientManager onResponse :" + response);
                        parseOrcResult(response);
                    }
                }, image);
    }

    private void parseOrcResult(String response) {
        if (response != null) {
            LogUtils.d(TAG, "parseOrcResult get :" + response);
            StringBuilder result = new StringBuilder();
            boolean valid = false;
            try {
                JSONObject json = new JSONObject(response);

                JSONArray resultsArray = json.getJSONArray("ParsedResults");
                if (resultsArray != null) {
                    JSONObject oneResult;
                    for (int i = 0; i < resultsArray.length(); i++) {
                        oneResult = (JSONObject) resultsArray.get(i);
                        int exitCode = oneResult.getInt("FileParseExitCode");

                        //result code ref: https://ocr.space/ocrapi#post
                        if (exitCode == 1) {
                            //if need parse textOverlay
                            JSONObject textOverlay = oneResult.getJSONObject("TextOverlay");

                            String text = oneResult.getString("ParsedText");
                            if (text != null) {
                                result.append(text);
                                valid = true;
                            }
                        }

                    }
                }

                //overall result code
                int overallCode = json.getInt("OCRExitCode");
                String msg = json.getString("ErrorMessage");
                if ((overallCode == 1 || overallCode == 2) && valid) {
                    LogUtils.d(TAG, "parseOrcResult :" + result);
                    handleOcrResult(result.toString());
                } else {
                    LogUtils.d(TAG, "parseOrcResult error :" + msg);
                    Toast.makeText(BoomOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                    stopOcr();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleOcrResult(String result){
        if (result != null) {
            LogUtils.d(TAG, "decode:" + result);

            mOcrText = result.trim();
            if (0 < mOcrText.length()) {
                stopTouchAnimation();
                stopLoopAnimation();
                startCircleAnimation();
            } else {
                Toast.makeText(BoomOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                stopOcr();
            }
        }
    }

    private boolean mOcrStarted = false;

    private void startOcr() {
        mOcrStarted = true;
        getOrcResult();
        mOcrResult = false;
    }

    private void startLoopAnimation() {
        if (sBoomCancel && !mOcrStarted || mOcrResult) {
            return;
        }
        ValueAnimator scaleIn = new ValueAnimator().ofFloat(LOOP_SCALE_FROM, LOOP_SCALE_TO);
        scaleIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();
                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleIn.setDuration(SCALE_DURATION);
        scaleIn.setInterpolator(new SineInoutInterpolator());

        ValueAnimator scaleOut = new ValueAnimator().ofFloat(LOOP_SCALE_TO, LOOP_SCALE_FROM);
        scaleOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();
                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleOut.setDuration(SCALE_DURATION);
        scaleOut.setInterpolator(new SineInoutInterpolator());

        AnimatorSet scaleAnimation = new AnimatorSet();
        scaleAnimation.playSequentially(scaleIn, scaleOut);
        scaleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mLoopAnimating && !mOcrResult) {
                    animation.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        ValueAnimator rotateAnimation = new ValueAnimator().ofInt(0, 360);
        mLoopAnimFrame.setPivotX(mLoopAnimFrame.getWidth() / 2f);
        mLoopAnimFrame.setPivotY(mLoopAnimFrame.getHeight() / 2f);
        rotateAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int animatorValue = (Integer) animation.getAnimatedValue();

                mLoopAnimFrame.setRotation(animatorValue);
            }
        });
        rotateAnimation.setDuration(SCALE_DURATION * 2);
        rotateAnimation.setRepeatMode(ValueAnimator.RESTART);
        rotateAnimation.setRepeatCount(ValueAnimator.INFINITE);
        rotateAnimation.setInterpolator(new LinearInterpolator());

        mLoopAnimation = new AnimatorSet();
        mLoopAnimation.playTogether(scaleAnimation, rotateAnimation);
        mLoopAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mLoopAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mLoopAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mLoopAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mLoopAnimation.start();
    }

    private void stopLoopAnimation() {
        if (null != mLoopAnimation) {
            mLoopAnimation.cancel();
            mLoopAnimation = null;
        }
        mLoopAnimating = false;
    }

    private void stopCircleAnimation() {
        if (null != mCircleAnimation) {
            mCircleAnimation.cancel();
            mCircleAnimation = null;
        }
        mCircleAnimating = false;
    }

    private void stopAllAnimation() {
        stopTouchAnimation();
        stopLoopAnimation();
        stopCircleAnimation();
    }

    public void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        sBoomCancel = false;
        super.onDestroy();
        stopAllAnimation();
        if (null != mContentFrame) {
            mContentFrame.removeCallbacks(mOcrRunnable);
        }

        if (null != mToastStop) {
            mToastStop.cancel();
            mToastStop = null;
        }
        mAnimating = false;
        mOcrStarted = false;
        if (null != sSelf && sSelf == this) {
            sSelf = null;
        }
    }

    public static BoomOcrActivity getInstance() {
        return sSelf;
    }

    public static final float TOUCH_SCALE_FROM = 2f;
    public static final float TOUCH_SCALE_TO_1 = 0.2f;
    public static final float TOUCH_SCALE_TO_2 = 1.15f;
    public static final long TOUCH_DELAY = 0;

    public static final long SCALE_1_DURATION = 400;
    public static final long SCALE_2_DURATION = 200;

    public static final float TOUCH_ALPHA_FROM = 0.4f;
    public static final float TOUCH_ALPHA_TO = 1f;

    private void startTouchBoomAnimation() {
        LogUtils.d(TAG, "startTouchBoomAnimation");
        if (sBoomCancel) {
            return;
        }
        mAnimating = true;
        mLoopAnimFrame.setVisibility(View.INVISIBLE);
        mLoopRotateImage.setVisibility(View.INVISIBLE);
        // init scale to TOUCH_SCALE_FROM
        mLoopAnimFrame.setScaleX(TOUCH_SCALE_FROM);
        mLoopAnimFrame.setScaleY(TOUCH_SCALE_FROM);
        mLoopAnimFrame.setAlpha(TOUCH_ALPHA_FROM);

        AnimatorSet setAnim = new AnimatorSet();
        // anim scale from TOUCH_SCALE_FROM to TOUCH_SCALE_TO_1
        ValueAnimator scaleAnimation = new ValueAnimator().ofFloat(TOUCH_SCALE_FROM, TOUCH_SCALE_TO_1);
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });
        scaleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                LogUtils.d(TAG, "touch scale start");
                mTouchAnimating = true;
                mLoopAnimFrame.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        scaleAnimation.setDuration(SCALE_1_DURATION);

        ValueAnimator alphaAnimation = new ValueAnimator().ofFloat(TOUCH_ALPHA_FROM, TOUCH_ALPHA_TO);
        alphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setAlpha(animatorValue);
            }
        });

        alphaAnimation.setDuration(SCALE_1_DURATION);
        setAnim.playTogether(scaleAnimation, alphaAnimation);

        // anim scale from TOUCH_SCALE_TO_1 to TOUCH_SCALE_TO_2
        ValueAnimator scaleAnimation2 = new ValueAnimator().ofFloat(TOUCH_SCALE_TO_1, TOUCH_SCALE_TO_2);
        scaleAnimation2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleAnimation2.setDuration(SCALE_2_DURATION);

        mTouchAnimation = new AnimatorSet();
        mTouchAnimation.playSequentially(setAnim, scaleAnimation2);
        mTouchAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                LogUtils.d(TAG, "touch scale end");
                if (!mTouchAnimating) {
                    return;
                }
                mLoopRotateImage.setVisibility(View.VISIBLE);
                mLoopAnimFrame.setAlpha(1f);
                if (!mOcrResult) {
                    startLoopAnimation();
                }
                mTouchAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                LogUtils.d(TAG, "touch scale cancel");
                mTouchAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mTouchAnimation.setInterpolator(new CubicInInterpolator());
        mTouchAnimation.setStartDelay(TOUCH_DELAY);
        mTouchAnimation.start();

    }

    private void stopTouchAnimation() {
        if (null != mTouchAnimation) {
            mTouchAnimation.cancel();
            mTouchAnimation = null;
        }
        mTouchAnimating = false;
    }

    private static final float CIRCLE_END_SCALE = 4f;
    private static final long CIRCLE_END_DURATION = 100;

    private void startCircleAnimation() {
        mLoopRotateImage.setVisibility(View.INVISIBLE);
        float currentScale = mLoopAnimFrame.getScaleX();

        mCircleAnimation = new AnimatorSet();

        ValueAnimator scaleAnimation = new ValueAnimator().ofFloat(currentScale, CIRCLE_END_SCALE);
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleAnimation.setDuration(CIRCLE_END_DURATION);

        ValueAnimator alphaAnimation = new ValueAnimator().ofFloat(1f, 0f);
        alphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setAlpha(animatorValue);
            }
        });

        alphaAnimation.setDuration(CIRCLE_END_DURATION);
        mCircleAnimation.playTogether(scaleAnimation, alphaAnimation);
        mCircleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mCircleAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCircleAnimating) {
                    return;
                }
                mCircleAnimating = false;
                startBoomActivity();
                finish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCircleAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mCircleAnimation.start();
    }

    private void startBoomActivity() {
        LogUtils.e(TAG, "startBoomActivity");
        Intent intent = new Intent(this, BoomActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, mOcrText);
        intent.putExtra("boom_startx", (int) mTouchX);
        intent.putExtra("boom_starty", (int) mTouchY);
        intent.putExtra("boom_index", 0);
        intent.putExtra("boom_image", "image");
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        mOcrStarted = false;
    }

    public void post(Runnable run) {
        if (null != mHandler && null != run) {
            mHandler.post(run);
        }
    }

    public void cancelOcr() {
        LogUtils.e(TAG, "cancelOcr from touch");
        stopOcr();
    }

    private void stopOcr() {
        LogUtils.e(TAG, "stop ocr");
        stopAllAnimation();
        if (null != mContentFrame) {
            mContentFrame.removeCallbacks(mOcrRunnable);
        }

        if (null != mToastStop) {
            mToastStop.cancel();
            mToastStop = null;
        }
        mAnimating = false;
        mOcrStarted = false;
        finish();
    }

    private int REQUEST_CROP_IMAGE = 3002;
    public void onImageCropped() {
        mOcrResult = true;
        mContentFrame.removeCallbacks(mOcrRunnable);
        mContentFrame.postDelayed(mOcrRunnable, OCR_DELAY);
    }


    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        stopOcr();
    }

}
