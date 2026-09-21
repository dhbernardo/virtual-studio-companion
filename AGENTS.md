# Reglas y Contexto del Agente: Virtual Studio Companion (KMP Suite)

## 1. Qué es este proyecto
Una suite de software multiplataforma de grado profesional desarrollada con **Kotlin Multiplatform (KMP)** y **Compose Multiplatform**. Permite transformar un dispositivo móvil Android en una cámara de transmisión de ultra-baja latencia para **OBS Studio** en Windows, operando a través de un Host nativo autocontenido, emparejamiento seguro mediante Código QR (y mDNS), telemetría bidireccional y un **Design System compartido** entre móvil y escritorio.

## 2. Pila Tecnológica
- **Lenguaje Principal:** Kotlin 2.x (Kotlin Multiplatform - KMP).
- **Lógica de Dominio y Protocolo:** `shared/commonMain` (Clean Architecture pura, cero dependencias de plataforma, kotlinx.coroutines, kotlinx.serialization).
- **Interfaz de Usuario:** Compose Multiplatform (Jetpack Compose en Android y Compose Desktop en Windows).
- **Móvil (Android):** CameraX para captura de video de alta eficiencia, Google ML Kit para escaneo instantáneo de códigos QR, StateFlow/ViewModel (MVVM).
- **Escritorio (Windows Host):** Ktor Server embebido (WebSockets / WebRTC signaling), renderizador de QR en canvas Compose, conector con OBS Studio mediante OBS WebSocket API v5.
- **Empaquetado Windows:** Gradle + `jpackage` para generar un ejecutable `.exe` / `.msi` autocontenido que encapsula un micro-runtime OpenJDK Temurin (**el usuario final no necesita Java instalado**).
- **Pruebas Automatizadas:** Kotlin Test Framework (`kotlin.test`), MockK y Turbine para testing reactivo de `StateFlow`.
- **CI/CD:** GitHub Actions con matriz multiplataforma (`ubuntu-latest` para Android y `windows-latest` para Windows Desktop).

## 3. Flujo de Trabajo Obligatorio (SDD)
1. **No improvises código:** Consulta siempre la especificación activa en `specs/`.
2. **Tareas incrementales:** Implementa una tarea a la vez siguiendo `tasks.md`. Marca el checkbox (`[x]`) únicamente cuando se cumpla la condición "Hecho cuando:".
3. **Tests primero (TDD):** Antes de implementar la lógica del protocolo, casos de uso o parsers, escribe la prueba unitaria que valide el comportamiento esperado.

## 4. Reglas Críticas del Código
- **Independencia en `shared/commonMain`:** Queda terminantemente prohibido importar paquetes específicos de plataforma (`android.*`, `java.*` o `kotlinx.cinterop.*`) dentro de `shared/commonMain`. Todo comportamiento dependiente del sistema debe resolverse mediante el patrón de Puertos y Adaptadores o `expect`/`actual`.
- **Cero Textos ni Recursos en Crudo (No Hardcoded Strings):** Queda terminantemente prohibido escribir cadenas de texto literales en composables, layouts o código de presentación. Todo texto, etiqueta, título o mensaje de error debe definirse y consumirse a través de **Compose Multiplatform Resources** (`Res.string.*`) en `shared/src/commonMain/composeResources/` o constantes centralizadas en el dominio.
- **Paridad de Temas (Dark Mode vs. Light Mode) y Detección de Sistema:** Todo componente visual y pantalla debe implementar soporte simétrico para modo oscuro (`StudioDarkColors`) y modo claro (`StudioLightColors`). La aplicación debe detectar e iniciar por defecto con la preferencia del sistema operativo (`isSystemInDarkTheme()`), permitiendo además conmutación manual.
- **Cero Dependencias de Java en el Cliente Final:** La aplicación de escritorio debe distribuirse mediante instalador o binario generado con `jpackage`. Jamás se debe exigir al usuario la instalación previa de JRE o JDK.
- **Licenciamiento Estricto y Abierto:** Solo se permiten dependencias bajo licencias permisivas (Apache 2.0, MIT, BSD, OpenJDK Classpath Exception). Prohibido el uso de librerías con copyleft invasivo (GPLv3 sin excepción) que comprometan el código fuente.
- **Resiliencia de Red y Fallback de Emparejamiento:** El sistema no debe asumir que el descubrimiento mDNS/ZeroConf siempre funciona (debido a routers con *AP Isolation*). El emparejamiento mediante Código QR es el mecanismo principal de contingencia y debe funcionar en cualquier red Wi-Fi local.
- **Aislamiento de Hilos en UI:** El procesamiento y codificación de video jamás debe ejecutar en el hilo principal (`Dispatchers.Main`). Debe utilizarse un despachador dedicado de alta prioridad (`Dispatchers.Default` o subprocesos de hardware).

## 5. Comandos Frecuentes
- `./gradlew allTests`: Ejecuta la suite completa de pruebas unitarias en todos los módulos compartidos y específicos.
- `./gradlew :shared:check`: Ejecuta validaciones de tipos, linter y tests del módulo común.
- `./gradlew :androidApp:assembleDebug`: Compila y genera el paquete APK de desarrollo de Android (sin requerir certificados ni keystores de producción).
- `./gradlew :desktopApp:packageExe`: Genera el instalador/ejecutable `.exe` nativo de Windows mediante `jpackage`.

## 6. Verificación al Finalizar Cualquier Tarea
Antes de dar una tarea por cerrada:
1. Ejecutar pruebas automatizadas (`./gradlew allTests`).
2. Comprobar que no hay violaciones a `docs/constitution.md`.
3. Actualizar el estado en `specs/<feature>/tasks.md`.

## 7. Patrón Arquitectónico Obligatorio (Clean Architecture & Puertos y Adaptadores)
Todo desarrollo debe estructurarse aislando el núcleo de la infraestructura:
- **`shared/commonMain/domain/`:** Lógica pura de negocio, máquinas de estado de conexión, cálculo de métricas de bitrate/FPS, formateadores y validación de tokens de sesión.
- **`shared/commonMain/ports/`:** Definición de contratos e interfaces (`IStreamGateway`, `IQrGenerator`, `IObsConnector`, `ITelemetryEmitter`).
- **`shared/commonMain/designsystem/`:** Tokens de diseño (paleta "Studio Dark", escalas tipográficas, espaciados y temas compartidos de Compose).
- **`androidApp/`:** Adaptadores móviles específicos (CameraX, ML Kit QR Scanner, ViewModels Android y pantallas Compose).
- **`desktopApp/`:** Adaptadores de escritorio (Ktor Server, integración nativa con OBS WebSocket, ventana Compose Desktop y configuración `jpackage`).

## 8. Topología del Monorepo y Convención de Directorios
El repositorio está dividido en módulos independientes coordinados mediante Gradle:
- **`shared/`:** Módulo KMP compartido (Domain, Ports, Protocol, Design System Tokens; targets `androidTarget()` y `jvm("desktop")`).
- **`androidApp/`:** Aplicación móvil Android para captura y control (`src/main/`).
- **`desktopApp/`:** Aplicación nativa de Windows Host basada en KMP (target `jvm("desktop")`, `src/desktopMain/`) para ingesta y enlace con OBS.
- **`specs/`:** Especificaciones SDD estructuradas en `spec.md`, `plan.md` y `tasks.md`.
- **`docs/`:** Constitución del proyecto y documentación arquitectónica de referencia.
