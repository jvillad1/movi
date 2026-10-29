package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.analisis.fechaCorta
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.coroutines.launch

/*
 * # Vuelta 67 · «Anulados» en Actividad, con «Restaurar»
 *
 * Anular saca un movimiento de todas las cifras sin borrarlo. Hasta acá no había dónde verlo ni cómo
 * volver atrás: el chip «Anulados» lista lo anulado (el último primero) y «Restaurar» lo devuelve
 * (`DELETE /api/events/{id}/void`). Un traspaso o una cuota se listan una vez y se restauran las dos
 * patas.
 */

/** Lo que dice el vacío de «Anulados». */
const val NADA_ANULADO: String = "Nada anulado"

/** El tag de una fila de «Anulados»: `TAG:<id>`. */
const val TAG_FILA_ANULADA: String = "anulados:fila"

/** El tag del botón «Restaurar» de una fila. */
const val TAG_RESTAURAR: String = "anulados:restaurar"

/** De un par (las dos patas de un traspaso o una cuota) se lista una sola, la primera. Pura. */
fun anuladosUnoPorPar(anulados: List<MovimientoAnulado>): List<MovimientoAnulado> {
    val vistos = mutableSetOf<String>()
    return anulados.filter { a -> a.evento.transferId?.let { vistos.add(it) } ?: true }
}

/**
 * **El subtítulo de una fila anulada**: «$40.000 · anulado el 29 oct · Duplicado». El monto va acá
 * porque a la derecha está «Restaurar». Pura.
 */
fun lineaDelAnulado(a: MovimientoAnulado): String = listOfNotNull(
    formatMoney(a.evento.amount, a.evento.currency),
    a.anuladoEn.takeIf { it > 0L }?.let { "anulado el ${fechaCorta(fechaDeEpoch(it))}" },
    a.motivo?.trim()?.takeIf { it.isNotEmpty() },
).joinToString(" · ")

/** La lista de «Anulados»: se lee aparte, y cada fila se restaura en su lugar. */
@Composable
internal fun ListaDeAnulados(
    recarga: Int,
    nombresDeCuentas: Map<String, String>,
    onRestaurado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutine = rememberCoroutineScope()
    var anulados by remember { mutableStateOf<List<MovimientoAnulado>?>(null) }
    var noSePudo by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    var restaurando by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresco = LocalRefreshTick.current
    LaunchedEffect(recarga, refresco, reintento) {
        intentar { Repositories.wallets.getAnulados() }
            .onSuccess { anulados = it; noSePudo = false }
            .onFailure { noSePudo = true }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        val lista = anulados
        when {
            lista == null && noSePudo -> NoSePudoLeer("No pudimos cargar lo anulado", onReintentar = { reintento++ })
            lista == null -> Text("Cargando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            lista.isEmpty() -> Vacio(
                titulo = NADA_ANULADO,
                detalle = "Lo que anules queda aquí, por si quieres restaurarlo.",
                modifier = Modifier.padding(top = Espacio.xxl),
            )
            else -> Superficie(relleno = RellenoDeSuperficie.Lista) {
                anuladosUnoPorPar(lista).forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    val e = a.evento
                    Fila(
                        titulo = textosDeLaFila(e, nombresDeCuentas[e.accountId]).titulo,
                        subtitulo = lineaDelAnulado(a),
                        lineasDelSubtitulo = 2,
                        inicio = { AvatarDelComercio(e) },
                        final = {
                            Boton(
                                "Restaurar",
                                onClick = {
                                    if (restaurando == null) {
                                        restaurando = e.id
                                        error = null
                                        coroutine.launch {
                                            intentar { Repositories.wallets.restaurarMovimiento(e.id) }
                                                .onSuccess {
                                                    anulados = anulados?.filterNot { it.evento.id == e.id || (e.transferId != null && it.evento.transferId == e.transferId) }
                                                    onRestaurado()
                                                }
                                                .onFailure { error = it.toUserMessage() }
                                            restaurando = null
                                        }
                                    }
                                },
                                variante = VarianteDeBoton.Fantasma,
                                tamano = TamanoDeBoton.S,
                                cargando = restaurando == e.id,
                                habilitado = restaurando == null,
                                modifier = Modifier.testTag(TAG_RESTAURAR),
                            )
                        },
                        modifier = Modifier.testTag("$TAG_FILA_ANULADA:${e.id}"),
                    )
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
    }
}
