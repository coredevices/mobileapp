package coredevices.toolschema

import kotlin.reflect.KClass

/**
 * Declares a built-in tool on its arguments class. `:toolschemagen` builds the tool's schema from
 * the [ToolParam] properties: one is required when it is non-null with no default. [output] is the
 * result class to build an output schema from the same way, if the tool declares one.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ToolDefinition(
    val servlet: String,
    val name: String,
    val description: String,
    val output: KClass<*> = Nothing::class,
)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ToolParam(val description: String)
