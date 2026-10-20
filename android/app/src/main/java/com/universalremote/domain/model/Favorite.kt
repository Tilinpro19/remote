package com.universalremote.domain.model

/** Acceso directo de la sección "Favoritos": una app o un canal del dispositivo. */
data class Favorite(val id: Long, val label: String, val target: Target) {
    sealed interface Target {
        data class App(val app: StreamingApp) : Target
        data class Channel(val number: String) : Target
    }
}
