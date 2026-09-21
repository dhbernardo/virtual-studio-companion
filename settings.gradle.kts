rootProject.name = "virtual-studio-companion"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared")
include(":desktopApp")

// Evaluación condicional del SDK de Android para compilar Desktop aun en ausencia de ANDROID_HOME
val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
        providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
        file("local.properties").let { localProp ->
            localProp.exists() && localProp.readLines().any { it.trim().startsWith("sdk.dir") }
        }

if (hasAndroidSdk) {
    include(":androidApp")
} else {
    logger.lifecycle("[Setup] Android SDK no detectado. Omitiendo inclusión de :androidApp para compilación independiente de Desktop.")
}
