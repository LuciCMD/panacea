buildscript {
    dependencies {
        // AGP's built-in Kotlin defaults to an older compiler; this pins the one Compose is built with.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
