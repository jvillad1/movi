package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.porQueNoEsHabil
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.MarcoDeHoja
import kotlinx.datetime.Clock
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja

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
    val ejemplo = remember(settings, hoy) { proximoCorteCorrido(hoy, settings) }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        // El contenido de la hoja se desplaza.
        //
        // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
        // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
        // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
        // propia hoja. Sin manera de llegar a él.
        //
        // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
        // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
        // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {

            EncabezadoDeHoja(
                PREGUNTA_DEL_INICIO_DEL_PERIODO,
                subtitulo = "Movi cuenta tus ingresos, gastos y presupuestos sobre esta ventana. " +
                    "Si te pagan el 26, elige 26 y el período te va a cuadrar con el sueldo.",
                modifier = Modifier.padding(top = Espacio.xxs, bottom = Espacio.m),
            )

            // La consecuencia, en vivo: sin esto «26» es un número sin significado.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radio.m))
                    .background(Movi.colores.tarjeta)
                    .padding(horizontal = Espacio.m, vertical = Espacio.s),
            ) {
                Text(
                    text = "Hoy estarías en ${nombreDe(hoy)}",
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Espacio.xxs))
                Text(
                    text = rangoLegibleDe(hoy, settings) ?: "Del 1 al último día del mes",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                if (settings.esMesDeCalendario) {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(
                        text = "Es el mes de calendario, como viene por defecto.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoApagado,
                    )
                }
            }

            Spacer(Modifier.height(Espacio.m))

            // Cinco filas de siete, no un `LazyVerticalGrid`.
            //
            // El grid tenía `.height(196.dp)` fija y su contenido mide **224**: cinco filas de
            // 40 dp con 6 de separación. La revisión lo midió a 411×731, o sea con espacio de
            // sobra en la pantalla: el «31» se dibujaba solo 4 de sus 24 dp — **los días 29,
            // 30 y 31 quedaban cortados**, justo los que esta hoja se toma el trabajo de
            // explicar en el aviso de abajo.
            //
            // Y el scroll nuevo de la hoja no podía revelarlos, porque el alto del grid es
            // fijo: había que arrastrar dentro de una ventanita de 196 dp en el medio. Es el
            // mismo anti-patrón que `HojaAgregarGeometriaTest` ya documenta — dos áreas
            // desplazables anidadas peleándose el gesto del dedo.
            //
            // 31 celdas de tamaño conocido no ganan nada con ser lazy. Con filas normales el
            // calendario entero se ve, la hoja tiene un solo desplazamiento, y desaparece el
            // alto mágico que había que mantener a mano cada vez que cambiara el tamaño de una
            // celda.
            (1..31).chunked(7).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    fila.forEach { d ->
                        val elegido = d == dia
                        Box(
                            modifier = Modifier
                                .size(Medida.avatar)
                                .clip(CircleShape)
                                .background(if (elegido) Movi.colores.marca else Movi.colores.tarjeta)
                                .clickable(enabled = !saving) { dia = d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = d.toString(),
                                style = if (elegido) Movi.textos.cuerpoFuerte else Movi.textos.cuerpoRegular,
                                color = if (elegido) Movi.colores.fondo else Movi.colores.texto,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.xs))
            }

            // Un 29, 30 o 31 no existe en todos los meses. Se dice ANTES de guardar, porque es la
            // duda que aparece justo al tocar ese número.
            if (dia > 28) {
                Spacer(Modifier.height(Espacio.s))
                Text(
                    text = "En los meses que no tienen día $dia, el corte cae el último día del mes.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }

            Spacer(Modifier.height(Espacio.m))
            Text(
                text = SI_CAE_EN_FIN_DE_SEMANA,
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                modifier = Modifier.padding(bottom = Espacio.xs),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radio.m))
                    .background(Movi.colores.tarjeta)
                    .padding(horizontal = Espacio.m, vertical = Espacio.xxs),
            ) {
                SiElCorteCaeEnFinDeSemana.entries.forEachIndexed { i, opcion ->
                    val elegida = opcion == regla
                    CardRow(
                        left = { Text(tituloDeLaRegla(opcion), style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                        right = {
                            // El check en el color de la marca, como el tema en Ajustes.
                            Box(modifier = Modifier.size(Icono.l), contentAlignment = Alignment.Center) {
                                if (elegida) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Elegido",
                                        tint = Movi.colores.marca,
                                        modifier = Modifier.size(Icono.m),
                                    )
                                }
                            }
                        },
                        isLast = i == SiElCorteCaeEnFinDeSemana.entries.lastIndex,
                        onClick = if (saving) null else ({ regla = opcion }),
                        modifier = Modifier.semantics { selected = elegida },
                    )
                }
            }
            // El ejemplo concreto: «el 25 de octubre es domingo» dice más que la regla en abstracto.
            if (ejemplo != null) {
                Spacer(Modifier.height(Espacio.xs))
                Text(text = ejemplo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }

            if (error != null) {
                Spacer(Modifier.height(Espacio.s))
                Text(text = error, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }

            Spacer(Modifier.height(Espacio.l))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radio.m))
                    .background(if (saving) Movi.colores.tarjeta else Movi.colores.marca)
                    .clickable(enabled = !saving) { onSave(dia, regla) }
                    .padding(vertical = Espacio.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Guardando…" else "Guardar",
                    color = if (saving) Movi.colores.textoMedio else Movi.colores.fondo,
                    style = Movi.textos.cuerpo,
                )
            }

            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

/** El título de la elección, en la hoja y en la fila de Ajustes. */
const val SI_CAE_EN_FIN_DE_SEMANA: String = "Si ese día cae en fin de semana o festivo"

/** Cómo se dice cada regla. */
fun tituloDeLaRegla(regla: SiElCorteCaeEnFinDeSemana): String = when (regla) {
    SiElCorteCaeEnFinDeSemana.ESE_DIA -> "Empieza ese mismo día"
    SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> "Empieza el día hábil anterior"
    SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> "Empieza el día hábil siguiente"
}

/**
 * «Por ejemplo, el 25 de octubre es domingo: ese período empieza el 23 de octubre.» — el próximo corte (desde el
 * período de [desde]) que la regla de [settings] corre, en el próximo año; `null` si la regla es
 * «ese mismo día» o ninguno cae en fin de semana.
 */
fun proximoCorteCorrido(desde: PeriodoFinanciero, settings: PeriodSettings): String? {
    if (settings.siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA) return null
    val sinCorrer = settings.copy(siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.ESE_DIA, iniciosPropios = emptyMap())
    val conLaRegla = settings.copy(iniciosPropios = emptyMap())
    var periodo = desde
    repeat(13) {
        val natural = inicioDelPeriodo(periodo, sinCorrer)
        val corrido = inicioDelPeriodo(periodo, conLaRegla)
        if (natural != corrido) {
            val nombreDelDia = porQueNoEsHabil(natural) ?: return null
            return "Por ejemplo, el ${diaLegible(natural)} es $nombreDelDia: ese período empieza el ${diaLegible(corrido)}."
        }
        periodo = periodoSiguiente(periodo)
    }
    return null
}
