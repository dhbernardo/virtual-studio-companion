# Constitución del Proyecto: Virtual Studio Companion (KMP Suite)

Este documento establece los principios innegociables y estándares de calidad de la suite **Virtual Studio Companion**. Cualquier propuesta técnica, diseño de arquitectura o generación de código debe someterse estrictamente a estas leyes fundamentales.

---

## Principios Innegociables

### 1. Núcleo Multiplataforma Puro e Inmutable (`shared/commonMain`)
Toda la lógica de negocio, validación de sesiones, cálculo de telemetría (bitrate, latencia, FPS) y definición del protocolo de comunicación debe residir exclusivamente en el módulo `shared/commonMain`.
- **Prohibición estricta:** Queda terminantemente vetado el uso de APIs dependientes de plataforma (`android.*`, `java.*`, `java.io.*`, `java.net.*`, `kotlinx.cinterop.*`) en el núcleo compartido.
- Toda interacción con hardware, red o sistema operativo debe desacoplarse mediante **Puertos e Interfaces** puros, cuyas implementaciones concretas residirán en `androidApp` o `desktopApp`.

### 2. Cero Dependencia de Java en el Cliente de Windows
El Host nativo para Windows debe distribuirse como un ejecutable `.exe` o instalador `.msi` completamente autocontenido generado mediante `jpackage`.
- El usuario final de Windows no debe requerir la instalación previa de Java (ni JRE ni JDK).
- El proceso de empaquetado debe aislar un micro-runtime optimizado (`jlink`) dentro del directorio de instalación del programa.

### 3. Licenciamiento Abierto y Transparente
Para proteger la integridad legal del proyecto y su viabilidad como software de portafolio:
- Queda prohibido incorporar bibliotecas o componentes con licencias virales restrictivas (GPLv3 sin excepción de classpath).
- Se permite únicamente el uso de dependencias bajo licencias comerciales y abiertas permisivas: **Apache 2.0**, **MIT**, **BSD** y **GPLv2 con Classpath Exception** (OpenJDK/Adoptium).

### 4. Resiliencia de Red y Emparejamiento por Código QR Garantizado
El sistema debe estar preparado para operar en redes Wi-Fi locales heterogéneas y restringidas:
- Aunque se soporte auto-descubrimiento mediante multidifusión (mDNS), el sistema **no debe asumir** que mDNS siempre estará disponible (debido a routers domésticos o corporativos con *AP Isolation* o bloqueo de multicast UDP).
- El emparejamiento mediante **Código QR óptico** (generado por el Host y escaneado por el móvil) es el mecanismo de contingencia obligatorio e incondicional.

### 5. Sistema de Diseño (Design System) Compartido en Compose (Dark y Light Mode)
La experiencia visual y de interacción debe ser coherente en todas las pantallas del ecosistema:
- Los tokens de diseño deben definirse de forma centralizada en el módulo compartido con soporte completo para dos modos: **Studio Dark** (estilo estudio de transmisión con fondos carbón) y **Studio Light** (alta visibilidad con superficies claras).
- La aplicación debe iniciar por defecto detectando reactivamente la preferencia del sistema operativo (`isSystemInDarkTheme()`), permitiendo al usuario la conmutación manual explícita (`SYSTEM`, `DARK`, `LIGHT`).
- Las vistas de Android y la interfaz de Windows deben consumir los mismos tokens y componentes visuales base (`Badge`, `Indicator`, `CounterCard`) implementados en Compose Multiplatform.

### 6. Desarrollo Guiado por Especificación (SDD) y Pruebas Primero (TDD)
Ningún archivo de código fuente de producción será creado o modificado si el requerimiento no está expresamente modelado en su respectivo documento dentro de `specs/`.
- Cada funcionalidad debe contar con el trío: `spec.md` (requisitos EARS y criterios BDD), `plan.md` (diseño técnico y diagramas) y `tasks.md` (pasos incrementales con condiciones "Hecho cuando:").
- Para toda lógica de dominio, parseo de protocolos o cálculos matemáticos, la prueba unitaria debe escribirse antes de la implementación de producción.

### 7. Integración y Entrega Continua Multiplataforma (CI/CD) sin Fricción de Credenciales
El proyecto debe validarse de forma automática en cada commit relevante:
- Un pipeline en GitHub Actions debe ejecutar la matriz multiplataforma en paralelo:
  1. Entorno Linux (`ubuntu-latest`): compila el proyecto compartido y genera el artefacto `app-debug.apk` firmado automáticamente mediante el keystore de depuración estándar de Android (cero requerimiento de certificados de producción o secretos en esta etapa).
  2. Entorno Windows (`windows-latest`): compila la aplicación de escritorio y genera el artefacto `virtual-studio-companion-setup.exe` mediante `jpackage`.
- Ningún pull request o commit a ramas principales podrá integrarse si existen fallos en linters (`ktlint`) o pruebas unitarias automatizadas.

### 8. Cero Recursos en Crudo y Centralización Estricta (No Hardcoded Strings)
Queda terminantemente prohibido incorporar literales de texto (*hardcoded strings*), dimensiones fijas o rutas de recursos codificadas directamente en composables o lógica de presentación:
- Todas las cadenas de texto visibles (títulos, etiquetas, descripciones accesibles, mensajes de error y estados), así como recursos gráficos vectoriales y fuentes tipográficas, deben residir en catálogos centralizados de **Compose Multiplatform Resources** (`shared/src/commonMain/composeResources/`) y consumirse exclusivamente mediante `Res.string.*`, `Res.drawable.*` y `Res.font.*`.
- Esta separación garantiza internacionalización inmediata, mantenimiento limpio y verificación estática mediante linters.
