package com.universal.textboom;

import android.graphics.Bitmap;


public interface MediaProjectionResult {
    public void onSuccessMediaProjection(Bitmap map);
    public void onFailedMediaProjection(int error);
}
