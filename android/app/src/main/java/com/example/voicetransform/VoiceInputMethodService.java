package com.example.voicetransform;

import android.Manifest;
import android.content.Intent;
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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;

import com.example.voicetransform.api.CorrectionApiClient;
import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import java.io.File;
import java.util.ArrayList;

public class VoiceInputMethodService extends InputMethodService {
    private static final long MAX_RECORDING_MS = 60000;
    private static final long DELETE_REPEAT_INITIAL_DELAY_MS = 350;
    private static final long DELETE_REPEAT_INTERVAL_MS = 60;

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
    private Button voiceButton;
    private Button deleteButton;
    private Button spaceButton;
    private Button enterButton;
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
    private boolean isRawExpanded;
    private boolean isDeleteRepeating;
    private boolean isRecording;
    private boolean isListening;
    private boolean isCorrecting;
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
        voiceButton = view.findViewById(R.id.imeVoiceButton);
        deleteButton = view.findViewById(R.id.imeDeleteButton);
        spaceButton = view.findViewById(R.id.imeSpaceButton);
        enterButton = view.findViewById(R.id.imeEnterButton);
        insertCorrectedButton = view.findViewById(R.id.imeInsertCorrectedButton);
        insertRawButton = view.findViewById(R.id.imeInsertRawButton);
        cancelButton = view.findViewById(R.id.imeCancelButton);
        chineseButton = view.findViewById(R.id.imeChineseButton);
        englishButton = view.findViewById(R.id.imeEnglishButton);
        rawToggleButton = view.findViewById(R.id.imeRawToggleButton);

        voiceButton.setOnClickListener(v -> startVoiceInput());
        deleteButton.setOnClickListener(v -> {
            if (!isDeleteRepeating) {
                deleteBackward();
            }
        });
        deleteButton.setOnLongClickListener(v -> {
            startDeleteRepeat();
            return true;
        });
        deleteButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                stopDeleteRepeat();
            }
            return false;
        });
        spaceButton.setOnClickListener(v -> commitText(" "));
        enterButton.setOnClickListener(v -> sendEnter());
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

    private void startVoiceInput() {
        if (isCorrecting) {
            return;
        }
        if (AppSettings.SPEECH_MODE_BACKEND.equals(AppSettings.getSpeechMode(this))) {
            startBackendVoiceInput();
            return;
        }
        startSystemSpeechRecognition();
    }

    private void startSystemSpeechRecognition() {
        if (isListening) {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
            setStatus(text("\u6b63\u5728\u7ed3\u675f\u8bc6\u522b...", "Finishing speech recognition..."));
            return;
        }
        if (!hasAudioPermission()) {
            setStatus(text("\u8bf7\u5148\u6388\u4e88\u9ea6\u514b\u98ce\u6743\u9650", "Grant microphone permission first"));
            openSettingsActivity();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
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
            voiceButton.setText(text("\u505c\u6b62", "Stop"));
            setStatus(text("\u6b63\u5728\u542c\u5199...", "Listening..."));
        } catch (Exception exception) {
            isListening = false;
            resetVoiceButton();
            setStatus(text("\u7cfb\u7edf\u8bed\u97f3\u542f\u52a8\u5931\u8d25\uff1a", "System speech failed: ") + exception.getClass().getSimpleName());
        }
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
        voiceButton.setEnabled(false);
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
                    voiceButton.setEnabled(true);
                    showCorrectionResult(response);
                    setStatus(text("\u8bf7\u786e\u8ba4\u7ed3\u679c\u540e\u63d2\u5165\u3002", "Review result, then insert. ") + shortCorrectionMethod(response));
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
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
            setStatus(text("\u8bf7\u5148\u6388\u4e88\u9ea6\u514b\u98ce\u6743\u9650", "Grant microphone permission first"));
            openSettingsActivity();
            return;
        }
        clearPendingResult(null);
        setStatus(text("\u540e\u7aef\u8bed\u97f3\u6a21\u5f0f\uff0c\u8bf7\u5f55\u97f3\u540e\u7b49\u5f85\u8bc6\u522b\u3002", "Backend ASR mode. Recording requires configured backend ASR."));
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = new File(getCacheDir(), "voice_input_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = createRecorder(recordingFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            voiceButton.setText(text("\u505c\u6b62", "Stop"));
            setStatus(text("\u5f55\u97f3\u4e2d\uff0c\u518d\u70b9\u4e00\u6b21\u505c\u6b62\u3002", "Recording... tap again to stop."));
            mainHandler.postDelayed(recordingTimeoutRunnable, MAX_RECORDING_MS);
        } catch (Exception exception) {
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
        voiceButton.setEnabled(false);
        setStatus(text("\u6b63\u5728\u4e0a\u4f20\u97f3\u9891...", "Uploading audio..."));

        String backendUrl = AppSettings.getBackendUrl(this);
        String userId = AppSettings.getUserId(this);
        String appContext = AppSettings.getAppContext(this);

        new CorrectionApiClient(backendUrl).correctAudio(audioFile, userId, appContext, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    showCorrectionResult(response);
                    setStatus(text("\u8bf7\u786e\u8ba4\u7ed3\u679c\u540e\u63d2\u5165\u3002", "Review result, then insert. ") + shortCorrectionMethod(response));
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
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
        resetVoiceButton();
        setStatus(status);
    }

    private void resetVoiceButton() {
        if (voiceButton != null) {
            voiceButton.setText(text("\u8bed\u97f3", "Voice"));
            voiceButton.setEnabled(!isCorrecting);
        }
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
        if (voiceButton != null) {
            resetVoiceButton();
        }
        if (deleteButton != null) {
            deleteButton.setText(text("\u5220\u9664", "Delete"));
        }
        if (spaceButton != null) {
            spaceButton.setText(text("\u7a7a\u683c", "Space"));
        }
        if (enterButton != null) {
            enterButton.setText(text("\u56de\u8f66", "Enter"));
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
    }

    private void toggleRawExpansion() {
        isRawExpanded = !isRawExpanded;
        updateRawTextView();
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
}
