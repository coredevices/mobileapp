package coredevices.ring.agent

import coredevices.util.queue.RecoverableTaskException

class AgentNetworkException: Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}