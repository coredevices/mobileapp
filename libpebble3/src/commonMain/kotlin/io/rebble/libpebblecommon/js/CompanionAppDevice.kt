package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.services.WatchInfo

public class CompanionAppDevice internal constructor(
    internal val identifier: PebbleIdentifier,
    public val watchInfo: WatchInfo,
    appMessages: ConnectedPebble.AppMessages,
): ConnectedPebble.AppMessages by appMessages
