package com.example.voicetransform;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.inputmethod.InputMethodManager;
import android.inputmethodservice.InputMethodService;
import android.widget.Button;
import android.widget.TextView;

import com.example.voicetransform.api.CorrectionApiClient;
import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceInputMethodService extends InputMethodService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private Button voiceButton;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening;
    private boolean isCorrecting;

    @Override
    public View onCreateInputView() {
        View view = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null);
        statusText = view.findViewById(R.id.imeStatusText);
        voiceButton = view.findViewById(R.id.imeVoiceButton);
        Button deleteButton = view.findViewById(R.id.imeDeleteButton);
        Button spaceButton = view.findViewById(R.id.imeSpaceButton);
        Button enterButton = view.findViewById(R.id.imeEnterButton);
        Button switchButton = view.findViewById(R.id.imeSwitchButton);

        voiceButton.setOnClickListener(v -> startVoiceInput());
        deleteButton.setOnClickListener(v -> deleteBackward());
        spaceButton.setOnClickListener(v -> commitText(" "));
        enterButton.setOnClickListener(v -> sendEnter());
        switchButton.setOnClickListener(v -> showInputMethodPicker());

        setStatus("Ready");
        return view;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        stopListening();
    }

    @Override
    public void onDestroy() {
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    private void startVoiceInput() {
        if (isListening || isCorrecting) {
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Grant microphone permission in Voice Transform first");
            openSettingsActivity();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("No speech recognizer available");
            return;
        }

        ensureSpeechRecognizer();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        isListening = true;
        voiceButton.setEnabled(false);
        setStatus("Listening...");
        speechRecognizer.startListening(intent);
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
                setStatus("Listening...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                setStatus("Recognizing...");
            }

            @Override
            public void onError(int error) {
                isListening = false;
                voiceButton.setEnabled(true);
                setStatus("Speech failed: " + speechErrorName(error));
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                voiceButton.setEnabled(true);
                String rawText = firstResult(results);
                if (TextUtils.isEmpty(rawText)) {
                    setStatus("No speech text");
                    return;
                }
                correctAndCommit(rawText);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String rawText = firstResult(partialResults);
                if (!TextUtils.isEmpty(rawText)) {
                    setStatus("Listening: " + rawText);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void correctAndCommit(String rawText) {
        isCorrecting = true;
        voiceButton.setEnabled(false);
        setStatus("Correcting: " + rawText);

        String backendUrl = AppSettings.getBackendUrl(this);
        String userId = AppSettings.getUserId(this);
        String appContext = AppSettings.getAppContext(this);
        TextCorrectionRequest request = new TextCorrectionRequest(userId, rawText, appContext);

        new CorrectionApiClient(backendUrl).correctText(request, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    commitText(response.correctedText);
                    setStatus("Inserted: " + response.correctedText);
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    setStatus("Request failed. Backend: " + backendUrl);
                });
            }
        });
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

    private void showInputMethodPicker() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showInputMethodPicker();
        }
    }

    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.cancel();
        }
        isListening = false;
        isCorrecting = false;
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

    private String firstResult(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        return matches.get(0);
    }

    private void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    private String speechErrorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "audio";
            case SpeechRecognizer.ERROR_CLIENT:
                return "client";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "permission";
            case SpeechRecognizer.ERROR_NETWORK:
                return "network";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "no match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "speech timeout";
            default:
                return String.valueOf(error);
        }
    }
}
