package com.example.voicetransform.model;

public class LlmCallLogResponse {
    public final int id;
    public final String traceId;
    public final String userId;
    public final String rawText;
    public final String fallbackText;
    public final String outputText;
    public final boolean success;
    public final String error;
    public final String correctionMethod;
    public final String baseUrl;
    public final String model;
    public final String wireApi;
    public final int durationMs;
    public final String createdAt;

    public LlmCallLogResponse(
            int id,
            String traceId,
            String userId,
            String rawText,
            String fallbackText,
            String outputText,
            boolean success,
            String error,
            String correctionMethod,
            String baseUrl,
            String model,
            String wireApi,
            int durationMs,
            String createdAt
    ) {
        this.id = id;
        this.traceId = traceId;
        this.userId = userId;
        this.rawText = rawText;
        this.fallbackText = fallbackText;
        this.outputText = outputText;
        this.success = success;
        this.error = error;
        this.correctionMethod = correctionMethod;
        this.baseUrl = baseUrl;
        this.model = model;
        this.wireApi = wireApi;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }
}
