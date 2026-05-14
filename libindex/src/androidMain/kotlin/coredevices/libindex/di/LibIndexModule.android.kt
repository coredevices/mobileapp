package coredevices.libindex.di

import coredevices.libindex.device.IndexPlatformBluetoothAssociations
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformLibIndexModule: Module = module {
    singleOf(::IndexPlatformBluetoothAssociations)
}