package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.porQueNoEsHabil
import com.jvillada.movi.shared.model.rangoDelPeriodo
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.GrupoDeCampos
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VistaPrevia
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/** La fila de Ajustes que abre esta hoja: «período», como en el resto de la app (vuelta 31). */
const val INICIO_DEL_PERIODO: String = "Inicio del período"

/** La pregunta de la hoja (vuelta 31: decía «¿Qué día empieza tu mes?»). */
const val PREGUNTA_DEL_INICIO_DEL_PERIODO: String = "¿Qué día empieza tu período?"

/**
 * Elegir el día en que arranca el período financiero.
 *
 * Existe porque el dueño lo pidió como ajuste, no como constante: *«otros usuarios no van a poder
 * gestionar desde Claude cambios en la app»*. Un valor por defecto sensato está bien; un valor
 * **solo** por defecto deja a todos los demás sin la función.
 *
 * La hoja muestra en vivo qué período sería hoy con el día elegido — es la única forma de que
 * «26» signifique algo antes de guardarlo.
 *
 * Y debajo, **qué pasa cuando ese día cae en fin de semana o festivo** ([SiElCorteCaeEnFinDeSemana]):
 * al dueño le pagan el día hábil anterior, a otros el siguiente. Antes eso se declaraba a mano mes por
 * mes; con la regla el período lo sabe solo.
 */
@Composable
fun PeriodoSheet(
    cutoffActual: Int,
    onDismiss: () -> Unit,
    onSave: (Int, SiElCorteCaeEnFinDeSemana) -> Unit,
    reglaActual: SiElCorteCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.ESE_DIA,
    saving: Boolean = false,
    error: String? = null,
) {
    // `remember(cutoffActual)` y no `remember {}` a secas: una hoja abierta mientras el perfil
    // todavía viajaba se quedaba **para siempre** en el día que tenía al abrirse —el 1 por
    // defecto— aunque el corte real (25) llegara un segundo después. El botón «Guardar» seguía
    // activo, así que un toque escribía 1 encima del 25 y le movía el mes entero. Con la clave,
    // el valor que llega manda; es el mismo patrón que ya usa `InicioDelPeriodoSheet`.
    var dia by remember(cutoffActual) { mutableStateOf(cutoffActual.coerceIn(1, 31)) }
    var regla by remember(reglaActual) { mutableStateOf(reglaActual) }
    val settings = remember(dia, regla) { PeriodSettings(cutoffDay = dia, siCaeEnFinDeSemana = regla) }
    val hoy = remember(settings) { periodoDe(Clock.System.now().toEpochMilliseconds(), settings) }
    val ejemplo = remember(settings, hoy) { ejemploCortoDelCorte(hoy, settings) }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        // El contenido se desplaza y la hoja crece con él hasta el borde (`weight(1f, fill = false)`):
        // sin eso, con el teclado abierto en un teléfono chico, «Guardar» quedaba recortado por el
        // `clip` de la hoja. Mismo patrón que las hojas de `CategorySheets.kt`.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            // Vuelta 41: una frase, no dos; y el título es lo más grande de la hoja.
            EncabezadoDeHoja(
                PREGUNTA_DEL_INICIO_DEL_PERIODO,
                subtitulo = "Movi cuenta tus gastos, ingresos y presupuestos desde ese día.",
            )
            Spacer(Modifier.height(Medida.entreSecciones))

            // Cinco filas de siete, no un `LazyVerticalGrid`: el grid tenía un alto fijo de 196 dp
            // para un contenido de 224 y cortaba el 29, el 30 y el 31 (ver [CalendarioDeDias]).
            CalendarioDeDias(
                dias = 31,
                elegido = dia,
                habilitado = !saving,
                onElegir = { dia = it },
            )

            Spacer(Modifier.height(Medida.entreSecciones))
            GrupoDeCampos(SI_CAE_EN_FIN_DE_SEMANA) {
                Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                    SiElCorteCaeEnFinDeSemana.entries.forEachIndexed { i, opcion ->
                        val elegida = opcion == regla
                        Fila(
                            titulo = tituloDeLaRegla(opcion),
                            // El check en tinta: lo elegido (vuelta 29).
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
                            onClick = if (saving) null else ({ regla = opcion }),
                            modifier = Modifier.semantics { selected = elegida },
                        )
                        if (i < SiElCorteCaeEnFinDeSemana.entries.lastIndex) Separador(SangriaDeSeparador.Margen)
                    }
                }
            }

            // Lo que va a pasar, en dos renglones como mucho: el período de hoy con lo elegido y, si
            // hace falta, la excepción que se ve justo al tocar (un 29-31, o un corte que la regla corre).
            Spacer(Modifier.height(Medida.entreSecciones))
            VistaPrevia(
                vistaPreviaDelPeriodo(hoy, settings),
                detalle = detalleDelInicioDelPeriodo(dia, ejemplo),
            )

            Spacer(Modifier.height(Espacio.l))
            PieDeHoja(
                texto = "Guardar",
                onClick = { onSave(dia, regla) },
                cargando = saving,
                error = error,
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

/**
 * **La vista previa de «Inicio del período»**: «Tu período: del 25 de octubre al 24 de noviembre», el
 * período de hoy con el día y la regla elegidos (también con el mes de calendario: «del 1 al 31 de
 * octubre»). Pura.
 */
fun vistaPreviaDelPeriodo(hoy: PeriodoFinanciero, settings: PeriodSettings): String =
    "Tu período: " + rangoDelPeriodo(hoy, settings).replaceFirstChar { it.lowercase() }

/**
 * El segundo renglón de la vista previa, si hace falta: un día que no todos los meses tienen, o el
 * próximo corte que la regla corre ([ejemploCortoDelCorte]). `null` si no hay nada que aclarar. Pura.
 */
fun detalleDelInicioDelPeriodo(dia: Int, ejemplo: String?): String? =
    if (dia > 28) "En los meses sin día $dia, empieza el último día del mes." else ejemplo

/**
 * El título del grupo de la elección. Vuelta 41: era «Si ese día cae en fin de semana o festivo», y a
 * 360 dp no entraba en el renglón de un `GrupoDeCampos`.
 */
const val SI_CAE_EN_FIN_DE_SEMANA: String = "Si cae en fin de semana o festivo"

/** Cómo se dice cada regla. */
fun tituloDeLaRegla(regla: SiElCorteCaeEnFinDeSemana): String = when (regla) {
    SiElCorteCaeEnFinDeSemana.ESE_DIA -> "Empieza ese mismo día"
    SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> "Empieza el día hábil anterior"
    SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> "Empieza el día hábil siguiente"
}

/**
 * «Por ejemplo, el 25 de octubre es domingo: ese período empieza el 23 de octubre.» — el próximo corte (desde el
 * período de [desde]) que la regla de [settings] corre, en el próximo año; `null` si la regla es
 * «ese mismo día» o ninguno cae en fin de semana. Lo dice la guía de primer uso.
 */
fun proximoCorteCorrido(desde: PeriodoFinanciero, settings: PeriodSettings): String? =
    corteCorrido(desde, settings)?.let { (natural, corrido, nombreDelDia) ->
        "Por ejemplo, el ${diaLegible(natural)} es $nombreDelDia: ese período empieza el ${diaLegible(corrido)}."
    }

/**
 * Lo mismo, en un renglón, para la vista previa de la hoja (vuelta 41): «El 25 de octubre es domingo:
 * empieza el 23.» Pura.
 */
fun ejemploCortoDelCorte(desde: PeriodoFinanciero, settings: PeriodSettings): String? =
    corteCorrido(desde, settings)?.let { (natural, corrido, nombreDelDia) ->
        val empieza = if (corrido.month == natural.month) "el ${corrido.dayOfMonth}" else "el ${diaLegible(corrido)}"
        "El ${diaLegible(natural)} es $nombreDelDia: empieza $empieza."
    }

/** El próximo corte que la regla corre: el día natural, el corrido y por qué no es hábil. */
private fun corteCorrido(desde: PeriodoFinanciero, settings: PeriodSettings): Triple<LocalDate, LocalDate, String>? {
    if (settings.siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA) return null
    val sinCorrer = settings.copy(siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.ESE_DIA, iniciosPropios = emptyMap())
    val conLaRegla = settings.copy(iniciosPropios = emptyMap())
    var periodo = desde
    repeat(13) {
        val natural = inicioDelPeriodo(periodo, sinCorrer)
        val corrido = inicioDelPeriodo(periodo, conLaRegla)
        if (natural != corrido) {
            val nombreDelDia = porQueNoEsHabil(natural) ?: return null
            return Triple(natural, corrido, nombreDelDia)
        }
        periodo = periodoSiguiente(periodo)
    }
    return null
}
