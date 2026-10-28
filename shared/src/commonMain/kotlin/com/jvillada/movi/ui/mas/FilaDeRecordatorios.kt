package com.jvillada.movi.ui.mas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.profile.CARGANDO
import com.jvillada.movi.ui.profile.NO_PUDIMOS_LEERLO
import com.jvillada.movi.ui.recurrentes.EstadoDeLosRecordatorios
import com.jvillada.movi.ui.recurrentes.RECORDATORIOS_APAGADOS
import com.jvillada.movi.ui.recurrentes.estadoDeLosRecordatorios
import com.jvillada.movi.ui.recurrentes.reminderLeadHint

/** El título de la fila de recordatorios de Ajustes → Avisos. */
const val TITULO_DE_LOS_RECORDATORIOS: String = "Avisarme antes de un vencimiento"

/** El tag de la fila, para sus pruebas. */
const val TAG_FILA_DE_RECORDATORIOS: String = "ajustes-fila-de-recordatorios"

/**
 * **Lo que dice la fila de recordatorios** (vuelta de diseño 22): sin canal, «Apagado: no te va a
 * llegar»; con canal (o sin saber todavía), cuántos días antes avisa; sin el perfil, que carga o que no
 * se pudo leer. `null` en [diasDeAviso] es que el perfil todavía no llegó.
 */
fun subtituloDeLosRecordatorios(estado: EstadoDeLosRecordatorios, diasDeAviso: Int?, falloElPerfil: Boolean): String = when {
    estado == EstadoDeLosRecordatorios.APAGADOS -> RECORDATORIOS_APAGADOS
    diasDeAviso != null -> reminderLeadHint(diasDeAviso)
    falloElPerfil -> NO_PUDIMOS_LEERLO
    else -> CARGANDO
}

/**
 * **«Avisarme antes de un vencimiento»**, que dice si de verdad va a avisar.
 *
 * La revisión del 27-oct: la fila decía «Te avisamos 3 días antes» al lado de «Notificaciones push ·
 * Activar», como si los avisos funcionaran, y el «Tus recordatorios no te van a llegar» salía al final de
 * Plan, donde se descubría tarde. Ahora, mientras no haya ningún canal ([estadoDeLosRecordatorios], la
 * regla de `ReminderWarning`), la fila lo dice en el tono de aviso.
 *
 * **Vuelta 31: sin botón propio.** «Apagado» solo pasa donde se pueden pedir las notificaciones, así
 * que la fila de las notificaciones va justo arriba con su botón: eran dos «Activar» seguidos para lo
 * mismo. Queda uno, «Activar avisos», en [FilasDeAvisos]; esta fila sigue abriendo cuántos días antes.
 */
@Composable
internal fun FilaDeRecordatorios(
    pushStatus: String,
    canales: ReminderChannels?,
    diasDeAviso: Int?,
    falloElPerfil: Boolean,
    onElegirDias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado = estadoDeLosRecordatorios(pushStatus, canales)
    val apagados = estado == EstadoDeLosRecordatorios.APAGADOS
    Fila(
        titulo = TITULO_DE_LOS_RECORDATORIOS,
        subtitulo = subtituloDeLosRecordatorios(estado, diasDeAviso, falloElPerfil),
        tonoDelSubtitulo = if (apagados) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
        inicio = { IconoEnCirculo(Icons.Rounded.Alarm, tono = if (apagados) TonoDeIcono.Aviso else TonoDeIcono.Neutro) },
        final = if (diasDeAviso != null) ({ Chevron() }) else null,
        // Cuántos días antes avisar. Sin el perfil no se afirma: sería el default.
        onClick = if (diasDeAviso == null) null else onElegirDias,
        modifier = modifier.testTag(TAG_FILA_DE_RECORDATORIOS),
    )
}

/** El único botón que prende los avisos en Ajustes (vuelta 31). */
const val ACTIVAR_AVISOS: String = "Activar avisos"

/**
 * **Las filas de los avisos**: las notificaciones (donde se pueden pedir) con **un solo** botón,
 * «Activar avisos» o «Desactivar», y debajo cuántos días antes avisar ([FilaDeRecordatorios]).
 * [pushSoportado] y [pushStatus] vienen de `PushOptIn`; se pasan para poder probar la web desde acá.
 */
@Composable
internal fun FilasDeAvisos(
    pushSoportado: Boolean,
    pushStatus: String,
    canales: ReminderChannels?,
    diasDeAviso: Int?,
    falloElPerfil: Boolean,
    onActivar: () -> Unit,
    onDesactivar: () -> Unit,
    onElegirDias: () -> Unit,
) {
    if (pushSoportado) {
        val activadas = pushStatus == "enabled"
        Fila(
            // Título corto y subtítulo en dos líneas: el botón ocupa la mitad de la fila, y con
            // «Notificaciones push» el título partía en dos y la frase se cortaba.
            titulo = "Notificaciones",
            subtitulo = when (pushStatus) {
                "enabled" -> "Activadas en este dispositivo"
                "denied" -> "Bloqueadas por el navegador"
                else -> "Antes de cada pago"
            },
            lineasDelSubtitulo = 2,
            inicio = { IconoEnCirculo(Icons.Rounded.Notifications, tono = TonoDeIcono.Neutro) },
            final = {
                Boton(
                    if (activadas) "Desactivar" else ACTIVAR_AVISOS,
                    onClick = if (activadas) onDesactivar else onActivar,
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    habilitado = pushStatus != "denied",
                )
            },
        )
        Separador()
    }
    FilaDeRecordatorios(
        pushStatus = pushStatus,
        canales = canales,
        diasDeAviso = diasDeAviso,
        falloElPerfil = falloElPerfil,
        onElegirDias = onElegirDias,
    )
}
