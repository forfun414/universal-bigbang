package com.universal.textboom;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mobstat.StatService;
import com.edmodo.cropper.CropImageView;
import com.squareup.okhttp.Request;
import com.universal.textboom.floating.FloatingService;
import com.universal.textboom.network.OkHttpClientManager;
import com.universal.textboom.screen.ScreenCaptureController;
import com.universal.textboom.util.Constant;
import com.universal.textboom.util.LogUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PreOcrActivity extends Activity implements MediaProjectionResult {
    private static final String TAG = PreOcrActivity.class.getSimpleName();
    private boolean DEBUG = false;
    private Bitmap mCropImageSrc;

    static boolean sBoomCancel = false;
    private Runnable mOcrRunnable = new Runnable() {
        @Override
        public void run() {
            /*
            if (!sBoomCancel) {
                startOcr();
            }*/
        }
    };

    private FrameLayout mMainFrame;
    private FrameLayout mLoopAnimFrame;
    private ImageView mLoopRotateImage;

    private AnimatorSet mLoopAnimation;
    private AnimatorSet mTouchAnimation;
    private AnimatorSet mCircleAnimation;

    private boolean mAnimating = false;//didn't use now, bz was triggered by capturing
    private boolean mTouchAnimating = false;
    private boolean mLoopAnimating = false;
    private boolean mCircleAnimating = false;
    private boolean mOcrResult = false;

    private static final float LOOP_SCALE_FROM = 1.15f;
    private static final float LOOP_SCALE_TO = 1.1f;
    private static final long SCALE_DURATION = 600;

    private Handler mHandler;
    private static int mStatusBarHeight = 0;
    private float mTouchX;
    private float mTouchY;
    private boolean mFullscreen = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not do ocr in landscape for now
        Configuration cf = getResources().getConfiguration();
        if (cf.orientation == cf.ORIENTATION_LANDSCAPE) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_pre_ocr);
        mHandler = new Handler();

        int statusbarId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusbarId > 0) {
            mStatusBarHeight = getResources().getDimensionPixelSize(statusbarId);
        }

        mTouchX = getIntent().getIntExtra("boom_startx", 0);
        //need remove statusbar heithgt!
        mTouchY = getIntent().getIntExtra("boom_starty", 0) - mStatusBarHeight;
        mFullscreen = getIntent().getBooleanExtra("boom_fullscreen", false);

        animatorInit();
        screenCaptureInit();
        beginScreenShot();
    }

    private void animatorInit() {
        mMainFrame  = (FrameLayout) findViewById(R.id.cropper_frame);
        mLoopAnimFrame = (FrameLayout) findViewById(R.id.anim_loop);
        mLoopAnimFrame.setVisibility(View.INVISIBLE);
        mLoopRotateImage = (ImageView) findViewById(R.id.loop_rotate);
        mLoopAnimFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (!mAnimating) {

                    final int l = left;
                    final int t = top;
                    final int r = right;
                    final int b = bottom;
                    mLoopAnimFrame.post(new Runnable() {
                        @Override
                        public void run() {
                            mLoopAnimFrame.setTranslationX(mTouchX - (r - l) / 2f);
                            mLoopAnimFrame.setTranslationY(mTouchY - (b - t) / 2f);
                            //startTouchBoomAnimation();
                        }
                    });
                }
            }
        });

        //BoomAnimator.makeFadeIn(mMainFrame, BoomAnimator.BOOM_DURATION);
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
                if (!mTouchAnimating) {
                    return;
                }
                mLoopRotateImage.setVisibility(View.VISIBLE);
                mLoopAnimFrame.setAlpha(1f);
                if (!mOcrResult) {
                    //startLoopAnimation();
                }
                mTouchAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
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

    private void startLoopAnimation() {
        if (sBoomCancel && !mOcrStarted || mOcrResult) {
            LogUtils.d(TAG, "startLoopAnimation before return");
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

    private void stopTouchAnimation() {
        if (null != mTouchAnimation) {
            mTouchAnimation.cancel();
            mTouchAnimation = null;
        }
        mTouchAnimating = false;
    }

    private boolean mOcrStarted = false;

    public void cancelOcr() {
        LogUtils.e(TAG, "cancelOcr from touch");
        stopOcr();
    }

    private void stopOcr() {
        LogUtils.e(TAG, "stop ocr");
        stopAllAnimation();

        mAnimating = false;
        mOcrStarted = false;
        finish();
    }


    private void showCropView(Bitmap image) {
        mBottomFrame.setVisibility(View.VISIBLE);
        mCancelButton.setVisibility(View.VISIBLE);
        mCropButton.setVisibility(View.VISIBLE);
        mCropTitle.setVisibility(View.VISIBLE);

        mCropImageSrc = image;
        mCropImageView.setImageBitmap(mCropImageSrc);
        mCropImageView.setVisibility(View.VISIBLE);
    }


    private static final String BOOM_DIR = ".universalboom";
    private static String OCR_IMAGE_DIR = null;
    private static String OCR_IMAGE_PATH = null;

    private int REQUEST_MEDIA_PROJECTION = 3001;
    private ScreenCaptureController mCaptureController;

    private TextView mCropTitle;
    private TextView mRecognizingText;
    private CropImageView mCropImageView;
    private FrameLayout mBottomFrame;
    private Button mCancelButton;
    private Button mCropButton;
    private void screenCaptureInit() {
        mCropTitle = (TextView) findViewById(R.id.crop_title);
        mCropTitle.setVisibility(View.INVISIBLE);
        mCropImageView = (CropImageView) findViewById(R.id.CropImageView);
        mCropImageView.setVisibility(View.INVISIBLE);

        mBottomFrame = (FrameLayout) findViewById(R.id.bottom_frame);
        mBottomFrame.setVisibility(View.INVISIBLE);

        mCancelButton = (Button) findViewById(R.id.button_cancel);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                stopAllAnimation();
                finish();
            }
        });
        mCancelButton.setVisibility(View.INVISIBLE);

        mCropButton = (Button) findViewById(R.id.button_crop);
        mCropButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Bitmap croppedImage = mCropImageView.getCroppedImage();
                saveScreenMap(croppedImage);
                setResult(Activity.RESULT_OK);
                //stopAllAnimation();
                stopTouchAnimation();

                mRecognizingText.setVisibility(View.VISIBLE);
                mLoopAnimFrame.setVisibility(View.VISIBLE);
                mLoopAnimFrame.bringToFront();
                mLoopRotateImage.setVisibility(View.VISIBLE);
                mLoopAnimFrame.setAlpha(1f);
                startLoopAnimation();
                startOcr();
            }
        });
        mCropButton.setVisibility(View.INVISIBLE);

        mRecognizingText = (TextView) findViewById(R.id.ocr_recognizing);
        mRecognizingText.setVisibility(View.INVISIBLE);

        OCR_IMAGE_DIR = getCacheDir().getAbsolutePath() +"/" + BOOM_DIR;
        OCR_IMAGE_PATH = OCR_IMAGE_DIR + "/imageboom.jpg";

        if (mCaptureController == null)
            mCaptureController = new ScreenCaptureController(this, REQUEST_MEDIA_PROJECTION, null, OCR_IMAGE_PATH, this);
    }

    private void beginScreenShot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, R.string.msg_android_too_old, Toast.LENGTH_SHORT).show();
            return;
        }
        mCaptureController.triggerCapture();
    }

    private void startScreenCapture(Intent intent, int resultCode) {
        try {
            mCaptureController.realCapture(intent, resultCode);;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onSuccessMediaProjection(Bitmap map) {
        LogUtils.d(TAG, "onSuccessMediaProjection " + map);
        Bitmap bm = map;//adjustScreenshot(map, 1.2f);
        stopAllAnimation();
        showCropView(bm);
    }

    public void onFailedMediaProjection(int error) {
        LogUtils.d(TAG, "onFailedMediaProjection");
        Toast.makeText(this, R.string.msg_screen_capture_error, Toast.LENGTH_SHORT).show();

        mBottomFrame.setVisibility(View.VISIBLE);
        mCancelButton.setVisibility(View.VISIBLE);
        mCropButton.setVisibility(View.VISIBLE);
        mCropButton.setEnabled(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                //got required permission, can continue to get screen
                startScreenCapture(data, resultCode);
            } else {
                //didn't get allowed from screen capture
                Toast.makeText(this, R.string.msg_screen_capture_error, Toast.LENGTH_SHORT).show();
                LogUtils.d(TAG, "mediaprojection not allowed ");
                return;
            }
        }
    }

    private boolean saveScreenMap(Bitmap screen) {
        if (null == screen) {
            LogUtils.d(TAG, "saveScreenMap screen is null return ");
            return false;
        }

        File dir = new File(OCR_IMAGE_DIR);
        File f = new File(OCR_IMAGE_PATH);
        try {
            if (!dir.exists()) {
                dir.mkdir();
            }
            if (f.exists()) {
                f.delete();
            }
            f.createNewFile();
        } catch (IOException e) {
            screen.recycle();
            e.printStackTrace();
            return false;
        }

        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            screen.recycle();
            return false;
        }
        screen.compress(Bitmap.CompressFormat.JPEG, 80, fOut);
        try {
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            screen.recycle();
        }
        return true;
    }

    private Bitmap adjustScreenshot(Bitmap screenshot, float scale) {
        int w = screenshot.getWidth();
        int h = screenshot.getHeight();

        int top = 0;
        int statusbarId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusbarId > 0) {
            top = getResources().getDimensionPixelSize(statusbarId);
        }
        int bottom = 0;
        int left = 0;
        int right = 0;

        Bitmap bm = Bitmap.createBitmap(screenshot, 0, top, w, h - top);

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() +"/.universalboom/imageboom.1.jpg";
        saveDebugBitmap(bm, path);
        screenshot.recycle();
        //return bm;

        int aw = (int) (bm.getWidth() / scale);
        int ah = (int) (bm.getHeight() / scale);
        Bitmap bm2 = Bitmap.createScaledBitmap(bm, aw, ah, true);
        bm.recycle();

        String path2 = Environment.getExternalStorageDirectory().getAbsolutePath() +"/.universalboom/imageboom.2.jpg";
        saveDebugBitmap(bm2, path2);

        return bm2;
    }


    private void saveDebugBitmap(Bitmap cutBitmap, String path) {
        if (DEBUG == false)
            return;

        File localFile = new File(path);
        String fileName = localFile.getAbsolutePath();
        try {
            if (!localFile.exists()) {
                localFile.createNewFile();
                LogUtils.d(TAG,"image file created");
            }
            FileOutputStream fileOutputStream = new FileOutputStream(localFile);
            if (fileOutputStream != null) {
                cutBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        } catch (IOException e) {
            return;
        }
    }


    //ocr related
    private String mOcrText = null;
    protected void getOrcResult() {
        Map<String, String> params = new HashMap<>(2);
        params.put("apikey", Constant.OCR_KEY);
        params.put("isOverlayRequired", "false");
        //params.put("url", "http://i.imgur.com/fwxooMv.png");
        params.put("language", "chs");//Chinese(Simplified)=chs

        File image = new File(OCR_IMAGE_PATH);

        OkHttpClientManager.getInstance().postImage(Constant.OCR_URL, params,
                new OkHttpClientManager.ResultCallback() {
                    @Override
                    public void onError(Request request, Exception e) {
                        e.printStackTrace();
                        LogUtils.d(TAG, "OkHttpClientManager onError :");
                        Toast.makeText(PreOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                        stopOcr();
                        return;
                    }

                    @Override
                    public void onResponse(String response) {
                        parseOrcResult(response);
                    }
                }, image);
    }


    private void parseOrcResult(String response) {
        if (response != null) {
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
                    handleOcrResult(result.toString());
                } else {
                    LogUtils.d(TAG, "parseOrcResult error :");
                    Toast.makeText(PreOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                    stopOcr();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleOcrResult(String result){
        if (result != null) {

            mOcrText = result.trim();
            if (0 < mOcrText.length()) {
                stopTouchAnimation();
                stopLoopAnimation();
                //todo debug animator
                //startCircleAnimation();
                startBoomActivity();
            } else {
                Toast.makeText(PreOcrActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                stopOcr();
            }
        }
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
        finish();
        mOcrStarted = false;
    }

    private void startOcr() {
        mOcrStarted = true;
        getOrcResult();
        mOcrResult = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //baidu mtj sdk
        //StatService.setDebugOn(true);
        //StatService.onResume((Context) this);
        FloatingService.sendBoomStateBroadcast(this, true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //baidu mtj sdk
        //StatService.onPause((Context) this);
        FloatingService.sendBoomStateBroadcast(this, false);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mCropImageSrc != null) {
            mCropImageSrc.recycle();
            mCropImageSrc = null;
        }

        sBoomCancel = false;
        stopAllAnimation();
        mAnimating = false;
        mOcrStarted = false;
    }

}
