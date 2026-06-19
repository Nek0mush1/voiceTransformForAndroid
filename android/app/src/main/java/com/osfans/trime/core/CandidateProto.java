package com.osfans.trime.core;

public final class CandidateProto {
    public final String text;
    public final String comment;
    public final String label;

    public CandidateProto(String text, String comment, String label) {
        this.text = text == null ? "" : text;
        this.comment = comment;
        this.label = label == null ? "" : label;
    }
}
