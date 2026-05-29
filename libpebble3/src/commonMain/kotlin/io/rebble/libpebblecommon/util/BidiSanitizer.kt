package io.rebble.libpebblecommon.util

/**
 * Sanitizes Unicode characters that some upstream apps include in notification text.
 *
 * The Pebble firmware fonts have no glyphs for these characters and fall back to U+25AF (white
 * vertical rectangle), so they appear as tofu.
 *
 * Removed (invisible / zero-width):
 *  - U+00AD (soft hyphen)
 *  - U+034F (combining grapheme joiner)
 *  - U+200A (hair space)
 *  - U+200B (zero width space)
 *  - U+200C (zero width non-joiner)
 *  - U+200D (zero width joiner)
 *  - U+200E..U+200F (LRM, RLM bidi marks)
 *  - U+202A..U+202E (legacy bidi formatting: LRE, RLE, PDF, LRO, RLO)
 *  - U+2060 (word joiner)
 *  - U+2066..U+2069 (bidi isolates: LRI, RLI, FSI, PDI)
 *  - U+FEFF (zero width no-break space / BOM)
 *
 * Replaced with regular space (U+0020):
 *  - U+202F (narrow no-break space) -- used by Android's ICU/CLDR time formatter before AM/PM
 *    in en_US. Stripping would join "10:30AM" instead of preserving "10:30 AM".
 */
fun stripBidiIsolates(text: CharSequence?): String? {
    if (text == null) return null

    // Allocate a StringBuilder only if we actually encounter a sanitized char, so the common case
    // (none present) stays allocation-free.
    var out: StringBuilder? = null
    for (i in 0 until text.length) {
        val ch = text[i]
        val replacement = sanitize(ch)
        if (replacement == ch) {
            out?.append(ch)
            continue
        }
        if (out == null) {
            out = StringBuilder(text.length)
            out.append(text, 0, i)
        }
        if (replacement != null) out.append(replacement)
    }

    return out?.toString() ?: text.toString()
}

private fun sanitize(ch: Char): Char? = when {
    ch == '\u00AD' ||
            ch == '\u034F' ||
            ch in '\u200A'..'\u200F' ||
            ch in '\u202A'..'\u202E' ||
            ch == '\u2060' ||
            ch in '\u2066'..'\u2069' ||
            ch == '\uFEFF' -> null
    ch == '\u202F' -> ' '
    else -> ch
}
