package com.example.voicetransform.model;

public class ProfileResponse {
    public final String userId;
    public final String profileText;
    public final String updatedAt;

    public ProfileResponse(String userId, String profileText, String updatedAt) {
        this.userId = userId;
        this.profileText = profileText;
        this.updatedAt = updatedAt;
    }
}
