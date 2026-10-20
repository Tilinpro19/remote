package com.universalremote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.universalremote.core.di.ApplicationScope
import com.universalremote.domain.manager.RemoteControllerManager
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Destino de "Compartir" en otras apps: abre en el TV seleccionado el vídeo de YouTube compartido.
 * No tiene pantalla; el resultado se informa con un aviso breve.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var manager: RemoteControllerManager

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val media = text?.let(::parseSharedUrl)
        when {
            media == null -> toast("No se encontró ningún enlace para enviar al TV")
            media.kind != Kind.YOUTUBE -> toast("Por ahora sólo se pueden enviar vídeos de YouTube")
            else -> send(RemoteCommand.PlayYouTube(media.id))
        }
        finish()
    }

    private fun send(command: RemoteCommand) {
        val appContext = applicationContext
        scope.launch {
            // Con la app cerrada el gestor acaba de crearse y aún está restaurando el último dispositivo.
            val device = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { manager.activeDevice.first { it != null } }
            val message = when {
                device == null -> "Elige primero un televisor en la app"
                !manager.supports(command) -> "${device.name} no permite abrir vídeos compartidos"
                else -> try {
                    manager.sendAndAwait(command)
                    "Abriendo el vídeo en ${device.name}"
                } catch (e: RemoteError) {
                    "No se pudo enviar: ${e.message}"
                }
            }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val RESTORE_TIMEOUT_MS = 2_000L
    }
}
