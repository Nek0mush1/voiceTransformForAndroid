package com.example.voicetransform;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import com.example.voicetransform.model.LlmCallLogResponse;
import com.example.voicetransform.model.LlmConfigResponse;
import com.example.voicetransform.model.LlmConfigTestResponse;
import com.example.voicetransform.model.ProfileResponse;
import com.example.voicetransform.model.TermCreateRequest;
import com.example.voicetransform.model.TermResponse;
import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    private static final long MAX_RECORDING_MS = 60000;
    private static final int SECTION_TEST = 0;
    private static final int SECTION_SETTINGS = 1;
    private static final int SECTION_PROFILE = 2;
    private static final int SECTION_TERMS = 3;
    private static final int SECTION_LLM = 4;
    private static final int SECTION_LOGS = 5;
    private static final String LLM_WIRE_API_RESPONSES = "responses";
    private static final String LLM_WIRE_API_CHAT_COMPLETIONS = "chat_completions";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTimeoutRunnable = this::stopRecordingAndUpload;

    private boolean isChinese = true;
    private boolean isRecording;
    private boolean isListening;
    private int activeSection = SECTION_TEST;
    private MediaRecorder mediaRecorder;
    private SpeechRecognizer speechRecognizer;
    private File recordingFile;
    private EditText backendUrlInput;
    private EditText userIdInput;
    private Spinner contextSpinner;
    private Spinner speechModeSpinner;
    private EditText profileInput;
    private EditText termInput;
    private EditText aliasesInput;
    private EditText categoryInput;
    private EditText weightInput;
    private EditText deleteTermIdInput;
    private EditText rawTextInput;
    private EditText llmBaseUrlInput;
    private EditText llmApiKeyInput;
    private EditText llmModelInput;
    private Spinner llmWireApiSpinner;
    private Button chineseButton;
    private Button englishButton;
    private Button testTabButton;
    private Button settingsTabButton;
    private Button profileTabButton;
    private Button termsTabButton;
    private Button llmTabButton;
    private Button logsTabButton;
    private Button voiceButton;
    private Button correctButton;
    private Button loadProfileButton;
    private Button saveProfileButton;
    private Button addTermButton;
    private Button refreshTermsButton;
    private Button deleteTermButton;
    private Button diagnoseBackendButton;
    private Button loadLlmButton;
    private Button saveLlmButton;
    private Button testLlmButton;
    private Button refreshLlmLogsButton;
    private ProgressBar progressBar;
    private View testSection;
    private View settingsSection;
    private View profileSection;
    private View termsSection;
    private View llmSection;
    private View logsSection;
    private TextView titleText;
    private TextView subtitleText;
    private TextView backendUrlLabel;
    private TextView userIdLabel;
    private TextView contextLabel;
    private TextView speechModeLabel;
    private TextView profileLabel;
    private TextView termLabel;
    private TextView llmBaseUrlLabel;
    private TextView llmApiKeyLabel;
    private TextView llmModelLabel;
    private TextView llmWireApiLabel;
    private TextView llmStatusValue;
    private TextView termsValue;
    private TextView rawTextLabel;
    private TextView correctedTextLabel;
    private TextView correctedTextValue;
    private TextView matchedTermsLabel;
    private TextView matchedTermsValue;
    private TextView correctionMethodLabel;
    private TextView correctionMethodValue;
    private TextView reasonLabel;
    private TextView reasonValue;
    private TextView backendDiagnosticValue;
    private TextView llmLogsValue;
    private TextView endpointHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupSpinners();
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
        speechModeLabel = findViewById(R.id.speechModeLabel);
        profileLabel = findViewById(R.id.profileLabel);
        termLabel = findViewById(R.id.termLabel);
        profileInput = findViewById(R.id.profileInput);
        termInput = findViewById(R.id.termInput);
        aliasesInput = findViewById(R.id.aliasesInput);
        categoryInput = findViewById(R.id.categoryInput);
        weightInput = findViewById(R.id.weightInput);
        deleteTermIdInput = findViewById(R.id.deleteTermIdInput);
        llmBaseUrlInput = findViewById(R.id.llmBaseUrlInput);
        llmApiKeyInput = findViewById(R.id.llmApiKeyInput);
        llmModelInput = findViewById(R.id.llmModelInput);
        llmWireApiSpinner = findViewById(R.id.llmWireApiSpinner);
        termsValue = findViewById(R.id.termsValue);
        rawTextLabel = findViewById(R.id.rawTextLabel);
        userIdInput = findViewById(R.id.userIdInput);
        contextSpinner = findViewById(R.id.contextSpinner);
        speechModeSpinner = findViewById(R.id.speechModeSpinner);
        rawTextInput = findViewById(R.id.rawTextInput);
        chineseButton = findViewById(R.id.chineseButton);
        englishButton = findViewById(R.id.englishButton);
        testTabButton = findViewById(R.id.testTabButton);
        settingsTabButton = findViewById(R.id.settingsTabButton);
        profileTabButton = findViewById(R.id.profileTabButton);
        termsTabButton = findViewById(R.id.termsTabButton);
        llmTabButton = findViewById(R.id.llmTabButton);
        logsTabButton = findViewById(R.id.logsTabButton);
        voiceButton = findViewById(R.id.voiceButton);
        correctButton = findViewById(R.id.correctButton);
        loadProfileButton = findViewById(R.id.loadProfileButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        addTermButton = findViewById(R.id.addTermButton);
        refreshTermsButton = findViewById(R.id.refreshTermsButton);
        deleteTermButton = findViewById(R.id.deleteTermButton);
        diagnoseBackendButton = findViewById(R.id.diagnoseBackendButton);
        loadLlmButton = findViewById(R.id.loadLlmButton);
        saveLlmButton = findViewById(R.id.saveLlmButton);
        testLlmButton = findViewById(R.id.testLlmButton);
        refreshLlmLogsButton = findViewById(R.id.refreshLlmLogsButton);
        progressBar = findViewById(R.id.progressBar);
        testSection = findViewById(R.id.testSection);
        settingsSection = findViewById(R.id.settingsSection);
        profileSection = findViewById(R.id.profileSection);
        termsSection = findViewById(R.id.termsSection);
        llmSection = findViewById(R.id.llmSection);
        logsSection = findViewById(R.id.logsSection);
        llmBaseUrlLabel = findViewById(R.id.llmBaseUrlLabel);
        llmApiKeyLabel = findViewById(R.id.llmApiKeyLabel);
        llmModelLabel = findViewById(R.id.llmModelLabel);
        llmWireApiLabel = findViewById(R.id.llmWireApiLabel);
        llmStatusValue = findViewById(R.id.llmStatusValue);
        correctedTextLabel = findViewById(R.id.correctedTextLabel);
        correctedTextValue = findViewById(R.id.correctedTextValue);
        matchedTermsLabel = findViewById(R.id.matchedTermsLabel);
        matchedTermsValue = findViewById(R.id.matchedTermsValue);
        correctionMethodLabel = findViewById(R.id.correctionMethodLabel);
        correctionMethodValue = findViewById(R.id.correctionMethodValue);
        reasonLabel = findViewById(R.id.reasonLabel);
        reasonValue = findViewById(R.id.reasonValue);
        backendDiagnosticValue = findViewById(R.id.backendDiagnosticValue);
        llmLogsValue = findViewById(R.id.llmLogsValue);
        endpointHint = findViewById(R.id.endpointHint);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> contextAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.context_values,
                android.R.layout.simple_spinner_item
        );
        contextAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contextSpinner.setAdapter(contextAdapter);

        ArrayAdapter<CharSequence> speechModeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.speech_mode_values,
                android.R.layout.simple_spinner_item
        );
        speechModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speechModeSpinner.setAdapter(speechModeAdapter);

        ArrayAdapter<CharSequence> llmWireApiAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.llm_wire_api_values,
                android.R.layout.simple_spinner_item
        );
        llmWireApiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        llmWireApiSpinner.setAdapter(llmWireApiAdapter);
        setSpinnerSelection(llmWireApiSpinner, LLM_WIRE_API_RESPONSES);
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
        String savedSpeechMode = AppSettings.getSpeechMode(this);
        for (int i = 0; i < speechModeSpinner.getCount(); i++) {
            if (savedSpeechMode.equals(speechModeSpinner.getItemAtPosition(i).toString())) {
                speechModeSpinner.setSelection(i);
                break;
            }
        }
        categoryInput.setText("course");
        weightInput.setText("1.0");
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
        testTabButton.setOnClickListener(view -> showSection(SECTION_TEST));
        settingsTabButton.setOnClickListener(view -> showSection(SECTION_SETTINGS));
        profileTabButton.setOnClickListener(view -> showSection(SECTION_PROFILE));
        termsTabButton.setOnClickListener(view -> showSection(SECTION_TERMS));
        llmTabButton.setOnClickListener(view -> showSection(SECTION_LLM));
        logsTabButton.setOnClickListener(view -> showSection(SECTION_LOGS));
        voiceButton.setOnClickListener(view -> toggleVoiceRecording());
        correctButton.setOnClickListener(view -> submitCorrection());
        loadProfileButton.setOnClickListener(view -> loadProfile());
        saveProfileButton.setOnClickListener(view -> saveProfile());
        addTermButton.setOnClickListener(view -> addTerm());
        refreshTermsButton.setOnClickListener(view -> loadTerms());
        deleteTermButton.setOnClickListener(view -> deleteTerm());
        diagnoseBackendButton.setOnClickListener(view -> diagnoseBackend());
        loadLlmButton.setOnClickListener(view -> loadLlmConfig());
        saveLlmButton.setOnClickListener(view -> saveLlmConfig());
        testLlmButton.setOnClickListener(view -> testLlmConfig());
        refreshLlmLogsButton.setOnClickListener(view -> loadLlmCallLogs());
        rawTextInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        showSection(activeSection);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentSettings();
    }

    @Override
    protected void onDestroy() {
        cancelRecording();
        destroySpeechRecognizer();
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
        speechModeLabel.setText(isChinese ? "\u8bed\u97f3\u8bc6\u522b\u6a21\u5f0f" : "Speech Mode");
        profileLabel.setText(isChinese ? "\u7528\u6237\u753b\u50cf" : "User Profile");
        testTabButton.setText(isChinese ? "\u6d4b\u8bd5" : "Test");
        settingsTabButton.setText(isChinese ? "\u8bbe\u7f6e" : "Settings");
        profileTabButton.setText(isChinese ? "\u753b\u50cf" : "Profile");
        termsTabButton.setText(isChinese ? "\u8bcd\u5e93" : "Terms");
        llmTabButton.setText(isChinese ? "LLM" : "LLM");
        logsTabButton.setText(isChinese ? "\u65e5\u5fd7" : "Logs");
        profileInput.setHint(isChinese ? "\u4f8b\u5982\uff1a\u8ba1\u7b97\u673a\u4e13\u4e1a\uff0c\u5b66\u4e60\u8ba1\u7ec4\u3001Agent \u5f00\u53d1" : "Example: CS student learning computer organization and Agent development");
        termLabel.setText(isChinese ? "\u4e13\u4e1a\u8bcd\u6761\uff08\u8bcd\u3001\u522b\u540d\u3001\u5206\u7c7b\u3001\u6743\u91cd\uff09" : "Term, aliases, category, weight");
        termInput.setHint(isChinese ? "\u8bcd\uff1a\u8ba1\u7ec4" : "Term: Cache");
        aliasesInput.setHint(isChinese ? "\u522b\u540d\uff0c\u9017\u53f7\u5206\u9694\uff1a\u796d\u7956,\u796d\u7956\u8bfe" : "Aliases, comma separated: cash,\u5feb\u53d6");
        categoryInput.setHint(isChinese ? "\u5206\u7c7b\uff1acourse" : "Category: course");
        weightInput.setHint(isChinese ? "\u6743\u91cd\uff1a1.0" : "Weight: 1.0");
        deleteTermIdInput.setHint(isChinese ? "\u8f93\u5165\u8981\u5220\u9664\u7684 ID" : "Term ID to delete");
        loadProfileButton.setText(isChinese ? "\u62c9\u53d6\u753b\u50cf" : "Load Profile");
        saveProfileButton.setText(isChinese ? "\u4fdd\u5b58\u753b\u50cf" : "Save Profile");
        addTermButton.setText(isChinese ? "\u65b0\u589e\u8bcd\u6761" : "Add Term");
        refreshTermsButton.setText(isChinese ? "\u5237\u65b0\u8bcd\u5e93" : "Refresh Terms");
        deleteTermButton.setText(isChinese ? "\u5220\u9664 ID" : "Delete ID");
        diagnoseBackendButton.setText(isChinese ? "\u8bca\u65ad\u540e\u7aef\u8fde\u63a5" : "Diagnose Backend");
        llmBaseUrlLabel.setText(isChinese ? "LLM \u4e2d\u8f6c\u7ad9\u5730\u5740" : "LLM Gateway URL");
        llmApiKeyLabel.setText(isChinese ? "LLM API Key" : "LLM API Key");
        llmModelLabel.setText(isChinese ? "LLM \u6a21\u578b" : "LLM Model");
        llmWireApiLabel.setText(isChinese ? "LLM \u63a5\u53e3\u683c\u5f0f" : "LLM Wire API");
        llmBaseUrlInput.setHint(isChinese ? "\u4f8b\u5982 https://api.example.com/v1" : "Example: https://api.example.com/v1");
        llmApiKeyInput.setHint(isChinese ? "\u7559\u7a7a\u8868\u793a\u4e0d\u8986\u76d6\u5df2\u4fdd\u5b58 Key" : "Leave empty to keep saved key");
        llmModelInput.setHint(isChinese ? "\u4f8b\u5982 gpt-5.5 / deepseek-chat" : "Example: gpt-5.5 / deepseek-chat");
        loadLlmButton.setText(isChinese ? "\u62c9\u53d6\u914d\u7f6e" : "Load Config");
        saveLlmButton.setText(isChinese ? "\u4fdd\u5b58\u914d\u7f6e" : "Save Config");
        testLlmButton.setText(isChinese ? "\u6d4b\u8bd5 LLM \u8fde\u63a5" : "Test LLM Connection");
        rawTextLabel.setText(isChinese ? "\u539f\u59cb\u6587\u672c" : "Raw Text");
        rawTextInput.setHint(isChinese ? "\u8f93\u5165\u8bed\u97f3\u8bc6\u522b\u540e\u7684\u6587\u672c" : "Enter recognized speech text");
        voiceButton.setText(isRecording
                ? (isChinese ? "\u505c\u6b62\u5f55\u97f3" : "Stop Recording")
                : isListening
                ? (isChinese ? "\u505c\u6b62\u8bc6\u522b" : "Stop Listening")
                : (isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input"));
        correctButton.setText(isChinese ? "\u5f00\u59cb\u7ea0\u9519" : "Correct");
        correctedTextLabel.setText(isChinese ? "\u7ea0\u9519\u7ed3\u679c" : "Corrected Text");
        matchedTermsLabel.setText(isChinese ? "\u547d\u4e2d\u672f\u8bed" : "Matched Terms");
        correctionMethodLabel.setText(isChinese ? "\u672c\u6b21\u65b9\u6cd5" : "Correction Method");
        reasonLabel.setText(isChinese ? "\u539f\u56e0" : "Reason");
        refreshLlmLogsButton.setText(isChinese ? "\u5237\u65b0 LLM \u8c03\u7528\u65e5\u5fd7" : "Refresh LLM Call Logs");
        endpointHint.setText(isChinese
                ? "\u9ed8\u8ba4\u4f7f\u7528\u4e91\u7aef\u670d\u52a1\u5668 http://39.106.51.35:8000\u3002Speech Mode=system \u4f7f\u7528\u624b\u673a\u7cfb\u7edf\u8bed\u97f3\uff1bbackend \u4f7f\u7528\u670d\u52a1\u5668\u4e0a\u7684\u767e\u5ea6\u4e91\u77ed\u8bed\u97f3\u8bc6\u522b ASR\u3002"
                : "Default cloud backend is http://39.106.51.35:8000. Speech Mode=system uses phone speech recognition; backend uses Baidu short speech ASR on the server.");
        chineseButton.setSelected(isChinese);
        englishButton.setSelected(!isChinese);

        if (TextUtils.isEmpty(termsValue.getText())) {
            termsValue.setText(isChinese ? "\u70b9\u51fb\u5237\u65b0\u8bcd\u5e93" : "Tap Refresh Terms");
        }
        if (TextUtils.isEmpty(correctedTextValue.getText())) {
            correctedTextValue.setText(isChinese ? "\u7b49\u5f85\u7ea0\u9519\u7ed3\u679c" : "Waiting for correction result");
        }
        if (TextUtils.isEmpty(matchedTermsValue.getText())) {
            matchedTermsValue.setText("-");
        }
        if (TextUtils.isEmpty(correctionMethodValue.getText())) {
            correctionMethodValue.setText(isChinese ? "\u7b49\u5f85\u7ea0\u9519" : "Waiting for correction");
        }
        if (TextUtils.isEmpty(reasonValue.getText())) {
            reasonValue.setText(isChinese ? "\u63d0\u4ea4\u540e\u663e\u793a\u539f\u56e0" : "Reason appears after submit");
        }
        if (TextUtils.isEmpty(backendDiagnosticValue.getText())) {
            backendDiagnosticValue.setText(isChinese
                    ? "\u70b9\u51fb\u8bca\u65ad\u540e\u7aef\u8fde\u63a5\uff0c\u533a\u5206\u7f51\u7edc\u3001ASR \u548c LLM \u914d\u7f6e\u95ee\u9898\u3002"
                    : "Tap Diagnose Backend to separate network, ASR, and LLM config issues.");
        }
        if (TextUtils.isEmpty(llmStatusValue.getText())) {
            llmStatusValue.setText(isChinese
                    ? "\u586b\u5199\u4e2d\u8f6c\u7ad9 /v1 \u5730\u5740\u3001API Key \u548c\u6a21\u578b\u540e\u4fdd\u5b58\u3002"
                    : "Enter gateway /v1 URL, API key, and model, then save.");
        }
        if (TextUtils.isEmpty(llmLogsValue.getText())) {
            llmLogsValue.setText(isChinese
                    ? "\u70b9\u51fb\u5237\u65b0\uff0c\u67e5\u770b\u6700\u8fd1 50 \u6761\u7ea0\u9519\u65f6\u7684 LLM \u8c03\u7528\u8bb0\u5f55\u3002"
                    : "Tap refresh to view the latest 50 LLM calls from correction requests.");
        }
        updateSectionVisibility();
    }

    private void toggleVoiceRecording() {
        if (isListening) {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
            reasonValue.setText(isChinese ? "\u6b63\u5728\u7ed3\u675f\u8bc6\u522b..." : "Finishing speech recognition...");
            return;
        }
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
        if (AppSettings.SPEECH_MODE_BACKEND.equals(getSelectedSpeechMode())) {
            startRecording();
            return;
        }
        startSystemSpeechRecognition();
    }

    private void startSystemSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reasonValue.setText(isChinese
                    ? "\u7cfb\u7edf\u8bed\u97f3\u8bc6\u522b\u4e0d\u53ef\u7528\u3002\u82e5\u8981\u7528\u540e\u7aef ASR\uff0c\u8bf7\u5148\u914d\u7f6e\u540e\u7aef ASR\u3002"
                    : "System speech recognition is unavailable. Configure backend ASR before using backend mode.");
            return;
        }

        ensureSpeechRecognizer();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            voiceButton.setText(isChinese ? "\u505c\u6b62\u8bc6\u522b" : "Stop Listening");
            reasonValue.setText(isChinese ? "\u6b63\u5728\u542c\u5199..." : "Listening...");
        } catch (Exception exception) {
            isListening = false;
            voiceButton.setText(isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input");
            reasonValue.setText((isChinese ? "\u7cfb\u7edf\u8bed\u97f3\u542f\u52a8\u5931\u8d25\uff1a" : "System speech failed: ")
                    + exception.getClass().getSimpleName());
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
                reasonValue.setText(isChinese ? "\u6b63\u5728\u542c\u5199..." : "Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {
                reasonValue.setText(isChinese ? "\u5df2\u542c\u5230\u58f0\u97f3..." : "Speech detected...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                finishSystemListening(isChinese ? "\u8bc6\u522b\u4e2d..." : "Recognizing...");
            }

            @Override
            public void onError(int error) {
                finishSystemListening((isChinese ? "\u8bed\u97f3\u8bc6\u522b\u5931\u8d25\uff1a" : "Speech recognition failed: ")
                        + speechErrorText(error));
            }

            @Override
            public void onResults(Bundle results) {
                finishSystemListening(isChinese ? "\u8bc6\u522b\u5b8c\u6210\uff0c\u6b63\u5728\u7ea0\u9519..." : "Recognized. Correcting...");
                String rawText = firstSpeechResult(results);
                if (TextUtils.isEmpty(rawText)) {
                    reasonValue.setText(isChinese ? "\u672a\u8bc6\u522b\u5230\u6587\u672c" : "No speech text recognized");
                    return;
                }
                rawTextInput.setText(rawText);
                rawTextInput.setSelection(rawTextInput.length());
                submitCorrection();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstSpeechResult(partialResults);
                if (!TextUtils.isEmpty(text)) {
                    rawTextInput.setText(text);
                    rawTextInput.setSelection(rawTextInput.length());
                    reasonValue.setText(isChinese ? "\u5df2\u542c\u5230\uff1a" + text : "Heard: " + text);
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

    private void finishSystemListening(String status) {
        isListening = false;
        voiceButton.setText(isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input");
        reasonValue.setText(status);
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
                    showCorrectionResponse(response);
                    deleteRecordingFile(audioFile);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = isChinese
                            ? "\u540e\u7aef ASR \u5931\u8d25\uff1a" + formatException(exception)
                            + "\n\u8bf7\u786e\u8ba4 Speech Mode=backend \u65f6\u5df2\u914d\u7f6e ASR\uff0c\u4e14\u624b\u673a\u80fd\u8bbf\u95ee\uff1a" + backendUrl
                            : "Backend ASR failed: " + formatException(exception)
                            + "\nConfirm backend ASR is configured and the phone can reach: " + backendUrl;
                    reasonValue.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    deleteRecordingFile(audioFile);
                });
            }
        });
    }

    private void loadProfile() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String userId = userIdInput.getText().toString().trim();
        if (!validateBackendAndUser(backendUrl, userId)) {
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).getProfile(userId, new CorrectionApiClient.ProfileCallback() {
            @Override
            public void onSuccess(ProfileResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    profileInput.setText(response.profileText);
                    profileInput.setSelection(profileInput.length());
                    reasonValue.setText(isChinese ? "\u5df2\u62c9\u53d6\u7528\u6237\u753b\u50cf" : "Profile loaded");
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showRequestError(isChinese ? "\u62c9\u53d6\u753b\u50cf\u5931\u8d25" : "Load profile failed", backendUrl, exception));
            }
        });
    }

    private void saveProfile() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String userId = userIdInput.getText().toString().trim();
        String profileText = profileInput.getText().toString().trim();
        if (!validateBackendAndUser(backendUrl, userId)) {
            return;
        }
        if (TextUtils.isEmpty(profileText)) {
            profileInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u7528\u6237\u753b\u50cf" : "Profile is required");
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).updateProfile(userId, profileText, new CorrectionApiClient.ProfileCallback() {
            @Override
            public void onSuccess(ProfileResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    profileInput.setText(response.profileText);
                    reasonValue.setText(isChinese ? "\u7528\u6237\u753b\u50cf\u5df2\u4fdd\u5b58" : "Profile saved");
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showRequestError(isChinese ? "\u4fdd\u5b58\u753b\u50cf\u5931\u8d25" : "Save profile failed", backendUrl, exception));
            }
        });
    }

    private void loadTerms() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String userId = userIdInput.getText().toString().trim();
        if (!validateBackendAndUser(backendUrl, userId)) {
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).listTerms(userId, new CorrectionApiClient.TermsCallback() {
            @Override
            public void onSuccess(List<TermResponse> response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    termsValue.setText(formatTerms(response));
                    reasonValue.setText(isChinese ? "\u8bcd\u5e93\u5df2\u5237\u65b0" : "Terms refreshed");
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showRequestError(isChinese ? "\u5237\u65b0\u8bcd\u5e93\u5931\u8d25" : "Refresh terms failed", backendUrl, exception));
            }
        });
    }

    private void addTerm() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String userId = userIdInput.getText().toString().trim();
        String term = termInput.getText().toString().trim();
        if (!validateBackendAndUser(backendUrl, userId)) {
            return;
        }
        if (TextUtils.isEmpty(term)) {
            termInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u8bcd\u6761" : "Term is required");
            return;
        }

        String category = categoryInput.getText().toString().trim();
        double weight = parseWeight();
        TermCreateRequest request = new TermCreateRequest(
                userId,
                term,
                category,
                parseAliases(aliasesInput.getText().toString()),
                weight
        );

        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).createTerm(request, new CorrectionApiClient.TermCallback() {
            @Override
            public void onSuccess(TermResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    reasonValue.setText((isChinese ? "\u8bcd\u6761\u5df2\u4fdd\u5b58\uff1a" : "Term saved: ") + response.term);
                    termInput.setText("");
                    aliasesInput.setText("");
                    loadTerms();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showRequestError(isChinese ? "\u65b0\u589e\u8bcd\u6761\u5931\u8d25" : "Add term failed", backendUrl, exception));
            }
        });
    }

    private void deleteTerm() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String userId = userIdInput.getText().toString().trim();
        String idText = deleteTermIdInput.getText().toString().trim();
        if (!validateBackendAndUser(backendUrl, userId)) {
            return;
        }
        if (TextUtils.isEmpty(idText)) {
            deleteTermIdInput.setError(isChinese ? "\u8bf7\u8f93\u5165 ID" : "ID is required");
            return;
        }

        int termId;
        try {
            termId = Integer.parseInt(idText);
        } catch (NumberFormatException exception) {
            deleteTermIdInput.setError(isChinese ? "ID \u5fc5\u987b\u662f\u6570\u5b57" : "ID must be a number");
            return;
        }

        setLoading(true);
        new CorrectionApiClient(backendUrl).deleteTerm(termId, new CorrectionApiClient.EmptyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setLoading(false);
                    deleteTermIdInput.setText("");
                    reasonValue.setText(isChinese ? "\u8bcd\u6761\u5df2\u5220\u9664" : "Term deleted");
                    loadTerms();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showRequestError(isChinese ? "\u5220\u9664\u8bcd\u6761\u5931\u8d25" : "Delete term failed", backendUrl, exception));
            }
        });
    }

    private void submitCorrection() {
        String userId = userIdInput.getText().toString().trim();
        String backendUrl = backendUrlInput.getText().toString().trim();
        String rawText = rawTextInput.getText().toString().trim();
        String context = contextSpinner.getSelectedItem().toString();

        if (!validateBackendAndUser(backendUrl, userId)) {
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
                    showCorrectionResponse(response);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showRequestError(isChinese ? "\u8bf7\u6c42\u5931\u8d25" : "Request failed", backendUrl, exception);
                });
            }
        });
    }

    private void diagnoseBackend() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        if (!validateBackendUrl(backendUrl)) {
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        backendDiagnosticValue.setText(isChinese ? "\u8bca\u65ad\u4e2d..." : "Diagnosing...");
        new CorrectionApiClient(backendUrl).diagnoseBackend(new CorrectionApiClient.DiagnosticCallback() {
            @Override
            public void onSuccess(String healthText, String statusText) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = (isChinese ? "\u540e\u7aef\u53ef\u8bbf\u95ee" : "Backend reachable")
                            + "\n/health: " + healthText
                            + "\n/api/v1/debug/status: " + statusText;
                    backendDiagnosticValue.setText(message);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = (isChinese ? "\u540e\u7aef\u8bca\u65ad\u5931\u8d25\uff1a" : "Backend diagnosis failed: ")
                            + formatException(exception)
                            + "\n" + backendUrl
                            + (isChinese
                            ? "\n\u8bf7\u68c0\u67e5\u4e91\u670d\u52a1\u5668\u8fdb\u7a0b\u662f\u5426\u8fd0\u884c\u3001\u963f\u91cc\u4e91\u5b89\u5168\u7ec4\u662f\u5426\u653e\u884c 8000\uff0c\u4ee5\u53ca\u540e\u7aef\u5730\u5740\u662f\u5426\u4e3a http://39.106.51.35:8000\u3002"
                            : "\nCheck the cloud backend process, security group port 8000, and backend URL http://39.106.51.35:8000.");
                    backendDiagnosticValue.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadLlmConfig() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        if (!validateBackendUrl(backendUrl)) {
            showSection(SECTION_SETTINGS);
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).getLlmConfig(new CorrectionApiClient.LlmConfigCallback() {
            @Override
            public void onSuccess(LlmConfigResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    llmBaseUrlInput.setText(response.baseUrl);
                    llmModelInput.setText(response.model);
                    setSpinnerSelection(llmWireApiSpinner, normalizeWireApi(response.wireApi));
                    llmApiKeyInput.setText("");
                    llmStatusValue.setText(formatLlmStatus(response));
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showLlmRequestError(isChinese ? "\u62c9\u53d6 LLM \u914d\u7f6e\u5931\u8d25" : "Load LLM config failed", backendUrl, exception));
            }
        });
    }

    private void saveLlmConfig() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        String llmBaseUrl = llmBaseUrlInput.getText().toString().trim();
        String apiKey = llmApiKeyInput.getText().toString().trim();
        String model = llmModelInput.getText().toString().trim();
        String wireApi = getSelectedLlmWireApi();
        if (!validateBackendUrl(backendUrl)) {
            showSection(SECTION_SETTINGS);
            return;
        }
        if (TextUtils.isEmpty(llmBaseUrl)) {
            llmBaseUrlInput.setError(isChinese ? "\u8bf7\u8f93\u5165 LLM \u5730\u5740" : "LLM URL is required");
            return;
        }
        if (TextUtils.isEmpty(model)) {
            llmModelInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u6a21\u578b" : "Model is required");
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        new CorrectionApiClient(backendUrl).updateLlmConfig(llmBaseUrl, apiKey, model, wireApi, new CorrectionApiClient.LlmConfigCallback() {
            @Override
            public void onSuccess(LlmConfigResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    llmApiKeyInput.setText("");
                    setSpinnerSelection(llmWireApiSpinner, normalizeWireApi(response.wireApi));
                    llmStatusValue.setText(formatLlmStatus(response));
                    Toast.makeText(MainActivity.this, isChinese ? "LLM \u914d\u7f6e\u5df2\u4fdd\u5b58" : "LLM config saved", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showLlmRequestError(isChinese ? "\u4fdd\u5b58 LLM \u914d\u7f6e\u5931\u8d25" : "Save LLM config failed", backendUrl, exception));
            }
        });
    }

    private void testLlmConfig() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        if (!validateBackendUrl(backendUrl)) {
            showSection(SECTION_SETTINGS);
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        llmStatusValue.setText(isChinese ? "\u6b63\u5728\u6d4b\u8bd5 LLM \u8fde\u63a5..." : "Testing LLM connection...");
        new CorrectionApiClient(backendUrl).testLlmConfig(new CorrectionApiClient.LlmConfigTestCallback() {
            @Override
            public void onSuccess(LlmConfigTestResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    llmStatusValue.setText(formatLlmTestStatus(response));
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> showLlmRequestError(isChinese ? "\u6d4b\u8bd5 LLM \u8fde\u63a5\u5931\u8d25" : "Test LLM connection failed", backendUrl, exception));
            }
        });
    }

    private void loadLlmCallLogs() {
        String backendUrl = backendUrlInput.getText().toString().trim();
        if (!validateBackendUrl(backendUrl)) {
            showSection(SECTION_SETTINGS);
            return;
        }
        saveCurrentSettings();
        setLoading(true);
        llmLogsValue.setText(isChinese ? "\u6b63\u5728\u62c9\u53d6 LLM \u8c03\u7528\u65e5\u5fd7..." : "Loading LLM call logs...");
        new CorrectionApiClient(backendUrl).listLlmCallLogs(new CorrectionApiClient.LlmCallLogsCallback() {
            @Override
            public void onSuccess(List<LlmCallLogResponse> response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    llmLogsValue.setText(formatLlmCallLogs(response));
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = (isChinese ? "\u62c9\u53d6 LLM \u65e5\u5fd7\u5931\u8d25\uff1a" : "Load LLM logs failed: ")
                            + formatException(exception)
                            + "\n" + backendUrl;
                    llmLogsValue.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showCorrectionResponse(TextCorrectionResponse response) {
        correctedTextValue.setText(response.correctedText);
        matchedTermsValue.setText(response.matchedTerms.isEmpty()
                ? "-"
                : TextUtils.join(", ", response.matchedTerms));
        correctionMethodValue.setText(formatCorrectionMethod(response));
        reasonValue.setText(response.reason);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        correctButton.setEnabled(!isLoading);
        voiceButton.setEnabled(isListening || !isLoading);
        loadProfileButton.setEnabled(!isLoading);
        saveProfileButton.setEnabled(!isLoading);
        addTermButton.setEnabled(!isLoading);
        refreshTermsButton.setEnabled(!isLoading);
        deleteTermButton.setEnabled(!isLoading);
        diagnoseBackendButton.setEnabled(!isLoading);
        loadLlmButton.setEnabled(!isLoading);
        saveLlmButton.setEnabled(!isLoading);
        testLlmButton.setEnabled(!isLoading);
        refreshLlmLogsButton.setEnabled(!isLoading);
        testTabButton.setEnabled(!isLoading);
        settingsTabButton.setEnabled(!isLoading);
        profileTabButton.setEnabled(!isLoading);
        termsTabButton.setEnabled(!isLoading);
        llmTabButton.setEnabled(!isLoading);
        logsTabButton.setEnabled(!isLoading);
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

    private void cancelSpeechRecognition() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
        }
        isListening = false;
        if (voiceButton != null) {
            voiceButton.setText(isChinese ? "\u8bed\u97f3\u8f93\u5165" : "Voice Input");
        }
    }

    private void destroySpeechRecognizer() {
        cancelSpeechRecognition();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
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
                context,
                getSelectedSpeechMode()
        );
    }

    private String getSelectedSpeechMode() {
        return speechModeSpinner.getSelectedItem() == null
                ? AppSettings.DEFAULT_SPEECH_MODE
                : speechModeSpinner.getSelectedItem().toString();
    }

    private boolean validateBackendAndUser(String backendUrl, String userId) {
        if (!validateBackendUrl(backendUrl)) {
            return false;
        }
        if (TextUtils.isEmpty(userId)) {
            userIdInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u7528\u6237 ID" : "User ID is required");
            return false;
        }
        return true;
    }

    private boolean validateBackendUrl(String backendUrl) {
        if (TextUtils.isEmpty(backendUrl)) {
            backendUrlInput.setError(isChinese ? "\u8bf7\u8f93\u5165\u540e\u7aef\u5730\u5740" : "Backend URL is required");
            return false;
        }
        return true;
    }

    private void showSection(int section) {
        activeSection = section;
        updateSectionVisibility();
    }

    private void updateSectionVisibility() {
        testSection.setVisibility(activeSection == SECTION_TEST ? View.VISIBLE : View.GONE);
        settingsSection.setVisibility(activeSection == SECTION_SETTINGS ? View.VISIBLE : View.GONE);
        profileSection.setVisibility(activeSection == SECTION_PROFILE ? View.VISIBLE : View.GONE);
        termsSection.setVisibility(activeSection == SECTION_TERMS ? View.VISIBLE : View.GONE);
        llmSection.setVisibility(activeSection == SECTION_LLM ? View.VISIBLE : View.GONE);
        logsSection.setVisibility(activeSection == SECTION_LOGS ? View.VISIBLE : View.GONE);

        testTabButton.setSelected(activeSection == SECTION_TEST);
        settingsTabButton.setSelected(activeSection == SECTION_SETTINGS);
        profileTabButton.setSelected(activeSection == SECTION_PROFILE);
        termsTabButton.setSelected(activeSection == SECTION_TERMS);
        llmTabButton.setSelected(activeSection == SECTION_LLM);
        logsTabButton.setSelected(activeSection == SECTION_LOGS);
    }

    private List<String> parseAliases(String aliasesText) {
        List<String> aliases = new ArrayList<>();
        if (TextUtils.isEmpty(aliasesText)) {
            return aliases;
        }
        String[] pieces = aliasesText.split("[,\\uFF0C]");
        for (String piece : pieces) {
            String alias = piece.trim();
            if (!TextUtils.isEmpty(alias)) {
                aliases.add(alias);
            }
        }
        return aliases;
    }

    private double parseWeight() {
        String weightText = weightInput.getText().toString().trim();
        if (TextUtils.isEmpty(weightText)) {
            return 1.0;
        }
        try {
            return Double.parseDouble(weightText);
        } catch (NumberFormatException exception) {
            return 1.0;
        }
    }

    private String formatTerms(List<TermResponse> terms) {
        if (terms.isEmpty()) {
            return isChinese ? "\u6682\u65e0\u8bcd\u6761" : "No terms";
        }
        StringBuilder builder = new StringBuilder();
        for (TermResponse term : terms) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append('#').append(term.id)
                    .append("  ")
                    .append(term.term);
            if (!TextUtils.isEmpty(term.category)) {
                builder.append("  [").append(term.category).append(']');
            }
            if (!term.aliases.isEmpty()) {
                builder.append("  aliases: ").append(TextUtils.join(", ", term.aliases));
            }
        }
        return builder.toString();
    }

    private String getSelectedLlmWireApi() {
        Object selected = llmWireApiSpinner.getSelectedItem();
        return normalizeWireApi(selected == null ? LLM_WIRE_API_RESPONSES : selected.toString());
    }

    private String normalizeWireApi(String wireApi) {
        if (LLM_WIRE_API_CHAT_COMPLETIONS.equals(wireApi)) {
            return LLM_WIRE_API_CHAT_COMPLETIONS;
        }
        return LLM_WIRE_API_RESPONSES;
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (spinner == null || value == null) {
            return;
        }
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String formatLlmStatus(LlmConfigResponse response) {
        String configuredText = response.configured
                ? (isChinese ? "\u5df2\u914d\u7f6e" : "configured")
                : (isChinese ? "\u672a\u5b8c\u6574\u914d\u7f6e" : "not fully configured");
        String keyText = TextUtils.isEmpty(response.apiKeyMasked)
                ? "-"
                : response.apiKeyMasked;
        String updatedText = TextUtils.isEmpty(response.updatedAt)
                ? "-"
                : response.updatedAt;
        if (isChinese) {
            return "\u72b6\u6001: " + configuredText
                    + "\n\u5730\u5740: " + emptyToDash(response.baseUrl)
                    + "\n\u6a21\u578b: " + emptyToDash(response.model)
                    + "\n\u63a5\u53e3: " + emptyToDash(response.wireApi)
                    + "\nKey: " + keyText
                    + "\n\u66f4\u65b0\u65f6\u95f4: " + updatedText;
        }
        return "Status: " + configuredText
                + "\nURL: " + emptyToDash(response.baseUrl)
                + "\nModel: " + emptyToDash(response.model)
                + "\nWire API: " + emptyToDash(response.wireApi)
                + "\nKey: " + keyText
                + "\nUpdated: " + updatedText;
    }

    private String formatLlmTestStatus(LlmConfigTestResponse response) {
        String status = response.success
                ? (isChinese ? "\u6d4b\u8bd5\u6210\u529f" : "Test succeeded")
                : (isChinese ? "\u6d4b\u8bd5\u5931\u8d25" : "Test failed");
        String output = TextUtils.isEmpty(response.sampleOutput) ? "-" : response.sampleOutput;
        if (isChinese) {
            return status + "\n\u6d88\u606f: " + response.message + "\n\u8f93\u51fa: " + output;
        }
        return status + "\nMessage: " + response.message + "\nOutput: " + output;
    }

    private String formatCorrectionMethod(TextCorrectionResponse response) {
        String methodText = methodLabel(response.correctionMethod);
        StringBuilder builder = new StringBuilder();
        if (response.llmUsed) {
            builder.append(isChinese ? "LLM \u5df2\u8c03\u7528\u5e76\u7528\u4e8e\u672c\u6b21\u7ed3\u679c" : "LLM was called and used for this result");
        } else {
            builder.append(isChinese ? "\u672c\u6b21\u672a\u4f7f\u7528 LLM \u7ed3\u679c" : "LLM result was not used this time");
        }
        builder.append("\n").append(isChinese ? "\u65b9\u6cd5: " : "Method: ").append(methodText);
        if (!TextUtils.isEmpty(response.llmError)) {
            builder.append("\n").append(isChinese ? "LLM \u9519\u8bef: " : "LLM error: ").append(response.llmError);
        }
        if (!TextUtils.isEmpty(response.traceId)) {
            builder.append("\ntrace_id: ").append(response.traceId);
        }
        return builder.toString();
    }

    private String formatLlmCallLogs(List<LlmCallLogResponse> logs) {
        if (logs.isEmpty()) {
            return isChinese
                    ? "\u6682\u65e0 LLM \u8c03\u7528\u65e5\u5fd7\u3002\u63d0\u4ea4\u4e00\u6b21\u7ea0\u9519\u540e\u518d\u5237\u65b0\u3002"
                    : "No LLM call logs yet. Submit a correction, then refresh.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(isChinese ? "\u6700\u8fd1 " : "Latest ")
                .append(logs.size())
                .append(isChinese ? " \u6761\uff08\u6700\u591a\u4fdd\u7559 50 \u6761\uff09" : " entries (max 50 retained)");
        for (LlmCallLogResponse log : logs) {
            builder.append("\n\n#").append(log.id)
                    .append("  ")
                    .append(log.success ? (isChinese ? "\u6210\u529f" : "success") : (isChinese ? "\u5931\u8d25" : "failed"))
                    .append("  ")
                    .append(methodLabel(log.correctionMethod));
            builder.append("\n").append(log.createdAt)
                    .append("  ").append(log.durationMs).append("ms");
            builder.append("\nmodel: ").append(emptyToDash(log.model))
                    .append("  wire: ").append(emptyToDash(log.wireApi));
            builder.append("\nraw: ").append(shorten(log.rawText, 80));
            builder.append("\nfallback: ").append(shorten(log.fallbackText, 80));
            builder.append("\noutput: ").append(shorten(log.outputText, 80));
            if (!TextUtils.isEmpty(log.error)) {
                builder.append("\nerror: ").append(shorten(log.error, 120));
            }
            if (!TextUtils.isEmpty(log.traceId)) {
                builder.append("\ntrace_id: ").append(log.traceId);
            }
        }
        return builder.toString();
    }

    private String methodLabel(String method) {
        if ("llm".equals(method)) {
            return isChinese ? "LLM \u7ea0\u9519" : "LLM correction";
        }
        if ("rule_pinyin_fallback".equals(method)) {
            return isChinese ? "\u89c4\u5219/\u62fc\u97f3 fallback" : "Rule/pinyin fallback";
        }
        if ("raw_text".equals(method)) {
            return isChinese ? "\u4fdd\u7559\u539f\u6587" : "Raw text kept";
        }
        return TextUtils.isEmpty(method) ? "unknown" : method;
    }

    private String shorten(String value, int maxLength) {
        if (TextUtils.isEmpty(value)) {
            return "-";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, Math.max(0, maxLength));
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String emptyToDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private String speechErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return isChinese ? "\u97f3\u9891\u9519\u8bef" : "audio error";
            case SpeechRecognizer.ERROR_CLIENT:
                return isChinese ? "\u5ba2\u6237\u7aef\u9519\u8bef" : "client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return isChinese ? "\u9ea6\u514b\u98ce\u6743\u9650\u4e0d\u8db3" : "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return isChinese ? "\u7f51\u7edc\u9519\u8bef" : "network error";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return isChinese ? "\u672a\u8bc6\u522b\u5230\u5185\u5bb9" : "no match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return isChinese ? "\u8bc6\u522b\u670d\u52a1\u5fd9" : "recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:
                return isChinese ? "\u8bed\u97f3\u670d\u52a1\u9519\u8bef" : "speech service error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return isChinese ? "\u6ca1\u6709\u68c0\u6d4b\u5230\u8bed\u97f3" : "no speech detected";
            default:
                return "error " + error;
        }
    }

    private String formatException(Exception exception) {
        String message = exception.getMessage();
        if (TextUtils.isEmpty(message)) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private void showRequestError(String prefix, String backendUrl, Exception exception) {
        setLoading(false);
        String message = prefix + ": " + formatException(exception) + "\n" + backendUrl;
        if (exception instanceof java.net.SocketTimeoutException) {
            message += isChinese
                    ? "\n\u6392\u67e5\uff1a\u6d4f\u89c8\u5668\u6253\u5f00 http://39.106.51.35:8000/health\uff1b\u786e\u8ba4\u4e91\u670d\u52a1\u5668\u8fdb\u7a0b\u548c 8000 \u7aef\u53e3\u653e\u884c\u3002"
                    : "\nOpen http://39.106.51.35:8000/health and check the cloud process and port 8000.";
        }
        reasonValue.setText(message);
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private void showLlmRequestError(String prefix, String backendUrl, Exception exception) {
        setLoading(false);
        String message = prefix + ": " + formatException(exception) + "\n" + backendUrl;
        llmStatusValue.setText(message);
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
    }

}
