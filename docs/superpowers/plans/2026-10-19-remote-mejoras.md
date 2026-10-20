# Universal Remote: marcas nuevas y mejoras de uso. Plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans. Los pasos usan casillas `- [ ]`.

**Meta:** Ampliar el soporte a más marcas de TV y equipos (prioridad máxima) y añadir las funciones de uso diario que faltan al mando.

**Arquitectura:** Cada marca nueva es un `RemoteAdapter` más, registrado en `DefaultRemoteAdapterFactory` con un `DeviceKind` nuevo. No hay migración de Room: `DeviceProfileEntity.kind` es `String`. El descubrimiento se amplía en `Ssdp.kt` y en la lista mDNS de `LanDiscoveryManager`. Las funciones transversales (macros, favoritos, widget, teclas de volumen) cuelgan de `RemoteControllerManager` y no tocan los adaptadores.

**Stack:** Kotlin, Hilt, Room (versión 1, `exportSchema = true`), OkHttp, coroutines/Flow, Jetpack Compose, DataStore, JUnit.

**Spec:** conversación del 2026-10-19 ("Mejoras para el remote"). No hay documento de diseño aparte.

## Restricciones globales

- Contrato de `RemoteAdapter` (`domain/adapter/RemoteAdapter.kt`): `connect` idempotente; `send` lanza `RemoteError.NotConnected` si no hay sesión; toda E/S fuera del hilo principal; `close` es definitivo.
- Los adaptadores se construyen con un `@AssistedFactory` (`XxxAdapter.Factory`), igual que `RokuEcpAdapter.Factory`.
- `DeviceKind` se guarda como `String`. Añadir valores al enum es seguro; nunca renombrar ni borrar los existentes.
- Los textos de UI y los comentarios van en español, como el resto del código.
- Los secretos (PSK, tokens, PIN) viven en `DeviceProfile.pairingToken`; nunca en logs.
- Sin dependencias nuevas salvo que la tarea lo diga (YAGNI).
- Ruta base del código: `android/app/src/main/java/com/universalremote/`. En el resto del plan se abrevia como `…/`.
- Pruebas en `android/app/src/test/java/com/universalremote/`. Comando: `cd android && ./gradlew :app:testDebugUnitTest`.

## Foco de revisión

1. Equipo apagado o fuera de la red al abrir la app: el adaptador debe fallar con `RemoteError` tipado, sin colgar la UI (timeout acotado).
2. PIN o PSK incorrecto en el emparejamiento: error claro y reintento posible, sin corromper el perfil guardado.
3. La IP del TV cambia por DHCP: `relocateSavedDevices` debe reencontrar el equipo también para las marcas nuevas (huella estable en `serviceIds`).
4. Un comando no soportado por la marca (por ejemplo `TouchDelta` en Sony): `supports()` devuelve `false` y la UI oculta el botón en lugar de fallar.
5. Macro con un paso que falla a mitad: debe abortar y avisar qué paso falló, sin dejar el equipo en estado intermedio silencioso.

---

## Fase 0: Base y orden del repositorio

### Tarea 0: Limpiar el repo y fijar la base

**Files:**
- Modify: `.gitignore`
- Commit: cambios pendientes en `AndroidManifest.xml`, `RemoteControllerManager.kt`, `CyberTelemetryHeader.kt`

- [ ] **Paso 1:** Revisar `git diff` de los tres archivos modificados. Si compilan y son intencionales, commitearlos: `git commit -m "feat: ajustes de cabecera de telemetría y manifest"`.
- [ ] **Paso 2:** Añadir a `.gitignore`: `respaldo_telefono/`, `UniversalRemote/`, `UniversalRemote-apk/`, `stitch_universal_smart_remote_control/`, `app_logo.jpg`. Antes de ignorar, confirmar con el usuario qué carpetas son respaldos descartables y cuáles hay que conservar.
- [ ] **Paso 3:** Ejecutar `cd android && ./gradlew :app:testDebugUnitTest`. Esperado: todas las pruebas existentes pasan. Esa es la línea base.
- [ ] **Paso 4:** Commit `chore: ignorar carpetas de respaldo`.

---

## Fase 1: Marcas nuevas (prioridad)

Orden por relación esfuerzo/alcance: Sony Bravia, Vizio, Philips, Fire TV, Hisense, Panasonic, receptores AV y Kodi, Apple TV. Apple TV va al final porque su protocolo es el más pesado y el riesgo de que no entre es real.

### Tarea 1: Marco común para marcas nuevas

**Files:**
- Modify: `…/domain/model/DeviceProfile.kt` (enum `DeviceKind`)
- Modify: `…/adapters/DefaultRemoteAdapterFactory.kt`
- Modify: `…/network/discovery/Ssdp.kt:26-28`
- Modify: `…/network/discovery/LanDiscoveryManager.kt:369-392`
- Modify: `…/presentation/screens/DevicesScreen.kt:434-437` y `:517-523`
- Modify: `…/presentation/screens/RemoteProScreen.kt:102-106`
- Create: `…/adapters/common/HttpAdapterSupport.kt`
- Test: `…/adapters/common/HttpAdapterSupportTest.kt`

**Interfaces:**
- Produces: `suspend fun OkHttpClient.getJson(url: String): JsonObject` y `suspend fun OkHttpClient.postBody(url: String, body: String, headers: Map<String,String>): String`. Los dos usan los helpers de `network/http/OkHttpExtensions.kt` y lanzan `RemoteError` tipado ante timeout o conexión rechazada.
- Produces: `DeviceKind` con valores nuevos (cada tarea de marca añade el suyo).
- Produces: patrón de registro. Una marca = un valor de `DeviceKind` + una rama en la factory + una línea en `Ssdp.classify` o en la lista mDNS + una entrada en los selectores de `DevicesScreen`.

- [ ] **Paso 1:** Escribir `HttpAdapterSupportTest` con `MockWebServer`: `getJson` devuelve el objeto; un 500 lanza `RemoteError`; un timeout lanza `RemoteError.NotConnected`.
- [ ] **Paso 2:** Ejecutar la prueba. Esperado: FALLA (símbolos no definidos).
- [ ] **Paso 3:** Implementar `HttpAdapterSupport.kt` reutilizando `OkHttpExtensions.kt`. No duplicar su lógica de `await`.
- [ ] **Paso 4:** Hacer que `RemoteSkin` en `RemoteProScreen.kt` tenga un caso por defecto (`GENERIC`) para marcas sin skin propia, y que `DeviceKind.displayName()` y `LanDiscoveryManager` usen un `when` exhaustivo. Así el compilador marca cada sitio que falta al añadir una marca.
- [ ] **Paso 5:** Ejecutar las pruebas. Esperado: PASS.
- [ ] **Paso 6:** Commit `feat: marco común para adaptadores HTTP`.

### Tarea 2: Sony Bravia

**Protocolo:** IRCC-IP, SOAP sobre HTTP en `http://<host>/sony/IRCC` con cabecera `X-Auth-PSK`. API REST de estado en `/sony/system`, `/sony/appControl` y `/sony/audio`. Emparejamiento: el usuario fija en el TV un PSK (Ajustes → Red → Control IP → Autenticación "Normal y Pre-Shared Key") o se usa PIN en `/sony/accessControl`. Descubrimiento SSDP: `ST: urn:schemas-sony-com:service:ScalarWebAPI:1`.

**Files:**
- Create: `…/adapters/sony/SonyBraviaAdapter.kt`
- Create: `…/adapters/sony/SonyKeys.kt` (mapa `RemoteCommand` → código IRCC en base64)
- Modify: `DeviceKind` (añadir `SONY_BRAVIA(80)`), factory, `Ssdp.kt`, selectores de `DevicesScreen.kt`
- Test: `…/adapters/sony/SonyBraviaAdapterTest.kt`

**Interfaces:**
- Consumes: `HttpAdapterSupport.postBody` (Tarea 1).
- Produces: `class SonyBraviaAdapter @AssistedInject constructor(@Assisted profile: DeviceProfile, …) : RemoteAdapter` con `capabilities = {POWER, VOLUME, CHANNELS, DIGITS, NAVIGATION, MEDIA}`. `object SonyKeys { fun codeFor(command: RemoteCommand): String? }` devuelve `null` si no existe el código.

- [ ] **Paso 1:** Escribir `SonyBraviaAdapterTest` con `MockWebServer`: `send(RemoteCommand.VolumeUp)` hace un POST a `/sony/IRCC` con `X-Auth-PSK: <psk>` y un cuerpo SOAP que contiene `<IRCCCode>AAAAAQAAAAEAAAASAw==</IRCCCode>` (código de Vol+). Un 403 lanza `RemoteError` de emparejamiento. `supports(RemoteCommand.TouchDelta(1f, 1f))` es `false`.
- [ ] **Paso 2:** Ejecutar la prueba. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `SonyKeys.codeFor` con la tabla IRCC pública (Power, Vol±, Mute, Ch±, dígitos, D-pad, Back, Home, Menu, Info, Input, media). Los códigos se verifican contra un TV real o contra la documentación de Sony antes de dar el paso por cerrado.
- [ ] **Paso 4:** Implementar `SonyBraviaAdapter`. `connect` hace `GET /sony/system` con `getPowerStatus` para validar el PSK. `PowerOn` usa `setPowerStatus(true)` por la API REST y Wake-on-LAN como respaldo.
- [ ] **Paso 5:** Registrar en la factory, en `Ssdp.classify` y en `DevicesScreen`. Pedir el PSK en el flujo de alta; guardarlo en `pairingToken`.
- [ ] **Paso 6:** Ejecutar las pruebas. Esperado: PASS.
- [ ] **Paso 7:** Prueba manual con un Bravia real (o documentar que quedó sin probar en hardware). Commit `feat: soporte Sony Bravia`.

### Tarea 3: Vizio SmartCast

**Protocolo:** HTTPS en el puerto 7345 (9000 en equipos antiguos), certificado autofirmado. Emparejamiento por PIN: `PUT /pairing/start` y luego `PUT /pairing/pair` con el PIN que muestra el TV, que devuelve un `AUTH_TOKEN`. Teclas: `PUT /key_command/` con `{"KEYLIST":[{"CODESET":n,"CODE":n,"ACTION":"KEYPRESS"}]}` y cabecera `AUTH`. Descubrimiento SSDP: `urn:dial-multiscreen-org:service:dial:1` con servidor Vizio. Reutilizar `TofuTls` para el certificado.

**Files:**
- Create: `…/adapters/vizio/VizioAdapter.kt`, `…/adapters/vizio/VizioKeys.kt`
- Modify: `DeviceKind` (`VIZIO(7345)`), factory, `Ssdp.kt`, `DevicesScreen.kt`
- Test: `…/adapters/vizio/VizioAdapterTest.kt`

**Interfaces:**
- Consumes: `TofuTls`, `HttpAdapterSupport`; `submitPairingCode(code)` del contrato, ya usado por Android TV.
- Produces: `VizioAdapter` con `capabilities = {POWER, VOLUME, CHANNELS, DIGITS, NAVIGATION, MEDIA}`; `object VizioKeys { fun codeFor(command: RemoteCommand): Pair<Int, Int>? }` devuelve `(codeset, code)`.

- [ ] **Paso 1:** Escribir `VizioAdapterTest`: `connect` sin token dispara `PUT /pairing/start` y deja el estado en "esperando PIN"; `submitPairingCode("1234")` envía `PUT /pairing/pair` y guarda el token en `ProfileUpdated`; `send(VolumeUp)` envía `KEYLIST` con codeset 5 y code 1.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `VizioKeys` y `VizioAdapter`, con TOFU sobre el certificado.
- [ ] **Paso 4:** Registrar y exponer el diálogo de PIN. `DevicesScreen` ya maneja un PIN para Android TV; reutilizar ese flujo.
- [ ] **Paso 5:** Pruebas en PASS. Commit `feat: soporte Vizio SmartCast`.

### Tarea 4: Philips (JointSpace)

**Protocolo:** los Philips con Android TV ya entran por `ANDROID_TV`. Esta tarea cubre los modelos con Saphi o Linux (JointSpace API v6): HTTPS en el puerto 1926 con digest auth, emparejamiento por PIN en `/6/pair/request` y `/6/pair/grant`. Teclas: `POST /6/input/key` con `{"key":"VolumeUp"}`. Descubrimiento: SSDP `philips` o consulta a `/6/system`.

**Files:**
- Create: `…/adapters/philips/PhilipsJointSpaceAdapter.kt`, `…/adapters/philips/PhilipsKeys.kt`
- Modify: `DeviceKind` (`PHILIPS_JOINTSPACE(1926)`), factory, `Ssdp.kt`, `DevicesScreen.kt`
- Test: `…/adapters/philips/PhilipsJointSpaceAdapterTest.kt`

**Interfaces:**
- Produces: `PhilipsJointSpaceAdapter` (`capabilities = {POWER, VOLUME, CHANNELS, DIGITS, NAVIGATION, MEDIA}`) y `object PhilipsKeys { fun nameFor(command: RemoteCommand): String? }`.

- [ ] **Paso 1:** Escribir la prueba: el flujo de emparejamiento `request` → `grant` con firma HMAC-SHA1 usando el secreto público conocido de JointSpace; `send(DpadUp)` hace `POST /6/input/key` con `{"key":"CursorUp"}`.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar con digest auth (OkHttp `Authenticator`, sin dependencias nuevas) y TOFU.
- [ ] **Paso 4:** Detección: si el equipo responde a `/6/system` es JointSpace; si anuncia `_androidtvremote2._tcp.`, queda como `ANDROID_TV`. Añadir una prueba que fije esa precedencia.
- [ ] **Paso 5:** PASS y commit `feat: soporte Philips JointSpace`.

### Tarea 5: Amazon Fire TV

**Protocolo:** Fire OS no usa Android TV Remote v2. Opciones, de menos a más trabajo: (a) API HTTP del Fire TV Remote en el puerto 8009 (HTTPS, PIN, no documentada oficialmente); (b) ADB por red (puerto 5555) con `input keyevent`, que exige el protocolo de autenticación RSA de ADB. **Decisión a confirmar con el usuario antes de empezar:** se propone (b) con una implementación mínima del protocolo ADB (CNXN/AUTH/OPEN) porque es estable entre versiones de Fire OS. Requiere activar "Depuración ADB" en el Fire TV. (a) queda como alternativa si (b) resulta demasiado frágil.

**Files:**
- Create: `…/adapters/firetv/FireTvAdapter.kt`, `…/adapters/firetv/AdbProtocol.kt`, `…/adapters/firetv/FireTvKeys.kt`
- Modify: `DeviceKind` (`FIRE_TV(5555)`), factory, `LanDiscoveryManager.kt` (mDNS `_amzn-wplay._tcp.`), `DevicesScreen.kt`
- Test: `…/adapters/firetv/AdbProtocolTest.kt`

**Interfaces:**
- Produces: `class AdbProtocol(socket: Socket, keyPair: KeyPair)` con `suspend fun connect()` y `suspend fun shell(command: String): String`. `object FireTvKeys { fun keyEventFor(command: RemoteCommand): Int? }` devuelve el `KEYCODE_*` (por ejemplo `24` para Vol+).

- [ ] **Paso 1:** Escribir `AdbProtocolTest`: serialización de un paquete `CNXN` (cabecera de 24 bytes, `magic = cmd xor 0xffffffff`); la respuesta `AUTH` firma el token con la clave RSA y devuelve la clave pública en formato Android.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `AdbProtocol` (solo CNXN, AUTH, OPEN, WRTE, OKAY, CLSE). Generar el par RSA una vez y persistirlo en `DataStoreSessionPreferences`.
- [ ] **Paso 4:** Implementar `FireTvAdapter`: `send` ejecuta `input keyevent N`; `LaunchApp` ejecuta `monkey -p <paquete> 1`. Apps: Netflix `com.netflix.ninja`, Prime Video `com.amazon.firebat`, YouTube `com.amazon.firetv.youtube`. Verificar los nombres en un dispositivo.
- [ ] **Paso 5:** PASS y commit `feat: soporte Fire TV por ADB`.

### Tarea 6: Hisense VIDAA

**Protocolo:** MQTT en el puerto 36669 (TLS), usuario `hisenseservice`, contraseña `multimqttservice`. Teclas: publicar en `/remoteapp/tv/remote_service/<clientId>$normal/actions/sendkey` con payload `KEY_VOLUMEUP`. El emparejamiento es un código de 4 dígitos que se publica en `.../authenticationcode`. Esta es la única tarea que **necesita una dependencia nueva** (un cliente MQTT, por ejemplo Eclipse Paho); preguntar al usuario antes de añadirla.

**Files:**
- Create: `…/adapters/vidaa/VidaaAdapter.kt`, `…/adapters/vidaa/VidaaKeys.kt`
- Modify: `android/app/build.gradle(.kts)`, `DeviceKind` (`HISENSE_VIDAA(36669)`), factory, `DevicesScreen.kt`
- Test: `…/adapters/vidaa/VidaaKeysTest.kt`

**Interfaces:**
- Produces: `VidaaAdapter` (`capabilities = {POWER, VOLUME, NAVIGATION, MEDIA}`) y `object VidaaKeys { fun keyFor(command: RemoteCommand): String? }`.

- [ ] **Paso 1:** Escribir `VidaaKeysTest`: `keyFor(VolumeUp) == "KEY_VOLUMEUP"` y `keyFor(DpadCenter) == "KEY_OK"`. La parte de red se prueba a mano porque necesita un broker.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `VidaaKeys` y `VidaaAdapter` (conexión TLS, suscripción al tópico de confirmación, código de 4 dígitos vía `submitPairingCode`).
- [ ] **Paso 4:** PASS y commit `feat: soporte Hisense VIDAA`.

### Tarea 7: Panasonic Viera

**Protocolo:** SOAP sobre HTTP en el puerto 55000 (`/nrc/control_0`) con `X_SendKey` y códigos `NRC_VOLUP-ONOFF`. Los modelos desde 2018 exigen cifrado AES con un `appId` y una clave que se obtienen emparejando con PIN (`/nrc/sdd_0.xml` y `X_PinCode`). Primera entrega: solo modelos sin cifrado; los cifrados quedan documentados como no soportados.

**Files:**
- Create: `…/adapters/panasonic/VieraAdapter.kt`, `…/adapters/panasonic/VieraKeys.kt`
- Modify: `DeviceKind` (`PANASONIC_VIERA(55000)`), factory, `DevicesScreen.kt`
- Test: `…/adapters/panasonic/VieraAdapterTest.kt`

- [ ] **Paso 1:** Escribir `VieraAdapterTest` con `MockWebServer`: `send(Mute)` hace un POST a `/nrc/control_0` con `<X_KeyEvent>NRC_MUTE-ONOFF</X_KeyEvent>`.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar. Si `connect` recibe un 4xx por falta de cifrado, lanzar `RemoteError.UnsupportedDevice("PANASONIC_ENCRYPTED")` con un mensaje claro.
- [ ] **Paso 4:** PASS y commit `feat: soporte Panasonic Viera (sin cifrado)`.

### Tarea 8: Receptores AV y Kodi

**Protocolo:** Yamaha MusicCast (HTTP `/YamahaExtendedControl/v1/main/setVolume`), Denon/Marantz (Telnet en el puerto 23, `MVUP`) y Kodi (JSON-RPC en `/jsonrpc`, `Input.Up`, `Application.SetVolume`). Un solo `DeviceKind` por protocolo, mismo patrón que las tareas anteriores. `capabilities = {POWER, VOLUME, NAVIGATION, MEDIA}`; sin canales ni dígitos.

**Files:**
- Create: `…/adapters/yamaha/YamahaAdapter.kt`, `…/adapters/denon/DenonAdapter.kt`, `…/adapters/kodi/KodiAdapter.kt`
- Modify: `DeviceKind` (`YAMAHA_MUSICCAST(80)`, `DENON_HEOS(23)`, `KODI(8080)`), factory, `DevicesScreen.kt`
- Test: una clase de prueba por adaptador (`…AdapterTest.kt`)

- [ ] **Paso 1:** Una prueba por adaptador con `MockWebServer` (Yamaha, Kodi) y un `ServerSocket` local (Denon): `send(VolumeUp)` produce exactamente la petición o línea indicada arriba.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar los tres adaptadores. Si el tiempo aprieta, entregar Kodi y Yamaha y dejar Denon para después, con una nota en el plan.
- [ ] **Paso 4:** PASS y commit `feat: soporte Yamaha, Denon y Kodi`.

**Nota de ejecución:** el valor quedó como `DENON_AVR(23)` en vez de `DENON_HEOS`: el protocolo usado es el de texto en TCP 23; HEOS es otro (puerto 1255) y el nombre se guarda para siempre en la base. Kodi se descubre por mDNS `_xbmc-jsonrpc-h._tcp.`; Yamaha y Denon se dan de alta a mano. Rutas de cursor/menú de Yamaha y órdenes `NS9x` de Denon sin verificar en hardware.

### Tarea 9: Apple TV (riesgo alto, opcional)

**Protocolo:** Companion Link sobre mDNS `_companion-link._tcp`, con emparejamiento HAP (SRP + ChaCha20-Poly1305) y mensajes OPACK. Es el protocolo más complejo de la lista y no tiene documentación oficial. **Decisión:** hacer primero un *spike* de un día. Si el emparejamiento no funciona con las clases criptográficas del JDK y de Android sin añadir librerías, se descarta y se documenta como no soportado.

**Files:**
- Create (solo si el spike funciona): `…/adapters/appletv/AppleTvAdapter.kt`, `…/adapters/appletv/CompanionProtocol.kt`
- Modify: `DeviceKind` (`APPLE_TV`), factory, `LanDiscoveryManager.kt`

- [ ] **Paso 1:** Spike: emparejar con un Apple TV real y enviar un solo comando `HIDC` (Home). Registrar el resultado en este documento.
- [ ] **Paso 2:** Si fue exitoso, escribir pruebas de codificación OPACK con vectores conocidos y seguir el patrón de las demás marcas. Si no, cerrar la tarea con "no soportado" y un motivo.

### Tarea 10: Pantalla de alta unificada y descubrimiento

**Files:**
- Modify: `…/presentation/screens/DevicesScreen.kt:434-437` (selector de marca)
- Modify: `…/presentation/RemoteViewModel.kt:243-276` (`addDiscoveredDevice`, `addManualDevice`)
- Test: `…/PureLogicTest.kt` (añadir casos)

- [ ] **Paso 1:** Escribir pruebas en `PureLogicTest`: `Ssdp.classify` reconoce cada marca nueva a partir de una respuesta SSDP de ejemplo; dos anuncios del mismo `host` se deduplican en una sola entrada (`DiscoveredDevice.key`).
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA para las marcas aún no cubiertas.
- [ ] **Paso 3:** Reemplazar la lista fija de marcas por una lista generada desde `DeviceKind.entries` con nombre visible y flujo de emparejamiento (PIN, PSK, ninguno). La pantalla deja de editarse a mano cada vez que se añade una marca.
- [ ] **Paso 4:** PASS y commit `feat: alta de dispositivos guiada por DeviceKind`.

---

## Fase 2: Funciones de uso diario

### Tarea 11: Botones físicos de volumen

**Files:**
- Modify: `…/MainActivity.kt` (`onKeyDown`/`onKeyUp` para `KEYCODE_VOLUME_UP/DOWN`)
- Modify: `…/presentation/RemoteViewModel.kt`, `…/data/preferences/DataStoreSessionPreferences.kt`, `…/presentation/screens/SettingsScreen.kt`
- Test: `…/presentation/VolumeKeyPolicyTest.kt`

**Interfaces:**
- Produces: `fun shouldInterceptVolumeKeys(enabled: Boolean, onRemoteScreen: Boolean, connected: Boolean): Boolean`.

- [ ] **Paso 1:** Escribir `VolumeKeyPolicyTest`: solo intercepta con el ajuste activo, la pantalla del mando visible y el equipo conectado. En cualquier otro caso devuelve `false` para no romper el volumen normal del teléfono.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar la función, el ajuste en DataStore (`volumeKeysEnabled`, por defecto `false`) y el interruptor en `SettingsScreen`. Mantener pulsado repite el comando (reutilizar la lógica de repetición del botón de volumen de la UI).
- [ ] **Paso 4:** PASS y commit `feat: botones de volumen del teléfono controlan el TV`.

### Tarea 12: Widget y notificación de control rápido

**Files:**
- Create: `…/widget/RemoteWidget.kt` (Glance), `…/widget/RemoteNotificationService.kt`
- Modify: `android/app/build.gradle(.kts)` (`androidx.glance:glance-appwidget`), `AndroidManifest.xml`
- Test: `…/widget/QuickActionTest.kt`

**Interfaces:**
- Produces: `enum class QuickAction { POWER, VOLUME_UP, VOLUME_DOWN, MUTE }` y `fun QuickAction.toCommand(): RemoteCommand`.

- [ ] **Paso 1:** Escribir `QuickActionTest`: cada `QuickAction` se traduce al `RemoteCommand` esperado.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar el widget con las cuatro acciones, que llama a `RemoteControllerManager.send` sobre el dispositivo seleccionado. La notificación persistente con los mismos botones es opcional y se desactiva por defecto (batería).
- [ ] **Paso 4:** PASS y commit `feat: widget de control rápido`.

### Tarea 13: Macros / escenas

**Files:**
- Create: `…/domain/model/Macro.kt`, `…/domain/macro/MacroRunner.kt`
- Modify: `…/data/local/Entities.kt` y `AppDatabase.kt` (versión 2 con migración `MIGRATION_1_2`, esquema exportado), `Daos.kt`
- Create: `…/presentation/screens/MacrosScreen.kt`
- Test: `…/domain/macro/MacroRunnerTest.kt`

**Interfaces:**
- Produces: `data class MacroStep(val command: RemoteCommand, val delayAfterMs: Long = 300)`, `data class Macro(val id: String, val name: String, val deviceId: String, val steps: List<MacroStep>)` y `class MacroRunner { suspend fun run(macro: Macro, send: suspend (RemoteCommand) -> Unit): MacroResult }` con `sealed interface MacroResult { Success; data class Failed(val stepIndex: Int, val error: Throwable) }`.

- [ ] **Paso 1:** Escribir `MacroRunnerTest` con `kotlinx-coroutines-test`: ejecuta los pasos en orden respetando los retardos; si el paso 2 falla devuelve `Failed(1, …)` y **no** ejecuta el paso 3; cancelar la corrutina detiene la macro.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `MacroRunner`.
- [ ] **Paso 4:** Persistencia: tabla `macros` con los pasos serializados a JSON usando `RemoteCommand.wireName`, más la migración y una prueba de migración con el esquema exportado.
- [ ] **Paso 5:** Pantalla con lista, grabar pasos tocando botones y botón de ejecutar. Soportar `LaunchApp` y `Input` como pasos.
- [ ] **Paso 6:** PASS y commit `feat: macros y escenas`.

### Tarea 14: Favoritos de canales y apps

**Files:**
- Modify: `…/data/local/Entities.kt`, `AppDatabase.kt` (misma migración 1→2 que la Tarea 13 si se hacen juntas), `Daos.kt`
- Modify: `…/presentation/screens/AppsAndChannelsScreen.kt`
- Test: `…/data/local/FavoritesDaoTest.kt`

- [ ] **Paso 1:** Escribir `FavoritesDaoTest` (Room en memoria): insertar, listar por dispositivo ordenado por posición y borrar un favorito.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar entidad `FavoriteEntity(id, deviceId, label, type, payload, position)`, el DAO y la sección "Favoritos" arriba en `AppsAndChannelsScreen.kt` (mantener pulsado para guardar o quitar).
- [ ] **Paso 4:** PASS y commit `feat: favoritos de canales y apps`.

### Tarea 15: Estado del TV y Wake-on-LAN automático

**Files:**
- Modify: `…/domain/manager/RemoteControllerManager.kt`, `…/core/lifecycle/AppForegroundObserver.kt`
- Modify: `…/domain/adapter/RemoteAdapter.kt` (añadir `suspend fun isPoweredOn(): Boolean? = null`)
- Test: `…/domain/manager/AutoWakePolicyTest.kt`

**Interfaces:**
- Produces: `fun shouldAutoWake(autoWakeEnabled: Boolean, hasMac: Boolean, powered: Boolean?): Boolean`.

- [ ] **Paso 1:** Escribir `AutoWakePolicyTest`: despierta solo con el ajuste activo, MAC conocida y estado `false`; con estado `null` (desconocido) no envía nada.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar la política. `isPoweredOn` tiene implementación real en Roku (`/query/device-info`), Sony (`getPowerStatus`) y webOS; el resto devuelve `null`. Mostrar el estado en `CyberTelemetryHeader`.
- [ ] **Paso 4:** PASS y commit `feat: estado del TV y auto-despertar`.

### Tarea 16: Cambio rápido de dispositivo

**Files:**
- Modify: `…/presentation/components/CyberTelemetryHeader.kt`
- Test: `…/presentation/DeviceSwitcherTest.kt`

- [ ] **Paso 1:** Escribir una prueba de ViewModel: `selectDevice(id)` cambia `selectedDevice` y deja el anterior desconectado.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA o PASS. Si ya pasa, la lógica existe y solo falta la UI.
- [ ] **Paso 3:** Añadir un menú desplegable en el encabezado con los dispositivos guardados, que llama a `RemoteViewModel.selectDevice`.
- [ ] **Paso 4:** Commit `feat: selector rápido de dispositivo`.

### Tarea 17: Voz y compartir al TV

**Files:**
- Modify: `…/presentation/components/TouchpadCard.kt` o el teclado actual (botón de micrófono con `RecognizerIntent`)
- Create: `…/ShareReceiverActivity.kt` y filtro `ACTION_SEND` en `AndroidManifest.xml`
- Test: `…/ShareParserTest.kt`

**Interfaces:**
- Produces: `fun parseSharedUrl(text: String): SharedMedia?` con `data class SharedMedia(val kind: Kind, val id: String)` y `enum class Kind { YOUTUBE, OTHER }`.

- [ ] **Paso 1:** Escribir `ShareParserTest`: `https://youtu.be/abc123` y `https://www.youtube.com/watch?v=abc123` devuelven `SharedMedia(YOUTUBE, "abc123")`; texto sin URL devuelve `null`.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar el parser. Roku: `POST /launch/837?contentId=<id>`. Android TV: intent a la app de YouTube. Las marcas sin soporte de deep link muestran un aviso claro en lugar de fallar en silencio.
- [ ] **Paso 4:** Voz: el resultado del reconocedor se envía como `RemoteCommand.Text`. No se necesitan permisos extra al usar `RecognizerIntent`.
- [ ] **Paso 5:** PASS y commit `feat: voz y compartir contenido al TV`.

### Tarea 18: Aprendizaje de IR

**Files:**
- Modify: `…/hardware/ir/IrHardwareManager.kt`, `…/data/repository/IrCodeRepositoryImpl.kt`
- Test: `…/hardware/ir/IrLearningTest.kt`

**Nota previa:** Android no ofrece una API pública de recepción IR. La mayoría de los teléfonos solo transmiten. **Este paso se hace solo si el usuario tiene un hub externo con receptor** (por ejemplo Broadlink o un bridge propio, ya existe `HttpIrBridgeTransmitter`). Confirmar antes de empezar.

- [ ] **Paso 1:** Escribir `IrLearningTest`: el parser convierte una captura de pulsos (µs) en `IrSignal` y puede ir a Pronto con `ProntoHex`.
- [ ] **Paso 2:** Ejecutar. Esperado: FALLA.
- [ ] **Paso 3:** Implementar `learn(): IrSignal` en el bridge HTTP y guardar la señal como codeset personalizado en `IrCodeRepositoryImpl`.
- [ ] **Paso 4:** PASS y commit `feat: aprendizaje de códigos IR vía hub`.

---

## Fase 3: Calidad transversal

### Tarea 19: Pruebas de adaptadores existentes

**Files:**
- Test: `…/adapters/webos/WebOsAdapterTest.kt`, `…/adapters/tizen/TizenAdapterTest.kt`, `…/adapters/roku/RokuEcpAdapterTest.kt`

- [ ] **Paso 1:** Para Roku, probar con `MockWebServer` que `send(VolumeUp)` hace `POST /keypress/VolumeUp`. Para webOS y Tizen, probar los mapas de teclas (`WebOsKeys`, `TizenKeys`) con una tabla de casos, porque los WebSocket reales necesitan un servidor.
- [ ] **Paso 2:** Ejecutar `./gradlew :app:testDebugUnitTest`. Esperado: todo PASS.
- [ ] **Paso 3:** Commit `test: cobertura de adaptadores existentes`.

### Tarea 20: Revisión final

- [ ] **Paso 1:** Ejecutar `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Esperado: BUILD SUCCESSFUL.
- [ ] **Paso 2:** Revisar la rama completa con una pasada de `code-review` y de `security-scan`, con foco en secretos (`pairingToken`, PSK, claves ADB) y en TLS (`TofuTls`).
- [ ] **Paso 3:** Actualizar el README con la tabla de marcas soportadas y su método de emparejamiento.

---

## Autorrevisión

- **Cobertura:** las 10 mejoras propuestas tienen tarea: botones de volumen (11), widget (12), macros (13), favoritos (14), estado/auto-WoL (15), selector de dispositivo (16), voz (17), compartir (17), marcas nuevas (1–10) e IR (18). La calidad (0, 19, 20) también está cubierta.
- **Consistencia de tipos:** `DeviceKind` nuevos y firmas de `XxxKeys` siguen el mismo patrón en todas las tareas. `MacroResult` y `QuickAction` se definen una sola vez.
- **Pendientes de verificar con hardware:** códigos IRCC de Sony, nombres de paquete de Fire TV, pares codeset/code de Vizio y claves de Panasonic. Ninguno se pudo comprobar sin los equipos.
- **Decisiones que necesitan al usuario:** qué carpetas del repo se pueden ignorar (Tarea 0), ADB frente a API HTTP en Fire TV (Tarea 5), dependencia MQTT para Hisense (Tarea 6), si hay hub con receptor IR (Tarea 18) y qué TVs reales hay para probar.
