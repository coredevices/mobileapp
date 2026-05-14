package coredevices.ring.util.trace

import coredevices.ring.data.entity.room.TraceEntryEntity
import coredevices.ring.data.entity.room.TraceSessionDocument
import coredevices.ring.data.entity.room.toDocument
import coredevices.ring.database.room.dao.TraceEntryDao
import coredevices.ring.database.room.dao.TraceSessionDao
import coredevices.libindex.database.repository.RingTransferRepository

class TraceSessionExporter(
    private val traceEntryDao: TraceEntryDao,
    private val traceSessionDao: TraceSessionDao,
    private val transferRepository: RingTransferRepository,
) {
    /**
     * Collects every trace entry related to [recordingId]: entries tagged with the recording id
     * directly, plus entries tagged with any transfer id linked to the recording (a single
     * recording can span multiple transfers). Entries are grouped by their originating trace
     * session since processing for one recording may straddle an app restart.
     */
    suspend fun exportForRecording(recordingId: Long): List<TraceSessionDocument> {
        val byRecording = traceEntryDao.getEntriesForRecording(recordingId)
        val transfers = transferRepository.getTransfersByRecordingId(recordingId)
        val byTransfers = transfers.flatMap { traceEntryDao.getEntriesForTransfer(it.id) }
        val merged = (byRecording + byTransfers).distinctBy { it.id }
        return groupBySession(merged)
    }

    suspend fun exportLastNSessions(limit: Int, offset: Int = 0): List<TraceSessionDocument> {
        val sessions = traceSessionDao.getLastNTraceSessions(limit, offset)
        return sessions.map { session ->
            val entries = traceEntryDao.getEntriesForSession(session.id)
            TraceSessionDocument(
                sessionId = session.id,
                timestamp = session.started.toString(),
                trace = entries.map { it.toDocument() },
            )
        }
    }

    private suspend fun groupBySession(entries: List<TraceEntryEntity>): List<TraceSessionDocument> {
        if (entries.isEmpty()) return emptyList()
        val sessionIds = entries.map { it.sessionId }.toSet()
        val sessions = traceSessionDao.getSessionsByIds(sessionIds).associateBy { it.id }
        return entries.groupBy { it.sessionId }
            .mapNotNull { (sessionId, sessionEntries) ->
                val session = sessions[sessionId] ?: return@mapNotNull null
                TraceSessionDocument(
                    sessionId = session.id,
                    timestamp = session.started.toString(),
                    trace = sessionEntries.sortedBy { it.timeMark }.map { it.toDocument() },
                )
            }
            .sortedBy { it.timestamp }
    }
}
