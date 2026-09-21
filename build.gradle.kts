plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("allTests") {
    group = "verification"
    description = "Coordina en paralelo todas las pruebas unitarias disponibles del ecosistema KMP."

    dependsOn(
        ":shared:desktopTest",
        ":desktopApp:desktopTest"
    )

    val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
            providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
            rootProject.file("local.properties").let { file ->
                file.exists() && file.readLines().any { it.trim().startsWith("sdk.dir") }
            }

    if (hasAndroidSdk) {
        dependsOn(
            ":shared:testDebugUnitTest",
            ":androidApp:testDebugUnitTest"
        )
    }
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Ejecuta validaciones estáticas, linters y auditoría de calidad de código."

    val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
            providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
            rootProject.file("local.properties").let { file ->
                file.exists() && file.readLines().any { it.trim().startsWith("sdk.dir") }
            }

    if (hasAndroidSdk) {
        dependsOn(":shared:lintDebug")
    }
}

