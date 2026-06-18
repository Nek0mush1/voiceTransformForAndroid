package com.example.voicetransform.model;

public class TextCorrectionRequest {
    public final String userId;
    public final String rawText;
    public final String appContext;

    public TextCorrectionRequest(String userId, String rawText, String appContext) {
        this.userId = userId;
        this.rawText = rawText;
        this.appContext = appContext;
    }
}
