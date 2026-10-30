package com.jvillada.movi.ui.sdui

import com.jvillada.movi.ui.components.ds.CambioDelDueno
import com.jvillada.movi.ui.components.ds.rememberCifraQueCuenta
import com.jvillada.movi.data.FormaDelHeroe
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.jvillada.movi.shared.model.nombreDelPeriodo
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
import com.jvillada.movi.ui.components.conCifrasOcultas
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TramoDelPatrimonio
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
import com.jvillada.movi.ui.dashboard.TAG_TARJETA_DEL_HERO
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.dashboard.NivelDelGasto
import com.jvillada.movi.ui.dashboard.TAG_CIFRA_TE_QUEDAN
import com.jvillada.movi.ui.dashboard.TAG_ESQUELETO_DETALLE_DEL_HERO
import com.jvillada.movi.ui.dashboard.TeQuedanDeHoy
import com.jvillada.movi.ui.dashboard.alcanzaParaElDisponible
import com.jvillada.movi.ui.dashboard.HojaDeDondeSale
import com.jvillada.movi.ui.dashboard.conteoPorRevisarDelInicio
import com.jvillada.movi.ui.dashboard.EstadoDelHeroe
import com.jvillada.movi.ui.dashboard.rememberEstadoQueCambia
import androidx.compose.ui.graphics.graphicsLayer
import com.jvillada.movi.ui.porrevisar.desgloseDePorRevisar
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.porrevisar.textoDePorRevisar
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.TonoDePildora
import androidx.compose.material.icons.rounded.Schedule
import com.jvillada.movi.ui.dashboard.VER_TUS_PERIODOS
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.dashboard.teQuedanDeHoy
import com.jvillada.movi.ui.dashboard.elHeroePideLaCuenta
import com.jvillada.movi.ui.dashboard.FilaMeAlcanza
import com.jvillada.movi.ui.dashboard.TAG_DETALLE_DEL_HEROE
import com.jvillada.movi.ui.dashboard.detalleDelHeroe
import com.jvillada.movi.ui.dashboard.rememberRenglonMeAlcanza
import com.jvillada.movi.ui.dashboard.esUnCambioDelHeroe
import com.jvillada.movi.ui.dashboard.EstadoQueSeVe
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


/** Lo que dice el héroe cuando el dueño todavía no tiene ninguna cuenta. */
internal const val DETALLE_DEL_HEROE_VACIO: String =
    "Crea la cuenta donde te llega la plata y Movi te dice cuánto te queda para el período."

/** La tarjeta héroe entera (vuelta 39), con la franja «Por confirmar» si la hay. */
const val TAG_HEROE_EN_TARJETA: String = "inicio-heroe-en-tarjeta"

/** La franja «Por confirmar» de la tarjeta héroe. */
const val TAG_FRANJA_POR_CONFIRMAR: String = "inicio-franja-por-confirmar"

/** El enlace «Tu plata $X» de la tarjeta héroe. */
const val TAG_ENLACE_TU_PLATA: String = "inicio-enlace-tu-plata"

/**
 * **El héroe del Inicio, en su tarjeta** (vuelta 39, la opción 3): una [Superficie] destacada —blanca,
 * radio 22, la sombra suave— montada sobre la banda amarilla.
 *
 * - Arriba, el rótulo «Te quedan» (o «Te pasaste por»; vuelta 28: un nombre por concepto) y a la
 *   derecha el enlace **«Tu plata $X»** —la cifra de `patrimonioDe`, la misma de Patrimonio— que abre
 *   Patrimonio, donde vive.
 * - La cifra en [VarianteDeCifra.Heroe] ([teQuedanDelInicio], la de la columna «Período» de Plan) y
 *   debajo «Para N días · $X por día» (la única cifra por día, `porDiaParaLoQueQueda`).
 * - Tocar la cifra abre «¿De dónde sale?» ([HojaDeDondeSale], vuelta 28). Sin «Te quedan» la cifra es
 *   «Tu plata» y el toque lleva a «Tus períodos», como siempre.
 * - Abajo, separada por el hilo y en la capa 2, la franja **«Por confirmar»** con la pastilla «N
 *   avisos» ([FranjaPorConfirmar]), solo si hay algo que espera en Por revisar.
 *
 * **Mientras carga, nada dice $0**: sin instantánea, cada pieza es un esqueleto con su alto; con ella,
 * su cifra; un cambio después pasa entero, cifra, línea y «Tu plata» juntas ([rememberEstadoQueCambia], vuelta 75).
 *
 * El ojo de «Ocultar los saldos» se mudó al encabezado, sobre la banda (como en la maqueta). La frase
 * del veredicto («Gastaste $X · lo que más: Mercado») salió de la tarjeta: lo que se gastó está en «En
 * qué se va» y en Pagos.
 */
@Composable
internal fun HeroDeUnVistazo(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit = {},
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
) {
    // Vuelta 75 · la cifra grande, su línea, «Tu plata» y el renglón «¿Me alcanza?» salen de UN estado
    // y cambian juntos (ver [EstadoDelHeroe]): nunca la cifra vieja junto a la línea nueva. Revisión de
    // la 75: mientras la carga del Inicio está en vuelo lo visible se queda —las cuentas llegan antes que
    // el resumen y los pagos— y al terminar cambia entero.
    val disponible = disponibleDelInicio(data, hoy)
    val renglon = rememberRenglonMeAlcanza(data, hoy)
    val visto = rememberEstadoQueCambia(
        EstadoDelHeroe(
            teQuedan = disponible?.let(::teQuedanDeHoy),
            tuPlata = data.accounts?.let { heroBalance(it, data.apartados.orEmpty()).tuPlata },
            renglon = renglon,
        ),
        congelado = LocalCargandoElInicio.current,
        // Vuelta 79: si el cambio lo hizo el dueño (anotar, anular, deshacer), no se desvanece: la cifra
        // cuenta hasta la nueva ([rememberCifraQueCuenta], en el cuerpo).
        seAnima = { antes, despues -> esUnCambioDelHeroe(antes, despues) && !CambioDelDueno.pendiente() },
    )
    Superficie(
        modifier = Modifier
            .padding(horizontal = Medida.margenDePantalla)
            .fillMaxWidth()
            .testTag(TAG_HEROE_EN_TARJETA),
        destacada = true,
        relleno = RellenoDeSuperficie.Ninguno,
    ) {
        if (elHeroePideLaCuenta(data)) {
            // `accounts` CONTESTÓ vacía (no `null`, que es el esqueleto): un «$0» de 40 px sería la
            // afirmación más fuerte de la pantalla, y falsa. La tarjeta es el vacío que enseña, con la
            // misma acción que «Primeros pasos».
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Medida.rellenoDeSuperficie)
                    .testTag(TAG_TARJETA_DEL_HERO),
                verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
            ) {
                Text("Aquí vas a ver tu plata", style = Movi.textos.titulo, color = Movi.colores.texto)
                Text(DETALLE_DEL_HEROE_VACIO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                // Con el aire de un renglón más: la tarjeta vacía mide lo que mide cargando (rótulo,
                // cifra y detalle), y ver que no hay cuentas no la achica (`EsqueletosDelInicioTest`).
                Spacer(Modifier.height(Espacio.s))
                Boton("Crear mi primera cuenta", onClick = onShowCreateSheet, tamano = TamanoDeBoton.S)
            }
            return@Superficie
        }
        CuerpoDelHeroe(section, data, onNavigate, disponible, visto)
        // Vuelta 78: mientras la carga está en vuelo, los renglones que la tarjeta llevaba la última vez
        // (o los dos, la primera vez) se reservan con su esqueleto: en un arranque en frío sin instantánea,
        // el héroe medía ~115 px y al llegar los datos ~235: todo el Inicio bajaba ~110 px a los 1,3 s.
        // Solo con el héroe en esqueleto —lo que se VE, que se queda quieto mientras la carga está en vuelo
        // ([rememberEstadoQueCambia]); no lo que va llegando—: con la instantánea a la vista, sus renglones
        // salen de ella, como siempre.
        val cargando = LocalCargandoElInicio.current
        // Si esta carga empezó con el héroe vacío (un arranque en frío sin instantánea), la reserva dura toda
        // la carga: las cuentas llegan antes que el supuesto del gasto, y el cuerpo ya no es esqueleto.
        val empezoVacio = remember(cargando) { cargando && visto.mostrado.teQuedan == null && visto.mostrado.tuPlata == null }
        val enEsqueleto = cargando && empezoVacio
        val hayPorConfirmar = conteoPorRevisarDelInicio(data).total > 0
        val forma = remember { FormaRecordada.delAparato.heroe(SessionManager.userId) ?: FormaDelHeroe() }
        // Vuelta 75: «¿Me alcanza?», la caja de Pagos en un renglón, siempre a la vista; del MISMO estado.
        val renglonVisto = visto.mostrado.renglon
        when {
            renglonVisto != null -> FilaMeAlcanza(renglonVisto, onNavigate, modifier = Modifier.graphicsLayer { alpha = visto.opacidad })
            // El renglón ya se sabe pero el héroe todavía muestra lo de antes (la primera mitad del cambio):
            // su lugar se reserva igual, o el héroe crecería al llegar.
            renglon != null || (enEsqueleto && forma.meAlcanza) -> RenglonEsqueletoDelHeroe(TAG_ESQUELETO_ME_ALCANZA, enLaFranja = false)
        }
        when {
            hayPorConfirmar -> FranjaPorConfirmar(data, onNavigate)
            enEsqueleto && forma.porConfirmar -> RenglonEsqueletoDelHeroe(TAG_ESQUELETO_POR_CONFIRMAR, enLaFranja = true)
        }
        // Y cuando la carga terminó bien, se anota cómo quedó para el próximo arranque en frío.
        val tieneMeAlcanza = renglonVisto != null
        LaunchedEffect(cargando, tieneMeAlcanza, hayPorConfirmar, data.puedeAfirmarVacio) {
            if (!cargando && data.puedeAfirmarVacio) {
                FormaRecordada.delAparato.guardarHeroe(SessionManager.userId, FormaDelHeroe(meAlcanza = tieneMeAlcanza, porConfirmar = hayPorConfirmar))
            }
        }
    }
}

/** Vuelta 78: el esqueleto de «¿Me alcanza?» en el héroe, mientras carga. */
const val TAG_ESQUELETO_ME_ALCANZA: String = "inicio-esqueleto-me-alcanza"

/** Vuelta 78: el esqueleto de la franja «Por confirmar» en el héroe, mientras carga. */
const val TAG_ESQUELETO_POR_CONFIRMAR: String = "inicio-esqueleto-por-confirmar"

/**
 * Un renglón del héroe mientras carga (vuelta 78): el hilo de arriba y una [EsqueletoDeFila] con su
 * círculo y sus dos líneas —el alto de [FilaMeAlcanza] y de [FranjaPorConfirmar]—; la franja, en la capa 2.
 */
@Composable
private fun RenglonEsqueletoDelHeroe(tag: String, enLaFranja: Boolean) {
    Separador(SangriaDeSeparador.Ninguna)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enLaFranja) Modifier.background(Movi.colores.capa2) else Modifier)
            .testTag(tag),
    ) {
        EsqueletoDeFila(conFinal = false)
    }
}

@Composable
private fun CuerpoDelHeroe(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    disponible: com.jvillada.movi.ui.dashboard.DisponibleDelPeriodo?,
    visto: EstadoQueSeVe<EstadoDelHeroe>,
) {
    // Vuelta de diseño 28: con «Te quedan», tocar la cifra abre «¿De dónde sale?» (la cascada del MISMO
    // disponible); sin él —«Tu plata»— lleva a «Tus períodos» como siempre.
    val teQuedan = visto.mostrado.teQuedan
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }
    if (verDeDondeSale && disponible != null) {
        HojaDeDondeSale(
            disponible = disponible,
            onDismiss = { verDeDondeSale = false },
            onVerTusPeriodos = {
                verDeDondeSale = false
                onNavigate(Screen.Periodos)
            },
        )
    }
    val hayCargaEnVuelo = LocalCargandoElInicio.current
    // «Te quedan» cuando ya se sabe, y también mientras sus lecturas vienen en camino: mostrar «Tu
    // plata» y cambiarla por otra cifra un segundo después sería un salto y dos respuestas a la misma
    // pregunta. Si las lecturas llegaron y no hay disponible que afirmar, vuelve «Tu plata».
    val modoTeQuedan = teQuedan != null || (hayCargaEnVuelo && !alcanzaParaElDisponible(data))
    val tuPlata = visto.mostrado.tuPlata
    // Vuelta 79: la cifra grande cuenta de la vieja a la nueva cuando el cambio lo hizo el dueño. Cuenta
    // con el signo (lo que te pasaste es negativo) y se pinta sin él, como siempre.
    val montoQueCuenta = rememberCifraQueCuenta(teQuedan?.let { if (it.enRojo) -it.monto else it.monto })?.let { kotlin.math.abs(it) }
    val tuPlataQueCuenta = rememberCifraQueCuenta(if (teQuedan == null) tuPlata else null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (disponible != null) {
                    Modifier.clickable(onClickLabel = "Ver de dónde sale") { verDeDondeSale = true }
                } else {
                    Modifier.clickable(onClickLabel = VER_TUS_PERIODOS) { onNavigate(Screen.Periodos) }
                },
            )
            .padding(Medida.rellenoDeSuperficie)
            .graphicsLayer { alpha = visto.opacidad }
            .testTag(TAG_TARJETA_DEL_HERO),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    teQuedan != null -> Text(teQuedan.rotulo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    modoTeQuedan -> LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.cuerpo)
                    // El rótulo viaja en el binario, no en la fila: ver [HERO_BALANCE_TITLE].
                    else -> Text(heroBalanceTitle(section), style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                }
            }
            // «Tu plata» se nombra con un enlace a su casa; si la cifra grande ya es «Tu plata», no.
            if (modoTeQuedan && tuPlata != null) {
                Text(
                    text = conCifrasOcultas("Tu plata ${formatCOP(tuPlata)}"),
                    style = Movi.textos.apoyoMedio,
                    color = Movi.colores.enlace,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = "Ver tu plata") { onNavigate(Screen.Accounts) }
                        .testTag(TAG_ENLACE_TU_PLATA),
                )
            }
        }
        if (modoTeQuedan) {
            if (teQuedan != null) {
                // El MISMO valor que la frase del disponible de Plan, entero: «$4.963.400», no «$5M». Sin
                // signo: el rótulo ya dice de qué lado está. Vuelta 79: si la cambió el dueño, cuenta.
                Cifra(
                    montoQueCuenta ?: teQuedan.monto,
                    VarianteDeCifra.Heroe,
                    tono = if (teQuedan.enRojo) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
                    modifier = Modifier.testTag(TAG_CIFRA_TE_QUEDAN),
                )
                // Vuelta 78: cerca del cierre, o si «¿Me alcanza?» dice que no, la línea de los días se calla.
                detalleDelHeroe(teQuedan, visto.mostrado.renglon)?.let { detalle ->
                    Text(
                        text = detalle,
                        style = Movi.textos.apoyo,
                        color = colorDelDetalle(teQuedan.nivelDelDetalle),
                        modifier = Modifier.testTag(TAG_DETALLE_DEL_HEROE),
                    )
                }
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
            if (tuPlata == null) {
                // Un guion mientras las cuentas no contestan (ni vienen en camino): nunca un «$0».
                Text(text = "—", style = Movi.textos.cifra, color = Movi.colores.texto)
            } else {
                Cifra(
                    tuPlataQueCuenta ?: tuPlata,
                    VarianteDeCifra.Heroe,
                    tono = if (tuPlata < 0) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
                )
            }
            encabezadoDelPeriodo(data, conDias = true)?.let { rango ->
                Text(text = rango, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
}

/**
 * «2 por revisar»: la pastilla de la franja «Por confirmar». No dice «avisos»: con un aviso y un
 * movimiento, «2 avisos» contradecía el desglose de debajo (la revisión de la vuelta 39).
 */
internal fun textoDeLaPastillaDeAvisos(n: Int): String = textoDePorRevisar(n)

/**
 * **Lo que dice la franja «Por confirmar»** (vuelta 44): con la suma de los avisos
 * (`DashboardSummary.porConfirmarSuman`, entera o nada), «Por confirmar» y debajo **la cifra pegada a
 * lo que suma** —«$103.600 en 3 avisos», o «$103.600 en 4 avisos · y 1 movimiento» si espera algo más,
 * que no está en la cifra—. Sin la suma, como en la vuelta 39: «Por confirmar» y, con varias fuentes,
 * el desglose. Pura.
 *
 * Vuelta 68: la cifra iba en el título («Por confirmar $1.100.200») y el desglose debajo («En 5 avisos
 * · y 1 movimiento»): se leía como la suma de los seis, y era solo la de los avisos (la revisión `r3`).
 */
internal fun textosDeLaFranjaPorConfirmar(conteo: ConteoPorRevisar, suman: Long?): Pair<String, String?> {
    val desglose = desgloseDePorRevisar(conteo)
    // Vuelta 51: los avisos de más de 30 días no entran en la cifra ni en el número; se mencionan aparte.
    val antiguos = conteo.avisosAntiguos.takeIf { it > 0 }?.let { if (it == 1) "y 1 antiguo" else "y $it antiguos" }
    if (suman == null || suman <= 0L || conteo.avisos <= 0) {
        val base = desglose?.takeIf { it.contains(" · ") }
        return "Por confirmar" to (if (antiguos == null) base else listOfNotNull(base ?: desglose, antiguos).joinToString(" · "))
    }
    val avisos = if (conteo.avisos == 1) "${formatCOP(suman)} en 1 aviso" else "${formatCOP(suman)} en ${conteo.avisos} avisos"
    val resto = desgloseDePorRevisar(conteo.copy(avisos = 0))
    val linea = if (resto == null) avisos else "$avisos · y $resto"
    return "Por confirmar" to (if (antiguos == null) linea else "$linea · $antiguos")
}

/** Vuelta 73: los renglones del desglose de la franja, a todo el ancho de la fila (ver [pastillaDeLaFranja]). */
internal const val LINEAS_DE_LA_FRANJA_POR_CONFIRMAR: Int = 3

/**
 * **¿La franja lleva la pastilla «N por revisar»?** (vuelta 73): solo sin renglón de abajo. Cuando el
 * renglón ya dice lo que espera —«$1.100.200 en 5 avisos · y 1 movimiento», «1 aviso · 1 movimiento»—,
 * la pastilla era la misma cuenta dicha otra vez, y a 360 dp le quitaba al renglón el ancho que
 * necesitaba: se cortaba en «y 1 movimien…» (la revisión `r4`). Sin renglón —«Por confirmar» con una
 * sola fuente y sin suma— la pastilla es lo único que dice cuántos son, y se queda. Pura.
 */
internal fun pastillaDeLaFranja(subtitulo: String?): Boolean = subtitulo.isNullOrBlank()

/**
 * **La franja «Por confirmar»** de la tarjeta héroe (vuelta 39): debajo del hilo, en la capa 2, el reloj
 * en el tinte, «Por confirmar», la pastilla «N por revisar» —el mismo número de la insignia de «Revisar
 * avisos» ([conteoPorRevisarDelInicio])— y la flecha; abre Por revisar. Sin nada que confirmar, la
 * franja no está (sin hueco).
 *
 * Vuelta 44: dice cuánto suman los avisos («Por confirmar $103.600 · En 3 avisos», ver
 * [textosDeLaFranjaPorConfirmar]), para saber cuánto va a bajar «Te quedan» antes de confirmar.
 */
@Composable
private fun FranjaPorConfirmar(data: DashboardData, onNavigate: (Screen) -> Unit) {
    val conteo = conteoPorRevisarDelInicio(data)
    if (conteo.total <= 0) return
    val (titulo, subtitulo) = textosDeLaFranjaPorConfirmar(conteo, data.porConfirmarSuman)
    Separador(SangriaDeSeparador.Ninguna)
    Box(modifier = Modifier.fillMaxWidth().background(Movi.colores.capa2)) {
        Fila(
            titulo = conCifrasOcultas(titulo),
            subtitulo = subtitulo?.let { conCifrasOcultas(it) },
            // Vuelta 55: con la letra al 130 %, «3 avisos · 1 movimiento» no entra al lado de la
            // pastilla; baja a un segundo renglón en vez de cortarse. Vuelta 73: con la pastilla al
            // lado, a 360 dp ni dos renglones alcanzaban («… y 1 movimien…»); ahora el renglón tiene
            // el ancho de la fila, y el tercero queda para «y 2 antiguos» con la letra grande.
            lineasDelSubtitulo = LINEAS_DE_LA_FRANJA_POR_CONFIRMAR,
            modifier = Modifier.testTag(TAG_FRANJA_POR_CONFIRMAR),
            inicio = { IconoEnCirculo(Icons.Rounded.Schedule, tono = TonoDeIcono.Aviso) },
            final = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    // Vuelta 73: la pastilla solo cuando el renglón de abajo no dice qué espera. Con
                    // «$1.100.200 en 5 avisos · y 1 movimiento» debajo, «6 por revisar» repetía la
                    // cuenta y le comía al renglón el ancho que necesitaba a 360 dp.
                    if (pastillaDeLaFranja(subtitulo)) Pildora(textoDeLaPastillaDeAvisos(conteo.total), tono = TonoDePildora.Aviso)
                    Chevron()
                }
            },
            onClick = { onNavigate(Screen.PorRevisar) },
            onClickLabel = "Revisar lo que está por confirmar",
        )
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
    // Vuelta 75: el nombre único del período; con el mes de calendario el héroe no lo repite.
    if (data.ajustesDePeriodo.esMesDeCalendario) return null
    val rango = nombreDelPeriodo(periodo, data.ajustesDePeriodo)
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
            tint = Movi.colores.enlace,
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
