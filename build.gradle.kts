// Top-level build file: plugin versions are declared here and applied in the modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere: AGP ships built-in Kotlin support. Declaring the plugin here
    // only pins the Kotlin Gradle plugin version that the built-in support uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
