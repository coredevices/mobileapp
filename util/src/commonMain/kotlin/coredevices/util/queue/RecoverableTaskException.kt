package coredevices.util.queue

open class RecoverableTaskException: Throwable {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}