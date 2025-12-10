package coredevices

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val experimentalModule = module {
    singleOf(::ExperimentalDevices)
}
