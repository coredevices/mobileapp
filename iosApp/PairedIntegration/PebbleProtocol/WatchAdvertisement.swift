package enum WatchAdvertisement {
    package static var vendorIdentifiers: Set<UInt16> { [0x0154, 0x0EEA] }
    /// Advertised by a watch that is not bonded yet, including after a reset,
    /// and published by every watch. The app's Info.plist names it again for
    /// AccessorySetupKit, which reads the declaration rather than this.
    package static let pairingServiceUUID = "0000FED9-0000-1000-8000-00805F9B34FB"

    /// What an advertisement says about the watch behind it.
    package struct AdvertisedWatch: Equatable, Sendable {
        /// A best guess for display only: the real one arrives with the version
        /// response once connected. Nil when the advertisement does not say —
        /// a watch just reset advertises a generic name and may omit the
        /// extended record — rather than some model picked for it, which the
        /// scan list then showed with another watch's name and picture.
        package var model: WatchModel?
    }

    /// Nil for anything that is not a Pebble, or a Pebble this app cannot
    /// drive; a Pebble whose model is unknown is still one.
    package static func watch(
        advertisesPebbleService: Bool,
        localName: String?,
        manufacturerData: [UInt8]
    ) -> AdvertisedWatch? {
        let containsCompanyIdentifier = manufacturerData.count >= 2
            && vendorIdentifiers.contains(
                UInt16(manufacturerData[0]) | UInt16(manufacturerData[1]) << 8
            )
        guard containsCompanyIdentifier || advertisesPebbleService else {
            return nil
        }

        // Payload: type(1) + serial(12), then an extended record whose first byte is
        // the hardware platform. Older firmware omits it, and platform 0 means the
        // watch did not say. CoreBluetooth's manufacturer data always starts with
        // a company identifier, so a watch found by its service under another
        // company's data carries no layout of Pebble's to read: its name is
        // asked instead. Reading two bytes short there took a serial number's
        // letter for a platform and turned the watch away.
        let hardwarePlatformOffset = 2 + 13
        if containsCompanyIdentifier,
           manufacturerData.indices.contains(hardwarePlatformOffset),
           manufacturerData[hardwarePlatformOffset] != 0 {
            // A watch that names its platform is trusted, even when that means rejecting
            // a model this app cannot drive.
            guard let model = WatchModel(hardwarePlatform: manufacturerData[hardwarePlatformOffset]) else {
                return nil
            }
            return AdvertisedWatch(model: model)
        }
        return AdvertisedWatch(model: model(fromName: localName))
    }

    static func model(fromName name: String?) -> WatchModel? {
        guard let normalizedName = name?.lowercased() else {
            return nil
        }
        if normalizedName.contains("duo") {
            return .pebble2Duo
        }
        if normalizedName.contains("round 2") {
            return .pebbleRound2
        }
        if normalizedName.contains("time 2") {
            return .pebbleTime2
        }
        return nil
    }
}
