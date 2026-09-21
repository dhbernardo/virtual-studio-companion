# Especificación: 005-ci-cd-and-distribution

## 1. Resumen Ejecutivo
Implementar la infraestructura de Integración y Despliegue Continuo (CI/CD) para el proyecto **Virtual Studio Companion** mediante **GitHub Actions**. El pipeline opera una matriz multiplataforma automatizada basada en tres jobs coordinados: verificación estática (`ktlint`), pruebas unitarias desacopladas por runner, compilación de artefactos (`app-debug.apk` y `virtual-studio-companion-setup.exe` vía `jpackage`) sin requerir credenciales externas, y publicación atómica de GitHub Releases ante tags de versión formales.

---

## 2. Requisitos Funcionales (EARS)

### RF-001: Matriz de Ejecución Multiplataforma y Aprovisionamiento
- **Tipo:** Event-driven
- **Definición:** CUANDO se realice un `push` o se abra un `pull_request` hacia las ramas principales (`main` / `develop`) o se envíe un tag `v*.*.*`, el sistema DEBE disparar simultáneamente trabajos en runners de Linux (`ubuntu-latest`) y Windows (`windows-latest`), aprovisionando JDK 21 Temurin, asegurando permisos de ejecución con `chmod +x gradlew` en Linux y habilitando la caché oficial de Gradle (`gradle/actions/setup-gradle`).
- **Criterio de Aceptación:**
  - **DADO QUE** se activa el pipeline
  - **CUANDO** se aprovisionan los entornos
  - **ENTONCES** ambos runners deben inicializar Java 21 Temurin y resolver la caché sin fallos de permisos en el script `gradlew`.

### RF-002: Verificación de Calidad y Pruebas Unitarias Desacopladas
- **Tipo:** Event-driven
- **Definición:** CUANDO se ejecute la fase de pruebas, el sistema DEBE evaluar de forma granular y desacoplada las suites de prueba según el entorno nativo de cada runner, bloqueando la integración si algún test o linter falla.
- **Criterio de Aceptación:**
  - **DADO QUE** se ejecuta el runner Linux (`ubuntu-latest`)
  - **CUANDO** se invoca el paso de verificación
  - **ENTONCES** debe ejecutar `ktlintCheck`, `:shared:allTests` y `:androidApp:testDebugUnitTest`.
  - **DADO QUE** se ejecuta el runner Windows (`windows-latest`)
  - **CUANDO** se invoca el paso de verificación
  - **ENTONCES** debe ejecutar `:shared:desktopTest` y `:desktopApp:desktopTest` sin requerir la toolchain de Android en Windows.

### RF-003: Compilación Automatizada del APK de Android (Modo Debug)
- **Tipo:** Event-driven
- **Definición:** CUANDO el job de Linux finalice las pruebas con éxito, el sistema DEBE compilar el artefacto APK en modo desarrollo (`./gradlew :androidApp:assembleDebug`), renombrarlo formalmente como `app-debug.apk` y subirlo a los artefactos de la build con una retención de 7 días.
- **Criterio de Aceptación:**
  - **DADO QUE** la compilación de Android concluye exitosamente
  - **ENTONCES** debe producir el archivo `app-debug.apk` firmado con el debug keystore estándar de Android (cero secretos requeridos en GitHub) y almacenarlo temporalmente en los artefactos de la ejecución.

### RF-004: Empaquetado Automatizado del Instalador Windows (`jpackage`)
- **Tipo:** Event-driven
- **Definición:** CUANDO el job de Windows finalice las pruebas con éxito, el sistema DEBE ejecutar la tarea de empaquetado nativo (`./gradlew :desktopApp:packageExe`), renombrar el instalador resultante a `virtual-studio-companion-setup.exe` y subirlo a los artefactos con una retención de 7 días.
- **Criterio de Aceptación:**
  - **DADO QUE** el runner de Windows completa `jpackage`
  - **ENTONCES** debe generar `virtual-studio-companion-setup.exe` encapsulando su propio micro-runtime OpenJDK sin requerir Java preinstalado en el cliente (Constitución Principio 2) y subirlo a los artefactos.

### RF-005: Job Consolidador y Publicación Atómica de GitHub Releases
- **Tipo:** Event-driven
- **Definición:** CUANDO se empuje un tag Git que cumpla el patrón `v*.*.*`, el sistema DEBE disparar un job consolidador `publish-release` que requiera el éxito de ambos jobs de compilación (`needs: [build-android, build-desktop]`), declare permisos `permissions: contents: write` mediante el `GITHUB_TOKEN` nativo, descargue ambos binarios y cree un Release atómico formal con notas automáticas de versión.
- **Criterio de Aceptación:**
  - **DADO QUE** se hace push del tag `v1.0.0`
  - **CUANDO** ambos jobs de compilación (`build-android` y `build-desktop`) concluyen con éxito
  - **ENTONCES** el job `publish-release` debe publicar el Release formal conteniendo exactamente los dos archivos adjuntos (`app-debug.apk` y `virtual-studio-companion-setup.exe`) con changelog generado automáticamente.
  - **DADO QUE** uno de los dos jobs de la matriz falla
  - **CUANDO** se evalúa la condición de release
  - **ENTONCES** el job de publicación debe abortar inmediatamente sin generar releases parciales o inconsistentes.

---

## 3. Requisitos No Funcionales (RNF)

- **RNF-001 (Optimización de Tiempos de CI):** El pipeline debe completar la ejecución de ambos jobs en paralelo en menos de 12 minutos aprovechando la caché de Gradle.
- **RNF-002 (Cero Fricción de Credenciales):** El pipeline no debe requerir Personal Access Tokens (PAT), secretos de firma de producción ni certificados externos, operando exclusivamente con el keystore estándar de depuración de Android y el `GITHUB_TOKEN` efímero provisto automáticamente por GitHub (Constitución Principio 7).
- **RNF-003 (Auditoría Automatizada de Licencias Permisivas):** El pipeline debe incluir un paso de validación que certifique que las dependencias declaradas cumplan estrictamente con las licencias autorizadas por la Constitución: Apache 2.0, MIT, BSD y GPLv2 con Classpath Exception (Constitución Principio 3).
- **RNF-004 (Verificación Estática de Cero Strings en Crudo):** Las revisiones de linter en CI deben verificar la ausencia de textos literales sin mapear a recursos `Res.string.*` en código composable (Constitución Principio 8).
