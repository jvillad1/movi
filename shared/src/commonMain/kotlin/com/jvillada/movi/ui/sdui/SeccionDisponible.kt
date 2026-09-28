package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.dashboard.DisponibleDelPeriodo
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.VentanaDelDisponible
import com.jvillada.movi.ui.dashboard.desgloseDelDisponible
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.fraseDelDisponible
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.rotuloDeLaSemana

/**
 * # «Disponible»: cuánto queda del período, compacto y con UNA frase
 *
 * El dueño la pidió así: *«Disponible en el periodo · por semana · por día»*. Se queda, pero baja de
 * lugar (debajo de «Falta por pagar», que son justamente los fijos que resta) y se achica: la cifra
 * del disponible, **una** frase de cómo viene y tres columnas —el período, la semana, hoy— con lo
 * que te queda por gastar en cada una, lo que ya gastaste y una barra que crece al cargar.
 *
 * **Una sola frase, no tres.** Cada fila decía la suya, y con el período pasado el dueño leía «Te
 * pasaste por $563.456» en rojo y, un renglón abajo, «Vas bien: te quedan … para esta semana». La
 * frase ahora es [fraseDelDisponible]: manda la peor ventana y nunca dice «vas bien». Y el veredicto
 * del hero sale de la misma cuenta ([excesoDelDisponible]), así que arriba y abajo no pueden decir
 * cosas opuestas.
 *
 * De dónde sale el disponible —lo que tenías el 25, lo que entró, los fijos— queda detrás de «¿De
 * dónde sale?»: es la explicación, no la noticia.
 *
 * **Cuando los fijos superan lo que hay lo dice con todas las letras** y no dibuja barras: una
 * barra contra un disponible negativo no tiene largo que signifique algo. Las columnas se siguen
 * mostrando —lo que queda, que ahí es cero o negativo, y lo gastado—, porque son lo único sobre lo
 * que se puede actuar hoy.
 */
@Composable
internal fun DisponibleDelPeriodoSection(
    section: ScreenSection,
    data: DashboardData,
    // Por parámetro para que una prueba fije el día; la pantalla usa el de hoy en Bogotá.
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
) {
    val disponible = disponibleDelInicio(data, hoy) ?: return
    TarjetaDelDisponible(titulo = section.title ?: "Disponible", disponible = disponible)
}

/** La tarjeta del disponible, cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_DISPONIBLE: String = "tarjeta-del-disponible"

/** La cifra esqueleto del disponible — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_DISPONIBLE: String = "esqueleto-del-disponible"

/** La barra de una columna (período, semana, hoy): está solo cuando hay margen contra qué medir. */
const val TAG_BARRA_DE_VENTANA: String = "barra-de-ventana"

/**
 * **La tarjeta del disponible**, con su título arriba. La pinta el Inicio (ver
 * [DisponibleDelPeriodoSection]) y la pestaña Plan, que la tiene de protagonista con otro título
 * («Cuánto puedes gastar»): una sola tarjeta, así que las dos pantallas no pueden decir cifras ni
 * frases distintas del mismo período.
 *
 * @param disponible `null` = **todavía no llegó**: la tarjeta se pinta con su forma y sin ninguna
 *   cifra (ola C). El Inicio nunca la pide así —si falta el dato no pinta la sección—; Plan sí,
 *   porque ahí la tarjeta es lo primero que se ve y aparecer de golpe empujaba todo lo de abajo.
 *   El esqueleto copia la forma CON margen (la de todos los días): sin margen la tarjeta llega
 *   un poco más baja, nunca más alta.
 */
@Composable
internal fun TarjetaDelDisponible(
    titulo: String,
    disponible: DisponibleDelPeriodo?,
    modifier: Modifier = Modifier,
) {
    val entrada = rememberProgresoDeEntrada("disponible", listo = disponible != null)
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = Movi.espacios.amplio)) {
        // El «¿De dónde sale?» está desde el primer cuadro, también cargando: es parte del alto del
        // encabezado (su letra es más alta que la del rótulo). Mientras carga no hace nada — no hay
        // de dónde, y si el toque quedara guardado la tarjeta llegaría ya abierta y más alta que
        // su esqueleto.
        MinSectionHeader(
            title = titulo,
            action = if (verDeDondeSale) "Ocultar" else "¿De dónde sale?",
            onAction = { if (disponible != null) verDeDondeSale = !verDeDondeSale },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_DISPONIBLE),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.amplio),
        ) {
            if (disponible == null) {
                CuerpoDelDisponibleEsqueleto()
            } else {
                CuerpoDelDisponible(disponible, verDeDondeSale, entrada)
            }
        }
    }
}

@Composable
private fun CuerpoDelDisponible(disponible: DisponibleDelPeriodo, verDeDondeSale: Boolean, entrada: Float) {
    val frase = fraseDelDisponible(disponible)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Disponible del período",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        Cifra(
            formatMoneyCompact(disponible.disponible),
            Movi.textos.titulo,
            color = if (disponible.hayMargen) Movi.colores.texto else Movi.colores.sale,
        )
    }
    Spacer(Modifier.height(Movi.espacios.minimo))
    Text(text = frase.texto, style = Movi.textos.apoyo, color = colorDeLaFrase(frase.nivel))
    if (verDeDondeSale) {
        Spacer(Modifier.height(Movi.espacios.minimo))
        // «Tenías $X el 25 · entraron $Y» y abajo los fijos: dos líneas cortas y no una
        // larga, para que quepan a 390 px con la letra grande del teléfono.
        desgloseDelDisponible(disponible).forEach { linea ->
            Text(text = linea, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
    }
    Spacer(Modifier.height(Movi.espacios.medio))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        ColumnaDeVentana("Período", disponible.periodo, disponible.hayMargen, entrada, Modifier.weight(1f))
        ColumnaDeVentana(rotuloDeLaSemana(disponible), disponible.semana, disponible.hayMargen, entrada, Modifier.weight(1f))
        ColumnaDeVentana("Hoy", disponible.hoy, disponible.hayMargen, entrada, Modifier.weight(1f))
    }
}

/**
 * [CuerpoDelDisponible] sin ninguna cifra: el rótulo «Disponible del período» (no es un dato, es
 * el nombre del renglón) con un bloque donde va la cifra, un renglón para la frase y las tres
 * columnas con su rótulo, lo que queda, los dos renglones de «gastaste $…» y la barra. Mismos
 * espacios y estilos que la real, para que al llegar el dato no cambie de alto (±8 dp, lo mide
 * `PlanScreenTest`).
 */
@Composable
private fun CuerpoDelDisponibleEsqueleto() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Disponible del período",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        BloqueEsqueleto(
            alto = altoDeUnRenglon(Movi.textos.titulo),
            ancho = 88.dp,
            modifier = Modifier.testTag(TAG_ESQUELETO_DEL_DISPONIBLE),
        )
    }
    Spacer(Modifier.height(Movi.espacios.minimo))
    LineaEsqueleto(fraccionDelAncho = 0.75f, estilo = Movi.textos.apoyo)
    Spacer(Modifier.height(Movi.espacios.medio))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        repeat(3) {
            Column(modifier = Modifier.weight(1f)) {
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                LineaEsqueleto(fraccionDelAncho = 0.8f, estilo = Movi.textos.monto)
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                Spacer(Modifier.height(Movi.espacios.minimo))
                BloqueEsqueleto(alto = Movi.espacios.minimo + 2.dp)
            }
        }
    }
}

/**
 * Una ventana en su columna: el rótulo, **lo que te queda por gastar en ella**, lo que ya gastaste
 * y la barra.
 *
 * El dueño, el 28-sep: *«yo quiero que esto me marque cuánto me puedo gastar por cada una de esas
 * unidades de tiempo no cuánto me gasté, son cosas muy diferentes»*. La cifra grande era lo gastado
 * y la meta iba chiquita («de $X»), y sin margen la meta ni aparecía — justo cuando más importa
 * saber cuánto queda. Ahora la grande es [VentanaDelDisponible.teQuedan] y la chica, lo gastado.
 *
 * **Las dos cifras se ven siempre, con o sin margen.** Sin margen la meta es cero, así que lo que
 * queda es menos lo gastado: «$0» si en esa ventana no gastaste nada, y el negativo en rojo si ya
 * gastaste — «no te queda nada, y ya gastaste $X de más» es información, no un susto.
 *
 * **La barra sí se va sin margen**: mide lo gastado contra la meta, y contra cero no tiene largo que
 * signifique algo.
 */
@Composable
private fun ColumnaDeVentana(
    rotulo: String,
    ventana: VentanaDelDisponible,
    hayMargen: Boolean,
    entrada: Float,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(text = rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
        // Rojo cuando la ventana está PASADA, que es exactamente cuando lo que queda es negativo
        // (sin margen: gastó algo contra una meta de cero).
        Cifra(
            formatMoneyCompact(ventana.teQuedan),
            Movi.textos.monto,
            color = if (ventana.nivel == NivelDelGasto.PASADO) Movi.colores.sale else Movi.colores.texto,
        )
        // «gastaste» y la cifra en dos renglones, no en uno: «gastaste $440.000» no entra en su
        // tercio de tarjeta a 390 dp —visto en la web, cortado en «gastaste $440…»— y achicar la
        // letra hasta que entre la dejaba en ~9 sp. Dos renglones fijos en las tres columnas, así
        // que las barras siguen parejas. Que cada cifra entre en su columna lo mide
        // `LasCifrasDelDisponibleEntranTest`.
        Text(text = "gastaste", style = Movi.textos.apoyo, color = Movi.colores.textoApagado, maxLines = 1)
        Text(
            text = formatMoneyCompact(ventana.gastado),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            maxLines = 1,
        )
        if (hayMargen) {
            Spacer(Modifier.height(Movi.espacios.minimo))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Movi.espacios.minimo + 2.dp)
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    .background(Movi.colores.hilo)
                    .testTag(TAG_BARRA_DE_VENTANA),
            ) {
                // Sin gasto no hay relleno: una barra vacía dice exactamente eso. Con algo gastado,
                // un mínimo del 2 % para que se vea que hay algo.
                val fraccion = ventana.fraccion
                if (fraccion != null && ventana.gastado > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraccion.coerceIn(0.02f, 1f) * entrada.coerceIn(0f, 1f))
                            .height(Movi.espacios.minimo + 2.dp)
                            .clip(RoundedCornerShape(Movi.formas.pleno))
                            .background(colorDelNivel(ventana.nivel)),
                    )
                }
            }
        }
    }
}

@Composable
private fun colorDelNivel(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.marca
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}

@Composable
private fun colorDeLaFrase(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.textoMedio
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}
