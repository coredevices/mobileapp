package coredevices.ring.agent.builtin_servlets.clock

actual suspend fun setAlarm(hours: Int, minutes: Int, label: String?) {
    error("It's not possible to set alarms on iOS")
}