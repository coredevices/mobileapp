package io.rebble.libpebblecommon.util

import kotlin.random.Random

internal fun randomCookie(): UInt = Random.nextInt(0, UShort.MAX_VALUE.toInt()).toUInt()
internal fun randomCookieByte(): UByte = Random.nextBytes(1)[0].toUByte()