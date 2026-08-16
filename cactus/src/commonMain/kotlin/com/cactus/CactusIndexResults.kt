package com.cactus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class IndexEntry(val document: String, val metadata: String)

@Serializable
internal data class IndexMatch(val id: Int, val score: Float)

@Serializable
private data class IndexEntries(val results: List<IndexEntry>)

@Serializable
private data class IndexMatches(val results: List<IndexMatch>)

private val json = Json

internal fun encodeIndexEntries(entries: List<IndexEntry>): String =
    json.encodeToString(IndexEntries.serializer(), IndexEntries(entries))

// JSON has no NaN/Infinity literals, so a non-finite score is reported as no similarity.
internal fun encodeIndexMatches(matches: List<IndexMatch>): String =
    json.encodeToString(
        IndexMatches.serializer(),
        IndexMatches(matches.map { if (it.score.isFinite()) it else it.copy(score = 0f) }),
    )