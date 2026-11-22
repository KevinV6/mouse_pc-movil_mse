# MSE Mouse — Control Remoto Wi-Fi para Windows 11 (PC + Android)

Ecosistema de control remoto local de baja latencia compuesto por un **servidor de escritorio para Windows 11** (`receptor_pc`, C# / .NET 8 / WPF Fluent) y una **aplicación móvil nativa para Android** (`mouse_movil`, Kotlin / Jetpack Compose / Material 3). Convierte el teléfono en un trackpad multigesto, teclado en tiempo real, control multimedia y panel de atajos personalizables a través de la red Wi-Fi local.

---

## Características Principales

### 1. Servidor de Escritorio (`receptor_pc` — Windows 11)
- **Interfaz Fluent / Mica (Windows 11):** Ejecución silenciosa en el área de notificación (bandeja del sistema `^`) sin ocupar espacio en la barra de tareas.
- **Panel emergente rápido (Popup):**
  - Estado del servidor TLS, dirección IPv4 local y puerto (`47800`).
  - **Código QR** dinámico y **PIN de 6 dígitos** con código de verificación (`XXXX-XXXX`).
  - Lista en tiempo real de dispositivos conectados (con medición de RTT y botones para **Desconectar** o **Revocar** acceso) y gestor de dispositivos de confianza.
  - Interruptor **Iniciar con Windows** (`HKCU\...\Run` con `--tray`) e interruptor para permitir/bloquear nuevos emparejamientos.
  - **Guía de Uso y Seguridad** integrada de 5 secciones y cierre completo limpio.
- **Inyección nativa (`SendInput` / P/Invoke):** Movimiento relativo, clics (izquierdo, derecho, central, doble clic), arrastre (`drag & drop`), scroll vertical/horizontal, zoom (`Ctrl + Rueda`), escritura Unicode (acentos, `ñ`, emojis), combinaciones de teclas, multimedia y energía (`LockWorkStation`, suspensión, apagado y reinicio).

### 2. Cliente Móvil (`mouse_movil` — Android)
- **Conectividad local (3 modos + reconexión automática):**
  1. **Detección automática UDP** en red local (puerto `47801`).
  2. **Escaneo de código QR** mediante CameraX + ML Kit.
  3. **Ingreso manual de IP y puerto.**
  - **Reconexión automática** con *exponential backoff* y redescubrimiento transparente si la IP de la PC cambia por DHCP.
- **Seguridad en red local:**
  - Transporte cifrado **TLS 1.2/1.3** con `TCP_NODELAY` y fijación de huella criptográfica (**Certificate Pinning** SHA-256).
  - Emparejamiento protegido por PIN de 6 dígitos con límite de intentos y bloqueo temporal.
  - Tokens persistidos con **DPAPI** (hash SHA-256 en Windows) y **AES-256-GCM en Android Keystore**.
- **Trackpad Multigesto (120 Hz coalescing):**
  - **1 dedo:** Mover cursor con aceleración dinámica, toque = clic izquierdo, doble toque = doble clic, mantener 400 ms + mover = arrastrar (`drag & drop`).
  - **2 dedos:** Toque = clic derecho, deslizar = scroll vertical y horizontal (dirección natural configurable), pellizcar = zoom (`Ctrl + Scroll`).
  - **3 dedos:** Toque = clic central.
- **Teclado, Multimedia y Atajos:**
  - Teclado virtual en tiempo real con barra de teclas especiales (`Esc`, `Tab`, `Ctrl`, `Alt`, `Shift`, `Win`, flechas, `Supr`, `Inicio`, `Fin`, `F1–F12`).
  - Controles de volumen, silencio, reproducción multimedia, atajos rápidos (`Alt+Tab`, Copiar, Pegar, Cortar, Deshacer, Mostrar escritorio) y editor completo de **atajos personalizados**.
- **Personalización y Guía Visual:**
  - Selector de tema (**Sistema / Modo Claro / Modo Oscuro**) y soporte para colores dinámicos (Material You).
  - Ajustes de sensibilidad, aceleración, velocidad de scroll, respuesta háptica y **Guía de Uso Visual** animada de 8 páginas.

---

## Requisitos y Compilación

### Aplicación de Escritorio (Windows 10/11)
- **Requisitos:** .NET 8 SDK (`net8.0-windows`).
- **Compilar y ejecutar pruebas:**
  ```powershell
  dotnet build receptor_pc/receptor_pc/receptor_pc.sln -c Debug
  dotnet test receptor_pc/receptor_pc/receptor_pc.sln
  ```
- **Ejecutar la aplicación:**
  ```powershell
  dotnet run --project receptor_pc/receptor_pc/receptor_pc/receptor_pc.csproj
  ```
- **Publicar ejecutable de distribución (`win-x64`):**
  ```powershell
  dotnet publish receptor_pc/receptor_pc/receptor_pc/receptor_pc.csproj -c Release -r win-x64 -p:PublishSingleFile=true --self-contained false
  ```
- **Regla de Firewall (opcional, requiere PowerShell como Administrador):**
  ```powershell
  .\receptor_pc\tools\add-firewall-rule.ps1
  ```

### Aplicación Android
- **Requisitos:** Android Studio (AGP 9+, JDK 17/21).
- **Compilar APK Debug y ejecutar pruebas unitarias:**
  ```powershell
  cd mouse_movil
  .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
  ```
- **Compilar APK Release optimizado:**
  ```powershell
  cd mouse_movil
  .\gradlew.bat :app:assembleRelease
  ```

---

## Notas y Limitaciones Conocidas
- **Red privada y Firewall:** Tanto la PC como el teléfono deben estar conectados a la misma red Wi-Fi local y el perfil de red en Windows debe estar configurado como **Red privada** para permitir conexiones entrantes en los puertos `TCP 47800` y `UDP 47801`.
- **Aislamiento de privilegios (UIPI):** Por diseño de seguridad de Windows, `SendInput` desde un proceso estándar (`asInvoker`) no puede enviar eventos a ventanas ejecutadas como Administrador ni a la pantalla segura de UAC / inicio de sesión.
