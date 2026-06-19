package com.osfans.trime.core;

public final class RimeKeyEvent {
    public final int keycode;
    public final int modifier;
    public final String repr;

    public RimeKeyEvent(int keycode, int modifier, String repr) {
        this.keycode = keycode;
        this.modifier = modifier;
        this.repr = repr == null ? "" : repr;
    }

    public static native RimeKeyEvent parse(String repr);

    public static native int getKeycodeByName(String name);

    public static native int getModifierByName(String name);
}
