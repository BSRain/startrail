package com.startrail.app;

/** Shared application colors for normal and red-light display modes. */
public final class AppPalette {
    private AppPalette() {}

    public static int bg(boolean redMode) {
        return redMode ? 0xff14090c : 0xff080f1e;
    }

    public static int panel(boolean redMode) {
        return redMode ? 0xff251116 : 0xff111e30;
    }

    public static int line(boolean redMode) {
        return redMode ? 0xff51252f : 0xff26374b;
    }

    public static int ink(boolean redMode) {
        return redMode ? 0xffe1a0a5 : 0xffe8f0f9;
    }

    public static int muted(boolean redMode) {
        return redMode ? 0xffb56e77 : 0xff8fa5bc;
    }

    public static int accent(boolean redMode) {
        return redMode ? 0xffe68790 : 0xff9be4d3;
    }
}
