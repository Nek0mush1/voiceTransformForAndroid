package com.example.voicetransform;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    public static final String DEFAULT_BACKEND_URL = "http://39.106.51.35:8000";
    public static final String DEFAULT_USER_ID = "local_user";
    public static final String DEFAULT_APP_CONTEXT = "chat";
    public static final String SPEECH_MODE_SYSTEM = "system";
    public static final String SPEECH_MODE_BACKEND = "backend";
    public static final String DEFAULT_SPEECH_MODE = SPEECH_MODE_BACKEND;
    public static final String LANGUAGE_ZH = "zh";
    public static final String LANGUAGE_EN = "en";
    public static final String DEFAULT_LANGUAGE = LANGUAGE_ZH;

    private static final String PREFS_NAME = "voice_transform_settings";
    private static final String KEY_BACKEND_URL = "backend_url";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_APP_CONTEXT = "app_context";
    private static final String KEY_SPEECH_MODE = "speech_mode";
    private static final String KEY_LANGUAGE = "language";

    private AppSettings() {
    }

    public static String getBackendUrl(Context context) {
        return prefs(context).getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
    }

    public static String getUserId(Context context) {
        return prefs(context).getString(KEY_USER_ID, DEFAULT_USER_ID);
    }

    public static String getAppContext(Context context) {
        return prefs(context).getString(KEY_APP_CONTEXT, DEFAULT_APP_CONTEXT);
    }

    public static String getSpeechMode(Context context) {
        return prefs(context).getString(KEY_SPEECH_MODE, DEFAULT_SPEECH_MODE);
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public static boolean isChinese(Context context) {
        return LANGUAGE_ZH.equals(getLanguage(context));
    }

    public static void saveLanguage(Context context, boolean isChinese) {
        prefs(context)
                .edit()
                .putString(KEY_LANGUAGE, isChinese ? LANGUAGE_ZH : LANGUAGE_EN)
                .apply();
    }

    public static void save(Context context, String backendUrl, String userId, String appContext) {
        save(context, backendUrl, userId, appContext, getSpeechMode(context));
    }

    public static void save(Context context, String backendUrl, String userId, String appContext, String speechMode) {
        prefs(context)
                .edit()
                .putString(KEY_BACKEND_URL, emptyToDefault(backendUrl, DEFAULT_BACKEND_URL))
                .putString(KEY_USER_ID, emptyToDefault(userId, DEFAULT_USER_ID))
                .putString(KEY_APP_CONTEXT, emptyToDefault(appContext, DEFAULT_APP_CONTEXT))
                .putString(KEY_SPEECH_MODE, normalizeSpeechMode(speechMode))
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String emptyToDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String normalizeSpeechMode(String value) {
        if (SPEECH_MODE_BACKEND.equals(value)) {
            return SPEECH_MODE_BACKEND;
        }
        return SPEECH_MODE_SYSTEM;
    }
}
