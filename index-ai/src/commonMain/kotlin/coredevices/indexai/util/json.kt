package coredevices.indexai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

val JsonSnake = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
}