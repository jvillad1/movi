package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/*
 * # Vuelta 108 · «Gasto de un día normal», en la caja de Pagos
 *
 * La proyección del héroe y la caja restan cada día un gasto del día a día. Movi lo estima (sin compras con
 * tarjeta ni gastos de una sola vez, ver `gastoDelDiaADia` en `:core`), pero el dueño puede escribir el suyo:
 * se guarda en el server (`UserProfile.gastoDeUnDiaNormal`, `PUT /api/users/me`) y reemplaza el estimado en
 * la caja, en el héroe del Inicio y en Movi AI. Opcional: «El de Movi» vuelve al estimado (guarda 0).
 */

/** El título de la hoja y el rótulo de la fila de la caja. */
const val GASTO_DE_UN_DIA_NORMAL: String = "Gasto de un día normal"

/** El tag de la fila del supuesto en la caja. */
const val TAG_FILA_DEL_SUPUESTO: String = "caja-fila-del-supuesto"

/**
 * Lo que dice la fila del supuesto en la caja: «$91.000 por día · lo estima Movi» o «$120.000 por día · lo
 * elegiste tú»; sin nada con qué estimar, que no hay estimado todavía. Pura.
 */
fun textoDelSupuesto(gasto: GastoDelDiaADia): String {
    val porDia = gasto.porDia ?: return "Movi todavía no lo puede estimar: escribe el tuyo"
    return if (gasto.elegido != null) "${formatCOP(porDia)} por día · lo elegiste tú"
    else "${formatCOP(redondeadoAMil(porDia))} por día · lo estima Movi"
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            EncabezadoDeHoja(
                GASTO_DE_UN_DIA_NORMAL,
                subtitulo = "Movi lo resta cada día hasta que te paguen para decirte si te alcanza. No mueve plata.",
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
                        ayuda = "Lo que se te va en un día cualquiera: comida, transporte, salidas. Sin tus pagos fijos.",
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
 * Lo que dice la hoja del estimado de Movi: la cifra y lo que deja fuera. «$91.000 por día, el promedio de
 * tus últimos 2 períodos sin las compras con tarjeta ($3,1M, se pagan cuando pagas la tarjeta) ni 5 gastos
 * de una sola vez ($18,4M; el más grande, Gardenera).» Pura.
 */
fun loQueEstimaMovi(gasto: GastoDelDiaADia): String {
    val estimado = gasto.estimado ?: return "Todavía no hay un período cerrado para estimarlo."
    val periodos = if (gasto.periodos.size == 1) "tu último período" else "tus últimos ${gasto.periodos.size} períodos"
    val fuera = buildList {
        if (gasto.conTarjeta > 0L) add("las compras con tarjeta (${formatMoneyCompact(gasto.conTarjeta)}, se pagan cuando pagas la tarjeta)")
        val deUnaVez = gasto.deUnaVez
        if (deUnaVez.isNotEmpty()) {
            val cuantos = if (deUnaVez.size == 1) "1 gasto de una sola vez" else "${deUnaVez.size} gastos de una sola vez"
            add("$cuantos (${formatMoneyCompact(deUnaVez.sumOf { it.monto })}; el más grande, ${deUnaVez.first().nombre})")
        }
    }
    val sin = if (fuera.isEmpty()) "" else " sin " + fuera.joinToString(" ni ")
    return "${formatCOP(redondeadoAMil(estimado))} por día, el promedio de $periodos$sin."
}
