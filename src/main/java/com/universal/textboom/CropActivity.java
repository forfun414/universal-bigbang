package com.universal.textboom;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import android.view.Window;

import com.edmodo.cropper.CropImageView;
import com.universal.textboom.util.LogUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CropActivity extends Activity {
    private static final int GUIDELINES_ON_TOUCH = 1;
    private static final String TAG = CropActivity.class.getSimpleName();
    private String mImagePath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_crop);
        LogUtils.d("TestActivity ocrr", "oncreate ");

        mImagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.universalboom/imageboom.jpg";
        Bitmap screen = BitmapFactory.decodeFile(mImagePath);

        final CropImageView cropImageView = (CropImageView) findViewById(R.id.CropImageView);
        cropImageView.setImageBitmap(screen);

        final Button cancelButton = (Button) findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        final Button cropButton = (Button) findViewById(R.id.button_crop);
        cropButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Bitmap croppedImage = cropImageView.getCroppedImage();
                saveScreenMap(croppedImage);
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    private boolean saveScreenMap(Bitmap screen) {
        if (null == screen) {
            LogUtils.d(TAG, "saveScreenMap screen is null return ");
            return false;
        }

        File f = new File(mImagePath);

        try {
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

}
