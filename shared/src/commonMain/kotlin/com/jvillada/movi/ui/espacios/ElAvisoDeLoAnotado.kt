package com.jvillada.movi.ui.espacios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.tuParteDe
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.FalloDelAviso
import com.jvillada.movi.ui.components.formatMoney

/*
 * # Vuelta 74 · «Anotado · Restaurantes $32.000 · Repartir con Caro · Deshacer»
 *
 * Repartir con la pareja tomaba 4 toques (abrir el movimiento, «Repartir», elegir el grupo,
 * confirmar). Ahora el aviso que dice lo que se anotó lo ofrece en uno: **«Repartir con Caro»**, con
 * el `…/compartir` de siempre (`repartirMovimiento`) y la regla del grupo (50/50, o sus porcentajes:
 * la decide el server). Hecho, el aviso pasa a «Repartido con Caro · tu parte $16.000 · Deshacer», y
 * deshacer es sacarlo del grupo (`quitarDelEspacio`), que también existía.
 *
 * En el teléfono sin señal el reparto no se pide: `LocalRepository.repartirMovimiento` da su vuelta
 * de subida y lanza `MovimientoSinSubir`, y el aviso dice [REPARTO_SIN_CONEXION] (vuelta de diseño 24).
 */

/**
 * **«Anotado · Restaurantes $32.000»**: lo que dice el aviso de abajo al guardar [evento]. El comercio
 * como se muestra en toda la app (lo que dijo el banco, «Carulla Laureles») y, sin comercio —lo
 * anotado a mano—, la categoría.
 */
fun textoDeLoAnotado(evento: FinancialEvent): String {
    val quien = evento.merchant?.takeIf { it.isNotBlank() }?.let(::nombreParaMostrar)
        ?: evento.category.trim().ifEmpty { nombreParaMostrar(evento.description) }
    return "Anotado · $quien ${formatMoney(evento.amount, evento.currency)}"
}

/**
 * **«Ya estaba anotado · Restaurantes $32.000»**: lo que dice el aviso tras «Es este» (revisión de la
 * vuelta 74). No se anotó nada: el aviso se juntó con lo que ya estaba.
 */
fun textoDeLoQueYaEstaba(evento: FinancialEvent): String = textoDeLoAnotado(evento).replaceFirst("Anotado · ", "Ya estaba anotado · ")

/** «Anotado · Traspaso $500.000»: lo que dice el aviso al guardar un traspaso o una cuota en Agregar. */
fun textoDeLoAnotado(que: String, monto: Long, moneda: String): String = "Anotado · $que ${formatMoney(monto, moneda)}"

/** Los otros miembros de [espacio] (sin el dueño), por su nombre. */
private fun losOtros(espacio: Espacio): List<String> =
    idsDeMiembros(espacio).filter { it != MIEMBRO_YO }.map { nombreEnElEspacio(espacio, it) }

/** «Repartir con Caro» (una persona más); «Repartir en Casa» (varias). */
fun rotuloDeRepartirCon(espacio: Espacio): String =
    losOtros(espacio).singleOrNull()?.let { "Repartir con $it" } ?: "Repartir en ${espacio.nombre}"

/** «Repartido con Caro · tu parte $16.000» (o «Repartido en Casa · …»). */
fun textoDeRepartido(espacio: Espacio, tuParte: Long): String {
    val con = losOtros(espacio).singleOrNull()?.let { "con $it" } ?: "en ${espacio.nombre}"
    return "Repartido $con · tu parte ${formatMoney(tuParte, "COP")}"
}

/** El rótulo de la acción cuando hay varios grupos: abre la elección. */
const val REPARTIR: String = "Repartir"

/** El título de la hoja de elegir el grupo. */
const val EN_QUE_GRUPO: String = "¿Con qué grupo lo repartes?"

/**
 * **La acción «Repartir con Caro» del aviso**, o `null` si no aplica: [evento] tiene que ser un gasto
 * que se puede repartir ([sePuedeRepartir]: en pesos, ni traspaso ni pago de un saldo), que todavía no
 * esté en un grupo, y el dueño tiene que tener al menos un grupo. Con uno, la acción lo hace; con
 * varios, «Repartir» abre la elección.
 */
fun accionDeRepartir(
    evento: FinancialEvent,
    espacios: List<Espacio>,
    aviso: AvisoDeDeshacer,
    repo: WalletRepository = Repositories.wallets,
): AvisoDeDeshacer.Accion? {
    if (!sePuedeRepartir(evento) || evento.espacioId != null || espacios.isEmpty()) return null
    suspend fun repartirEn(espacio: Espacio) {
        val gasto = try {
            repo.repartirMovimiento(espacio.id, evento.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw FalloDelAviso(motivoDelRepartoPendiente(e))
        }
        val tuParte = tuParteDe(gasto).takeIf { gasto.partes.isNotEmpty() }
            ?: (partesDelGasto(evento.amount, espacio, MIEMBRO_YO)[MIEMBRO_YO] ?: 0L)
        aviso.mostrar(textoDeRepartido(espacio, tuParte)) { repo.quitarDelEspacio(espacio.id, evento.id) }
    }
    val unico = espacios.singleOrNull()
    if (unico != null) return AvisoDeDeshacer.Accion(rotuloDeRepartirCon(unico), hacer = { repartirEn(unico) })
    return AvisoDeDeshacer.Accion(
        rotulo = REPARTIR,
        opciones = espacios.map { e -> AvisoDeDeshacer.Opcion(e.nombre, detalle = quienesComparten(e)) { repartirEn(e) } },
        tituloDeLaEleccion = EN_QUE_GRUPO,
    )
}

/**
 * **El aviso de lo que se acaba de anotar** ([evento]): el texto, «Deshacer» si hay un camino limpio
 * ([deshacer]; `null` = sin el botón) y «Repartir con …» si aplica ([accionDeRepartir]).
 */
fun AvisoDeDeshacer.mostrarLoAnotado(
    evento: FinancialEvent,
    espacios: List<Espacio>,
    deshacer: (suspend () -> Unit)?,
    repo: WalletRepository = Repositories.wallets,
    /** «Es este»: el movimiento ya estaba («Ya estaba anotado · …»), no se anotó ahora. */
    yaEstaba: Boolean = false,
) {
    mostrar(
        if (yaEstaba) textoDeLoQueYaEstaba(evento) else textoDeLoAnotado(evento),
        accion = accionDeRepartir(evento, espacios, this, repo),
        deshacer = deshacer,
    )
}

/**
 * **«Deshacer» de lo que se acaba de anotar**, por los caminos que ya existían y en este orden:
 *
 * 1. Los [avisos] del banco que quedaron confirmados con el movimiento vuelven a «Por revisar»
 *    (`desvincularAvisos`). Primero, porque pide red: si falla, no se tocó nada.
 * 2. El movimiento [eventoId] se anula (`voidEvent`, la anulación de la vuelta 67: con un traspaso o
 *    una cuota se anulan las dos patas). En el teléfono va primero a la base local, como siempre.
 *
 * Un aviso que ya no estaba confirmado (el confirmar falló antes, o otro aparato lo deshizo) no es un
 * error: el server contesta 404 y se sigue.
 */
suspend fun deshacerLoAnotado(
    eventoId: String?,
    avisos: List<String>,
    repo: WalletRepository = Repositories.wallets,
) {
    if (avisos.isNotEmpty()) {
        try {
            repo.desvincularAvisos(avisos)
        } catch (e: com.jvillada.movi.shared.repository.ApiException) {
            if (e.status != 404) throw e
        }
    }
    if (eventoId != null) repo.voidEvent(eventoId)
}

/**
 * Los grupos del dueño, para ofrecer «Repartir» en el aviso. Vacía mientras no contestan, y si fallan:
 * sin grupos leídos, el aviso no ofrece repartir (no inventa a nadie).
 */
@Composable
fun rememberEspaciosParaRepartir(): List<Espacio> {
    var espacios by remember { mutableStateOf(emptyList<Espacio>()) }
    LaunchedEffect(Unit) {
        intentar { Repositories.wallets.getEspacios() }.onSuccess { espacios = it }
    }
    return espacios
}
