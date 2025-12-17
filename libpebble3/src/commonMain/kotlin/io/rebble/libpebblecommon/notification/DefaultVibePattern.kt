package io.rebble.libpebblecommon.notification

enum class DefaultVibePattern(
    val displayName: String,
    val pattern: List<Long>,
) {
    Standard("Standard", listOf(500)),
    Pulses("Pulses", listOf(50, 50, 50, 50, 50, 50, 50)),
    Double("Double", listOf(200, 75, 200)),
    Triple("Triple", listOf(200, 75, 200, 75, 200)),
    Bloom("Bloom", listOf(35, 61, 47, 53, 50, 40, 81, 171, 189, 236, 47, 70, 38, 44, 39, 62, 79, 171, 181)),
    Pips("Pips", listOf(40, 960, 40, 960, 40, 960, 40, 960, 40, 960, 500)),
    Ole("Olé", listOf(61, 194, 272, 153, 47, 77, 47, 78, 46, 89, 54, 78, 47, 70, 388)),
    SOS(
        "SOS",
        listOf(100, 75, 100, 75, 100, 220, 300, 75, 300, 75, 300, 150, 100, 75, 100, 75, 100)
    ),
    OhhhOh("Ohhh, Oh", listOf(459, 522, 144, 171, 173, 162, 72, 135, 555, 386, 514)),
    Five("Five", listOf(68, 178, 80, 237, 54, 95, 122, 221, 154, 221, 139, 218, 81, 161, 137, 189, 55, 95, 130, 211, 188, 178, 222)),
    Two("Two", listOf(135, 269, 847, 394, 40, 159, 48, 170, 31, 144, 64, 136, 64, 162, 36, 163, 122)),
}

fun DefaultVibePattern.uIntPattern() = pattern.map { it.toUInt() }