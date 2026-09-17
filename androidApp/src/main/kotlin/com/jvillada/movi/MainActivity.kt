package com.jvillada.movi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
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
        setContent {
            App()
        }
    }
}
