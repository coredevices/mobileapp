package coredevices.util.models

enum class CactusSTTMode(val id: Int) {
    RemoteOnly(0),
    LocalOnly(1),
    RemoteFirst(2),
    LocalFirst(3),
    RebbleOnly(4),
    RebbleFirst(5),
    RebbleFallback(6);

    companion object {
        fun fromId(id: Int): CactusSTTMode {
            return entries.firstOrNull { it.id == id } ?: RemoteOnly
        }
    }

    fun usesLocalCactus(): Boolean {
        return this in setOf(RemoteFirst, LocalOnly, LocalFirst, RebbleFirst, RebbleFallback)
    }
}