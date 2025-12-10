package coredevices.util

import dev.gitlive.firebase.auth.FirebaseUser

val FirebaseUser.emailOrNull: String? get() = this.email?.ifBlank { null }