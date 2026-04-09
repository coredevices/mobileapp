package coredevices.ring.agent.builtin_servlets.js

interface JsEngine {
    suspend fun evaluate(js: String): String
}