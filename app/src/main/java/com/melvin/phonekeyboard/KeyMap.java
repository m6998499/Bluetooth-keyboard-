package com.melvin.phonekeyboard;

/** USB HID key codes (US layout). */
public final class KeyMap {
    private KeyMap() { }

    public static final int CTRL = 0x01, SHIFT = 0x02, ALT = 0x04, WIN = 0x08;

    public static final int ENTER = 0x28, ESC = 0x29, BACKSPACE = 0x2A, TAB = 0x2B, SPACE = 0x2C;
    public static final int DELETE = 0x4C, HOME = 0x4A, END = 0x4D, PAGE_UP = 0x4B, PAGE_DOWN = 0x4E;
    public static final int RIGHT = 0x4F, LEFT = 0x50, DOWN = 0x51, UP = 0x52;
    public static final int F1 = 0x3A; // F1..F12 = 0x3A..0x45

    private static final String UNSHIFTED = "-=[]\\;',./`";
    private static final String SHIFTED   = "_+{}|:\"<>?~";
    private static final int[] PUNCT_CODES = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x38, 0x35};
    private static final String SHIFTED_DIGITS = "!@#$%^&*()"; // 1..0

    /** Returns {modifiers, keyCode} for a character, or null if it can't be typed. */
    public static int[] forChar(char c) {
        // Map smart punctuation from phone keyboards to plain ASCII
        switch (c) {
            case '‘': case '’': c = '\''; break;
            case '“': case '”': c = '"'; break;
            case '–': case '—': c = '-'; break;
            case ' ': c = ' '; break;
        }
        if (c >= 'a' && c <= 'z') return new int[]{0, 0x04 + (c - 'a')};
        if (c >= 'A' && c <= 'Z') return new int[]{SHIFT, 0x04 + (c - 'A')};
        if (c >= '1' && c <= '9') return new int[]{0, 0x1E + (c - '1')};
        if (c == '0') return new int[]{0, 0x27};
        if (c == ' ') return new int[]{0, SPACE};
        if (c == '\n') return new int[]{0, ENTER};
        if (c == '\t') return new int[]{0, TAB};
        int i = SHIFTED_DIGITS.indexOf(c);
        if (i >= 0) return new int[]{SHIFT, i == 9 ? 0x27 : 0x1E + i};
        i = UNSHIFTED.indexOf(c);
        if (i >= 0) return new int[]{0, PUNCT_CODES[i]};
        i = SHIFTED.indexOf(c);
        if (i >= 0) return new int[]{SHIFT, PUNCT_CODES[i]};
        return null;
    }
}
