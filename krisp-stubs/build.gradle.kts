
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

android {
    namespace = "coredevices.krispkmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
//        compose = true
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

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64 {
    }

    iosArm64 {
    }

    iosSimulatorArm64 {
    }

    sourceSets {
        commonMain {
            dependencies {
            }
        }

        commonTest {
            dependencies {
            }
        }

        androidMain {
            dependencies {
            }
        }

        iosMain {
            dependencies {
            }
        }
    }
}