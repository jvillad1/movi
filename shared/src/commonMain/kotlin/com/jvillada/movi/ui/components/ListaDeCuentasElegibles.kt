package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CuentasDelPicker
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextMute

/**
 * **La lista de cuentas para elegir con el dedo, en las pantallas de lo que llegó del banco.**
 *
 * Las dos pantallas que confirman algo que la app leyó sola —el SMS y el extracto— mostraban la
 * cuenta **de solo lectura**. Eso convertía un valor adivinado en un hecho: si Movi le erraba, la
 * única salida era no confirmar. Y desde que el respaldo dejó de ser `accounts.firstOrNull()`
 * (ver [resolverCuentaDelBanco]) el selector deja de ser un lujo: sin candidata no hay cuenta
 * puesta, así que este es el único camino que le queda al dueño hacia adelante.
 *
 * Es la parte que **carga la regla** —la lista partida por `cuentasPara`, el pie de «Ver todas»,
 * el caso de que arriba no quede ninguna—, y solo esa parte: la fila que la abre la dibuja cada
 * pantalla en su propio lenguaje (un renglón con su tilde en el SMS, el chip de «Destino» en el
 * extracto). Compartir lo que decide y dejar libre lo que se ve es justo lo contrario de lo que
 * pasaba antes, cuando lo copiado era la decisión.
 *
 * `Column` y no `LazyColumn` a propósito: en el SMS esto se dibuja **dentro de un item** de una
 * `LazyColumn`, y una lista perezosa anidada en otra de la misma dirección revienta por alto
 * infinito. Las cuentas de una persona se cuentan con los dedos; no hay nada que reciclar.
 *
 * @param cuentas ya partidas por `cuentasPara`, con `conservar` puesto en la cuenta elegida — o la
 *   que el dueño sacó del «Ver todas» desaparecería al reabrir.
 * @param uso para qué se elige; se lo pasa a [VerTodasLasCuentas], que explica con eso por qué las
 *   otras no estaban arriba.
 */
@Composable
fun ListaDeCuentasElegibles(
    cuentas: CuentasDelPicker,
    uso: UsoDeCuenta,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Se pliega solo, y con la llave puesta, por lo mismo que en la hoja de «Agregar»: acá las
    // cuentas llegan por red DESPUÉS de que la pantalla se dibuja, así que sin la llave el valor
    // inicial se congelaría con la lista vacía y todo aparecería desplegado — con el crédito del
    // vehículo en el medio, que es la pantalla que esto vino a arreglar.
    var verTodas by remember(cuentas.principales.isEmpty()) {
        mutableStateOf(cuentas.principales.isEmpty())
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (cuentas.vacio) {
            Text(
                "No tienes cuentas todavía.",
                fontSize = 13.5.sp,
                color = MinTextMute,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            val visibles = if (verTodas) cuentas.todas else cuentas.principales
            visibles.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(account.id) }
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        account.name,
                        fontSize = 14.5.sp,
                        color = MinText,
                        fontWeight = if (account.id == selectedId) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (account.id == selectedId) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MinText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (cuentas.hayOtras) {
                VerTodasLasCuentas(
                    expandido = verTodas,
                    cuantas = cuentas.otras.size,
                    uso = uso,
                    onToggle = { verTodas = !verTodas },
                )
            }
        }
    }
}
