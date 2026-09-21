# Desglose de Tareas: 000-architecture-and-tech-stack

## Fase 1: Inicialización del Catálogo y Configuración Raíz
- [x] **T01: Creación del catálogo de versiones centralizado (`gradle/libs.versions.toml`)**  
  *Requisitos cubiertos:* `RF-003`, `RNF-003`  
  *Hecho cuando:* El archivo `gradle/libs.versions.toml` declare las versiones fijas y verificadas de Kotlin 2.x, Compose Multiplatform, Ktor, Coroutines, Serialization, toolchain Java 21, desugaring (`desugar_jdk_libs`) y dependencias bajo licencias permisivas (Apache 2.0, MIT, BSD, GPLv2+CE).
- [x] **T02: Configuración de `settings.gradle.kts` y `build.gradle.kts` raíz con evaluación condicional**  
  *Requisitos cubiertos:* `RF-001`, `RF-003`, `RNF-002`  
  *Hecho cuando:* La configuración raíz declare los repositorios oficiales (`mavenCentral()`, `google()`), aplique los plugins de KMP y Compose, soporte Configuration Cache y permita compilar Desktop aun en ausencia de `ANDROID_HOME`.

## Fase 2: Configuración del Módulo KMP Compartido (`:shared`)
- [x] **T03: Configuración de targets, pureza y dependencias en `shared/build.gradle.kts`**  
  *Requisitos cubiertos:* `RF-001`, `RF-002`, `RNF-001`  
  *Hecho cuando:* El archivo configure `androidTarget()` y `jvm("desktop")`, Java toolchain 21 con compatibilidad Java 17 en Android, agregando `kotlinx-coroutines-core` y `kotlinx-serialization-json` en `commonMainApi`, verificando la ausencia total de `android.*`, `java.*` y `kotlinx.cinterop.*`.
- [x] **T04: Creación de estructura de directorios y test base en `shared`**  
  *Requisitos cubiertos:* `RF-002`, `RF-004`  
  *Hecho cuando:* Exista `shared/src/commonTest/kotlin/ArchitectureSanityTest.kt` que valide que el runner de pruebas `kotlin.test` ejecute con éxito mediante `./gradlew :shared:desktopTest`.
- [x] **T04b: Configuración integral de Compose Multiplatform Resources**  
  *Requisitos cubiertos:* `RF-005` (Constitución Principios 5 y 8)  
  *Hecho cuando:* `shared/build.gradle.kts` configure `compose.components.resources` con `publicResClass = true` y paquete canónico `com.vcompanion.shared.resources`, existan directorios para `values/strings.xml`, `drawable/` y `font/`, y la compilación genere exitosamente `Res.string.*` utilizable desde cualquier módulo.

## Fase 3: Configuración de los Módulos de Plataforma
- [x] **T05: Configuración base de `androidApp/build.gradle.kts` con Desugaring y build Debug**  
  *Requisitos cubiertos:* `RF-001`, `RF-002`, `RF-003`, Constitución Principio 7  
  *Hecho cuando:* `androidApp` configure `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`, `coreLibraryDesugaring`, dependa de `project(":shared")` y la ejecución de `./gradlew :androidApp:assembleDebug` genere exitosamente el APK sin requerir certificados de producción.
- [x] **T06: Configuración de `desktopApp/build.gradle.kts` como KMP (`jvm("desktop")`)**  
  *Requisitos cubiertos:* `RF-001`, `RF-002`, `RNF-003`  
  *Hecho cuando:* `desktopApp` se configure como módulo KMP con target `jvm("desktop")`, código en `src/desktopMain/kotlin`, dependa de `project(":shared")`, configure `compose.desktop { application { mainClass = "MainKt" } }` y arranque una ventana nativa de prueba.

## Fase 4: Orquestación de Pruebas Globales
- [x] **T07: Registro de la tarea coordinadora `allTests`**  
  *Requisitos cubiertos:* `RF-004`  
  *Hecho cuando:* La ejecución de `./gradlew allTests` coordine en paralelo las suites de pruebas (`:shared:desktopTest`, `:desktopApp:desktopTest` y opcionalmente suites de Android si está presente el SDK), soportando `--continue` para reportes consolidados.
