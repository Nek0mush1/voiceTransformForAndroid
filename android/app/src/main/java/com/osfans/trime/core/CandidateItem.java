package com.osfans.trime.core;

public final class CandidateItem {
    public final String text;
    public final String comment;

    public CandidateItem(String text, String comment) {
        this.text = text == null ? "" : text;
        this.comment = comment == null ? "" : comment;
    }
}
