package coredevices.toolschemagen

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.io.File

private const val TOOL_DEFINITION = "coredevices.toolschema.ToolDefinition"
private const val TOOL_PARAM = "coredevices.toolschema.ToolParam"
private const val SERIAL_NAME = "kotlinx.serialization.SerialName"

private const val OPTION_PACKAGE = "toolSchema.package"
private const val OPTION_MODEL_VERSION = "toolSchema.localModelVersion"
private const val OPTION_CHECKPOINT_DIR = "toolSchema.localModelCheckpointDir"
private const val OPTION_EXCLUDED_SERVLETS = "toolSchema.localModelExcludedServlets"

class ToolSchemaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ToolSchemaProcessor(environment)
}

class ToolSchemaProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()
        processed = true

        // Every file, so an incremental run still sees the tools it didn't recompile.
        val files = resolver.getAllFiles().toList()
        val tools = files
            .flatMap { file -> file.declarations.flatMap { it.withNested() } }
            .mapNotNull { it.toToolModel() }
            .sortedWith(compareBy({ it.servlet }, { it.name }))
        if (tools.isEmpty()) return emptyList()

        val packageName = option(OPTION_PACKAGE)
        val checkpoint = loadOrCreateCheckpoint(
            dir = File(option(OPTION_CHECKPOINT_DIR)),
            modelVersion = option(OPTION_MODEL_VERSION),
            excludedServlets = option(OPTION_EXCLUDED_SERVLETS).split(",").filter { it.isNotBlank() },
            tools = tools,
        )
        write(files, packageName, DEFINITIONS_FILE, renderDefinitions(packageName, tools))
        write(files, packageName, CHECKPOINT_FILE, renderCheckpoint(packageName, checkpoint))
        return emptyList()
    }

    private fun option(name: String): String =
        environment.options[name] ?: error("Missing KSP option $name")

    private fun write(sources: List<KSFile>, packageName: String, fileName: String, content: String) {
        val dependencies = Dependencies(aggregating = true, *sources.toTypedArray())
        environment.codeGenerator.createNewFile(dependencies, packageName, fileName)
            .use { it.write(content.toByteArray()) }
    }

    private fun KSDeclaration.withNested(): Sequence<KSClassDeclaration> {
        if (this !is KSClassDeclaration) return emptySequence()
        return sequenceOf(this) + declarations.flatMap { it.withNested() }
    }

    private fun KSClassDeclaration.toToolModel(): ToolModel? {
        val definition = annotation(TOOL_DEFINITION) ?: return null
        // Absent when defaulted on some targets (iosArm64). The default, Nothing, has no annotated properties either.
        val output = (definition.arguments.firstOrNull { it.name?.asString() == "output" }?.value as? KSType)
            ?.declaration as? KSClassDeclaration
        return ToolModel(
            servlet = definition.string("servlet"),
            name = definition.string("name"),
            description = definition.string("description"),
            parameters = toolParameters(),
            outputParameters = output?.toolParameters()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun KSClassDeclaration.toolParameters(): List<ToolParameterModel> {
        val properties = getDeclaredProperties().associateBy { it.simpleName.asString() }
        return primaryConstructor?.parameters.orEmpty().mapNotNull { parameter ->
            val property = properties[parameter.name?.asString()] ?: return@mapNotNull null
            val param = property.annotation(TOOL_PARAM) ?: return@mapNotNull null
            ToolParameterModel(
                name = property.annotation(SERIAL_NAME)?.string("value")
                    ?: property.simpleName.asString().toSnakeCase(),
                type = property.schemaType(),
                description = param.string("description").ifEmpty { null },
                required = !parameter.hasDefault && !property.type.resolve().isMarkedNullable,
            )
        }
    }

    private fun KSPropertyDeclaration.schemaType(): String =
        when (val name = type.resolve().declaration.qualifiedName?.asString()) {
            "kotlin.String" -> "string"
            "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double" -> "number"
            "kotlin.Boolean" -> "boolean"
            else -> error("Unsupported tool parameter type $name on ${qualifiedName?.asString()}")
        }

    private fun KSAnnotated.annotation(qualifiedName: String): KSAnnotation? = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
    }

    private fun KSAnnotation.string(argument: String): String =
        arguments.first { it.name?.asString() == argument }.value as String
}
