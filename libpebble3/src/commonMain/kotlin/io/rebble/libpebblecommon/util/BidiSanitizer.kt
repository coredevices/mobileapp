package io.rebble.libpebblecommon.util

/**
 * Strips invisible/zero-width Unicode characters that some upstream apps include in notification
 * text (commonly email-marketer preheader padding).
 *
 * These characters are invisible by spec, but the Pebble firmware fonts have no glyphs for them
 * and fall back to U+25AF (white vertical rectangle), so they appear as long runs of tofu.
 *
 * Removed:
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
 */
fun stripBidiIsolates(text: CharSequence?): String? {
    if (text == null) return null

    // Allocate a StringBuilder only if we actually encounter a stripped char, so the common case
    // (none present) stays allocation-free.
    var out: StringBuilder? = null
    for (i in 0 until text.length) {
        val ch = text[i]
        if (ch == '\u00AD' ||
            ch == '\u034F' ||
            ch in '\u200A'..'\u200F' ||
            ch in '\u202A'..'\u202E' ||
            ch == '\u2060' ||
            ch in '\u2066'..'\u2069' ||
            ch == '\uFEFF'
        ) {
            if (out == null) {
                out = StringBuilder(text.length)
                out.append(text, 0, i)
            }
            continue
        }
        out?.append(ch)
    }

    return out?.toString() ?: text.toString()
}
