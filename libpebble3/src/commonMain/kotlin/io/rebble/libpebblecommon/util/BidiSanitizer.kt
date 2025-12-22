package io.rebble.libpebblecommon.util

/**
 * Strips Unicode bidi isolate markers that some upstream apps include in notification text.
 *
 * These characters are invisible control codes in Unicode, but some downstream renderers
 * (e.g. Pebble firmware fonts) may display them as tofu/squares.
 *
 * Removed range: U+2066..U+2069 (LRI, RLI, FSI, PDI).
 */
fun stripBidiIsolates(text: CharSequence?): String? {
    if (text == null) return null

    // Allocate a StringBuilder only if we actually encounter isolate markers, so the common case
    // (no markers) stays allocation-free.
    var out: StringBuilder? = null
    for (i in 0 until text.length) {
        val ch = text[i]
        if (ch >= '\u2066' && ch <= '\u2069') {
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
