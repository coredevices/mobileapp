import java.util.Properties

val properties = Properties()
if (file("local.properties").exists()) {
    file("local.properties").inputStream().use { properties.load(it) }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven {
            name = "GitHubPackagesSpeex"
            url = uri("https://maven.pkg.github.com/coredevices/kotlin-speex")
            credentials {
                username = properties.getProperty("github.username") ?: System.getenv("GITHUB_ACTOR")
                password = properties.getProperty("github.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "libpebbleroot"

include(":libpebble3")
include(":blobdbgen")
include(":blobannotations")
include(":composeApp")
include(":pebble")
include(":util")
include(":experimental")

// Include PebbleKit library until
// it is stable enough to make a release
includeBuild("PebbleKitAndroid2") {
    dependencySubstitution {
        substitute(module("io.rebble.pebblekit2:server"))
            .using(project(":server"))
    }
}
