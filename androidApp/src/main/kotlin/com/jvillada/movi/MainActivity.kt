package com.jvillada.movi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jvillada.movi.sensor.SensorScreen
import com.jvillada.movi.sensor.migratePermissionSignals
import com.jvillada.movi.shared.db.DatabaseDriverFactory
import com.jvillada.movi.sms.SmsFilterRefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        DatabaseDriverFactory.init(applicationContext)
        // Antes de setContent: la pantalla lee esas prefs al componer, y leerlas sin migrar
        // es lo que haría acusar a Android en un teléfono que solo denegó el permiso.
        migratePermissionSignals(applicationContext)
        // Refresco diario de la config del filtro sin depender de que el usuario abra la
        // app ni de que haya habido una captura exitosa (ver SmsFilterRefreshWorker).
        SmsFilterRefreshWorker.schedule(applicationContext)
        setContent {
            SensorScreen()
        }
    }
}
