package coredevices.ring

import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import org.koin.dsl.module

internal val firestoreModule = module {
    single { FirestoreRecordingsDao { get() } }
}