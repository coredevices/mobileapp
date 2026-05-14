@file:OptIn(ExperimentalTime::class)

package coredevices.indexai.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Firestore-shape document for a list (e.g. "Notes to self", "Todos", "Shopping list").
 * Stored at `lists/{uid}/lists/{listId}`.
 *
 * The three system seed lists are bootstrapped on first launch with stable doc IDs
 * (`list_notes_self`, `list_todos`, `list_shopping`) and marked via [seed]. Any other
 * lists are user-created and have whatever doc id Firestore generates.
 */
@Serializable
data class ListDocument(
    @Serializable(with = InstantComponentSerializer::class)
    val createdAt: Instant = Clock.System.now(),
    @Serializable(with = InstantComponentSerializer::class)
    val updatedAt: Instant = Clock.System.now(),
    val title: String = "",
    /** Emoji shown next to the list title in the index feed. */
    val icon: String = "📝",
    /**
     * How items inside this list render: "note" (free text), "checklist" (every
     * item gets a checkbox), or "bullets" (compact bullet list). Mirrors the
     * prototype's `listKind` field.
     */
    val listKind: String = "note",
    /**
     * Marker for system-bootstrapped lists. One of "notes_self", "todos",
     * "shopping" for the three defaults; null for user-created lists.
     */
    val seed: String? = null,
    val deleted: Boolean = false,
)
