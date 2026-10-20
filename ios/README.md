# UniversalRemote — núcleo iOS (SwiftPM + xtool)

```
Package.swift / xtool.yml          Configuración declarativa (sin .xcodeproj)
Resources/Info.plist               Red local, NSBonjourServices, BLE, ATS local
Resources/*.entitlements           Multicast (activar en xtool.yml cuando Apple lo conceda)
Sources/RemoteCore/
  Domain/      RemoteCommand, RemoteAdapter, DeviceProfile, MACAddress, ConnectionState, RemoteError
  Manager/     RemoteControllerManager (@Observable), hápticos, ProfileStore, TouchpadTracker
  Network/     NWConnection async, TCP/TLS (autofirmado + TOFU), WebSocket TOFU, WoL, mDNS, SSDP, HTTP
  Adapters/    Roku (ECP + WoL), Android TV (Remote v2), LG webOS (SSAP), Samsung Tizen, PointerAccumulator
  IR/          ProntoHex → IRSignal (Hz + µs)
Sources/UniversalRemote/           @main + RootView (punto de montaje de la UI existente)
Tests/RemoteCoreTests/             swift-testing
```

## Flujo

```bash
xtool setup          # una vez: SDK de Darwin a partir de Xcode.xip
xtool auth           # Apple ID / cuenta de desarrollador
xtool dev            # compila, firma e instala en el iPhone conectado
swift test           # en macOS: tests de RemoteCore
```

## Limitaciones de plataforma

- **Multicast/broadcast (SSDP, WoL broadcast):** iOS 14+ exige `com.apple.developer.networking.multicast`.
  Sin él, `SSDPDiscovery` y el broadcast fallan con `permissionDenied`; el escáner sigue con mDNS y
  `addManualDevice(...)`, y WoL cae al unicast a la última IP conocida.
- **Network.framework no admite broadcast UDP**, por eso el Magic Packet broadcast usa un socket BSD
  con `SO_BROADCAST`; el unicast sí usa `NWConnection`.
- **Roku ECP 403:** el usuario debe activar *Control por apps móviles › Acceso a la red* en el Roku.
- **Android TV:** el certificado de cliente (RSA 2048, X.509 propio) vive en el llavero. Si se borra
  (reinstalación) el adaptador lo detecta y vuelve a pedir el código de 6 caracteres del TV.
- **Cambio de IP:** si un TV guardado deja de responder, el gestor escanea 5 s y, si reaparece con el
  mismo tipo y nombre (o número de serie en Roku), actualiza su IP y reconecta (`relocate`).
- **Sockets en segundo plano:** iOS los suspende; el gestor desconecta al pasar a background y reconecta
  (con backoff exponencial) al volver a primer plano.
