package coredevices.ring.database.firestore.dao

class DaoAuthException: Exception {
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}