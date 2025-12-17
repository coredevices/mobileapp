package io.rebble.libpebblecommon.timeline

import io.rebble.libpebblecommon.util.toPebbleColor

enum class TimelineColor(
    val color: Int,
    val identifier: String,
    val displayName: String,
) {
    MintGreen(0xAAFFAA, "MintGreen", "Mint Green"),
    Melon(0xFFAAAA, "Melon", "Melon"),
    ShockingPink(0xFF55FF, "ShockingPink", "Shocking Pink (Crayola)"),
    Folly(0xFF0055, "Folly", "Folly"),
    SunsetOrange(0xFF5555, "SunsetOrange", "Sunset Orange"),
    ArmyGreen(0x555500, "ArmyGreen", "Army Green"),
    DukeBlue(0x0000AA, "DukeBlue", "Duke Blue"),
    TiffanyBlue(0x00AAAA, "TiffanyBlue", "Tiffany Blue"),
    ScreaminGreen(0x55FF55, "ScreaminGreen", "Screamin' Green"),
    PastelYellow(0xFFFFAA, "PastelYellow", "Pastel Yellow"),
    RichBrilliantLavender(0xFFAAFF, "RichBrilliantLavender", "Rich Brilliant Lavender"),
    BrightGreen(0x55FF00, "BrightGreen", "Bright Green"),
    BrilliantRose(0xFF55AA, "BrilliantRose", "Brilliant Rose"),
    CadetBlue(0x55AAAA, "CadetBlue", "Cadet Blue"),
    RoseVale(0xAA5555, "RoseVale", "Rose Vale"),
    FashionMagenta(0xFF00AA, "FashionMagenta", "Fashion Magenta"),
    JaegerGreen(0x00AA55, "JaegerGreen", "Jaeger Green"),
    BabyBlueEyes(0xAAAAFF, "BabyBlueEyes", "Baby Blue Eyes"),
    Purpureus(0xAA55AA, "Purpureus", "Purpureus"),
    ChromeYellow(0xFFAA00, "ChromeYellow", "Chrome Yellow"),
    DarkGreen(0x005500, "DarkGreen", "Dark Green (X11)"),
    Red(0xFF0000, "Red", "Red"),
    Liberty(0x5555AA, "Liberty", "Liberty"),
    LightGray(0xAAAAAA, "LightGray", "Light Gray"),
    VividViolet(0xAA00FF, "VividViolet", "Vivid Violet"),
    Rajah(0xFFAA55, "Rajah", "Rajah"),
    Indigo(0x5500AA, "Indigo", "Indigo (Web)"),
    MayGreen(0x55AA55, "MayGreen", "May Green"),
    Icterine(0xFFFF55, "Icterine", "Icterine"),
    BulgarianRose(0x550000, "BulgarianRose", "Bulgarian Rose"),
    Orange(0xFF5500, "Orange", "Orange"),
    Green(0x00FF00, "Green", "Green"),
    WindsorTan(0xAA5500, "WindsorTan", "Windsor Tan"),
    LavenderIndigo(0xAA55FF, "LavenderIndigo", "Lavender Indigo"),
    DarkGray(0x555555, "DarkGray", "Dark Gray"),
    ElectricBlue(0x55FFFF, "ElectricBlue", "Electric Blue"),
    BlueMoon(0x0055FF, "BlueMoon", "Blue Moon"),
    Cyan(0x00FFFF, "Cyan", "Cyan"),
    Black(0x000000, "Black", "Black"),
    MediumAquamarine(0x55FFAA, "MediumAquamarine", "Medium Aquamarine"),
    DarkCandyAppleRed(0xAA0000, "DarkCandyAppleRed", "Dark Candy Apple Red"),
    Limerick(0xAAAA00, "Limerick", "Limerick"),
    CobaltBlue(0x0055AA, "CobaltBlue", "Cobalt Blue"),
    Celeste(0xAAFFFF, "Celeste", "Celeste"),
    ElectricUltramarine(0x5500FF, "ElectricUltramarine", "Electric Ultramarine"),
    PictonBlue(0x55AAFF, "PictonBlue", "Picton Blue"),
    Inchworm(0xAAFF55, "Inchworm", "Inchworm"),
    Blue(0x0000FF, "Blue", "Blue"),
    VividCerulean(0x00AAFF, "VividCerulean", "Vivid Cerulean"),
    Purple(0xAA00AA, "Purple", "Purple"),
    KellyGreen(0x55AA00, "KellyGreen", "Kelly Green"),
    Malachite(0x00FF55, "Malachite", "Malachite"),
    MidnightGreen(0x005555, "MidnightGreen", "Midnight Green (Eagle Green)"),
    Yellow(0xFFFF00, "Yellow", "Yellow"),
    Magenta(0xFF00FF, "Magenta", "Magenta"),
    SpringBud(0xAAFF00, "SpringBud", "Spring Bud"),
    JazzberryJam(0xAA0055, "JazzberryJam", "Jazzberry Jam"),
    VeryLightBlue(0x5555FF, "VeryLightBlue", "Very Light Blue"),
    White(0xFFFFFF, "White", "White"),
    IslamicGreen(0x00AA00, "IslamicGreen", "Islamic Green"),
    OxfordBlue(0x000055, "OxfordBlue", "Oxford Blue"),
    ImperialPurple(0x550055, "ImperialPurple", "Imperial Purple"),
    Brass(0xAAAA55, "Brass", "Brass"),
    MediumSpringGreen(0x00FFAA, "MediumSpringGreen", "Medium Spring Green"),
    ;

    companion object {
        fun findByName(name: String?): TimelineColor? = entries.find { it.name.equals(name, ignoreCase = true) }
    }
}

fun TimelineColor.argbColor() = color or 0xFF000000.toInt()

fun TimelineColor.toPebbleColor() = argbColor().toPebbleColor()