package com.example.voicetransform.model;

public class LlmConfigResponse {
    public final String baseUrl;
    public final String model;
    public final String wireApi;
    public final boolean configured;
    public final String apiKeyMasked;
    public final String updatedAt;

    public LlmConfigResponse(String baseUrl, String model, String wireApi, boolean configured, String apiKeyMasked, String updatedAt) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.wireApi = wireApi;
        this.configured = configured;
        this.apiKeyMasked = apiKeyMasked;
        this.updatedAt = updatedAt;
    }
}
