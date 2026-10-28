package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlin.math.abs

/** La superficie de la cascada: el mismo tag en la hoja de Hoy y en la tarjeta del Disponible. */
const val TAG_CASCADA_DEL_DISPONIBLE: String = "cascada-del-disponible"

/** El renglón del total («Te quedan»): para medir que su cifra es la del héroe. */
const val TAG_TOTAL_DE_LA_CASCADA: String = "total-de-la-cascada"

/** El título de la hoja y de la acción de la tarjeta. */
const val DE_DONDE_SALE: String = "¿De dónde sale?"

/** El enlace de abajo de la hoja: lo que antes abría tocar el héroe. */
const val VER_TUS_PERIODOS: String = "Ver tus períodos"

/**
 * **La cascada de «Te quedan», pintada** (vuelta de diseño 28): una [Fila] por renglón con su [Cifra]
 * y su signo, un [Separador] y «Disponible del período» —el nombre que Plan le da a esa cifra—, lo
 * gastado, otro [Separador] y el total con el rótulo del héroe. Una [Superficie] en la capa 2 con
 * relleno de lista: va dentro de una hoja o de la tarjeta del Disponible, las dos en la capa 1.
 *
 * El único componente de los dos lugares (el héroe de Hoy y «¿De dónde sale?» de la tarjeta del
 * Disponible). Lo que dice sale de [cascadaDelDisponible], pura y probada.
 */
@Composable
internal fun CascadaDelDisponibleEnPantalla(disponible: DisponibleDelPeriodo, modifier: Modifier = Modifier) {
    val cascada = cascadaDelDisponible(disponible)
    Superficie(
        modifier = modifier.fillMaxWidth().testTag(TAG_CASCADA_DEL_DISPONIBLE),
        nivel = NivelDeSuperficie.Capa2,
        relleno = RellenoDeSuperficie.Lista,
    ) {
        cascada.deDondeSale.forEach { RenglonEnPantalla(it) }
        Separador(SangriaDeSeparador.Margen)
        Fila(
            titulo = ROTULO_DISPONIBLE_DEL_PERIODO,
            final = {
                // El tono de la tarjeta de Plan: en rojo solo sin margen.
                Cifra(cascada.disponible, tono = if (cascada.disponible > 0L) TonoDeCifra.Neutro else TonoDeCifra.Alerta)
            },
        )
        cascada.despues.forEach { RenglonEnPantalla(it) }
        Separador(SangriaDeSeparador.Margen)
        Fila(
            titulo = cascada.rotuloDelTotal,
            modifier = Modifier.testTag(TAG_TOTAL_DE_LA_CASCADA),
            // Sin signo, como el héroe: el rótulo ya dice de qué lado está.
            final = { Cifra(abs(cascada.total), tono = if (cascada.enRojo) TonoDeCifra.Alerta else TonoDeCifra.Neutro) },
        )
    }
}

@Composable
private fun RenglonEnPantalla(renglon: RenglonDeLaCascada) {
    Fila(
        titulo = renglon.rotulo,
        subtitulo = renglon.detalle,
        final = {
            Cifra(
                renglon.monto,
                tono = when {
                    renglon.apagado -> TonoDeCifra.Apagado
                    renglon.suma -> TonoDeCifra.Ingreso
                    else -> TonoDeCifra.Neutro
                },
            )
        },
    )
}

/**
 * **La hoja que abre tocar el héroe de Hoy** (vuelta de diseño 28): «¿De dónde sale?» con la cascada
 * ([CascadaDelDisponibleEnPantalla]) y, abajo, «Ver tus períodos», que es lo que antes abría el toque.
 * Así la cifra se explica sin salir de la pantalla.
 */
@Composable
internal fun HojaDeDondeSale(
    disponible: DisponibleDelPeriodo,
    onDismiss: () -> Unit,
    onVerTusPeriodos: () -> Unit,
) {
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            EncabezadoDeHoja(
                DE_DONDE_SALE,
                subtitulo = "Cómo se llega a lo que te queda para gastar hasta el cierre.",
                modifier = Modifier.padding(top = Espacio.xxs, bottom = Espacio.m),
            )
            CascadaDelDisponibleEnPantalla(disponible)
            Spacer(Modifier.height(Espacio.s))
            Boton(
                VER_TUS_PERIODOS,
                onClick = onVerTusPeriodos,
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
