package coredevices.util.auth

/**
 * Attempts to restore a lost Firebase session without user interaction (e.g. from a cached
 * platform credential). Used when Firebase's persisted auth state fails to load at startup.
 */
interface SilentSignIn {
    /** True if sign-in succeeded (auth state then updates via the normal Firebase listeners). */
    suspend fun attempt(): Boolean
}

object NoOpSilentSignIn : SilentSignIn {
    override suspend fun attempt() = false
}
