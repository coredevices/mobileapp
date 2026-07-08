package coredevices.ring.service.indexfeed

import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_NOTES_SELF
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_SHOPPING
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_TODOS
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.isDefaultTodosListTitle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultListsBootstrapTest {

    @Test
    fun renamesSystemTodosListStillNamedTodos() {
        assertTrue(isDefaultTodosListTitle(seed = SEED_TODOS, title = "Todos"))
    }

    @Test
    fun doesNotRenameOnceAlreadyReminders() {
        // Converges: after migration the title is "Reminders", so it won't fire again.
        assertFalse(isDefaultTodosListTitle(seed = SEED_TODOS, title = "Reminders"))
    }

    @Test
    fun doesNotClobberUserRenamedSystemList() {
        assertFalse(isDefaultTodosListTitle(seed = SEED_TODOS, title = "My tasks"))
    }

    @Test
    fun doesNotTouchUserCreatedListNamedTodos() {
        // A user-created (non-system) list has a null seed even if named "Todos".
        assertFalse(isDefaultTodosListTitle(seed = null, title = "Todos"))
    }

    @Test
    fun doesNotTouchOtherSystemListsNamedTodos() {
        assertFalse(isDefaultTodosListTitle(seed = SEED_NOTES_SELF, title = "Todos"))
        assertFalse(isDefaultTodosListTitle(seed = SEED_SHOPPING, title = "Todos"))
    }
}
