# Especificación: 000-architecture-and-tech-stack

## 1. Resumen Ejecutivo
Definir y estructurar el marco arquitectónico integral del proyecto monorepo **Virtual Studio Companion** basado en **Kotlin Multiplatform (KMP)** y **Compose Multiplatform**. El sistema debe articular de manera desacoplada los tres subsistemas esenciales: el módulo común de dominio (`shared`), la aplicación móvil Android (`androidApp`) y el Host de escritorio para Windows (`desktopApp`), garantizando compilación reproducible, aislamiento estricto de dependencias, soporte condicional para desarrollo de escritorio y pruebas automatizadas unificadas.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Topología de monorepo y aislamiento modular KMP
- **Tipo:** Ubiquitous
- **Definición:** El sistema DEBE estructurarse como un proyecto Gradle multimodular compuesto por tres módulos principales: `:shared` (KMP), `:androidApp` (Android Application) y `:desktopApp` (Kotlin Multiplatform Desktop Application con target `jvm("desktop")`).
- **Criterio de Aceptación:**
  - **DADO QUE** se inspecciona la raíz del proyecto
  - **CUANDO** se analiza la configuración de `settings.gradle.kts`
  - **ENTONCES** debe declarar explícitamente los proyectos `:shared`, `:androidApp` y `:desktopApp`.
  - **Y** `:shared` debe exponer los targets `androidTarget()` y `jvm("desktop")` sin acoplarse a librerías de plataforma en `commonMain`.
  - **Y** `:desktopApp` debe estructurarse como módulo KMP con target `jvm("desktop")`, localizando su código fuente en `src/desktopMain/kotlin`.
  - **Y** `:androidApp` debe configurar `compileSdk = 34`, `minSdk = 26` y `targetSdk = 34`.
  - **Y** la configuración de Gradle debe evaluar condicionalmente la toolchain de Android si las variables `ANDROID_HOME` o `ANDROID_SDK_ROOT` están ausentes, permitiendo compilar y testear `:desktopApp` y `:shared` (target desktop) de forma independiente.

### RF-002: Reutilización de Dominio, Contratos y Recursos en `commonMain`
- **Tipo:** Ubiquitous
- **Definición:** El módulo `:shared` DEBE exponer a los módulos `:androidApp` y `:desktopApp` las entidades de dominio, casos de uso, interfaces de puertos, modelos serializables y recursos compartidos bajo el paquete canónico `com.vcompanion.shared` sin duplicación de código.
- **Criterio de Aceptación:**
  - **DADO QUE** `:androidApp` y `:desktopApp` dependen de `:shared`
  - **CUANDO** compilan en sus respectivos entornos
  - **ENTONCES** ambos deben consumir exactamente las mismas clases de datos, esquemas JSON, puertos abstractos y accesores de recursos `Res.*` generados públicamente desde `commonMain`.

### RF-003: Compatibilidad de plugins, toolchains y versiones
- **Tipo:** Ubiquitous
- **Definición:** El archivo de versiones centralizadas (`gradle/libs.versions.toml`) DEBE gestionar de manera unificada las versiones de Kotlin (2.x), Compose Multiplatform, Coroutines, Serialization y Ktor, así como el toolchain global Java (`jvmToolchain(21)`).
- **Criterio de Aceptación:**
  - **DADO QUE** se sincroniza el proyecto Gradle
  - **CUANDO** se evalúa la configuración de compilación
  - **ENTONCES** no debe existir desalineación de versiones entre módulos.
  - **Y** `:desktopApp` debe compilar hacia JVM 21, mientras que `:androidApp` debe configurar compatibilidad de bytecode Java 17 con `coreLibraryDesugaring` habilitado para soportar APIs modernas en dispositivos con `minSdk = 26`.

### RF-004: Tarea coordinadora de pruebas (`allTests`) y desacoplamiento con CI/CD
- **Tipo:** Event-driven
- **Definición:** CUANDO el desarrollador ejecute la tarea `./gradlew allTests`, el sistema DEBE coordinar en paralelo la ejecución de las suites de prueba unitarias disponibles (`:shared:desktopTest`, `:desktopApp:desktopTest` y, si el SDK de Android está presente, `:androidApp:testDebugUnitTest`), permitiendo la recolección agregada de fallos con el flag `--continue`.
- **Criterio de Aceptación:**
  - **DADO QUE** existen suites de pruebas en los módulos común y específicos
  - **CUANDO** se ejecuta `./gradlew allTests`
  - **ENTONCES** debe ejecutar los tests unitarios en paralelo y fallar con código no nulo ante cualquier aserción rota.
  - **Y** en GitHub Actions, cada runner ejecutará de forma granular y desacoplada las pruebas de su entorno (`:desktopApp:desktopTest` y `:shared:desktopTest` en Windows; `:androidApp:testDebugUnitTest` y `:shared:allTests` en Linux) sin dependencias cruzadas incompatibles.

### RF-005: Soporte y distribución de Compose Multiplatform Resources
- **Tipo:** Ubiquitous
- **Definición:** El build Gradle DEBE habilitar el plugin oficial de recursos de Compose (`components.resources`), configurando visibilidad pública (`publicResClass = true`) bajo el paquete canónico `com.vcompanion.shared.resources` a partir del directorio `shared/src/commonMain/composeResources/` para eliminar cadenas, dimensiones, tipografías e iconos en crudo.
- **Criterio de Aceptación:**
  - **DADO QUE** se declaran claves en `composeResources/values/strings.xml`, gráficos vectoriales en `composeResources/drawable/` o tipografías en `composeResources/font/`
  - **CUANDO** se compila el módulo `:shared`
  - **ENTONCES** deben generarse los accesores estáticos correspondientes (`Res.string.*`, `Res.drawable.*`, `Res.font.*`) accesibles públicamente desde `:androidApp` y `:desktopApp`.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Pureza Multiplataforma):** El directorio `shared/src/commonMain` no debe contener referencias a `android.*`, `java.*`, `java.io.*`, `java.net.*`, `javax.*`, `java.awt.*` ni `kotlinx.cinterop.*`.
- **RNF-002 (Tiempos de Compilación y Cacheabilidad):** La configuración Gradle debe soportar plenamente Gradle Configuration Cache y Build Cache local. La ejecución de `./gradlew --configuration-cache --build-cache help` debe finalizar limpiamente sin advertencias críticas que invaliden la caché.
- **RNF-003 (Conformidad de Licenciamiento):** Todos los plugins de Gradle y dependencias declaradas en `libs.versions.toml` deben regirse exclusivamente bajo licencias autorizadas por la Constitución: **Apache 2.0**, **MIT**, **BSD** y **GPLv2 con Classpath Exception** (OpenJDK/Adoptium).
