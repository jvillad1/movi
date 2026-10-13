package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ParteDelReparto
import com.jvillada.movi.shared.model.PropuestaDeReparto
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.repartoAMano
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatMoney

/*
 * # Repartir un envío en Reconciliar
 *
 * Ver `RepartirUnEnvio.kt` en `:core`. Cuando el envío es **exactamente** la suma de 2 o 3 pagos del
 * período asociados al tercero, Reconciliar lo propone arriba: «Son 2 pagos: Hipoteca Papá
 * $2.228.152 + Libranza Papá $2.051.848», con «Repartir así». Si no hay suma exacta pero el aviso va
 * a alguien, «Repartir entre varios pagos…» deja elegirlos a mano: cada uno toma su monto y **el
 * último toma el resto**. Se confirma por el mismo `POST /api/sms/grupo/{id}/confirmar` de siempre,
 * con `reparto`.
 */

/** «Son 2 pagos: Hipoteca Papá $2.228.152 + Libranza Papá $2.051.848». */
fun textoDelRepartoExacto(pagos: List<PagoParaRepartir>, moneda: String): String =
    "Son ${pagos.size} pagos: " + pagos.joinToString(" + ") { "${it.nombre} ${formatMoney(it.monto, moneda)}" }

/**
 * **Las partes que viajan al server**: una por pago, con el monto que le tocó. La cuota de un crédito
 * va con sus dos patas (sale de [cuenta], entra al crédito, «Cuota de crédito» y la deuda baja por el
 * capital); un recurrente común, con un gasto con su categoría y su nombre de nota. Todas con el texto
 * del aviso, que es el que dice a quién fue la plata, y con ids nuevos: un reintento no duplica.
 */
fun partesDelReparto(
    elegidos: List<Pair<PagoParaRepartir, Long>>,
    cuenta: Account,
    leido: ParsedSms,
    momento: Long,
    textoDelAviso: String,
    esComprobante: Boolean,
    nuevoId: (String) -> String,
): List<ParteDelReparto> = elegidos.map { (pago, monto) ->
    val deuda = pago.cuentaDeLaDeudaId
    if (deuda != null) {
        ParteDelReparto(
            ruleId = pago.ruleId,
            periodo = pago.periodo,
            monto = monto,
            patas = DosPatasDelAviso(
                operacion = OperacionDelAviso.CUOTA,
                origenId = cuenta.id,
                destinoId = deuda,
                monto = monto,
                timestamp = momento,
                transferId = nuevoId("tr"),
                origenEventId = nuevoId("ev"),
                destinoEventId = nuevoId("ev"),
                nota = pago.nombre,
            ),
        )
    } else {
        ParteDelReparto(
            ruleId = pago.ruleId,
            periodo = pago.periodo,
            monto = monto,
            evento = FinancialEvent(
                id = nuevoId("ev"),
                accountId = cuenta.id,
                type = TransactionType.EXPENSE,
                amount = monto,
                currency = leido.currency,
                category = pago.categoria,
                description = pago.nombre,
                merchant = leido.merchant,
                timestamp = momento,
                source = if (esComprobante) EventSource.OCR else EventSource.SMS,
                rawPayload = textoDelAviso.ifBlank { null },
                reconciliationStatus = ReconciliationStatus.RECONCILED,
            ),
        )
    }
}

const val TAG_REPARTO_EN_RECONCILIAR: String = "sms:reparto"
const val REPARTIR_ASI: String = "Repartir así"
const val REPARTIR_A_MANO: String = "Repartir entre varios pagos…"

/** «Repartir en 2 pagos». */
fun botonDeRepartirAMano(cuantos: Int): String = "Repartir en $cuantos pagos"

/**
 * La tarjeta del reparto en Reconciliar. [onRepartir] recibe cada pago con su monto y si hay que
 * recordar el tercero en esos pagos.
 */
@Composable
internal fun TarjetaDelReparto(
    propuesta: PropuestaDeReparto,
    habilitado: Boolean,
    onRepartir: (elegidos: List<Pair<PagoParaRepartir, Long>>, recordar: Boolean) -> Unit,
) {
    val exactos = propuesta.exacto.mapNotNull { id -> propuesta.pagos.firstOrNull { it.ruleId == id } }
    var aMano by remember(propuesta.avisoId) { mutableStateOf(false) }
    var elegidos by remember(propuesta.avisoId) { mutableStateOf(emptyList<PagoParaRepartir>()) }
    var recordar by remember(propuesta.avisoId) { mutableStateOf(true) }

    if (exactos.isEmpty() && !aMano) {
        if (propuesta.pagos.size < 2) return
        Text(
            REPARTIR_A_MANO,
            style = Movi.textos.cuerpo,
            color = Movi.colores.marca,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable(enabled = habilitado, role = Role.Button) { aMano = true }
                .padding(vertical = 6.dp),
        )
        return
    }

    Spacer(Modifier.height(14.dp))
    MinSectionHeader(title = if (aMano) "Repartir entre varios pagos" else "Movi lo puede repartir")
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_REPARTO_EN_RECONCILIAR),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        if (!aMano) {
            Text(textoDelRepartoExacto(exactos, propuesta.moneda), style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            Spacer(Modifier.height(6.dp))
            Text(
                "Suman exactamente lo que dice el aviso. Se anota un movimiento por pago, con su categoría, y cada pago del período queda marcado.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PastillaDelPago(REPARTIR_ASI, principal = true, habilitada = habilitado) {
                    onRepartir(exactos.map { it to it.monto }, false)
                }
                PastillaDelPago("Elegir otros…", habilitada = habilitado) { aMano = true; elegidos = exactos }
            }
            return@MinCard
        }

        Text(
            "Elige los pagos en orden: cada uno toma su monto y el último toma el resto de los ${formatMoney(propuesta.monto, propuesta.moneda)}.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        val reparto = repartoAMano(propuesta.monto, elegidos)
        propuesta.pagos.forEachIndexed { i, pago ->
            if (i > 0) Hairline()
            val orden = elegidos.indexOfFirst { it.ruleId == pago.ruleId }
            val toma = reparto?.firstOrNull { it.first.ruleId == pago.ruleId }?.second
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = habilitado, role = Role.Checkbox) {
                        elegidos = if (orden >= 0) elegidos.filter { it.ruleId != pago.ruleId } else elegidos + pago
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (orden >= 0) Modifier.background(Movi.colores.texto)
                            else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (orden >= 0) Text("${orden + 1}", style = Movi.textos.apoyo, color = Movi.colores.fondo, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(pago.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Text(
                        "${pago.categoria} · ${formatMoney(pago.monto, propuesta.moneda)}" +
                            if (pago.asociado) " · va a ${propuesta.destinoNombre ?: "esta persona"}" else "",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
                if (toma != null) {
                    Text(formatMoney(toma, propuesta.moneda), style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (elegidos.isNotEmpty() && reparto == null) {
            Text(
                "Con esos pagos no alcanza: lo de los primeros ya suma lo que dice el aviso. Quita alguno.",
                style = Movi.textos.apoyo,
                color = Movi.colores.aviso,
            )
            Spacer(Modifier.height(8.dp))
        }
        val nombre = propuesta.destinoNombre
        val hayQueRecordar = propuesta.destinoId != null && elegidos.any { !it.asociado }
        if (hayQueRecordar && nombre != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { recordar = !recordar }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (recordar) Modifier.background(Movi.colores.texto)
                            else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(4.dp)),
                        ),
                )
                Text("Recordar que estos pagos van a $nombre", style = Movi.textos.apoyo, color = Movi.colores.texto)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PastillaDelPago(
                botonDeRepartirAMano(elegidos.size),
                principal = true,
                habilitada = habilitado && reparto != null && elegidos.size >= 2,
            ) { reparto?.let { onRepartir(it, hayQueRecordar && recordar) } }
            PastillaDelPago("Cancelar", habilitada = habilitado) { aMano = false; elegidos = emptyList() }
        }
    }
}
