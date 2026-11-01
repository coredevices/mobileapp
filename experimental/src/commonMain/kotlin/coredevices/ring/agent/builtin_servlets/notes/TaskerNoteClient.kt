package coredevices.ring.agent.builtin_servlets.notes

import coredevices.ring.agent.integrations.NoteIntegration

/**
 * Tasker is an Android-only automation app. The Android implementation routes notes to it via an
 * `ACTION_SEND` intent; iOS returns a disabled stub so the provider never surfaces there.
 */
expect fun createTaskerNoteClient(): NoteIntegration
