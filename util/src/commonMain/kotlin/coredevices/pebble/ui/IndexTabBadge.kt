package coredevices.pebble.ui

import kotlinx.coroutines.flow.Flow

/** Items needing attention on the Index tab; bound by the index feature module. */
interface IndexTabBadge {
    val count: Flow<Int>
}
