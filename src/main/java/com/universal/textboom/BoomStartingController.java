package com.universal.textboom;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.universal.textboom.util.Constant;
import com.universal.textboom.util.LogUtils;
import com.universal.textboom.util.SharedPreferencesManager;

public class BoomStartingController {
    public static String TAG = "BoomStartingController";
    private WindowManager mWindowManager;
    private Context mContext;

    public BoomStartingController(Context context, View decor) {
        mContext = context;
        mBoomDecor = (FrameLayout) decor;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mTextBoom = new TextBoom(mContext);
    }

    public void startBoom(int x, int y, String boomText) {
        mDownX = x;
        mDownY = y;
        int touchIndex = (boomText.length() > 0) ? boomText.length() - 1 : 0;

        LogUtils.d(TAG, "startboom!!: " + boomText + " mDownX " + mDownX + " mDownY " + mDownY);

        if (boomText.length() != 0)
            prepareTextBoom(boomText);
        else
            prepareImageBoom();
    }



    //for booming animation and all preparing staff
    private FrameLayout mBoomDecor;
    private float mDownX = 0;
    private float mDownY = 0;
    private float mDownRawX = 0;
    private float mDownRawY = 0;

    String mBoomString;
    private View mTouchedView;
    boolean mCanImageBoom = false;
    private TextBoom mTextBoom;

    //entrance for text boom
    public void prepareTextBoom(String text) {
        mBoomString = text;
        mTextBoom.boomCreate();
        mTextBoom.prepareTextBoom();
    }

    //entrance for image boom, change from tryImageBoom
    public void prepareImageBoom() {
        mTextBoom.boomCreate();
        LogUtils.d(TAG, "prepareImageBoom 1");

        if (canImageBoom()) {
            LogUtils.d(TAG, "prepareImageBoom " + mCanImageBoom);
            mCanImageBoom = true;
            mTextBoom.prepareImageBoom();
            mTextBoom.startImageBoom();
        }
    }

    private boolean canImageBoom() {
        return mTextBoom.enabled && mTextBoom.ocrEnabled && !isInOcrBlacklist();
    }

    private boolean isInOcrBlacklist() {
        /*
        String[] blackList = getResources().getStringArray(com.android.internal.R.array.ocr_black_list);
        for (String name : blackList) {
            if (name.equals(getPackageName())) {
                return true;
            }
        }
        */
        return false;
    }

    private class TextBoom {
        private static final float LOOP_START_SCALE = 2f;
        private static final float LOOP_END_SCALE = 0.4f;
        private static final float CIRCLE_START_SCALE = 0.4f;
        private static final float CIRCLE_END_SCALE = 15f;
        private static final String BIG_BANG_TEXT_TRIGGER_AREA = "big_bang_text_trigger_area";
        private static final String BIG_BANG_OCR_TRIGGER_AREA = "big_bang_ocr_trigger_area";
        private static final String BIG_BANG_OCR_ENABLE = "big_bang_ocr";

        private ImageView mLoopView;
        private ImageView mCircleView;
        private AnimatorSet mLoopAnimator;
        private FrameLayout mBoomIndictor;

        public boolean enabled = false;
        public boolean ocrEnabled = false;
        public boolean preparing = false;
        private Context mContext;

        public TextBoom(Context ctx) {
            mContext = ctx;
        }

        public void boomCreate() {
            enabled = true;//SharedPreferencesManager.getInt(mContext, Constant.AI_BOOM, 1) == 1;
            ocrEnabled = true;//SharedPreferencesManager.getInt(mContext, BIG_BANG_OCR_ENABLE, 1) == 1;

            if (enabled) {
                addIndicator();
            }
        }

        public void boomDestroy() {
            if (enabled) {
                removeIndicator();
            }
        }

        private ImageView createImageView(int drawableRes) {
            ImageView imageView = new ImageView(mContext);
            imageView.setBackgroundResource(drawableRes);
            final DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            imageView.measure(
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels,
                            View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels,
                            View.MeasureSpec.AT_MOST));
            //imageView.setClipToOutline(false);
            return imageView;
        }

        private void addIndicator() {
            mLoopView = createImageView(R.drawable.boom_loop);//same as smartisan_boom_circle
            mLoopView.setVisibility(View.INVISIBLE);
            mLoopView.setScaleX(LOOP_END_SCALE);
            mLoopView.setScaleY(LOOP_END_SCALE);
            mCircleView = createImageView(R.drawable.smartisan_boom_circle);
            mCircleView.setVisibility(View.INVISIBLE);
            mCircleView.setScaleX(CIRCLE_END_SCALE);
            mCircleView.setScaleY(CIRCLE_END_SCALE);
            mBoomIndictor = new FrameLayout(mContext);
            final FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            flp.gravity = Gravity.TOP | Gravity.LEFT;
            mBoomIndictor.addView(mLoopView, flp);
            mBoomIndictor.addView(mCircleView, flp);
            mBoomIndictor.setBackgroundColor(Color.TRANSPARENT);
            mBoomIndictor.setVisibility(View.GONE);


            mBoomDecor.addView(mBoomIndictor);
        }


        private void removeIndicator() {
            mWindowManager.removeView(mLoopView);
            mWindowManager.removeView(mCircleView);
        }

        public void prepareTextBoom() {
            if (mBoomIndictor == null) return;
            startPreparingTextBoom();
            mBoomIndictor.bringToFront();
            mBoomIndictor.setVisibility(View.VISIBLE);
            mLoopView.bringToFront();
            mLoopView.setTranslationX(mDownX - mLoopView.getMeasuredWidth() / 2);
            mLoopView.setTranslationY(mDownY - mLoopView.getMeasuredHeight() / 2);
            mLoopView.setScaleX(LOOP_END_SCALE);
            mLoopView.setScaleY(LOOP_END_SCALE);
            mLoopView.setVisibility(View.VISIBLE);
            mLoopView.setAlpha(0f);
            startLoopAnimation();
        }

        public void prepareImageBoom() {
            startPreparingTextBoom();
            preparing = true;
        }

        private void startLoopAnimation() {
            if (mLoopAnimator == null) {
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mLoopView, View.ALPHA, 0, 1f);
                alphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (animation.isRunning()) {
                            preparing = true;
                            LogUtils.d(TAG, "startLoopAnimation onAnimationUpdate !!!!!! ");

                            if (mBoomString == null) {
                                LogUtils.w(TAG, "Cancel animation, mTouchedTextView has been reset to null");
                                stopTextBoom();
                                return;
                            }

                            if (TextUtils.isEmpty(mBoomString)) {
                                LogUtils.w(TAG, "Found text is empty for view=" + mTouchedView);
                                stopTextBoom();
                                return;
                            } /*else if (!canGetTextFromWebView()) {
                                LogUtils.d(TAG, "Found text is empty for webview=" + mTouchedView);
                                stopTextBoom();
                                prepareImageBoom();
                                return;
                            }*/
                        }
                    }
                });
                alphaAnimator.setDuration(100);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(mLoopView, View.SCALE_X, LOOP_START_SCALE, LOOP_END_SCALE);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(mLoopView, View.SCALE_Y, LOOP_START_SCALE, LOOP_END_SCALE);
                scaleX.setDuration(300);
                scaleY.setDuration(300);
                mLoopAnimator = new AnimatorSet();
                mLoopAnimator.setStartDelay(100);
                mLoopAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
                mLoopAnimator.playTogether(alphaAnimator, scaleX, scaleY);
                mLoopAnimator.addListener(new Animator.AnimatorListener() {
                    private boolean mIsCanceled = false;

                    @Override
                    public void onAnimationStart(Animator animation) {
                        mIsCanceled = false;
                        LogUtils.d(TAG, "mLoopAnimator onAnimationStart ");
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        LogUtils.d(TAG, "mLoopAnimator onAnimationEnd ");
                        if (mIsCanceled) {
                            return;
                        }
                        mLoopView.setVisibility(View.INVISIBLE);

                        startTextBoom((int) mDownX, (int) mDownY);

                        mCircleView.bringToFront();
                        mCircleView.setVisibility(View.VISIBLE);
                        mCircleView.setTranslationX(mDownX - mCircleView.getMeasuredWidth() / 2);
                        mCircleView.setTranslationY(mDownY - mCircleView.getMeasuredHeight() / 2);
                        startCircleAnimation();
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
            LogUtils.d(TAG, "mLoopAnimator start ");
            mLoopAnimator.start();
        }

        private boolean canGetTextFromWebView() {
            if (mTouchedView == null) return false;
            if (mBoomString == null) return false;
            for (int i = mBoomString.length() - 1; i >= 0; --i) {
                char c = mBoomString.charAt(i);
                if (!Character.isSpaceChar(c) && !Character.isWhitespace(c)) {
                    return true;
                }
            }
            return false;
        }

        private void startCircleAnimation() {
            Animator alpha = ObjectAnimator.ofFloat(mCircleView, View.ALPHA, 1f, 0);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(mCircleView, View.SCALE_X, CIRCLE_START_SCALE, CIRCLE_END_SCALE);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(mCircleView, View.SCALE_Y, CIRCLE_START_SCALE, CIRCLE_END_SCALE);
            AnimatorSet set = new AnimatorSet();
            set.setDuration(300);
            set.setInterpolator(new DecelerateInterpolator(1.5f));
            set.playTogether(alpha, scaleX, scaleY);
            set.start();
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mCircleView.setVisibility(View.INVISIBLE);
                    mCircleView.setScaleX(CIRCLE_START_SCALE);
                    mCircleView.setScaleY(CIRCLE_START_SCALE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }

        public void stopTextBoom() {
            preparing = false;
            if (mLoopView == null) {
                return;
            }
            if (mLoopAnimator != null) {
                mLoopAnimator.cancel();
            }
            if (mLoopView.getVisibility() == View.VISIBLE && mLoopView.getAlpha() > 0) {
                Animator alpha = ObjectAnimator.ofFloat(mLoopView, View.ALPHA, mLoopView.getAlpha(), 0);
                alpha.setDuration(100);
                alpha.setInterpolator(new DecelerateInterpolator(1.5f));
                alpha.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mLoopView.setVisibility(View.INVISIBLE);
                        mLoopView.setScaleX(LOOP_START_SCALE);
                        mLoopView.setScaleY(LOOP_START_SCALE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mLoopView.setAlpha(0f);
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                alpha.start();
            } else {
                mLoopView.setVisibility(View.INVISIBLE);
                mLoopView.setScaleX(LOOP_START_SCALE);
                mLoopView.setScaleY(LOOP_START_SCALE);
            }
        }

        private void startPreparingTextBoom() {
        }

        private void startTextBoom(int x, int y) {
            preparing = false;
            try {
                /*InputMethodManager imm = InputMethodManager.peekInstance();
                if (imm != null && imm.isActive(mTouchedView)) {
                    imm.hideSoftInputFromWindow(mTouchedView.getWindowToken(), 0);
                }*/

                if (mBoomString == null) {
                    LogUtils.w(TAG, "Null text for " + mTouchedView);
                    return;
                }
                int index = (mBoomString.length() > 0) ? mBoomString.length() - 1 : 0;
                Intent intent = new Intent();
                intent.setClassName(Constant.TEXTBOOM_PKG_NAME, Constant.TEXTBOOM_PKG_NAME + ".BoomActivity");
                intent.putExtra(Intent.EXTRA_TEXT, mBoomString);
                intent.putExtra("boom_startx", x);
                intent.putExtra("boom_starty", y);
                intent.putExtra("boom_index", index);
                intent.putExtra("caller_pkg", mContext.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Context context = mContext;
                if (!(context instanceof Activity)) {
                    if (context instanceof ContextWrapper) {
                        context = ((ContextWrapper) context).getBaseContext();
                        if (!(context instanceof Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        }
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    }
                }
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

        private void startImageBoom() {
            mBoomDecor.postDelayed(mImageBoomRunnable, 400);
        }
    }

    private Runnable mImageBoomRunnable = new Runnable() {

        @Override
        public void run() {
            mTextBoom.preparing = false;
            try {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                intent.setClassName(Constant.TEXTBOOM_PKG_NAME, Constant.TEXTBOOM_PKG_NAME + ".BoomOcrActivity");
                Context context = mContext;
                LogUtils.d(TAG, "mImageBoomRunnable.run");
                if (!(context instanceof Activity)) {
                    if (context instanceof ContextWrapper) {
                        context = ((ContextWrapper) context).getBaseContext();
                        if (!(context instanceof Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                }
                if (mCanImageBoom) {
                    intent.putExtra("caller_pkg", context.getPackageName());

                    int x = (int) mDownRawX;
                    int y = (int) mDownRawY;

                    int[] offset = {0, 0};
                    boolean fullScreen = false;
                    /*ViewRootImpl viewRoot = mDecorView.getViewRootImpl();
                    if (viewRoot != null) {
                        viewRoot.getScreenSizeOffset(offset);
                        x -= offset[0];
                        y -= offset[1];
                        fullScreen = WindowManager.LayoutParams.FLAG_FULLSCREEN ==
                                (viewRoot.getWindowFlags() & WindowManager.LayoutParams.FLAG_FULLSCREEN);

                        int flag = getWindow().getAttributes().flags;
                        if ((flag & WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                == WindowManager.LayoutParams.FLAG_FULLSCREEN) {
                            fullScreen = true;
                        }
                    } else {
                        offset[0] = offset[1] = 0;
                    }*/
                    intent.putExtra("boom_offsetx", offset[0]);
                    intent.putExtra("boom_offsety", offset[1]);
                    intent.putExtra("boom_startx", x);
                    intent.putExtra("boom_starty", y);
                    intent.putExtra("boom_fullscreen", fullScreen);
                }
                context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle());
            } catch (ActivityNotFoundException e) {
                LogUtils.e(TAG, "Image boom Error e=" + e);
            }
        }
    };

}
