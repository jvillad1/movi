package com.jvillada.movi.aplicacion

import android.app.Application
import com.jvillada.movi.data.SaldosOcultosStore
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
        InstantaneaDelInicio.alCambiar = { WidgetTeQuedan.pintarTodos(contexto) }
        // «Ocultar los saldos»: el widget pinta la misma cifra que el héroe, así que también se
        // tapa (o se destapa) en el acto.
        SaldosOcultosStore.alCambiar = { WidgetTeQuedan.pintarTodos(contexto) }
    }
}
