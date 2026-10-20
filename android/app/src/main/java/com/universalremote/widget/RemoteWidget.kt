package com.universalremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.universalremote.R
import com.universalremote.core.di.ApplicationScope
import com.universalremote.domain.manager.RemoteControllerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget de escritorio con encendido, volumen y silencio sobre el dispositivo seleccionado.
 * RemoteViews nativo (sin Glance): cuatro botones no justifican una dependencia nueva.
 */
@AndroidEntryPoint
class RemoteWidget : AppWidgetProvider() {

    @Inject lateinit var manager: RemoteControllerManager

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_remote).apply {
            BUTTONS.forEach { (viewId, action) -> setOnClickPendingIntent(viewId, pendingIntent(context, action)) }
        }
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_QUICK) return
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { name -> QuickAction.entries.firstOrNull { it.name == name } }
            ?: return
        val pending = goAsync()
        scope.launch {
            try {
                // Con la app cerrada el gestor acaba de crearse y aún está restaurando el último dispositivo.
                withTimeoutOrNull(RESTORE_TIMEOUT_MS) { manager.activeDevice.first { it != null } }
                manager.dispatch(action.toCommand())
            } finally {
                pending.finish()
            }
        }
    }

    private fun pendingIntent(context: Context, action: QuickAction): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.ordinal,
            Intent(context, RemoteWidget::class.java).setAction(ACTION_QUICK).putExtra(EXTRA_ACTION, action.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val ACTION_QUICK = "com.universalremote.widget.QUICK_ACTION"
        const val EXTRA_ACTION = "action"
        const val RESTORE_TIMEOUT_MS = 2_000L
        val BUTTONS = listOf(
            R.id.widget_power to QuickAction.POWER,
            R.id.widget_vol_down to QuickAction.VOLUME_DOWN,
            R.id.widget_vol_up to QuickAction.VOLUME_UP,
            R.id.widget_mute to QuickAction.MUTE,
        )
    }
}
