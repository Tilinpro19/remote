package com.universalremote.presentation

import com.universalremote.domain.model.DeviceKind

/** Cómo se vincula cada marca la primera vez; la UI lo muestra al darla de alta. */
enum class PairingHint(val description: String) {
    NONE("No necesita vinculación"),
    PIN("El TV mostrará un código que tendrás que escribir aquí"),
    PASSPHRASE("Te pediremos la clave configurada en el equipo"),
    CONFIRM_ON_TV("Acepta el aviso que aparecerá en el TV"),
}

/** Marcas que se pueden dar de alta por IP: todas las de red. IR y Bluetooth tienen su propio flujo. */
val manualAddKinds: List<DeviceKind> = DeviceKind.entries.filter { it.defaultControlPort != null }

/** `when` exhaustivo: al añadir un [DeviceKind] el compilador obliga a darle nombre visible. */
fun DeviceKind.displayName(): String = when (this) {
    DeviceKind.ROKU -> "Roku"
    DeviceKind.ANDROID_TV -> "Android / Google TV"
    DeviceKind.LG_WEBOS -> "LG webOS"
    DeviceKind.SAMSUNG_TIZEN -> "Samsung Smart TV"
    DeviceKind.SONY_BRAVIA -> "Sony Bravia"
    DeviceKind.VIZIO -> "Vizio SmartCast"
    DeviceKind.PHILIPS_JOINTSPACE -> "Philips"
    DeviceKind.FIRE_TV -> "Amazon Fire TV"
    DeviceKind.HISENSE_VIDAA -> "Hisense VIDAA"
    DeviceKind.PANASONIC_VIERA -> "Panasonic Viera"
    DeviceKind.YAMAHA_MUSICCAST -> "Yamaha MusicCast"
    DeviceKind.DENON_AVR -> "Denon / Marantz"
    DeviceKind.KODI -> "Kodi"
    DeviceKind.INFRARED -> "Televisor Clásico (IR)"
    DeviceKind.BLUETOOTH_HID -> "Bluetooth"
}

fun DeviceKind.pairingHint(): PairingHint = when (this) {
    DeviceKind.ROKU, DeviceKind.PANASONIC_VIERA, DeviceKind.YAMAHA_MUSICCAST, DeviceKind.DENON_AVR,
    DeviceKind.INFRARED, DeviceKind.BLUETOOTH_HID -> PairingHint.NONE
    DeviceKind.ANDROID_TV, DeviceKind.VIZIO, DeviceKind.PHILIPS_JOINTSPACE, DeviceKind.HISENSE_VIDAA -> PairingHint.PIN
    // Kodi sólo la pide si su servidor web tiene usuario y contraseña.
    DeviceKind.SONY_BRAVIA, DeviceKind.KODI -> PairingHint.PASSPHRASE
    DeviceKind.LG_WEBOS, DeviceKind.SAMSUNG_TIZEN, DeviceKind.FIRE_TV -> PairingHint.CONFIRM_ON_TV
}
