package coredevices.coreapp.di

import CommonApiConfig
import coredevices.coreapp.api.BugApi
import coredevices.coreapp.api.BugReports
import coredevices.coreapp.api.BugReportsService
import coredevices.coreapp.api.PushService
import coredevices.util.CommonBuildKonfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val apiModule = module {
    single { object : CommonApiConfig {
        override val bugUrl: String?
            get() = CommonBuildKonfig.BUG_URL
        override val tokenUrl: String?
            get() = CommonBuildKonfig.TOKEN_URL
        override val version: String
            get() = CommonBuildKonfig.USER_AGENT_VERSION
    } } bind CommonApiConfig::class
    single {
        BugApi(get())
    }
    singleOf(::BugReportsService)
    singleOf(::PushService)
    singleOf(::BugReports)
}