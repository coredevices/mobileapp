/// Several revisions share a watch model — a Pebble Time 2 can be any of the
/// obelix boards — and firmware is built per board.
public enum WatchBoard: String, CaseIterable, Codable, Sendable {
    case asterix
    case obelixEVT = "obelix_evt"
    case obelixDVT = "obelix_dvt"
    case obelixPVT = "obelix_pvt"
    case obelixBigboard = "obelix_bb"
    case obelixBigboard2 = "obelix_bb2"
    case getafixEVT = "getafix_evt"
    case getafixDVT = "getafix_dvt"
    case getafixDVT2 = "getafix_dvt2"
    case robertEVT = "robert_evt"
    case robertBigboard = "robert_bb"
    case robertBigboard2 = "robert_bb2"
    /// The three boards PebbleOS builds for the emulator, named as its own
    /// `boards/` directories are. They were missing, so a watch in QEMU had no
    /// board at all — which is the only thing the firmware screen goes on, and
    /// left the diagnostic log writing "platform 245" for want of a name.
    ///
    /// They have no release asset: `build-qemu.yml` is a separate workflow from
    /// the `build-firmware.yml` that publishes `normal_<board>_<version>.pbz`.
    /// So the firmware catalogue answers `noFirmwareForBoard`, which is a
    /// sentence, where a nil board was silence.
    case qemuEmery = "qemu_emery"
    case qemuFlint = "qemu_flint"
    case qemuGabbro = "qemu_gabbro"

    /// The platform byte the firmware puts in its version response.
    ///
    /// These are `FirmwareMetadataPlatform` in PebbleOS's
    /// `include/pebbleos/firmware_metadata.h`, and only the ones that file
    /// still has a `FIRMWARE_METADATA_HW_PLATFORM` mapping for are here — the
    /// classic Pebbles are in that enum too, and this app cannot drive them.
    public init?(hardwarePlatform: UInt8) {
        switch hardwarePlatform {
        case 13: self = .robertEVT
        case 15: self = .asterix
        case 16: self = .obelixEVT
        case 17: self = .obelixDVT
        case 18: self = .obelixPVT
        case 19: self = .getafixEVT
        case 20: self = .getafixDVT
        case 21: self = .getafixDVT2
        case 242: self = .qemuGabbro
        case 243: self = .obelixBigboard2
        case 244: self = .obelixBigboard
        case 245: self = .qemuEmery
        case 246: self = .qemuFlint
        case 247: self = .robertBigboard2
        case 249: self = .robertBigboard
        default: return nil
        }
    }
}

public extension WatchBoard {
    /// What this board was built with, read out of `boards/<name>/defconfig`
    /// in PebbleOS. These gate settings rows: a switch for hardware the watch
    /// does not have is a row that does nothing in front of the reader.
    ///
    /// Per board and not per model, because the emulator differs from the
    /// hardware it stands in for: `qemu_emery` is a Pebble Time 2 to
    /// `WatchModel`, but its defconfig has no `CONFIG_DYNAMIC_BACKLIGHT`
    /// where the real board's does — gating by model would offer the emulated
    /// watch a row its whitelist refuses.
    ///
    /// The robert boards answer false throughout: they have no `boards/`
    /// directory in this PebbleOS tree, so what they were built with cannot be
    /// read, and a row that might do nothing is worse than no row.

    /// `CONFIG_TOUCH`. Gates the touch-wake row; the `lightTouch` pref itself
    /// exists on every board and is compared against presets everywhere.
    var hasTouch: Bool {
        switch self {
        case .getafixEVT, .getafixDVT, .getafixDVT2,
             .obelixEVT, .obelixDVT, .obelixPVT, .obelixBigboard, .obelixBigboard2,
             .qemuEmery, .qemuGabbro:
            true
        default:
            false
        }
    }

    /// `CONFIG_DYNAMIC_BACKLIGHT`. Unlike the other two, this one gates the
    /// *whitelist* itself (`settings_blob_db.c`): a board without it answers
    /// a `lightDynamicMode` write with `E_INVALID_OPERATION`.
    var hasDynamicBacklight: Bool {
        switch self {
        case .getafixEVT, .getafixDVT, .getafixDVT2,
             .obelixEVT, .obelixDVT, .obelixPVT, .obelixBigboard, .obelixBigboard2:
            true
        default:
            false
        }
    }

    /// `CONFIG_BACKLIGHT_HAS_COLOR`, selected by the LED driver: obelix's
    /// AW2016 and the emulator's `BACKLIGHT_QEMU_COLOR` have colour; getafix's
    /// AW9364E and asterix's PWM do not. Also whitelist-gating, for
    /// `lightColor`.
    var hasColorBacklight: Bool {
        switch self {
        case .obelixEVT, .obelixDVT, .obelixPVT, .obelixBigboard, .obelixBigboard2,
             .qemuEmery:
            true
        default:
            false
        }
    }

    /// `CONFIG_HRM`. Only obelix carries a heart-rate sensor — the Goodix
    /// GH3X2X (`CONFIG_HRM_GH3X2X`), whose red/IR path is also what the firmware
    /// samples blood oxygen (SpO2) on. asterix (Pebble 2 Duo) and getafix
    /// (Pebble Round 2) have no `CONFIG_HRM` at all, so they measure neither.
    /// The emulator's `qemu_emery` sets `CONFIG_HRM` with a stub, standing in
    /// for a Pebble Time 2. Gates the Heart Rate and Blood Oxygen rows: a
    /// reading a watch cannot take is a row that does nothing.
    var hasHeartRateSensor: Bool {
        switch self {
        case .obelixEVT, .obelixDVT, .obelixPVT, .obelixBigboard, .obelixBigboard2,
             .qemuEmery:
            true
        default:
            false
        }
    }
}

public extension WatchBoard {
    /// Several boards make up one model. The emulator's three are named for
    /// the models they stand in for: PebbleOS builds `qemu_flint`,
    /// `qemu_emery` and `qemu_gabbro`, and those are the raw values below.
    var model: WatchModel {
        switch self {
        case .asterix, .qemuFlint:
            .pebble2Duo
        case .robertEVT, .robertBigboard, .robertBigboard2,
             .obelixEVT, .obelixDVT, .obelixPVT, .obelixBigboard, .obelixBigboard2,
             .qemuEmery:
            .pebbleTime2
        case .getafixEVT, .getafixDVT, .getafixDVT2, .qemuGabbro:
            .pebbleRound2
        }
    }
}

/// What an application is built for: the folder names inside a `.pbw` and the
/// store's `hardware_platforms`, which are one vocabulary (libpebble3's
/// `WatchType`).
///
/// Only the names a watch this app drives can run. A package or a store row
/// naming anything else says nothing any of them could install, so the
/// decoding boundaries drop such a name rather than carry it as a string.
public enum WatchPlatform: String, CaseIterable, Codable, Sendable {
    case aplite, basalt, chalk, diorite, emery, flint, gabbro

    /// The names that are platforms, in the order given, and nothing else.
    public static func known(in names: [String]) -> [WatchPlatform] {
        names.compactMap(WatchPlatform.init(rawValue:))
    }
}

public enum WatchModel: String, CaseIterable, Codable, Sendable {
    case pebble2Duo = "FLINT"
    case pebbleTime2 = "EMERY"
    case pebbleRound2 = "GABBRO"

    /// Which watch a platform byte belongs to, by way of its board.
    public init?(hardwarePlatform: UInt8) {
        guard let board = WatchBoard(hardwarePlatform: hardwarePlatform) else { return nil }
        self = board.model
    }

    /// The platforms this watch runs, its own first and then the older ones it
    /// is compatible with, in the order a package's variants are tried.
    public var compatiblePlatforms: [WatchPlatform] {
        switch self {
        case .pebble2Duo:
            [.flint, .diorite, .aplite]
        case .pebbleTime2:
            [.emery, .basalt, .diorite, .aplite]
        case .pebbleRound2:
            [.gabbro, .chalk]
        }
    }

    /// The watch's own platform, which is what the store is asked for.
    public var platform: WatchPlatform {
        compatiblePlatforms[0]
    }

    public var displayName: String {
        switch self {
        case .pebble2Duo:
            "Pebble 2 Duo"
        case .pebbleTime2:
            "Pebble Time 2"
        case .pebbleRound2:
            "Pebble Round 2"
        }
    }
}
