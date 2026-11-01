package coredevices.util

import co.touchlab.kermit.Logger
import kotlin.io.encoding.Base64

object BugReportContext {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val logger = Logger.withTag("BugReportContext")

    /** Decodes the base64url `context` from a bug-report deep link; null when absent or undecodable. */
    fun decodeBugReportContext(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return try {
            base64Url.decode(encoded).decodeToString()
        } catch (e: IllegalArgumentException) {
            logger.w(e) { "Ignoring undecodable bug report context" }
            null
        }
    }

    /** Encodes the base64url `context` for use in a bug-report deep link */
    fun encodeBugReportContext(context: String): String {
        return base64Url.encode(context.encodeToByteArray())
    }
}
