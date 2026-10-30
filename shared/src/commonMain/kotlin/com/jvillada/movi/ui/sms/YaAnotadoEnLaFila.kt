package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.LocalDate

/*
 * # Vuelta 74 · el aviso que ya está anotado se cierra con «Es este»
 *
 * La revisión del 30-oct (punto 1): cuando el banco avisaba un gasto que el dueño ya había anotado
 * (Crepes $32.000, la cuota de $980.000), la tarjeta de «Por revisar» solo decía «Revisar», y adentro
 * el botón amarillo seguía siendo «Confirmar», que **crea un movimiento nuevo**: la forma más fácil
 * de contar dos veces un gasto.
 *
 * Ahora, con la MISMA detección de «¿Ya lo anotaste?» (`PropuestaDelAviso.coincidencias`, que es lo
 * que contesta `GET /api/sms/{id}/coincidencias`), la tarjeta lo dice —«Ya anotado: Restaurantes
 * $32.000 · hoy»— y su botón amarillo es **«Es este»**: un toque, sin abrir nada, que vincula el aviso
 * al movimiento que ya estaba (`…/confirm?eventoId=`) y **no crea ninguno**. Con dos o más
 * candidatos no hay un toque: «Puede ser uno de 2 anotados» y «Elegir» abre Revisar aviso, donde
 * están los dos.
 */

/** El botón que cierra el aviso con el movimiento que ya estaba. */
const val ES_ESTE: String = "Es este"

/** Con dos o más candidatos: abre Revisar aviso para elegir. */
const val ELEGIR_EL_ANOTADO: String = "Elegir"

/** Revisar aviso con algo ya anotado: el botón que crea otro movimiento, ahora secundario. */
const val ANOTARLO_COMO_NUEVO: String = "Anotarlo como nuevo"

/** Lo que dice debajo de los botones: que «Anotarlo como nuevo» crea OTRO movimiento. */
const val ANOTARLO_COMO_NUEVO_CREA_OTRO: String = "«Anotarlo como nuevo» crea otro movimiento, aparte del que ya está anotado."

const val TAG_ES_ESTE_EN_RECONCILIAR: String = "es-este-en-reconciliar"
const val TAG_ANOTARLO_COMO_NUEVO: String = "anotarlo-como-nuevo"
const val TAG_ANOTARLO_COMO_NUEVO_CREA_OTRO: String = "anotarlo-como-nuevo-crea-otro"
const val TAG_ES_ESTE_EN_LA_FILA: String = "es-este-en-la-fila"
const val TAG_ELEGIR_EL_ANOTADO_EN_LA_FILA: String = "elegir-el-anotado-en-la-fila"
const val TAG_LINEA_DEL_YA_ANOTADO: String = "linea-del-ya-anotado"

/**
 * Lo que la bandeja le pasa a la tarjeta de un aviso que «¿Ya lo anotaste?» encontró anotado.
 *
 * @property coincidencias los movimientos ya anotados que parecen este (al menos uno).
 * @property onEsEste «Es este» con el único candidato.
 * @property onElegir «Elegir», con dos o más: abre Revisar aviso.
 */
data class YaAnotadoEnLaFila(
    val coincidencias: List<FinancialEvent>,
    val onEsEste: (FinancialEvent) -> Unit,
    val onElegir: () -> Unit,
)

/** «Restaurantes $32.000 · hoy»: el movimiento que ya estaba, como lo dice la fila. */
fun loQueYaEstaba(evento: FinancialEvent, hoy: LocalDate): String {
    val titulo = evento.description.substringBefore(" · ").trim().takeIf { it.isNotEmpty() }?.let(::nombreParaMostrar)
        ?: evento.category
    val dia = etiquetaDeFecha(fechaDeEpoch(evento.timestamp), hoy).let { if (it == "Hoy" || it == "Ayer") it.lowercase() else it }
    return "$titulo ${formatMoney(evento.amount, evento.currency)} · $dia"
}

/**
 * **La línea de la tarjeta**: «Ya anotado: Restaurantes $32.000 · hoy» con un candidato; «Puede ser
 * uno de 2 anotados» con varios. Vacía no se llama.
 */
fun lineaDelYaAnotado(coincidencias: List<FinancialEvent>, hoy: LocalDate): String =
    coincidencias.singleOrNull()?.let { "Ya anotado: ${loQueYaEstaba(it, hoy)}" }
        ?: "Puede ser uno de ${coincidencias.size} anotados"

@Composable
internal fun LineaDelYaAnotado(linea: String) {
    Text(
        linea,
        style = Movi.textos.apoyoMedio,
        color = Movi.colores.texto,
        modifier = Modifier.testTag(TAG_LINEA_DEL_YA_ANOTADO),
    )
}

/**
 * Las acciones de la tarjeta: «Es este» (amarillo) · «Revisar» · «Ignorar» con un candidato; «Elegir»
 * (amarillo) · «Ignorar» con varios.
 */
@Composable
internal fun RowScope.AccionesDelYaAnotado(
    ya: YaAnotadoEnLaFila,
    trabajando: Boolean,
    onRevisar: () -> Unit,
    onIgnorar: (() -> Unit)?,
) {
    val unico = ya.coincidencias.singleOrNull()
    if (unico != null) {
        PastillaDelPago(
            ES_ESTE, principal = true, habilitada = !trabajando, onClick = { ya.onEsEste(unico) },
            modifier = Modifier.testTag(TAG_ES_ESTE_EN_LA_FILA),
        )
        PastillaDelPago("Revisar", habilitada = !trabajando, onClick = onRevisar)
    } else {
        PastillaDelPago(
            ELEGIR_EL_ANOTADO, principal = true, habilitada = !trabajando, onClick = ya.onElegir,
            modifier = Modifier.testTag(TAG_ELEGIR_EL_ANOTADO_EN_LA_FILA),
        )
    }
    onIgnorar?.let { PastillaDelPago(IGNORAR_EN_LA_FILA, habilitada = !trabajando, onClick = it) }
}
