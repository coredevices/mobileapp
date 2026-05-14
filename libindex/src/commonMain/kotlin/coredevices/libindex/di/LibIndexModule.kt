package coredevices.libindex.di

import coredevices.libindex.LibIndex
import coredevices.libindex.RealLibIndex
import coredevices.libindex.Rings
import coredevices.libindex.Scanning
import coredevices.libindex.device.IndexDeviceFactory
import coredevices.libindex.device.IndexDeviceManager
import coredevices.libindex.device.IndexPairing
import coredevices.libindex.device.RealIndexPairing
import coredevices.libindex.device.RealScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

expect val platformLibIndexModule: Module

//TODO: don't rely on app global Koin
val libIndexModule = module {
    includes(platformLibIndexModule)
    single { LibIndexCoroutineScope(Dispatchers.Default) }
    singleOf(::RealIndexPairing) bind IndexPairing::class
    singleOf(::IndexDeviceFactory)

    single {
        IndexDeviceManager(
            get(),
            get(),
            get(),
            get(),
            getOrNull(),
            get(),
            get()
        )
    } bind Rings::class
    singleOf(::RealScanning) bind Scanning::class
    single {
        RealLibIndex(
            get(),
            get(),
            getOrNull()
        )
    } bind LibIndex::class
}

class LibIndexCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope