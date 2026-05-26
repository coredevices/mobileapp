package io.rebble.libpebblecommon.connection.endpointmanager.findmyphone

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.services.PhoneSoundService
import io.rebble.libpebblecommon.services.appmessage.AppMessageService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FindMyPhoneManager(
    private val watchScope: ConnectionCoroutineScope,
    private val appMessageService: AppMessageService,
    private val phoneSoundService: PhoneSoundService,
) {
    companion object {
        private val logger = Logger.withTag(FindMyPhoneManager::class.simpleName!!)
    }

    fun init() {
        appMessageService.inboundAppMessages(SystemAppIDs.FIND_MY_PHONE_UUID).onEach {
            logger.i { "Find My Phone triggered by watch" }
            phoneSoundService.stopSound()
            phoneSoundService.playDefaultRingtone()
        }.launchIn(watchScope)
    }
}
