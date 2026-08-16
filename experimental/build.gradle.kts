
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.nativeCocoaPods)
}

compose.resources {
    packageOfResClass = "coreapp.ring.generated.resources"
}

// Set by the IDE during sync only. The simulator target doubles the pod/cinterop work sync waits
// on; command-line and Xcode builds still configure it.
val ideSync = providers.systemProperty("idea.sync.active").orNull.toBoolean()

kotlin {
    val xcodeExists = providers.exec {
        isIgnoreExitValue = true
        commandLine("which", "xcode-select")
    }.result.get().exitValue == 0
    val xcodeDir = if (xcodeExists) {
        providers.exec {
            commandLine("xcode-select", "-p")
        }.standardOutput.asText.get().trim()
    } else {
        ""
    }
    android {
        namespace = "coredevices.ring"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildList {
        add(iosArm64())
        if (!ideSync) add(iosSimulatorArm64())
    }.forEach {
        it.binaries.getTest(NativeBuildType.DEBUG).apply {
            if (xcodeExists) {
                linkerOpts.addAll(listOf("-Wl,-weak-lswift_Concurrency", "-Wl,-rpath,/usr/lib/swift"))
            }
        }
        it.binaries.all {
            val osName =
                if (target.konanTarget.name.contains("simulator")) "iphonesimulator" else "iphoneos"
            if (xcodeExists) {
                linkerOpts.addAll(listOf(
                    "-weak_framework", "CoreML",
                    "-L$xcodeDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$osName"
                ))
            }
            // The binary FirebaseFirestoreInternal is static, so its C deps must be linked into
            // every binary that pulls in Firestore, not just the pod framework.
            val grpcSlice = if (target.konanTarget.name.contains("simulator")) {
                "ios-arm64_x86_64-simulator"
            } else {
                "ios-arm64"
            }
            listOf(
                "FirebaseFirestoreGRPCCoreBinary/grpc.xcframework" to "grpc",
                "FirebaseFirestoreGRPCCPPBinary/grpcpp.xcframework" to "grpcpp",
                "FirebaseFirestoreGRPCBoringSSLBinary/openssl_grpc.xcframework" to "openssl_grpc",
                "FirebaseFirestoreAbseilBinary/absl.xcframework" to "absl",
            ).forEach { (path, fw) ->
                val sliceDir = layout.buildDirectory
                    .dir("cocoapods/synthetic/ios/Pods/$path/$grpcSlice")
                    .get().asFile
                linkerOpts.addAll(listOf("-F" + sliceDir.absolutePath, "-framework", fw))
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
        }
        pod("GoogleSignIn") {
            version = "8.0.0"
            linkOnly = true
        }
        pod("FirebaseCore", "11.10.0")
        pod("FirebaseAuth") {
            version = "11.10.0"
            linkOnly = true
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
        pod("FirebaseFirestore") {
            linkOnly = true
            source = git("https://github.com/invertase/firestore-ios-sdk-frameworks.git") {
                // 11.10.0 — keep in step with :composeApp, or the two modules resolve
                // different Firestore builds
                commit = "e43715cc392c819b522c7a189bed9400e757c788"
            }
        }
        pod("nanopb") {
            version = "3.30910.0"
            linkOnly = true
        }
        pod("leveldb-library") {
            version = "1.22.6"
            moduleName = "leveldb"
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
                implementation(project(":libindex"))
                implementation(project(":libpebble3"))
                implementation(libs.settings)
                implementation(libs.kable)
                implementation(libs.uri)
                implementation(libs.kmpnotifier)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.settings.test)
                implementation(libs.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                // gitlive's compile variant declares com.google.firebase:* without versions.
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.androidx.glance)
                implementation(libs.androidx.glance.material3)
                implementation(compose.uiTooling)
                implementation(libs.identity.google)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.credentials)
                implementation(libs.zxing.core)
                implementation(libs.androidx.documentfile)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.kotlin.test)
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
        buildConfigField(FieldSpec.Type.STRING, "NENYA_URL", "https://nenya.repebble.com")
        buildConfigField(FieldSpec.Type.STRING, "NOTION_OAUTH_BACKEND_URL", "https://index-oauth.repebble.com")

        buildConfigField(FieldSpec.Type.STRING, "TESTS_NOTION_TOKEN", System.getenv("TESTS_NOTION_TOKEN") ?: properties.getProperty("TESTS_NOTION_TOKEN") ?: "")
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    if (!ideSync) {
        //add("kspIosX64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }
}
