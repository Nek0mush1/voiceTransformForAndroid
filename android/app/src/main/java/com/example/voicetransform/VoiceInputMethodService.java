package com.example.voicetransform;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import com.example.voicetransform.model.TextCorrectionResponse;

import java.io.File;

public class VoiceInputMethodService extends InputMethodService {
    private static final long MAX_RECORDING_MS = 60000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTimeoutRunnable = this::stopRecordingAndUpload;

    private TextView statusText;
    private Button voiceButton;
    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private boolean isRecording;
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
        cancelRecording();
    }

    @Override
    public void onDestroy() {
        cancelRecording();
        super.onDestroy();
    }

    private void startVoiceInput() {
        if (isCorrecting) {
            return;
        }
        if (isRecording) {
            stopRecordingAndUpload();
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Grant microphone permission in Voice Transform first");
            openSettingsActivity();
            return;
        }
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
            setStatus("Recording... tap again to stop");
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
        uploadAudioAndCommit(audioFile);
    }

    private void uploadAudioAndCommit(File audioFile) {
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
                    commitText(response.correctedText);
                    setStatus("Inserted: " + response.correctedText);
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    isCorrecting = false;
                    voiceButton.setEnabled(true);
                    setStatus("Audio request failed. Backend: " + backendUrl);
                    deleteRecordingFile(audioFile);
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
        if (voiceButton != null) {
            voiceButton.setText(R.string.ime_voice);
            voiceButton.setEnabled(true);
        }
        deleteRecordingFile(recordingFile);
        recordingFile = null;
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

    private void deleteRecordingFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
