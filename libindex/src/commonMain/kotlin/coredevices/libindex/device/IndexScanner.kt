package coredevices.libindex.device

interface IndexScanner {
    fun scanForDevices(): List<DiscoveredIndexDevice>
}