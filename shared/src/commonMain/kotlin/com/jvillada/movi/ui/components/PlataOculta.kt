package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.theme.Movi

/**
 * # «Ocultar los saldos», donde se pinta
 *
 * El estado vive en [SaldosOcultosStore]. Acá está todo lo que lo pinta:
 *
 * - **[PLATA_OCULTA]**, lo único que se ve en lugar de una cifra: en pesos, en dólares y en la
 *   forma compacta. Sin signo a propósito: un «−» suelto ya dice que la cuenta va en rojo.
 * - Las funciones de formato de siempre ([formatCOP], [formatMoney], [formatMoneyCompact],
 *   [signedMoney]) devuelven [PLATA_OCULTA] con los saldos ocultos; sus variantes `…SinOcultar`
 *   son para lo que no se pinta.
 * - **[conCifrasOcultas]**, para el texto que llega ya armado de `:core` o del server (el cierre del
 *   período, lo que se sale de lo normal, el débito automático): ahí no hay formato que tocar, así
 *   que se tapan las cifras del texto al pintarlo.
 * - **[LocalSaldosOcultos]**: el mismo estado para una composición. Lo pone `MoviTheme`, así que
 *   lo ve toda pantalla (y toda prueba que monte una).
 * - **[OjoDeLosSaldos]**, el botón.
 *
 * **Qué no se oculta**: porcentajes, fechas, conteos, lo que el dueño está escribiendo (el teclado
 * de Agregar y los campos de edición tienen su propio formato, `formatAmountKeypadDisplay`) y el
 * chat de Movi AI, que es texto que él pidió.
 */
const val PLATA_OCULTA: String = "$•••••"

/** ¿Están ocultos los saldos? Lo provee `MoviTheme` desde [SaldosOcultosStore]. */
val LocalSaldosOcultos = compositionLocalOf { false }

/**
 * Una cifra de plata dentro de un texto ya armado: «$1.234», «−$12.345.678», «US$19,99», «$1,7M»,
 * «USD 20» o «20 USD». Lo mismo que escriben los formatos de la UI y los de `:core`.
 */
private val CIFRA_DE_PLATA = Regex(
    // Los dígitos van de a grupos («22.500.000», «1,7»): una coma o un punto que cierra la frase
    // («entró $22.500.000, salió…») no es parte de la cifra.
    "[−+-]?(?:US|COP\\s?)?\\$\\s?\\d+(?:[.,]\\d+)*M?|\\b(?:USD|COP|EUR)\\s\\d+(?:[.,]\\d+)*|\\d+(?:[.,]\\d+)*\\s(?:USD|COP|EUR)\\b",
)

/** [texto] con cada cifra de plata cambiada por [PLATA_OCULTA]. Pura, para probarla. */
fun ocultarCifras(texto: String): String = CIFRA_DE_PLATA.replace(texto) { PLATA_OCULTA }

/**
 * **Un texto que llega armado, como se pinta**: tal cual, o con las cifras tapadas si los saldos
 * están ocultos. Para lo que viene de `:core` o del server; lo que la UI formatea con [formatCOP] y
 * compañía ya sale tapado.
 */
@Composable
fun conCifrasOcultas(texto: String): String =
    if (LocalSaldosOcultos.current) ocultarCifras(texto) else texto

/** La etiqueta del ojo, que dice lo que hace tocarlo. */
const val ROTULO_OCULTAR_LOS_SALDOS: String = "Ocultar los saldos"
const val ROTULO_MOSTRAR_LOS_SALDOS: String = "Mostrar los saldos"

const val TAG_OJO_DE_LOS_SALDOS: String = "ojo-de-los-saldos"

/**
 * **El ojo**: alterna «Ocultar los saldos», con la vibración de un interruptor ([Vibrador.alternar]).
 * Ojo abierto con las cifras a la vista (tocarlo las oculta), ojo tachado con las cifras ocultas.
 *
 * Es su propio nodo con su propio `clickable`, como el chevron del héroe: la tarjeta de alrededor
 * sigue llevando a donde lleva.
 */
@Composable
fun OjoDeLosSaldos(modifier: Modifier = Modifier) {
    val ocultos = LocalSaldosOcultos.current
    val vibrador = rememberVibrador()
    val rotulo = if (ocultos) ROTULO_MOSTRAR_LOS_SALDOS else ROTULO_OCULTAR_LOS_SALDOS
    Box(
        modifier = modifier
            // 24 dp, como el chevron del héroe: va en la línea del rótulo y no puede hacerla más
            // alta (la cifra de abajo no salta, ni con datos ni con esqueleto).
            .size(24.dp)
            .testTag(TAG_OJO_DE_LOS_SALDOS)
            .clickable(role = Role.Button, onClickLabel = rotulo) {
                SaldosOcultosStore.alternar()
                vibrador.alternar(prendido = SaldosOcultosStore.ocultos)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (ocultos) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = rotulo,
            tint = Movi.colores.textoMedio,
            modifier = Modifier.size(20.dp),
        )
    }
}
