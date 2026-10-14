package com.universalremote.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.universalremote.domain.manager.RemoteControllerManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registrado en `ProcessLifecycleOwner`: mantiene sockets abiertos sólo con la app visible.
 * `ON_STOP` del proceso llega ~700 ms después de la última Activity, por lo que rotaciones y
 * cambios de pantalla internos no provocan reconexiones.
 */
@Singleton
class AppForegroundObserver @Inject constructor(
    private val manager: RemoteControllerManager,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) = manager.onForeground()

    override fun onStop(owner: LifecycleOwner) = manager.onBackground()
}
