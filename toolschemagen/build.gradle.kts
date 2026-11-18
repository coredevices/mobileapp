plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:${libs.versions.ksp.get()}")
    implementation(libs.serialization)
    testImplementation(libs.kotlin.test.junit)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}
