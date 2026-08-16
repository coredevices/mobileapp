package coredevices.firestore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilentReauthPolicyTest {
    @Test
    fun waitsForFirebaseRestorationBeforeFirstAttempt() {
        assertFalse(shouldAttemptSilentReauth(attempt = 1, silentAttempts = 0, providers = "google.com"))
        assertFalse(shouldAttemptSilentReauth(attempt = SILENT_REAUTH_MIN_WAIT_ATTEMPT - 1, silentAttempts = 0, providers = "google.com"))
        assertTrue(shouldAttemptSilentReauth(attempt = SILENT_REAUTH_MIN_WAIT_ATTEMPT, silentAttempts = 0, providers = "google.com"))
    }

    @Test
    fun attemptsAreCapped() {
        assertTrue(shouldAttemptSilentReauth(attempt = 10, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS - 1, providers = "google.com"))
        assertFalse(shouldAttemptSilentReauth(attempt = 10, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS, providers = "google.com"))
    }

    @Test
    fun givesUpOnceAttemptsAreSpentAndNoSilentReauthIsLeft() {
        assertFalse(shouldGiveUpWaitingForAuth(attempt = AUTH_RESTORE_MAX_ATTEMPTS - 1, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS, providers = "github.com"))
        assertTrue(shouldGiveUpWaitingForAuth(attempt = AUTH_RESTORE_MAX_ATTEMPTS, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS, providers = "github.com"))
        // A Google account with silent re-auth still to try keeps waiting.
        assertFalse(shouldGiveUpWaitingForAuth(attempt = AUTH_RESTORE_MAX_ATTEMPTS, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS - 1, providers = "google.com"))
        assertTrue(shouldGiveUpWaitingForAuth(attempt = AUTH_RESTORE_MAX_ATTEMPTS, silentAttempts = SILENT_REAUTH_MAX_ATTEMPTS, providers = "google.com"))
    }

    @Test
    fun onlyForGoogleAccounts() {
        assertTrue(canSilentReauth("google.com"))
        assertTrue(canSilentReauth("password,google.com"))
        assertFalse(canSilentReauth(""))
        assertFalse(canSilentReauth("github.com"))
        assertFalse(canSilentReauth("apple.com,password"))
        assertFalse(shouldAttemptSilentReauth(attempt = 10, silentAttempts = 0, providers = "github.com"))
    }
}
