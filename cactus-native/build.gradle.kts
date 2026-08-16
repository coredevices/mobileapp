// The KMP Android library plugin has no NDK support, so the CMake build and the prebuilt
// libcactus_engine.so live in this plain Android library, consumed by :cactus.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cactus.nativelib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
