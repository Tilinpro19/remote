import Foundation

/// Persistencia JSON de perfiles en Application Support (con protección de datos en iOS).
public actor ProfileStore {
    public static let shared = ProfileStore(fileURL: ProfileStore.defaultFileURL())

    private let fileURL: URL

    public init(fileURL: URL) {
        self.fileURL = fileURL
    }

    public func load() throws -> [DeviceProfile] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return [] }
        do {
            let data = try Data(contentsOf: fileURL)
            return try JSONDecoder.profiles.decode([DeviceProfile].self, from: data)
        } catch {
            throw RemoteError.storage("lectura de perfiles: \(error.localizedDescription)")
        }
    }

    public func save(_ profiles: [DeviceProfile]) throws {
        do {
            try FileManager.default.createDirectory(
                at: fileURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            let data = try JSONEncoder.profiles.encode(profiles)
            #if os(iOS)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            #else
            try data.write(to: fileURL, options: .atomic)
            #endif
        } catch {
            throw RemoteError.storage("escritura de perfiles: \(error.localizedDescription)")
        }
    }

    public static func defaultFileURL() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base
            .appendingPathComponent("UniversalRemote", isDirectory: true)
            .appendingPathComponent("profiles.json")
    }
}

private extension JSONEncoder {
    static var profiles: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }
}

private extension JSONDecoder {
    static var profiles: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
