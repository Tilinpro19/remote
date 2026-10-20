#if canImport(SwiftUI)
import RemoteCore
import SwiftUI

@main
struct UniversalRemoteApp: App {
    @State private var remote = RemoteControllerManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(remote)
                .task { await remote.bootstrap() }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                remote.applicationDidBecomeActive()
            case .background:
                remote.applicationDidEnterBackground()
            case .inactive:
                break
            @unknown default:
                break
            }
        }
    }
}
#endif
