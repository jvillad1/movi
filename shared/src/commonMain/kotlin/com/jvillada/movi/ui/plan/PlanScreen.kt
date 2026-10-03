package com.jvillada.movi.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.budgets.HojasDePresupuestos
import com.jvillada.movi.ui.budgets.presupuestos
import com.jvillada.movi.ui.budgets.rememberEstadoDePresupuestos
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NewItemButton
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.dashboard.alcanzaParaElDisponible
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import com.jvillada.movi.ui.sdui.encabezadoDelPeriodo
import kotlinx.datetime.Clock

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

/** «Pagos del mes»: el tablero de Recurrentes. Es el segmento con el que se entra por la pestaña. */
const val SEGMENTO_PAGOS: Int = 0

/** «Presupuestos». */
const val SEGMENTO_PRESUPUESTOS: Int = 1

/** Los rótulos del selector, en el orden de los índices de arriba. */
private val ROTULOS_DE_LOS_SEGMENTOS = listOf("Pagos del mes", "Presupuestos")

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
    // Si la última vez que el perfil contestó el período llevaba línea de rango. `null` (nada
    // recordado) la reserva, como Movimientos: ver `FormaRecordada.recordarLineaDePeriodo`.
    val lineaRecordada = remember { FormaRecordada.delAparato.movimientos(SessionManager.userId)?.lineaDePeriodo }

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

    // Las piezas de Plan, una sola vez: en una columna van todas en la misma lista; en dos, las tres
    // primeras a la izquierda y el selector con su segmento a la derecha.
    val lineaDelPeriodo: @Composable () -> Unit = {
        LineaDelPeriodo(
            texto = encabezadoDelPeriodo(data),
            reservar = data.periodoActual == null && disponible.cargando && lineaRecordada != false,
        )
    }
    val cuantoPuedesGastar: @Composable () -> Unit = {
        SeccionCuantoPuedesGastar(
            disponible = disponible,
            onReintentar = { pagos.estado.recargar() },
        )
    }
    // Ola 4: lo que viene. Debajo de «Cuánto puedes gastar», con la misma lista del período.
    val cajaProyectada: @Composable () -> Unit = {
        SeccionCajaProyectada(
            data = data,
            cargando = disponible.cargando,
            recarga = pagos.estado.recargas,
            onNavigate = onNavigate,
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
    fun LazyListScope.segmentoElegido() {
        if (enPagos) {
            item(key = "aire-de-pagos") { Spacer(Modifier.height(Movi.espacios.amplio)) }
            if (esqueletoDePagos) {
                tableroDeRecurrentesEsqueleto()
            } else {
                tableroDeRecurrentes(
                    estado = pagos.estado,
                    periodoVisible = periodoDeHoy,
                    periodoDeHoy = periodoDeHoy,
                    ajustesDelPeriodo = ajustesDelPeriodo,
                    accountNames = pagos.accountNames,
                    onNavigate = onNavigate,
                )
            }
        } else {
            presupuestos(presupuestos)
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
                title = "Plan",
                leading = HeaderLeading.Avatar(onNavigate),
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
                    if (!enPagos && presupuestos.nuevoEnElEncabezado) {
                        NewItemButton(label = "Nuevo", onClick = { presupuestos.abrirNuevo() })
                    }
                },
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
                            .padding(bottom = 80.dp),
                    ) {
                        lineaDelPeriodo()
                        cuantoPuedesGastar()
                        cajaProyectada()
                        tusPeriodos()
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LOS_PAGOS),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item(key = "segmentos") {
                            // A la altura de la línea del período de la izquierda (su aire `corto`).
                            Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio).padding(top = Movi.espacios.corto)) {
                                selector()
                            }
                        }
                        segmentoElegido()
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    item(key = "periodo") { lineaDelPeriodo() }
                    item(key = "disponible") { cuantoPuedesGastar() }
                    item(key = "caja-proyectada") { cajaProyectada() }
                    item(key = "tus-periodos") { tusPeriodos() }
                    item(key = "segmentos") {
                        Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
                            Spacer(Modifier.height(Movi.espacios.seccion))
                            selector()
                        }
                    }
                    segmentoElegido()
                }
            }
        }
        } // PanelDeTablero
        SnackbarHost(
            hostState = pagos.aviso,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
        HojasDelTableroDeRecurrentes(pagos.estado)
        HojasDePresupuestos(presupuestos)
    }
}

/**
 * «Del 25 de agosto al 24 de septiembre · quedan 9 días» — la misma línea que el hero del Inicio
 * ([encabezadoDelPeriodo]). Con el mes de calendario no hay nada que aclarar y no se pinta, igual
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
        modifier = Modifier.fillMaxWidth().padding(
            start = Movi.espacios.margen,
            end = Movi.espacios.margen,
            top = if (hayLinea) Movi.espacios.corto else 0.dp,
        ),
    ) {
        when {
            texto != null -> Text(
                text = texto,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN),
            )
            reservar -> LineaEsqueleto(
                fraccionDelAncho = 0.7f,
                estilo = Movi.textos.apoyo,
                modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO),
            )
        }
        Spacer(Modifier.height(Movi.espacios.amplio))
    }
}

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
                Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
                    MinSectionHeader(title = TITULO_CUANTO_PUEDES_GASTAR)
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(Movi.espacios.amplio),
                    ) {
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
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = TITULO_CUANTO_PUEDES_GASTAR)
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
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        // El mismo aire que hay entre cualquier par de secciones de la pantalla (y del Inicio): la
        // fila hablaba de otra cosa que la tarjeta de arriba y estaba pegada a ella, sin nada.
        Spacer(Modifier.height(Movi.espacios.seccion))
        MinCard(
            modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_TUS_PERIODOS),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Tus períodos", style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                    Spacer(Modifier.height(Movi.espacios.minimo))
                    Text("Cómo te fue en cada ciclo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                ChevronRight()
            }
        }
    }
}
