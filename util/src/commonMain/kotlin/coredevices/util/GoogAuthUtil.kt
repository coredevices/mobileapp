package coredevices.util

import dev.gitlive.firebase.auth.AuthCredential

interface GoogleAuthUtil {
    suspend fun signInGoogle(): AuthCredential?
}