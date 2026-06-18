package com.example.voicetransform.model;

import java.util.List;

public class TextCorrectionResponse {
    public final String rawText;
    public final String correctedText;
    public final List<String> matchedTerms;
    public final String reason;

    public TextCorrectionResponse(String rawText, String correctedText, List<String> matchedTerms, String reason) {
        this.rawText = rawText;
        this.correctedText = correctedText;
        this.matchedTerms = matchedTerms;
        this.reason = reason;
    }
}
