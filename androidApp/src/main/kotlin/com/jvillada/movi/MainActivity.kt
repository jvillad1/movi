package com.jvillada.movi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jvillada.movi.shared.db.DatabaseDriverFactory
import com.jvillada.movi.sms.SmsFilterConfigStore
import com.jvillada.movi.sms.SmsFilterRefreshWorker

class MainActivity : ComponentActivity() {
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
        // Y un refresh oportunista en cada apertura — reemplaza el que disparaba la
        // pantalla del sensor cuando era la única UI del APK.
        SmsFilterConfigStore.refreshIfStale(applicationContext)
        setContent {
            App()
        }
    }
}
