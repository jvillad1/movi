package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.problemaDelGastoDeUnDiaNormal
import com.jvillada.movi.shared.model.redondeadoAMil
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.GrupoDeCampos
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.desplazableConElTeclado

/*
 * # Vuelta 108 · «Gasto de un día normal», en la caja de Pagos
 *
 * La proyección del héroe y la caja restan cada día un gasto del día a día, y son dos supuestos (ver
 * `GastoDelDiaADia` en `:core`): el del héroe es **todo** lo que se gasta en un día normal, con tarjeta o en
 * efectivo; el de la caja, solo lo que sale en efectivo (lo de la tarjeta sale cuando pagas la tarjeta). Los
 * dos sin gastos de una sola vez. El dueño puede escribir el suyo, que es TODO lo que gasta: se guarda en el
 * server (`UserProfile.gastoDeUnDiaNormal`, `PUT /api/users/me`); el héroe lo usa tal cual y la caja solo su
 * parte en efectivo (la de su historial; sin historial, todo). Opcional: «El de Movi» vuelve al estimado.
 */

/** El título de la hoja y el rótulo de la fila de la caja. */
const val GASTO_DE_UN_DIA_NORMAL: String = "Gasto de un día normal"

/** El tag de la fila del supuesto en la caja. */
const val TAG_FILA_DEL_SUPUESTO: String = "caja-fila-del-supuesto"

/**
 * Lo que dice la fila del supuesto en la caja: el gasto de un día normal (todo, el del héroe) y la parte en
 * efectivo que resta la caja. «$143.000 por día · $91.000 en efectivo · lo estima Movi» o «$120.000 por día ·
 * $90.000 en efectivo · lo elegiste tú»; sin nada con qué estimar, que no hay estimado todavía. Pura.
 */
fun textoDelSupuesto(gasto: GastoDelDiaADia): String {
    val todo = gasto.delHeroe ?: return "Movi todavía no lo puede estimar: escribe el tuyo"
    val enEfectivo = gasto.porDia
    val elegido = gasto.elegido != null
    fun cifra(monto: Long) = formatCOP(if (elegido) monto else redondeadoAMil(monto))
    val efectivo = if (enEfectivo != null && enEfectivo != todo) " · ${cifra(enEfectivo)} en efectivo" else ""
    return "${cifra(todo)} por día$efectivo · " + if (elegido) "lo elegiste tú" else "lo estima Movi"
}

/** Guardar el gasto de un día normal: `null` vuelve al estimado de Movi (el server guarda 0 como «ninguno»). */
internal suspend fun guardarElGastoDeUnDiaNormal(valor: Long?) =
    Repositories.wallets.updateUserProfile(UpdateProfileRequest(gastoDeUnDiaNormal = valor ?: 0L))

/**
 * **Elegir el gasto de un día normal**: el de Movi o uno propio. «Guardar» se apaga y dice por qué
 * ([problemaDelGastoDeUnDiaNormal], la misma regla del server).
 */
@Composable
fun GastoDeUnDiaNormalSheet(
    actual: GastoDelDiaADia,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    var propio by remember(actual) { mutableStateOf(actual.elegido != null) }
    var monto by remember(actual) { mutableStateOf(actual.elegido) }
    val problema = when {
        !propio -> null
        monto == null || monto == 0L -> "Escribe cuánto gastas en un día normal"
        else -> problemaDelGastoDeUnDiaNormal(monto!!)
    }
    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.desplazableConElTeclado().weight(1f, fill = false)) {
            EncabezadoDeHoja(
                GASTO_DE_UN_DIA_NORMAL,
                subtitulo = "Todo lo que gastas en un día cualquiera, con tarjeta o en efectivo. No mueve plata.",
            )
            Spacer(Modifier.height(Medida.entreSecciones))
            GrupoDeCampos("Cuál usar") {
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    PildoraDeFiltro("El de Movi", seleccionada = !propio, onClick = { if (!saving) propio = false })
                    PildoraDeFiltro("El mío", seleccionada = propio, onClick = { if (!saving) propio = true })
                }
                if (propio) {
                    CampoDeMonto(
                        monto, { monto = it },
                        rotulo = "Por día",
                        ejemplo = "90.000",
                        ayuda = "Con tarjeta y en efectivo: comida, transporte, salidas. Sin tus pagos fijos. " +
                            "El Inicio lo resta entero; la caja de Pagos, solo ${parteEnEfectivo(actual)}.",
                        habilitado = !saving,
                    )
                } else {
                    Text(
                        loQueEstimaMovi(actual),
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }
            Spacer(Modifier.height(Espacio.l))
            PieDeHoja(
                texto = "Guardar",
                onClick = { if (problema == null) onSave(if (propio) monto else null) },
                habilitado = problema == null && !saving,
                razon = problema,
                cargando = saving,
                error = error,
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

/**
 * «la parte que pagas en efectivo (el 63 %)», o «todo» sin historial con qué medirla. Pura.
 */
fun parteEnEfectivo(gasto: GastoDelDiaADia): String {
    val sinHistorial = gasto.periodos.sumOf { it.total + it.conTarjeta } <= 0L
    if (sinHistorial) return "todo, porque todavía no hay historial para saber cuánto pagas con tarjeta"
    return "la parte que pagas en efectivo (el ${kotlin.math.round(gasto.proporcionEnEfectivo * 100).toInt()} % en tu historial)"
}

/**
 * Lo que dice la hoja del estimado de Movi: la cifra y lo que deja fuera. «$91.000 por día, el promedio de
 * tus últimos 2 períodos sin las compras con tarjeta ($3,1M, se pagan cuando pagas la tarjeta) ni 5 gastos
 * de una sola vez ($18,4M; el más grande, Gardenera).» Pura.
 */
fun loQueEstimaMovi(gasto: GastoDelDiaADia): String {
    val todo = gasto.estimadoConTarjetas ?: gasto.estimado ?: return "Todavía no hay un período cerrado para estimarlo."
    val periodos = if (gasto.periodos.size == 1) "tu último período" else "tus últimos ${gasto.periodos.size} períodos"
    val deUnaVez = gasto.deUnaVez
    val sin = if (deUnaVez.isEmpty()) "" else {
        val cuantos = if (deUnaVez.size == 1) "1 gasto de una sola vez" else "${deUnaVez.size} gastos de una sola vez"
        " sin $cuantos (${formatMoneyCompact(deUnaVez.sumOf { it.monto })}; el más grande, ${deUnaVez.first().nombre})"
    }
    val efectivo = gasto.estimado?.takeIf { gasto.conTarjeta > 0L }
        ?.let { " La caja de Pagos resta solo lo que sale en efectivo: ${formatCOP(redondeadoAMil(it))} por día." }.orEmpty()
    return "${formatCOP(redondeadoAMil(todo))} por día con tarjeta y en efectivo, el promedio de $periodos$sin.$efectivo"
}
