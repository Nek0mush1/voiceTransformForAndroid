package com.osfans.trime.core;

public final class Rime {
    private static final Throwable LOAD_ERROR;

    static {
        Throwable error = null;
        try {
            System.loadLibrary("rime_jni");
        } catch (Throwable throwable) {
            error = throwable;
        }
        LOAD_ERROR = error;
    }

    private Rime() {
    }

    public static boolean isNativeAvailable() {
        return LOAD_ERROR == null;
    }

    public static Throwable loadError() {
        return LOAD_ERROR;
    }

    public static void handleRimeMessage(int type, Object[] params) {
        // The Trime JNI bridge posts deploy/status messages here. This app reads
        // Rime state synchronously, so the callback only needs to exist.
    }

    public static native void startupRime(
            String sharedDir,
            String userDir,
            String versionName,
            boolean fullCheck
    );

    public static native void exitRime();

    public static native boolean deployRimeSchemaFile(String schemaFile);

    public static native boolean deployRimeConfigFile(String fileName, String versionKey);

    public static native boolean syncRimeUserData();

    public static native boolean processRimeKey(int keycode, int mask);

    public static native boolean commitRimeComposition();

    public static native void clearRimeComposition();

    public static native CommitProto getRimeCommit();

    public static native ContextProto getRimeContext();

    public static native StatusProto getRimeStatus();

    public static native void setRimeOption(String option, boolean value);

    public static native boolean getRimeOption(String option);

    public static native SchemaItem[] getRimeSchemaList();

    public static native String getCurrentRimeSchema();

    public static native boolean selectRimeSchema(String schemaId);

    public static native boolean simulateRimeKeySequence(String keySequence);

    public static native String getRimeRawInput();

    public static native int getRimeCaretPos();

    public static native void setRimeCaretPos(int caretPos);

    public static native boolean selectRimeCandidate(int index, boolean global);

    public static native boolean deleteRimeCandidate(int index, boolean global);

    public static native boolean changeRimeCandidatePage(boolean backward);

    public static native SchemaItem[] getAvailableRimeSchemaList();

    public static native SchemaItem[] getSelectedRimeSchemaList();

    public static native boolean selectRimeSchemas(String[] schemaIds);

    public static native CandidateItem[] getRimeCandidates(int startIndex, int limit);

    public static native Object[] getRimeBulkCandidates();
}
