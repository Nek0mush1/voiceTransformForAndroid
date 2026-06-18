package com.example.voicetransform;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    public static final String DEFAULT_BACKEND_URL = "http://10.0.2.2:8000";
    public static final String DEFAULT_USER_ID = "local_user";
    public static final String DEFAULT_APP_CONTEXT = "chat";

    private static final String PREFS_NAME = "voice_transform_settings";
    private static final String KEY_BACKEND_URL = "backend_url";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_APP_CONTEXT = "app_context";

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

    public static void save(Context context, String backendUrl, String userId, String appContext) {
        prefs(context)
                .edit()
                .putString(KEY_BACKEND_URL, emptyToDefault(backendUrl, DEFAULT_BACKEND_URL))
                .putString(KEY_USER_ID, emptyToDefault(userId, DEFAULT_USER_ID))
                .putString(KEY_APP_CONTEXT, emptyToDefault(appContext, DEFAULT_APP_CONTEXT))
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
}
