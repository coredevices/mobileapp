package io.rebble.libpebblecommon.metadata

enum class WatchType(val codename: String) {
    APLITE("aplite"),
    BASALT("basalt"),
    CHALK("chalk"),
    DIORITE("diorite"),
    EMERY("emery"),
    FLINT("flint"),
    GABBRO("gabbro"),
    ;

    fun getCompatibleAppVariants(): List<WatchType> {
        return when (this) {
            APLITE -> listOf(APLITE)
            BASALT -> listOf(BASALT, APLITE)
            CHALK -> listOf(CHALK)
            DIORITE -> listOf(DIORITE, APLITE)
            EMERY -> listOf(
                EMERY,
                BASALT,
                DIORITE,
                APLITE
            )
            FLINT -> listOf(FLINT, DIORITE, APLITE)
            GABBRO -> listOf(GABBRO, CHALK)
        }
    }

    /**
     * Get the most compatible variant for this WatchType
     * @param availableAppVariants List of variants, from [io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo.targetPlatforms]
     */
    fun getBestVariant(availableAppVariants: List<String>): WatchType? {
        val compatibleVariants = getCompatibleAppVariants()

        return compatibleVariants.firstOrNull() { variant ->
            availableAppVariants.contains(variant.codename)
        }
    }

    companion object {
        fun fromCodename(codename: String): WatchType? {
            return entries.firstOrNull { it.codename == codename }
        }
    }
}

fun WatchType.isColor(): Boolean = when (this) {
    WatchType.APLITE -> false
    WatchType.BASALT -> true
    WatchType.CHALK -> true
    WatchType.DIORITE -> false
    WatchType.EMERY -> true
    WatchType.FLINT -> false
    WatchType.GABBRO -> true
}