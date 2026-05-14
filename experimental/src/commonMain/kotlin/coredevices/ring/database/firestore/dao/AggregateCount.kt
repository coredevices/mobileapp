package coredevices.ring.database.firestore.dao

import dev.gitlive.firebase.firestore.CollectionReference

expect suspend fun CollectionReference.count(): Int
