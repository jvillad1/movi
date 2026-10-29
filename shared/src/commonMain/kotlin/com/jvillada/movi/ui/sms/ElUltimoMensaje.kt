package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.avisoDeCaptura
import com.jvillada.movi.shared.model.fechaCortaDeSms
import kotlinx.datetime.LocalDate

/**
 * **Lo que dice la primera tarjeta de «Mensajes del banco» / «Captura del banco»**, plegado
 * (vuelta de diseño 8). Antes eran dos párrafos (≈8 renglones): el rótulo, «El último de los 120
 * mensajes de tu banco llegó el… Esto dice lo que llegó, no que la captura siga andando» y, en la web,
 * por qué este aparato no lee mensajes. Ahora es una fila y una línea de ayuda; el resto se abre con
 * «¿Qué es esto?». **Nada se pierde**: lo largo va entero en [masAyuda].
 *
 * @property titulo «Último mensaje · hoy, 11:02 a. m.» o «Nunca ha llegado un mensaje».
 * @property subtitulo cuántos llegaron, o qué significa que no llegue ninguno.
 * @property esAlerta nunca llegó nada: el único estado que amerita el color de aviso.
 * @property ayuda una línea: por qué este aparato no lee mensajes (web, iOS) o qué dice la fecha.
 * @property masAyuda el detalle entero, que se abre al tocar «¿Qué es esto?».
 */
data class ElUltimoMensaje(
    val titulo: String,
    val subtitulo: String,
    val esAlerta: Boolean,
    val ayuda: String,
    val masAyuda: String,
)

/** El título de la fila cuando nunca llegó nada. */
const val NUNCA_HA_LLEGADO_UN_MENSAJE: String = "Nunca ha llegado un mensaje"

/** El comienzo del título de la fila cuando sí llegó algo: «Último mensaje · hoy, 11:02 a. m.». */
const val ULTIMO_MENSAJE: String = "Último mensaje"

/** La línea de ayuda en la web y en iOS. */
const val ESTE_APARATO_NO_LEE_MENSAJES: String = "Este aparato no lee los mensajes del banco."

/** La línea de ayuda en Android: la fecha dice lo que llegó, no que la captura ande. */
const val LA_FECHA_DICE_LO_QUE_LLEGO: String = "Dice lo que llegó, no si la captura sigue andando."

/** Lo que se abre en la web y en iOS: quién lee los mensajes y dónde se revisa lo pendiente. */
internal const val QUIEN_LEE_LOS_MENSAJES: String =
    "Los mensajes los lee un teléfono Android con Movi instalado. Aquí queda el historial de lo que " +
        "llegó; lo pendiente se revisa en «Por revisar», desde Actividad."

/**
 * Arma [ElUltimoMensaje] con lo que dice [avisoDeCaptura] (en `:core`, la misma regla de siempre).
 * [enAndroid] decide la línea de ayuda: en la web y en iOS, que este aparato no lee mensajes. Pura.
 */
fun elUltimoMensaje(captura: CapturaDeSms, hoy: LocalDate, enAndroid: Boolean): ElUltimoMensaje {
    val aviso = avisoDeCaptura(captura)
    val titulo = if (captura.nuncaLlegoNada) {
        NUNCA_HA_LLEGADO_UN_MENSAJE
    } else {
        "$ULTIMO_MENSAJE · " + fechaCortaDeSms(captura.ultimo.orEmpty(), hoy).replaceFirstChar { it.lowercaseChar() }
    }
    val subtitulo = when {
        captura.nuncaLlegoNada -> "Tus movimientos solo entran a mano"
        captura.total == 1 -> "El único que ha llegado de tu banco"
        else -> "${captura.total} mensajes de tu banco"
    }
    return ElUltimoMensaje(
        titulo = titulo,
        subtitulo = subtitulo,
        esAlerta = aviso.esAlerta,
        ayuda = if (enAndroid) LA_FECHA_DICE_LO_QUE_LLEGO else ESTE_APARATO_NO_LEE_MENSAJES,
        masAyuda = if (enAndroid) aviso.detalle else "${aviso.detalle} $QUIEN_LEE_LOS_MENSAJES",
    )
}

/**
 * **La línea de «Avisarme si un banco se calla»** (vuelta de diseño 8): era un párrafo; ahora una
 * línea y, detrás de «¿Qué es esto?», la explicación entera. Pura. `first` = la línea, `second` =
 * lo que se abre.
 */
fun ayudaDelBancoQueSeCalla(dias: Int): Pair<String, String> =
    if (dias <= 0) {
        "Apagado: Movi no te avisa." to
            "Movi no te avisa si un banco deja de mandar mensajes, avisos o correos. Súbelo con «+» " +
            "para que te avise en Inicio."
    } else {
        "Te avisa en Inicio tras ${textoDeDiasDeBancoMudo(dias)} sin nada." to
            "Si un banco que te avisaba siempre lleva ${textoDeDiasDeBancoMudo(dias)} sin mandar nada " +
            "—mensajes, avisos o correos—, Movi te lo dice en Inicio. Con «Nunca», no te avisa."
    }
