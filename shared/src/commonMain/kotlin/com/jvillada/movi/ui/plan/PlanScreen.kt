package com.jvillada.movi.ui.plan

import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.rangoDelPeriodo
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.budgets.HojasDePresupuestos
import com.jvillada.movi.ui.budgets.presupuestos
import com.jvillada.movi.ui.budgets.rememberEstadoDePresupuestos
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.components.SubirAlRetocarLaPestana
import com.jvillada.movi.ui.dashboard.alcanzaParaElDisponible
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import com.jvillada.movi.ui.sdui.encabezadoDelPeriodo
import kotlinx.datetime.Clock

/**
 * El título de la pestaña Pagos (antes «Plan»; vuelta 38, la opción 3). Por dentro sigue igual: Pagos
 * del mes, Presupuestos, el Disponible y Tus períodos.
 */
const val TITULO_DE_PAGOS: String = "Pagos"

/**
 * # Plan: ¿cuánto puedo gastar y qué me falta pagar?
 *
 * Ola C de «Movi sin grasa». Hasta acá esas dos preguntas vivían en tres lugares: el disponible en el
 * Inicio, el tablero de pagos como un chip de Movimientos y los presupuestos detrás de «Más». Plan
 * los junta en una pestaña, en ese orden: arriba el período y **«Cuánto puedes gastar»** (la tarjeta
 * del disponible del Inicio), y debajo dos segmentos — **«Pagos del mes»** (el tablero de
 * Recurrentes: checklist, próximos, sin confirmar, flujo libre, suscripciones) y **«Presupuestos»**.
 *
 * Nada de lo que muestra es nuevo ni está copiado: la tarjeta es [TarjetaDelDisponible] con los datos
 * de [rememberDisponibleDelPlan]; los pagos son [tableroDeRecurrentes]; los presupuestos son
 * [presupuestos]. Todo va en **una sola lista**: el período, la tarjeta, el selector y el segmento
 * se desplazan juntos, como una pantalla y no como tres pegadas.
 *
 * En pantalla ancha (Ola W3) las dos preguntas van lado a lado: el período, la tarjeta y «Tus
 * períodos» en una columna fija a la izquierda; el selector y su segmento a la derecha. Ver
 * [planEnDosColumnas].
 */

/** «Pagos»: el tablero de Recurrentes. Es el segmento con el que se entra por la pestaña. */
const val SEGMENTO_PAGOS: Int = 0

/** «Presupuestos». */
const val SEGMENTO_PRESUPUESTOS: Int = 1

/** Los rótulos del selector, en el orden de los índices de arriba. */
private val ROTULOS_DE_LOS_SEGMENTOS = listOf(ROTULO_SEGMENTO_PAGOS, "Presupuestos")

/**
 * El rótulo del segmento de los pagos. Era «Pagos del mes», pero el período del dueño no es el mes de
 * calendario (su corte es el 25): «Pagos» a secas, y la sección de adentro dice «Pagos del período».
 *
 * **Vuelta 40: «Facturas».** Desde la vuelta 38 la pestaña se llama «Pagos», y el título decía
 * «Pagos» y, justo debajo, el primer segmento también: «Pagos | Presupuestos» bajo «Pagos». El
 * segmento pasó a «Facturas», que es lo que lista adentro («Tus facturas del período»), y queda
 * «Facturas | Presupuestos».
 */
const val ROTULO_SEGMENTO_PAGOS: String = "Facturas"

/**
 * El aire entre el selector y lo primero de su segmento: el mismo en «Pagos» y en «Presupuestos»
 * (el vacío de Presupuestos lo usa también), para que cambiar de segmento no mueva el comienzo.
 */
val AIRE_DEBAJO_DEL_SELECTOR = Espacio.m

/** La línea del rango del período bajo el título, y su esqueleto mientras el perfil no contestó. */
const val TAG_LINEA_DEL_PERIODO_DE_PLAN: String = "linea-del-periodo-de-plan"
const val TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO: String = "linea-del-periodo-de-plan-esqueleto"

/**
 * Un índice que no es de ningún segmento (una pila restaurada de una versión que tenía otros) cae en
 * [SEGMENTO_PAGOS] en vez de dejar la pantalla sin nada abajo del selector.
 */
internal fun segmentoValido(segmento: Int): Int =
    if (segmento in ROTULOS_DE_LOS_SEGMENTOS.indices) segmento else SEGMENTO_PAGOS

/**
 * @param segmento con cuál arranca (ver [Screen.Plan]). El que el dueño elige después se recuerda
 *   mientras navega: `rememberSaveable`, dentro del `SaveableStateProvider` que App.kt pone por
 *   pantalla, así que ir a ver un movimiento y volver deja el segmento donde estaba.
 */
@Composable
fun PlanScreen(onNavigate: (Screen) -> Unit, segmento: Int = SEGMENTO_PAGOS) {
    var elegido by rememberSaveable { mutableStateOf(segmentoValido(segmento)) }
    val enPagos = elegido == SEGMENTO_PAGOS

    // `vencimientosSiempre`: la tarjeta del disponible saca sus fijos de los vencimientos y las
    // ocurrencias del tablero, también con Presupuestos a la vista (ver [DisponibleDelPlan]).
    val pagos = rememberTableroMontadoSolo(activo = enPagos, vencimientosSiempre = true)
    val presupuestos = rememberEstadoDePresupuestos(activo = !enPagos)
    // Marcar que algo ocurrió, confirmar un cobro o editar un recurrente cambia los fijos del
    // período, que son la mitad del disponible: la tarjeta de arriba vuelve a leer con cada acción
    // del tablero. Su «Reintentar» pasa por el mismo camino (`recargar()`), que además vuelve a
    // pedir los vencimientos — la otra mitad de lo que pudo fallar.
    val disponible = rememberDisponibleDelPlan(recarga = pagos.estado.recargas, tablero = pagos.estado)
    val data = disponible.data
    // Recargando con cifras ya pintadas (lo del Inicio): lo dice, como el Inicio, en vez de hacer
    // pasar lo de antes por lo de ahora.
    val actualizando = disponible.cargando && alcanzaParaElDisponible(data)
    val ajustesDelPeriodo = data.ajustesDePeriodo
    val periodoDeHoy = remember(ajustesDelPeriodo) {
        periodoActual(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)
    }
    // El tablero enumera el período del dueño; mientras su corte todavía viene en camino, cualquier
    // checklist sería el del mes de calendario — y cambiaría de filas al llegar el perfil. Esqueleto.
    val esqueletoDePagos = pagos.estado.primeraLecturaEnCurso || (data.periodoActual == null && disponible.cargando)

    val listState = rememberLazyListState()
    // La columna del disponible, en dos columnas: su propio scroll.
    val scrollDelDisponible = rememberScrollState()
    // Pantalla ancha: la rueda del mouse sobre los márgenes también mueve esta lista (en dos columnas,
    // la de los pagos: es la larga).
    ScrollDesdeLosMargenes(listState)
    // Re-tocar «Plan» estando acá vuelve arriba, las dos columnas (ver [SubirAlRetocarLaPestana]).
    SubirAlRetocarLaPestana(listState, scrollDelDisponible)

    // Las piezas de Plan, una sola vez: en una columna van todas en la misma lista; en dos, las tres
    // primeras a la izquierda y el selector con su segmento a la derecha.
    val lineaDelPeriodo: @Composable () -> Unit = {
        LineaDelPeriodo(
            // Vuelta 40: «Del 25 de octubre al 24 de noviembre», sin «quedan N días» (la opción 3), y
            // **siempre**: también con el mes de calendario (corte 1, el del usuario demo), que antes
            // se callaba por diseño —«no hay nada que aclarar»— y dejaba a Pagos sin fechas.
            texto = lineaDelPeriodoDePagos(data),
            reservar = data.periodoActual == null && disponible.cargando,
        )
    }
    val cuantoPuedesGastar: @Composable () -> Unit = {
        SeccionCuantoPuedesGastar(
            disponible = disponible,
            onReintentar = { pagos.estado.recargar() },
        )
    }
    // Ola 4: lo que viene. **El orden de Plan (ola R4)**: «Cuánto puedes gastar» → «Pagos del
    // período» → «Tu plata, día a día» → lo que se repite y las suscripciones; Presupuestos es el
    // segundo segmento.
    //
    // **Por qué la caja va debajo del selector y no pegada a la tarjeta.** Plan tiene una regla
    // vieja y probada (`PlanScreenTest`): al llegar los datos ni la tarjeta, ni «Tus períodos», ni
    // el selector se corren más de 8 dp. La caja tiene un alto que depende de lo que diga (un día en
    // rojo, una tarjeta sin mínimo), así que encima del selector lo empujaba ~100 dp. En pantalla
    // ancha va en la columna del disponible, debajo de todo; en una columna, dentro de «Pagos»,
    // justo después de la lista del período: los pagos primero, y debajo cómo mueven tu plata.
    val cajaProyectada: @Composable (Boolean, Boolean) -> Unit = { arriba, abajo ->
        SeccionCajaProyectada(
            data = data,
            cargando = disponible.cargando,
            recarga = pagos.estado.recargas,
            onNavigate = onNavigate,
            aireArriba = arriba,
            aireAbajo = abajo,
        )
    }
    val tusPeriodos: @Composable () -> Unit = {
        FilaDeTusPeriodos(onClick = { onNavigate(Screen.Periodos) })
    }
    val selector: @Composable () -> Unit = {
        SelectorSegmentado(
            labels = ROTULOS_DE_LOS_SEGMENTOS,
            selected = elegido,
            onSelect = { elegido = it },
        )
    }
    // Lo de debajo del selector: el tablero de Recurrentes o los presupuestos. Perezoso siempre.
    //
    // **Vuelta 40 (la opción 3): primero las facturas.** En una columna, lo primero debajo del
    // selector es lo del segmento —en «Facturas», la tarjeta amarilla «Falta por pagar» y «Tus
    // facturas del período»; en «Presupuestos», los presupuestos— y después, igual en los dos, el
    // Disponible con «¿De dónde sale?» y «Tus períodos» (en «Facturas», también la caja). En dos
    // columnas eso ya está a la izquierda.
    fun LazyListScope.loDeAbajo(conCaja: Boolean) {
        item(key = "disponible") { cuantoPuedesGastar() }
        item(key = "tus-periodos") { tusPeriodos() }
        if (conCaja) item(key = "caja-proyectada") { cajaProyectada(true, true) }
    }
    fun LazyListScope.segmentoElegido(dosColumnas: Boolean) {
        if (enPagos) {
            item(key = "aire-de-pagos") { Spacer(Modifier.height(AIRE_DEBAJO_DEL_SELECTOR)) }
            val caja: (LazyListScope.() -> Unit)? = if (dosColumnas) null else {
                { loDeAbajo(conCaja = true) }
            }
            if (esqueletoDePagos) {
                tableroDeRecurrentesEsqueleto()
                caja?.invoke(this)
            } else {
                tableroDeRecurrentes(
                    estado = pagos.estado,
                    periodoVisible = periodoDeHoy,
                    periodoDeHoy = periodoDeHoy,
                    ajustesDelPeriodo = ajustesDelPeriodo,
                    accountNames = pagos.accountNames,
                    onNavigate = onNavigate,
                    despuesDeLaLista = caja,
                )
            }
        } else {
            presupuestos(presupuestos)
            if (!dosColumnas) {
                item(key = "aire-del-disponible") { Spacer(Modifier.height(Medida.entreSecciones)) }
                loDeAbajo(conCaja = false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // Ola W3: en pantalla ancha, el disponible a la izquierda y los pagos a la derecha. El ancho
        // lo mide el panel, ya sin el rail — ver [planEnDosColumnas] para la cuenta completa.
        PanelDeTablero(enDosColumnas = ::planEnDosColumnas) { dosColumnas ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Las acciones del encabezado, desde el primer cuadro: «Nuevo» es la de Presupuestos y
            // solo tiene sentido con ese segmento a la vista (ver
            // `EstadoDePresupuestos.nuevoEnElEncabezado`). El avatar tiene el alto del encabezado,
            // así que ponerlo o sacarlo no mueve nada.
            MinScreenHeader(
                title = TITULO_DE_PAGOS,
                // Vuelta 38: el avatar va solo en Inicio, como en la maqueta.
                leading = HeaderLeading.Ninguno,
                action = {
                    // Va en la cabecera, cuyo alto fija el avatar: aparecer y desaparecer no mueve
                    // nada de lo de abajo. Mismo texto y estilo que el Inicio. También cuando lo que
                    // se recarga son los presupuestos recordados, con ese segmento a la vista.
                    if (actualizando || (!enPagos && presupuestos.actualizandoConAlgoALaVista)) {
                        Text(
                            "Actualizando…",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                            maxLines = 1,
                        )
                    }
                },
                accion = if (!enPagos && presupuestos.nuevoEnElEncabezado) {
                    AccionDeEncabezado("Nuevo", Icons.Rounded.Add, onClick = { presupuestos.abrirNuevo() })
                } else null,
            )
            if (dosColumnas) {
                // Las mismas piezas que en una columna, en los mismos lugares relativos: cargando o
                // cargadas, cada una ya está en su columna, así que al llegar los datos nada cambia
                // de lado. El esqueleto del disponible y el del tablero se reparten igual.
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(ANCHO_DE_LA_COLUMNA_DEL_DISPONIBLE)
                            .fillMaxHeight()
                            .testTag(TAG_COLUMNA_DEL_DISPONIBLE)
                            .verticalScroll(scrollDelDisponible)
                            .padding(bottom = Medida.botonFlotante + Espacio.xl),
                    ) {
                        lineaDelPeriodo()
                        cuantoPuedesGastar()
                        tusPeriodos()
                        cajaProyectada(true, false)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LOS_PAGOS),
                        contentPadding = PaddingValues(bottom = Medida.botonFlotante + Espacio.xl),
                    ) {
                        item(key = "segmentos") {
                            // A la altura de la línea del período de la izquierda (su aire `corto`).
                            Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Espacio.xs)) {
                                selector()
                            }
                        }
                        segmentoElegido(dosColumnas = true)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Medida.botonFlotante + Espacio.xl),
                ) {
                    item(key = "periodo") { lineaDelPeriodo() }
                    // Vuelta 40: el selector justo debajo del período, antes de lo que cambia.
                    item(key = "segmentos") {
                        Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
                            selector()
                        }
                    }
                    segmentoElegido(dosColumnas = false)
                }
            }
        }
        } // PanelDeTablero
        SnackbarHost(
            hostState = pagos.aviso,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Espacio.s),
        )
        HojasDelTableroDeRecurrentes(pagos.estado, onNavigate)
        HojasDePresupuestos(presupuestos)
    }
}

/**
 * «Del 25 de agosto al 24 de septiembre · quedan 9 días» — la misma línea que el hero del Inicio
 * ([encabezadoDelPeriodo]). Vuelta 40: va siempre, también con el mes de calendario (ver
 * [lineaDelPeriodoDePagos]); lo que sigue es la historia de antes: con el mes de calendario no se pintaba, igual
 * que allá. [reservar]: el perfil (de donde sale el período) todavía viene en camino y la última vez
 * la línea iba — un esqueleto de su alto, para que al llegar no empuje la tarjeta de abajo.
 */
@Composable
private fun LineaDelPeriodo(texto: String?, reservar: Boolean) {
    // El aire de arriba es de la línea y no del encabezado: el encabezado lo comparten todas las
    // pantallas y su respiro de abajo (`medio`) es para el título solo; esta línea es otra cosa y
    // sin su propio aire quedaba pegada a él.
    // Sin línea (mes de calendario) no hay nada a lo que darle aire: quedan solo los `amplio` de
    // abajo, como siempre, y no 8 + 16 vacíos sobre la tarjeta.
    val hayLinea = texto != null || reservar
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla).then(
            if (hayLinea) Modifier.padding(top = Espacio.xs) else Modifier,
        ),
    ) {
        when {
            // Vuelta 40: en texto medio (era apagado), como en la maqueta.
            texto != null -> Text(
                text = texto,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN),
            )
            reservar -> LineaEsqueleto(
                fraccionDelAncho = 0.7f,
                estilo = Movi.textos.apoyo,
                modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO),
            )
        }
        Spacer(Modifier.height(Espacio.m))
    }
}

/**
 * **La línea del período de Pagos** (vuelta 40): «Del 25 de octubre al 24 de noviembre», o «Del 1 de
 * octubre al 31 de octubre» con el mes de calendario. `null` solo mientras no se sabe el período.
 */
internal fun lineaDelPeriodoDePagos(data: com.jvillada.movi.ui.dashboard.DashboardData): String? =
    data.periodoActual?.let { rangoDelPeriodo(it, data.ajustesDePeriodo) }

/** El título de la tarjeta del disponible en Plan: la pregunta que contesta. */
const val TITULO_CUANTO_PUEDES_GASTAR = "Cuánto puedes gastar"

/**
 * **«Cuánto puedes gastar»**, en sus cuatro estados, sin afirmar nada que no se leyó:
 *
 * - con los datos: la tarjeta del disponible, la misma del Inicio (con «Actualizando…» en la
 *   cabecera si lo que se ve es de antes y la carga sigue en vuelo);
 * - cargando y sin datos: la misma tarjeta con su forma y sin cifras;
 * - la carga terminó y alguna lectura de ESTA carga no contestó, o falta alguna: «no pudimos
 *   calcular», con «Reintentar» — aunque haya cifras de antes, que ya no se pueden dar por
 *   actuales (ver [DisponibleDelPlan.fallo]);
 * - están todas y no hay nada honesto que decir (sin ingresos ni plata anotados, ver
 *   `disponibleDelPeriodo`): lo dice y dice qué falta, en vez de un disponible negativo que asuste.
 */
@Composable
private fun SeccionCuantoPuedesGastar(disponible: DisponibleDelPlan, onReintentar: () -> Unit) {
    val data = disponible.data
    val alcanza = alcanzaParaElDisponible(data)
    when {
        !disponible.cargando && disponible.fallo -> NoSePudoLeerElDisponible(onReintentar)
        alcanza -> {
            val cifras = disponibleDelInicio(data)
            if (cifras != null) {
                TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = cifras)
            } else {
                Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
                    Seccion(titulo = TITULO_CUANTO_PUEDES_GASTAR)
                    Superficie(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Anota tus ingresos y tus cuentas para saber cuánto puedes gastar en el período",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                        )
                    }
                }
            }
        }
        disponible.cargando -> TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = null)
        else -> NoSePudoLeerElDisponible(onReintentar)
    }
}

@Composable
private fun NoSePudoLeerElDisponible(onReintentar: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        Seccion(titulo = TITULO_CUANTO_PUEDES_GASTAR)
        NoSePudoLeer("No pudimos calcular cuánto puedes gastar", onReintentar = onReintentar)
    }
}

/** El tag de la fila «Tus períodos», para encontrarla sin depender de su texto en una prueba. */
const val TAG_FILA_DE_TUS_PERIODOS: String = "fila-de-tus-periodos"

/**
 * **«Tus períodos»**: la puerta a la lista de `Screen.Periodos` desde Plan, la
 * pestaña que ya contesta «¿cómo me fue?». No pide nada (no tiene número que mostrar, es un enlace
 * fijo), así que no tiene esqueleto ni error propios — siempre está, desde el primer cuadro.
 */
@Composable
private fun FilaDeTusPeriodos(onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        // El mismo aire que hay entre cualquier par de secciones de la pantalla (y del Inicio): la
        // fila hablaba de otra cosa que la tarjeta de arriba y estaba pegada a ella, sin nada.
        Spacer(Modifier.height(Medida.entreSecciones))
        Superficie(
            modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_TUS_PERIODOS),
            relleno = RellenoDeSuperficie.Lista,
        ) {
            Fila(
                titulo = "Tus períodos",
                subtitulo = "Cómo te fue en cada ciclo",
                inicio = { IconoEnCirculo(Icons.Rounded.History) },
                final = { Chevron() },
                onClick = onClick,
            )
        }
    }
}
