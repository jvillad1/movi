package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RepartoDeUnMovimiento
import com.jvillada.movi.shared.model.partesQueSeVen
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmacionEnLinea
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.launch

/*
 * # Vuelta 67 · una compra dividida deja rastro
 *
 * «Dividir en categorías» anulaba el original con el motivo «Repartido en partes» y dejaba filas
 * sueltas, sin nada que las uniera. Ahora cada parte sabe de qué compra es
 * ([FinancialEvent.repartoDe]) y su detalle lo dice: «Parte de una compra de $100.000 · 2 partes», con
 * «Ver» (las partes) y «Deshacer» (vuelve a ser un solo movimiento; `POST
 * /api/events/{id}/deshacer-reparto`).
 */

/** El tag del bloque «Parte de una compra» del detalle. */
const val TAG_PARTE_DE_UNA_COMPRA: String = "movimiento:parte-de-una-compra"

/** El tag de «Deshacer» de ese bloque. */
const val TAG_DESHACER_LA_DIVISION: String = "movimiento:deshacer-la-division"

/** «Parte de una compra de $100.000 · 2 partes». Pura. */
fun textoDeLaParte(reparto: RepartoDeUnMovimiento): String {
    val cuantas = partesQueSeVen(reparto).size
    val total = formatMoney(reparto.original.amount, reparto.original.currency)
    return "Parte de una compra de $total · $cuantas ${if (cuantas == 1) "parte" else "partes"}"
}

/** Lo que dice la pregunta antes de deshacer: qué vuelve y qué se va. Pura. */
fun queHaceDeshacerLaDivision(reparto: RepartoDeUnMovimiento): Pair<String, String> {
    val total = formatMoney(reparto.original.amount, reparto.original.currency)
    val cuantas = partesQueSeVen(reparto).size
    return "¿Volver a un solo movimiento?" to
        "Vuelve el movimiento de $total en ${reparto.original.category}, y las $cuantas partes se anulan."
}

/** El bloque del detalle de una parte: de qué compra es, con «Ver» y «Deshacer». */
@Composable
internal fun ParteDeUnaCompra(evento: FinancialEvent, onDeshecho: (FinancialEvent) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var reparto by remember(evento.id) { mutableStateOf<RepartoDeUnMovimiento?>(null) }
    var viendo by remember(evento.id) { mutableStateOf(false) }
    var preguntando by remember(evento.id) { mutableStateOf(false) }
    var trabajando by remember(evento.id) { mutableStateOf(false) }
    var error by remember(evento.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(evento.id) {
        intentar { Repositories.wallets.getRepartoDe(evento.id) }.onSuccess { reparto = it }
    }
    // Sin la lectura (sin red, o una parte de antes de esta vuelta), nada: no se afirma lo que no se sabe.
    val leido = reparto ?: return
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_PARTE_DE_UNA_COMPRA), nivel = NivelDeSuperficie.Capa2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            IconoEnCirculo(Icons.AutoMirrored.Rounded.CallSplit, tono = TonoDeIcono.Neutro)
            Text(textoDeLaParte(leido), style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
        }
        if (viendo) {
            Spacer(Modifier.height(Espacio.xs))
            partesQueSeVen(leido).forEach { parte ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        // Vuelta 67 (revisión): cada parte por su categoría (el concepto es el mismo en todas).
                        parte.category + if (parte.id == evento.id) " · esta" else "",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.weight(1f),
                    )
                    Cifra(parte.amount, VarianteDeCifra.Chica, moneda = parte.currency)
                }
            }
        }
        Spacer(Modifier.height(Espacio.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Boton(if (viendo) "Ocultar" else "Ver", onClick = { viendo = !viendo }, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
            Boton(
                "Deshacer",
                onClick = { preguntando = true },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                habilitado = !trabajando,
                modifier = Modifier.testTag(TAG_DESHACER_LA_DIVISION),
            )
        }
    }
    if (preguntando) {
        val (pregunta, detalle) = queHaceDeshacerLaDivision(leido)
        Spacer(Modifier.height(Espacio.xs))
        ConfirmacionEnLinea(
            pregunta = pregunta,
            detalle = detalle,
            textoConfirmar = "Deshacer",
            ocupado = trabajando,
            onConfirmar = {
                if (!trabajando) {
                    trabajando = true
                    error = null
                    coroutine.launch {
                        intentar { Repositories.wallets.deshacerReparto(evento.id) }
                            .onSuccess { original -> preguntando = false; onDeshecho(original) }
                            .onFailure { error = it.toUserMessage() }
                        trabajando = false
                    }
                }
            },
            onCancelar = { if (!trabajando) preguntando = false },
        )
    }
    error?.let {
        Spacer(Modifier.height(Espacio.xs))
        Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
    }
}
