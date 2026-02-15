
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("schema")
}

android {
    namespace = "coredevices.util"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "coreapp.util.generated.resources"
}

kotlin {

// Target declarations - add or remove as needed below. These define
// which platforms this KMP module supports.
// See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidTarget {
        publishLibraryVariants("release", "debug")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

// For iOS targets, this is also where you should
// configure native binary output. For more information, see:
// https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

// A step-by-step guide on how to include this library in an XCode
// project can be found here:
// https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "coreapp-util"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

// Source set declarations.
// Declaring a target automatically creates a source set with the same name. By default, the
// Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
// common to share sources between related targets.
// See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.uuid.ExperimentalUuidApi")
            }
        }
        commonMain {
            dependencies {
                implementation(libs.kotlinx.io.core)
                implementation(libs.coroutines)
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(libs.ktor.client.core)
                implementation(libs.kermit)
                implementation(libs.serialization)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.client.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.webview)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.cactus)
                implementation(project(":libpebble3"))
                implementation(libs.kmpio)
                api(libs.room.runtime)
                implementation(libs.sqlite.bundled)
                api(libs.settings)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.okhttp)
                implementation(compose.uiTooling)
                implementation(libs.play.update)
                implementation(libs.play.update.ktx)
                implementation(libs.mixpanel.android)
            }
        }

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }

}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

val headSha by lazy {
    project.providers.exec {
        commandLine("git", "describe", "--always", "--dirty")
    }.standardOutput.asText.get().trim()
}

val properties = Properties().apply {
    try {
        load(rootDir.resolve("local.properties").reader())
    } catch (e: Exception) {
        println("local.properties file not found")
    }
}
val enableQa = System.getenv("QA")?.toBoolean() ?: properties.getProperty("QA")?.toBoolean() ?: true

fun gradleStringPropOrNull(name: String): String? {
    val prop = providers.gradleProperty(name).orNull ?: return null
    if (prop.isEmpty()) return null
    return prop
}

buildkonfig {
    packageName = "coredevices.util"
    objectName = "CommonBuildKonfig"
    exposeObjectWithName = "CommonBuildKonfig"

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "GIT_HASH", headSha)
        buildConfigField(FieldSpec.Type.BOOLEAN, "QA", enableQa.toString())
        buildConfigField(FieldSpec.Type.STRING, "USER_AGENT_VERSION", headSha)
        buildConfigField(FieldSpec.Type.STRING, "BUG_URL", gradleStringPropOrNull("bugUrl"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "TOKEN_URL", gradleStringPropOrNull("tokenUrl"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "GITHUB_CLIENT_ID", gradleStringPropOrNull("githubClientId"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "GITHUB_CLIENT_SECRET", gradleStringPropOrNull("githubClientSecret"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "MIXPANEL_TOKEN", gradleStringPropOrNull("mixpanelToken"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "WISPR_KEY", gradleStringPropOrNull("wisprKey"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "MEMFAULT_TOKEN", gradleStringPropOrNull("memfaultToken"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "GOOGLE_CLIENT_ID", gradleStringPropOrNull("googleClientId"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "CACTUS_PRO_KEY", gradleStringPropOrNull("cactusProKey"), nullable = true)
        buildConfigField(FieldSpec.Type.STRING, "CACTUS_LM_MODEL_NAME", "qwen3-0.6")
    }
}