package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.ui.components.ds.Seccion

/*
 * # «Tu mes típico» (vuelta 86)
 *
 * La revisión 9 contó en Pagos cinco cifras de «lo que queda» que no se relacionaban: «Te quedan» (con su
 * «por día» y su «meta de hoy»), «Tu plata, día a día», «Flujo libre», «Libre sin las tarjetas» y «Falta por
 * pagar». Pagos se quedó con dos preguntas —¿cuánto me queda? y ¿cuánto me falta pagar?— y lo que contesta
 * **cuánto queda en un mes cualquiera** (los ingresos y gastos que se repiten, los mínimos de las tarjetas)
 * se mudó a «Tus períodos», donde se mira cómo fue cada ciclo: es la referencia contra la que se leen.
 *
 * **Ninguna cuenta cambia**: es [ResumenFlujoLibreCard] tal cual, con [resumenRecurrentes] sobre las mismas
 * dos lecturas del tablero (las suscripciones y los vencimientos), y la misma frase de «¿Qué cuenta?».
 */

/** El título del bloque en «Tus períodos» y de la fila que lleva allá desde Pagos. */
const val TITULO_TU_MES_TIPICO: String = "Tu mes típico"

/** El subtítulo de la fila de Pagos que lleva a «Tu mes típico». */
const val SUBTITULO_DE_LA_FILA_DEL_MES_TIPICO: String = "Lo que entra y sale cada mes, en Tus períodos"

/** El bloque «Tu mes típico» (en «Tus períodos») y la fila que lleva a él (en Pagos). */
const val TAG_TU_MES_TIPICO: String = "periodos:tu-mes-tipico"
const val TAG_FILA_DEL_MES_TIPICO: String = "pagos:fila-del-mes-tipico"

/**
 * **¿Hay mes típico que mostrar?** Sin ningún pago fijo ni suscripción (la lectura contestó vacía), no: la
 * tarjeta solo diría «$0». Mientras se lee, sí (con «—»: el mismo alto). Pura sobre el estado.
 */
internal fun hayMesTipico(estado: EstadoDelTableroDeRecurrentes): Boolean =
    !(estado.vencimientosOk && estado.subsParaRecurrentesOk && estado.upcomingRecurrentes.isEmpty() && estado.activas.isEmpty())

/**
 * **«Tu mes típico»**: el título y la tarjeta del flujo libre. [periodoDeHoy] y [ajustes] sirven para la
 * frase de «¿Qué cuenta?» que separa «al mes» de «este período» (el «de $X» de «Falta por pagar»); sin
 * ellos, esa frase no va.
 */
@Composable
fun TuMesTipico(
    estado: EstadoDelTableroDeRecurrentes,
    periodoDeHoy: PeriodoFinanciero?,
    ajustes: PeriodSettings?,
    modifier: Modifier = Modifier,
    /**
     * Alguna de sus lecturas falló. En «Tus períodos» el bloque entonces no se pinta: la pantalla ya tiene
     * su propio aviso y su «Reintentar» (vuelta 68 · un solo error por pantalla), y este bloque es una
     * referencia, no lo que se vino a mirar. Nada se afirma: ni un «$0» ni un vacío.
     */
    fallo: Boolean = false,
) {
    if (fallo || !hayMesTipico(estado)) return
    // Las tarjetas, de la misma lectura recordada que la lista de Pagos (no sale a la red si ya está).
    val tarjetas = rememberLectura(ClaveDeLectura.Tarjetas, reintento = 0) { Repositories.wallets.getCards() }
    val totalDelPeriodo = remember(
        estado.upcomingRecurrentes, estado.ocurrencias, estado.vencimientosOk, estado.ocurrenciasOk,
        periodoDeHoy, ajustes, tarjetas.valor,
    ) {
        if (periodoDeHoy == null || ajustes == null) null
        else estado.totalDeFacturasDelPeriodo(periodoDeHoy, ajustes, tarjetas.valor.orEmpty().map { it.account.id }.toSet())
    }
    Column(modifier = modifier.testTag(TAG_TU_MES_TIPICO)) {
        Seccion(titulo = TITULO_TU_MES_TIPICO)
        ResumenFlujoLibreCard(
            // **Sin los vencimientos no hay cifra.** Las cuotas de los créditos llegan solo por ahí y son la
            // mitad del mes del dueño: un total sin ellas saldría plausible y mucho más alto que la verdad.
            // Un guion se entiende; un número optimista, no.
            cifras = estado.resumen?.takeIf { estado.vencimientosOk },
            totalDelPeriodo = totalDelPeriodo,
        )
    }
}

/**
 * El estado del tablero para «Tu mes típico» fuera de Pagos: solo las lecturas que el flujo libre necesita
 * (las suscripciones y los vencimientos), sin los créditos, los movimientos ni el permiso de avisos.
 * [recarga] es la del «Reintentar» de quien lo monta.
 */
@Composable
fun rememberEstadoDelMesTipico(recarga: Int): EstadoDelMesTipico {
    val error = remember { mutableStateOf<String?>(null) }
    // Reintentar (una [recarga] nueva) vuelve a intentar: lo que falló antes no esconde lo de ahora.
    androidx.compose.runtime.LaunchedEffect(recarga) { error.value = null }
    val estado = rememberEstadoDelTableroDeRecurrentes(activo = true, recarga = recarga, error = error, soloElMesTipico = true)
    return remember(estado, error) { EstadoDelMesTipico(estado, error) }
}

/** Ver [rememberEstadoDelMesTipico]. [fallo]: alguna lectura no contestó (se limpia al reintentar). */
@androidx.compose.runtime.Stable
class EstadoDelMesTipico internal constructor(
    val estado: EstadoDelTableroDeRecurrentes,
    private val error: androidx.compose.runtime.State<String?>,
) {
    val fallo: Boolean get() = error.value != null || estado.recurrentesNoSePudieronLeer
}
