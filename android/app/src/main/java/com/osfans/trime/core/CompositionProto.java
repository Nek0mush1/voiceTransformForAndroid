package com.osfans.trime.core;

public final class CompositionProto {
    public final int length;
    public final int cursorPos;
    public final int selStart;
    public final int selEnd;
    public final String preedit;
    public final String commitTextPreview;

    public CompositionProto(
            int length,
            int cursorPos,
            int selStart,
            int selEnd,
            String preedit,
            String commitTextPreview
    ) {
        this.length = length;
        this.cursorPos = cursorPos;
        this.selStart = selStart;
        this.selEnd = selEnd;
        this.preedit = preedit;
        this.commitTextPreview = commitTextPreview;
    }
}
