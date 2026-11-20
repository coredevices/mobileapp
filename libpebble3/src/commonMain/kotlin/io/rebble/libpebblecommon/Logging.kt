package io.rebble.libpebblecommon

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

internal object Logging {
    fun setMinSeverity(severity: LPKSeverity): Unit = Logger.setMinSeverity(
        when (severity) {
            LPKSeverity.Verbose -> Severity.Verbose
            LPKSeverity.Debug -> Severity.Debug
            LPKSeverity.Info -> Severity.Info
            LPKSeverity.Warn -> Severity.Warn
            LPKSeverity.Error -> Severity.Error
            LPKSeverity.Assert -> Severity.Assert
        }
    )
}

internal enum class LPKSeverity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert
}