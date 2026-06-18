package com.example.voicetransform;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.voicetransform.api.CorrectionApiClient;
import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    private static final long MAX_RECORDING_MS = 60000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTimeoutRunnable = this::stopRecordingAndUpload;

    private boolean isChinese = true;
    private boolean isRecording;
    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private EditText backendUrlInput;
    private EditText userIdInput;
    private Spinner contextSpinner;
    private EditText rawTextInput;
    private Button chineseButton;
    private Button englishButton;
    private Button voiceButton;
    private Button correctButton;
    private ProgressBar progressBar;
    private TextView titleText;
    private TextView subtitleText;
    private TextView backendUrlLabel;
    private TextView userIdLabel;
    private TextView contextLabel;
    private TextView rawTextLabel;
    private TextView correctedTextLabel;
    private TextView correctedTextValue;
    private TextView matchedTermsLabel;
    private TextView matchedTermsValue;
    private TextView reasonLabel;
    private TextView reasonValue;
    private TextView endpointHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupContextSpinner();
        loadSavedSettings();
        setupActions();
        requestRecordAudioPermissionIfNeeded();
        applyLanguage();
    }

    private void bindViews() {
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);
        backendUrlLabel = findViewById(R.id.backendUrlLabel);
        backendUrlInput = findViewById(R.id.backendUrlInput);
        userIdLabel = findViewById(R.id.userIdLabel);
        contextLabel = findViewById(R.id.contextLabel);
        rawTextLabel = findViewById(R.id.rawTextLabel);
        userIdInput = findViewById(R.id.userIdInput);
        contextSpinner = findViewById(R.id.contextSpinner);
        rawTextInput = findViewById(R.id.rawTextInput);
        chineseButton = findViewById(R.id.chineseButton);
        englishButton = findViewById(R.id.englishButton);
        voiceButton = findViewById(R.id.voiceButton);
        correctButton = findViewById(R.id.correctButton);
        progressBar = findViewById(R.id.progressBar);
        correctedTextLabel = findViewById(R.id.correctedTextLabel);
        correctedTextValue = findViewById(R.id.correctedTextValue);
        matchedTermsLabel = findViewById(R.id.matchedTermsLabel);
        matchedTermsValue = findViewById(R.id.matchedTermsValue);
        reasonLabel = findViewById(R.id.reasonLabel);
        reasonValue = findViewById(R.id.reasonValue);
        endpointHint = findViewById(R.id.endpointHint);
    }

    private void setupContextSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.context_values,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contextSpinner.setAdapter(adapter);
    }

    private void loadSavedSettings() {
        backendUrlInput.setText(AppSettings.getBackendUrl(this));
        userIdInput.setText(AppSettings.getUserId(this));
        String savedContext = AppSettings.getAppContext(this);
        for (int i = 0; i < contextSpinner.getCount(); i++) {
            if (savedContext.equals(contextSpinner.getItemAtPosition(i).toString())) {
                contextSpinner.setSelection(i);
                break;
            }
        }
    }

    private void setupActions() {
        chineseButton.setOnClickListener(view -> {
            isChinese = true;
            applyLanguage();
        });
        englishButton.setOnClickListener(view -> {
            isChinese = false;
            applyLanguage();
        });
        voiceButton.setOnClickListener(view -> toggleVoiceRecording());
        correctButton.setOnClickListener(view -> submitCorrection());
        rawTextInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentSettings();
    }

    @Override
    protected void onDestroy() {
        cancelRecording();
        super.onDestroy();
    }

    private void requestRecordAudioPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void applyLanguage() {
        titleText.setText(isChinese ? "\u8bed\u97f3\u6587\u672c\u7ea0\u9519" : "Voice Text Correction");
        subtitleText.setText(isChinese
                ? "\u8f93\u5165\u6216\u8bed\u97f3\u8bc6\u522b\u6587\u672c\uff0c\u8c03\u7528\u540e\u7aef\u8bcd\u5e93\u5b8c\u6210\u7ea0\u9519\u3002"
                : "Type or dictate text, then call the backend correction API.");
        userIdLabel.setText(isChinese ? "\u7528\u6237 ID" : "User ID");
        backendUrlLabel.setText(isChinese ? "\u540e\u7aef\u5730\u5740" : "Backend URL");
        backendUrlInput.setHint(isChinese ? "\u4f8b\u5982 http://39.106.51.35:8000" : "Example: http://39.106.51.35:8000");
        contextLabel.setText(isChinese ? "\u5e94\u7528\u573a\u666f" : "App Context");
        rawTextLabel.setText(isChinese ? "\u539f\u59cb\u6587\u672c" : "Raw Text");
        rawTextInput.setHint(isChinese ? "\u8f93\u5165\u8bed\u97f3\u8bc6\u522b\u540e\u7684\u6587\u672c" : "Enter recognized speech text");
        voiceButton.setText(isRecording
                ? (isChinese ? "\u505c\u6b62\u5f55\u97f3" : "Stop Recording")
                : (isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input"));
        correctButton.setText(isChinese ? "\u5f00\u59cb\u7ea0\u9519" : "Correct");
        correctedTextLabel.setText(isChinese ? "\u7ea0\u9519\u7ed3\u679c" : "Corrected Text");
        matchedTermsLabel.setText(isChinese ? "\u547d\u4e2d\u672f\u8bed" : "Matched Terms");
        reasonLabel.setText(isChinese ? "\u539f\u56e0" : "Reason");
        endpointHint.setText(isChinese
                ? "\u771f\u673a\u548c\u6a21\u62df\u5668\u5747\u53ef\u586b\u516c\u7f51\u670d\u52a1\u5668\u5730\u5740\uff1ahttp://39.106.51.35:8000"
                : "Use the public backend URL for both phone and emulator: http://39.106.51.35:8000");
        chineseButton.setSelected(isChinese);
        englishButton.setSelected(!isChinese);

        if (TextUtils.isEmpty(correctedTextValue.getText())) {
            correctedTextValue.setText(isChinese ? "\u7b49\u5f85\u7ea0\u9519\u7ed3\u679c" : "Waiting for correction result");
        }
        if (TextUtils.isEmpty(matchedTermsValue.getText())) {
            matchedTermsValue.setText("-");
        }
        if (TextUtils.isEmpty(reasonValue.getText())) {
            reasonValue.setText(isChinese ? "\u63d0\u4ea4\u540e\u663e\u793a\u539f\u56e0" : "Reason appears after submit");
        }
    }

    private void toggleVoiceRecording() {
        if (isRecording) {
            stopRecordingAndUpload();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        saveCurrentSettings();
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = new File(getCacheDir(), "voice_input_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = createRecorder(recordingFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            voiceButton.setText(isChinese ? "\u505c\u6b62\u5f55\u97f3" : "Stop Recording");
            reasonValue.setText(isChinese ? "\u5f55\u97f3\u4e2d\uff0c\u518d\u70b9\u4e00\u6b21\u505c\u6b62\u5e76\u4e0a\u4f20" : "Recording. Tap again to stop and upload.");
            mainHandler.postDelayed(recordingTimeoutRunnable, MAX_RECORDING_MS);
        } catch (Exception exception) {
            cancelRecording();
            Toast.makeText(this, isChinese ? "\u5f55\u97f3\u5931\u8d25" : "Recording failed", Toast.LENGTH_LONG).show();
            reasonValue.setText((isChinese ? "\u5f55\u97f3\u5931\u8d25\uff1a" : "Recording failed: ") + exception.getClass().getSimpleName());
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
            reasonValue.setText(isChinese ? "\u5f55\u97f3\u592a\u77ed\uff0c\u8bf7\u91cd\u8bd5" : "Recording too short. Try again.");
            return;
        } finally {
            releaseRecorder();
        }

        isRecording = false;
        mainHandler.removeCallbacks(recordingTimeoutRunnable);
        voiceButton.setText(isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input");
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            reasonValue.setText(isChinese ? "\u6ca1\u6709\u5f55\u5230\u97f3\u9891" : "No audio recorded");
            return;
        }
        uploadAudioForCorrection(audioFile);
    }

    private void uploadAudioForCorrection(File audioFile) {
        String userId = userIdInput.getText().toString().trim();
        String backendUrl = backendUrlInput.getText().toString().trim();
        String context = contextSpinner.getSelectedItem().toString();

        if (TextUtils.isEmpty(backendUrl)) {
            backendUrlInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u540e\u7aef\u5730\u5740" : "Backend URL is required");
            deleteRecordingFile(audioFile);
            return;
        }
        if (TextUtils.isEmpty(userId)) {
            userIdInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u7528\u6237 ID" : "User ID is required");
            deleteRecordingFile(audioFile);
            return;
        }

        setLoading(true);
        reasonValue.setText(isChinese ? "\u4e0a\u4f20\u97f3\u9891\u5e76\u8bc6\u522b\u4e2d..." : "Uploading audio and recognizing...");
        CorrectionApiClient apiClient = new CorrectionApiClient(backendUrl);
        apiClient.correctAudio(audioFile, userId, context, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    rawTextInput.setText(response.rawText);
                    rawTextInput.setSelection(rawTextInput.length());
                    correctedTextValue.setText(response.correctedText);
                    matchedTermsValue.setText(response.matchedTerms.isEmpty()
                            ? "-"
                            : TextUtils.join(", ", response.matchedTerms));
                    reasonValue.setText(response.reason);
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = isChinese
                            ? "\u97f3\u9891\u8bf7\u6c42\u5931\u8d25\uff1a" + exception.getClass().getSimpleName() + "\n" + backendUrl
                            : "Audio request failed: " + exception.getClass().getSimpleName() + "\n" + backendUrl;
                    reasonValue.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    deleteRecordingFile(audioFile);
                });
            }
        });
    }

    private void submitCorrection() {
        String userId = userIdInput.getText().toString().trim();
        String backendUrl = backendUrlInput.getText().toString().trim();
        String rawText = rawTextInput.getText().toString().trim();
        String context = contextSpinner.getSelectedItem().toString();

        if (TextUtils.isEmpty(backendUrl)) {
            backendUrlInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u540e\u7aef\u5730\u5740" : "Backend URL is required");
            return;
        }
        if (TextUtils.isEmpty(userId)) {
            userIdInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u7528\u6237 ID" : "User ID is required");
            return;
        }
        if (TextUtils.isEmpty(rawText)) {
            rawTextInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u539f\u59cb\u6587\u672c" : "Raw text is required");
            return;
        }

        saveCurrentSettings();
        setLoading(true);
        TextCorrectionRequest request = new TextCorrectionRequest(userId, rawText, context);
        CorrectionApiClient apiClient = new CorrectionApiClient(backendUrl);
        apiClient.correctText(request, new CorrectionApiClient.Callback() {
            @Override
            public void onSuccess(TextCorrectionResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    correctedTextValue.setText(response.correctedText);
                    matchedTermsValue.setText(response.matchedTerms.isEmpty()
                            ? "-"
                            : TextUtils.join(", ", response.matchedTerms));
                    reasonValue.setText(response.reason);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = isChinese
                            ? "\u8bf7\u6c42\u5931\u8d25\uff1a" + exception.getClass().getSimpleName() + "\n" + backendUrl
                            : "Request failed: " + exception.getClass().getSimpleName() + "\n" + backendUrl;
                    reasonValue.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        correctButton.setEnabled(!isLoading);
        voiceButton.setEnabled(!isLoading);
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

    private void deleteRecordingFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private void saveCurrentSettings() {
        String context = contextSpinner.getSelectedItem() == null
                ? AppSettings.DEFAULT_APP_CONTEXT
                : contextSpinner.getSelectedItem().toString();
        AppSettings.save(
                this,
                backendUrlInput.getText().toString(),
                userIdInput.getText().toString(),
                context
        );
    }

}
