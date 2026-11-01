package com.jvillada.movi.aplicacion

import android.app.ActivityManager
import android.app.Application
import com.jvillada.movi.avisos.Avisador
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.fallos.CapturaDeFallos
import com.jvillada.movi.fallos.SubirFallosWorker
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.sms.SmsBackfillWorker
import com.jvillada.movi.sms.SubirCapturasPorSubirWorker
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.widget.WidgetTeQuedan

/**
 * La `Application` de Movi. Existe por una sola cosa: conectar el widget «Te quedan hoy» con la
 * instantánea del Inicio **en cualquier proceso**, no solo cuando se abre `MainActivity`.
 *
 * El cierre de sesión también lo dispara un Worker de fondo (tres 401 seguidos, ver
 * `SessionManager.clear`), sin actividad. Si el aviso se conectara en `MainActivity`, ese cierre
 * dejaría la cifra del dueño en su pantalla de inicio hasta la siguiente media hora.
 */
class MoviAplicacion : Application() {
    override fun onCreate() {
        super.onCreate()
        val contexto = applicationContext
        // Vuelta 100 · los fallos: lo primero, para que una caída en lo que sigue también quede. El
        // handler guarda en disco y deja seguir al del sistema; lo guardado sube al próximo arranque
        // (o apenas haya red), y lo que un Worker atrapa sube en cuanto lo reporta.
        CapturaDeFallos.conocerElAparato(contexto)
        CapturaDeFallos.instalar(CapturaDeFallos.carpeta(contexto))
        CapturaDeFallos.alGuardarAtrapado = { SubirFallosWorker.encolarSiHayAlgo(it) }
        runCatching { SubirFallosWorker.encolarSiHayAlgo(contexto) }
        InstantaneaDelInicio.alCambiar = { WidgetTeQuedan.pintarTodos(contexto) }
        // «Ocultar los saldos»: el widget pinta la misma cifra que el héroe, así que también se
        // tapa (o se destapa) en el acto.
        SaldosOcultosStore.alCambiar = { WidgetTeQuedan.pintarTodos(contexto) }
        // El server cerró la sesión (ver `SessionManager.seRechazoElToken`). Con la app a la vista,
        // el login ya lo dice; sin ella —un Worker subiendo un SMS a las 3 a. m.— nadie se entera,
        // y eso es lo que pasó el 26-oct: la captura muda y el dueño creyendo que estaba adentro.
        SessionManager.alRechazarseLaSesion = {
            if (!seVeLaApp()) Avisador.avisarSesionCerrada(contexto)
        }
        // Volvió a entrar: se quita el aviso, sube lo que el banco mandó en la pausa y se barre el
        // inbox por si algún SMS ni siquiera alcanzó a guardarse.
        SessionManager.alEntrar = {
            Avisador.quitarSesionCerrada(contexto)
            SubirCapturasPorSubirWorker.encolar(contexto)
            SmsBackfillWorker.barrerAhora(contexto)
        }
    }

    private fun seVeLaApp(): Boolean = runCatching {
        ActivityManager.RunningAppProcessInfo()
            .also { ActivityManager.getMyMemoryState(it) }
            .importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)
}
