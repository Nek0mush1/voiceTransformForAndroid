package com.osfans.trime.voice.model;

import java.util.List;

public class TextCorrectionResponse {
    public final String rawText;
    public final String correctedText;
    public final List<String> matchedTerms;
    public final String reason;
    public final String correctionMethod;
    public final boolean llmUsed;
    public final String llmError;
    public final String traceId;

    public TextCorrectionResponse(
            String rawText,
            String correctedText,
            List<String> matchedTerms,
            String reason,
            String correctionMethod,
            boolean llmUsed,
            String llmError,
            String traceId
    ) {
        this.rawText = rawText;
        this.correctedText = correctedText;
        this.matchedTerms = matchedTerms;
        this.reason = reason;
        this.correctionMethod = correctionMethod;
        this.llmUsed = llmUsed;
        this.llmError = llmError;
        this.traceId = traceId;
    }
}
