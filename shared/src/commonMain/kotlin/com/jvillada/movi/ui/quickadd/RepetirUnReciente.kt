package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.enFormaDeTitulo
import com.jvillada.movi.shared.model.esTuParteDeOtro
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.AltoDeFiltro
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.transactions.descripcionParaMostrar
import com.jvillada.movi.ui.transactions.laDescripcionEsLaCategoria
import com.jvillada.movi.ui.transactions.textosDeLaFila

/**
 * # «Repetir uno reciente» (vuelta 83)
 *
 * La revisión 8 vio Agregar a 390 con el aire partido en dos huecos (~170 px arriba y ~150 abajo). Ahí
 * va algo útil: **los últimos gastos distintos**, en píldoras ([PildoraDeFiltro], la que se toca) —«Uber Rides · $8.400»—. Tocar una
 * **llena** el monto, la categoría, la cuenta y el concepto; el dueño solo toca Guardar. **Nunca
 * guarda sola** (sin reglas de plata).
 *
 * - Solo en «Gasto», sin teclado en pantalla (la web, el escritorio): con el teclado no hay hueco, y
 *   cada dp de la hoja empuja el pie (`HojaAgregarGeometriaTest`). Y solo si cabe: si con la fila el
 *   cuerpo no entra en el hueco, se esconde (revisión de la 83).
 * - Los recientes salen de **los movimientos que la app ya tiene** (la historia de Actividad, la misma
 *   lectura sin repetir), no de un pedido nuevo al server.
 * - Sin recientes, no hay nada: el hueco se cierra.
 */

/** Un gasto reciente para repetir: lo que dice la píldora y lo que llena al tocarla. */
data class GastoReciente(
    /** El comercio o el concepto, o la categoría: lo que dice la fila en Actividad. */
    val rotulo: String,
    val monto: Long,
    val categoria: String,
    val cuentaId: String,
    /** Lo que va en «Nota»; vacío si el concepto era la categoría misma. */
    val concepto: String,
)

/** Cuántos recientes se ofrecen (vuelta 83). */
const val RECIENTES_PARA_REPETIR: Int = 3

/** El título de la fila de recientes. */
const val REPETIR_UNO_RECIENTE: String = "Repetir uno reciente"

/** El tag de la fila de recientes, y el de cada píldora. */
const val TAG_REPETIR_UNO_RECIENTE: String = "agregar:repetir-uno-reciente"
const val TAG_RECIENTE_PARA_REPETIR: String = "agregar:reciente-para-repetir"

/**
 * **Los gastos distintos que más se repiten** de [eventos] (vuelta 83; frecuencia en la 87), como mucho
 * [cuantos]. Distintos por lo que dice la píldora (el comercio, el concepto o la categoría, sin
 * mayúsculas ni tildes): dos Uber seguidos son un solo «Uber Rides», con el monto del último. **Orden
 * (vuelta 87): los que más veces aparecen** entre los últimos [MOVIMIENTOS_PARA_LA_FRECUENCIA] gastos, y a
 * igual número el más reciente.
 *
 * Solo gastos en pesos que se anotarían igual a mano: ni una pata de traspaso, ni una cuota o un ajuste
 * (categorías de Movi), ni tu parte de lo que pagó otro, ni una cuenta que la pestaña no ofrece
 * ([cuentas]). Pura.
 */
fun gastosRecientesParaRepetir(
    eventos: List<FinancialEvent>,
    cuentas: Set<String>,
    cuantos: Int = RECIENTES_PARA_REPETIR,
): List<GastoReciente> {
    val candidatos = eventos.asSequence()
        .filter { e ->
            e.type == TransactionType.EXPENSE &&
                e.currency == "COP" &&
                e.amount > 0L &&
                e.transferId == null &&
                e.accountId in cuentas &&
                !esTuParteDeOtro(e) &&
                e.category.isNotBlank() &&
                !isReservedCategory(e.category) &&
                e.category != CUOTA_CATEGORY &&
                e.category != ADJUSTMENT_CATEGORY
        }
        .sortedByDescending { it.timestamp }
        .take(MOVIMIENTOS_PARA_LA_FRECUENCIA)
        .toList()
    // Vuelta 87 · **los más repetidos, no el último confirmado**: la revisión 9 vio «Carulla $186.450» (un
    // aviso recién confirmado) en vez de Uber, que se repite 30 veces. Se cuentan las veces de cada píldora
    // entre los últimos [MOVIMIENTOS_PARA_LA_FRECUENCIA] gastos; ganan las que más se repiten y, a igual
    // número, la más reciente (con una sola vez cada una, el orden es el de siempre). El monto es el del
    // último de ese comercio.
    val grupos = candidatos.groupBy { normalizarParaBuscar(textosDeLaFila(it, null).titulo.trim()) }
        .filterKeys { it.isNotEmpty() }
    return grupos.values
        .sortedWith(compareByDescending<List<FinancialEvent>> { it.size }.thenByDescending { it.first().timestamp })
        .take(cuantos)
        .map { delComercio ->
            val e = delComercio.first()
            GastoReciente(
                rotulo = textosDeLaFila(e, null).titulo.trim(),
                monto = e.amount,
                categoria = e.category,
                cuentaId = e.accountId,
                concepto = conceptoParaRepetir(e),
            )
        }
}

/** Vuelta 87: de cuántos gastos recientes se cuenta la frecuencia de cada comercio. */
const val MOVIMIENTOS_PARA_LA_FRECUENCIA: Int = 150

/**
 * Lo que va en «Nota» al repetir [e]: su concepto como se lee en la fila; si el concepto es la
 * categoría, el comercio (o nada). Pura.
 */
internal fun conceptoParaRepetir(e: FinancialEvent): String = when {
    !laDescripcionEsLaCategoria(e) -> descripcionParaMostrar(e).trim()
    else -> e.merchant?.trim()?.takeIf { it.isNotEmpty() }?.let(::enFormaDeTitulo).orEmpty()
}

/** Lo que dice la píldora de un reciente: «Uber Rides · $8.400». Pura. */
fun textoDelReciente(reciente: GastoReciente): String = "${reciente.rotulo} · ${formatMoney(reciente.monto, "COP")}"

/**
 * **La fila de recientes** (vuelta 83): el título y las píldoras **en un solo renglón que se desplaza de
 * lado** (revisión de la 83: iban una debajo de otra, ~130 px, y a 360 × 640 quedaban bajo la barra
 * fija). Tocar una llama a [onRepetir], que llena el formulario.
 */
@Composable
internal fun RepetirUnReciente(
    recientes: List<GastoReciente>,
    onRepetir: (GastoReciente) -> Unit,
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (recientes.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_REPETIR_UNO_RECIENTE)) {
        Text(
            REPETIR_UNO_RECIENTE,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla).semantics { heading() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_RENGLON_DE_RECIENTES)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Espacio.xxs),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            recientes.forEach { reciente ->
                // La píldora que se toca del sistema (la de los chips de categoría, en su alto compacto).
                PildoraDeFiltro(
                    texto = textoDelReciente(reciente),
                    seleccionada = false,
                    onClick = { if (habilitado) onRepetir(reciente) },
                    alto = AltoDeFiltro.Compacto,
                    modifier = Modifier.testTag(TAG_RECIENTE_PARA_REPETIR),
                )
            }
        }
    }
}

/** El renglón de las píldoras de los recientes (revisión de la 83: uno solo, que se desplaza). */
const val TAG_RENGLON_DE_RECIENTES: String = "agregar:renglon-de-recientes"
