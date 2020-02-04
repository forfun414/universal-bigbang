package com.universal.textboom.util;

import android.content.Context;
import android.content.SharedPreferences;

import static android.content.Context.MODE_PRIVATE;

public class SharedPreferencesManager {

    private static final String SHARED_PREFERENCES_NAME = "boom_config";

    SharedPreferencesManager() {
    }

    public static String getString(Context context, String key, String def) {
         SharedPreferences preferences = context.getSharedPreferences(
            SHARED_PREFERENCES_NAME, MODE_PRIVATE);

         return preferences.getString(key, def);
    }

    public static void setString(Context context, String key, String value) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static int getInt(Context context, String key, int def) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        return preferences.getInt(key, def);
    }

    public static void setInt(Context context, String key, int value) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }


    public static boolean getBoolean(Context context, String key, boolean def) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        return preferences.getBoolean(key, def);
    }

    public static void setBoolean(Context context, String key, boolean value) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public static float getFloat(Context context, String key, float def) {
        SharedPreferences preferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        return preferences.getFloat(key, def);
    }


}
