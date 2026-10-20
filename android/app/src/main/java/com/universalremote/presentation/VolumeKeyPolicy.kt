package com.universalremote.presentation

/**
 * Las teclas físicas de volumen sólo controlan el TV cuando el usuario lo activó, está viendo el
 * mando y hay sesión; en cualquier otro caso se respeta el volumen normal del teléfono.
 */
fun shouldInterceptVolumeKeys(enabled: Boolean, onRemoteScreen: Boolean, connected: Boolean): Boolean =
    enabled && onRemoteScreen && connected
