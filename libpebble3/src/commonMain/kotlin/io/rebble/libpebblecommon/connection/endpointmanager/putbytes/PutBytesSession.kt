package io.rebble.libpebblecommon.connection.endpointmanager.putbytes

import io.rebble.libpebblecommon.packets.ObjectType
import io.rebble.libpebblecommon.packets.PutBytesAbort
import io.rebble.libpebblecommon.services.PutBytesService
import io.rebble.libpebblecommon.util.Crc32Calculator
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.Source

class PutBytesSession(
    private val putBytesService: PutBytesService
) {
    companion object {
        const val APP_PUT_CHUNK_SIZE =
            2000 // Can't be too large to avoid locking up comms, probably
    }

    private val _currentSession = MutableStateFlow<CurrentSession?>(null)
    val currentSession = _currentSession.asStateFlow()
    private val sessionMutex = Mutex()

    data class CurrentSession(val cookie: UInt)

    sealed class SessionState {
        abstract val cookie: UInt

        data class Open(override val cookie: UInt) : SessionState()
        data class Finished(override val cookie: UInt) : SessionState()
        data class Sending(override val cookie: UInt, val totalSent: UInt) : SessionState()
    }

    private fun putBytesFlow(block: suspend FlowCollector<SessionState>.() -> Unit) = flow {
        check(sessionMutex.tryLock()) { "PutBytesSession already active" }
        try {
            block()
        } catch (e: PutBytesService.PutBytesException) {
            e.cookie?.let { putBytesService.send(PutBytesAbort(it)) }
            throw e
        } finally {
            _currentSession.value = null
            sessionMutex.unlock()
        }
    }

    private suspend fun FlowCollector<SessionState>.transferData(
        cookie: UInt,
        size: UInt,
        source: Source
    ): UInt {
        var totalSent = 0u
        val buffer = ByteArray(APP_PUT_CHUNK_SIZE)
        val crc32Calculator = Crc32Calculator()
        emit(SessionState.Sending(cookie, 0u))
        while (totalSent < size) {
            val read = source.readAtMostTo(buffer)
            if (read == -1) break
            totalSent += read.toUInt()
            val toSend = buffer.copyOfRange(0, read).asUByteArray()
            crc32Calculator.addBytes(toSend)
            val response = putBytesService.sendPut(cookie, toSend)
            check(response.cookie.get() == cookie) { "Received response for wrong cookie" }
            emit(SessionState.Sending(cookie, totalSent))
        }
        return crc32Calculator.finalize()
    }

    /**
     * Begin a session to send an app to the watch.
     *
     * Should be `flowOn(IO)`
     */
    suspend fun beginAppSession(appId: UInt, size: UInt, type: ObjectType, source: Source) =
        putBytesFlow {
            val initResponse = putBytesService.initAppSession(appId, size, type)
            val cookie = initResponse.cookie.get()
            emit(SessionState.Open(cookie))
            _currentSession.value = CurrentSession(cookie)
            val crc32 = transferData(cookie, size, source)
            val response = putBytesService.sendCommit(cookie, crc32)
            check(response.cookie.get() == cookie) { "Received response for wrong cookie" }
            sendInstall(cookie)
        }

    /**
     * Begin a session to send a file (usually firmware) to the watch.
     *
     * Should be `flowOn(IO)`
     *
     * @param sendInstall send an install command? You will want to do this unless it's a FW update.
     * @param resumeOffset resume an interrupted transfer from this offset. [source] must already be
     * skipped to it, and [objectCrc] must be provided (the CRC of the whole object - the watch
     * expects that on commit even for a partial transfer).
     */
    fun beginSession(
        size: UInt,
        type: ObjectType,
        bank: UByte,
        filename: String,
        source: Source,
        sendInstall: Boolean,
        resumeOffset: UInt = 0u,
        objectCrc: UInt? = null,
    ) = putBytesFlow {
        require(resumeOffset < size) { "Resume offset $resumeOffset >= size $size" }
        require(resumeOffset == 0u || objectCrc != null) { "objectCrc required when resuming" }
        val remaining = size - resumeOffset
        val initResponse = putBytesService.initSession(remaining, type, bank, filename, resumeOffset)
        val cookie = initResponse.cookie.get()
        emit(SessionState.Open(cookie))
        _currentSession.value = CurrentSession(cookie)
        val crc32 = transferData(cookie, remaining, source)
        val response = putBytesService.sendCommit(cookie, objectCrc ?: crc32)
        check(response.cookie.get() == cookie) { "Received response for wrong cookie" }
        if (sendInstall) {
            sendInstall(cookie)
        }
        emit(SessionState.Finished(cookie))
    }

    suspend fun sendInstall(cookie: UInt) {
        val installResponse = putBytesService.sendInstall(cookie)
        // TODO this fired?
//        check(installResponse.cookie.get() == cookie) { "Received response for wrong cookie" }
    }
}