package com.example.voicetransform.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.text.TextUtils;

import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.core.CommitProto;
import com.osfans.trime.core.ContextProto;
import com.osfans.trime.core.Rime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class RimeCandidateEngine {
    private static final String PREFS_NAME = "voice_transform_rime";
    private static final String KEY_ASSET_VERSION = "asset_version";
    private static final String ASSET_VERSION = "trime-3.3.10-vt-2";
    private static final String ASSET_ROOT = "rime/shared";
    private static final String SCHEMA_ID = "voice_transform_pinyin";
    private static final String VERSION_NAME = "VoiceTransform-Trime-3.3.10";
    private static final int MAX_CANDIDATES = 8;

    private final Context appContext;
    private boolean available;
    private boolean started;
    private String disabledReason = "";

    public RimeCandidateEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized boolean start(boolean asciiMode, boolean asciiPunct) {
        if (started) {
            syncOptions(asciiMode, asciiPunct);
            return available;
        }
        started = true;
        if (!Rime.isNativeAvailable()) {
            disable("native load failed: " + simpleName(Rime.loadError()));
            return false;
        }
        try {
            File sharedDir = new File(appContext.getFilesDir(), "rime_shared");
            File userDir = new File(appContext.getFilesDir(), "rime_user");
            syncAssets(sharedDir, userDir);
            Rime.startupRime(sharedDir.getAbsolutePath(), userDir.getAbsolutePath(), VERSION_NAME, true);
            Rime.selectRimeSchemas(new String[]{SCHEMA_ID});
            Rime.deployRimeConfigFile("default", "config_version");
            Rime.deployRimeSchemaFile(SCHEMA_ID);
            Rime.deployRimeSchemaFile(SCHEMA_ID + ".schema.yaml");
            if (!Rime.selectRimeSchema(SCHEMA_ID)) {
                throw new IllegalStateException("schema not selectable: " + SCHEMA_ID);
            }
            Rime.setRimeOption("zh_simp", true);
            available = true;
            syncOptions(asciiMode, asciiPunct);
            return true;
        } catch (Throwable throwable) {
            disable("startup failed: " + simpleName(throwable));
            return false;
        }
    }

    public synchronized boolean isAvailable() {
        return available;
    }

    public synchronized String disabledReason() {
        return disabledReason;
    }

    public synchronized boolean inputLetter(char letter) {
        if (!available) {
            return false;
        }
        try {
            boolean handled = Rime.simulateRimeKeySequence(String.valueOf(letter));
            if (!handled) {
                disable("Rime did not handle key: " + letter);
            }
            return handled;
        } catch (Throwable throwable) {
            disable("input failed: " + simpleName(throwable));
            return false;
        }
    }

    public synchronized void delete() {
        if (!available) {
            return;
        }
        try {
            Rime.processRimeKey(0xff08, 0);
        } catch (Throwable throwable) {
            disable("delete failed: " + simpleName(throwable));
        }
    }

    public synchronized String commitComposition() {
        if (!available) {
            return "";
        }
        try {
            if (!Rime.commitRimeComposition()) {
                return "";
            }
            return committedText();
        } catch (Throwable throwable) {
            disable("commit failed: " + simpleName(throwable));
            return "";
        }
    }

    public synchronized String selectCandidate(int index) {
        if (!available) {
            return "";
        }
        try {
            if (!Rime.selectRimeCandidate(index, false)) {
                return "";
            }
            return committedText();
        } catch (Throwable throwable) {
            disable("select failed: " + simpleName(throwable));
            return "";
        }
    }

    public synchronized void clearComposition() {
        if (!available) {
            return;
        }
        try {
            Rime.clearRimeComposition();
        } catch (Throwable throwable) {
            disable("clear failed: " + simpleName(throwable));
        }
    }

    public synchronized String rawInput() {
        if (!available) {
            return "";
        }
        try {
            String input = Rime.getRimeRawInput();
            return input == null ? "" : input;
        } catch (Throwable throwable) {
            disable("raw input failed: " + simpleName(throwable));
            return "";
        }
    }

    public synchronized List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        if (!available) {
            return result;
        }
        try {
            CandidateItem[] items = Rime.getRimeCandidates(0, MAX_CANDIDATES);
            if (items != null) {
                for (int index = 0; index < items.length; index++) {
                    CandidateItem item = items[index];
                    if (item != null && !TextUtils.isEmpty(item.text)) {
                        result.add(new Candidate(index, item.text, item.comment));
                    }
                }
            }
        } catch (Throwable throwable) {
            disable("candidate failed: " + simpleName(throwable));
        }
        return result;
    }

    public synchronized boolean hasComposition() {
        return !TextUtils.isEmpty(rawInput());
    }

    public synchronized void syncOptions(boolean asciiMode, boolean asciiPunct) {
        if (!available) {
            return;
        }
        try {
            Rime.setRimeOption("ascii_mode", asciiMode);
            Rime.setRimeOption("ascii_punct", asciiPunct);
            Rime.setRimeOption("zh_simp", true);
        } catch (Throwable throwable) {
            disable("option failed: " + simpleName(throwable));
        }
    }

    private String committedText() {
        CommitProto commit = Rime.getRimeCommit();
        return commit == null || commit.text == null ? "" : commit.text;
    }

    private void syncAssets(File sharedDir, File userDir) throws IOException {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (ASSET_VERSION.equals(prefs.getString(KEY_ASSET_VERSION, "")) && sharedDir.exists()) {
            ensureUserCustomFile(userDir);
            return;
        }
        deleteRecursively(sharedDir);
        deleteRecursively(new File(userDir, "build"));
        sharedDir.mkdirs();
        userDir.mkdirs();
        copyAssetTree(appContext.getAssets(), ASSET_ROOT, sharedDir);
        ensureUserCustomFile(userDir);
        prefs.edit().putString(KEY_ASSET_VERSION, ASSET_VERSION).apply();
    }

    private void ensureUserCustomFile(File userDir) throws IOException {
        if (!userDir.exists() && !userDir.mkdirs()) {
            throw new IOException("Cannot create " + userDir);
        }
        File custom = new File(userDir, "default.custom.yaml");
        if (custom.exists() && fileContains(custom, SCHEMA_ID)) {
            return;
        }
        String content = "patch:\n"
                + "  schema_list:\n"
                + "    - schema: " + SCHEMA_ID + "\n";
        writeUtf8(custom, content);
    }

    private static boolean fileContains(File file, String needle) {
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 8192)];
            int read = input.read(data);
            return read > 0 && new String(data, 0, read, "UTF-8").contains(needle);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File dest) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, dest);
            return;
        }
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Cannot create " + dest);
        }
        for (String child : children) {
            copyAssetTree(assets, assetPath + "/" + child, new File(dest, child));
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void writeUtf8(File file, String content) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes("UTF-8"));
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Cannot delete " + file);
        }
    }

    private void disable(String reason) {
        available = false;
        disabledReason = reason;
    }

    private static String simpleName(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        if (TextUtils.isEmpty(message)) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    public static final class Candidate {
        public final int index;
        public final String text;
        public final String comment;

        Candidate(int index, String text, String comment) {
            this.index = index;
            this.text = text;
            this.comment = comment == null ? "" : comment;
        }
    }
}
