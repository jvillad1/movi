package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.tiposDeParteDesde
import com.jvillada.movi.shared.model.seDivideLoQueSalioDe
import com.jvillada.movi.ui.sms.TIPOS_DE_PARTE
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParteElegida
import com.jvillada.movi.shared.model.PartesDelAviso
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.aParteDelReparto
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.sePuedenConfirmar
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.sms.CATEGORIAS_EN_LAS_PARTES
import com.jvillada.movi.ui.sms.EditorDePartes
import com.jvillada.movi.ui.sms.ParteEnPantalla
import com.jvillada.movi.ui.sms.REPARTIR_EN_PARTES
import com.jvillada.movi.ui.sms.TerceroDelAviso
import com.jvillada.movi.ui.sms.botonDeConfirmarLasPartes
import com.jvillada.movi.ui.sms.categoriaDeGastoOAnada
import com.jvillada.movi.ui.sms.categoriasParaElegirEnElSms
import com.jvillada.movi.ui.sms.enPantalla
import com.jvillada.movi.ui.sms.esCategoriaDeGastoSuelta
import com.jvillada.movi.ui.sms.razonDeLasPartes
import kotlinx.coroutines.launch

/*
 * # Repartir en partes un movimiento ya anotado (vuelta 27, punto 3)
 *
 * El giro al papá que se confirmó antes de esta vuelta como un solo gasto: en «Más», «Repartir en
 * partes» abre el mismo editor de Reconciliar (`RepartirEnPartes.kt`) con el monto del movimiento. Al
 * confirmar, el server lo **anula** —el mecanismo de siempre, `void_events`— y escribe las partes con
 * la misma función que un aviso, en una transacción (`POST /api/events/{id}/repartir`). El aviso que lo
 * había anotado queda enlazado a la primera parte.
 */

/** El tag del bloque «Repartir en partes» del detalle de un movimiento. */
const val TAG_REPARTIR_EL_MOVIMIENTO: String = "movimiento:repartir-en-partes"

/**
 * **¿Este movimiento se puede repartir en partes?** Lo que salió de una cuenta de dinero: un gasto, o
 * la pata que sale de un traspaso o de una cuota (el server anula las dos). No el saldo inicial, ni lo
 * que está por confirmar, ni un gasto en un espacio, ni una pata que se quedó sin su hermana.
 *
 * **Vuelta 71 · y una compra con tarjeta de crédito**: un gasto suelto de la tarjeta (sin `transferId`
 * —la pata de un avance no— y en una categoría que se elige, no un ajuste) se divide en gastos con su
 * categoría ([tiposDeParteDesde]). Las deudas se excluían porque desde una deuda una cuota o un
 * traspaso no tienen sentido; un gasto con su categoría sí. Un crédito (LOAN) sigue afuera: lo que
 * «sale» de él es un desembolso, no una compra.
 */
fun sePuedeRepartirEnPartes(evento: FinancialEvent, cuentas: List<Account>): Boolean {
    if (evento.type != TransactionType.EXPENSE || evento.amount <= 0L) return false
    if (isOpeningBalance(evento) || evento.espacioId != null || isOrphanedTransferLeg(evento)) return false
    if (evento.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) return false
    val cuenta = cuentas.firstOrNull { it.id == evento.accountId } ?: return false
    if (cuenta.esBien) return false
    // La misma regla que el server (`repartirUnMovimiento`).
    return seDivideLoQueSalioDe(cuenta.type, evento.transferId, evento.category)
}

/** La sección «Repartir en partes» de «Más»: un botón que abre el editor, y el editor con su confirmar. */
@Composable
internal fun SeccionDeRepartirEnPartes(
    evento: FinancialEvent,
    cuentas: List<Account>,
    onRepartido: (FinancialEvent) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var abierto by remember(evento.id) { mutableStateOf(false) }
    var partes by remember(evento.id) { mutableStateOf(emptyList<ParteEnPantalla>()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val origen = cuentas.firstOrNull { it.id == evento.accountId }
    val categoria = categoriaDeGastoOAnada(evento.category)
    // A quién fue, si el texto del banco nombra una cuenta: lo que sobra puede ir a esa persona.
    val tercero = evento.merchant?.trim()?.takeIf { it.isNotEmpty() && identificadorDelDestinoEn(evento) != null }
        ?.let { TerceroDelAviso(it, it) }

    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_REPARTIR_EL_MOVIMIENTO)) {
        if (!abierto) {
            Boton(
                texto = REPARTIR_EN_PARTES,
                onClick = {
                    partes = enPantalla(listOf(ParteElegida(QueFueLaParte.GASTO, categoria = categoria)))
                    abierto = true
                },
                variante = VarianteDeBoton.Secundario,
                anchoCompleto = true,
            )
            Spacer(Modifier.height(Espacio.xxs))
            Text(
                "Si este movimiento pagó varias cosas, divídelo: queda una parte por cada cosa, y se puede deshacer.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            return@Column
        }
        EditorDePartes(
            total = evento.amount,
            moneda = evento.currency,
            origen = origen,
            partes = partes,
            onPartes = { partes = it },
            cuentas = cuentas,
            categorias = categoriasParaElegirEnElSms(
                propuesta = categoria,
                tipo = TransactionType.EXPENSE,
                usadas = UsedCategoriesCache.used,
                prefs = UsedCategoriesCache.prefs,
                usos = UsedCategoriesCache.usosRecientes,
                cuantas = CATEGORIAS_EN_LAS_PARTES,
            ).filter { esCategoriaDeGastoSuelta(it) },
            pagos = emptyList(),
            tercero = tercero,
            categoriaDelAviso = categoria,
            habilitado = !guardando,
            onVolver = { abierto = false },
            // Vuelta 67: lo anotado a mano no tiene aviso con qué cuadrar: «Suma $100.000».
            conAviso = evento.source != EventSource.MANUAL,
            // Vuelta 71: desde una tarjeta, solo gastos con su categoría.
            tipos = origen?.type?.let(::tiposDeParteDesde) ?: TIPOS_DE_PARTE,
        )
        Spacer(Modifier.height(Espacio.s))
        val elegidas = partes.map { it.parte }
        // El botón apagado dice por qué, debajo, como en toda hoja.
        PieDeHoja(
            texto = botonDeConfirmarLasPartes(partes.size),
            onClick = {
                val desde = origen ?: return@PieDeHoja
                if (guardando || !sePuedenConfirmar(evento.amount, elegidas)) return@PieDeHoja
                guardando = true
                error = null
                coroutine.launch {
                    intentar {
                        val enPartes = elegidas.map {
                            // Cada parte hereda el texto del original: su descripción y su comercio.
                            aParteDelReparto(
                                it, desde, evento.timestamp, evento.rawPayload.orEmpty(), evento.merchant,
                                evento.source, ::newId,
                                // Vuelta 71 (revisión): si la descripción es solo la categoría («Restaurantes»),
                                // no es un texto que heredar: cada parte lleva la suya.
                                descripcionHeredada = evento.description.takeIf { it.trim() != evento.category.trim() },
                            )
                        }
                        Repositories.wallets.repartirElMovimiento(evento.id, PartesDelAviso(enPartes))
                    }.onSuccess {
                        guardando = false
                        onRepartido(evento)
                    }.onFailure {
                        guardando = false
                        error = it.toUserMessage()
                    }
                }
            },
            habilitado = !guardando && origen != null && sePuedenConfirmar(evento.amount, elegidas),
            razon = razonDeLasPartes(evento.amount, elegidas, evento.currency),
            cargando = guardando,
            error = error,
        )
    }
}
