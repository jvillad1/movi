package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.ai.preguntasSugeridas
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.CifraProtagonista
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.StatusDot
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TramoDelPatrimonio
import com.jvillada.movi.ui.dashboard.cifraContando
import com.jvillada.movi.ui.dashboard.cuentasDelHero
import com.jvillada.movi.ui.dashboard.destinoDelTramo
import com.jvillada.movi.ui.dashboard.fraccionQueEntro
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.dashboard.heroBalanceTitle
import com.jvillada.movi.ui.dashboard.patrimonioDelInicio
import com.jvillada.movi.ui.dashboard.patrimonioExplicacion
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.veredictoDelInicio
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # Las secciones nuevas del Inicio de un vistazo (generación 8)
 *
 * El hero repintado —¿cómo estoy?—, «Pregúntale a Movi» y «Tu patrimonio». Lo que deciden sale de
 * `InicioDeUnVistazo.kt`, que es puro y está probado; acá solo hay disposición, color y la entrada
 * animada. Misma regla que `SeccionesDelPeriodo.kt`: si una decisión sobre la plata se escribe en este
 * archivo, está en el lugar equivocado.
 */

// ── ¿Cómo estoy? ─────────────────────────────────────────────────────────────

/**
 * **El hero: Tu plata, un veredicto y la barra de lo que entró contra lo que salió. Nada más.**
 *
 * Antes esta tarjeta tenía Tu plata, el uso condicionado, la lista de cuentas, el patrimonio neto
 * con su explicación y tres cifras más (ingresos, gastos, flujo). Todo eso sigue en el Inicio, pero
 * no acá: los tramos del patrimonio y las cuentas van a la tarjeta «Tu patrimonio», y el flujo lo dice
 * el veredicto en palabras. Lo que queda contesta una sola pregunta.
 *
 * - **La cifra entra contando** (ver [rememberProgresoDeEntrada]): una vez por proceso, y solo cuando
 *   las cuentas ya contestaron — mientras tanto, un guion. Tocarla lleva a Cuentas, que es donde está
 *   cada saldo.
 * - **El veredicto** es [veredictoDelInicio]: una frase, de una sola regla, coherente con la tarjeta
 *   del disponible de más abajo.
 * - **La barra** tiene dos tramos, entró (verde) y salió (coral), y crece al cargar. Debajo, las dos
 *   cifras. El «Flujo del mes» ya no va como tercera cifra: la resta la dice el veredicto, con
 *   palabras y con el número.
 *
 * [conPatrimonio]: una definición guardada anterior a la generación 8 no trae la tarjeta PATRIMONIO
 * (ver [visibleSections] y `DASHBOARD_LAYOUT_VERSION`). Para que en esa ventana el patrimonio no se
 * pierda del Inicio, el hero lo dice en una línea —la de siempre, con su resta escrita—.
 */
@Composable
internal fun HeroDeUnVistazo(
    section: ScreenSection,
    data: DashboardData,
    conPatrimonio: Boolean,
    onNavigate: (Screen) -> Unit,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
) {
    val balance = heroBalance(data.accounts.orEmpty())
    val entradaDeLaCifra = rememberProgresoDeEntrada("hero.cifra", listo = data.accounts != null)
    val entradaDeLaBarra = rememberProgresoDeEntrada("hero.barra", listo = data.summary != null)
    val veredicto = veredictoDelInicio(data, hoy)
    val ingresos = data.summary?.ingresos ?: 0L
    val egresos = data.summary?.egresos ?: 0L

    MinCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Movi.espacios.amplio),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(Movi.espacios.margen),
    ) {
        // El rótulo viaja en el binario, no en la fila: ver [HERO_BALANCE_TITLE].
        Text(text = heroBalanceTitle(section), style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        encabezadoDelPeriodo(data)?.let { rango ->
            Text(text = rango, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
        Spacer(Modifier.height(Movi.espacios.corto))
        CifraProtagonista(
            // Un guion mientras las cuentas no contestan: un «$0» de 42 sp es la afirmación más fuerte
            // de la pantalla, y sería falsa mientras carga.
            text = if (data.accounts == null) "—" else formatCOP(cifraContando(balance.tuPlata, entradaDeLaCifra)),
            color = if (balance.tuPlata < 0) Movi.colores.sale else Movi.colores.texto,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Ver tus cuentas") {
                onNavigate(Screen.Accounts)
            },
        )
        if (veredicto != null) {
            Spacer(Modifier.height(Movi.espacios.medio))
            Text(text = veredicto.frase, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        }
        val fraccion = fraccionQueEntro(ingresos, egresos)
        if (data.summary != null && fraccion != null) {
            Spacer(Modifier.height(Movi.espacios.amplio))
            BarraDeDosTramos(
                fraccionIzquierda = fraccion,
                colorIzquierda = Movi.colores.entra,
                colorDerecha = Movi.colores.sale,
                entrada = entradaDeLaBarra,
            )
            Spacer(Modifier.height(Movi.espacios.corto))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ParDeCifra("Entró", formatMoneyCompact(ingresos), Movi.colores.entra, Modifier.weight(1f))
                ParDeCifra("Salió", formatMoneyCompact(egresos), Movi.colores.sale, alFinal = true)
            }
        }
        if (conPatrimonio && data.accounts != null && balance.muestraPatrimonio) {
            Spacer(Modifier.height(Movi.espacios.amplio))
            Hairline()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Accounts) }
                    .padding(top = Movi.espacios.medio),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Patrimonio neto",
                        modifier = Modifier.weight(1f),
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                    Cifra(formatMoneyCompact(balance.patrimonio), Movi.textos.titulo, color = Movi.colores.texto)
                }
                Text(
                    text = patrimonioExplicacion(balance),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                )
            }
        }
    }
}

/** Un rótulo chico arriba y su cifra abajo; [alFinal] lo alinea a la derecha. */
@Composable
private fun ParDeCifra(
    rotulo: String,
    cifra: String,
    color: Color,
    modifier: Modifier = Modifier,
    alFinal: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alFinal) Alignment.End else Alignment.Start,
    ) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Cifra(cifra, Movi.textos.titulo, color = color)
    }
}

/**
 * Una barra fina de dos tramos que crece al cargar: [fraccionIzquierda] del ancho para el primero, el
 * resto para el segundo, separados por un hilo. [entrada] va de 0 a 1 y escala la barra entera.
 */
@Composable
internal fun BarraDeDosTramos(
    fraccionIzquierda: Float,
    colorIzquierda: Color,
    colorDerecha: Color,
    entrada: Float,
) {
    val forma = RoundedCornerShape(Movi.formas.pleno)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Movi.espacios.corto)
            .clip(forma)
            .background(Movi.colores.hilo),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(entrada.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(forma),
        ) {
            val izquierda = fraccionIzquierda.coerceIn(0f, 1f)
            if (izquierda > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(izquierda).background(colorIzquierda))
            }
            if (izquierda < 1f) {
                if (izquierda > 0f) Spacer(Modifier.width(2.dp))
                Box(Modifier.fillMaxHeight().weight(1f).background(colorDerecha))
            }
        }
    }
}

/**
 * «Del 25 de agosto al 24 de septiembre · quedan 9 días», o `null` si el dueño usa el mes de
 * calendario (ahí el nombre del mes ya lo dice todo) o si el perfil todavía no contestó.
 *
 * Los días que quedan importan tanto como el rango: son la diferencia entre «me pasé» y «me estoy
 * por pasar», y es lo que convierte el resumen en algo accionable.
 */
internal fun encabezadoDelPeriodo(data: DashboardData): String? {
    val periodo = data.periodoActual ?: return null
    val rango = rangoLegibleDe(periodo, data.ajustesDePeriodo) ?: return null
    val ahora = Clock.System.now().toEpochMilliseconds()
    val fin = ventanaDe(periodo, data.ajustesDePeriodo).last
    val dias = ((fin - ahora) / 86_400_000L).toInt()
    return when {
        dias > 1 -> "$rango · quedan $dias días"
        dias == 1 -> "$rango · queda 1 día"
        dias == 0 -> "$rango · último día"
        else -> rango
    }
}

// ── Pregúntale a Movi ────────────────────────────────────────────────────────

/**
 * **Movi AI, arriba y con algo para preguntar.** Era la última sección del Inicio, un banner que
 * decía «Pregúntale a Movi AI» y abría un chat en blanco; el dueño escribió «Hola, me puedes
 * ayudar?» porque no sabía qué preguntar.
 *
 * Ahora son tres preguntas armadas con SUS datos ([preguntasSugeridas]: reglas, sin llamar al
 * modelo, así que abrir el Inicio no cuesta un peso) y un campo que abre el chat vacío. Tocar una
 * pregunta abre el chat con ella ya enviada ([Screen.AIChat.preguntaInicial]).
 *
 * El campo no es un campo: es un botón con cara de campo. Escribir acá y después saltar al chat
 * perdería el foco y el teclado en el camino (el teléfono, la web), y el chat ya tiene su campo —el
 * que sabe adjuntar un extracto—. Lo que hace es decir «acá también se puede escribir lo que
 * quieras», que es la mitad del mensaje.
 */
@Composable
internal fun PreguntaleAMoviSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    // Las reglas son baratas, pero se recalculan solo cuando cambian los datos, no en cada frame de
    // la entrada animada del hero.
    val preguntas = remember(data) { preguntasSugeridas(data) }

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Pregúntale a Movi")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(
                start = Movi.espacios.amplio,
                end = Movi.espacios.amplio,
                top = Movi.espacios.minimo,
                bottom = Movi.espacios.amplio,
            ),
        ) {
            preguntas.forEachIndexed { i, pregunta ->
                FilaDePregunta(pregunta) { onNavigate(Screen.AIChat(preguntaInicial = pregunta)) }
                if (i < preguntas.lastIndex) Hairline()
            }
            Spacer(Modifier.height(Movi.espacios.corto))
            CampoParaPreguntar { onNavigate(Screen.AIChat()) }
        }
    }
}

@Composable
private fun FilaDePregunta(pregunta: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Movi.espacios.medio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Movi.colores.marca,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = pregunta,
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        ChevronRight()
    }
}

@Composable
private fun CampoParaPreguntar(onClick: () -> Unit) {
    val forma = RoundedCornerShape(Movi.formas.pleno)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .border(1.dp, Movi.colores.borde, forma)
            .clickable(role = Role.Button, onClickLabel = "Abrir el chat de Movi", onClick = onClick)
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Escribe tu pregunta…",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoApagado,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = null,
            tint = Movi.colores.marca,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Tu patrimonio ────────────────────────────────────────────────────────────

/**
 * **Lo que tienes contra lo que debes, con una barra que se entiende sin leer números.**
 *
 * El patrimonio neto del dueño decía −$2.074M y mentía por omisión: contaba las deudas y ni un bien
 * (ver la entrega A). Ya con la casa cargada es −$662,3M, y aun así una cifra negativa sola asusta
 * más de lo que explica. La barra dice lo que la cifra no: **tienes $1.528,7M y debes $2.191M**, y
 * la mayor parte de lo que tienes es la casa que esas deudas pagan.
 *
 * - El neto va en el color del texto, **no en rojo**: es estructura de largo plazo (hipotecas), no una
 *   pérdida de este período. Misma regla que siempre tuvo esta cifra en el hero y en Cuentas.
 * - Los tramos van con su nombre —tu plata, uso condicionado, bienes, deudas— y cada uno lleva a
 *   donde se mira: las deudas a Créditos, lo demás a Cuentas ([destinoDelTramo]).
 * - Debajo de «Tu plata», el saldo de cada cuenta. Lo pidió el dueño («no el total sino el disponible
 *   en cada cuenta») cuando vivía en el hero; el hero ahora contesta una sola pregunta y la lista se
 *   mudó acá, al lado de la cifra que desglosa.
 */
@Composable
internal fun PatrimonioSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    val patrimonio = patrimonioDelInicio(data) ?: return
    val entrada = rememberProgresoDeEntrada("patrimonio")
    val cuentas = cuentasDelHero(data.accounts).orEmpty()

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Tu patrimonio")
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.margen),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Patrimonio neto",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.weight(1f),
                )
                Cifra(formatMoneyCompact(patrimonio.neto), Movi.textos.titulo, color = Movi.colores.texto)
            }
            Spacer(Modifier.height(Movi.espacios.medio))
            BarraDeDosTramos(
                fraccionIzquierda = patrimonio.fraccionQueTienes,
                colorIzquierda = Movi.colores.entra,
                colorDerecha = Movi.colores.sale,
                entrada = entrada,
            )
            Spacer(Modifier.height(Movi.espacios.corto))
            Row(modifier = Modifier.fillMaxWidth()) {
                ParDeCifra("Tienes", formatMoneyCompact(patrimonio.tienes), Movi.colores.entra, Modifier.weight(1f))
                ParDeCifra("Debes", formatMoneyCompact(patrimonio.debes), Movi.colores.sale, alFinal = true)
            }
            Spacer(Modifier.height(Movi.espacios.medio))
            Hairline()
            Spacer(Modifier.height(Movi.espacios.minimo))
            patrimonio.tramos.forEach { tramo ->
                FilaDeTramo(tramo) { onNavigate(destinoDelTramo(tramo)) }
                if (tramo.nombre == "Tu plata" && cuentas.size > 1) {
                    cuentas.forEach { cuenta ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Movi.espacios.margen, bottom = Movi.espacios.minimo),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = cuenta.nombre,
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Movi.espacios.corto))
                            Cifra(cuenta.monto, Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaDeTramo(tramo: TramoDelPatrimonio, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Movi.espacios.corto),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        StatusDot(color = if (tramo.esDeuda) Movi.colores.sale else Movi.colores.entra, size = 7.dp)
        Text(
            text = tramo.nombre,
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        Cifra(formatMoneyCompact(tramo.monto), Movi.textos.monto, color = Movi.colores.texto, fontWeight = FontWeight.Medium)
        ChevronRight()
    }
}
