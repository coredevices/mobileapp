package coredevices.util

import kotlinx.io.Buffer
import kotlinx.io.bytestring.toByteString
import kotlinx.io.write
import platform.Foundation.NSData

fun NSData.toBuffer(): Buffer {
    val buf = Buffer()
    buf.write(this.toByteString())
    return buf
}