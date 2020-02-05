package com.universal.textboom.util;

public class Constant {

    public static final String ACTION_TEXTBOOM_DICT_UPDATE_COMPLETED = "textboom.intent.action.TEXTBOOM_DICT_UPDATE_COMPLETED";
    public static final String ACTION_TEXTBOOM_DICT_CHECKUP_WAKEUP = "textboom.intent.action.TEXTBOOM_DICT_CHECKUP_WAKEUP";

    public static final int POLLING_CHECKUP_DICT_UPDATE_PERIOD = 3 * 24 * 60 * 60; // unit: seconds

    public static final String DICT_FILES_NAME = "dict_files";
    public static final String DICT_FILE_TEMP_NAME = "dict_temp.zip";

    public static final String PKG_CAMSCANNER = "com.intsig.camscanner";
    public static final String BIG_BANG_DEFAULT_DICT = "big_bang_default_dict";
    public static final String BIG_BANG_OCR = "big_bang_ocr";

    public static final String SEGMENT_URL = "http://bigbang.sanjiaoshou.net/http";
    public static final String OCR_URL = "http://api.ocr.space/parse/image";//?? not work "https://api.ocr.space/parse/image";
    public static final String OCR_KEY = "xxxxxxxxxxxxx";

    public static final String TEXTBOOM_PKG_NAME = "com.universal.textboom";


    public static final class TEXT_BOOM_SEARCH_VALUE {
        public static final int TYPE_BAIDU = 0x000;
        public static final int TYPE_GOOGLE = 0x001;
        public static final int TYPE_BING = 0x002;
        public static final int TYPE_SHENMA = 0x003;
        public static final int TYPE_WIKI = 0x010;
        public static final int TYPE_YOUDAO = 0x100;
        public static final int TYPE_KINGSOFT = 0x101;
        public static final int TYPE_BINGDICT = 0x102;
        public static final int TYPE_HIDICT = 0x103;
    }

    //public static final String TEXT_BOOM = "text_boom";
    public static final String AI_BOOM = "ai_boom";

    /**
     * for detail values, to see {@link TEXT_BOOM_SEARCH_VALUE}
     * @hide
     */
    public static final String TEXT_BOOM_SEARCH_METHOD = "text_boom_search_method";
    /**
     * @hide
     * */
    public static final String BOOM_TEXT_TRIGGER_AREA = "boom_text_trigger_area";
    /**
     *@hide
     */
    public static final int BOOM_TEXT_TRIGGER_AREA_SMALLEST = 0;
    /**
     * @hide
     */
    public static final int BOOM_TEXT_TRIGGER_AREA_SMALL = 1;
    /**
     * @hide
     */
    public static final int BOOM_TEXT_TRIGGER_AREA_MIDDLE = 2;
    /**
     * @hide
     */
    public static final int BOOM_TEXT_TRIGGER_AREA_LARGE = 3;
    /**
     * @hide
     */
    public static final int BOOM_TEXT_TRIGGER_AREA_LARGEST = 4;
}
