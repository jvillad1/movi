package com.jvillada.movi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jvillada.movi.MainActivity
import com.jvillada.movi.app.R
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.COLORES_OSCUROS
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.TextoDelWidget
import com.jvillada.movi.ui.dashboard.textoDelWidget

/**
 * # El widget «Te quedan hoy»
 *
 * La cifra grande de Hoy en la pantalla de inicio del teléfono: «Te quedan $4.963.400» —o «Te pasaste
 * por», o «Tu plata» cuando el héroe dice eso— y abajo de cuándo es («a las 9:40»). El texto lo decide
 * [textoDelWidget] (`:shared`, con sus pruebas); acá solo se pinta.
 *
 * **Ningún widget pide red.** Lee la instantánea del Inicio que la app ya guarda en el aparato
 * ([InstantaneaDelInicio]). Se repinta:
 * - cuando la app guarda una instantánea nueva o la borra al cerrar sesión (`InstantaneaDelInicio.alCambiar`,
 *   que conecta `MoviAplicacion`);
 * - y cada media hora, el mínimo que deja el sistema (`updatePeriodMillis` en `widget_te_quedan_info.xml`),
 *   para que el «ayer» y el período vigente se recalculen aunque la app no se abra.
 *
 * Tocarlo abre la app por la puerta de siempre: si está «Entrar con huella», pide el dedo antes de
 * mostrar nada. El widget no es más que lo que el dueño ya dejó a la vista en su pantalla de inicio.
 *
 * Con `AppWidgetProvider` + `RemoteViews` y sin dependencias nuevas (Glance sumaría una librería y un
 * runtime de Compose aparte para tres renglones de texto). Los colores salen de `Tokens.kt` en el tema
 * que el dueño eligió en la app.
 */
class WidgetTeQuedan : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        pintar(context, manager, ids)
    }

    companion object {
        /** Repinta todos los widgets puestos. Sin ninguno puesto, no hace nada. */
        fun pintarTodos(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WidgetTeQuedan::class.java))
            if (ids.isNotEmpty()) pintar(context, manager, ids)
        }

        private fun pintar(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // Un widget que lanza tumba el proceso entero de la app: si algo de lo guardado no se
            // puede leer, dice «Abre Movi» en vez de caerse.
            val texto = runCatching {
                val usuario = SessionManager.userId
                textoDelWidget(
                    instantanea = InstantaneaDelInicio.delAparato.datos(usuario),
                    selladaEn = InstantaneaDelInicio.delAparato.selladaEn(usuario),
                    haySesion = SessionManager.isLoggedIn,
                    ahora = System.currentTimeMillis(),
                )
            }.getOrElse { textoDelWidget(null, null, haySesion = false, ahora = 0L) }
            val oscuro = runCatching { TemaStore.oscuro }.getOrDefault(true)
            manager.updateAppWidget(ids, vistas(context, texto, oscuro))
        }

        internal fun vistas(context: Context, texto: TextoDelWidget, oscuro: Boolean): RemoteViews {
            val colores = if (oscuro) COLORES_OSCUROS else COLORES_CLAROS
            return RemoteViews(context.packageName, R.layout.widget_te_quedan).apply {
                setInt(R.id.widget_fondo, "setColorFilter", colores.tarjeta.toArgb())
                if (texto.cifra == null) {
                    // «Abre Movi» en el lugar de la cifra, y sin rótulo encima.
                    setViewVisibility(R.id.widget_rotulo, View.GONE)
                    setTextViewText(R.id.widget_cifra, texto.rotulo)
                    setTextColor(R.id.widget_cifra, colores.marca.toArgb())
                } else {
                    setViewVisibility(R.id.widget_rotulo, View.VISIBLE)
                    setTextViewText(R.id.widget_rotulo, texto.rotulo)
                    setTextColor(R.id.widget_rotulo, colores.textoMedio.toArgb())
                    setTextViewText(R.id.widget_cifra, texto.cifra)
                    setTextColor(R.id.widget_cifra, (if (texto.enRojo) colores.sale else colores.texto).toArgb())
                }
                setTextViewText(R.id.widget_pie, texto.pie)
                setTextColor(R.id.widget_pie, colores.textoApagado.toArgb())
                setContentDescription(
                    R.id.widget_raiz,
                    listOfNotNull(texto.rotulo, texto.cifra, texto.pie).joinToString(" "),
                )
                setOnClickPendingIntent(R.id.widget_raiz, abrirMovi(context))
            }
        }

        private fun abrirMovi(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
