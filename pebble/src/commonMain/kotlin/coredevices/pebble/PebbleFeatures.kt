package coredevices.pebble

class PebbleFeatures(
    private val platform: Platform,
) {
    fun supportsNotificationFiltering(): Boolean = platform == Platform.Android
    fun supportsNotificationAppSorting(): Boolean = true
    fun supportsNotifiedOnlyFilter(): Boolean = platform == Platform.Android
    fun supportsNotificationCountSorting(): Boolean = platform == Platform.Android
    fun supportsNotificationLogging(): Boolean = platform == Platform.Android
    fun supportsPostTestNotification(): Boolean = platform == Platform.Android
    fun supportsDetectingOtherPebbleApps(): Boolean = platform == Platform.Android
    fun supportsBtClassic(): Boolean = platform == Platform.Android
    fun supportsCompanionDeviceManager(): Boolean = platform == Platform.Android
    fun supportsVibePatterns(): Boolean = platform == Platform.Android
}