package com.universal.textboom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.universal.textboom.floating.FloatingService;
import com.universal.textboom.network.OkHttpClientManager;
import com.universal.textboom.util.Constant;
import com.universal.textboom.util.LogUtils;
import com.universal.textboom.util.SharedPreferencesManager;
import com.universal.textboom.util.Utils;
import com.squareup.okhttp.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class BoomActivity extends Activity {

    private final static String TAG = "BoomActivity";
    private final static String SELECTED_STATE = "selected_state";

    private BoomChipPage mBoomChipPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View contentView = getLayoutInflater().inflate(R.layout.boom_activity_layout, null);
        View boom_page = contentView.findViewById(R.id.boom_page);
        boom_page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        BoomAnimator.makeFadeIn(boom_page, BoomAnimator.BOOM_DURATION);
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBoomChipPage.handleClick()) {
                    finish();
                }
            }
        });
        mBoomChipPage = new BoomChipPage(this, contentView);

        setContentView(contentView);
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        lp.width = dm.widthPixels;
        lp.height = dm.heightPixels;
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        LogUtils.d(TAG, "BoomAccService boom!!!");

        boolean aiBoom = SharedPreferencesManager.getInt(BoomActivity.this, Constant.AI_BOOM, 1) == 1;

        if (aiBoom && Utils.isNetworkAvailable(this)) {
            segmentOnline();
        } else {
            segmentLocally();
            /*finish();
            Toast.makeText(getApplicationContext(), R.string.network_disconnected, Toast.LENGTH_SHORT).show();
            */
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    //just segment word one by one, so the result must be [0,0,1,1, ...,n-1,n-1, -1]
    private void segmentLocally() {
        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        int textLength = text.length();
        int[] result = new int[2 * textLength + 1];
        for (int i = 0; i< textLength; i++) {
            result[2*i] = i;
            result[2*i + 1] = i;
        }
        result[2*textLength] = -1;

        handleSegmentResult(text, result);
    }


    private void segmentOnline() {
        final String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) {

            boolean ocr = (getIntent().getStringExtra("boom_image") != null);
            Map<String, String> params = new HashMap<>(2);
            params.put("words", text);
            params.put("filter", ocr ? "1" : "0");
            OkHttpClientManager.getInstance().post(Constant.SEGMENT_URL, params,
                    new OkHttpClientManager.ResultCallback() {
                @Override
                public void onError(Request request, Exception e) {
                    e.printStackTrace();
                    segmentLocally();
                }

                @Override
                public void onResponse(String response) {
                    if (response != null) {
                        try {
                            JSONObject json = new JSONObject(response);
                            int code = json.getInt("code");
                            if (code != 0) {
                                String msg = json.getString("msg");
                                Toast.makeText(BoomActivity.this, msg, Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            JSONArray array = json.getJSONArray("list");

                            if (array != null) {
                                int[] result = new int[array.length()];
                                for (int i = 0; i < array.length(); i++) {
                                    result[i] = array.getInt(i);
                                }
                                handleSegmentResult(text, result);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    private void handleSegmentResult(String text, int[] result) {
        if (result == null) {
            Log.e(TAG, "Segmentation fails for text");
            result = new int[2];
            result[0] = 0;
            result[1] = text.length() - 1;
        }
        int touchIndex = getIntent().getIntExtra("boom_index", -1);
        int touchedX = getIntent().getIntExtra("boom_startx", -1);
        int touchedY = getIntent().getIntExtra("boom_starty", -1);

        if (!mBoomChipPage.initWords(result, text, touchIndex, touchedX, touchedY)) {
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < result.length; ++i) {
                log.append(result[i] + ", ");
            }

            boolean image = (getIntent().getStringExtra("boom_image") != null);
            if (image) {
                Toast.makeText(BoomActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mBoomChipPage != null && mBoomChipPage.mBoomActionHandler.hasSelection()) {
            outState.putSerializable(SELECTED_STATE,
                    mBoomChipPage.mBoomActionHandler.mSelectedId);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (mBoomChipPage != null) {
            mBoomChipPage.mSavedData = savedInstanceState.getSerializable(SELECTED_STATE);
        }
    }

}
