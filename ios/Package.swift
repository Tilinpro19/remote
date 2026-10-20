// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "UniversalRemote",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        // Producto que xtool empaqueta como .app (ver `product` en xtool.yml).
        .library(name: "UniversalRemote", targets: ["UniversalRemote"]),
    ],
    targets: [
        // Núcleo sin UI: dominio, gestor, red, adaptadores e IR.
        .target(name: "RemoteCore"),
        // Punto de entrada de la app; aquí se montan las pantallas existentes.
        .target(
            name: "UniversalRemote",
            dependencies: ["RemoteCore"]
        ),
        .testTarget(
            name: "RemoteCoreTests",
            dependencies: ["RemoteCore"]
        ),
    ]
)
