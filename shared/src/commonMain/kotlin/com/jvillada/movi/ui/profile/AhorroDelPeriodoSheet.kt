package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.jvillada.movi.shared.model.AhorroDelPeriodo
import com.jvillada.movi.shared.model.TipoDeAhorro
import com.jvillada.movi.shared.model.problemaDelAhorro
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import com.jvillada.movi.ui.components.formatCOP

/*
 * # «Ahorro de cada período», en Ajustes → Tu plata
 *
 * El dueño (27-oct-2026): *«en Ajustes eliges cuánto apartar cada período (un monto o un %). "Te
 * quedan" es lo libre después de fijos y ese ahorro»*. La cuenta vive en `:core`
 * (`AhorroDelPeriodo.kt`, `PlataDelDisponible.ahorroDelPeriodo`); acá están la frase de la fila y la
 * hoja que lo elige.
 */

/** El título de la fila de Ajustes y de la hoja. */
const val AHORRO_DE_CADA_PERIODO: String = "Ahorro de cada período"

/** Lo que dice la fila cuando no aparta nada. */
const val NO_APARTAS_NADA: String = "No apartas nada"

/** Las tres opciones de la hoja, en el orden en que se pintan. */
fun opcionDelAhorro(tipo: TipoDeAhorro): String = when (tipo) {
    TipoDeAhorro.NINGUNO -> "Nada"
    TipoDeAhorro.MONTO -> "Un monto"
    TipoDeAhorro.PORCENTAJE -> "Un porcentaje"
}

/**
 * Lo que dice la fila de Ajustes: «No apartas nada», «$500.000» o «20 % de lo que entra». Un monto o
 * un porcentaje en cero es lo mismo que nada (ver [AhorroDelPeriodo.apartaAlgo]).
 */
fun textoDelAhorro(ahorro: AhorroDelPeriodo): String = when {
    !ahorro.apartaAlgo -> NO_APARTAS_NADA
    ahorro.tipo == TipoDeAhorro.MONTO -> formatCOP(ahorro.valor)
    else -> "${ahorro.valor} % de lo que entra"
}

/**
 * **Elegir el ahorro de cada período**: nada, un monto en pesos o un porcentaje de lo que entra.
 *
 * Con clave en [actual], por lo mismo que `PeriodoSheet`: una hoja abierta antes de que llegara el
 * perfil no puede quedarse con el valor por defecto y escribirlo como si fuera elección del dueño.
 * «Guardar» se apaga y dice por qué ([problemaDelAhorro], la misma regla que valida el server).
 */
@Composable
fun AhorroDelPeriodoSheet(
    actual: AhorroDelPeriodo,
    onDismiss: () -> Unit,
    onSave: (AhorroDelPeriodo) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    var tipo by remember(actual) { mutableStateOf(actual.tipo) }
    var monto by remember(actual) { mutableStateOf(actual.valor.takeIf { actual.tipo == TipoDeAhorro.MONTO && it > 0L }) }
    var porcentaje by remember(actual) {
        mutableStateOf(if (actual.tipo == TipoDeAhorro.PORCENTAJE && actual.valor > 0L) actual.valor.toString() else "")
    }
    val valor: Long? = when (tipo) {
        TipoDeAhorro.NINGUNO -> 0L
        TipoDeAhorro.MONTO -> monto
        TipoDeAhorro.PORCENTAJE -> porcentaje.toLongOrNull()
    }
    val problema = problemaDelAhorro(tipo, valor)

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            EncabezadoDeHoja(
                AHORRO_DE_CADA_PERIODO,
                subtitulo = "Movi lo resta de «Te quedan» antes de decirte cuánto puedes gastar. " +
                    "No mueve plata en tu banco ni cambia «Tu plata».",
                modifier = Modifier.padding(top = Espacio.xxs, bottom = Espacio.m),
            )

            RotuloDeCampo("Cuánto apartas")
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                TipoDeAhorro.entries.forEach { opcion ->
                    PildoraDeFiltro(
                        opcionDelAhorro(opcion),
                        seleccionada = tipo == opcion,
                        onClick = { if (!saving) tipo = opcion },
                    )
                }
            }

            when (tipo) {
                TipoDeAhorro.NINGUNO -> Unit
                TipoDeAhorro.MONTO -> {
                    Spacer(Modifier.height(Espacio.m))
                    CampoDeMonto(
                        monto, { monto = it },
                        rotulo = "Cada período",
                        ejemplo = "500.000",
                        ayuda = "Se resta entero, haya entrado tu sueldo o no.",
                        habilitado = !saving,
                    )
                }
                TipoDeAhorro.PORCENTAJE -> {
                    Spacer(Modifier.height(Espacio.m))
                    Campo(
                        porcentaje, { porcentaje = it.filter(Char::isDigit).take(2) },
                        rotulo = "De lo que entra en el período",
                        ejemplo = "20",
                        sufijo = "%",
                        ayuda = "Sobre lo que ya entró: si no ha entrado nada, todavía no resta.",
                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                        habilitado = !saving,
                    )
                }
            }

            Spacer(Modifier.height(Espacio.l))
            PieDeHoja(
                texto = "Guardar",
                onClick = { if (problema == null && valor != null) onSave(AhorroDelPeriodo(tipo, valor)) },
                habilitado = problema == null && !saving,
                razon = problema,
                cargando = saving,
                error = error,
                secundaria = "Cancelar",
                onSecundaria = onDismiss,
                secundariaHabilitada = !saving,
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
