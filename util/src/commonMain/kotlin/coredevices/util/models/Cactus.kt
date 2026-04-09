package coredevices.util.models

enum class CactusSTTMode(val id: Int) {
    RemoteOnly(0),
    LocalOnly(1),
    RemoteFirst(2),
    LocalFirst(3);

    companion object {
        fun fromId(id: Int): CactusSTTMode {
            return entries.firstOrNull { it.id == id } ?: RemoteOnly
        }
    }
}