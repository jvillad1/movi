package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
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
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MotivoDeReparto
import com.jvillada.movi.shared.model.PropuestaDeRepartir
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.huellaDeNoEsCompartido
import com.jvillada.movi.shared.model.huellaDeNuncaRepartir
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Interruptor
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.AnomaliasDescartadasEnLaSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * # «Movi propone repartir» en pantalla (vuelta 97)
 *
 * La regla es de `:core` (`propuestasDeRepartir`) y la corre el server. Acá van las palabras y las tres
 * acciones, las mismas en «Movi propone hoy» y en Por revisar:
 *
 * - **Repartir** abre la hoja de siempre ([HojaDeRepartirElGasto]) llena con el reparto de la última vez.
 *   Nunca reparte sola.
 * - **No es compartido** descarta ese movimiento ([huellaDeNoEsCompartido]).
 * - **Nunca para este comercio** (o «este recurrente») descarta el comercio o el recurrente para
 *   siempre ([huellaDeNuncaRepartir]).
 *
 * Los dos descartes se van en el acto ([AnomaliasDescartadasEnLaSesion]) y se guardan en
 * `anomalias_descartadas`; si el server no lo guarda, la tarjeta vuelve.
 */

/** Tags para las pruebas. */
const val TAG_PROPUESTA_DE_REPARTIR: String = "espacios:propuesta-de-repartir"
const val TAG_SE_REPARTE_EN: String = "espacios:se-reparte-en"

/** El que descarta el movimiento. */
const val NO_ES_COMPARTIDO: String = "No es compartido"

/** «Nunca para este comercio» / «Nunca para este recurrente». */
fun botonDeNuncaRepartir(p: PropuestaDeRepartir): String =
    if (p.motivo == MotivoDeReparto.RECURRENTE) "Nunca para este recurrente" else "Nunca para este comercio"

/** «con Caro», «con Caro y Andrés». */
private fun conQuien(espacio: Espacio): String {
    val nombres = idsDeMiembros(espacio).filter { it != MIEMBRO_YO }.map { nombreEnElEspacio(espacio, it) }
    return when (nombres.size) {
        0 -> ""
        1 -> "con ${nombres.single()}"
        else -> "con ${nombres.dropLast(1).joinToString(", ")} y ${nombres.last()}"
    }
}

/** «¿Repartes Arriendo $2.400.000 con Caro?». */
fun tituloDeLaPropuestaDeRepartir(p: PropuestaDeRepartir): String =
    "¿Repartes ${p.descripcion.trim().ifBlank { "este gasto" }} ${formatCOP(p.monto)} ${conQuien(p.espacio)}?".replace("  ", " ")

/** «Casa con Caro, 50/50», y «como la última vez» si viene el reparto de antes. */
fun cuerpoDeLaPropuestaDeRepartir(p: PropuestaDeRepartir): String =
    "${p.espacio.nombre}, ${textoCortoDelReparto(p.reparto, p.espacio)}" + if (p.reparto != null) ", como la última vez" else ""

/** Las huellas que esconden [p]: «No es compartido» y «Nunca». */
fun huellasDeLaPropuestaDeRepartir(p: PropuestaDeRepartir): List<String> =
    listOf(huellaDeNoEsCompartido(p.eventoId), huellaDeNuncaRepartir(p.clave))

/** [propuestas] sin las que se descartaron o repartieron en esta sesión (la tarjeta se va en el acto). */
fun propuestasDeRepartirVisibles(propuestas: List<PropuestaDeRepartir>, descartadas: Set<String>): List<PropuestaDeRepartir> =
    propuestas.filterNot { p -> huellasDeLaPropuestaDeRepartir(p).any { it in descartadas } }

/** «No es compartido»: se va ya y se guarda; si el server no lo guarda, vuelve. */
fun noEsCompartido(p: PropuestaDeRepartir) {
    val huella = huellaDeNoEsCompartido(p.eventoId)
    AnomaliasDescartadasEnLaSesion.descartarYGuardar(huella) { Repositories.wallets.descartarAnomalia(huella) }
}

/** «Nunca para este comercio»: lo mismo, por comercio o recurrente. */
fun nuncaRepartir(p: PropuestaDeRepartir) {
    val huella = huellaDeNuncaRepartir(p.clave)
    AnomaliasDescartadasEnLaSesion.descartarYGuardar(huella) { Repositories.wallets.descartarAnomalia(huella) }
}

/**
 * **La hoja de la propuesta**: [HojaDeRepartirElGasto] con el reparto de la última vez. Al repartir,
 * la tarjeta se va en esta sesión (el server ya no la propone: el movimiento quedó en el grupo).
 */
@Composable
fun HojaDeLaPropuestaDeRepartir(p: PropuestaDeRepartir, onDismiss: () -> Unit, onRepartido: () -> Unit = {}) {
    HojaDeRepartirElGasto(
        espacio = p.espacio,
        monto = p.monto,
        concepto = p.descripcion,
        repartoInicial = p.reparto,
        onDismiss = onDismiss,
        onRepartir = { reparto ->
            Repositories.wallets.repartirMovimiento(p.espacio.id, p.eventoId, reparto)
            AnomaliasDescartadasEnLaSesion.descartar(huellaDeNoEsCompartido(p.eventoId))
            onRepartido()
            onDismiss()
        },
    )
}

/** **La propuesta en Por revisar**: la misma tarjeta y las mismas tres acciones que en el Inicio. */
@Composable
fun TarjetaDeRepartir(p: PropuestaDeRepartir, onHecho: () -> Unit, modifier: Modifier = Modifier) {
    var repartiendo by remember(p.eventoId) { mutableStateOf(false) }
    Column(modifier = modifier.testTag(TAG_PROPUESTA_DE_REPARTIR)) {
        TarjetaDePropuesta(
            icono = Icons.Rounded.Groups,
            titulo = tituloDeLaPropuestaDeRepartir(p),
            cuerpo = cuerpoDeLaPropuestaDeRepartir(p),
            accion = REPARTIR,
            onAccion = { repartiendo = true },
            accionSecundaria = NO_ES_COMPARTIDO,
            onAccionSecundaria = { noEsCompartido(p); onHecho() },
            accionTerciaria = botonDeNuncaRepartir(p),
            onAccionTerciaria = { nuncaRepartir(p); onHecho() },
        )
    }
    if (repartiendo) {
        HojaDeLaPropuestaDeRepartir(p, onDismiss = { repartiendo = false }, onRepartido = onHecho)
    }
}

/**
 * **«Se reparte en Casa con Caro»**, en el detalle de un recurrente de gasto (vuelta 97): un interruptor
 * por grupo. Encendido, Movi propone repartir cada cobro de este recurrente; nunca lo reparte solo.
 * Un recurrente va a un grupo o a ninguno: encender otro lo mueve. Se guarda al tocarlo (como «Editar
 * inicio»), aparte del «Guardar» de la regla. Sin grupos, o en un ingreso, no se pinta.
 */
@Composable
fun SeRepartirEnUnGrupo(regla: RecurringRule, modifier: Modifier = Modifier) {
    if (regla.type != TransactionType.EXPENSE) return
    val coroutine = rememberCoroutineScope()
    var espacios by remember(regla.id) { mutableStateOf<List<Espacio>>(emptyList()) }
    var enCual by remember(regla.id) { mutableStateOf<String?>(null) }
    var trabajando by remember(regla.id) { mutableStateOf(false) }
    var error by remember(regla.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(regla.id) {
        espacios = runCatching { Repositories.wallets.getEspacios() }.getOrDefault(emptyList())
        enCual = runCatching { Repositories.wallets.getRecurrentesDelHogar() }.getOrDefault(emptyList())
            .firstOrNull { it.reglaId == regla.id }?.espacioId
    }
    if (espacios.isEmpty()) return

    fun cambiar(espacio: Espacio, encender: Boolean) {
        if (trabajando) return
        trabajando = true
        error = null
        val antes = enCual
        enCual = if (encender) espacio.id else null
        coroutine.launch {
            try {
                if (encender) Repositories.wallets.marcarRecurrenteDelHogar(espacio.id, regla.id)
                else Repositories.wallets.quitarRecurrenteDelHogar(espacio.id, regla.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                enCual = antes
                error = mensajeDelEspacio(e)
            } finally {
                trabajando = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_SE_REPARTE_EN)) {
        Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista, modifier = Modifier.fillMaxWidth()) {
            espacios.forEachIndexed { i, espacio ->
                if (i > 0) Separador()
                val encendido = enCual == espacio.id
                Fila(
                    titulo = "Se reparte en ${espacio.nombre}",
                    final = {
                        Interruptor(
                            encendido = encendido,
                            onCambio = { cambiar(espacio, it) },
                            habilitado = !trabajando,
                        )
                    },
                    onClick = { cambiar(espacio, !encendido) },
                    chevron = false,
                )
            }
        }
        Spacer(Modifier.height(EspacioDeDiseno.xxs))
        Text(
            "Movi te propone repartir cada cobro; nunca lo reparte solo.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
}
