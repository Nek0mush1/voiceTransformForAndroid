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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTimeoutRunnable = this::stopRecordingAndUpload;

    private TextView statusText;
    private TextView rawTextView;
    private TextView correctedTextView;
    private View resultPanel;
    private Button voiceButton;
    private MediaRecorder mediaRecorder;
    private SpeechRecognizer speechRecognizer;
    private File recordingFile;
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
        resultPanel = view.findViewById(R.id.imeResultPanel);
        voiceButton = view.findViewById(R.id.imeVoiceButton);
        Button deleteButton = view.findViewById(R.id.imeDeleteButton);
        Button spaceButton = view.findViewById(R.id.imeSpaceButton);
        Button enterButton = view.findViewById(R.id.imeEnterButton);
        Button insertCorrectedButton = view.findViewById(R.id.imeInsertCorrectedButton);
        Button insertRawButton = view.findViewById(R.id.imeInsertRawButton);
        Button cancelButton = view.findViewById(R.id.imeCancelButton);

        voiceButton.setOnClickListener(v -> startVoiceInput());
        deleteButton.setOnClickListener(v -> deleteBackward());
        spaceButton.setOnClickListener(v -> commitText(" "));
        enterButton.setOnClickListener(v -> sendEnter());
        insertCorrectedButton.setOnClickListener(v -> insertPendingText(true));
        insertRawButton.setOnClickListener(v -> insertPendingText(false));
        cancelButton.setOnClickListener(v -> clearPendingResult("Canceled"));

        clearPendingResult(null);
        setStatus("Ready");
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
        cancelRecording();
        cancelSpeechRecognition();
    }

    @Override
    public void onDestroy() {
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
            setStatus("Finishing speech recognition...");
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Grant microphone permission in Voice Transform first");
            openSettingsActivity();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("System speech unavailable. Set Speech Mode to backend only after backend ASR is configured.");
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
            voiceButton.setText("Stop");
            setStatus("Listening...");
        } catch (Exception exception) {
            isListening = false;
            voiceButton.setText(R.string.ime_voice);
            setStatus("System speech failed: " + exception.getClass().getSimpleName());
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
                setStatus("Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {
                setStatus("Listening... speak clearly");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                finishSystemListening("Recognizing...");
            }

            @Override
            public void onError(int error) {
                finishSystemListening("Speech recognition failed: " + speechErrorText(error));
            }

            @Override
            public void onResults(Bundle results) {
                finishSystemListening("Correcting...");
                String rawText = firstSpeechResult(results);
                if (TextUtils.isEmpty(rawText)) {
                    setStatus("No speech text recognized");
                    return;
                }
                requestTextCorrection(rawText);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstSpeechResult(partialResults);
                if (!TextUtils.isEmpty(text)) {
                    setStatus("Heard: " + text);
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
        setStatus("Correcting...");

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
                    showCorrectionResult(response.rawText, response.correctedText);
                    setStatus("Review result, then insert");
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    showCorrectionResult(rawText, rawText);
                    setStatus("Correction failed. Raw text is available. Backend: " + backendUrl);
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
            setStatus("Grant microphone permission in Voice Transform first");
            openSettingsActivity();
            return;
        }
        clearPendingResult(null);
        setStatus("Backend ASR mode. Recording requires configured backend ASR.");
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = new File(getCacheDir(), "voice_input_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = createRecorder(recordingFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            voiceButton.setText("Stop");
            setStatus("Backend ASR recording... tap again to stop");
            mainHandler.postDelayed(recordingTimeoutRunnable, MAX_RECORDING_MS);
        } catch (Exception exception) {
            cancelRecording();
            setStatus("Recording failed: " + exception.getClass().getSimpleName());
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
            setStatus("Recording too short");
            return;
        } finally {
            releaseRecorder();
        }

        isRecording = false;
        mainHandler.removeCallbacks(recordingTimeoutRunnable);
        voiceButton.setText(R.string.ime_voice);
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            setStatus("No audio recorded");
            return;
        }
        uploadAudioForCorrection(audioFile);
    }

    private void uploadAudioForCorrection(File audioFile) {
        isCorrecting = true;
        voiceButton.setEnabled(false);
        setStatus("Uploading audio...");

        String backendUrl = AppSettings.getBackendUrl(this);
        String userId = AppSettings.getUserId(this);
        String appContext = AppSettings.getAppContext(this);

        new CorrectionApiClient(backendUrl).correctAudio(audioFile, userId, appContext, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    showCorrectionResult(response.rawText, response.correctedText);
                    setStatus("Review result, then insert");
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    setStatus("Backend ASR failed: " + exception.getClass().getSimpleName() + ". Check ASR config and " + backendUrl);
                    deleteRecordingFile(audioFile);
                });
            }
        });
    }

    private void showCorrectionResult(String rawText, String correctedText) {
        pendingRawText = emptyToString(rawText);
        pendingCorrectedText = TextUtils.isEmpty(correctedText) ? pendingRawText : correctedText;
        if (rawTextView != null) {
            rawTextView.setText("Raw: " + pendingRawText);
        }
        if (correctedTextView != null) {
            correctedTextView.setText("Corrected: " + pendingCorrectedText);
        }
        if (resultPanel != null) {
            resultPanel.setVisibility(View.VISIBLE);
        }
    }

    private void insertPendingText(boolean useCorrectedText) {
        String text = useCorrectedText ? pendingCorrectedText : pendingRawText;
        if (TextUtils.isEmpty(text)) {
            setStatus("No pending text");
            return;
        }
        commitText(text);
        clearPendingResult("Inserted");
    }

    private void clearPendingResult(String status) {
        pendingRawText = "";
        pendingCorrectedText = "";
        if (resultPanel != null) {
            resultPanel.setVisibility(View.GONE);
        }
        if (rawTextView != null) {
            rawTextView.setText("");
        }
        if (correctedTextView != null) {
            correctedTextView.setText("");
        }
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
            voiceButton.setText(R.string.ime_voice);
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

    private String speechErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "audio error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "network error";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "no match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "speech service error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "no speech detected";
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
