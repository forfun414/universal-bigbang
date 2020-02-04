package com.universal.textboom;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.universal.textboom.BoomActivity;
import com.universal.textboom.BoomWordsLayout;
import com.universal.textboom.BoomAnimator;
import com.universal.textboom.SwipeSelectView;
import com.universal.textboom.BoomActionHandler;
import com.universal.textboom.util.LogUtils;

import java.io.Serializable;
import java.util.TreeSet;

public class BoomChipPage {
    
    private final static String TAG = "BoomChipPage";

    final BoomWordsLayout mLayout;
    final Activity mActivity;
    final View mBoomTable;
    final View mMask;
    final CustomScrollView mScroller;
    final View mCancel;
    final View mBoomPage;
    final BoomActionHandler mBoomActionHandler;

    private final SwipeSelectView mBoomConent;

    Serializable mSavedData;

    private int mTouchedX;
    private int mTouchedY;

    OnGlobalLayoutListener mDoBoomAnimation = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(mDoBoomAnimation);
            if (mScroller.canScrollVertically(1)) {
                mMask.setVisibility(View.VISIBLE);
            }
            if (restoreSelectedState()) {
                LogUtils.d(TAG, "Skip boom animation when restoring");
                return;
            }
            if (mTouchedX == -1 || mTouchedY == -1) {
                Log.e(TAG, "WTF, bad touch position passed");
                return;
            }
            float pageX = getChipParentX();
            float pageY = getChipParentY();
            LogUtils.d(TAG, "init Chip and do boom animation");

            final int animationRows = Math.min(mLayout.getRowCount(), 12);
            for (int i = 0; i < animationRows; ++i) {
                LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
                float rowX = row.getX();
                float rowY = row.getY();
                for (int j = 0; j < row.getChildCount(); ++j) {
                    View child = row.getChildAt(j);
                    child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    float newX = mTouchedX - pageX - rowX - child.getMeasuredWidth() / 2;
                    float newY = mTouchedY - pageY - rowY - child.getMeasuredHeight() / 2;
                    float x = child.getX();
                    float y = child.getY();
                    child.setTranslationX(newX - x);
                    child.setTranslationY(newY - y);
                    BoomAnimator.makeBoomAnimation(child);
                }
            }
        }

        private float getChipParentX() {
            return mBoomTable.getX() + mBoomConent.getX();
        }

        private float getChipParentY() {
            return mBoomTable.getY() + mBoomConent.getY();
        }
    };

    public BoomChipPage(Activity activity, View contentView) {
        mActivity = activity;
        mBoomPage = contentView;
        mBoomTable = contentView.findViewById(R.id.boom_table);
        mBoomConent = (SwipeSelectView) contentView.findViewById(R.id.boom_content);
        mMask = contentView.findViewById(R.id.boom_mask);
        mCancel = contentView.findViewById(R.id.mask_cancel);
        mScroller = (CustomScrollView) contentView.findViewById(R.id.boom_scroller);
        mLayout = new BoomWordsLayout(mActivity);
        mBoomConent.setBoomPage(this);
        mBoomTable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleClick()) {
                    mActivity.finish();
                }
            }
        });
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleClick()) {
                    mActivity.finish();
                }
            }
        });
        mBoomActionHandler = new BoomActionHandler(this);
        mScroller.setOnScrollListener(mBoomActionHandler);
    }

    public void chopingReinit(int[] segment, String text, int touchedIndex) {
        if (mLayout.layoutWordsAfterFilter(segment, text, touchedIndex)) {
            initChips();
            return;
        }
        mActivity.finish();
    }

    public boolean initWords(int[] segment, String text, int touchedIndex, int touchedX, int touchedY) {
        if (layoutWords(segment, text, touchedIndex)) {
            mTouchedX = touchedX;
            mTouchedY = touchedY;
            initChips();
            return true;
        }
        return false;
    }

    public boolean layoutWords(int[] segment, String text, int touchedIndex) {
        int puncIndexStart = -1;
        for (int i = 0; i < segment.length; ++i) {
            if (segment[i] == -1) {
                puncIndexStart = i;
                break;
            }
        }
        if (puncIndexStart == -1) return false;
        int[] newSeg = new int[puncIndexStart];
        int wordIndexStart = 0;//the real word  index
        ++puncIndexStart;
        int garbageOffset = 0;
        int touchIndexOffset = 0;
        StringBuilder newText = new StringBuilder();
        for (int i = 0; i < newSeg.length; i += 2) { //only purpose is to get garbageoffset then a new newSeg and newText??
            int curWordStart = segment[i];
            int curPuncStart = puncIndexStart == segment.length ? text.length() : segment[puncIndexStart];
            if (curWordStart < curPuncStart) {
                if (curWordStart > wordIndexStart) {
                    int garbageDiff = curWordStart - wordIndexStart;
                    if (touchedIndex > curWordStart) {
                        touchIndexOffset += garbageDiff;
                    }
                    garbageOffset += garbageDiff;
                } else if (curWordStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with rebuild segment curWordStart=" + curWordStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                newSeg[i] = segment[i] - garbageOffset;
                newSeg[i + 1] = segment[i + 1] - garbageOffset;
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            } else { //curWordStart >= curPuncStart, processing punc, bz punc is before word
                if (curPuncStart > wordIndexStart) { //this means pun is after wordindex, there are garbage?
                    int garbageDiff = curPuncStart - wordIndexStart;
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += garbageDiff;
                    }
                    garbageOffset += garbageDiff;
                } else if (curPuncStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with rebuild segment curPuncStart=" + curPuncStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                //most should be equal, no additonal processing like aboving

                wordIndexStart = segment[puncIndexStart + 1] + 1;
                newText.append(text.substring(segment[puncIndexStart], segment[puncIndexStart + 1] + 1));
                puncIndexStart += 2;
                i -= 2; //bz this is pun, skip back to word
            }
        }

        if (puncIndexStart < segment.length) {
            for (int i = puncIndexStart; i < segment.length; i += 2) {
                int curPuncStart = segment[i];
                if (curPuncStart > wordIndexStart) {
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += curPuncStart - wordIndexStart;
                    }
                } else if (curPuncStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with add ending punc curPuncStart=" + curPuncStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            }
        }

        for (int i = 0; i< segment.length; i++) {
            LogUtils.d(TAG, "firstsegment first old[" + i + "]=" + segment[i]);
        }

        for (int i = 0; i< newSeg.length; i+=2) {
            LogUtils.d(TAG, "firstsegment segment new[" + i + "]=" + newSeg[i] + ":" + newSeg[i+1]);
        }

        return mLayout.layoutWordsAfterFilter(newSeg, newText.toString(), touchedIndex - touchIndexOffset);
    }

    public void resetChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    chip.setSelected(false);
                }
            }
            BoomAnimator.makeMoveAnimation(row, row.getTranslationY(), 0);
        }
    }

    public void moveChipRow(int row, float to) {
        View child = mBoomConent.getChildAt(row);
        BoomAnimator.makeMoveAnimation(child, child.getTranslationY(), to);
    }

    public boolean handleClick() {
        return mBoomActionHandler != null && mBoomActionHandler.handleClick();
    }

    public void invalidateChips() {
        mBoomConent.removeAllViews();
    }

    public SwipeSelectView getContentView() {
        return mBoomConent;
    }

    private void initChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final int start = mLayout.getRowStart(i);
            final int count = mLayout.getColumnCount(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for(int j = 0; j < count; ++j) {
                boolean isPunc = mLayout.isPunc(start + j);
                View chipView = mActivity.getLayoutInflater().inflate(
                        isPunc ? R.layout.boom_punc_layout : R.layout.boom_chip_layout, null);
                BoomChip chip = new BoomChip(start + j, chipView);
                chipView.setTag(chip);
                row.addView(chipView);
            }
            mBoomConent.addView(row);

            LogUtils.d(TAG, "initChips  " + i + " row: " + row);
        }
        mBoomConent.requestLayout();
        mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(mDoBoomAnimation);
    }

    private boolean restoreSelectedState() {
        if (mSavedData instanceof TreeSet) {
            TreeSet<Integer> set = (TreeSet<Integer>) mSavedData;
            if (set.size() > 0) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
                    for (int j = 0; j < row.getChildCount(); ++j) {
                        View child = row.getChildAt(j);
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            if (set.contains(new Integer(chip.index))) {
                                chip.setSelected(true);
                            }
                        }
                    }
                }
                mBoomActionHandler.onSelect(set);
                return true;
            }
        }
        return false;
    }

    public class BoomChip {
        int index;
        TextView word;
        boolean punc;


        public BoomChip(final int id, View chipView) {
            index = id;
            punc = mLayout.isPunc(id);
            if (punc) {
                word = (TextView) chipView.findViewById(R.id.punc);
            } else {
                word = (TextView) chipView.findViewById(R.id.word);
            }
            word.setText(mLayout.getWord(id));
        }

        public void setSelected(boolean selected) {
            word.setShadowLayer(selected ? 1.0f : 0, 0, -3.0f, 0x1f000000);
            word.setSelected(selected);
        }
    }
}
