package coredevices.util

/** Uppercases the first letter unless the first word is already mixed-case (e.g. "iPhone"). */
fun String.capitalizeFirstWord(): String =
    if (takeWhile { !it.isWhitespace() }.any { it.isUpperCase() }) this
    else replaceFirstChar { it.uppercase() }
