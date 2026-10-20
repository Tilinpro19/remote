# UniversalRemote — app Android

Mando a distancia universal para Android (Kotlin + Jetpack Compose). Controla televisores y reproductores
por red local, infrarrojos o un puente IR por HTTP, y degrada a los transportes que el teléfono tenga disponibles.

## Funciones

- **Descubrimiento en red local** mediante SSDP y mDNS, y alta de dispositivos encontrados.
- **Marcas por red:** ver la tabla de abajo.
- **Infrarrojos:** emisor IR integrado (`ConsumerIrManager`) o puente IR por HTTP; códigos Pronto Hex y
  codificación de protocolos (NEC, Samsung, Sony…).
- **Catálogo IRDB** incluido en `assets/irdb` e importado a Room al arrancar.
- **Wake-on-LAN** para encender equipos apagados.
- **Reconexión automática** al volver a primer plano, con estado de conexión visible.
- **Háptica** en cada pulsación.
- **Mando adaptativo:** solo muestra los controles (encendido, navegación, volumen, canales,
  multimedia, dígitos) que admite el dispositivo activo.
- **Macros y escenas:** se graban usando el mando y se ejecutan paso a paso; si un paso falla, la macro
  se detiene e indica cuál.
- **Favoritos** de apps y canales por dispositivo (mantener pulsado para guardar o quitar).
- **Botones de volumen del teléfono** para el TV (opcional, solo con el mando visible).
- **Widget de escritorio** con encendido, volumen y silencio.
- **Estado de encendido** en el encabezado y **encendido automático** al abrir la app (opcional).
- **Selector rápido de dispositivo** desde el encabezado.
- **Dictado por voz** en el teclado y **"Compartir → Ver en el TV"** para vídeos de YouTube (Roku y Android TV).

## Marcas soportadas

| Marca | Protocolo | Emparejamiento |
|---|---|---|
| Roku | ECP (HTTP 8060) | Ninguno (activar *Acceso a la red*) |
| Android / Google TV | Android TV Remote v2 (TLS 6466/6467) | Código en pantalla |
| LG webOS | SSAP sobre WebSocket (3001) | Aceptar el aviso en el TV |
| Samsung Tizen | WebSocket (8002) | Aceptar el aviso en el TV |
| Sony Bravia | IRCC-IP + REST (HTTP 80) | Clave PSK configurada en el TV |
| Vizio SmartCast | REST HTTPS (7345) | PIN de 4 dígitos |
| Philips | JointSpace v6 (HTTPS 1926, Digest) | PIN de 4 dígitos |
| Amazon Fire TV | ADB por red (5555) | Aceptar la clave ADB en el TV |
| Hisense VIDAA | MQTT sobre TLS (36669) | Código de 4 dígitos |
| Panasonic Viera | SOAP (HTTP 55000, sin cifrado) | Ninguno; los modelos con cifrado no están soportados |
| Yamaha MusicCast | HTTP (80) | Ninguno |
| Denon / Marantz | Telnet (23) | Ninguno |
| Kodi | JSON-RPC (HTTP 8080) | Usuario y contraseña solo si el servidor web los pide |
| TV clásico | Infrarrojos (emisor del teléfono o puente HTTP) | Prueba guiada de códigos |

Los equipos con certificado autofirmado se fijan en el primer emparejamiento (TOFU). Los códigos de
Sony, Vizio, Panasonic y los paquetes de Fire TV no se han verificado todavía con hardware real.

## Arquitectura

```
app/src/main/java/com/universalremote/
  domain/        Modelos (RemoteCommand, DeviceProfile, ConnectionState…), contratos y RemoteControllerManager
  data/          Room (dispositivos, códigos IR, macros, favoritos), DataStore, importador IRDB, repositorios
  adapters/      Una implementación de RemoteAdapter por marca (ver tabla) e IR
  hardware/      Emisor IR, codificador de protocolos, Pronto Hex, háptica
  network/       Descubrimiento SSDP, Wake-on-LAN, utilidades OkHttp
  presentation/  ViewModels (mando, macros, favoritos) y pantallas Compose
  widget/        Widget de escritorio (RemoteViews)
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
