# UniversalRemote — app Android

Mando a distancia universal para Android (Kotlin + Jetpack Compose). Controla televisores y reproductores
por red local, infrarrojos o un puente IR por HTTP, y degrada a los transportes que el teléfono tenga disponibles.

## Funciones

- **Descubrimiento en red local** mediante SSDP y alta de dispositivos encontrados.
- **Roku (ECP):** control completo por HTTP, información del dispositivo y entrada de texto.
- **Infrarrojos:** emisor IR integrado (`ConsumerIrManager`) o puente IR por HTTP; códigos Pronto Hex y
  codificación de protocolos (NEC, Samsung, Sony…).
- **Catálogo IRDB** incluido en `assets/irdb` e importado a Room al arrancar.
- **Wake-on-LAN** para encender equipos apagados.
- **Reconexión automática** al volver a primer plano, con estado de conexión visible.
- **Háptica** en cada pulsación.
- **Mando adaptativo:** solo muestra los controles (encendido, navegación, volumen, canales,
  multimedia, dígitos) que admite el dispositivo activo.

## Arquitectura

```
app/src/main/java/com/universalremote/
  domain/        Modelos (RemoteCommand, DeviceProfile, ConnectionState…), contratos y RemoteControllerManager
  data/          Room (dispositivos, códigos IR), DataStore, importador IRDB, repositorios
  adapters/      Implementaciones de RemoteAdapter: Roku ECP, IR
  hardware/      Emisor IR, codificador de protocolos, Pronto Hex, háptica
  network/       Descubrimiento SSDP, Wake-on-LAN, utilidades OkHttp
  presentation/  RemoteViewModel + RemoteUiState
  core/          Inyección con Hilt, observador de primer plano
  MainActivity   UI en Compose (Material 3, tema oscuro)
```

Tecnologías: Kotlin 2.2, Compose (BOM 2025.08), Hilt, Room, DataStore, OkHttp y corrutinas.

## Requisitos

- JDK 17 o superior
- Android SDK con API 36 (configurado en `local.properties`)
- Dispositivo con Android 9 (API 28) o superior

## Compilar e instalar

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

## Notas

- **Roku:** activa *Control por apps móviles › Acceso a la red* en el Roku, o responderá 403.
- El tráfico HTTP sin cifrar solo se permite en la red local (`network_security_config.xml`).
- La versión release necesita un keystore de firma propio.
