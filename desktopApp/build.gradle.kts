plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.websockets)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.turbine)
                implementation(libs.ktor.server.test.host)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.vcompanion.desktop.MainKt"
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() && file(it).exists() }?.let {
            javaHome = it
        } ?: run {
            val localJdk = "${System.getProperty("user.home")}/.jdks/corretto-24.0.2"
            if (file(localJdk).exists()) {
                javaHome = localJdk
            }
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "VirtualStudioCompanion"
            packageVersion = "1.0.0"
            description = "Virtual Studio Companion Windows Host"
            vendor = "Virtual Studio Companion"
            copyright = "Copyright (c) 2026"

            windows {
                menuGroup = "Virtual Studio"
                upgradeUuid = "6b4d32a0-8f1b-4f40-8b1b-3b7c2598f821"
            }
        }
    }
}
