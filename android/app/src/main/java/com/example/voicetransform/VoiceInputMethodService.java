package com.example.voicetransform;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.voicetransform.api.CorrectionApiClient;
import com.example.voicetransform.ime.RimeCandidateEngine;
import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoiceInputMethodService extends InputMethodService {
    private static final long MAX_RECORDING_MS = 60000;
    private static final long DELETE_REPEAT_INITIAL_DELAY_MS = 350;
    private static final long DELETE_REPEAT_INTERVAL_MS = 60;
    private static final long CAPS_LOCK_DOUBLE_TAP_MS = 450;
    private static final String CANDIDATE_PREFS_NAME = "voice_transform_pinyin_learning";
    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_ASSET_CANDIDATES_PER_PINYIN = 8;
    private static final int MAX_ASSET_TERM_LENGTH = 8;
    private static final int THUOCL_FREQUENCY_BOOST = 6000;
    private static final String LUNA_PINYIN_ASSET = "ime/luna_pinyin.dict.yaml";
    private static final String THUOCL_IT_ASSET = "ime/THUOCL_IT.txt";
    private static final String[][] ZH_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"shift", "z", "x", "c", "v", "b", "n", "m", "delete"},
            {"symbols", "space", "enter"}
    };
    private static final String[][] EN_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"shift", "z", "x", "c", "v", "b", "n", "m", "delete"},
            {"symbols", "space", "enter"}
    };
    private static final String[][] SYMBOL_ROWS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {",", ".", "?", "!", ":", ";", "@", "#", "%"},
            {"-", "_", "'", "\"", "(", ")", "/", "delete"},
            {"symbol_toggle", "letters", "space", "enter"}
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTimeoutRunnable = this::stopRecordingAndUpload;
    private final Runnable deleteRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            deleteBackward();
            mainHandler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS);
        }
    };

    private TextView statusText;
    private TextView rawTextView;
    private TextView correctedTextView;
    private TextView correctionMethodView;
    private View resultPanel;
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidateBar;
    private LinearLayout keyboardContainer;
    private Button deleteButton;
    private Button spaceButton;
    private Button zhModeButton;
    private Button enModeButton;
    private Button symbolModeButton;
    private Button insertCorrectedButton;
    private Button insertRawButton;
    private Button cancelButton;
    private Button chineseButton;
    private Button englishButton;
    private Button rawToggleButton;
    private MediaRecorder mediaRecorder;
    private SpeechRecognizer speechRecognizer;
    private File recordingFile;
    private boolean isChinese = true;
    private boolean useChineseSymbols = AppSettings.DEFAULT_CHINESE_SYMBOLS;
    private boolean isShiftEnabled;
    private boolean isCapsLock;
    private boolean isRawExpanded;
    private boolean isDeleteRepeating;
    private boolean isRecording;
    private boolean isListening;
    private boolean isCorrecting;
    private boolean isVoiceHoldActive;
    private boolean dictionariesLoaded;
    private long lastShiftTapMs;
    private String keyboardInputMode = AppSettings.DEFAULT_KEYBOARD_MODE;
    private String previousTextKeyboardMode = AppSettings.DEFAULT_KEYBOARD_MODE;
    private final StringBuilder pinyinBuffer = new StringBuilder();
    private final List<Candidate> currentCandidates = new ArrayList<>();
    private final Map<String, List<Candidate>> pinyinDictionary = createBasePinyinDictionary();
    private RimeCandidateEngine rimeCandidateEngine;
    private boolean isRimeStarting;
    private boolean didReportRimeFallback;
    private String pendingRawText = "";
    private String pendingCorrectedText = "";

    @Override
    public View onCreateInputView() {
        View view = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null);
        statusText = view.findViewById(R.id.imeStatusText);
        rawTextView = view.findViewById(R.id.imeRawText);
        correctedTextView = view.findViewById(R.id.imeCorrectedText);
        correctionMethodView = view.findViewById(R.id.imeCorrectionMethod);
        resultPanel = view.findViewById(R.id.imeResultPanel);
        candidateScroll = view.findViewById(R.id.imeCandidateScroll);
        candidateBar = view.findViewById(R.id.imeCandidateBar);
        keyboardContainer = view.findViewById(R.id.imeKeyboardContainer);
        zhModeButton = view.findViewById(R.id.imeZhModeButton);
        enModeButton = view.findViewById(R.id.imeEnModeButton);
        symbolModeButton = view.findViewById(R.id.imeSymbolModeButton);
        insertCorrectedButton = view.findViewById(R.id.imeInsertCorrectedButton);
        insertRawButton = view.findViewById(R.id.imeInsertRawButton);
        cancelButton = view.findViewById(R.id.imeCancelButton);
        chineseButton = view.findViewById(R.id.imeChineseButton);
        englishButton = view.findViewById(R.id.imeEnglishButton);
        rawToggleButton = view.findViewById(R.id.imeRawToggleButton);

        zhModeButton.setOnClickListener(v -> setKeyboardInputMode(AppSettings.KEYBOARD_MODE_ZH));
        enModeButton.setOnClickListener(v -> setKeyboardInputMode(AppSettings.KEYBOARD_MODE_EN));
        symbolModeButton.setOnClickListener(v -> setKeyboardInputMode(AppSettings.KEYBOARD_MODE_SYMBOLS));
        insertCorrectedButton.setOnClickListener(v -> insertPendingText(true));
        insertRawButton.setOnClickListener(v -> insertPendingText(false));
        cancelButton.setOnClickListener(v -> clearPendingResult(text("\u5df2\u53d6\u6d88", "Canceled")));
        chineseButton.setOnClickListener(v -> {
            setLanguage(true);
        });
        englishButton.setOnClickListener(v -> {
            setLanguage(false);
        });
        rawToggleButton.setOnClickListener(v -> toggleRawExpansion());

        isChinese = AppSettings.isChinese(this);
        useChineseSymbols = AppSettings.isChineseSymbols(this);
        loadLocalDictionaries();
        rimeCandidateEngine = new RimeCandidateEngine(this);
        keyboardInputMode = AppSettings.getKeyboardInputMode(this);
        if (!AppSettings.KEYBOARD_MODE_SYMBOLS.equals(keyboardInputMode)) {
            previousTextKeyboardMode = keyboardInputMode;
        }
        startRimeCandidateEngine();
        renderKeyboard();
        updateCandidates();
        applyLanguage();
        clearPendingResult(null);
        setStatus(text("\u5c31\u7eea", "Ready"));
        return view;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        cancelRecording();
        cancelSpeechRecognition();
        clearPendingResult(null);
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        stopDeleteRepeat();
        cancelRecording();
        cancelSpeechRecognition();
    }

    @Override
    public void onDestroy() {
        stopDeleteRepeat();
        cancelRecording();
        destroySpeechRecognizer();
        super.onDestroy();
    }

    private void renderKeyboard() {
        if (keyboardContainer == null) {
            return;
        }
        keyboardContainer.removeAllViews();
        String[][] rows = rowsForCurrentMode();
        for (String[] row : rows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46)
            );
            if (keyboardContainer.getChildCount() > 0) {
                rowParams.topMargin = dp(5);
            }
            keyboardContainer.addView(rowLayout, rowParams);

            for (String key : row) {
                Button keyButton = makeKeyboardButton(key);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        keyWeight(key)
                );
                if (rowLayout.getChildCount() > 0) {
                    keyParams.leftMargin = dp(5);
                }
                rowLayout.addView(keyButton, keyParams);
            }
        }
        updateModeButtons();
    }

    private String[][] rowsForCurrentMode() {
        if (AppSettings.KEYBOARD_MODE_SYMBOLS.equals(keyboardInputMode)) {
            return SYMBOL_ROWS;
        }
        if (AppSettings.KEYBOARD_MODE_EN.equals(keyboardInputMode)) {
            return EN_ROWS;
        }
        return ZH_ROWS;
    }

    private Button makeKeyboardButton(String key) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(2), 0, dp(2), dp(1));
        button.setTextSize(isLetterKey(key) ? 18 : 14);
        button.setTextColor(getResources().getColor(R.color.ime_text_primary));
        button.setBackgroundResource(R.drawable.ime_key_background);
        button.setGravity(Gravity.CENTER);
        button.setText(keyLabel(key));
        button.setOnClickListener(v -> handleKeyPress(key));
        if ("delete".equals(key)) {
            bindDeleteRepeater(button);
        } else if ("space".equals(key)) {
            bindSpaceVoiceShortcut(button);
        } else if ("shift".equals(key)) {
            button.setSelected(isShiftEnabled || isCapsLock);
        } else if ("symbol_toggle".equals(key)) {
            button.setSelected(useChineseSymbols);
        }
        return button;
    }

    private void bindDeleteRepeater(Button button) {
        deleteButton = button;
        button.setOnClickListener(v -> {
            if (!isDeleteRepeating) {
                handleDeletePress();
            }
        });
        button.setOnLongClickListener(v -> {
            startDeleteRepeat();
            return true;
        });
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopDeleteRepeat();
            }
            return false;
        });
    }

    private void bindSpaceVoiceShortcut(Button button) {
        spaceButton = button;
        button.setOnClickListener(v -> handleSpacePress());
        button.setOnLongClickListener(v -> {
            isVoiceHoldActive = true;
            startVoiceInput();
            return true;
        });
        button.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL)
                    && isVoiceHoldActive) {
                isVoiceHoldActive = false;
                stopVoiceInputFromHold();
                return true;
            }
            return false;
        });
    }

    private float keyWeight(String key) {
        if ("space".equals(key)) {
            return 4.5f;
        }
        if ("shift".equals(key) || "delete".equals(key) || "symbols".equals(key)
                || "letters".equals(key) || "symbol_toggle".equals(key) || "enter".equals(key)) {
            return 1.35f;
        }
        return 1f;
    }

    private String keyLabel(String key) {
        if ("shift".equals(key)) {
            if (AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode)) {
                return "\u4e2d/EN";
            }
            return isCapsLock ? "\u21ea" : "\u21e7";
        }
        if ("delete".equals(key)) {
            return "\u232b";
        }
        if ("symbols".equals(key)) {
            return "123";
        }
        if ("letters".equals(key)) {
            return AppSettings.KEYBOARD_MODE_ZH.equals(previousTextKeyboardMode) ? "\u62fc\u97f3" : "ABC";
        }
        if ("symbol_toggle".equals(key)) {
            return useChineseSymbols ? text("\u4e2d\u7b26", "CN.") : text("\u82f1\u7b26", "EN.");
        }
        if ("space".equals(key)) {
            if (isRecording || isListening) {
                return isVoiceHoldActive ? text("\u677e\u624b\u7ed3\u675f", "Release") : text("\u505c\u6b62", "Stop");
            }
            if (AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode) && hasActiveComposition()) {
                return firstCandidateTextOrBuffer();
            }
            return AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode) ? "\u7a7a\u683c / \u6309\u4f4f\u8bed\u97f3" : "Space / hold voice";
        }
        if ("enter".equals(key)) {
            return "\u23ce";
        }
        if (isLetterKey(key) && AppSettings.KEYBOARD_MODE_EN.equals(keyboardInputMode)
                && (isShiftEnabled || isCapsLock)) {
            return key.toUpperCase();
        }
        return symbolLabel(key);
    }

    private String symbolLabel(String key) {
        if (!AppSettings.KEYBOARD_MODE_SYMBOLS.equals(keyboardInputMode)) {
            return key;
        }
        if (useChineseSymbols) {
            if (",".equals(key)) {
                return "\uff0c";
            }
            if (".".equals(key)) {
                return "\u3002";
            }
            if ("?".equals(key)) {
                return "\uff1f";
            }
            if ("!".equals(key)) {
                return "\uff01";
            }
            if (":".equals(key)) {
                return "\uff1a";
            }
            if (";".equals(key)) {
                return "\uff1b";
            }
            if ("(".equals(key)) {
                return "\uff08";
            }
            if (")".equals(key)) {
                return "\uff09";
            }
        }
        return key;
    }

    private boolean isLetterKey(String key) {
        return key.length() == 1 && key.charAt(0) >= 'a' && key.charAt(0) <= 'z';
    }

    private void handleKeyPress(String key) {
        if (isLetterKey(key)) {
            handleLetterPress(key.charAt(0));
            return;
        }
        if ("shift".equals(key)) {
            handleShiftPress();
            return;
        }
        if ("delete".equals(key)) {
            handleDeletePress();
            return;
        }
        if ("space".equals(key)) {
            handleSpacePress();
            return;
        }
        if ("enter".equals(key)) {
            handleEnterPress();
            return;
        }
        if ("symbols".equals(key)) {
            setKeyboardInputMode(AppSettings.KEYBOARD_MODE_SYMBOLS);
            return;
        }
        if ("letters".equals(key)) {
            setKeyboardInputMode(previousTextKeyboardMode);
            return;
        }
        if ("symbol_toggle".equals(key)) {
            toggleSymbolStyle();
            return;
        }
        commitText(symbolLabel(key));
    }

    private void handleLetterPress(char letter) {
        clearPendingResult(null);
        if (AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode)) {
            if (isRimeActive()) {
                if (!rimeCandidateEngine.inputLetter(letter)) {
                    pinyinBuffer.append(letter);
                }
                updateCandidates();
            } else {
                pinyinBuffer.append(letter);
                updateCandidates();
            }
            return;
        }

        String value = String.valueOf(letter);
        if (AppSettings.KEYBOARD_MODE_EN.equals(keyboardInputMode) && (isShiftEnabled || isCapsLock)) {
            value = value.toUpperCase();
        }
        commitText(value);
        if (isShiftEnabled && !isCapsLock) {
            isShiftEnabled = false;
            renderKeyboard();
        }
    }

    private void handleShiftPress() {
        if (AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode)) {
            setKeyboardInputMode(AppSettings.KEYBOARD_MODE_EN);
            return;
        }
        if (AppSettings.KEYBOARD_MODE_SYMBOLS.equals(keyboardInputMode)) {
            setKeyboardInputMode(previousTextKeyboardMode);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastShiftTapMs <= CAPS_LOCK_DOUBLE_TAP_MS) {
            isCapsLock = !isCapsLock;
            isShiftEnabled = isCapsLock;
        } else if (isCapsLock) {
            isCapsLock = false;
            isShiftEnabled = false;
        } else {
            isShiftEnabled = !isShiftEnabled;
        }
        lastShiftTapMs = now;
        renderKeyboard();
    }

    private void handleDeletePress() {
        if (isRimeActive() && rimeCandidateEngine.hasComposition()) {
            rimeCandidateEngine.delete();
            updateCandidates();
            return;
        }
        if (!isRimeActive() && pinyinBuffer.length() > 0) {
            pinyinBuffer.deleteCharAt(pinyinBuffer.length() - 1);
            updateCandidates();
            return;
        }
        deleteBackward();
    }

    private void handleSpacePress() {
        if (AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode)) {
            if (isRimeActive() && rimeCandidateEngine.hasComposition()) {
                String rawInput = rimeCandidateEngine.rawInput();
                commitText(emptyToDefault(rimeCandidateEngine.commitComposition(), rawInput));
                clearPinyinBuffer();
                return;
            }
            if (!isRimeActive() && pinyinBuffer.length() > 0) {
                commitCandidate(currentCandidates.isEmpty()
                        ? new Candidate(pinyinBuffer.toString(), pinyinBuffer.toString(), 0)
                        : currentCandidates.get(0));
                return;
            }
        }
        if (isRimeActive() && AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode)) {
            rimeCandidateEngine.syncOptions(false, !useChineseSymbols);
        }
        commitText(" ");
    }

    private void handleEnterPress() {
        if (isRimeActive() && rimeCandidateEngine.hasComposition()) {
            String rawInput = rimeCandidateEngine.rawInput();
            String committed = rimeCandidateEngine.commitComposition();
            if (TextUtils.isEmpty(committed)) {
                committed = rawInput;
            }
            commitText(committed);
            clearPinyinBuffer();
            return;
        }
        if (!isRimeActive() && pinyinBuffer.length() > 0) {
            commitText(pinyinBuffer.toString());
            clearPinyinBuffer();
            return;
        }
        sendEnter();
    }

    private void setKeyboardInputMode(String mode) {
        String normalizedMode = normalizeKeyboardMode(mode);
        if (!AppSettings.KEYBOARD_MODE_SYMBOLS.equals(normalizedMode)) {
            previousTextKeyboardMode = normalizedMode;
        }
        if (!normalizedMode.equals(keyboardInputMode)) {
            if (hasActiveComposition()) {
                commitText(currentRawInput());
                clearPinyinBuffer();
            }
            keyboardInputMode = normalizedMode;
            AppSettings.saveKeyboardInputMode(this, keyboardInputMode);
        }
        syncRimeOptions();
        renderKeyboard();
        updateCandidates();
    }

    private String normalizeKeyboardMode(String mode) {
        if (AppSettings.KEYBOARD_MODE_EN.equals(mode)) {
            return AppSettings.KEYBOARD_MODE_EN;
        }
        if (AppSettings.KEYBOARD_MODE_SYMBOLS.equals(mode)) {
            return AppSettings.KEYBOARD_MODE_SYMBOLS;
        }
        return AppSettings.KEYBOARD_MODE_ZH;
    }

    private void updateModeButtons() {
        if (zhModeButton != null) {
            zhModeButton.setSelected(AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode));
        }
        if (enModeButton != null) {
            enModeButton.setSelected(AppSettings.KEYBOARD_MODE_EN.equals(keyboardInputMode));
        }
        if (symbolModeButton != null) {
            symbolModeButton.setSelected(AppSettings.KEYBOARD_MODE_SYMBOLS.equals(keyboardInputMode));
        }
    }

    private void updateCandidates() {
        currentCandidates.clear();
        if (candidateBar == null) {
            return;
        }
        candidateBar.removeAllViews();

        String rawInput = currentRawInput();
        if (!AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode) || TextUtils.isEmpty(rawInput)) {
            if (candidateScroll != null) {
                candidateScroll.setVisibility(View.GONE);
            }
            updateSpaceKeyLabel();
            return;
        }

        if (candidateScroll != null) {
            candidateScroll.setVisibility(View.VISIBLE);
        }
        if (isRimeActive()) {
            for (RimeCandidateEngine.Candidate candidate : rimeCandidateEngine.candidates()) {
                currentCandidates.add(new Candidate(
                        rawInput,
                        candidate.text,
                        0,
                        candidate.index,
                        candidate.comment
                ));
            }
        } else {
            currentCandidates.addAll(findCandidates(rawInput));
        }

        addCandidateButton(rawInput, rawInput, false);
        for (Candidate candidate : currentCandidates) {
            addCandidateButton(candidate.text, candidate.pinyin, true, candidate.rimeIndex);
        }
        updateSpaceKeyLabel();
    }

    private void addCandidateButton(String label, String pinyin, boolean learnOnCommit) {
        addCandidateButton(label, pinyin, learnOnCommit, -1);
    }

    private void addCandidateButton(String label, String pinyin, boolean learnOnCommit, int rimeIndex) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(getResources().getColor(R.color.ime_text_primary));
        button.setBackgroundResource(R.drawable.ime_key_background);
        button.setOnClickListener(v -> {
            if (isRimeActive() && rimeIndex >= 0) {
                commitText(emptyToDefault(rimeCandidateEngine.selectCandidate(rimeIndex), label));
                clearPinyinBuffer();
                return;
            }
            if (isRimeActive() && !learnOnCommit) {
                rimeCandidateEngine.clearComposition();
                commitText(label);
                clearPinyinBuffer();
                return;
            }
            if (learnOnCommit) {
                commitCandidate(new Candidate(pinyin, label, 0));
            } else {
                commitText(label);
                clearPinyinBuffer();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        if (candidateBar.getChildCount() > 0) {
            params.leftMargin = dp(6);
        }
        candidateBar.addView(button, params);
    }

    private List<Candidate> findCandidates(String pinyin) {
        List<Candidate> candidates = new ArrayList<>();
        List<Candidate> directCandidates = pinyinDictionary.get(pinyin);
        if (directCandidates != null) {
            candidates.addAll(directCandidates);
        }

        Candidate sentenceCandidate = buildSentenceCandidate(pinyin);
        if (sentenceCandidate != null) {
            candidates.add(sentenceCandidate);
        }

        Collections.sort(candidates, (left, right) -> {
            int scoreCompare = Integer.compare(candidateScore(right), candidateScore(left));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Integer.compare(left.text.length(), right.text.length());
        });

        LinkedHashMap<String, Candidate> uniqueCandidates = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            uniqueCandidates.put(candidate.text, candidate);
            if (uniqueCandidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return new ArrayList<>(uniqueCandidates.values());
    }

    private Candidate buildSentenceCandidate(String pinyin) {
        int index = 0;
        StringBuilder text = new StringBuilder();
        int frequency = 0;
        while (index < pinyin.length()) {
            Candidate best = null;
            String bestKey = null;
            int maxEnd = Math.min(pinyin.length(), index + MAX_ASSET_TERM_LENGTH * 6);
            for (int end = index + 1; end <= maxEnd; end++) {
                String key = pinyin.substring(index, end);
                List<Candidate> candidates = pinyinDictionary.get(key);
                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }
                Candidate candidate = candidates.get(0);
                if (best == null || key.length() > bestKey.length()) {
                    best = candidate;
                    bestKey = key;
                }
            }
            if (best == null || bestKey == null) {
                return null;
            }
            text.append(best.text);
            frequency += best.frequency;
            index += bestKey.length();
        }
        if (text.length() == 0 || text.toString().equals(pinyin)) {
            return null;
        }
        return new Candidate(pinyin, text.toString(), Math.max(1, frequency / 2));
    }

    private int candidateScore(Candidate candidate) {
        SharedPreferences prefs = getSharedPreferences(CANDIDATE_PREFS_NAME, MODE_PRIVATE);
        return candidate.frequency + prefs.getInt(candidate.pinyin + "|" + candidate.text, 0) * 1000;
    }

    private void commitCandidate(Candidate candidate) {
        commitText(candidate.text);
        if (!TextUtils.isEmpty(candidate.pinyin) && !candidate.text.equals(candidate.pinyin)) {
            String key = candidate.pinyin + "|" + candidate.text;
            SharedPreferences prefs = getSharedPreferences(CANDIDATE_PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        }
        clearPinyinBuffer();
    }

    private void clearPinyinBuffer() {
        if (isRimeActive()) {
            rimeCandidateEngine.clearComposition();
        }
        pinyinBuffer.setLength(0);
        updateCandidates();
    }

    private String firstCandidateTextOrBuffer() {
        if (!currentCandidates.isEmpty()) {
            return currentCandidates.get(0).text;
        }
        return currentRawInput();
    }

    private void updateSpaceKeyLabel() {
        if (spaceButton != null) {
            spaceButton.setText(keyLabel("space"));
        }
    }

    private void startRimeCandidateEngine() {
        if (rimeCandidateEngine == null) {
            return;
        }
        isRimeStarting = true;
        new Thread(() -> {
            boolean enabled = rimeCandidateEngine.start(
                    !AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode),
                    !useChineseSymbols
            );
            mainHandler.post(() -> {
                isRimeStarting = false;
                if (enabled) {
                    migrateFallbackBufferToRime();
                    syncRimeOptions();
                    updateCandidates();
                    updateSpaceKeyLabel();
                    if (TextUtils.isEmpty(pendingRawText) && !isRecording && !isListening && !isCorrecting) {
                        setStatus(text("\u5c31\u7eea", "Ready"));
                    }
                    return;
                }
                didReportRimeFallback = true;
                setStatus(text("\u5df2\u4f7f\u7528\u515c\u5e95\u62fc\u97f3\uff1a", "Fallback pinyin: ")
                        + rimeCandidateEngine.disabledReason());
            });
        }, "VoiceTransformRimeInit").start();
    }

    private boolean isRimeActive() {
        return rimeCandidateEngine != null && rimeCandidateEngine.isAvailable();
    }

    private boolean hasActiveComposition() {
        if (isRimeActive()) {
            return rimeCandidateEngine.hasComposition();
        }
        return pinyinBuffer.length() > 0;
    }

    private String currentRawInput() {
        if (isRimeActive()) {
            return rimeCandidateEngine.rawInput();
        }
        return pinyinBuffer.toString();
    }

    private void syncRimeOptions() {
        if (!isRimeActive()) {
            if (rimeCandidateEngine != null && !didReportRimeFallback && !isRimeStarting) {
                didReportRimeFallback = true;
                setStatus(text("\u5df2\u4f7f\u7528\u515c\u5e95\u62fc\u97f3\uff1a", "Fallback pinyin: ")
                        + rimeCandidateEngine.disabledReason());
            }
            return;
        }
        rimeCandidateEngine.syncOptions(
                !AppSettings.KEYBOARD_MODE_ZH.equals(keyboardInputMode),
                !useChineseSymbols
        );
    }

    private void migrateFallbackBufferToRime() {
        if (!isRimeActive() || pinyinBuffer.length() == 0) {
            return;
        }
        String pending = pinyinBuffer.toString();
        pinyinBuffer.setLength(0);
        for (int index = 0; index < pending.length(); index++) {
            rimeCandidateEngine.inputLetter(pending.charAt(index));
        }
    }

    private String emptyToDefault(String value, String defaultValue) {
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadLocalDictionaries() {
        if (dictionariesLoaded) {
            return;
        }
        dictionariesLoaded = true;
        Map<String, String> singleCharacterPinyin = loadLunaPinyinDictionary();
        loadThuoclItDictionary(singleCharacterPinyin);
    }

    private Map<String, String> loadLunaPinyinDictionary() {
        Map<String, String> singleCharacterPinyin = new LinkedHashMap<>();
        try (BufferedReader reader = openAssetReader(LUNA_PINYIN_ASSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLunaPinyinLine(line, singleCharacterPinyin);
            }
        } catch (IOException ignored) {
        }
        return singleCharacterPinyin;
    }

    private void parseLunaPinyinLine(String line, Map<String, String> singleCharacterPinyin) {
        if (TextUtils.isEmpty(line) || line.charAt(0) == '#' || line.indexOf('\t') < 0) {
            return;
        }
        String[] columns = line.split("\t");
        if (columns.length < 2) {
            return;
        }
        String word = columns[0].trim();
        String pinyin = normalizePinyin(columns[1]);
        if (TextUtils.isEmpty(word) || TextUtils.isEmpty(pinyin) || word.length() > MAX_ASSET_TERM_LENGTH) {
            return;
        }
        if (word.length() == 1 && shouldUseSingleCharacterPinyin(columns, singleCharacterPinyin, word)) {
            singleCharacterPinyin.put(word, pinyin);
        }
    }

    private void loadThuoclItDictionary(Map<String, String> singleCharacterPinyin) {
        try (BufferedReader reader = openAssetReader(THUOCL_IT_ASSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseThuoclLine(line, singleCharacterPinyin);
            }
        } catch (IOException ignored) {
        }
    }

    private void parseThuoclLine(String line, Map<String, String> singleCharacterPinyin) {
        if (TextUtils.isEmpty(line)) {
            return;
        }
        String[] columns = line.trim().split("\\s+");
        if (columns.length == 0) {
            return;
        }
        String word = columns[0].trim();
        if (TextUtils.isEmpty(word) || word.length() > MAX_ASSET_TERM_LENGTH) {
            return;
        }
        String pinyin = pinyinForWord(word, singleCharacterPinyin);
        if (TextUtils.isEmpty(pinyin)) {
            return;
        }
        int frequency = parseFrequency(columns) + THUOCL_FREQUENCY_BOOST;
        addAssetCandidate(pinyin, word, frequency);
    }

    private BufferedReader openAssetReader(String assetPath) throws IOException {
        InputStream inputStream = getAssets().open(assetPath);
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private String pinyinForWord(String word, Map<String, String> singleCharacterPinyin) {
        StringBuilder pinyin = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            String charText = word.substring(index, index + 1);
            String syllable;
            char current = word.charAt(index);
            if (isAsciiLetterOrDigit(current)) {
                syllable = String.valueOf(current).toLowerCase(Locale.US);
            } else {
                syllable = singleCharacterPinyin.get(charText);
            }
            if (TextUtils.isEmpty(syllable)) {
                return "";
            }
            pinyin.append(syllable);
        }
        return pinyin.toString();
    }

    private boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9');
    }

    private boolean shouldUseSingleCharacterPinyin(
            String[] columns,
            Map<String, String> singleCharacterPinyin,
            String word
    ) {
        if (!singleCharacterPinyin.containsKey(word)) {
            return true;
        }
        return pinyinWeight(columns) > 50.0f;
    }

    private float pinyinWeight(String[] columns) {
        if (columns.length < 3) {
            return 100.0f;
        }
        String value = columns[2].trim();
        if (!value.endsWith("%")) {
            return 100.0f;
        }
        try {
            return Float.parseFloat(value.substring(0, value.length() - 1));
        } catch (NumberFormatException ignored) {
            return 100.0f;
        }
    }

    private int parseFrequency(String[] columns) {
        if (columns.length < 2) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(columns[columns.length - 1]));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void addAssetCandidate(String pinyin, String text, int frequency) {
        List<Candidate> candidates = pinyinDictionary.get(pinyin);
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate.text.equals(text)) {
                    return;
                }
            }
        }
        addCandidates(pinyinDictionary, pinyin, new Candidate(pinyin, text, frequency));
        trimCandidates(pinyin);
    }

    private void trimCandidates(String pinyin) {
        List<Candidate> candidates = pinyinDictionary.get(pinyin);
        if (candidates == null || candidates.size() <= MAX_ASSET_CANDIDATES_PER_PINYIN) {
            return;
        }
        candidates.subList(MAX_ASSET_CANDIDATES_PER_PINYIN, candidates.size()).clear();
    }

    private String normalizePinyin(String value) {
        return value == null ? "" : value.replace(" ", "").trim().toLowerCase(Locale.US);
    }

    private void startVoiceInput() {
        if (isCorrecting) {
            isVoiceHoldActive = false;
            updateSpaceKeyLabel();
            return;
        }
        if (AppSettings.SPEECH_MODE_BACKEND.equals(AppSettings.getSpeechMode(this))) {
            startBackendVoiceInput();
            return;
        }
        startSystemSpeechRecognition();
    }

    private void stopVoiceInputFromHold() {
        if (AppSettings.SPEECH_MODE_BACKEND.equals(AppSettings.getSpeechMode(this))) {
            stopRecordingAndUpload();
            return;
        }
        stopSystemSpeechRecognition();
    }

    private void startSystemSpeechRecognition() {
        if (isListening) {
            stopSystemSpeechRecognition();
            return;
        }
        if (!hasAudioPermission()) {
            isVoiceHoldActive = false;
            updateSpaceKeyLabel();
            setStatus(text("\u8bf7\u5148\u6388\u4e88\u9ea6\u514b\u98ce\u6743\u9650", "Grant microphone permission first"));
            openSettingsActivity();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            isVoiceHoldActive = false;
            updateSpaceKeyLabel();
            setStatus(text(
                    "\u7cfb\u7edf\u8bed\u97f3\u4e0d\u53ef\u7528\uff0c\u8bf7\u5728\u540e\u7aef\u8bed\u97f3\u914d\u7f6e\u5b8c\u6210\u540e\u4f7f\u7528\u540e\u7aef\u6a21\u5f0f\u3002",
                    "System speech unavailable. Configure backend ASR before using backend mode."
            ));
            return;
        }

        clearPendingResult(null);
        ensureSpeechRecognizer();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            updateSpaceKeyLabel();
            setStatus(text("\u6b63\u5728\u542c\u5199...", "Listening..."));
        } catch (Exception exception) {
            isListening = false;
            isVoiceHoldActive = false;
            resetVoiceButton();
            setStatus(text("\u7cfb\u7edf\u8bed\u97f3\u542f\u52a8\u5931\u8d25\uff1a", "System speech failed: ") + exception.getClass().getSimpleName());
        }
    }

    private void stopSystemSpeechRecognition() {
        if (!isListening || speechRecognizer == null) {
            return;
        }
        speechRecognizer.stopListening();
        setStatus(text("\u6b63\u5728\u7ed3\u675f\u8bc6\u522b...", "Finishing speech recognition..."));
    }

    private void ensureSpeechRecognizer() {
        if (speechRecognizer != null) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                setStatus(text("\u6b63\u5728\u542c\u5199...", "Listening..."));
            }

            @Override
            public void onBeginningOfSpeech() {
                setStatus(text("\u5df2\u542c\u5230\u58f0\u97f3\uff0c\u8bf7\u7ee7\u7eed\u8bf4\u3002", "Listening... speak clearly."));
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                finishSystemListening(text("\u8bc6\u522b\u4e2d...", "Recognizing..."));
            }

            @Override
            public void onError(int error) {
                finishSystemListening(text("\u8bed\u97f3\u8bc6\u522b\u5931\u8d25\uff1a", "Speech recognition failed: ") + speechErrorText(error));
            }

            @Override
            public void onResults(Bundle results) {
                finishSystemListening(text("\u7ea0\u9519\u4e2d...", "Correcting..."));
                String rawText = firstSpeechResult(results);
                if (TextUtils.isEmpty(rawText)) {
                    setStatus(text("\u672a\u8bc6\u522b\u5230\u6587\u672c", "No speech text recognized"));
                    return;
                }
                requestTextCorrection(rawText);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstSpeechResult(partialResults);
                if (!TextUtils.isEmpty(text)) {
                    setStatus((isChinese ? "\u5df2\u542c\u5230\uff1a" : "Heard: ") + text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private String firstSpeechResult(Bundle results) {
        if (results == null) {
            return "";
        }
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        for (String match : matches) {
            if (!TextUtils.isEmpty(match)) {
                return match.trim();
            }
        }
        return "";
    }

    private void requestTextCorrection(String rawText) {
        isCorrecting = true;
        refreshKeyboardState();
        setStatus(text("\u7ea0\u9519\u4e2d...", "Correcting..."));

        String backendUrl = AppSettings.getBackendUrl(this);
        TextCorrectionRequest request = new TextCorrectionRequest(
                AppSettings.getUserId(this),
                rawText,
                AppSettings.getAppContext(this)
        );

        new CorrectionApiClient(backendUrl).correctText(request, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    refreshKeyboardState();
                    showCorrectionResult(response);
                    setStatus(text("\u8bf7\u786e\u8ba4\u7ed3\u679c\u540e\u63d2\u5165\u3002", "Review result, then insert. ") + shortCorrectionMethod(response));
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    refreshKeyboardState();
                    showCorrectionResult(rawText, rawText);
                    setStatus(text("\u7ea0\u9519\u5931\u8d25\uff0c\u5df2\u4fdd\u7559\u539f\u6587\u3002\u540e\u7aef\uff1a", "Correction failed. Raw text is available. Backend: ") + backendUrl);
                });
            }
        });
    }

    private void startBackendVoiceInput() {
        if (isRecording) {
            stopRecordingAndUpload();
            return;
        }
        if (!hasAudioPermission()) {
            isVoiceHoldActive = false;
            updateSpaceKeyLabel();
            setStatus(text("\u8bf7\u5148\u6388\u4e88\u9ea6\u514b\u98ce\u6743\u9650", "Grant microphone permission first"));
            openSettingsActivity();
            return;
        }
        clearPendingResult(null);
        setStatus(text(
                "\u6309\u4f4f\u8bf4\u8bdd\uff0c\u677e\u624b\u540e\u81ea\u52a8\u8bc6\u522b\u3002",
                "Hold to speak, release to recognize."
        ));
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = new File(getCacheDir(), "voice_input_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = createRecorder(recordingFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            updateSpaceKeyLabel();
            setStatus(text("\u5f55\u97f3\u4e2d\uff0c\u677e\u624b\u81ea\u52a8\u505c\u6b62\u3002", "Recording... release to stop."));
            mainHandler.postDelayed(recordingTimeoutRunnable, MAX_RECORDING_MS);
        } catch (Exception exception) {
            isVoiceHoldActive = false;
            cancelRecording();
            setStatus(text("\u5f55\u97f3\u5931\u8d25\uff1a", "Recording failed: ") + exception.getClass().getSimpleName());
        }
    }

    private MediaRecorder createRecorder(File outputFile) {
        MediaRecorder recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new MediaRecorder(this)
                : new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(16000);
        recorder.setAudioChannels(1);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        return recorder;
    }

    private void stopRecordingAndUpload() {
        if (!isRecording) {
            return;
        }
        File audioFile = recordingFile;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException exception) {
            cancelRecording();
            setStatus(text("\u5f55\u97f3\u592a\u77ed", "Recording too short"));
            return;
        } finally {
            releaseRecorder();
        }

        isRecording = false;
        isVoiceHoldActive = false;
        mainHandler.removeCallbacks(recordingTimeoutRunnable);
        resetVoiceButton();
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            setStatus(text("\u6ca1\u6709\u5f55\u5230\u97f3\u9891", "No audio recorded"));
            return;
        }
        uploadAudioForCorrection(audioFile);
    }

    private void uploadAudioForCorrection(File audioFile) {
        isCorrecting = true;
        refreshKeyboardState();
        setStatus(text("\u6b63\u5728\u4e0a\u4f20\u97f3\u9891...", "Uploading audio..."));

        String backendUrl = AppSettings.getBackendUrl(this);
        String userId = AppSettings.getUserId(this);
        String appContext = AppSettings.getAppContext(this);

        new CorrectionApiClient(backendUrl).correctAudio(audioFile, userId, appContext, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    refreshKeyboardState();
                    showCorrectionResult(response);
                    setStatus(text("\u8bf7\u786e\u8ba4\u7ed3\u679c\u540e\u63d2\u5165\u3002", "Review result, then insert. ") + shortCorrectionMethod(response));
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    refreshKeyboardState();
                    setStatus(text("\u540e\u7aef\u8bed\u97f3\u8bc6\u522b\u5931\u8d25\uff1a", "Backend ASR failed: ")
                            + exception.getClass().getSimpleName()
                            + text("\u3002\u8bf7\u68c0\u67e5\u8bed\u97f3\u8bc6\u522b\u914d\u7f6e\u548c ", ". Check ASR config and ")
                            + backendUrl);
                    deleteRecordingFile(audioFile);
                });
            }
        });
    }

    private void showCorrectionResult(TextCorrectionResponse response) {
        pendingRawText = emptyToString(response.rawText);
        pendingCorrectedText = TextUtils.isEmpty(response.correctedText) ? pendingRawText : response.correctedText;
        isRawExpanded = false;
        updateRawTextView();
        updateRawToggleVisibility();
        updateCorrectedTextView();
        if (correctionMethodView != null) {
            correctionMethodView.setText(correctionMethodText(response));
        }
        if (resultPanel != null) {
            resultPanel.setVisibility(View.VISIBLE);
        }
    }

    private void showCorrectionResult(String rawText, String correctedText) {
        pendingRawText = emptyToString(rawText);
        pendingCorrectedText = TextUtils.isEmpty(correctedText) ? pendingRawText : correctedText;
        isRawExpanded = false;
        updateRawTextView();
        updateRawToggleVisibility();
        updateCorrectedTextView();
        if (correctionMethodView != null) {
            correctionMethodView.setText(text("\u65b9\u6cd5\uff1a\u7ea0\u9519\u5931\u8d25\uff0c\u5df2\u4fdd\u7559\u539f\u6587", "Method: correction failed, raw text available"));
        }
        if (resultPanel != null) {
            resultPanel.setVisibility(View.VISIBLE);
        }
    }

    private String correctionMethodText(TextCorrectionResponse response) {
        String text = (isChinese ? "\u65b9\u6cd5\uff1a" : "Method: ") + methodLabel(response.correctionMethod);
        if (response.llmUsed) {
            text += isChinese ? "\uff08\u5df2\u4f7f\u7528\u6a21\u578b\uff09" : " (LLM used)";
        } else if (!TextUtils.isEmpty(response.llmError)) {
            text += (isChinese ? "\uff08\u672a\u4f7f\u7528\u6a21\u578b\uff1a" : " (LLM not used: ") + response.llmError + (isChinese ? "\uff09" : ")");
        } else {
            text += isChinese ? "\uff08\u672a\u4f7f\u7528\u6a21\u578b\uff09" : " (LLM not used)";
        }
        return text;
    }

    private String shortCorrectionMethod(TextCorrectionResponse response) {
        if (response.llmUsed) {
            return text("\u5df2\u4f7f\u7528\u6a21\u578b\u3002", "LLM used.");
        }
        if (!TextUtils.isEmpty(response.llmError)) {
            return (isChinese ? "\u5df2\u4f7f\u7528\u515c\u5e95\u7ed3\u679c\uff1a" : "Fallback used: ") + response.llmError;
        }
        return (isChinese ? "\u65b9\u6cd5\uff1a" : "Method: ") + methodLabel(response.correctionMethod);
    }

    private String methodLabel(String method) {
        if ("llm".equals(method)) {
            return isChinese ? "\u6a21\u578b\u7ea0\u9519" : "LLM correction";
        }
        if ("rule_pinyin_fallback".equals(method)) {
            return isChinese ? "\u89c4\u5219/\u62fc\u97f3\u515c\u5e95" : "Rule/pinyin fallback";
        }
        if ("raw_text".equals(method)) {
            return isChinese ? "\u4fdd\u7559\u539f\u6587" : "Raw text kept";
        }
        return TextUtils.isEmpty(method) ? text("\u672a\u77e5", "unknown") : method;
    }

    private void insertPendingText(boolean useCorrectedText) {
        String text = useCorrectedText ? pendingCorrectedText : pendingRawText;
        if (TextUtils.isEmpty(text)) {
            setStatus(text("\u6ca1\u6709\u5f85\u63d2\u5165\u6587\u672c", "No pending text"));
            return;
        }
        commitText(text);
        clearPendingResult(text("\u5df2\u63d2\u5165", "Inserted"));
    }

    private void clearPendingResult(String status) {
        pendingRawText = "";
        pendingCorrectedText = "";
        isRawExpanded = false;
        if (resultPanel != null) {
            resultPanel.setVisibility(View.GONE);
        }
        if (rawTextView != null) {
            rawTextView.setText("");
        }
        if (correctedTextView != null) {
            correctedTextView.setText("");
        }
        if (correctionMethodView != null) {
            correctionMethodView.setText("");
        }
        updateRawToggleVisibility();
        if (status != null) {
            setStatus(status);
        }
    }

    private void commitText(String text) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null && !TextUtils.isEmpty(text)) {
            inputConnection.commitText(text, 1);
        }
    }

    private void deleteBackward() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.deleteSurroundingText(1, 0);
        }
    }

    private void startDeleteRepeat() {
        stopDeleteRepeat();
        isDeleteRepeating = true;
        deleteBackward();
        mainHandler.postDelayed(deleteRepeatRunnable, DELETE_REPEAT_INITIAL_DELAY_MS);
    }

    private void stopDeleteRepeat() {
        mainHandler.removeCallbacks(deleteRepeatRunnable);
        mainHandler.post(() -> isDeleteRepeating = false);
    }

    private void sendEnter() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    private void cancelRecording() {
        mainHandler.removeCallbacks(recordingTimeoutRunnable);
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    mediaRecorder.stop();
                }
            } catch (RuntimeException ignored) {
            }
            releaseRecorder();
        }
        isRecording = false;
        isCorrecting = false;
        isVoiceHoldActive = false;
        resetVoiceButton();
        deleteRecordingFile(recordingFile);
        recordingFile = null;
    }

    private void cancelSpeechRecognition() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
        }
        isListening = false;
        isVoiceHoldActive = false;
        resetVoiceButton();
    }

    private void destroySpeechRecognizer() {
        cancelSpeechRecognition();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void finishSystemListening(String status) {
        isListening = false;
        isVoiceHoldActive = false;
        resetVoiceButton();
        setStatus(status);
    }

    private void resetVoiceButton() {
        refreshKeyboardState();
    }

    private void refreshKeyboardState() {
        renderKeyboard();
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    private void toggleSymbolStyle() {
        useChineseSymbols = !useChineseSymbols;
        AppSettings.saveChineseSymbols(this, useChineseSymbols);
        syncRimeOptions();
        renderKeyboard();
        setStatus(useChineseSymbols
                ? text("\u5df2\u5207\u6362\u4e3a\u4e2d\u6587\u7b26\u53f7", "Chinese punctuation")
                : text("\u5df2\u5207\u6362\u4e3a\u82f1\u6587\u7b26\u53f7", "English punctuation"));
    }

    private void setLanguage(boolean chinese) {
        if (isChinese == chinese) {
            return;
        }
        isChinese = chinese;
        AppSettings.saveLanguage(this, chinese);
        applyLanguage();
        if (TextUtils.isEmpty(pendingRawText) && !isRecording && !isListening && !isCorrecting) {
            setStatus(text("\u5c31\u7eea", "Ready"));
        }
    }

    private void applyLanguage() {
        if (chineseButton != null) {
            chineseButton.setSelected(isChinese);
        }
        if (englishButton != null) {
            englishButton.setSelected(!isChinese);
        }
        if (deleteButton != null) {
            deleteButton.setText("\u232b");
        }
        if (spaceButton != null) {
            spaceButton.setText(keyLabel("space"));
        }
        if (insertCorrectedButton != null) {
            insertCorrectedButton.setText(text("\u63d2\u5165\u4fee\u6b63", "Insert Fix"));
        }
        if (insertRawButton != null) {
            insertRawButton.setText(text("\u63d2\u5165\u539f\u6587", "Insert Raw"));
        }
        if (cancelButton != null) {
            cancelButton.setText(text("\u53d6\u6d88", "Cancel"));
        }
        updateRawTextView();
        updateCorrectedTextView();
        updateRawToggleVisibility();
        syncRimeOptions();
        renderKeyboard();
        updateCandidates();
    }

    private void toggleRawExpansion() {
        isRawExpanded = !isRawExpanded;
        updateRawTextView();
        updateCorrectedTextView();
        updateRawToggleVisibility();
    }

    private void updateRawTextView() {
        if (rawTextView == null) {
            return;
        }
        rawTextView.setText((isChinese ? "\u539f\u6587\uff1a" : "Raw: ") + pendingRawText);
        rawTextView.setMaxLines(isRawExpanded ? 4 : 1);
        rawTextView.setEllipsize(isRawExpanded ? null : TextUtils.TruncateAt.END);
    }

    private void updateCorrectedTextView() {
        if (correctedTextView != null) {
            correctedTextView.setText((isChinese ? "\u4fee\u6b63\uff1a" : "Corrected: ") + pendingCorrectedText);
            correctedTextView.setMaxLines(isRawExpanded ? 6 : 2);
            correctedTextView.setEllipsize(isRawExpanded ? null : TextUtils.TruncateAt.END);
        }
    }

    private void updateRawToggleVisibility() {
        if (rawToggleButton == null) {
            return;
        }
        rawToggleButton.setText(isRawExpanded ? text("\u6536\u8d77", "Less") : text("\u5c55\u5f00", "More"));
        rawToggleButton.setVisibility(TextUtils.isEmpty(pendingRawText) ? View.GONE : View.VISIBLE);
    }

    private String text(String chinese, String english) {
        return isChinese ? chinese : english;
    }

    private String speechErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return text("\u97f3\u9891\u9519\u8bef", "audio error");
            case SpeechRecognizer.ERROR_CLIENT:
                return text("\u5ba2\u6237\u7aef\u9519\u8bef", "client error");
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return text("\u9ea6\u514b\u98ce\u6743\u9650\u4e0d\u8db3", "microphone permission denied");
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return text("\u7f51\u7edc\u9519\u8bef", "network error");
            case SpeechRecognizer.ERROR_NO_MATCH:
                return text("\u672a\u8bc6\u522b\u5230\u5185\u5bb9", "no match");
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return text("\u8bc6\u522b\u670d\u52a1\u5fd9", "recognizer busy");
            case SpeechRecognizer.ERROR_SERVER:
                return text("\u8bed\u97f3\u670d\u52a1\u9519\u8bef", "speech service error");
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return text("\u6ca1\u6709\u68c0\u6d4b\u5230\u8bed\u97f3", "no speech detected");
            default:
                return "error " + error;
        }
    }

    private String emptyToString(String value) {
        return value == null ? "" : value;
    }

    private void deleteRecordingFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static Map<String, List<Candidate>> createBasePinyinDictionary() {
        Map<String, List<Candidate>> dictionary = new LinkedHashMap<>();
        addCandidates(dictionary, "jizu", candidate("jizu", "\u8ba1\u7ec4", 9000), candidate("jizu", "\u673a\u7ec4", 3000), candidate("jizu", "\u8bb0\u4f4f", 2500));
        addCandidates(dictionary, "jiwang", candidate("jiwang", "\u8ba1\u7f51", 9000), candidate("jiwang", "\u5bc4\u671b", 1800));
        addCandidates(dictionary, "caozuoxitong", candidate("caozuoxitong", "\u64cd\u4f5c\u7cfb\u7edf", 9000));
        addCandidates(dictionary, "shujujiegou", candidate("shujujiegou", "\u6570\u636e\u7ed3\u6784", 9000));
        addCandidates(dictionary, "xiancheng", candidate("xiancheng", "\u7ebf\u7a0b", 7000), candidate("xiancheng", "\u73b0\u6210", 2800), candidate("xiancheng", "\u53bf\u57ce", 1200));
        addCandidates(dictionary, "jincheng", candidate("jincheng", "\u8fdb\u7a0b", 7000), candidate("jincheng", "\u8fdb\u57ce", 1800));
        addCandidates(dictionary, "huancun", candidate("huancun", "\u7f13\u5b58", 7000));
        addCandidates(dictionary, "suanfa", candidate("suanfa", "\u7b97\u6cd5", 7000));
        addCandidates(dictionary, "biancheng", candidate("biancheng", "\u7f16\u7a0b", 6500));
        addCandidates(dictionary, "jisuanji", candidate("jisuanji", "\u8ba1\u7b97\u673a", 6500));
        addCandidates(dictionary, "rengongzhineng", candidate("rengongzhineng", "\u4eba\u5de5\u667a\u80fd", 6500));
        addCandidates(dictionary, "shenjinwangluo", candidate("shenjinwangluo", "\u795e\u7ecf\u7f51\u7edc", 5000));
        addCandidates(dictionary, "shenduxuexi", candidate("shenduxuexi", "\u6df1\u5ea6\u5b66\u4e60", 5000));
        addCandidates(dictionary, "jiqixuexi", candidate("jiqixuexi", "\u673a\u5668\u5b66\u4e60", 5000));
        addCandidates(dictionary, "agent", candidate("agent", "Agent", 9000));
        addCandidates(dictionary, "rag", candidate("rag", "RAG", 9000));
        addCandidates(dictionary, "cache", candidate("cache", "Cache", 9000));

        addCandidates(dictionary, "jin", candidate("jin", "\u4eca", 3800));
        addCandidates(dictionary, "jintian", candidate("jintian", "\u4eca\u5929", 7000));
        addCandidates(dictionary, "shangwu", candidate("shangwu", "\u4e0a\u5348", 6000));
        addCandidates(dictionary, "xiawu", candidate("xiawu", "\u4e0b\u5348", 6000));
        addCandidates(dictionary, "wanshang", candidate("wanshang", "\u665a\u4e0a", 6000));
        addCandidates(dictionary, "wo", candidate("wo", "\u6211", 7000));
        addCandidates(dictionary, "women", candidate("women", "\u6211\u4eec", 6500));
        addCandidates(dictionary, "ni", candidate("ni", "\u4f60", 6500));
        addCandidates(dictionary, "ta", candidate("ta", "\u4ed6", 4500), candidate("ta", "\u5979", 4300), candidate("ta", "\u5b83", 2000));
        addCandidates(dictionary, "shang", candidate("shang", "\u4e0a", 5000));
        addCandidates(dictionary, "shangle", candidate("shangle", "\u4e0a\u4e86", 6500));
        addCandidates(dictionary, "liang", candidate("liang", "\u4e24", 5000));
        addCandidates(dictionary, "jie", candidate("jie", "\u8282", 4200));
        addCandidates(dictionary, "ke", candidate("ke", "\u8bfe", 5000));
        addCandidates(dictionary, "laoshi", candidate("laoshi", "\u8001\u5e08", 5500));
        addCandidates(dictionary, "jiang", candidate("jiang", "\u8bb2", 4500));
        addCandidates(dictionary, "jiangliao", candidate("jiangliao", "\u8bb2\u4e86", 5500));
        addCandidates(dictionary, "zuoye", candidate("zuoye", "\u4f5c\u4e1a", 5200));
        addCandidates(dictionary, "kaoshi", candidate("kaoshi", "\u8003\u8bd5", 5200));
        addCandidates(dictionary, "xuexi", candidate("xuexi", "\u5b66\u4e60", 5200));
        addCandidates(dictionary, "xiangmu", candidate("xiangmu", "\u9879\u76ee", 5000));
        addCandidates(dictionary, "houtai", candidate("houtai", "\u540e\u53f0", 5000));
        addCandidates(dictionary, "qianduan", candidate("qianduan", "\u524d\u7aef", 5000));
        addCandidates(dictionary, "houduan", candidate("houduan", "\u540e\u7aef", 5000));
        addCandidates(dictionary, "jiekou", candidate("jiekou", "\u63a5\u53e3", 5000));
        addCandidates(dictionary, "shuju", candidate("shuju", "\u6570\u636e", 5200));
        addCandidates(dictionary, "moxing", candidate("moxing", "\u6a21\u578b", 5000));
        addCandidates(dictionary, "yuyin", candidate("yuyin", "\u8bed\u97f3", 5000));
        addCandidates(dictionary, "shuru", candidate("shuru", "\u8f93\u5165", 5000));
        addCandidates(dictionary, "shurufa", candidate("shurufa", "\u8f93\u5165\u6cd5", 6000));
        addCandidates(dictionary, "jiucuo", candidate("jiucuo", "\u7ea0\u9519", 5600));
        addCandidates(dictionary, "houxuan", candidate("houxuan", "\u5019\u9009", 4500));
        addCandidates(dictionary, "queren", candidate("queren", "\u786e\u8ba4", 5000));
        addCandidates(dictionary, "charu", candidate("charu", "\u63d2\u5165", 5000));

        addCandidates(dictionary, "jintianshangwushangleliangjiejizuke", candidate("jintianshangwushangleliangjiejizuke", "\u4eca\u5929\u4e0a\u5348\u4e0a\u4e86\u4e24\u8282\u8ba1\u7ec4\u8bfe", 12000));
        addCandidates(dictionary, "laoshijiangliaoxianchengdiaodu", candidate("laoshijiangliaoxianchengdiaodu", "\u8001\u5e08\u8bb2\u4e86\u7ebf\u7a0b\u8c03\u5ea6", 9000));
        addCandidates(dictionary, "woyaoxueshujvjiegou", candidate("woyaoxueshujvjiegou", "\u6211\u8981\u5b66\u6570\u636e\u7ed3\u6784", 7000));
        addCandidates(dictionary, "woyaoxueshujujiegou", candidate("woyaoxueshujujiegou", "\u6211\u8981\u5b66\u6570\u636e\u7ed3\u6784", 7000));
        return dictionary;
    }

    private static void addCandidates(Map<String, List<Candidate>> dictionary, String pinyin, Candidate... candidates) {
        List<Candidate> values = dictionary.get(pinyin);
        if (values == null) {
            values = new ArrayList<>();
            dictionary.put(pinyin, values);
        }
        Collections.addAll(values, candidates);
        Collections.sort(values, Comparator.comparingInt((Candidate candidate) -> candidate.frequency).reversed());
    }

    private static Candidate candidate(String pinyin, String text, int frequency) {
        return new Candidate(pinyin, text, frequency);
    }

    private static final class Candidate {
        final String pinyin;
        final String text;
        final int frequency;
        final int rimeIndex;
        final String comment;

        Candidate(String pinyin, String text, int frequency) {
            this(pinyin, text, frequency, -1, "");
        }

        Candidate(String pinyin, String text, int frequency, int rimeIndex, String comment) {
            this.pinyin = pinyin;
            this.text = text;
            this.frequency = frequency;
            this.rimeIndex = rimeIndex;
            this.comment = comment == null ? "" : comment;
        }
    }
}
