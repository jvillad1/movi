package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi

/**
 * # Agregar en **modo pago** de una factura (vuelta 43)
 *
 * El día del dueño: «Pago vencido · hace 23 días» → «Anotar pago» abría un Agregar genérico, y al
 * guardar quedaba un gasto suelto que no saldaba la factura: la fila seguía pendiente y «Te quedan»
 * bajaba dos veces (el fijo pendiente y el gasto). La hoja nunca supo de qué pago se trataba: se
 * confiaba en que el emparejamiento automático reconociera el movimiento después, y un pago tarde
 * (fuera de la ventana de 10 días del vencimiento) no lo reconoce nadie.
 *
 * Ahora «Anotar pago» —desde Pagos, «Próximo pago» / «Pago vencido» en Inicio y «Movi propone hoy»,
 * todos por `hojaParaAnotar`— abre la hoja con un [PagoDeLaFactura]: el encabezado dice de qué es
 * («Pago de Arriendo · octubre»), el monto, la categoría y el título son los de la regla, la fecha es
 * hoy, y **al guardar el movimiento sale junto con la ocurrencia que paga**
 * (`WalletRepository.anotarElPagoDeLaOcurrencia`, una sola transacción del server). La fila pasa a
 * «Pagado» con su movimiento, y «Te quedan» no cambia (o cambia por la diferencia, si se pagó otro
 * monto).
 *
 * La cuota de un crédito y el pago de una tarjeta NO entran por acá: abren la pestaña «Cuota», que
 * arma las dos patas y baja la deuda (ver `hojaParaAnotar`).
 */
data class PagoDeLaFactura(
    val ruleId: String,
    /**
     * El `"YYYY-MM"` con que se sella (`PagoDelPeriodo.periodoDelSello`). `null` en una factura que
     * todavía no vence: el server sabe cuál está en juego.
     */
    val periodo: String?,
    /** El nombre de la regla: «Arriendo». */
    val nombre: String,
    /** El mes del período del dueño, en palabras: «octubre». Vacío si no se entiende. */
    val mes: String,
    /** La cuenta de la regla, si la tiene: es la que la hoja propone primero. */
    val cuentaDeLaRegla: String?,
    /** Un sueldo no se paga: llega. Cambia el encabezado y el tipo del movimiento. */
    val esIngreso: Boolean = false,
)

/**
 * El encabezado de la hoja en modo pago: «Pago de Arriendo · octubre», «Ingreso de Salario ·
 * octubre». Sin mes, sin el « · ».
 */
fun encabezadoDelPago(pago: PagoDeLaFactura): String {
    val que = if (pago.esIngreso) "Ingreso de ${pago.nombre}" else "Pago de ${pago.nombre}"
    return if (pago.mes.isBlank()) que else "$que · ${pago.mes}"
}

/** El pedido que la hoja le manda al server: el movimiento que armó y la ocurrencia que paga. */
fun pedidoDelPago(pago: PagoDeLaFactura, movimiento: FinancialEvent): AnotarElPagoDeLaOcurrencia =
    AnotarElPagoDeLaOcurrencia(periodo = pago.periodo, movimiento = movimiento)

/** El `testTag` del encabezado de la hoja en modo pago. */
const val TAG_ENCABEZADO_DEL_PAGO = "quickadd:encabezado-del-pago"

/**
 * El encabezado de la hoja en modo pago, donde van las pestañas en el Agregar de siempre: «Pago de
 * Arriendo · octubre». Título de la hoja, en la letra de los títulos y centrado sobre el monto.
 */
@Composable
internal fun EncabezadoDelPago(texto: String) {
    Text(
        text = texto,
        style = Movi.textos.tituloMedio,
        color = Movi.colores.texto,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Espacio.xs)
            .semantics { heading() }
            .testTag(TAG_ENCABEZADO_DEL_PAGO),
    )
}
