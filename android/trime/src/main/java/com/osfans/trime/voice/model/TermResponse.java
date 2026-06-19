package com.osfans.trime.voice.model;

import java.util.List;

public class TermResponse {
    public final int id;
    public final String userId;
    public final String term;
    public final String category;
    public final List<String> aliases;
    public final double weight;
    public final String createdAt;

    public TermResponse(
            int id,
            String userId,
            String term,
            String category,
            List<String> aliases,
            double weight,
            String createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.term = term;
        this.category = category;
        this.aliases = aliases;
        this.weight = weight;
        this.createdAt = createdAt;
    }
}
