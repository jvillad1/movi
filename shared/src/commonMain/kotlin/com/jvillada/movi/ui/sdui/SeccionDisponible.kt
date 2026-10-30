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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.dashboard.DisponibleDelPeriodo
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Cifra as CifraDelSistema
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.FormaDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.VentanaDelDisponible
import com.jvillada.movi.ui.dashboard.CascadaDelDisponibleEnPantalla
import com.jvillada.movi.ui.dashboard.DE_DONDE_SALE
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.AlertaDelDisponible
import com.jvillada.movi.ui.dashboard.MIRA_EN_QUE_SE_FUE
import com.jvillada.movi.ui.dashboard.ROTULO_TE_QUEDAN
import com.jvillada.movi.ui.dashboard.ROTULO_DISPONIBLE_DEL_PERIODO
import com.jvillada.movi.ui.dashboard.alertaDelDisponible
import com.jvillada.movi.ui.dashboard.barraEnAlerta
import com.jvillada.movi.ui.dashboard.teQuedanDeHoy
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.CifraConRotulo
import com.jvillada.movi.ui.components.ds.DisposicionDeCifraConRotulo
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.rotuloDeLaSemana
import com.jvillada.movi.ui.Screen

/**
 * # «Disponible»: cuánto queda del período, compacto y con UNA frase
 *
 * El dueño la pidió así: *«Disponible en el periodo · por semana · por día»*. Se queda, pero baja de
 * lugar (debajo de «Falta por pagar», que son justamente los fijos que resta) y se achica: la cifra
 * del disponible, **una** frase de cómo viene y tres columnas —el período, la semana, hoy— con lo
 * que te queda por gastar en la semana y hoy, lo que ya gastaste y una barra que crece al cargar.
 * En el período, lo gastado y de cuánto: lo que le queda ya lo dice la frase (ver [ColumnaDeVentana]).
 *
 * **Una sola frase, no tres.** Cada fila decía la suya, y con el período pasado el dueño leía «Te
 * pasaste por $563.456» en rojo y, un renglón abajo, «Vas bien: te quedan … para esta semana». La
 * frase ahora es [fraseDelDisponible]: manda la peor ventana y nunca dice «vas bien». Y el veredicto
 * del hero sale de la misma cuenta ([avisoDelDisponible]), así que arriba y abajo no pueden decir
 * cosas opuestas.
 *
 * De dónde sale el disponible —lo que tenías el 25, lo que entró, los fijos— queda detrás de «¿De
 * dónde sale?»: es la explicación, no la noticia. Desde la vuelta de diseño 28 es la cascada de
 * [CascadaDelDisponibleEnPantalla], la misma que abre el héroe de Hoy.
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
    onNavigate: (Screen) -> Unit = {},
) {
    val disponible = disponibleDelInicio(data, hoy) ?: return
    Column {
        // Ola 3 · Hoy en una mirada: sin la frase. «Te quedan $X para N días · $Y por día» (o
        // la que mande: te pasaste, sin margen…) ya la dice el héroe de arriba con la MISMA cuenta
        // (`teQuedanDeHoy` sobre el mismo `disponibleDelInicio`), y la sección solo se pinta cuando
        // el héroe la tiene. En Hoy quedan la cifra y las tres columnas; en Plan, la frase sigue.
        TarjetaDelDisponible(titulo = section.title ?: "Disponible", disponible = disponible, comoEnPagos = false)
        // Vuelta 75: «Tu día más justo» ya no va acá: es el renglón «¿Me alcanza?» del héroe, que se ve
        // aunque esta sección esté oculta (ver `RenglonMeAlcanzaDelHeroe`). Dos veces sería repetirlo.
    }
}

/**
 * **El rótulo de la columna de hoy** (vuelta de diseño 28). Decía «Hoy», y su cifra —lo que queda de la
 * meta diaria, el disponible entre los días del período— competía con la otra cifra «por día» de la
 * app, la del héroe y la frase de Plan («$2,2M por día»: lo que queda entre los días que faltan,
 * `porDiaParaLoQueQueda`). Con su nombre ya no se confunden: esa es la única «por día»; esta es la meta.
 */
const val ROTULO_META_DE_HOY: String = "Meta de hoy"

/**
 * **La semana y hoy cuando ya no queda nada** (vuelta 60): «$0», y en el último renglón esto en vez de
 * «de $X». Las metas de la semana y de hoy salen de lo que queda (ver `DisponibleDelPeriodo`), así que
 * con el período pasado no hay plata que repartir y la columna no ofrece una meta positiva.
 */
const val YA_TE_PASASTE: String = "ya te pasaste"

/** El renglón que va debajo de lo que queda en la semana y hoy: «gastaste», y la cifra. */
const val GASTASTE: String = "gastaste"

/**
 * El mismo renglón en la columna del período (vuelta 60): su cifra es el gasto **del día a día** —sin
 * los fijos—, y «gastaste» es el verbo con el que «En qué se va» dice todo lo que salió («Llevas
 * gastado $4.888.850»). Dos cifras distintas con el mismo nombre en pantallas vecinas; la revisión de
 * la 70 lo volvió a ver. Se queda «en el día a día».
 */
const val ROTULO_DIA_A_DIA_DEL_PERIODO: String = "en el día a día"

/** El último renglón de una columna: «de $3,6M», la meta de esa ventana. */
internal fun deLaMeta(meta: Long): String = "de ${formatMoneyCompact(meta)}"

/** La tarjeta del disponible, cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_DISPONIBLE: String = "tarjeta-del-disponible"

/** La cifra esqueleto del disponible — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_DISPONIBLE: String = "esqueleto-del-disponible"

/** La barra de una columna (período, semana, hoy): está solo cuando hay margen contra qué medir. */
const val TAG_BARRA_DE_VENTANA: String = "barra-de-ventana"

/** La barra en alerta (vuelta 45): a lo sumo una, la del período pasado. */
const val TAG_BARRA_EN_ALERTA: String = "barra-de-ventana-en-alerta"

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
    /**
     * Vuelta 45: **Pagos dice lo mismo que el Inicio.** `true` (Pagos): la cifra protagonista es «Te
     * quedan» —la del héroe, de [teQuedanDeHoy] sobre el mismo disponible— con su detalle, el total del
     * período baja a un renglón («Disponible del período», el nombre de la vuelta 28) y la alarma sale
     * acá, una vez, con «Mira en qué se fue ›» ([alertaDelDisponible]). `false` (el Inicio, que ya
     * tiene el héroe arriba): el total del período como cifra y las tres columnas, sin frase ni alarma.
     */
    comoEnPagos: Boolean = true,
    /** Vuelta 78 (revisión): el renglón «¿Me alcanza?», para callar «Para N días» como el héroe. */
    renglon: com.jvillada.movi.ui.dashboard.RenglonMeAlcanza? = null,
    /** «Mira en qué se fue ›»: abre «En qué se va». `null` no pinta el enlace. */
    onVerEnQueSeVa: (() -> Unit)? = null,
) {
    val entrada = rememberProgresoDeEntrada("disponible", listo = disponible != null)
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = Medida.margenDePantalla)) {
        // El «¿De dónde sale?» está desde el primer cuadro, también cargando: es parte del alto del
        // encabezado (su letra es más alta que la del rótulo). Mientras carga no hace nada — no hay
        // de dónde, y si el toque quedara guardado la tarjeta llegaría ya abierta y más alta que
        // su esqueleto.
        Seccion(
            titulo = titulo,
            accion = if (verDeDondeSale) "Ocultar" else DE_DONDE_SALE,
            onAccion = { if (disponible != null) verDeDondeSale = !verDeDondeSale },
        )
        Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_DISPONIBLE)) {
            if (disponible == null) {
                CuerpoDelDisponibleEsqueleto()
            } else {
                CuerpoDelDisponible(disponible, verDeDondeSale, entrada, comoEnPagos, onVerEnQueSeVa, renglon)
            }
        }
    }
}

/** Vuelta 78: la línea de abajo de «Te quedan» en Pagos, cuando se dice. */
const val TAG_DETALLE_TE_QUEDAN_EN_PAGOS: String = "detalle-te-quedan-en-pagos"

/** La cifra «Te quedan» de la tarjeta de Pagos: la misma del héroe del Inicio. */
const val TAG_CIFRA_TE_QUEDAN_EN_PAGOS: String = "cifra-te-quedan-en-pagos"

/** La alarma de la tarjeta de Pagos (vuelta 45). */
const val TAG_ALERTA_DEL_DISPONIBLE: String = "alerta-del-disponible"

@Composable
private fun CuerpoDelDisponible(
    disponible: DisponibleDelPeriodo,
    verDeDondeSale: Boolean,
    entrada: Float,
    comoEnPagos: Boolean,
    onVerEnQueSeVa: (() -> Unit)?,
    renglon: com.jvillada.movi.ui.dashboard.RenglonMeAlcanza? = null,
) {
    if (comoEnPagos) {
        // Vuelta 45: la protagonista es «Te quedan», la MISMA cifra del héroe del Inicio, con la MISMA
        // función ([teQuedanDeHoy]) sobre el MISMO disponible. Pagos encabezaba con el total del
        // período ($4.536.800) y dejaba lo que queda en la letra chica de la frase; el dueño leía dos
        // cifras distintas para «¿cuánto me queda?» en Inicio y en Pagos. Entera, en `Grande`: la héroe
        // de Pagos es «Falta por pagar» (una sola héroe por pantalla).
        val teQuedan = teQuedanDeHoy(disponible)
        Text(text = teQuedan.rotulo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        CifraDelSistema(
            teQuedan.monto,
            VarianteDeCifra.Grande,
            tono = if (teQuedan.enRojo) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
            modifier = Modifier.testTag(TAG_CIFRA_TE_QUEDAN_EN_PAGOS),
        )
        Spacer(Modifier.height(Movi.espacios.minimo))
        // Vuelta 78 (revisión): la misma regla del héroe ([detalleDelHeroe]): a tres días del cierre o si no
        // alcanza, «Para N días · $X por día» se calla. Su renglón queda (sin verse ni leerse): el renglón
        // «¿Me alcanza?» llega después que el disponible, y la tarjeta no puede saltar (`PlanScreenTest`).
        val detalle = com.jvillada.movi.ui.dashboard.detalleDelHeroe(teQuedan, renglon)
        Text(
            text = detalle ?: teQuedan.detalle.replaceFirstChar { it.uppercase() },
            style = Movi.textos.apoyo,
            color = colorDeLaFrase(teQuedan.nivelDelDetalle),
            modifier = if (detalle == null) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier.testTag(TAG_DETALLE_TE_QUEDAN_EN_PAGOS),
        )
        Spacer(Modifier.height(Movi.espacios.minimo))
        // El total del período, en su renglón: es el punto de partida, no la noticia. Con el nombre que
        // le fijó la vuelta 28 («un nombre por concepto»), el mismo de la cascada.
        CifraConRotulo(
            rotulo = ROTULO_DISPONIBLE_DEL_PERIODO,
            monto = disponible.disponible,
            variante = VarianteDeCifra.Chica,
            tono = if (disponible.disponible < 0L) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
            disposicion = DisposicionDeCifraConRotulo.EnRenglon,
        )
        alertaDelDisponible(disponible)?.let { alerta ->
            Spacer(Modifier.height(Movi.espacios.minimo))
            AlertaDeLaTarjeta(alerta, onVerEnQueSeVa)
        }
    } else {
        // Ola R4b: la cifra principal es la `Cifra.Grande` del sistema, entera («$3.240.000», no «$3,2M»)
        // y con «Ocultar los saldos» incluido; el rótulo va arriba, como en el camino 1. En rojo
        // (`Alerta`) solo sin margen: gastar no es una alarma, pasarse sí.
        Text(text = ROTULO_DISPONIBLE_DEL_PERIODO, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        CifraDelSistema(
            disponible.disponible,
            VarianteDeCifra.Grande,
            tono = if (disponible.hayMargen) TonoDeCifra.Neutro else TonoDeCifra.Alerta,
        )
    }
    if (verDeDondeSale) {
        Spacer(Modifier.height(Espacio.s))
        // Vuelta de diseño 28: la cascada renglón por renglón —lo que tenías, lo que entró, los fijos,
        // el ahorro, «Disponible del período», lo gastado y «Te quedan»—, el MISMO componente que abre
        // el héroe de Hoy. Eran dos líneas de texto («Tenías $X el 25 · entraron $Y») que no sumaban
        // a la vista.
        CascadaDelDisponibleEnPantalla(disponible)
    }
    Spacer(Modifier.height(Movi.espacios.medio))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        ColumnaDeVentana("Período", disponible.periodo, disponible, entrada, Modifier.weight(1f), delPeriodo = true)
        ColumnaDeVentana(rotuloDeLaSemana(disponible), disponible.semana, disponible, entrada, Modifier.weight(1f))
        ColumnaDeVentana(ROTULO_META_DE_HOY, disponible.hoy, disponible, entrada, Modifier.weight(1f))
    }
}

/**
 * **La alarma, una vez y con qué hacer** (vuelta 45): la frase de qué pasó en `sale` —si la cifra de
 * arriba no lo dice ya— y el enlace «Mira en qué se fue ›», que abre «En qué se va».
 */
@Composable
private fun AlertaDeLaTarjeta(alerta: AlertaDelDisponible, onVerEnQueSeVa: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_ALERTA_DEL_DISPONIBLE)) {
        alerta.texto?.let { Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
        if (onVerEnQueSeVa != null) {
            Boton(
                MIRA_EN_QUE_SE_FUE,
                onClick = onVerEnQueSeVa,
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                icono = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            )
        }
    }
}

/**
 * [CuerpoDelDisponible] sin ninguna cifra: el rótulo «Disponible del período» (no es un dato, es
 * el nombre del renglón) con un bloque donde va la cifra, un renglón para la frase y las tres
 * columnas con su rótulo, lo que queda, los tres renglones de «gastaste $… de $…» y la barra. Mismos
 * espacios y estilos que la real, para que al llegar el dato no cambie de alto (±8 dp, lo mide
 * `PlanScreenTest`).
 */
@Composable
private fun CuerpoDelDisponibleEsqueleto() {
    // Vuelta 45: el rótulo de la cifra es «Te quedan» (en Pagos, el único lugar que pinta esqueleto).
    Text(text = ROTULO_TE_QUEDAN, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
    BloqueEsqueleto(
        alto = altoDeUnRenglon(Movi.textos.cifraDeTarjeta),
        ancho = Medida.avatar * 4,
        modifier = Modifier.testTag(TAG_ESQUELETO_DEL_DISPONIBLE),
    )
    Spacer(Modifier.height(Movi.espacios.minimo))
    LineaEsqueleto(fraccionDelAncho = 0.75f, estilo = Movi.textos.apoyo)
    Spacer(Modifier.height(Movi.espacios.minimo))
    // El renglón «Disponible del período … $X».
    LineaEsqueleto(fraccionDelAncho = 1f, estilo = Movi.textos.apoyo)
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
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                Spacer(Modifier.height(Movi.espacios.minimo))
                BloqueEsqueleto(alto = Medida.altoDeBarraDeProgreso)
            }
        }
    }
}

/**
 * Una ventana en su columna: el rótulo, **lo que te queda por gastar en ella**, lo que ya gastaste
 * de cuánto, y la barra.
 *
 * El dueño, el 28-sep: *«yo quiero que esto me marque cuánto me puedo gastar por cada una de esas
 * unidades de tiempo no cuánto me gasté, son cosas muy diferentes»*. La cifra grande es
 * [VentanaDelDisponible.teQuedan] y debajo, chico, lo gastado y la meta.
 *
 * **Vuelta 70 · las tres columnas con el mismo patrón** (`r4-pagos-390-b.png`). El período era la
 * excepción: su cifra grande era lo gastado («$2,2M en el día a día de $3,6M») mientras la semana y
 * hoy decían lo que queda («$1,4M · gastaste $603.100»): tres columnas lado a lado midiendo cosas
 * distintas. Ahora las tres dicen lo mismo: en grande lo que queda —en el período, la MISMA cifra que
 * «Te quedan» del héroe de Inicio y de la tarjeta de Pagos ([teQuedanDeHoy] lee el mismo
 * `periodo.teQuedan`)—, y debajo «gastaste» · $Y · «de $Z» (en el período, «en el día a día» en vez de
 * «gastaste», ver [ROTULO_DIA_A_DIA_DEL_PERIODO]). Ninguna cifra cambió, solo dónde va.
 *
 * Tres renglones chicos y no «gastaste $X de $Y» en uno: a 390 dp, con la letra de la app, ni
 * «gastaste $440.000» entra en su tercio de tarjeta (`LasCifrasDelDisponibleEntranTest`). Los mismos
 * renglones y estilos en las tres columnas, así que sus barras quedan a la misma altura.
 *
 * **Las cifras se ven siempre, con o sin margen.** Sin margen la meta es cero, así que lo que queda es
 * menos lo gastado: «$0» si no gastaste nada, y el negativo si ya gastaste. La semana y hoy, cuando ya
 * no queda nada que repartir (vuelta 60), dicen «$0» y en el último renglón «ya te pasaste».
 *
 * **La barra se va sin margen**: mide lo gastado contra la meta, y contra cero no tiene largo que
 * signifique algo.
 */
@Composable
private fun ColumnaDeVentana(
    rotulo: String,
    ventana: VentanaDelDisponible,
    disponible: DisponibleDelPeriodo,
    entrada: Float,
    modifier: Modifier,
    delPeriodo: Boolean = false,
) {
    val hayMargen = disponible.hayMargen
    // Vuelta 45: **solo lo que de verdad importa va en alerta**: la columna del período si te pasaste
    // del período. La semana y la meta de hoy van en tinta aunque estén pasadas (su «−$1,3M» ya lo
    // dice el signo, y la alarma, con qué hacer, sale una vez arriba).
    val enAlerta = barraEnAlerta(disponible, delPeriodo) ||
        (delPeriodo && !hayMargen && ventana.nivel == NivelDelGasto.PASADO)
    // Vuelta 60: la semana y hoy con el período ya pasado dicen «$0 · ya te pasaste», no una meta.
    val sinNadaQueRepartir = !delPeriodo && disponible.yaTePasaste
    Column(modifier = modifier) {
        Text(text = rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
        // Vuelta de diseño 19: la `Cifra` del sistema, compacta porque son tres columnas (§6).
        CifraDelSistema(
            if (sinNadaQueRepartir) 0L else ventana.teQuedan,
            tono = if (enAlerta) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
            forma = FormaDeCifra.Compacta,
        )
        Text(
            text = if (delPeriodo) ROTULO_DIA_A_DIA_DEL_PERIODO else GASTASTE,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            maxLines = 1,
        )
        Text(
            text = formatMoneyCompact(ventana.gastado),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            maxLines = 1,
        )
        Text(
            text = if (sinNadaQueRepartir) YA_TE_PASASTE else deLaMeta(ventana.meta),
            style = Movi.textos.apoyo,
            color = if (sinNadaQueRepartir) Movi.colores.textoMedio else Movi.colores.textoApagado,
            maxLines = 1,
        )
        if (hayMargen) {
            Spacer(Modifier.height(Movi.espacios.minimo))
            // La barra del sistema: pista en la capa 3, relleno del nivel. Sin gasto no hay relleno
            // (una barra vacía dice eso); con algo gastado, un mínimo del 2 % para que se vea.
            val fraccion = ventana.fraccion
            val relleno = if (fraccion != null && ventana.gastado > 0L) {
                fraccion.coerceIn(0.02f, 1f) * entrada.coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(if (enAlerta) TAG_BARRA_EN_ALERTA else TAG_BARRA_DE_VENTANA),
            ) {
                BarraDeProgreso(fraccion = relleno, tono = tonoDeLaBarra(enAlerta))
            }
        }
    }
}

/**
 * Vuelta 45: las barras van **en tinta** ([TonoDeBarra.Marca], que desde la vuelta 29 es la tinta) y solo
 * la del período pasado va en peligro. Antes cada una tenía el color de su nivel y con la semana y el día
 * pasados se veían tres barras rojas.
 */
internal fun tonoDeLaBarra(enAlerta: Boolean): TonoDeBarra = if (enAlerta) TonoDeBarra.Peligro else TonoDeBarra.Marca

@Composable
private fun colorDeLaFrase(nivel: NivelDelGasto): Color = when (nivel) {
    NivelDelGasto.BIEN -> Movi.colores.textoMedio
    NivelDelGasto.CERCA -> Movi.colores.aviso
    NivelDelGasto.PASADO -> Movi.colores.sale
}
