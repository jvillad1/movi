package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.CifraConRotulo
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.recurrentes.BajoLaFila
import com.jvillada.movi.ui.ai.CUANTAS_PREGUNTAS_SUGERIDAS
import com.jvillada.movi.ui.ai.preguntasParaTocar
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.CifraProtagonista
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.OjoDeLosSaldos
import com.jvillada.movi.ui.components.conCifrasOcultas
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TramoDelPatrimonio
import com.jvillada.movi.ui.dashboard.cifraContando
import com.jvillada.movi.ui.dashboard.CuentaHero
import com.jvillada.movi.ui.dashboard.cuentasDelHero
import com.jvillada.movi.ui.dashboard.destinoDelTramo
import com.jvillada.movi.ui.dashboard.fraccionQueEntro
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.dashboard.heroBalanceTitle
import com.jvillada.movi.ui.dashboard.patrimonioDelInicio
import com.jvillada.movi.ui.dashboard.patrimonioExplicacion
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.TAG_ESQUELETO_CIFRA_DEL_HERO
import com.jvillada.movi.ui.dashboard.TAG_ESQUELETO_VEREDICTO_DEL_HERO
import com.jvillada.movi.ui.dashboard.TAG_TARJETA_DEL_HERO
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.veredictoDelInicio
import com.jvillada.movi.ui.dashboard.veredictoCortoDelInicio
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.TAG_CIFRA_TE_QUEDAN
import com.jvillada.movi.ui.dashboard.TAG_ESQUELETO_DETALLE_DEL_HERO
import com.jvillada.movi.ui.dashboard.TeQuedanDeHoy
import com.jvillada.movi.ui.dashboard.alcanzaParaElDisponible
import com.jvillada.movi.ui.dashboard.teQuedanDelInicio
import com.jvillada.movi.ui.dashboard.elHeroePideLaCuenta
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

/** La línea del veredicto del héroe (la corta en pantalla, la larga como `contentDescription`). */
const val TAG_VEREDICTO_DEL_HERO: String = "veredicto-del-hero"

/** Lo que dice el héroe cuando el dueño todavía no tiene ninguna cuenta. */
internal const val DETALLE_DEL_HEROE_VACIO: String =
    "Crea la cuenta donde te llega la plata y Movi te dice cuánto te queda para el período."

/**
 * **El héroe del Inicio: «Te quedan $X», para cuántos días y cuánto da por día** (ola R1, camino 1).
 *
 * Contesta una sola pregunta —¿cuánto me queda para el período?— con la cifra de la columna «Período»
 * de la tarjeta Disponible de Plan ([teQuedanDelInicio], la misma función) en [VarianteDeCifra.Heroe],
 * el ojo de «Ocultar los saldos» junto al rótulo y, debajo, la línea «para N días · $X por día» (lo que
 * queda entre los días que faltan; vuelta de diseño 1).
 * El veredicto corto de la ola 3 va al final si existe.
 *
 * **Lo que salió** (ola R1): la barra Entró/Salió y la fila «Tu plata» con su desglose. Las dos
 * cifras tienen casa —el flujo en Plan y en «Tus períodos», Tu plata en Patrimonio— y en el Inicio se
 * repetían. Tampoco va en una tarjeta: el héroe va sobre el fondo, como en la maqueta del camino 1.
 *
 * Todo el héroe se toca y lleva a «Tus períodos».
 *
 * Cuando el disponible no se puede afirmar (sin ingresos ni plata anotados, o la carga terminó sin sus
 * lecturas) la cifra grande es «Tu plata» (`heroBalance`): es lo único honesto que hay para decir.
 * Mientras cargan las lecturas de «Te quedan», cada pieza es un esqueleto con su alto.
 */
@Composable
internal fun HeroDeUnVistazo(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit = {},
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
) {
    // `accounts` CONTESTÓ vacía (no `null`, que es el esqueleto): un «$0» de 42 sp sería la afirmación
    // más fuerte de la pantalla, y falsa para quien todavía no tiene ni una cuenta. El héroe entero se
    // vuelve el vacío que enseña, con la misma acción que «Primeros pasos».
    //
    // Con la forma del héroe y no la de un vacío de pantalla entera: el héroe mide un renglón, una
    // cifra, un detalle y el veredicto, y el vacío ocupa lo mismo (título, detalle y un botón chico),
    // así ver que no hay cuentas no lo agranda (la regla de los 8 dp de `EsqueletosDelInicioTest`).
    if (elHeroePideLaCuenta(data)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Medida.margenDePantalla)
                .testTag(TAG_TARJETA_DEL_HERO),
            verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
        ) {
            Text("Aquí vas a ver tu plata", style = Movi.textos.titulo, color = Movi.colores.texto)
            Text(DETALLE_DEL_HEROE_VACIO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Espacio.xxs))
            Boton("Crear mi primera cuenta", onClick = onShowCreateSheet, tamano = TamanoDeBoton.S)
        }
        return
    }

    val teQuedan = teQuedanDelInicio(data, hoy)
    val entradaDeLaCifra = rememberProgresoDeEntrada("hero.cifra", listo = teQuedan != null || data.accounts != null)
    // Con «Te pasaste por $X» ya arriba, el veredicto no lo repite (ver [veredictoDelInicio]).
    val veredicto = veredictoDelInicio(data, hoy, conAvisoDelDisponible = teQuedan == null)
    // Ola 3 · Hoy en una mirada: en pantalla va la línea corta con cifras; la frase larga queda para
    // el lector de pantalla (ver [veredictoCortoDelInicio]).
    val veredictoCorto = veredictoCortoDelInicio(data, hoy, conAvisoDelDisponible = teQuedan == null)
    // Cada pieza se gatilla con SU propio dato, y solo con una carga en vuelo ([LocalCargandoElInicio]):
    // una carga en frío sin red que ya se rindió no puede quedar pulsando para siempre.
    val hayCargaEnVuelo = LocalCargandoElInicio.current
    val resumenCargando = data.summary == null && hayCargaEnVuelo
    // «Te quedan» cuando ya se sabe, y también mientras sus lecturas vienen en camino: mostrar «Tu
    // plata» y cambiarla por otra cifra un segundo después sería un salto y dos respuestas a la misma
    // pregunta. Si las lecturas llegaron y no hay disponible que afirmar, vuelve «Tu plata».
    val modoTeQuedan = teQuedan != null || (hayCargaEnVuelo && !alcanzaParaElDisponible(data))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Ver tus períodos") { onNavigate(Screen.Periodos) }
            .padding(horizontal = Medida.margenDePantalla)
            .testTag(TAG_TARJETA_DEL_HERO),
    ) {
        if (modoTeQuedan) {
            RotuloConElOjo {
                if (teQuedan != null) {
                    Text(text = teQuedan.rotulo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                } else {
                    LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.cuerpo)
                }
            }
            if (teQuedan != null) {
                // El MISMO valor que la frase del disponible de Plan, entero: es la cifra principal del
                // dueño, «$4.963.400» y no «$5M». Sin signo: el rótulo ya dice de qué lado está.
                Cifra(
                    cifraContando(teQuedan.monto, entradaDeLaCifra),
                    VarianteDeCifra.Heroe,
                    tono = if (teQuedan.enRojo) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
                    modifier = Modifier.testTag(TAG_CIFRA_TE_QUEDAN),
                )
                Text(text = teQuedan.detalle, style = Movi.textos.apoyo, color = colorDelDetalle(teQuedan.nivelDelDetalle))
            } else {
                LineaEsqueleto(
                    fraccionDelAncho = 0.55f,
                    estilo = Movi.textos.cifra,
                    modifier = Modifier.testTag(TAG_ESQUELETO_CIFRA_DEL_HERO),
                )
                LineaEsqueleto(
                    fraccionDelAncho = 0.6f,
                    estilo = Movi.textos.apoyo,
                    modifier = Modifier.testTag(TAG_ESQUELETO_DETALLE_DEL_HERO),
                )
            }
        } else {
            // El rótulo viaja en el binario, no en la fila: ver [HERO_BALANCE_TITLE].
            RotuloConElOjo {
                Text(text = heroBalanceTitle(section), style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            }
            val balance = heroBalance(data.accounts.orEmpty(), data.apartados.orEmpty())
            if (data.accounts == null) {
                // Un guion mientras las cuentas no contestan (ni vienen en camino): nunca un «$0».
                Text(text = "—", style = Movi.textos.cifra, color = Movi.colores.texto)
            } else {
                Cifra(
                    cifraContando(balance.tuPlata, entradaDeLaCifra),
                    VarianteDeCifra.Heroe,
                    tono = if (balance.tuPlata < 0) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
                )
            }
            encabezadoDelPeriodo(data, conDias = true)?.let { rango ->
                Text(text = rango, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        if (resumenCargando) {
            Spacer(Modifier.height(Espacio.xs))
            LineaEsqueleto(
                fraccionDelAncho = 0.65f,
                estilo = Movi.textos.cuerpo,
                modifier = Modifier.testTag(TAG_ESQUELETO_VEREDICTO_DEL_HERO),
            )
        } else if (veredicto != null && veredictoCorto != null) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = veredictoCorto,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag(TAG_VEREDICTO_DEL_HERO)
                    .semantics { contentDescription = veredicto.frase },
            )
        }
    }
}

/**
 * **El rótulo del héroe con el ojo de «Ocultar los saldos» al final de su línea** (junto a «Te
 * quedan» o «Tu plata»). El ojo va siempre, también mientras el rótulo es un esqueleto: aparecer
 * después movería la línea.
 *
 * **La línea mide lo que mide el rótulo**, no el ojo: el ojo (24 dp) se centra sobre ella y puede
 * sobresalir un poco arriba y abajo. Con una `Row` la línea crecía al alto del ojo, más con el
 * esqueleto que con el texto, y el héroe cargando dejaba de medir lo que mide cargado
 * (`EsqueletosDelInicioTest`).
 */
@Composable
private fun RotuloConElOjo(rotulo: @Composable () -> Unit) {
    Layout(
        content = {
            Box { rotulo() }
            OjoDeLosSaldos()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { medibles, limites ->
        val ojo = medibles[1].measure(limites.copy(minWidth = 0, minHeight = 0))
        val anchoDelRotulo = (limites.maxWidth - ojo.width).coerceAtLeast(0)
        val texto = medibles[0].measure(limites.copy(minWidth = anchoDelRotulo, maxWidth = anchoDelRotulo, minHeight = 0))
        layout(limites.maxWidth, texto.height) {
            texto.place(0, 0)
            ojo.place(limites.maxWidth - ojo.width, (texto.height - ojo.height) / 2)
        }
    }
}

@Composable
private fun colorDelDetalle(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.textoMedio
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}

/**
 * El saldo de una cuenta debajo del tramo «Tu plata» de «Tu patrimonio» ([PatrimonioSection]):
 * la marca del banco en chiquito, el nombre y su [com.jvillada.movi.ui.components.ds.Cifra] en su
 * moneda, `Chica` y apagada (es un desglose). Cuelga de la fila con `BajoLaFila`.
 */
@Composable
private fun FilaDeCuentaDelHero(cuenta: CuentaHero) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // La marca del banco en chiquito (Ola 2a), o el ícono del tipo de cuenta.
        AvatarDeLaCuenta(nombre = cuenta.nombre, tipo = cuenta.tipo, tamano = TamanoDeAvatar.Linea)
        Spacer(Modifier.width(Espacio.xs))
        Text(
            text = cuenta.nombre,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Espacio.xs))
        Cifra(cuenta.saldo, VarianteDeCifra.Chica, tono = TonoDeCifra.Apagado, moneda = cuenta.moneda)
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
internal fun encabezadoDelPeriodo(data: DashboardData, conDias: Boolean = true): String? {
    val periodo = data.periodoActual ?: return null
    val rango = rangoLegibleDe(periodo, data.ajustesDePeriodo) ?: return null
    if (!conDias) return rango
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
    val preguntas = remember(data) { preguntasParaTocar(data) }
    // Mismo criterio que el hero: sin cuentas ni resumen, `preguntasSugeridas` no tiene ninguna
    // señal propia y cae en `PREGUNTAS_DE_RESPALDO` — tres preguntas genéricas que no son «lo que
    // viene» sino un relleno. Task 7: en la primera carga se ve el esqueleto de esas tres filas, no
    // el relleno genérico haciéndose pasar por dato real.
    //
    // Esta sección SÍ se queda con una sola condición para las dos cosas (a diferencia del hero,
    // Task 7 fix round 1): no tiene piezas independientes que puedan llegar en momentos distintos,
    // «accounts» y «summary» entran juntas acá. Pero sí necesita `LocalCargandoElInicio` por el
    // mismo motivo que el hero: sin eso, una carga en frío sin red que ya se rindió dejaba las tres
    // filas pulsando para siempre en vez de caer en las preguntas de respaldo de antes.
    val cargando = data.accounts == null && data.summary == null && LocalCargandoElInicio.current

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        Seccion(titulo = section.title ?: "Pregúntale a Movi")
        // Vuelta de diseño 19: una `Superficie` de lista con una `Fila` por pregunta (el ícono de
        // Movi AI en su círculo, la flecha al final) y el `Separador` del sistema entre ellas.
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            if (cargando) {
                repeat(CUANTAS_PREGUNTAS_SUGERIDAS) { i ->
                    FilaDePreguntaEsqueleto()
                    if (i < CUANTAS_PREGUNTAS_SUGERIDAS - 1) Separador()
                }
            } else {
                preguntas.forEachIndexed { i, pregunta ->
                    // Se pinta con las cifras tapadas si los saldos están ocultos, pero viaja
                    // entera: Movi AI tiene que saber de qué cifra se le pregunta. Ola 3: el chip
                    // dice la versión corta; la que se manda (y la que oye el lector de pantalla)
                    // es la completa.
                    FilaDePregunta(conCifrasOcultas(pregunta.corta), completa = conCifrasOcultas(pregunta.texto)) {
                        onNavigate(Screen.AIChat(preguntaInicial = pregunta.texto, sugerida = pregunta.sugerida?.id))
                    }
                    if (i < preguntas.lastIndex) Separador()
                }
            }
            // El campo se ve igual cargando o no: se puede preguntar sin datos. Con el margen de la
            // fila a los lados y el relleno de la superficie abajo.
            Box(
                modifier = Modifier.padding(
                    start = Medida.margenDePantalla,
                    end = Medida.margenDePantalla,
                    top = Espacio.xs,
                    bottom = Medida.rellenoDeSuperficie - Espacio.xxs,
                ),
            ) {
                CampoParaPreguntar { onNavigate(Screen.AIChat()) }
            }
        }
    }
}

/**
 * El tag de cada fila esqueleto de «Pregúntale a Movi» (Task 7): tres preguntas genéricas y tres
 * filas esqueleto se ven distinto pero las dos son tres `Row`, así que contarlas por texto no
 * alcanza para probar que lo que se ve es el esqueleto y no el relleno.
 */
const val TAG_FILA_ESQUELETO_PREGUNTA: String = "fila-esqueleto-pregunta"

/** El esqueleto de [FilaDePregunta]: la `Fila` del sistema sin el texto, del mismo alto. */
@Composable
private fun FilaDePreguntaEsqueleto() {
    Box(modifier = Modifier.testTag(TAG_FILA_ESQUELETO_PREGUNTA)) {
        EsqueletoDeFila(conSubtitulo = false, conFinal = false)
    }
}

/**
 * Una pregunta para tocar: la `Fila` del sistema. [pregunta] es la versión corta que se ve;
 * [completa], la que oye el lector de pantalla (y la que se manda).
 */
@Composable
private fun FilaDePregunta(pregunta: String, completa: String = pregunta, onClick: () -> Unit) {
    Fila(
        titulo = pregunta,
        modifier = if (completa != pregunta) Modifier.semantics { contentDescription = completa } else Modifier,
        inicio = { IconoEnCirculo(Icons.Rounded.AutoAwesome) },
        final = { Chevron() },
        onClick = onClick,
        onClickLabel = "Preguntarle a Movi",
    )
}

@Composable
private fun CampoParaPreguntar(onClick: () -> Unit) {
    val forma = RoundedCornerShape(Movi.formas.pleno)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .border(Medida.trazo, Movi.colores.borde, forma)
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
            modifier = Modifier.size(Icono.m),
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
 * - Debajo de «Tu plata», el saldo de cada cuenta, siempre a la vista (a diferencia del hero, donde
 *   el mismo desglose —[FilaDeCuentaDelHero], la MISMA lista— se pliega bajo un chevron: ver
 *   [HeroDeUnVistazo]). Lo pidió el dueño («no el total sino el disponible en cada cuenta»).
 */
@Composable
internal fun PatrimonioSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    val patrimonio = patrimonioDelInicio(data) ?: return
    val entrada = rememberProgresoDeEntrada("patrimonio")
    val cuentas = cuentasDelHero(data.accounts, data.apartados.orEmpty()).orEmpty()

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        Seccion(titulo = section.title ?: "Tu patrimonio")
        // Vuelta de diseño 19: arriba el neto y la barra con `CifraConRotulo` (las cifras enteras,
        // como en el resto de la app: «$1,5M» al lado del héroe entero se leía como otro número);
        // abajo los tramos, una `Fila` cada uno, en la misma superficie de lista.
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Medida.rellenoDeSuperficie,
                    vertical = Medida.rellenoDeSuperficie - Espacio.xxs,
                ),
            ) {
                // El neto en el color del texto, nunca en rojo: es estructura de largo plazo.
                CifraConRotulo("Patrimonio neto", patrimonio.neto, variante = VarianteDeCifra.Grande)
                Spacer(Modifier.height(Espacio.s))
                BarraDeDosTramos(
                    fraccionIzquierda = patrimonio.fraccionQueTienes,
                    colorIzquierda = Movi.colores.entra,
                    // Las deudas no son una alerta (Ola 2a): son estructura de largo plazo, en neutro.
                    colorDerecha = Movi.colores.neutro,
                    entrada = entrada,
                )
                Spacer(Modifier.height(Espacio.xs))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CifraConRotulo("Tienes", patrimonio.tienes, Modifier.weight(1f))
                    Spacer(Modifier.width(Espacio.xs))
                    CifraConRotulo("Debes", patrimonio.debes, alFinal = true)
                }
            }
            Separador(SangriaDeSeparador.Ninguna)
            patrimonio.tramos.forEachIndexed { i, tramo ->
                FilaDeTramo(tramo) { onNavigate(destinoDelTramo(tramo)) }
                if (tramo.nombre == "Tu plata" && cuentas.size > 1) {
                    BajoLaFila {
                        cuentas.forEach { cuenta -> FilaDeCuentaDelHero(cuenta) }
                    }
                }
                if (i < patrimonio.tramos.lastIndex) Separador()
            }
        }
    }
}

/**
 * Un tramo del patrimonio: la `Fila` del sistema, con el ícono de lo que es en su círculo —verde lo
 * que tienes, neutro lo que debes, como los dos lados de la barra— y la cifra entera al final.
 */
@Composable
private fun FilaDeTramo(tramo: TramoDelPatrimonio, onClick: () -> Unit) {
    Fila(
        titulo = tramo.nombre,
        inicio = {
            IconoEnCirculo(iconoDelTramo(tramo), tono = if (tramo.esDeuda) TonoDeIcono.Neutro else TonoDeIcono.Exito)
        },
        final = { Cifra(tramo.monto) },
        onClick = onClick,
    )
}

/** El ícono de un tramo, por lo que es. */
private fun iconoDelTramo(tramo: TramoDelPatrimonio): ImageVector = when {
    tramo.esDeuda -> Icons.Rounded.CreditCard
    tramo.nombre == "Tu plata" -> Icons.Rounded.AccountBalanceWallet
    tramo.nombre.startsWith("Apartado") -> Icons.Rounded.Savings
    tramo.nombre.startsWith("Uso condicionado") -> Icons.Rounded.Lock
    tramo.nombre == "Bienes" -> Icons.Rounded.Home
    else -> Icons.Rounded.AccountBalance
}
