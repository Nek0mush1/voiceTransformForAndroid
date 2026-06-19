package com.osfans.trime.voice.model;

public class LlmConfigTestResponse {
    public final boolean success;
    public final String message;
    public final String sampleOutput;

    public LlmConfigTestResponse(boolean success, String message, String sampleOutput) {
        this.success = success;
        this.message = message;
        this.sampleOutput = sampleOutput;
    }
}
