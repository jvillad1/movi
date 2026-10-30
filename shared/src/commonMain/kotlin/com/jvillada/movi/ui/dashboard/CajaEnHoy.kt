package com.jvillada.movi.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.conCifrasOcultas
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TonoDeFila
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.formatMoneyAbreviadaSinOcultar
import com.jvillada.movi.ui.accounts.ESPACIO_QUE_NO_SE_PARTE
import com.jvillada.movi.ui.accounts.UNION_QUE_NO_SE_PARTE
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.cajaDelPeriodoDe
import kotlinx.datetime.LocalDate

/**
 * # «¿Me alcanza?» en el héroe de Inicio (vuelta 75)
 *
 * La respuesta a «¿me alcanza hasta que me paguen?» vivía en la caja de Pagos, dos pantallas más abajo, y
 * en Inicio dentro de «Disponible», una sección opcional que el dueño puede esconder (y que la revisión 5
 * encontró escondida). Ahora es **un renglón del héroe**: siempre se ve, aunque «Disponible» esté oculto, y
 * un APK viejo simplemente no lo pinta —va dentro de `HERO_BALANCE`, sin tocar la definición SDUI—.
 *
 * - «Te alcanza hasta el 25 nov · tu día más justo, el 5 nov» cuando lo libre del peor día es ≥ 0;
 * - «No te alcanza: el 5 nov quedarías en −$1,3M» si es negativo.
 *
 * **Es la MISMA cuenta que la caja de Pagos**: [cajaDelPeriodoDe] → `cajaHastaQueTePaguen`, sobre los
 * mismos datos del Inicio, con el mismo supuesto del gasto del día a día (la lectura recordada
 * [ClaveDeLectura.GastoDelDiaADia]). Tocarlo abre Pagos en la caja ([Screen.Plan.enLaCaja]).
 *
 * Si la caja no se puede calcular —sin período, sin la lista, sin cuentas, o con la lectura del supuesto
 * caída o en camino— **no se pinta**: un renglón que no afirma nada no le sirve a nadie en el héroe.
 */

/** El tag del renglón «¿Me alcanza?» del héroe. */
const val TAG_ME_ALCANZA: String = "inicio-me-alcanza"

/**
 * Lo que dice el renglón: el título («Te alcanza hasta el 25 nov»), el detalle («tu día más justo, el 5
 * nov») y si va en rojo. Se pinta en dos renglones de la `Fila` —a 360 dp con la letra al 130 % una frase
 * sola no entraba—; [texto] es la frase entera, como se lee. Puro, para las pruebas.
 */
data class RenglonMeAlcanza(val titulo: String, val detalle: String, val enRojo: Boolean) {
    val texto: String get() = if (enRojo) "$titulo: $detalle" else "$titulo · $detalle"
}

/**
 * [RenglonMeAlcanza] de la caja. Se mide en **el peor día** ([CajaProyectada.diaMasBajo]) —la misma cifra
 * titular de la caja de Pagos—: si lo libre ese día es negativo, no alcanza.
 */
fun renglonMeAlcanza(caja: CajaProyectada, hoy: LocalDate): RenglonMeAlcanza {
    val bajo = caja.diaMasBajo
    val cuando = if (bajo.fecha == hoy.toString()) "hoy" else "el ${fechaCortaDelRenglon(bajo.fecha)}"
    if (bajo.libre < 0L) {
        // Revisión de la 75: el menos pegado al «$» y la cifra pegada a «en»: el renglón no se parte ahí.
        return RenglonMeAlcanza("No te alcanza", "$cuando quedarías en$ESPACIO_QUE_NO_SE_PARTE${cifraCortaQueNoSeParte(bajo.libre)}", enRojo = true)
    }
    // Revisión de la 75: «hasta el 25 nov» solo cuando ese día entra el salario; sin él, nada entra ese
    // día, y decirlo con fecha sonaba a que sí.
    val hasta = when {
        caja.hastaElSalario -> "Te alcanza hasta el ${fechaCortaDelRenglon(caja.alCierre.fecha)}"
        caja.hastaElPeriodoSiguiente -> TE_ALCANZA_HASTA_EL_FIN_DEL_PERIODO
        else -> TE_ALCANZA_30_DIAS
    }
    return RenglonMeAlcanza(hasta, "tu día más justo, $cuando", enRojo = false)
}

/**
 * El renglón con lo que el Inicio tiene, o `null` si la caja no se puede calcular (sin período, sin la
 * lista, sin cuentas, o sin el supuesto del gasto del día a día): entonces no se pinta. Es la MISMA
 * [cajaDelPeriodoDe] de la caja de Pagos.
 */
fun renglonMeAlcanzaDe(data: DashboardData, gasto: GastoDelDiaADia?, hoy: LocalDate): RenglonMeAlcanza? {
    val supuesto = gasto ?: return null
    return cajaDelPeriodoDe(data, supuesto, hoy)?.let { renglonMeAlcanza(it, hoy) }
}

/** Sin salario y con el período elegido: la caja mira hasta que arranca el siguiente. */
const val TE_ALCANZA_HASTA_EL_FIN_DEL_PERIODO: String = "Te alcanza hasta el fin del período"

/** Sin salario y con el mes de calendario: la caja mira 30 días. */
const val TE_ALCANZA_30_DIAS: String = "Te alcanza los próximos 30 días"

/**
 * Una cifra corta que no se parte en dos renglones (revisión de la 75): «−$1,2M» con el menos pegado al
 * «$» por [UNION_QUE_NO_SE_PARTE] (no se ve). Sin «Ocultar los saldos»: el renglón lo tapa entero.
 */
internal fun cifraCortaQueNoSeParte(monto: Long): String =
    formatMoneyAbreviadaSinOcultar(monto).replace("\u2212$", "\u2212$UNION_QUE_NO_SE_PARTE$")

/** «5 nov». */
private fun fechaCortaDelRenglon(iso: String): String {
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "${fecha.dayOfMonth} ${MESES_DEL_ANIO[fecha.monthNumber - 1].take(3)}"
}

/**
 * El renglón que el Inicio puede afirmar ahora, o `null` (ver [renglonMeAlcanzaDe]). Pide el supuesto del
 * gasto del día a día —la misma lectura recordada de Pagos— solo si hay con qué proyectar.
 */
@Composable
internal fun rememberRenglonMeAlcanza(data: DashboardData, hoy: LocalDate): RenglonMeAlcanza? {
    // Sin la lista del período o sin Tu plata, la caja de Pagos tampoco proyecta: no hay renglón (y no se
    // pide el supuesto, que no serviría para nada).
    val sePuedeProyectar = data.periodoActual != null && data.upcoming != null && data.ocurrencias != null &&
        (data.accounts != null || data.patrimonio != null)
    if (!sePuedeProyectar) return null
    val gastoLeido = rememberLectura(ClaveDeLectura.GastoDelDiaADia, reintento = 0) {
        Repositories.wallets.getGastoDelDiaADia()
    }
    return renglonMeAlcanzaDe(data, gastoLeido.valor, hoy)
}

/** El renglón suelto, con el hilo de arriba (lo que pinta el héroe, para montarlo solo en una prueba). */
@Composable
internal fun RenglonMeAlcanzaDelHeroe(
    data: DashboardData,
    hoy: LocalDate,
    onNavigate: (Screen) -> Unit,
) {
    val renglon = rememberRenglonMeAlcanza(data, hoy) ?: return
    FilaMeAlcanza(renglon, onNavigate)
}

/** La fila del renglón, con el hilo de arriba. */
@Composable
internal fun FilaMeAlcanza(renglon: RenglonMeAlcanza, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Separador(SangriaDeSeparador.Ninguna)
    Fila(
        titulo = renglon.titulo,
        subtitulo = conCifrasOcultas(renglon.detalle.replaceFirstChar { it.uppercase() }),
        lineasDelSubtitulo = 2,
        tonoDelTitulo = if (renglon.enRojo) TonoDeFila.Alerta else TonoDeFila.Normal,
        modifier = modifier.testTag(TAG_ME_ALCANZA),
        inicio = {
            IconoEnCirculo(
                Icons.AutoMirrored.Rounded.TrendingDown,
                tono = if (renglon.enRojo) TonoDeIcono.Peligro else TonoDeIcono.Neutro,
            )
        },
        final = { Chevron() },
        onClick = { onNavigate(Screen.Plan(SEGMENTO_PAGOS, enLaCaja = true)) },
        onClickLabel = "Ver tu plata día a día en Pagos",
    )
}
