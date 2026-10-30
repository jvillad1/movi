package com.jvillada.movi.ui.mas

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.avisos.PreferenciasDeAvisos
import com.jvillada.movi.avisos.avisosPidenPermiso
import com.jvillada.movi.avisos.puedeAvisar
import com.jvillada.movi.avisos.tienePermisoDeAvisos
import com.jvillada.movi.sensor.OnResume
import com.jvillada.movi.sensor.canShowRationaleFor
import com.jvillada.movi.sensor.findComponentActivity
import com.jvillada.movi.sensor.openAppSettings
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.profile.Interruptor

/** El título de la fila del permiso: si Android deja que Movi avise en este teléfono. */
internal const val AVISOS_EN_ESTE_TELEFONO = "En este teléfono"

/** Vuelta 68: el interruptor del resumen de la mañana. */
internal const val TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA = "Resumen de la mañana, a las 8:00"

/** Vuelta 68: el interruptor del resumen semanal de los grupos. */
internal const val TEXTO_INTERRUPTOR_RESUMEN_SEMANAL = "Resumen semanal de tus grupos, el domingo"

/** El tag de cada interruptor de los avisos del teléfono en Tú, para las pruebas. */
internal const val TAG_AVISO_DEL_TELEFONO = "tu:aviso-del-telefono"

/** Un interruptor: lo que dice, cómo se lee y cómo se guarda (en `PreferenciasDeAvisos`, como siempre). */
private class InterruptorDeAviso(
    val texto: String,
    val leer: (Context) -> Boolean,
    val guardar: (Context, Boolean) -> Unit,
)

/**
 * Los interruptores, en el orden de siempre. Cada uno se guarda donde se guardaba en Captura del banco
 * (vuelta 73: se mudó la pantalla, no la preferencia).
 */
private val INTERRUPTORES: List<InterruptorDeAviso> = listOf(
    InterruptorDeAviso("Avisarme cuando llegue un movimiento", PreferenciasDeAvisos::avisarMovimientos, PreferenciasDeAvisos::ponerAvisarMovimientos),
    InterruptorDeAviso("Avisarme antes de que venza un pago", PreferenciasDeAvisos::avisarVencimientos, PreferenciasDeAvisos::ponerAvisarVencimientos),
    // Banco mudo: cuántos días cuentan como silencio sigue en Captura del banco (es un ajuste de la
    // captura, vale para la web y el teléfono); acá, solo si este teléfono suena.
    InterruptorDeAviso("Avisarme si mi banco deja de avisar", PreferenciasDeAvisos::avisarBancoMudo, PreferenciasDeAvisos::ponerAvisarBancoMudo),
    InterruptorDeAviso("Avisarme el día de un débito automático", PreferenciasDeAvisos::avisarDebitos, PreferenciasDeAvisos::ponerAvisarDebitos),
    // El correo con el mismo resumen se apaga arriba, en «Resumen del período por correo» (es de la cuenta).
    InterruptorDeAviso("Avisarme cuando cierre mi período", PreferenciasDeAvisos::avisarCierre, PreferenciasDeAvisos::ponerAvisarCierre),
    InterruptorDeAviso(TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA, PreferenciasDeAvisos::avisarResumenDeLaManana, PreferenciasDeAvisos::ponerAvisarResumenDeLaManana),
    InterruptorDeAviso(TEXTO_INTERRUPTOR_RESUMEN_SEMANAL, PreferenciasDeAvisos::avisarResumenSemanal, PreferenciasDeAvisos::ponerAvisarResumenSemanal),
)

/**
 * Android: el permiso de notificaciones (Android 13+) y los interruptores de lo que suena en este
 * teléfono, como filas de Tú → Notificaciones. Lo que era la tarjeta «Avisos de Movi» de Captura del banco.
 *
 * Sin el permiso los interruptores se ven pero no se mueven: prenderlos no haría sonar nada, y un
 * interruptor encendido que no hace nada es la misma mentira que «AUTO-LECTURA ACTIVA» con la captura
 * muda. Si Android ya no muestra el diálogo (se negó dos veces), el botón lleva a los ajustes de
 * notificaciones de la app.
 */
@Composable
actual fun AvisosDeEsteTelefono() {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var puede by remember { mutableStateOf(puedeAvisar(context)) }
    var conPermiso by remember { mutableStateOf(tienePermisoDeAvisos(context)) }
    var aAjustes by remember { mutableStateOf(false) }
    var valores by remember { mutableStateOf(INTERRUPTORES.map { it.leer(context) }) }

    fun refrescar() {
        puede = puedeAvisar(context)
        conPermiso = tienePermisoDeAvisos(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        refrescar()
        // Negado y sin poder volver a preguntar: el próximo toque lleva a los ajustes del sistema.
        if (!concedido) aAjustes = !canShowRationaleFor(activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    // Se concede y se apaga FUERA de la app (ajustes del sistema): se relee al volver.
    OnResume(activity) { refrescar() }

    Separador()
    Fila(
        titulo = AVISOS_EN_ESTE_TELEFONO,
        subtitulo = if (puede) "Activos: lo de abajo suena en este teléfono" else "Apagados: Android no deja que Movi avise",
        lineasDelSubtitulo = 2,
        inicio = { IconoEnCirculo(Icons.Rounded.PhoneAndroid, tono = TonoDeIcono.Neutro) },
        final = if (puede) null else ({
            Boton(
                if (conPermiso || aAjustes) "Abrir ajustes" else "Permitir",
                onClick = {
                    if (!conPermiso && !aAjustes && avisosPidenPermiso()) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        abrirAjustesDeAvisos(context)
                    }
                },
                variante = VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S,
            )
        }),
    )
    INTERRUPTORES.forEachIndexed { i, interruptor ->
        val encendido = valores[i]
        val cambiar: (Boolean) -> Unit = { nuevo ->
            interruptor.guardar(context, nuevo)
            valores = valores.toMutableList().also { it[i] = nuevo }
        }
        Separador()
        Fila(
            titulo = interruptor.texto,
            final = { Interruptor(encendido && puede, onCambio = cambiar, habilitado = puede) },
            onClick = if (puede) ({ cambiar(!encendido) }) else null,
            modifier = Modifier.testTag(TAG_AVISO_DEL_TELEFONO),
        )
    }
}

/** Los ajustes de notificaciones de Movi en el sistema (Android 8+), o la ficha de la app si no. */
private fun abrirAjustesDeAvisos(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) openAppSettings(context)
}
