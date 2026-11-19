package stapel.input

/**
 * Character folding shared by every format: what a keyboard, an IME or a clipboard may hand over,
 * reduced to what a raw value holds.
 */
public object InputChars {
    /**
     * The ASCII digit for any Unicode decimal digit (`'٣'` Arabic-Indic, `'۳'` extended Arabic-Indic,
     * `'३'` Devanagari, `'３'` full-width …), or null when [c] is not a decimal digit.
     */
    public fun asciiDigit(c: Char): Char? {
        if (c in '0'..'9') return c
        if (!c.isDigit()) return null
        val value = c.digitToIntOrNull() ?: return null
        return '0' + value
    }

    /**
     * True for invisible formatting characters that travel with copied text and never carry meaning
     * in an input: bidirectional marks and isolates, zero-width spaces and joiners, the byte-order mark.
     */
    public fun isInvisible(c: Char): Boolean =
        c in '​'..'‏' || c in '‪'..'‮' || c in '⁦'..'⁩' ||
            c == '﻿' || c == '؜' || c == '­' || c == '⁠'

    /** True for any space, including no-break and narrow no-break spaces used as group separators. */
    public fun isSpace(c: Char): Boolean = c.isWhitespace() || c == ' ' || c == ' ' || c == ' '

    /** True for a hyphen or minus sign of any common form (`-`, `−`, `‐`, `‑`, `–`, `－`). */
    public fun isMinus(c: Char): Boolean =
        c == '-' || c == '−' || c == '‐' || c == '‑' || c == '–' || c == '－'

    /** True for a plus sign (`+`, `＋`). */
    public fun isPlus(c: Char): Boolean = c == '+' || c == '＋'

    /**
     * Shows ASCII digit [c] in the digit family whose zero is [zeroDigit] (e.g. `'٠'` for Arabic-Indic);
     * any other character, or a [zeroDigit] that is not a zero, leaves [c] as it is.
     */
    public fun shapeDigit(c: Char, zeroDigit: Char): Char {
        if (c !in '0'..'9' || zeroDigit == '0') return c
        if (asciiDigit(zeroDigit) != '0') return c
        val shaped = zeroDigit + (c - '0')
        return if (asciiDigit(shaped) == c) shaped else c
    }
}

/** [text] without invisible characters, digits folded to ASCII. */
internal fun foldInput(text: CharSequence): String {
    val out = StringBuilder(text.length)
    for (c in text) {
        if (InputChars.isInvisible(c)) continue
        out.append(InputChars.asciiDigit(c) ?: c)
    }
    return out.toString()
}

/** A safe substring: indexes are clamped and ordered, so no input can throw. */
internal fun String.cut(start: Int, end: Int): String {
    val s = start.coerceIn(0, length)
    val e = end.coerceIn(0, length)
    return if (s <= e) substring(s, e) else ""
}

/** Clamps an edit range onto [length]: ordered, inside the text. */
internal fun clampRange(start: Int, end: Int, length: Int): Pair<Int, Int> {
    val a = start.coerceIn(0, length)
    val b = end.coerceIn(0, length)
    return if (a <= b) a to b else b to a
}
