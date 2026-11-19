package stapel.input

/**
 * Region knowledge for phone entry, supplied by the app or an optional artifact.
 *
 * This artifact ships **no** country tables: [GenericPhoneMetadata] knows nothing about any region.
 * A full provider (for example one generated from the public numbering-plan metadata an app chooses to
 * bundle) needs, per country calling code: the code itself and its length (the prefix tree of the
 * ITU-T E.164 assignments), the national grouping patterns keyed by leading digits, the possible
 * national lengths, the national trunk prefix and how a nationally dialled number becomes E.164
 * ([nationalToE164]), and the international dialling prefixes used in that region.
 */
public interface PhoneMetadata {
    /** Length of the country calling code at the start of [digits] (E.164 digits, no `+`), or null if unknown. */
    public fun callingCodeLength(digits: String): Int?

    /**
     * Group sizes of the national significant number [national] after [callingCode], or null to use the
     * generic grouping. Sizes may sum to less than the national length (the rest is one group).
     */
    public fun nationalGroups(callingCode: String, national: String): List<Int>?

    /** Maximum digits (without `+`) of numbers with [callingCode]; E.164 caps every number at 15. */
    public fun maxDigits(callingCode: String?): Int = E164_MAX_DIGITS

    /** True when [digits] (E.164, no `+`) has a possible length; the server stays the judge of validity. */
    public fun isPossible(digits: String): Boolean = digits.length in GENERIC_MIN_DIGITS..E164_MAX_DIGITS

    /** E.164 digits for a number dialled nationally ([nationalDigits]) in [callingCode] (e.g. a trunk prefix dropped). */
    public fun nationalToE164(callingCode: String, nationalDigits: String): String = callingCode + nationalDigits

    /** International dialling prefixes that stand for `+` at the start of a typed number. */
    public val internationalPrefixes: List<String> get() = listOf(ITU_INTERNATIONAL_PREFIX)

    /** Limits shared by every provider. */
    public companion object {
        /** The longest E.164 number, in digits. */
        public const val E164_MAX_DIGITS: Int = 15

        /** The shortest number the generic check calls possible. */
        public const val GENERIC_MIN_DIGITS: Int = 7

        /** The international prefix ITU-T recommends ("00"). */
        public const val ITU_INTERNATIONAL_PREFIX: String = "00"
    }
}

/** No region knowledge: calling-code length unknown, generic grouping, E.164 length limits. */
public object GenericPhoneMetadata : PhoneMetadata {
    override fun callingCodeLength(digits: String): Int? = null

    override fun nationalGroups(callingCode: String, national: String): List<Int>? = null
}

/**
 * A phone number in E.164: raw is `""` or `"+"` followed by up to 15 ASCII digits.
 *
 * - Without [defaultCallingCode] the user types the full international number (`+` optional).
 * - With it, a number typed or pasted without `+` (or an international prefix) over the whole value
 *   is national and is prefixed through [PhoneMetadata.nationalToE164]; a number with `+` is taken as
 *   it is. When
 *   [lockCallingCode] is set the calling code cannot be edited away, a paste of another country's
 *   number is refused, and a value with no national digits is empty.
 * - A paste of a full number (`+…` or an international prefix) replaces the whole value.
 *
 * Display: `+<calling code> <groups>` with the calling code from [metadata] (or [defaultCallingCode]
 * when the number starts with it); national digits in the provider's groups, else in threes with a
 * last group of four instead of a lone digit. When the calling code is unknown the digits are shown
 * ungrouped after `+`.
 */
public class PhoneFormat(
    /** Region knowledge; [GenericPhoneMetadata] by default. */
    public val metadata: PhoneMetadata = GenericPhoneMetadata,
    defaultCallingCode: String? = null,
    /** Keep [defaultCallingCode] fixed. */
    public val lockCallingCode: Boolean = false,
    /** Zero of the digit family to show ('0' keeps ASCII). */
    public val zeroDigit: Char = '0',
) : InputFormat {
    /** The calling code for national input (digits only), or null. */
    public val defaultCallingCode: String? = defaultCallingCode?.let(::digitsOf)?.takeIf { it.isNotEmpty() }

    override val keyboard: InputKeyboard get() = InputKeyboard.Phone

    override fun edit(raw: String, start: Int, end: Int, inserted: CharSequence): RawEdit {
        val digits = digitsOf(raw).take(PhoneMetadata.E164_MAX_DIGITS)
        val cc = defaultCallingCode
        val locked = lockCallingCode && cc != null && digits.startsWith(cc)
        val text = foldInput(inserted).filterNot { InputChars.isSpace(it) }
        val international = internationalDigits(text)
        if (international != null) {
            val full = when {
                !lockCallingCode || cc == null -> international
                international.startsWith(cc) -> international
                else -> return RawEdit(canonical(digits), raw.length)
            }
            val out = canonical(clamp(full))
            return RawEdit(out, out.length)
        }
        val typed = digitsOf(text)
        // Raw offsets: index 0 is '+', digits follow.
        var ds = (minOf(start, end) - 1).coerceIn(0, digits.length)
        var de = (maxOf(start, end) - 1).coerceIn(0, digits.length)
        if (locked) {
            ds = ds.coerceAtLeast(cc.length)
            de = de.coerceAtLeast(cc.length)
        }
        val prefix = digits.cut(0, ds)
        val suffix = digits.cut(de, digits.length)
        val wholeValue = suffix.isEmpty() && (prefix.isEmpty() || (locked && prefix == cc))
        if (wholeValue && cc != null && typed.isNotEmpty()) {
            val full = clamp(metadata.nationalToE164(cc, typed).let(::digitsOf))
            val out = canonical(full)
            return RawEdit(out, out.length)
        }
        val max = maxDigitsFor(prefix + typed + suffix)
        val ins = typed.take((max - prefix.length - suffix.length).coerceAtLeast(0))
        var next = prefix + ins + suffix
        var shift = 0
        if (cc == null) {
            // A country calling code never starts with 0: leading digits that spell an international
            // prefix were typed as one.
            val idd = metadata.internationalPrefixes.firstOrNull { it.isNotEmpty() && it.startsWith('0') && next.startsWith(it) }
            if (idd != null) {
                next = next.substring(idd.length)
                shift = minOf(idd.length, prefix.length + ins.length)
            }
        }
        if (lockCallingCode && cc != null && next.length <= cc.length && cc.startsWith(next)) return RawEdit("", 0)
        val out = canonical(next)
        return RawEdit(out, if (out.isEmpty()) 0 else 1 + prefix.length + ins.length - shift)
    }

    override fun format(raw: String): Formatted {
        val digits = clamp(digitsOf(raw).take(PhoneMetadata.E164_MAX_DIGITS))
        val b = Formatted.Builder()
        if (digits.isEmpty()) return b.build()
        b.raw('+')
        val ccLength = callingCodeLength(digits)
        if (ccLength == null) {
            for (d in digits) b.raw(d, InputChars.shapeDigit(d, zeroDigit))
            return b.build()
        }
        val cc = digits.take(ccLength)
        val national = digits.drop(ccLength)
        for (d in cc) b.raw(d, InputChars.shapeDigit(d, zeroDigit))
        val groups = metadata.nationalGroups(cc, national)?.filter { it > 0 } ?: genericGroups(national.length)
        var at = 0
        var g = 0
        while (at < national.length) {
            val size = if (g < groups.size) groups[g] else national.length - at
            b.literal(" ")
            for (d in national.substring(at, minOf(national.length, at + size))) b.raw(d, InputChars.shapeDigit(d, zeroDigit))
            at += size
            g++
        }
        return b.build()
    }

    override fun isComplete(raw: String): Boolean {
        val digits = digitsOf(raw)
        return raw == canonical(digits) && metadata.isPossible(digits)
    }

    private fun canonical(digits: String): String = if (digits.isEmpty()) "" else "+$digits"

    private fun clamp(digits: String): String = digits.take(maxDigitsFor(digits))

    private fun maxDigitsFor(digits: String): Int {
        val cc = callingCodeLength(digits)?.let { digits.take(it) }
        return metadata.maxDigits(cc).coerceIn(1, PhoneMetadata.E164_MAX_DIGITS)
    }

    private fun callingCodeLength(digits: String): Int? {
        val fromMetadata = metadata.callingCodeLength(digits)?.takeIf { it in 1..minOf(3, digits.length) }
        if (fromMetadata != null) return fromMetadata
        val cc = defaultCallingCode ?: return null
        return if (digits.length >= cc.length && digits.startsWith(cc)) cc.length else null
    }

    /** E.164 digits when [text] starts with `+` or an international prefix (and has digits); else null. */
    private fun internationalDigits(text: String): String? {
        if (text.isEmpty()) return null
        val body = when {
            InputChars.isPlus(text[0]) -> text.substring(1)
            else -> {
                val prefix = metadata.internationalPrefixes.firstOrNull { it.isNotEmpty() && text.startsWith(it) } ?: return null
                text.substring(prefix.length)
            }
        }
        val digits = digitsOf(body)
        return digits.ifEmpty { null }
    }

    private fun genericGroups(length: Int): List<Int> {
        if (length <= 0) return emptyList()
        val groups = MutableList(length / 3) { 3 }
        when (length % 3) {
            1 -> if (groups.isNotEmpty()) groups[groups.lastIndex] = 4 else groups += 1
            2 -> groups += 2
        }
        return groups
    }
}
