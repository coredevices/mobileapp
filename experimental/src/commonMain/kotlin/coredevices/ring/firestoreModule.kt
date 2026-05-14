package coredevices.ring

import coredevices.ring.database.firestore.FirestoreKnownRingsSync
import coredevices.ring.database.firestore.FirestoreKnownRingsSyncImpl
import coredevices.ring.database.firestore.dao.FirestoreKnownRingsDao
import coredevices.ring.database.firestore.dao.FirestoreItemsDao
import coredevices.ring.database.firestore.dao.FirestoreListsDao
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.firestore.dao.FirestoreTracesDao
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

internal val firestoreModule = module {
    single { FirestoreRecordingsDao { get() } }
    single { FirestoreTracesDao { get() } }
    single { FirestoreKnownRingsDao { get() } }
    singleOf(::FirestoreKnownRingsSyncImpl) binds arrayOf(FirestoreKnownRingsSync::class)
    single { FirestoreItemsDao { get() } }
    single { FirestoreListsDao { get() } }
}