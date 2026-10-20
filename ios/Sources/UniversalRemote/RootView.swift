#if canImport(SwiftUI)
import RemoteCore
import SwiftUI

/// Punto de montaje de las pantallas ya diseñadas (escáner híbrido, mando sencillo, mando pro,
/// WoL/IRDB, validación de emparejamiento). Cada pantalla obtiene el gestor con
/// `@Environment(RemoteControllerManager.self)` y llama, por ejemplo, a:
///
/// - `remote.send(.volumeUp)` / `remote.send(wireName: "CMD_DPAD_CENTER")`
/// - `remote.touchpadBegan/Moved/Ended(x:y:)` desde un `DragGesture(minimumDistance: 0)`
/// - `remote.startDiscovery()` y `remote.pair(with:)` en el escáner
/// - observa `remote.connectionState`, `remote.lastError` y `remote.keyboardRequest`
struct RootView: View {
    var body: some View {
        EmptyView()
    }
}
#endif
