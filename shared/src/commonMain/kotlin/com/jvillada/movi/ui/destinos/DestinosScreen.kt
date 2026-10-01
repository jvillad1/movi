package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.identificadoresDelDestino
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NewItemButton
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.TAG_TITULO_DE_FILA_ESQUELETO
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * # «Más → Cuentas de otros»
 *
 * El dueño lo pidió así: *«Es la cuenta de Caro, yo le transferí a ella lo de Cotrafa. Guarda esa
 * cuenta como una cuenta no mía pero sí de mi esposa, me interesa tenerla guardada y poder ver los
 * movimientos hacia esa cuenta.»*
 *
 * Esta pantalla es las dos mitades de ese pedido:
 *
 * - **Guardarla.** Registrar, renombrar y borrar un destino desde acá, sin que nadie toque código.
 *   No es un detalle: la regla del proyecto es que nada se configure solo escribiendo Kotlin, y
 *   *«otros usuarios no tienen a Claude al lado»*.
 * - **Ver lo que fue para allá.** Cada ficha dice cuánto y cuántos movimientos; tocarla abre el
 *   detalle con la lista y el total por período.
 *
 * ## Por qué la lista de movimientos vive ACÁ y no en Movimientos
 *
 * Se consideraron las tres puertas:
 *
 * - **Un chip en Movimientos** no escala: la fila de chips es un juego fijo y global
 *   (`CHIPS_DE_MOVIMIENTOS`), así que un destino nuevo necesitaría un chip nuevo — o sea, volver a
 *   tocar código cada vez que registre a alguien, justo lo que esta feature vino a evitar.
 * - **Una tarjeta en Más** sería una segunda superficie que dice lo mismo que esta, y Más es un
 *   índice de accesos, no un lugar donde se lean cifras.
 * - **El detalle del propio destino** —esto— no agrega ninguna pantalla que el registro no
 *   necesitara igual: la hoja de alta/edición tiene que existir de todos modos, y el total se lee
 *   al lado del nombre de quien lo recibió, que es la pregunta que se está haciendo.
 *
 * ## Solo en línea, y dicho en voz alta
 *
 * No hay espejo local (ver `WalletRepository.getDestinos`): el total sale de cruzar TODOS los
 * movimientos, y el teléfono tiene una foto que puede estar corrida. Sin señal, la pantalla dice
 * que no pudo leer y ofrece reintentar — no muestra una cifra vieja como si fuera la de hoy.
 */
@Composable
fun DestinosScreen(onNavigate: (Screen) -> Unit) {
    var destinos by remember { mutableStateOf<List<DestinoConocido>>(emptyList()) }
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    var ajustes by remember { mutableStateOf(PeriodSettings()) }
    var cargando by remember { mutableStateOf(true) }
    // Ver [NoSePudoLeer]: «Aún no hay ninguna» solo si la lectura contestó.
    var leidos by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }

    // Qué hoja está abierta. El detalle y el formulario son dos hojas y no una: mezclar campos
    // editables con una lista de plata que se lee hace que no se entienda qué se está mirando.
    var detalle by remember { mutableStateOf<DestinoConocido?>(null) }
    var formulario by remember { mutableStateOf<DestinoConocido?>(null) }
    var formularioAbierto by remember { mutableStateOf(false) }

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(loadKey, refreshTick) {
        cargando = true
        runCatching { Repositories.wallets.getDestinos() }
            .onSuccess { destinos = it; leidos = true }
        // Las cuentas son para la guarda del alta: la hoja avisa «ese número es de tu Fiducuenta»
        // sin ir al server. Si no se pudieron leer, el server rechaza igual — la guarda de verdad
        // está allá (ver `DestinoRoutes`).
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
        runCatching { Repositories.wallets.getUserProfile() }
            .onSuccess { ajustes = PeriodSettings(it.periodCutoffDay, it.periodStarts) }
        cargando = false
    }
    val noSeLeyo = !cargando && !leidos

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
            MinScreenHeader(
                title = "Cuentas de otros",
                // Ola C, tarea 4: la primera puerta fue la tarjeta de Patrimonio, hoy «Cuentas de
                // otros» (ver `SeccionDeCuentasDeOtros` en `AccountsScreen`). Ola V agregó una segunda, «Cuentas de
                // otros» en Ajustes (ver `MasScreen`) — «volver» sigue el historial real y solo
                // cae a este `fallback` (Patrimonio) cuando no hay ninguno.
                leading = HeaderLeading.Back(fallback = Screen.Accounts),
                subtitle = when {
                    cargando || noSeLeyo -> null
                    destinos.size == 1 -> "1 cuenta guardada"
                    destinos.isNotEmpty() -> "${destinos.size} cuentas guardadas"
                    else -> null
                },
                action = if (destinos.isNotEmpty() && !noSeLeyo) {
                    {
                        NewItemButton(
                            label = "Nueva cuenta",
                            onClick = { formulario = null; formularioAbierto = true },
                        )
                    }
                } else null,
            )

            if (noSeLeyo) {
                Spacer(Modifier.height(14.dp))
                NoSePudoLeer(
                    "No pudimos cargar las cuentas de otros",
                    onReintentar = { loadKey++ },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else if (destinos.isEmpty() && !cargando) {
                NewItemButton(
                    label = "Guardar una cuenta de otra persona",
                    onClick = { formulario = null; formularioAbierto = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
                )
            }

            if (!noSeLeyo) LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item {
                    MinSectionHeader(
                        title = "Guardadas",
                        count = if (destinos.isNotEmpty()) destinos.size else null,
                    )
                    if (destinos.isEmpty() && !cargando) {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(18.dp),
                        ) {
                            Text(
                                QUE_ES_ESTO,
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                            )
                        }
                    }
                }
                // Ola B, tarea 9: antes de la primera lectura buena la pantalla quedaba en
                // blanco debajo de «Guardadas» — ni una ficha, ni una rueda. `!leidos` y no solo
                // `cargando`: una recarga con destinos ya pintados (guardar uno, volver del
                // detalle) sigue con `items(destinos)` de siempre.
                if (cargando && !leidos) {
                    destinosEsqueleto()
                } else {
                    items(destinos) { d ->
                        Column {
                            FichaDelDestino(d, onClick = { detalle = d })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        detalle?.let { d ->
            DetalleDelDestinoSheet(
                destino = d,
                ajustes = ajustes,
                onDismiss = { detalle = null },
                onEditar = { detalle = null; formulario = d; formularioAbierto = true },
            )
        }

        if (formularioAbierto) {
            DestinoSheet(
                cuentas = cuentas,
                existente = formulario,
                onDismiss = { formularioAbierto = false },
                onGuardado = { formularioAbierto = false; loadKey++ },
            )
        }
    }
}

/**
 * Lo que la pantalla vacía explica. Va acá y no adentro del `Composable` para que se lea completo
 * de una vez: es el texto que tiene que dejar claro, sin ejemplos de código, qué es esto y qué NO
 * es — que no es una cuenta suya y que no entra en su plata.
 */
internal const val QUE_ES_ESTO: String =
    "Aquí guardas cuentas que no son tuyas: la de tu pareja, la de tu papá. No entran en tu plata " +
        "ni en tu patrimonio. Sirven para dos cosas: cuando el banco te avise de una transferencia " +
        "a esa cuenta o a su llave, Movi le pone el nombre en vez del número; y aquí ves junto todo " +
        "lo que le has enviado. También puedes guardarlas desde el aviso del banco, con «Guardar como…»."

/**
 * El renglón de debajo del nombre: cómo lo reconoce el banco (la cola del número, la llave, o los
 * dos — ver [identificadoresDelDestino]) y de quién es, si lo llenó.
 */
internal fun subtituloDelDestino(destino: DestinoConocido): String =
    (identificadoresDelDestino(destino) + listOfNotNull(destino.deQuien)).joinToString(" · ")

/** «Este período»: lo de [DestinoConocido.totalesDelPeriodo], o que no hubo nada. */
internal fun loDeEstePeriodo(destino: DestinoConocido): String =
    destino.totalesDelPeriodo.filterValues { it != 0L }.entries.sortedBy { it.key }
        .joinToString(" · ") { (moneda, total) -> formatMoney(total, moneda) }
        .ifEmpty { "nada todavía" }

/**
 * **Una cuenta de otro, de un vistazo** (30-sep): el nombre, cómo la reconoce el banco y de quién
 * es; a la derecha el total y cuántos envíos; y debajo lo de este período y el último envío —
 * «¿cuándo fue la última vez que le mandé a Caro?» se contesta sin abrir nada. Tocarla abre el
 * detalle con todos los movimientos ([DetalleDelDestinoSheet]).
 */
@Composable
private fun FichaDelDestino(destino: DestinoConocido, onClick: () -> Unit) {
    val hoy = remember { hoyEnAppZone() }
    MinCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                Text(destino.nombre, style = Movi.textos.titulo, color = Movi.colores.texto)
                Text(
                    subtituloDelDestino(destino),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                // Sin movimientos NO se escribe «$0»: un cero dicho con la misma letra que una
                // cifra real se lee como «no le mandaste nada», y lo que pasa casi siempre es que
                // el banco todavía no nombró ese número en ningún mensaje.
                if (destino.cuantos == 0) {
                    Text("Sin movimientos", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                } else {
                    TotalesEnColumna(destino.totales, alineadoAlFinal = true)
                    Text(
                        if (destino.cuantos == 1) "1 movimiento" else "${destino.cuantos} movimientos",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        // Lo de este período y el último envío, solo si hubo alguno: sin envíos ya lo dice
        // «Sin movimientos», y dos renglones vacíos más no agregan nada.
        val ultimo = destino.ultimo
        if (destino.cuantos > 0) {
            Spacer(Modifier.height(12.dp))
            RenglonDeLaFicha("Este período", loDeEstePeriodo(destino))
            if (ultimo != null) {
                Spacer(Modifier.height(4.dp))
                RenglonDeLaFicha(
                    "Último",
                    "${ultimo.descripcion} · ${etiquetaDeFecha(fechaDeEpoch(ultimo.timestamp), hoy)} · " +
                        formatMoney(ultimo.monto, ultimo.moneda),
                )
            }
        }
    }
}

@Composable
private fun RenglonDeLaFicha(rotulo: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        Text(
            valor,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Los totales, **una línea por moneda**. Sumar pesos con dólares daría una cifra que no existe
 * (mismo criterio que `computeBalances`), y casi siempre va a haber una sola línea.
 */
@Composable
internal fun TotalesEnColumna(totales: Map<String, Long>, alineadoAlFinal: Boolean) {
    Column(
        horizontalAlignment = if (alineadoAlFinal) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Orden fijo por moneda: sin esto el mapa podría pintar COP arriba una vez y USD arriba la
        // siguiente, y una cifra que cambia de lugar se lee como una cifra que cambió.
        totales.entries.sortedBy { it.key }.forEach { (moneda, total) ->
            Text(
                formatMoney(total, moneda),
                style = Movi.textos.monto,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
            )
        }
    }
}

/** Cuántas fichas pinta [destinosEsqueleto] mientras la lista no llegó ni una vez. */
private const val FICHAS_DE_DESTINO_ESQUELETO = 3

/**
 * **Cuentas de otros mientras carga, con la forma de [FichaDelDestino]** (Ola B, tarea 9). Antes
 * de esta tarea la pantalla quedaba en blanco debajo de «Guardadas» hasta que los destinos
 * contestaban. Mismo `MinCard` de 18 dp, mismo reparto 55/45 entre el nombre+número y los
 * totales+conteo del lado derecho.
 */
private fun LazyListScope.destinosEsqueleto() {
    items(FICHAS_DE_DESTINO_ESQUELETO) {
        Column {
            FichaDelDestinoEsqueleto()
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FichaDelDestinoEsqueleto() {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_LISTA_ESQUELETO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                LineaEsqueleto(
                    fraccionDelAncho = 0.7f,
                    estilo = Movi.textos.titulo,
                    modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                )
                Spacer(Modifier.height(4.dp))
                LineaEsqueleto(fraccionDelAncho = 0.5f, estilo = Movi.textos.apoyo)
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.monto), ancho = 96.dp)
                Spacer(Modifier.height(4.dp))
                LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
            }
        }
    }
}
