package com.jvillada.movi

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.jvillada.movi.avisos.AvisoDeVencimientosWorker
import com.jvillada.movi.avisos.Avisador
import com.jvillada.movi.avisos.DestinoDesdeAfuera
import com.jvillada.movi.avisos.EXTRA_ABRIR
import com.jvillada.movi.avisos.destinoDeAviso
import com.jvillada.movi.compartir.esCompartirConMovi
import com.jvillada.movi.compartir.leerLoCompartido
import com.jvillada.movi.compartir.urisCompartidos
import com.jvillada.movi.ui.papeles.PapelesCompartidos
import com.jvillada.movi.shared.db.DatabaseDriverFactory
import com.jvillada.movi.sms.SmsBackfillWorker
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.SmsFilterRefreshWorker

/**
 * `FragmentActivity` y no `ComponentActivity` por «Entrar con huella»: el `BiometricPrompt` de
 * AndroidX se monta como fragmento y no sabe hospedarse en otra cosa. `FragmentActivity` ES una
 * `ComponentActivity`, así que `enableEdgeToEdge`, `setContent` y el resto siguen igual; lo único
 * que cambia es que ahora hay un `FragmentManager` donde el prompt pueda vivir. Sin esto,
 * `huellaDeLaPlataforma()` no encuentra actividad y la función queda apagada en el teléfono
 * —sin fallar, pero sin existir—.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // El tema de la app es oscuro (MinBg) en todas las plataformas: barras
            // transparentes con iconos claros.
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // El repositorio offline-first de :core (LocalRepository + SQLDelight) necesita el
        // contexto antes del primer createRepository() — igual que hace la web/iOS con sus
        // propios drivers.
        DatabaseDriverFactory.init(applicationContext)
        // Refresco diario de la config del filtro sin depender de que el usuario abra la
        // app ni de que haya habido una captura exitosa (ver SmsFilterRefreshWorker).
        SmsFilterRefreshWorker.schedule(applicationContext)
        // Y el barrido de SMS cada 6 horas: la captura en vivo se pierde mensajes cuando Android
        // castiga a la app en segundo plano (ver SmsBackfillWorker).
        SmsBackfillWorker.schedule(applicationContext)
        // Y uno ahora mismo: abrir la app es cuando va a mirar los números, y el periódico puede
        // estar a horas de su turno.
        SmsBackfillWorker.barrerAhora(applicationContext)
        // Y un refresh oportunista en cada apertura — reemplaza el que disparaba la
        // pantalla del sensor cuando era la única UI del APK.
        SmsFilterConfigStore.refreshIfStale(applicationContext)
        // Ola 1 · Movi avisa: los dos canales (con nombre en español, para silenciarlos por
        // separado) y el aviso diario de vencimientos. El Worker se programa siempre y decide al
        // correr: con el interruptor apagado o sin permiso no hace nada.
        Avisador.crearCanales(applicationContext)
        AvisoDeVencimientosWorker.programar(applicationContext)
        // Si la app se abrió tocando un aviso, a qué pantalla va (lo cumple App() tras la puerta).
        recibirAviso(intent)
        // Ola 2 · «Compartir con Movi». Solo en un arranque de verdad: si Android recrea la
        // actividad con el mismo Intent, lo compartido ya se recibió la primera vez.
        if (savedInstanceState == null) recibirCompartido(intent)
        setContent {
            App()
        }
    }

    /** La app ya estaba abierta y se tocó un aviso: llega acá en vez de a `onCreate`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirAviso(intent)
        recibirCompartido(intent)
    }

    /**
     * Lee lo compartido (imágenes o PDF) en un hilo aparte y lo deja en [PapelesCompartidos].
     * **No sube nada**: `App()` lo procesa después de la puerta de «Entrar con huella» (ver
     * `sePuedeLeerLoCompartido`). El permiso de leer cada `content://` dura lo que viva esta
     * actividad, así que se lee ya y no se guarda el URI para después.
     */
    private fun recibirCompartido(intent: Intent?) {
        if (intent == null || !esCompartirConMovi(intent.action, intent.type)) return
        val uris = urisCompartidos(intent)
        val tipo = intent.type
        // Una sola vez: el mismo Intent no se vuelve a leer si la actividad recibe otro onNewIntent.
        intent.action = null
        if (uris.isEmpty()) return
        val contexto = applicationContext
        Thread {
            val archivos = leerLoCompartido(contexto, uris, tipo)
            runOnUiThread { PapelesCompartidos.recibir(archivos) }
        }.start()
    }

    private fun recibirAviso(intent: Intent?) {
        val destino = destinoDeAviso(intent?.getStringExtra(EXTRA_ABRIR)) ?: return
        DestinoDesdeAfuera.pendiente = destino
        // Una sola vez: rotar la pantalla recrea la actividad con el mismo Intent.
        intent?.removeExtra(EXTRA_ABRIR)
    }
}
