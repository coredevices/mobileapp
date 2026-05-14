package io.rebble.libpebblecommon.connection.bt.classic

// Matches "Pebble XXXX", "Pebble Time XXXX", "Pebble Time Le XXXX" (XXXX = 4 hex chars).
// Explicitly rejects other suffixed variants (e.g. "Pebble Index 1234").
internal val PEBBLE_NAME_REGEX = Regex("""^Pebble(?: Time(?: Le)?)? [0-9A-Fa-f]{4}$""")
