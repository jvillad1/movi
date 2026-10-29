package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.CampoDeBusqueda
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.transactions.CifraDelMovimiento
import com.jvillada.movi.ui.transactions.tonoDelEvento
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.ahoraEnMs
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.groupLabel
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.transactions.HojaDelMovimiento

/** El `testTag` del campo de búsqueda. */
const val TAG_CAMPO_DE_BUSCAR: String = "buscar-campo"

/** El `testTag` de un resultado: el prefijo y su [ResultadoDeBusqueda.clave]. */
const val TAG_RESULTADO_DE_BUSCAR: String = "buscar-resultado-"

/**
 * El `testTag` de la superficie de un grupo de resultados: el prefijo y el nombre de su
 * [TipoDeResultado] («MOVIMIENTO», «COMERCIO»…), o «recientes».
 */
const val TAG_GRUPO_DE_BUSCAR: String = "buscar-grupo-"

/** El `testTag` de una búsqueda reciente: el prefijo y el texto. */
const val TAG_RECIENTE_DE_BUSCAR: String = "buscar-reciente-"

/** El título de la pantalla. */
const val TITULO_DE_BUSCAR: String = "Buscar"

/**
 * **El texto guía de la caja**, corto para que quepa en un renglón a 360 dp con la letra agrandada
 * (vuelta de diseño 5: «Movimientos, comercios, personas, pantallas…» se cortaba a 390). Todo lo
 * que se puede buscar lo dice la [PISTA_DE_BUSCAR] de abajo.
 */
const val GUIA_DE_BUSCAR: String = "Gasto, comercio o persona"

/** El texto de ayuda debajo de los chips, con la caja vacía y sin recientes. */
const val PISTA_DE_BUSCAR: String =
    "Busca movimientos, comercios, personas, categorías, cuentas o pantallas. Un número busca el monto exacto."

/** El texto guía de la caja vacía, para las pruebas. */
const val TAG_GUIA_DE_BUSCAR: String = "buscar-guia"

/** El chip del período: elegido, solo el período en curso; sin elegir, todo el historial. */
const val ESTE_PERIODO: String = "Este período"

/**
 * # «Buscar» (Ola 5)
 *
 * La lupa del encabezado de Hoy abre esto. Escribir busca en toda la app ([buscarEnMovi]) con lo que
 * el cliente ya tiene leído —la historia completa, las cuentas, los terceros y las categorías—, y los
 * resultados salen agrupados por tipo, cada uno con su avatar. Tocar un movimiento abre su hoja acá
 * mismo; lo demás navega a su lugar.
 *
 * Los chips filtran: Todo / Gastos / Ingresos, «Este período» y una cuenta. Sin nada escrito se ven las
 * búsquedas recientes ([BusquedasRecientesStore]): se anota una al tocar un resultado o al apretar
 * «Buscar» en el teclado. Los montos pasan por [formatMoney], así que «Ocultar los saldos» los tapa
 * igual que en el resto de la app.
 */
@Composable
fun BuscarScreen(onNavigate: (Screen) -> Unit) {
    var consulta by remember { mutableStateOf("") }
    var filtros by remember { mutableStateOf(FiltrosDeBusqueda()) }
    var recarga by remember { mutableStateOf(0) }
    var eventoAbierto by remember { mutableStateOf<FinancialEvent?>(null) }

    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = recarga) { Repositories.wallets.getUserProfile() }
    val eventos = rememberLectura(ClaveDeLectura.EventosPorDia, reintento = recarga) { Repositories.wallets.getEventsByDay() }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = recarga) { Repositories.wallets.getAccounts() }
    val destinos = rememberLectura(ClaveDeLectura.Destinos, reintento = recarga) { Repositories.wallets.getDestinos() }
    var categoriasDelDueno by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(recarga) {
        intentar { Repositories.wallets.getCategories() }.onSuccess { lista -> categoriasDelDueno = lista.map { it.name } }
    }

    val periodo = remember(perfil.valor) {
        perfil.valor?.ajustesDelPeriodo()?.let { ajustes ->
            val ventana = ventanaDe(periodoActual(ahoraEnMs(), ajustes), ajustes)
            RangoDelPeriodo(ventana.first, ventana.last + 1)
        }
    }
    val todosLosEventos = remember(eventos.valor) { eventos.valor.orEmpty().flatMap { it.items } }
    val datos = remember(todosLosEventos, cuentas.valor, destinos.valor, categoriasDelDueno, UsedCategoriesCache.used) {
        DatosDeBusqueda(
            eventos = todosLosEventos,
            cuentas = cuentas.valor.orEmpty(),
            destinos = destinos.valor.orEmpty(),
            categorias = (categoriasDelDueno + UsedCategoriesCache.categories + todosLosEventos.map { it.category }).distinct(),
        )
    }
    val grupos = remember(consulta, filtros, datos, periodo) { buscarEnMovi(consulta, datos, filtros, periodo) }

    val anotar: () -> Unit = { BusquedasRecientesStore.anotar(consulta) }
    val alTocar: (ResultadoDeBusqueda) -> Unit = { resultado ->
        anotar()
        val destino = resultado.destino
        if (destino != null) onNavigate(destino)
        else if (resultado is ResultadoDeBusqueda.Movimiento) eventoAbierto = resultado.evento
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(title = TITULO_DE_BUSCAR, leading = HeaderLeading.Back(Screen.Dashboard))
            CampoDeBuscar(consulta = consulta, alCambiar = { consulta = it }, alBuscar = anotar)
            ChipsDeBuscar(
                filtros = filtros,
                hayPeriodo = periodo != null,
                cuentas = cuentas.valor.orEmpty().map { it.id to it.name },
                alCambiar = { filtros = it },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones),
            ) {
                // Todo va con el margen de la pantalla: los textos sueltos y cada grupo (su título y
                // su superficie). Dentro de la superficie las filas traen el suyo (ver [Fila]).
                val conMargen = Modifier.padding(horizontal = Medida.margenDePantalla)
                if (consulta.isBlank()) {
                    val recientes = BusquedasRecientesStore.recientes
                    if (recientes.isEmpty()) {
                        item(key = "pista") {
                            Text(
                                PISTA_DE_BUSCAR,
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                modifier = conMargen,
                            )
                        }
                    } else {
                        item(key = "recientes") {
                            GrupoDeBuscar(titulo = "Recientes", tag = TAG_GRUPO_DE_BUSCAR + "recientes", modifier = conMargen) {
                                recientes.forEachIndexed { i, reciente ->
                                    if (i > 0) Separador()
                                    FilaReciente(reciente, alElegir = { consulta = reciente }, alQuitar = { BusquedasRecientesStore.quitar(reciente) })
                                }
                            }
                        }
                    }
                } else if (grupos.isEmpty()) {
                    item(key = "nada") {
                        if (eventos.valor == null && eventos.actualizando) {
                            Text("Buscando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio, modifier = conMargen)
                        } else {
                            Vacio(
                                titulo = "No encontramos nada con «${consulta.trim()}»",
                                detalle = "Prueba con otra palabra, o con el monto exacto.",
                                modifier = conMargen.padding(top = Espacio.xxl),
                            )
                        }
                    }
                } else {
                    // Vuelta 35: cada grupo va en su superficie de lista, como en Movimientos, Ajustes
                    // y Patrimonio; antes las filas flotaban sueltas sobre el fondo. Un grupo es un
                    // solo `item` (son pocas filas: [TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA] y
                    // [TOPE_POR_GRUPO_EN_LA_BUSQUEDA]), así la superficie no se parte.
                    grupos.forEachIndexed { i, grupo ->
                        item(key = "grupo-${grupo.tipo}") {
                            GrupoDeBuscar(
                                titulo = grupo.tipo.titulo,
                                conteo = grupo.resultados.size,
                                tag = TAG_GRUPO_DE_BUSCAR + grupo.tipo.name,
                                modifier = conMargen.padding(top = if (i == 0) Espacio.xxs else Medida.entreSecciones),
                            ) {
                                grupo.resultados.forEachIndexed { j, resultado ->
                                    if (j > 0) Separador()
                                    FilaDeResultado(resultado, cuentas = datos.cuentas, alTocar = { alTocar(resultado) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    eventoAbierto?.let { evento ->
        HojaDelMovimiento(
            event = evento,
            cuentas = cuentas.valor.orEmpty(),
            onDismiss = { eventoAbierto = null },
            onCambiado = { eventoAbierto = null; recarga++ },
        )
    }
}

/**
 * La caja de Buscar: el [CampoDeBusqueda] del sistema (vuelta 35), con el foco al abrir y la tecla
 * «Buscar» del teclado, que anota la búsqueda en las recientes.
 */
@Composable
private fun CampoDeBuscar(consulta: String, alCambiar: (String) -> Unit, alBuscar: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    CampoDeBusqueda(
        valor = consulta,
        alCambiar = alCambiar,
        guia = GUIA_DE_BUSCAR,
        alBuscar = alBuscar,
        foco = foco,
        tag = TAG_CAMPO_DE_BUSCAR,
        tagDeLaGuia = TAG_GUIA_DE_BUSCAR,
        modifier = Modifier
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Espacio.xxs, bottom = Medida.dentroDeSeccion),
    )
}

/**
 * «Todo» · «Gastos» · «Ingresos» · «Este período» · la cuenta. Los tres primeros eligen el tipo
 * ([FiltroDeTipo]): uno siempre elegido, «Todo» por defecto, y tocar el elegido de Gastos o
 * Ingresos vuelve a «Todo». «Este período» y la cuenta se prenden y se apagan aparte.
 *
 * Vuelta de diseño 5: antes iba «Gastos · Ingresos · Todo · Cuenta», y ese «Todo» del medio era el
 * chip del período (sin elegir decía «Todo»; elegido, «Este período»): se leía como el tipo. Ahora
 * «Todo» es el tipo y el del período siempre dice «Este período». Lo que filtra cada uno no cambió.
 */
@Composable
private fun ChipsDeBuscar(
    filtros: FiltrosDeBusqueda,
    hayPeriodo: Boolean,
    cuentas: List<Pair<String, String>>,
    alCambiar: (FiltrosDeBusqueda) -> Unit,
) {
    var eligiendoCuenta by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Medida.margenDePantalla),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        PildoraDeFiltro(FiltroDeTipo.TODO.rotulo, filtros.tipo == FiltroDeTipo.TODO, onClick = {
            alCambiar(filtros.copy(tipo = FiltroDeTipo.TODO))
        })
        PildoraDeFiltro(FiltroDeTipo.GASTOS.rotulo, filtros.tipo == FiltroDeTipo.GASTOS, onClick = {
            alCambiar(filtros.copy(tipo = if (filtros.tipo == FiltroDeTipo.GASTOS) FiltroDeTipo.TODO else FiltroDeTipo.GASTOS))
        })
        PildoraDeFiltro(FiltroDeTipo.INGRESOS.rotulo, filtros.tipo == FiltroDeTipo.INGRESOS, onClick = {
            alCambiar(filtros.copy(tipo = if (filtros.tipo == FiltroDeTipo.INGRESOS) FiltroDeTipo.TODO else FiltroDeTipo.INGRESOS))
        })
        if (hayPeriodo) {
            PildoraDeFiltro(ESTE_PERIODO, filtros.soloEstePeriodo, onClick = {
                alCambiar(filtros.copy(soloEstePeriodo = !filtros.soloEstePeriodo))
            })
        }
        if (cuentas.isNotEmpty()) {
            Box {
                val elegida = cuentas.firstOrNull { it.first == filtros.cuentaId }?.second
                PildoraDeFiltro(elegida ?: "Cuenta", elegida != null, onClick = { eligiendoCuenta = true })
                DropdownMenu(
                    expanded = eligiendoCuenta,
                    onDismissRequest = { eligiendoCuenta = false },
                    containerColor = Movi.colores.capa1,
                ) {
                    DropdownMenuItem(
                        text = { Text("Todas las cuentas", style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                        onClick = { eligiendoCuenta = false; alCambiar(filtros.copy(cuentaId = null)) },
                    )
                    cuentas.forEach { (id, nombre) ->
                        DropdownMenuItem(
                            text = { Text(nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                            onClick = { eligiendoCuenta = false; alCambiar(filtros.copy(cuentaId = id)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * **Un grupo de Buscar**: su [Seccion] («Movimientos · 2») y, debajo, sus filas en una [Superficie]
 * de lista con el [Separador] entre una y otra — la forma de las listas de Movimientos, Ajustes y
 * Patrimonio. Las filas traen su propio margen ([Fila]); el grupo va con el de la pantalla.
 */
@Composable
private fun GrupoDeBuscar(
    titulo: String,
    tag: String,
    modifier: Modifier = Modifier,
    conteo: Int? = null,
    filas: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Seccion(titulo = titulo, conteo = conteo)
        Superficie(modifier = Modifier.fillMaxWidth().testTag(tag), relleno = RellenoDeSuperficie.Lista) {
            filas()
        }
    }
}

@Composable
private fun FilaReciente(texto: String, alElegir: () -> Unit, alQuitar: () -> Unit) {
    Fila(
        titulo = texto,
        modifier = Modifier.testTag(TAG_RECIENTE_DE_BUSCAR + texto),
        inicio = { IconoEnCirculo(Icons.Rounded.History, tono = TonoDeIcono.Neutro) },
        final = { BotonDeIcono(Icons.Rounded.Close, "Quitar «$texto» de las recientes", onClick = alQuitar) },
        onClick = alElegir,
    )
}

/** Un resultado: una [Fila] del sistema con el avatar de lo que es, su detalle y su cifra si la tiene. */
@Composable
private fun FilaDeResultado(
    resultado: ResultadoDeBusqueda,
    cuentas: List<com.jvillada.movi.shared.model.Account>,
    alTocar: () -> Unit,
) {
    val hoy = remember { epochMillisToAppDate(ahoraEnMs()) }
    // El monto como en Movimientos: la Cifra del sistema con el mismo signo y el mismo tono (el azul
    // de un traspaso incluido) y «Ocultar los saldos».
    val detalleYDerecha: Pair<String, (@Composable () -> Unit)?> = when (resultado) {
        is ResultadoDeBusqueda.Movimiento -> {
            val e = resultado.evento
            val laCuenta = cuentas.firstOrNull { it.id == e.accountId }
            val cuenta = laCuenta?.name
            val linea = listOfNotNull(e.category.takeIf { it.isNotBlank() }, etiquetaDeFecha(epochMillisToAppDate(e.timestamp), hoy), cuenta)
                .joinToString(" · ")
            // Vuelta 50: de un par se lista la pata que cuenta (`unaPataPorPar`), así que la cuota sale
            // como el gasto que es, «−$980.000».
            val tono = tonoDelEvento(e, laCuenta?.type)
            linea to @Composable { CifraDelMovimiento(e.amount, e.currency, tono) }
        }
        is ResultadoDeBusqueda.Comercio -> {
            val veces = when (resultado.enElPeriodo) {
                0 -> "Nada este período"
                1 -> "1 vez este período"
                else -> "${resultado.enElPeriodo} veces este período"
            }
            // Vuelta de diseño 30: con el signo y el tono del sistema, como los movimientos de arriba
            // («−$40.100» lo gastado en Uber); antes iba sin signo.
            val tono = tonoDelEvento(resultado.ejemplo)
            veces to resultado.totalDelPeriodo.takeIf { it > 0 }?.let { total ->
                @Composable { CifraDelMovimiento(total, resultado.moneda, tono) }
            }
        }
        is ResultadoDeBusqueda.Persona -> {
            val d = resultado.destinoConocido
            (d.deQuien?.takeIf { it.isNotBlank() } ?: if (d.tipo?.name == "COMERCIO") "Comercio" else "Persona") to null
        }
        is ResultadoDeBusqueda.Categoria -> "Categoría" to null
        is ResultadoDeBusqueda.Cuenta -> resultado.cuenta.type.groupLabel to
            @Composable { Cifra(resultado.cuenta.balance, moneda = resultado.cuenta.currency) }
        is ResultadoDeBusqueda.Pantalla -> resultado.donde to null
    }
    val (detalle, derecha) = detalleYDerecha
    Fila(
        titulo = resultado.titulo,
        subtitulo = detalle,
        modifier = Modifier.testTag(TAG_RESULTADO_DE_BUSCAR + resultado.clave),
        inicio = {
            when (resultado) {
                is ResultadoDeBusqueda.Movimiento -> AvatarDelComercio(resultado.evento)
                is ResultadoDeBusqueda.Comercio -> AvatarDelComercio(resultado.ejemplo)
                is ResultadoDeBusqueda.Categoria -> IconoDeCategoria(resultado.nombre)
                is ResultadoDeBusqueda.Cuenta -> AvatarDeLaCuenta(resultado.cuenta)
                is ResultadoDeBusqueda.Persona -> IconoEnCirculo(Icons.Rounded.Groups, tono = TonoDeIcono.Neutro)
                is ResultadoDeBusqueda.Pantalla -> IconoEnCirculo(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tono = TonoDeIcono.Neutro)
            }
        },
        final = derecha,
        onClick = alTocar,
    )
}
