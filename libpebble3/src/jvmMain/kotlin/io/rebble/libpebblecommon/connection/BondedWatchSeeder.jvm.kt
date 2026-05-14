package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.entity.KnownWatchItem

internal actual suspend fun seedBondedWatches(
    appContext: AppContext,
    knownWatchDao: KnownWatchDao,
): List<KnownWatchItem>? = emptyList()
