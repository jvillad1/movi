package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.FrecuenciaDelPeriodo
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.frecuenciaDelPeriodoDe
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.GrupoDeCampos
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.SelectorDeDiaDelMes
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie

/*
 * # Vuelta 105 · «¿Cada cuánto te pagan?»
 *
 * El período puede ser cada mes (lo de siempre) o cada quincena (diseño:
 * `docs/superpowers/specs/2026-11-02-periodo-quincenal-semanal-design.md`). La pregunta vive en
 * Ajustes → «Inicio del período» y en el paso «Tu período» de la guía de primer uso; las dos usan estas
 * piezas para decir lo mismo. «Cada mes» está elegido por defecto (D12).
 */

/** El título del grupo de la elección. */
const val CADA_CUANTO_TE_PAGAN: String = "¿Cada cuánto te pagan?"

/** El título del primer selector de la quincena. */
const val PRIMER_DIA_DE_LA_QUINCENA: String = "El primer pago"

/** El título del segundo selector de la quincena. */
const val SEGUNDO_DIA_DE_LA_QUINCENA: String = "El segundo pago"

const val TAG_FRECUENCIA_MENSUAL: String = "frecuencia-mensual"
const val TAG_FRECUENCIA_QUINCENAL: String = "frecuencia-quincenal"
const val TAG_SEGUNDO_DIA_DE_LA_QUINCENA: String = "segundo-dia-de-la-quincena"

/** Cómo se dice cada frecuencia. */
fun tituloDeLaFrecuencia(frecuencia: FrecuenciaDelPeriodo): String = when (frecuencia) {
    FrecuenciaDelPeriodo.MENSUAL -> "Cada mes"
    FrecuenciaDelPeriodo.QUINCENAL -> "Cada quincena"
}

/** La línea de apoyo de cada frecuencia. */
fun detalleDeLaFrecuencia(frecuencia: FrecuenciaDelPeriodo): String = when (frecuencia) {
    FrecuenciaDelPeriodo.MENSUAL -> "Un período por mes, desde tu día de pago"
    FrecuenciaDelPeriodo.QUINCENAL -> "Dos períodos por mes, uno por cada pago"
}

/** «el 15 y el último día», «el 1 y el 16». Pura. */
fun losDiasDeLaQuincena(a: Int, b: Int): String =
    "el $a y " + if (b == 31) "el último día" else "el $b"

/**
 * Lo que dice la fila de Ajustes con una quincena: «Cada quincena · el 15 y el último día · o el día
 * hábil anterior». Pura; la del mes sigue siendo [textoDelInicioDelMes].
 */
fun textoDeLaQuincena(a: Int, b: Int, regla: SiElCorteCaeEnFinDeSemana): String {
    val base = "Cada quincena · " + losDiasDeLaQuincena(a, b)
    return when (regla) {
        SiElCorteCaeEnFinDeSemana.ESE_DIA -> base
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> "$base · o el día hábil anterior"
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> "$base · o el día hábil siguiente"
    }
}

/** La fila «Inicio del período» de un perfil: el mes de siempre o la quincena. Pura. */
fun textoDelPeriodoDelPerfil(perfil: UserProfile, regla: SiElCorteCaeEnFinDeSemana): String {
    val segundo = perfil.periodSecondDay
    return if (frecuenciaDelPeriodoDe(perfil.periodFrequency) == FrecuenciaDelPeriodo.QUINCENAL && segundo != null) {
        textoDeLaQuincena(perfil.periodCutoffDay, segundo, regla)
    } else {
        textoDelInicioDelMes(perfil.periodCutoffDay, regla)
    }
}

/** El grupo «¿Cada cuánto te pagan?»: Cada mes · Cada quincena, con el check en lo elegido. */
@Composable
fun ElegirCadaCuantoTePagan(
    frecuencia: FrecuenciaDelPeriodo,
    onElegir: (FrecuenciaDelPeriodo) -> Unit,
    habilitado: Boolean = true,
) {
    GrupoDeCampos(CADA_CUANTO_TE_PAGAN) {
        Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
            FrecuenciaDelPeriodo.entries.forEachIndexed { i, opcion ->
                val elegida = opcion == frecuencia
                Fila(
                    titulo = tituloDeLaFrecuencia(opcion),
                    subtitulo = detalleDeLaFrecuencia(opcion),
                    // Con la letra grande en un teléfono angosto el detalle no entra en un renglón.
                    lineasDelSubtitulo = 2,
                    final = {
                        Box(modifier = Modifier.size(Icono.l), contentAlignment = Alignment.Center) {
                            if (elegida) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Elegido",
                                    tint = Movi.colores.texto,
                                    modifier = Modifier.size(Icono.m),
                                )
                            }
                        }
                    },
                    onClick = if (habilitado) ({ onElegir(opcion) }) else null,
                    modifier = Modifier
                        .semantics { selected = elegida }
                        .testTag(if (opcion == FrecuenciaDelPeriodo.MENSUAL) TAG_FRECUENCIA_MENSUAL else TAG_FRECUENCIA_QUINCENAL),
                )
                if (i < FrecuenciaDelPeriodo.entries.lastIndex) Separador(SangriaDeSeparador.Margen)
            }
        }
    }
}

/**
 * Los dos días de la quincena, cada uno con su selector. El 31 es «el último día»: en los meses más
 * cortos se recorta solo (como el corte del mes).
 */
@Composable
fun DiasDeLaQuincena(
    primero: Int,
    segundo: Int,
    onPrimero: (Int) -> Unit,
    onSegundo: (Int) -> Unit,
    habilitado: Boolean = true,
) {
    Column {
        GrupoDeCampos(PRIMER_DIA_DE_LA_QUINCENA) {
            SelectorDeDiaDelMes(dias = 31, elegido = primero, habilitado = habilitado, onElegir = onPrimero)
        }
        Spacer(Modifier.height(Medida.entreSecciones))
        GrupoDeCampos(SEGUNDO_DIA_DE_LA_QUINCENA, descripcion = "El 31 es el último día de cada mes.") {
            Box(Modifier.testTag(TAG_SEGUNDO_DIA_DE_LA_QUINCENA)) {
                SelectorDeDiaDelMes(dias = 31, elegido = segundo, habilitado = habilitado, onElegir = onSegundo)
            }
        }
    }
}
