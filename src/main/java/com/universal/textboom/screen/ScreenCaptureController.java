package com.universal.textboom.screen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.universal.textboom.MediaProjectionResult;
import com.universal.textboom.PreOcrActivity;
import com.universal.textboom.R;
import com.universal.textboom.util.LogUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class ScreenCaptureController {
    private static final String TAG = "ScreenCapture";
    public static final String MESSAGE = "message";
    public static final String FILE_NAME = "file_name";

    public static MediaProjectionManager mMediaProjectionManager = null;
    private WindowManager mWindowManager = null;

    private int mScreenDensity = 0;
    private int mWindowWidth = 0;
    private int mWindowHeight = 0;
    private Rect mRect;
    private String mImagePath;

    private Activity mActivity;
    public static int mRequestCode = -1;

    private MediaProjection mMediaProjection = null;
    private VirtualDisplay mVirtualDisplay = null;
    private ImageReader mImageReader = null;
    private DisplayMetrics mMetrics = null;
    private MediaProjectionResult mResultCallback= null;

    Handler handler = new Handler(Looper.getMainLooper());

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public ScreenCaptureController(PreOcrActivity activity, int requestCode, Rect rect, String imagePath, MediaProjectionResult callback){
        mActivity = activity;
        mRequestCode = requestCode;
        //mGraphicPath = graphicPath;
        mImagePath = imagePath;
        mResultCallback = callback;

        mMediaProjectionManager = (MediaProjectionManager) mActivity.getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mWindowManager = (WindowManager) mActivity.getApplication().getSystemService(Context.WINDOW_SERVICE);


        mWindowWidth = mActivity.getWindowManager().getDefaultDisplay().getWidth();
        mWindowHeight = mActivity.getWindowManager().getDefaultDisplay().getHeight();
        mMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mScreenDensity = mMetrics.densityDpi;

        if (rect != null) {
            mRect = rect;
        } else {
            mRect = new Rect(0, 0, mWindowWidth, mWindowHeight);
        }

        mImageReader = ImageReader.newInstance(mWindowWidth, mWindowHeight, PixelFormat.RGBA_8888, 2); //PixelFormat.RGBA_8888 ImageFormat.RGB_565
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void triggerCapture() {
        mActivity.startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), mRequestCode);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void realCapture(final Intent intent, final int resultCode) {

        try {
            handler.postDelayed(new Runnable() {
                public void run() {
                    getVirtualDisplay(intent, resultCode);
                }
            }, 10);

            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        realCaptureInner(intent, resultCode);
                    } catch (Exception e) {
                        LogUtils.d(TAG, "error during realCaptureInner " + e);
                        sendCaptureResult(false, null);
                    }
                }
            }, 100);
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Error e) {
            e.printStackTrace();
        }
    }

    private void  sendCaptureResult(boolean success, Bitmap map) {
        LogUtils.d(TAG, "screen capture success " + success);
        if (success && map != null)
            mResultCallback.onSuccessMediaProjection(map);
        else {
            mResultCallback.onFailedMediaProjection(-1);
            if (map != null)
                map.recycle();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void getVirtualDisplay(Intent intent, int resultCode) {
        if (mMediaProjection != null) {
            createVirtualDisplay();
        } else {
            setUpMediaProjection(intent, resultCode);
            createVirtualDisplay();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setUpMediaProjection(Intent intent, int resultCode) {
        try {
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, intent);
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.d(TAG, "mMediaProjection defined");
        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createVirtualDisplay() {
        try {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("screenshot-boom",
                    mWindowWidth, mWindowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(), null, null);
            LogUtils.d(TAG, "virtual displayed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void realCaptureInner(final Intent intent, final int resultCode) throws Exception {
        Image image = mImageReader.acquireLatestImage();

        if (image == null) {
            LogUtils.d(TAG, "image = null,restart");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    realCapture(intent, resultCode);
                }
            });
            return;
        }

        //bz there may exist padding, refer http://www.jianshu.com/p/d7eb518195fd
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        LogUtils.d(TAG, "image data captured");

        if (width != mWindowWidth || rowPadding != 0) {
            int[] pixel = new int[width + rowPadding / pixelStride];
            bitmap.getPixels(pixel, 0, width + rowPadding / pixelStride, 0, 0, width + rowPadding / pixelStride, 1);
            int leftPadding = 0;
            int rightPadding = width + rowPadding / pixelStride;
            for (int i = 0; i < pixel.length; i++){
                if (pixel[i] != 0){
                    leftPadding = i;
                    break;
                }
            }
            for (int i = pixel.length - 1 ; i >=0 ; i--){
                if (pixel[i] != 0){
                    rightPadding = i;
                    break;
                }
            }
            width = Math.min(width, mWindowWidth);
            if (rightPadding - leftPadding > width){
                rightPadding = width;
            }
            bitmap = Bitmap.createBitmap(bitmap, leftPadding, 0, rightPadding-leftPadding, height);
        }

        /*
        LogUtils.d(TAG, "bitmap cuted first");
        if (mGraphicPath != null) {
            mRect = new Rect(mGraphicPath.getLeft(), mGraphicPath.getTop(), mGraphicPath.getRight(), mGraphicPath.getBottom());
        }
        if (mRect != null) {
            if (mRect.left < 0)
                mRect.left = 0;
            if (mRect.right < 0)
                mRect.right = 0;
            if (mRect.top < 0)
                mRect.top = 0;
            if (mRect.bottom < 0)
                mRect.bottom = 0;
            int cut_width = Math.abs(mRect.left - mRect.right);
            int cut_height = Math.abs(mRect.top - mRect.bottom);
            if (cut_width > 0 && cut_height > 0) {
                Bitmap cutBitmap = Bitmap.createBitmap(bitmap, mRect.left, mRect.top, cut_width, cut_height);
                LogUtils.d(TAG, "bitmap cuted second");
                if (mGraphicPath != null){
                    // 准备画笔
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    paint.setColor(Color.WHITE);
                    Bitmap temp = Bitmap.createBitmap(cut_width, cut_height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(temp);

                    Path path = new Path();
                    if (mGraphicPath.size() > 1) {
                        path.moveTo((float) ((mGraphicPath.pathX.get(0)-mRect.left)), (float) ((mGraphicPath.pathY.get(0)- mRect.top)));
                        for (int i = 1; i < mGraphicPath.size(); i++) {
                            path.lineTo((float) ((mGraphicPath.pathX.get(i)-mRect.left)), (float) ((mGraphicPath.pathY.get(i)- mRect.top)));
                        }
                    } else {
                        return;
                    }
                    canvas.drawPath(path, paint);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

                    // 关键代码，关于Xfermode和SRC_IN请自行查阅
                    canvas.drawBitmap(cutBitmap, 0 , 0, paint);
                    LogUtils.d(TAG, "bitmap cuted third");
                    saveCutBitmap(temp);
                } else {
                    saveCutBitmap(cutBitmap);
                }
            }
        } else {
            saveCutBitmap(bitmap);
        }
        */
        saveCutBitmap(bitmap);
        //bitmap.recycle();//自由选择是否进行回收 ??
    }

    private void saveCutBitmap(Bitmap cutBitmap) {
        String dir = mActivity.getCacheDir().getAbsolutePath() +"/.universalboom/";
        String path = dir + "imageboom.0.jpg";

        File localFile = new File(path);


        try {
            if (!localFile.exists()) {

                File dirF = new File(dir);

                if (!dirF.exists()) {
                    dirF.mkdir();
                }

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
            sendCaptureResult(false, null);
            return;
        }

        sendCaptureResult(true, cutBitmap);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        LogUtils.d(TAG, "mMediaProjection undefined");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        LogUtils.d(TAG, "virtual display stopped");
    }

    public void onDestroy() {
        stopVirtual();
        tearDownMediaProjection();
    }
}