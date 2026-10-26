import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.test.assertTrue

/**
 * Combination of [runBlocking] and [withTimeout] for brevity.
 */
inline fun runBlockingWithTimeout(timeoutMs: Long = 5_000L, crossinline block: suspend () -> Unit) {
    runBlocking {
        withTimeout(timeoutMs) {
            block()
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T> assertIs(obj: Any?, message: String? = null) {
    contract { returns() implies (obj is T) }
    assertTrue(
        obj is T,
        messagePrefix(message) + "Expected provided object to be <${T::class.java}>, " +
                "is <${obj?.javaClass}>."
    )
}

fun messagePrefix(message: String?) = if (message == null) "" else "$message. "
