package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/*
 * # «Repartir este gasto» desde Agregar (vuelta de diseño 23)
 *
 * Antes el ícono de personas de Agregar sacaba de la hoja y abría «Pagó otro · ¿Cuánto pagó Caro?»
 * en la pantalla del espacio, con el campo vacío: se perdía el monto que ya estaba escrito, el sentido
 * quedaba al revés (el que anota casi siempre es el que pagó) y el formulario era otro.
 *
 * Ahora abre esta hoja **encima de Agregar**, con lo escrito: el monto, la categoría y la fecha.
 * Pregunta quién pagó («Yo» por defecto), en qué espacio si hay varios, y dice cómo se reparte. Al
 * confirmar **no hay camino nuevo**, solo se unen los dos que ya existían:
 *
 * - **Pagué yo**: el guardado de siempre de Agregar (`POST /api/events`) y, con el movimiento ya
 *   guardado, el «Repartir» del detalle de un movimiento (`POST /api/espacios/{id}/compartir`).
 * - **Pagó otro**: lo mismo que la hoja «Pagó otro» (`POST /api/espacios/{id}/gastos`), con la fecha
 *   que se eligió en Agregar en vez de «ahora».
 *
 * **Cómo se reparte no se elige acá**: la proporción es del espacio ([Espacio.reparto], mitad y mitad
 * o porcentajes), y no existe una por gasto. La hoja la dice; inventar otra sería lógica de plata nueva.
 *
 * **Si se guardó y no se pudo repartir** (sin conexión; o, en el teléfono, porque el movimiento todavía
 * no subió al server y el reparto lo pide el server), el gasto ya quedó anotado: la hoja lo dice y
 * ofrece reintentar **solo el reparto**, con el mismo movimiento. Nunca vuelve a guardar.
 */

/** Lo que Agregar ya tiene escrito y viaja a la hoja. */
data class GastoParaRepartir(
    /** El monto escrito; 0 si todavía no hay. */
    val monto: Long,
    val categoria: String,
    val nota: String,
    /** El `timestamp` con que Agregar guardaría el movimiento (la fecha elegida). */
    val momento: Long,
    /** La fecha como se lee en Agregar: «Hoy», «Ayer», «23 de octubre». */
    val fecha: String,
    /** El nombre de la cuenta de la que sale, si pagaste tú; `null` si no se eligió. */
    val cuenta: String? = null,
    /** La moneda de la cuenta elegida: un espacio solo reparte pesos. */
    val moneda: String = "COP",
    /** Lo que le falta a Agregar para guardar («Falta la cuenta»), o `null` si puede. */
    val faltaParaGuardar: String? = null,
)

/** Quién pagó, y en qué espacio. */
data class DecisionDeRepartir(val espacio: Espacio, val pagadoPor: String) {
    val pagueYo: Boolean get() = pagadoPor == MIEMBRO_YO
}

/** Los dos caminos que ya existen, inyectables para las pruebas. */
class CaminosDeRepartir(
    /** El guardado de Agregar: devuelve el movimiento guardado o lanza. */
    val guardarElGasto: suspend () -> FinancialEvent,
    /** El «Repartir» del detalle de un movimiento. */
    val repartirMovimiento: suspend (espacioId: String, eventoId: String) -> Unit,
    /** La hoja «Pagó otro». */
    val anotarGastoDeOtro: suspend (espacioId: String, gasto: GastoDelEspacio) -> Unit,
)

/** Cómo terminó un intento. */
sealed interface ResultadoDeRepartir {
    /** Todo quedó: el gasto [evento] (si era tuyo) y su reparto, o lo que pagó otro ([evento] nulo). */
    data class Listo(val evento: FinancialEvent?) : ResultadoDeRepartir

    /** No se escribió nada. */
    data class NoSeGuardo(val motivo: String) : ResultadoDeRepartir

    /** El gasto quedó guardado y el reparto no: se reintenta solo el reparto, con [evento]. */
    data class GuardadoSinRepartir(val evento: FinancialEvent, val motivo: String) : ResultadoDeRepartir
}

/**
 * **Lo que pagó otro, armado desde Agregar**: lo mismo que arma la hoja «Pagó otro» —la descripción es
 * la nota o, sin nota, la categoría ([descripcionDelPagoOtro])—, con la fecha elegida en Agregar.
 */
fun gastoDeOtroDesdeAgregar(gasto: GastoParaRepartir, decision: DecisionDeRepartir, id: String): GastoDelEspacio =
    GastoDelEspacio(
        id = id,
        espacioId = decision.espacio.id,
        pagadoPor = decision.pagadoPor,
        monto = gasto.monto,
        categoria = gasto.categoria.trim(),
        descripcion = descripcionDelPagoOtro(gasto.nota, gasto.categoria),
        fecha = gasto.momento,
    )

/**
 * **Une los dos caminos.** [yaGuardado] es el movimiento de un intento anterior que se guardó y no se
 * repartió: con él no se vuelve a guardar, solo se reparte.
 */
suspend fun repartirDesdeAgregar(
    gasto: GastoParaRepartir,
    decision: DecisionDeRepartir,
    idDelGastoDeOtro: String,
    yaGuardado: FinancialEvent?,
    caminos: CaminosDeRepartir,
): ResultadoDeRepartir {
    if (!decision.pagueYo) {
        return intentar { caminos.anotarGastoDeOtro(decision.espacio.id, gastoDeOtroDesdeAgregar(gasto, decision, idDelGastoDeOtro)) }
            .fold({ ResultadoDeRepartir.Listo(null) }, { ResultadoDeRepartir.NoSeGuardo(mensajeDelEspacio(it)) })
    }
    val evento = yaGuardado ?: intentar { caminos.guardarElGasto() }
        .getOrElse { return ResultadoDeRepartir.NoSeGuardo(it.toUserMessage()) }
    return intentar { caminos.repartirMovimiento(decision.espacio.id, evento.id) }
        .fold({ ResultadoDeRepartir.Listo(evento) }, { ResultadoDeRepartir.GuardadoSinRepartir(evento, mensajeDelEspacio(it)) })
}

/** «Mitad y mitad», o los porcentajes del espacio: «Tú 60 % · Caro 40 %». */
fun comoSeReparte(espacio: Espacio): String = when {
    espacio.reparto == RepartoDelEspacio.PARTES_IGUALES && idsDeMiembros(espacio).size == 2 -> "Mitad y mitad"
    espacio.reparto == RepartoDelEspacio.PARTES_IGUALES -> "En partes iguales"
    else -> idsDeMiembros(espacio).joinToString(" · ") { id ->
        val porcentaje = espacio.miembros.firstOrNull { it.id == id }?.porcentaje ?: 0
        "${nombreEnElEspacio(espacio, id)} $porcentaje %"
    }
}

/** Por qué no se puede confirmar todavía, o `null` si se puede. */
fun queFaltaParaRepartir(gasto: GastoParaRepartir, pagueYo: Boolean): String? = when {
    gasto.monto <= 0L -> "Falta el monto"
    gasto.categoria.isBlank() -> "Falta la categoría"
    pagueYo && gasto.moneda != "COP" -> "Por ahora solo se reparten gastos en pesos"
    pagueYo -> gasto.faltaParaGuardar
    else -> null
}

/** Tags para las pruebas. */
const val TAG_HOJA_REPARTIR_ESTE_GASTO: String = "espacios:repartir-este-gasto"
const val TAG_CONFIRMAR_REPARTIR_ESTE_GASTO: String = "espacios:confirmar-repartir-este-gasto"

/** El título de la hoja. */
const val TITULO_REPARTIR_ESTE_GASTO: String = "Repartir este gasto"

/**
 * **La hoja «Repartir este gasto».** [onMonto] escribe el monto en Agregar (el campo es el mismo dato,
 * no una copia). [onListo] recibe el movimiento guardado si pagaste tú, o `null` si pagó otro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HojaRepartirEsteGasto(
    gasto: GastoParaRepartir,
    espacios: List<Espacio>,
    caminos: CaminosDeRepartir,
    onMonto: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onListo: (FinancialEvent?) -> Unit,
) {
    val primero = espacios.firstOrNull() ?: return
    val coroutine = rememberCoroutineScope()
    val idDelGastoDeOtro = remember { newId("gde") }
    var espacioId by remember { mutableStateOf(primero.id) }
    val espacio = espacios.firstOrNull { it.id == espacioId } ?: primero
    var quien by remember { mutableStateOf(MIEMBRO_YO) }
    // Si cambió el espacio y quien pagó no es de este, vuelve a «Yo».
    val pagador = quien.takeIf { q -> q == MIEMBRO_YO || espacio.miembros.any { it.id == q } } ?: MIEMBRO_YO
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var yaGuardado by remember { mutableStateOf<FinancialEvent?>(null) }
    val decision = DecisionDeRepartir(espacio, pagador)
    val falta = queFaltaParaRepartir(gasto, decision.pagueYo)
    val tuParte = if (gasto.monto > 0L) partesDelGasto(gasto.monto, espacio, pagador)[MIEMBRO_YO] ?: 0L else 0L
    // Con el gasto ya guardado, lo único que queda es el reparto: no se cambia nada de lo de arriba.
    val bloqueado = trabajando || yaGuardado != null

    fun confirmar() {
        if (trabajando || falta != null) return
        trabajando = true
        error = null
        coroutine.launch {
            when (val r = repartirDesdeAgregar(gasto, decision, idDelGastoDeOtro, yaGuardado, caminos)) {
                is ResultadoDeRepartir.Listo -> onListo(r.evento)
                is ResultadoDeRepartir.NoSeGuardo -> error = r.motivo
                is ResultadoDeRepartir.GuardadoSinRepartir -> {
                    yaGuardado = r.evento
                    error = "El gasto quedó anotado, pero no se pudo repartir: ${r.motivo}"
                }
            }
            trabajando = false
        }
    }

    // Con el gasto ya guardado, cerrar la hoja es «Cerrar»: Agregar también se cierra. Si quedara abierto
    // con lo mismo escrito, un «Guardar movimiento» lo anotaría dos veces.
    MarcoDeHoja(onDismiss = { yaGuardado?.let(onListo) ?: onDismiss() }, dismissEnabled = !trabajando) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_REPARTIR_ESTE_GASTO),
        ) {
            EncabezadoDeHoja(TITULO_REPARTIR_ESTE_GASTO, subtitulo = "En tus gastos cuenta solo tu parte.")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CampoDeMonto(gasto.monto.takeIf { it > 0L }, onMonto, rotulo = "Monto", habilitado = !bloqueado)
            Spacer(Modifier.height(EspacioDeDiseno.xxs))
            Text(
                listOfNotNull(gasto.categoria.trim().ifBlank { null }, gasto.fecha).joinToString(" · "),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            if (espacios.size > 1) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                RotuloDeCampo("¿En qué espacio?")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                    verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                ) {
                    espacios.forEach { e ->
                        PildoraDeFiltro(e.nombre, seleccionada = e.id == espacio.id, onClick = { if (!bloqueado) espacioId = e.id })
                    }
                }
            }
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            RotuloDeCampo("¿Quién pagó?")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
            ) {
                PildoraDeFiltro("Yo", seleccionada = pagador == MIEMBRO_YO, onClick = { if (!bloqueado) quien = MIEMBRO_YO })
                espacio.miembros.filter { it.id != MIEMBRO_YO }.forEach { m ->
                    PildoraDeFiltro(
                        nombreEnElEspacio(espacio, m.id),
                        seleccionada = pagador == m.id,
                        onClick = { if (!bloqueado) quien = m.id },
                    )
                }
            }
            Spacer(Modifier.height(EspacioDeDiseno.xxs))
            Text(
                if (decision.pagueYo) {
                    gasto.cuenta?.let { "Se anota en $it y se reparte en ${espacio.nombre}." }
                        ?: "Se anota en tu cuenta y se reparte en ${espacio.nombre}."
                } else {
                    "No toca tus cuentas: se anota en ${espacio.nombre}."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            RotuloDeCampo("¿Cómo se reparte?")
            Text(
                if (tuParte > 0L) "${comoSeReparte(espacio)} · Tu parte ${formatCOP(tuParte)}" else comoSeReparte(espacio),
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
            )
            Spacer(Modifier.height(Medida.entreSecciones))
            PieDeHoja(
                texto = when {
                    yaGuardado != null -> "Reintentar el reparto"
                    decision.pagueYo -> "Guardar y repartir"
                    else -> "Guardar"
                },
                onClick = ::confirmar,
                habilitado = falta == null && !trabajando,
                razon = falta,
                cargando = trabajando,
                error = error,
                modifier = Modifier.testTag(TAG_CONFIRMAR_REPARTIR_ESTE_GASTO),
                secundaria = if (yaGuardado != null) "Cerrar" else null,
                onSecundaria = { yaGuardado?.let(onListo) },
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}
