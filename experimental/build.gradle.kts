
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.nativeCocoaPods)
}

android {
    namespace = "coredevices.ring"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "coreapp.ring.generated.resources"
}

kotlin {
    val xcodeExists = providers.exec {
        isIgnoreExitValue = true
        commandLine("which", "xcode-select")
    }.result.get().exitValue == 0
    androidTarget {
        publishLibraryVariants("release", "debug")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

// Target declarations - add or remove as needed below. These define
// which platforms this KMP module supports.
// See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets


// For iOS targets, this is also where you should
// configure native binary output. For more information, see:
// https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

// A step-by-step guide on how to include this library in an XCode
// project can be found here:
// https://developer.android.com/kotlin/multiplatform/migrate
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.getTest(NativeBuildType.DEBUG).apply {
            val xcodeExists = providers.exec {
                isIgnoreExitValue = true
                commandLine("which", "xcode-select")
            }.result.get().exitValue == 0
            if (xcodeExists) {
                val xcodeDir = providers.exec {
                    commandLine("xcode-select", "-p")
                }.standardOutput.asText.get().trim()
                linkerOpts.addAll(listOf(
                    "-weak_framework", "CoreML",
                    "-L$xcodeDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator",
                    "-Wl,-weak-lswift_Concurrency", "-Wl,-rpath,/usr/lib/swift"
                ))
            }
        }
    }

    cocoapods {
        ios.deploymentTarget = "15.6"
        version = "1.0"
        summary = "CoreDevices Ring Module"
        homepage = "https://repebble.com"
        license = "proprietary"
        framework {
            baseName = "RingModule"
            isStatic = false
            if (xcodeExists) {
                val xcodeDir = providers.exec {
                    commandLine("xcode-select", "-p")
                }.standardOutput.asText.get().trim()
                linkerOpts.addAll(listOf("-weak_framework", "CoreML", "-L$xcodeDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator"))
            }
        }
        pod("GoogleSignIn", "8.0.0")
        pod("FirebaseCore", "11.10.0")
        pod("FirebaseAuth") {
            version = "11.10.0"
            linkOnly = true
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
        pod("FirebaseFirestore") {
            version = "11.10.0"
            linkOnly = true
        }
        pod("FirebaseStorage") {
            version = "11.10.0"
            linkOnly = true
        }
        pod("FirebaseCrashlytics") {
            version = "11.10.0"
            linkOnly = true
        }
        pod("FirebaseMessaging") {
            version = "11.10.0"
            linkOnly = true
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
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlin.time.ExperimentalTime")
            }
        }
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.paging.compose)
                implementation(libs.backhandler)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(compose.components.resources)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kermit)
                implementation(project(":util"))
                implementation(libs.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.settings)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.sqlite.bundled)

                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
                implementation(libs.firebase.crashlytics)
                implementation(libs.firebase.storage)

                implementation(project(":mcp"))
                implementation(project(":index-ai"))
                implementation(project(":resampler"))
                implementation(libs.coredevices.haversine)
                implementation(project(":cactus"))
                implementation(libs.settings)
                implementation(libs.kable)
                implementation(libs.uri)
                implementation(libs.kmpnotifier)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.glance)
                implementation(libs.androidx.glance.material3)
                implementation(compose.uiTooling)
                implementation(libs.identity.google)
            }
        }

        androidInstrumentedTest {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }

        iosMain {
            dependencies {
                implementation(libs.okio)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

val properties = Properties().apply {
    try {
        load(rootDir.resolve("local.properties").reader())
    } catch (e: Exception) {
        println("local.properties file not found")
    }
}

buildkonfig {
    packageName = "coredevices.ring"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "NENYA_URL", "https://nenya-staging-460977838956.us-west1.run.app")
        buildConfigField(FieldSpec.Type.STRING, "NOTION_OAUTH_BACKEND_URL", "https://index-oauth-460977838956.us-west1.run.app")
        buildConfigField(FieldSpec.Type.STRING, "GCLOUD_DICTATION_URL", "https://gcloud-dictation-460977838956.us-central1.run.app/recognize")

        buildConfigField(FieldSpec.Type.STRING, "TESTS_NOTION_TOKEN", System.getenv("TESTS_NOTION_TOKEN") ?: properties.getProperty("TESTS_NOTION_TOKEN") ?: "")
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    if (System.getenv("CI_RELEASE") != "true") {
        //add("kspIosX64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }
}
