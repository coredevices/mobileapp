package coredevices.util.integrations

/** PKCE for hosted plugin OAuth, reusing `:util`'s 128-char verifier and base64url SHA-256 challenge. */
private val PKCE_ALPHABET = ('0'..'9') + ('a'..'z') + ('A'..'Z')

fun generateOAuthCodeVerifier(): String = generateSecureRandomString(128, PKCE_ALPHABET)

fun oauthCodeChallenge(verifier: String): String = sha256(verifier)
