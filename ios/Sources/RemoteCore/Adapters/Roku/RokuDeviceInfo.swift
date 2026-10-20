import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

/// Respuesta de `GET /query/device-info`.
public struct RokuDeviceInfo: Sendable, Equatable {
    public let fields: [String: String]

    public init(xml: Data) throws {
        let collector = FlatXMLCollector()
        let parser = XMLParser(data: xml)
        parser.delegate = collector
        guard parser.parse(), collector.root == "device-info" else {
            throw RemoteError.invalidResponse("device-info de Roku no es XML válido")
        }
        fields = collector.fields
    }

    public var serialNumber: String? { fields["serial-number"] }
    public var modelName: String? { fields["model-name"] }
    public var friendlyName: String? {
        fields["user-device-name"] ?? fields["friendly-device-name"] ?? fields["default-device-name"]
    }
    public var isTV: Bool { fields["is-tv"] == "true" }
    /// `PowerOn`, `DisplayOff`, `Ready`, `Headless`…
    public var powerMode: String? { fields["power-mode"] }
    public var supportsWakeOnWLAN: Bool { fields["supports-wake-on-wlan"] == "true" }

    /// MAC de la interfaz activa (la que debe recibir el Magic Packet).
    public var macAddress: MACAddress? {
        let active = fields["network-type"] == "ethernet" ? fields["ethernet-mac"] : fields["wifi-mac"]
        let candidate = active ?? fields["wifi-mac"] ?? fields["ethernet-mac"]
        return candidate.flatMap { try? MACAddress($0) }
    }
}

/// Recoge los hijos directos del elemento raíz como pares clave/valor.
private final class FlatXMLCollector: NSObject, XMLParserDelegate {
    private(set) var root: String?
    private(set) var fields: [String: String] = [:]
    private var depth = 0
    private var text = ""

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        if root == nil { root = elementName }
        depth += 1
        text = ""
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        text += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if depth == 2 {
            fields[elementName] = text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        depth -= 1
        text = ""
    }
}
