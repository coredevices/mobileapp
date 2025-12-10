package coredevices.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class DoneInitialOnboarding {
    private val _doneInitialOnboarding = CompletableDeferred<Unit>()
    val doneInitialOnboarding: Deferred<Unit> = _doneInitialOnboarding

    fun onDoneInitialOnboarding() {
        _doneInitialOnboarding.complete(Unit)
    }
}