package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.cajaDelPeriodoDe
import kotlinx.datetime.LocalDate

/**
 * # «Tu día más justo» en Hoy
 *
 * Plan ya dibuja la caja proyectada día a día («Tu plata, día a día», Ola 4). Hoy dice lo único que
 * de ella hace falta de un vistazo: **cuál es el día más justo del período y con cuánto quedas ese
 * día** —«El 27 de octubre quedas en $180.000»—, o el primer día que quedarías en rojo. Tocarla lleva
 * a Plan, donde está la línea entera.
 *
 * **Es el mismo dato que Plan, no otra cuenta**: la caja sale de [cajaDelPeriodoDe] —la función que
 * usa la sección de Plan— sobre los mismos datos del Inicio (la lista del período y Tu plata), y el
 * supuesto del gasto del día a día es la MISMA lectura recordada ([ClaveDeLectura.GastoDelDiaADia]).
 *
 * **Nada salta al llegar el dato**: mientras viaja el supuesto se pinta la misma fila con un esqueleto
 * en lugar de la frase y la cifra, del mismo alto (un renglón cada una, sin partirse), y si la lectura
 * falla la fila lo dice en el mismo lugar en vez de desaparecer. Lo mide `CajaEnHoyTest`.
 */

/** El tag de la fila, cargando o cargada: el mismo en las dos para medir que no salte. */
const val TAG_CAJA_EN_HOY: String = "caja-en-hoy"

/** El tag del esqueleto de la cifra — está solo mientras carga. */
const val TAG_ESQUELETO_CAJA_EN_HOY: String = "esqueleto-caja-en-hoy"

/** Lo que dice la fila: el rótulo, cuándo, la cifra y si va en rojo. Puro, para las pruebas. */
data class CajaDeHoy(val rotulo: String, val cuando: String, val saldo: Long, val enRojo: Boolean)

/**
 * [CajaDeHoy] de la proyección. **El rojo manda**: si algún día queda en negativo se dice el primero
 * —eso es lo que hay que atender—, aunque el más bajo sea otro más adelante. Si no, el día más justo.
 */
fun cajaDeHoy(caja: CajaProyectada, hoy: LocalDate): CajaDeHoy {
    caja.primerDiaEnRojo?.let { rojo ->
        val cuando = if (rojo.fecha == hoy.toString()) "Hoy quedarías en" else "El ${fechaEnPalabras(rojo.fecha, hoy)} quedarías en"
        return CajaDeHoy("Quedarías en rojo", cuando, rojo.saldo, enRojo = true)
    }
    val bajo = caja.diaMasBajo
    val cuando = if (bajo.fecha == hoy.toString()) "Hoy tienes" else "El ${fechaEnPalabras(bajo.fecha, hoy)} quedas en"
    return CajaDeHoy("Tu día más justo del período", cuando, bajo.saldo, enRojo = false)
}

/** El rótulo de la fila mientras no hay frase (cargando o sin poder leer): no afirma nada. */
const val ROTULO_CAJA_SIN_DATO: String = "Tu plata, día a día"

/**
 * La fila de Hoy. `null` del todo —ni el esqueleto— cuando no hay nada honesto que proyectar: sin
 * período, sin la lista del período o sin cuentas (lo mismo que hace la sección de Plan).
 */
@Composable
internal fun CajaEnHoy(
    data: DashboardData,
    hoy: LocalDate,
    onNavigate: (Screen) -> Unit,
) {
    // Sin la lista del período o sin Tu plata, la sección de Plan tampoco proyecta: no hay fila (y no
    // se pide el supuesto, que no serviría para nada).
    val sePuedeProyectar = data.periodoActual != null && data.upcoming != null && data.ocurrencias != null &&
        (data.accounts != null || data.patrimonio != null)
    if (!sePuedeProyectar) return
    val gastoLeido = rememberLectura(ClaveDeLectura.GastoDelDiaADia, reintento = 0) {
        Repositories.wallets.getGastoDelDiaADia()
    }
    val gasto = gastoLeido.valor
    val caja = gasto?.let { cajaDelPeriodoDe(data, it, hoy) }
    val noSePudo = gasto == null && gastoLeido.terminada && !gastoLeido.actualizando

    MinCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Movi.espacios.amplio)
            .testTag(TAG_CAJA_EN_HOY),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        onClick = { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) },
        onClickLabel = "Ver tu plata día a día en Plan",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val fila = caja?.let { cajaDeHoy(it, hoy) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fila?.rotulo ?: ROTULO_CAJA_SIN_DATO,
                    style = Movi.textos.apoyo,
                    color = if (fila?.enRojo == true) Movi.colores.sale else Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    fila != null -> Text(
                        text = fila.cuando,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    noSePudo -> Text(
                        text = "No pudimos proyectarla: tócala para verla en Plan",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    else -> LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.cuerpo)
                }
            }
            Spacer(Modifier.size(Movi.espacios.corto))
            when {
                fila != null -> Cifra(
                    formatCOP(fila.saldo),
                    Movi.textos.monto,
                    color = if (fila.enRojo) Movi.colores.sale else Movi.colores.texto,
                )
                !noSePudo -> BloqueEsqueleto(
                    alto = altoDeUnRenglon(Movi.textos.monto),
                    ancho = 84.dp,
                    modifier = Modifier.testTag(TAG_ESQUELETO_CAJA_EN_HOY),
                )
            }
            Spacer(Modifier.size(Movi.espacios.minimo))
            ChevronRight()
        }
    }
}
