package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.jvillada.movi.observabilidad.ToqueDeUso
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.alAlternar
import com.jvillada.movi.ui.components.ds.anilloDeFoco

/*
 * # Vuelta 124 · «¿Ya pagaste el de este período?» fuera de la hoja
 *
 * La vuelta 121 la puso en la hoja de un pago fijo nuevo (`CreateRecurringRuleSheet`), como una [Fila] en
 * su superficie. La guía de primer uso (el formulario de «Lo que pagas cada mes») y la tarjeta de lo que
 * propone Movi AI la necesitan **dentro** de un bloque que ya tiene su margen, donde una [Fila] sumaría el
 * suyo: por eso acá va como un renglón sin margen propio, con las MISMAS palabras de la hoja y la misma
 * [Casilla] («Sí» por defecto). Como en la hoja, **se toca el renglón entero** (revisión de la 124), no
 * solo la casilla. Cuándo se pregunta lo decide siempre `sePreguntaSiYaPagasteElDeEstePeriodo` (`:core`),
 * y la respuesta viaja en `RecurringRule.yaPagoElDeEstePeriodo`.
 *
 * Todas las formas del renglón ([PreguntaDeYaPagaste], [RenglonDeLoQueEsperaMovi] y
 * [EsqueletoDeYaPagaste]) miden lo mismo —dos renglones de texto y al menos el toque de 48—: la tarjeta de
 * Movi AI reserva ese alto mientras lee el perfil y no salta al saber cuál va.
 */

/**
 * El renglón de la pregunta: el título y, debajo, qué hace Movi con la respuesta; la [Casilla] al final
 * dice el estado. **El toque es el renglón entero** (con el estado de casilla para el lector). [tag] va en
 * el renglón.
 */
@Composable
fun PreguntaDeYaPagaste(
    marcada: Boolean,
    onCambiar: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    habilitada: Boolean = true,
) {
    RenglonDeYaPagaste(
        modifier = modifier
            .anilloDeFoco(RoundedCornerShape(Radio.s))
            .alAlternar(
                valor = marcada,
                onCambiar = onCambiar,
                toque = ToqueDeUso("ya-pagaste", modificador = modifier),
                habilitado = habilitada,
                rol = Role.Checkbox,
            )
            .testTag(tag),
    ) {
        Textos(YA_PAGASTE_EL_DE_ESTE_PERIODO, if (marcada) LO_ESPERA_DESDE_EL_PROXIMO_PERIODO else LO_ESPERA_EN_ESTE_PERIODO)
        Spacer(Modifier.width(Espacio.xs))
        Casilla(marcada = marcada)
    }
}

/**
 * Lo que dice el lugar de la pregunta cuando no hay nada que preguntar (la fecha de este período todavía
 * no llega): «Movi lo espera en este período». Del mismo alto que la pregunta. Sin toque.
 */
@Composable
fun RenglonDeLoQueEsperaMovi(tag: String, modifier: Modifier = Modifier) {
    RenglonDeYaPagaste(modifier = modifier.testTag(tag)) {
        Textos(LO_ESPERA_EN_ESTE_PERIODO, SU_FECHA_TODAVIA_NO_LLEGA)
    }
}

/** El lugar de la pregunta mientras no se sabe cuál va (el perfil se está leyendo). Del mismo alto. */
@Composable
fun EsqueletoDeYaPagaste(tag: String, modifier: Modifier = Modifier) {
    RenglonDeYaPagaste(modifier = modifier.testTag(tag)) {
        Column(modifier = Modifier.weight(1f)) {
            LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.cuerpo)
            LineaEsqueleto(fraccionDelAncho = 0.5f, estilo = Movi.textos.apoyo)
        }
    }
}

/** El subtítulo de [RenglonDeLoQueEsperaMovi]. */
const val SU_FECHA_TODAVIA_NO_LLEGA: String = "Su fecha de este período todavía no llega"

@Composable
private fun RenglonDeYaPagaste(modifier: Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Medida.objetivoTactil),
        verticalAlignment = Alignment.CenterVertically,
        content = contenido,
    )
}

@Composable
private fun RowScope.Textos(titulo: String, subtitulo: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(titulo, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Text(subtitulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}
