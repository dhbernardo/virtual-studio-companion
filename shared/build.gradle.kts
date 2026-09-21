plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("com.android.library") apply false
}

val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
        providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
        rootProject.file("local.properties").let { file ->
            file.exists() && file.readLines().any { it.trim().startsWith("sdk.dir") }
        }

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    jvm("desktop")

    if (hasAndroidSdk) {
        androidTarget {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val desktopTest by getting

        if (hasAndroidSdk) {
            val androidMain by getting
        }
    }
}

if (hasAndroidSdk) {
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.vcompanion.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.vcompanion.shared.resources"
    generateResClass = always
}
