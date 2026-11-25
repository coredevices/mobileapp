package io.rebble.libpebblecommon.metadata

import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform.CORE_GETAFIX_EVT

private const val Silver = 0xFFC4C6C8
private const val Gunmetal = 0xFF55585C
private const val Gold = 0xFFD4B36A

/** [color] and [accentColor] are ARGB. */
public enum class WatchColor(public val protocolNumber: Int, public val jsName: String, public val uiDescription: String, public val platform: WatchType, public val color: Long = 0xFF000000, public val accentColor: Long? = null) {
    ClassicBlack(1, "pebble_black", "Pebble Classic - Black", WatchType.APLITE),
    ClassicWhite(2, "pebble_white", "Pebble Classic - White", WatchType.APLITE, color = 0xFFFFFFFF),
    ClassicRed(3, "pebble_red", "Pebble Classic - Red", WatchType.APLITE, color = 0xFFFF0000),
    ClassicOrange(4, "pebble_orange", "Pebble Classic - Orange", WatchType.APLITE, color = 0xFFFA4A36),
    ClassicPink(5, "pebble_pink", "Pebble Classic - Pink", WatchType.APLITE, color = 0xFFF7B5C9),
    ClassicSteelSilver(6, "pebble_steel_silver", "Pebble Steel - Silver", WatchType.APLITE, color = Silver),
    ClassicSteelGunmetal(7, "pebble_steel_gunmetal", "Pebble Steel - Gunmetal", WatchType.APLITE, color = Gunmetal),
    ClassicFlyBlue(8, "pebble_fly_blue", "Pebble Classic - Fly BLue", WatchType.APLITE, color = 0xFF0000FF),
    ClassicFreshGreen(9, "pebble_fresh_green", "Pebble Classic - Fresh Green", WatchType.APLITE, color = 0xFF00FF00),
    ClassicHotPink(10, "pebble_hot_pink", "Pebble Classic - Hot Pink", WatchType.APLITE, color = 0xFFFF4F9A),
    TimeWhite(11, "pebble_time_white", "Pebble Time - White", WatchType.BASALT, color = 0xFFFFFFFF),
    TimeBlack(12, "pebble_time_black", "Pebble Time - Black", WatchType.BASALT),
    TimeRed(13, "pebble_time_red", "Pebble Time - Red", WatchType.BASALT, color = 0xFFFF0000),
    TimeSteelSilver(14, "pebble_time_steel_silver", "Pebble Time Steel - Silver", WatchType.BASALT, color = Silver),
    TimeSteelGunmetal(15, "pebble_time_steel_black", "Pebble Time Steel - Black", WatchType.BASALT),
    TimeSteelGold(16, "pebble_time_steel_gold", "Pebble Time Steel - Gold", WatchType.BASALT, color = Gold),
    TimeRoundSilver14(17, "pebble_time_round_silver", "Pebble Time Round - Silver", WatchType.CHALK, color = Silver),
    TimeRoundBlack14(18, "pebble_time_round_black", "Pebble Time Round - Black", WatchType.CHALK),
    TimeRoundSilver20(19, "pebble_time_round_silver_20", "Pebble Time Round - Silver", WatchType.CHALK, color = Silver),
    TimeRoundBlack20(20, "pebble_time_round_black_20", "Pebble Time Round - Black", WatchType.CHALK),
    TimeRoundRoseGold14(21, "pebble_time_round_rose_gold", "Pebble Time Round - Rose Gold", WatchType.CHALK, color = 0xFFE3B2A3),
    TimeRoundRainbowSilver14(22, "pebble_time_round_silver_rainbow", "Pebble Time Round - Silver Rainbow", WatchType.CHALK, color = Silver),
    TimeRoundRainbowBlack20(23, "pebble_time_round_black_rainbow", "Pebble Time Round - Black Rainbow", WatchType.CHALK),
    // Was: 34
    TimeRoundBlackSilverPolish20(-999, "pebble_time_round_polished_silver", "Pebble Time Round - Polished Silver", WatchType.CHALK, color = Silver),
    // Was: 35
    TimeRoundBlackGoldPolish20(-999, "pebble_time_round_polished_gold", "Pebble Time Round - Polished Gold", WatchType.CHALK, color = Gold),
    Pebble2SEBlack(24, "pebble_2_se_black_charcoal", "Pebble 2 SE - Black Charcoal", WatchType.DIORITE),
    Pebble2HRBlack(25, "pebble_2_hr_black_charcoal", "Pebble 2 HR - Black Charcoal", WatchType.DIORITE),
    Pebble2SEWhite(26, "pebble_2_se_white_gray", "Pebble 2 SE - White/Gray", WatchType.DIORITE, color = 0xFFFFFFFF),
    Pebble2HRLime(27, "pebble_2_hr_charcoal_sorbet_green", "Pebble 2 HR - Sorbet Green", WatchType.DIORITE, color = 0xFFB8E04A),
    Pebble2HRFlame(28, "pebble_2_hr_charcoal_red", "Pebble 2 HR - Charcoal Red", WatchType.DIORITE, color = 0xFFFF0000),
    Pebble2HRWhite(29, "pebble_2_hr_white_gray", "Pebble 2 HR - White Gray", WatchType.DIORITE, color = 0xFFFFFFFF),
    Pebble2HRAqua(30, "pebble_2_hr_white_turquoise", "Pebble 2 HR - White Turquoise", WatchType.DIORITE, color = 0xFF3FD1C4),
    Time2Gunmetal(31, "pebble_time_2_black", "Pebble Time 2 - Black", WatchType.EMERY, color = Gunmetal),
    Time2Silver(32, "pebble_time_2_silver", "Pebble Time 2 - Silver", WatchType.EMERY, color = Silver),
    Time2Gold(33, "pebble_time_2_gold", "Pebble Time 2 - Gold", WatchType.EMERY, color = Gold),
    Pebble2DuoBlack(34, "pebble_2_duo_black", "Pebble 2 Duo - Black", WatchType.FLINT),
    Pebble2DuoWhite(35, "pebble_2_duo_white", "Pebble 2 Duo - White", WatchType.FLINT, color = 0xFFFFFFFF),
    PebbleTime2BlackGray(36, "pebble_time_2_black_gray", "Pebble Time 2 - Black/Gray", WatchType.EMERY, accentColor = 0xFF888888),
    PebbleTime2BlackRed(37, "pebble_time_2_black_red", "Pebble Time 2 - Black/Red", WatchType.EMERY, accentColor = 0xFFFF0000),
    PebbleTime2SilverBlue(38, "pebble_time_2_silver_blue", "Pebble Time 2 - Silver/Blue", WatchType.EMERY, color = Silver, accentColor = 0xFF3A6FD8),
    PebbleTime2SilverGray(39, "pebble_time_2_silver_gray", "Pebble Time 2 - Silver/Gray", WatchType.EMERY, color = Silver, accentColor = 0xFF888888),
    PebbleRound2Black(40, "pebble_round_2_black", "Pebble Round 2 - Black", WatchType.GABBRO),
    PebbleRound2Silver(41, "pebble_round_2_silver", "Pebble Round 2 - Silver", WatchType.GABBRO, color = Silver),
    PebbleRound2Gold(42, "pebble_round_2_gold", "Pebble Round 2 - Gold", WatchType.GABBRO, color = Gold),
    PebbleRound2Silver14(43, "pebble_round_2_silver_14", "Pebble Round 2 - Silver", WatchType.GABBRO, color = Silver),
    Unknown(-1, "unknown_unknown", "Unknown Watch!", WatchType.APLITE);

    public companion object {
        public fun fromProtocolNumber(protocolNumber: Int?): WatchColor {
            return entries.firstOrNull { it.protocolNumber == protocolNumber } ?: Unknown
        }
    }
}

public fun WatchColor.supportsHrm(): Boolean = when (this) {
    WatchColor.Pebble2HRBlack,
    WatchColor.Pebble2HRLime,
    WatchColor.Pebble2HRFlame,
    WatchColor.Pebble2HRWhite,
    WatchColor.Pebble2HRAqua,
    WatchColor.Time2Gunmetal,
    WatchColor.Time2Silver,
    WatchColor.Time2Gold,
    WatchColor.PebbleTime2BlackGray,
    WatchColor.PebbleTime2BlackRed,
    WatchColor.PebbleTime2SilverBlue,
    WatchColor.PebbleTime2SilverGray -> true
    else -> false
}