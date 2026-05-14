package coredevices.libindex.device

actual fun String.toPlatformAddress(): String = this.chunked(2).joinToString(":")