package io.rebble.libpebblecommon

import io.rebble.libpebblecommon.connection.Errors
import io.rebble.libpebblecommon.connection.UserFacingError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class ErrorTracker : Errors {
    private val _errors = MutableSharedFlow<UserFacingError>(extraBufferCapacity = 1)
    override val userFacingErrors: Flow<UserFacingError> = _errors.asSharedFlow()

    fun reportError(error: UserFacingError) {
        _errors.tryEmit(error)
    }
}