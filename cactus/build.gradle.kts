import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
                }
            }
        }
    }

    android {
        namespace = "com.cactus"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosLibDir = project.file("src/commonMain/resources/ios/lib")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val libSubdir = when (target.name) {
            "iosArm64" -> "ios-arm64"
            else -> "ios-arm64-simulator"
        }
        target.compilations.getByName("main") {
            cinterops {
                create("cactus") {
                    defFile("src/nativeInterop/cinterop/cactus.def")
                    includeDirs("src/nativeInterop/cinterop")
                    extraOpts("-libraryPath", iosLibDir.resolve(libSubdir).absolutePath)
                }
            }
        }
        target.binaries.all {
            linkerOpts("-lc++")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization)
        }
        androidMain.dependencies {
            api(project(":cactus-native"))
        }
    }
}
