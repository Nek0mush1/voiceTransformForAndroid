package com.osfans.trime.core;

public final class MenuProto {
    public final int pageSize;
    public final int pageNumber;
    public final boolean isLastPage;
    public final int highlightedCandidateIndex;
    public final CandidateProto[] candidates;
    public final String selectKeys;
    public final String[] selectLabels;

    public MenuProto(
            int pageSize,
            int pageNumber,
            boolean isLastPage,
            int highlightedCandidateIndex,
            CandidateProto[] candidates,
            String selectKeys,
            String[] selectLabels
    ) {
        this.pageSize = pageSize;
        this.pageNumber = pageNumber;
        this.isLastPage = isLastPage;
        this.highlightedCandidateIndex = highlightedCandidateIndex;
        this.candidates = candidates == null ? new CandidateProto[0] : candidates;
        this.selectKeys = selectKeys == null ? "" : selectKeys;
        this.selectLabels = selectLabels == null ? new String[0] : selectLabels;
    }
}
