package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeFila
import com.jvillada.movi.ui.components.ds.TonoDeIcono
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

    // Vuelta de diseño 19: la `Fila` del sistema en una `Superficie` de lista. Arriba lo que es
    // («Tu día más justo del período», en `sale` si quedaría en rojo), debajo cuándo y al final la
    // `Cifra`. Cargando, el subtítulo vacío reserva su renglón y la cifra es un bloque del mismo alto:
    // la fila mide lo mismo con dato y sin él.
    val fila = caja?.let { cajaDeHoy(it, hoy) }
    Superficie(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .testTag(TAG_CAJA_EN_HOY),
        relleno = RellenoDeSuperficie.Lista,
    ) {
        Fila(
            titulo = fila?.rotulo ?: ROTULO_CAJA_SIN_DATO,
            subtitulo = when {
                fila != null -> fila.cuando
                noSePudo -> "No pudimos proyectarla: tócala para verla en Pagos"
                else -> ""
            },
            tonoDelTitulo = if (fila?.enRojo == true) TonoDeFila.Alerta else TonoDeFila.Normal,
            inicio = {
                IconoEnCirculo(
                    Icons.AutoMirrored.Rounded.TrendingDown,
                    tono = if (fila?.enRojo == true) TonoDeIcono.Peligro else TonoDeIcono.Neutro,
                )
            },
            final = {
                when {
                    fila != null -> Cifra(fila.saldo, tono = if (fila.enRojo) TonoDeCifra.Alerta else TonoDeCifra.Neutro)
                    noSePudo -> Chevron()
                    else -> BloqueEsqueleto(
                        alto = altoDeUnRenglon(Movi.textos.monto),
                        ancho = Medida.avatar * 2,
                        modifier = Modifier.testTag(TAG_ESQUELETO_CAJA_EN_HOY),
                    )
                }
            },
            onClick = { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) },
            onClickLabel = "Ver tu plata día a día en Pagos",
        )
    }
}
