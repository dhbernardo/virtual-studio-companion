# Desglose de Tareas: 005-ci-cd-and-distribution

## Fase 1: Configuración del Pipeline de Integración Continua (CI)
- [x] **T01: Creación del archivo de workflow `.github/workflows/ci-cd.yml` con topología de 3 jobs**  
  *Requisitos cubiertos:* `RF-001`, `RNF-001`, `RNF-002`  
  *Hecho cuando:* El archivo YAML defina los jobs `build-android-and-shared`, `build-desktop-windows` y `publish-release`, configure JDK 21 Temurin, `chmod +x gradlew` en Linux y `permissions: contents: write` en el job de Release.
- [x] **T02: Verificación de calidad, auditoría y pruebas unitarias desacopladas**  
  *Requisitos cubiertos:* `RF-002`, `RNF-003`, `RNF-004` (Constitución Principios 3 y 8)  
  *Hecho cuando:* El runner de Ubuntu ejecute `ktlintCheck`, `:shared:allTests` y `:androidApp:testDebugUnitTest`; y el runner de Windows ejecute `:shared:desktopTest` y `:desktopApp:desktopTest`.

## Fase 2: Automatización de Construcción y Retención de Artefactos
- [x] **T03: Compilación, renombrado y subida del APK de Android (`app-debug.apk`)**  
  *Requisitos cubiertos:* `RF-003`, `RNF-002` (Constitución Principio 7)  
  *Hecho cuando:* El job de Ubuntu ejecute `./gradlew :androidApp:assembleDebug`, renombre el binario a `app-debug.apk` y lo suba a los artefactos con retención de 7 días.
- [x] **T04: Empaquetado, renombrado y subida del instalador Windows (`virtual-studio-companion-setup.exe`)**  
  *Requisitos cubiertos:* `RF-004`, `RNF-001` (Constitución Principio 2)  
  *Hecho cuando:* El job de Windows ejecute `./gradlew :desktopApp:packageExe`, renombre el binario a `virtual-studio-companion-setup.exe` y lo suba a los artefactos con retención de 7 días.

## Fase 3: Automatización de Releases Consolidados
- [x] **T05: Configuración del job consolidador `publish-release` en GitHub Releases**  
  *Requisitos cubiertos:* `RF-005`  
  *Hecho cuando:* Ante un push de tag `v*.*.*` y con ambos jobs previos exitosos, el job descargue `app-debug.apk` y `virtual-studio-companion-setup.exe`, y publique el Release formal mediante `softprops/action-gh-release@v2` con notas de versión automáticas.
