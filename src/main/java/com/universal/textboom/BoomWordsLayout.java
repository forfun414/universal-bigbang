package com.universal.textboom;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

public class BoomWordsLayout {

    private final static String TAG = "BoomWordsLayout";

    private final int mMaxRowNumber;
    private final int mBoomPageWidth;
    private final int mWordMinWidth;
    private final int mWordBaseWidth;
    private final int mPuncMinWidth;
    private final int mPuncBaseWidth;
    private final TextPaint mWordPaint;
    private final TextPaint mPuncPaint;

    private RangeList<Word> mWords = new RangeList<Word>();
    private ArrayList<Integer> mRowStart = new ArrayList<Integer>();
    private ArrayList<Integer> mRowCount = new ArrayList<Integer>();
    private int[] mIdToRow;
    private int mTouchedIndex;
    private String mOriText;
    public int[] mLastSegment;

    private class RangeList<E> extends ArrayList<E> {
        public void remove(int fromIndex, int toIndex) {
            if (fromIndex < toIndex) {
                removeRange(fromIndex, toIndex);
            }
        }
    }

    public class Word {
        public final String word;
        public final int start;
        public final boolean punc;

        public Word(String w, int s, boolean isPunc) {
            word = w;
            start = s;
            punc = isPunc;
        }
    }

    public BoomWordsLayout(Context context) {
        Resources res = context.getResources();
        final int displayWidth = res.getDisplayMetrics().widthPixels;
        mBoomPageWidth = displayWidth - res.getDimensionPixelSize(R.dimen.page_margin_left)
                - res.getDimensionPixelSize(R.dimen.page_margin_right);
        mMaxRowNumber = displayWidth > 1080 ? 11 : 10;
        //mMaxRowNumber = 1000;
        mWordMinWidth = res.getDimensionPixelSize(R.dimen.word_min_width);
        mWordBaseWidth = res.getDimensionPixelSize(R.dimen.word_base_width);
        mPuncMinWidth = res.getDimensionPixelSize(R.dimen.punc_min_width);
        mPuncBaseWidth = res.getDimensionPixelSize(R.dimen.punc_base_width);
        mWordPaint = ((TextView) View.inflate(context, R.layout.boom_chip_layout, null)
                .findViewById(R.id.word)).getPaint();
        mPuncPaint = ((TextView) View.inflate(context, R.layout.boom_punc_layout, null)
                .findViewById(R.id.punc)).getPaint();
    }


    public int getTouchIndex() {
        return mTouchedIndex;
    }

    public boolean layoutWordsAfterFilter(int[] segment, String text, int touchedIndex) {
        mLastSegment = segment;

        mOriText = text;
        mWords.clear();
        mTouchedIndex = -1;
        int start;
        int end;
        int prev = 0;
        for (int i = 0; i < segment.length; i += 2) {
            start = segment[i];
            end = segment[i + 1] + 1;
            addPuncIntoChips(prev, start);//punc is between last end and now, process first
            String trim = text.substring(start, end).replaceAll("\\p{Z}", " ").trim();
            if (!TextUtils.isEmpty(trim)) {
                if (touchedIndex >= start && touchedIndex < end) {
                    mTouchedIndex = mWords.size();
                }
                mWords.add(new Word(trim, start, false));
            }
            prev = end;
        }
        addPuncIntoChips(prev, text.length());

        final int wordCount = mWords.size();
        if (wordCount > 0) {
            generateLayout();
            final int rowCount = mRowCount.size();
            if (rowCount > mMaxRowNumber) {
                if (mTouchedIndex == -1) {
                    start = 0;
                    end = mRowStart.get(mMaxRowNumber);
                } else {
                    final int row = getRowForIndex(mTouchedIndex);
                    if (row < mMaxRowNumber / 2) {
                        start = 0;
                        end = getRowStart(mMaxRowNumber);
                    } else if (row >= rowCount - mMaxRowNumber / 2) {
                        start = getRowStart(rowCount - mMaxRowNumber);
                        end = wordCount;
                    } else {
                        start = getRowStart(row - mMaxRowNumber / 2);
                        end = getRowStart(row + mMaxRowNumber / 2);
                    }
                }
                mWords.remove(end, wordCount);
                mWords.remove(0, start);
                generateLayout();
            }
            return true;
        }
        return false;
    }

    //this is the segment showing to user, different to the segment pass to layoutWordsAfterFilter, which removed the punc
    public ArrayList<Word> getWords() {
        return mWords;
    }


    private void addPuncIntoChips(int start, int end) {
        for (int i = start; i < end; ++i) {
            char punc = mOriText.charAt(i);
            if (!Character.isWhitespace(punc) && !Character.isSpaceChar(punc)) {
                mWords.add(new Word(String.valueOf(punc), i, true));
            }
        }
    }

    private int measureChip(int index) {
        final Word word = mWords.get(index);
        if (word.punc) {
            return Math.max(mPuncMinWidth, mPuncBaseWidth + (int)mPuncPaint.measureText(word.word));
        } else {
            return Math.max(mWordMinWidth, mWordBaseWidth + (int)mWordPaint.measureText(word.word));
        }
    }

    //layout by width of each word
    private void generateLayout() {
        int count = 0;
        int start = 0;
        int remain = mBoomPageWidth;
        mRowCount.clear();
        mRowStart.clear();
        mIdToRow = new int[mWords.size()];
        for (int i = 0; i < mWords.size(); ++i) {
            final int chipWidth = measureChip(i);
            if (chipWidth > remain) {
                if (count == 0) {
                    mIdToRow[i] = mRowCount.size();
                    mRowCount.add(1);
                    mRowStart.add(i);
                    start = i + 1;
                } else {
                    //change to next line, so need to reenter, --i
                    mRowCount.add(count);
                    mRowStart.add(start);
                    start = i;
                    count = 0;
                    remain = mBoomPageWidth;
                    --i;
                }
            } else {
                ++count;
                remain -= chipWidth;
                mIdToRow[i] = mRowCount.size();
            }
        }
        //last line
        if (count > 0) {
            mRowCount.add(count);
            mRowStart.add(start);
        }
    }

    public int getRowCount() {
        return mRowCount.size();
    }

    public int getRowStart(int row) {
        return mRowStart.get(row);
    }

    public int getColumnCount(int row) {
        return mRowCount.get(row);
    }

    public int getRowForIndex(int index) {
        return mIdToRow[index];
    }

    public boolean isPunc(int index) {
        return mWords.get(index).punc;
    }

    public String getWord(int index) {
        return mWords.get(index).word;
    }

    public int getWordEnd(int index) {
        return mWords.get(index).word.length() + mWords.get(index).start;
    }

    public String getOriText(int start, int end) {
        return mOriText.substring(start, end);
    }

    public String getOriText() {
        return mOriText;
    }

    public int getWordCount() {
        return mWords.size();
    }
}
