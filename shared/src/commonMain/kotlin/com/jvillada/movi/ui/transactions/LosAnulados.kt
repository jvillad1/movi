package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.MovimientoAnulado
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.analisis.fechaCorta
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
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
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.coroutines.launch

/*
 * # Vuelta 67 · «Lo anulado», con «Restaurar»
 *
 * Anular saca un movimiento de todas las cifras sin borrarlo. Lo anulado tiene su lista (`GET
 * /api/events/anulados`), el último anulado primero, y «Restaurar» lo devuelve (`DELETE
 * /api/events/{id}/void`). Un traspaso o una cuota se listan una vez y se restauran las dos patas.
 *
 * La revisión de la vuelta sacó «Anulados» de los chips de Actividad (no es un filtro del período y
 * empujaba «Compartidos» fuera de la vista): Actividad termina con «2 anulados en octubre · Ver», que
 * abre esta pantalla con ese período, y en Tú, junto a los mensajes del banco, «Lo anulado» la abre
 * con todo. Lo que anuló el sistema (un ajuste de saldo, un extracto deshecho, una división) no ofrece
 * «Restaurar»: dice por qué ([MovimientoAnulado.porQueNo]).
 */

/** El título de la pantalla y de su fila en Tú. */
const val TITULO_LO_ANULADO: String = "Lo anulado"

/** Lo que dice el vacío de «Lo anulado». */
const val NADA_ANULADO: String = "Nada anulado"

/** El tag de una fila de «Lo anulado»: `TAG:<id>`. */
const val TAG_FILA_ANULADA: String = "anulados:fila"

/** El tag del botón «Restaurar» de una fila. */
const val TAG_RESTAURAR: String = "anulados:restaurar"

/** El tag de la fila «2 anulados en octubre · Ver» del final de Actividad. */
const val TAG_ANULADOS_DEL_PERIODO: String = "anulados:del-periodo"

/** De un par (las dos patas de un traspaso o una cuota) se lista una sola, la primera. Pura. */
fun anuladosUnoPorPar(anulados: List<MovimientoAnulado>): List<MovimientoAnulado> {
    val vistos = mutableSetOf<String>()
    return anulados.filter { a -> a.evento.transferId?.let { vistos.add(it) } ?: true }
}

/** Lo anulado cuyo movimiento cae en [periodo] (un par, una vez). Pura. */
fun anuladosDelPeriodo(anulados: List<MovimientoAnulado>, periodo: PeriodoFinanciero, settings: PeriodSettings): List<MovimientoAnulado> {
    val ventana = ventanaDe(periodo, settings)
    return anuladosUnoPorPar(anulados).filter { it.evento.timestamp in ventana }
}

/** «1 anulado en octubre», «3 anulados en octubre». Pura. */
fun textoDeLosAnuladosDelPeriodo(cuantos: Int, periodo: PeriodoFinanciero): String =
    "$cuantos ${if (cuantos == 1) "anulado" else "anulados"} en ${MESES_DEL_ANIO[periodo.month - 1]}"

/**
 * **El subtítulo de una fila anulada**: «$180.000 · 12 oct · anulado el 29 oct · Duplicado». El monto
 * va acá porque a la derecha está «Restaurar»; la fecha del movimiento y la de la anulación, porque la
 * lista no va por días. Lo que no se restaura dice por qué, en el renglón siguiente. Pura.
 */
fun lineaDelAnulado(a: MovimientoAnulado): String {
    val linea = listOfNotNull(
        formatMoney(a.evento.amount, a.evento.currency),
        a.evento.timestamp.takeIf { it > 0L }?.let { fechaCorta(fechaDeEpoch(it)) },
        a.anuladoEn.takeIf { it > 0L }?.let { "anulado el ${fechaCorta(fechaDeEpoch(it))}" },
        a.motivo?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
    return a.porQueNo?.let { "$linea\n$it" } ?: linea
}

/** La fila del final de Actividad: «2 anulados en octubre» y «Ver». */
@Composable
internal fun FilaDeLosAnulados(texto: String, onVer: () -> Unit, modifier: Modifier = Modifier) {
    Superficie(modifier = modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = texto,
            final = { Boton("Ver", onClick = onVer, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S) },
            onClick = onVer,
            onClickLabel = "Ver lo anulado",
            modifier = Modifier.testTag(TAG_ANULADOS_DEL_PERIODO),
        )
    }
}

/**
 * **«Lo anulado»**: de [periodo] (el prefijo, «2026-10») si viene de Actividad, o de cualquier fecha
 * si viene de Tú.
 */
@Composable
fun AnuladosScreen(periodo: String?, onNavigate: (Screen) -> Unit) {
    var recarga by remember { mutableStateOf(0) }
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = recarga) { Repositories.wallets.getUserProfile() }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = recarga) { Repositories.wallets.getAccounts() }
    val delPeriodo = periodo?.let { periodoDelPrefijo(it) }
    val ajustes = perfil.valor?.ajustesDelPeriodo()
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_LO_ANULADO,
            leading = HeaderLeading.Back(if (delPeriodo != null) Screen.Transactions() else Screen.Tu),
            subtitle = delPeriodo?.let { "En ${tituloDelPeriodo(it)}" },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones),
        ) {
            ListaDeAnulados(
                recarga = recarga,
                nombresDeCuentas = cuentas.valor.orEmpty().associate { it.id to it.name },
                filtrar = { lista ->
                    if (delPeriodo == null) anuladosUnoPorPar(lista)
                    else if (ajustes == null) null
                    else anuladosDelPeriodo(lista, delPeriodo, ajustes)
                },
                onRestaurado = { recarga++ },
            )
        }
    }
}

/**
 * La lista de lo anulado: se lee aparte, [filtrar] decide qué se ve (`null` = todavía no se sabe), y
 * cada fila se restaura en su lugar o dice por qué no.
 */
@Composable
internal fun ListaDeAnulados(
    recarga: Int,
    nombresDeCuentas: Map<String, String>,
    filtrar: (List<MovimientoAnulado>) -> List<MovimientoAnulado>?,
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
        val lista = anulados?.let(filtrar)
        when {
            anulados == null && noSePudo -> NoSePudoLeer("No pudimos cargar lo anulado", onReintentar = { reintento++ })
            lista == null -> Text("Cargando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            lista.isEmpty() -> Vacio(
                titulo = NADA_ANULADO,
                detalle = "Lo que anules queda aquí, por si quieres restaurarlo.",
                modifier = Modifier.padding(top = Espacio.xxl),
            )
            else -> Superficie(relleno = RellenoDeSuperficie.Lista) {
                lista.forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    val e = a.evento
                    Fila(
                        titulo = textosDeLaFila(e, nombresDeCuentas[e.accountId]).titulo,
                        subtitulo = lineaDelAnulado(a),
                        lineasDelSubtitulo = if (a.porQueNo != null) 4 else 2,
                        inicio = { AvatarDelComercio(e) },
                        // Lo que anuló el sistema no se restaura: la frase de abajo dice por qué.
                        final = if (a.porQueNo != null) null else {
                            {
                                Boton(
                                    "Restaurar",
                                    onClick = {
                                        if (restaurando == null) {
                                            restaurando = e.id
                                            error = null
                                            coroutine.launch {
                                                intentar { Repositories.wallets.restaurarMovimiento(e.id) }
                                                    .onSuccess {
                                                        anulados = anulados?.filterNot {
                                                            it.evento.id == e.id || (e.transferId != null && it.evento.transferId == e.transferId)
                                                        }
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
                            }
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
