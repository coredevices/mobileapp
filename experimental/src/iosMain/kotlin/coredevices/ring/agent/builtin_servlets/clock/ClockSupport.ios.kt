package coredevices.ring.agent.builtin_servlets.clock

import coredevices.util.clock.NativeAlarmKitBridge

actual fun clockUnavailableReason(): String? =
    if (NativeAlarmKitBridge.isAvailable) null else IOS_26_REQUIRED_REASON
