package io.rebble.libpebblecommon.calls

public sealed interface Call {
    public val contactName: String?
    public val contactNumber: String
    public val cookie: UInt

    public sealed interface EndableCall: Call {
        public fun endCall()
    }
    public sealed interface AnswerableCall: Call {
        public fun answerCall()
    }

    public class RingingCall internal constructor(
        override val contactName: String?,
        override val contactNumber: String,
        override val cookie: UInt,
        private val onCallEnd: (EndableCall) -> Unit,
        private val onCallAnswer: (AnswerableCall) -> Unit
    ): EndableCall, AnswerableCall {
        override fun endCall() {
            onCallEnd(this)
        }

        override fun answerCall() {
            onCallAnswer(this)
        }
    }

    public class DialingCall internal constructor(
        override val contactName: String?,
        override val contactNumber: String,
        override val cookie: UInt,
        private val onCallEnd: (EndableCall) -> Unit,
    ): EndableCall {
        override fun endCall() {
            onCallEnd(this)
        }
    }

    public class ActiveCall internal constructor(
        override val contactName: String?,
        override val contactNumber: String,
        override val cookie: UInt,
        private val onCallEnd: (EndableCall) -> Unit,
    ): EndableCall {
        override fun endCall() {
            onCallEnd(this)
        }
    }

    public class HoldingCall internal constructor(
        override val contactName: String?,
        override val contactNumber: String,
        override val cookie: UInt,
        private val onCallEnd: (EndableCall) -> Unit,
    ): EndableCall {
        override fun endCall() {
            onCallEnd(this)
        }
    }
}