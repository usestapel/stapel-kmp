package stapel.input

import kotlin.random.Random

/**
 * The laws every [InputFormat] keeps, checked over random edits. Returns the violations (empty =
 * lawful) instead of asserting, so the harness itself can be inversion-tested against broken formats.
 */
internal object FormatLaws {
    /** Characters a keyboard, IME or clipboard may deliver, deliberately hostile. */
    private val ALPHABET: List<String> = listOf(
        "0", "1", "2", "5", "7", "9", "٠", "٣", "٩", "۴", "३", "０", "９",
        "a", "Z", "ж", "ß", "ي", "中", "+", "＋", "-", "−", ".", ",", "٫", "/", "(", ")", " ", " ", " ",
        "‎", "‏", "‫", "⁦", "﻿", "​", "#", "*", "[", "]", "\\", "\n", "\t",
        "😀", "\uD83D", "\uDE00", "$", "€", "x", "e", "0", "0", "1",
    )

    fun garbage(random: Random, maxLength: Int = 24): String {
        val n = random.nextInt(maxLength + 1)
        val sb = StringBuilder()
        repeat(n) { sb.append(ALPHABET[random.nextInt(ALPHABET.size)]) }
        return sb.toString()
    }

    /** Runs [steps] random edits from an empty value and returns every law broken (with the step). */
    fun check(format: InputFormat, random: Random, steps: Int = 400): List<String> {
        val broken = ArrayList<String>()
        var raw = ""
        repeat(steps) { step ->
            val start = random.nextInt(-2, raw.length + 3)
            val end = random.nextInt(-2, raw.length + 3)
            val inserted = if (random.nextInt(4) == 0) "" else garbage(random, if (random.nextBoolean()) 2 else 24)
            val where = "step $step edit(\"$raw\", $start, $end, \"$inserted\")"
            val edit = try {
                format.edit(raw, start, end, inserted)
            } catch (e: Exception) {
                broken += "$where threw $e"
                return broken
            }
            val next = edit.raw
            if (edit.cursor !in 0..next.length) broken += "$where: cursor ${edit.cursor} outside 0..${next.length}"
            val f = try {
                format.format(next)
            } catch (e: Exception) {
                broken += "$where: format threw $e"
                return broken
            }
            if (f.raw != next) broken += "$where: not canonical, format(\"$next\").raw = \"${f.raw}\""
            val back = format.normalize(f.text)
            if (back != next) broken += "$where: round trip \"$next\" → \"${f.text}\" → \"$back\""
            if (rebuild(f) != f.text) broken += "$where: pieces do not rebuild \"${f.text}\""
            for (r in 0..next.length) {
                val d = f.rawToDisplay(r)
                if (f.displayToRaw(d) != r) broken += "$where: displayToRaw(rawToDisplay($r)) != $r in \"${f.text}\""
                if (parkedAfterLiteral(f, d)) broken += "$where: cursor $d inside a literal of \"${f.text}\""
            }
            for (d in -1..f.text.length + 1) {
                val s = f.snapCursor(d)
                if (parkedAfterLiteral(f, s)) broken += "$where: snap($d) = $s inside a literal of \"${f.text}\""
            }
            format.isComplete(next)
            format.isComplete(garbage(random))
            format.format(garbage(random))
            if (broken.isNotEmpty()) return broken
            raw = if (random.nextInt(25) == 0) "" else next
        }
        return broken
    }

    /** True when display offset [p] sits right after a literal (inside or at the far end of a literal run). */
    fun parkedAfterLiteral(f: Formatted, p: Int): Boolean = p > 0 && f.isLiteralAt(p - 1)

    /** The display rebuilt from the raw value and the pieces — what an output transformation does. */
    fun rebuild(f: Formatted): String {
        val sb = StringBuilder(f.raw)
        for (p in f.pieces.asReversed()) {
            when (p) {
                is FormatPiece.Insert -> sb.insert(p.rawOffset, p.text)
                is FormatPiece.Substitute -> sb[p.rawIndex] = p.shownAs
            }
        }
        return sb.toString()
    }
}
