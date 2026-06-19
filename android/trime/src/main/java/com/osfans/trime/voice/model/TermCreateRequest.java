package com.osfans.trime.voice.model;

import java.util.List;

public class TermCreateRequest {
    public final String userId;
    public final String term;
    public final String category;
    public final List<String> aliases;
    public final double weight;

    public TermCreateRequest(String userId, String term, String category, List<String> aliases, double weight) {
        this.userId = userId;
        this.term = term;
        this.category = category;
        this.aliases = aliases;
        this.weight = weight;
    }
}
