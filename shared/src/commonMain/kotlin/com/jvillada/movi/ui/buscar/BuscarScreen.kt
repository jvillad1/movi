package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.ahoraEnMs
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
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
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.transactions.HojaDelMovimiento

/** El `testTag` del campo de búsqueda. */
const val TAG_CAMPO_DE_BUSCAR: String = "buscar-campo"

/** El `testTag` de un resultado: el prefijo y su [ResultadoDeBusqueda.clave]. */
const val TAG_RESULTADO_DE_BUSCAR: String = "buscar-resultado-"

/** El `testTag` de una búsqueda reciente: el prefijo y el texto. */
const val TAG_RECIENTE_DE_BUSCAR: String = "buscar-reciente-"

/** El título de la pantalla. */
const val TITULO_DE_BUSCAR: String = "Buscar"

/**
 * # «Buscar» (Ola 5)
 *
 * La lupa del encabezado de Hoy abre esto. Escribir busca en toda la app ([buscarEnMovi]) con lo que
 * el cliente ya tiene leído —la historia completa, las cuentas, los terceros y las categorías—, y los
 * resultados salen agrupados por tipo, cada uno con su avatar. Tocar un movimiento abre su hoja acá
 * mismo; lo demás navega a su lugar.
 *
 * Los chips filtran: Gastos / Ingresos, «Este período» y una cuenta. Sin nada escrito se ven las
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
            Spacer(Modifier.height(12.dp))
            CampoDeBuscar(consulta = consulta, alCambiar = { consulta = it }, alBuscar = anotar)
            ChipsDeBuscar(
                filtros = filtros,
                hayPeriodo = periodo != null,
                cuentas = cuentas.valor.orEmpty().map { it.id to it.name },
                alCambiar = { filtros = it },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (consulta.isBlank()) {
                    val recientes = BusquedasRecientesStore.recientes
                    if (recientes.isEmpty()) {
                        item(key = "pista") {
                            Text(
                                "Busca movimientos, comercios, personas, categorías, cuentas o pantallas. Un número busca el monto exacto.",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        item(key = "recientes-titulo") { MinSectionHeader(title = "Recientes") }
                        items(recientes, key = { "reciente-$it" }) { reciente ->
                            FilaReciente(reciente, alElegir = { consulta = reciente }, alQuitar = { BusquedasRecientesStore.quitar(reciente) })
                        }
                    }
                } else if (grupos.isEmpty()) {
                    item(key = "nada") {
                        Text(
                            if (eventos.valor == null && eventos.actualizando) "Buscando…" else "No encontramos nada con «${consulta.trim()}».",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    grupos.forEach { grupo ->
                        item(key = "grupo-${grupo.tipo}") {
                            Spacer(Modifier.height(8.dp))
                            MinSectionHeader(title = grupo.tipo.titulo, count = grupo.resultados.size)
                        }
                        items(grupo.resultados, key = { it.clave }) { resultado ->
                            FilaDeResultado(resultado, cuentas = datos.cuentas, alTocar = { alTocar(resultado) })
                        }
                    }
                }
                item(key = "pie") { Spacer(Modifier.height(24.dp)) }
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

@Composable
private fun CampoDeBuscar(consulta: String, alCambiar: (String) -> Unit, alBuscar: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Movi.colores.textoApagado, modifier = Modifier.size(16.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (consulta.isEmpty()) {
                Text("Movimientos, comercios, personas, pantallas…", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado, maxLines = 1)
            }
            val campo = rememberCampoConSeleccion(consulta, alCambiar)
            BasicTextField(
                value = campo.valor,
                onValueChange = campo::alCambiar,
                singleLine = true,
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                cursorBrush = SolidColor(Movi.colores.texto),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { alBuscar() }),
                modifier = Modifier.fillMaxWidth().testTag(TAG_CAMPO_DE_BUSCAR).focusRequester(foco)
                    .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            )
        }
        if (consulta.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Borrar la búsqueda",
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(16.dp).clickable { alCambiar("") },
            )
        }
    }
}

@Composable
private fun Chip(texto: String, activo: Boolean, alTocar: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (activo) Movi.colores.tarjeta else Color.Transparent)
            .then(if (!activo) Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = alTocar)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (activo) Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(14.dp))
        Text(
            texto,
            style = Movi.textos.cuerpo,
            fontWeight = FontWeight.Medium,
            color = if (activo) Movi.colores.texto else Movi.colores.textoMedio,
            maxLines = 1,
        )
    }
}

/** «Gastos» · «Ingresos» · «Este período» · la cuenta. Tocar un chip activo lo apaga. */
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
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(FiltroDeTipo.GASTOS.rotulo, filtros.tipo == FiltroDeTipo.GASTOS) {
            alCambiar(filtros.copy(tipo = if (filtros.tipo == FiltroDeTipo.GASTOS) FiltroDeTipo.TODO else FiltroDeTipo.GASTOS))
        }
        Chip(FiltroDeTipo.INGRESOS.rotulo, filtros.tipo == FiltroDeTipo.INGRESOS) {
            alCambiar(filtros.copy(tipo = if (filtros.tipo == FiltroDeTipo.INGRESOS) FiltroDeTipo.TODO else FiltroDeTipo.INGRESOS))
        }
        if (hayPeriodo) {
            Chip(if (filtros.soloEstePeriodo) "Este período" else "Todo", filtros.soloEstePeriodo) {
                alCambiar(filtros.copy(soloEstePeriodo = !filtros.soloEstePeriodo))
            }
        }
        if (cuentas.isNotEmpty()) {
            Box {
                val elegida = cuentas.firstOrNull { it.first == filtros.cuentaId }?.second
                Chip(elegida ?: "Cuenta", elegida != null) { eligiendoCuenta = true }
                DropdownMenu(
                    expanded = eligiendoCuenta,
                    onDismissRequest = { eligiendoCuenta = false },
                    containerColor = Movi.colores.tarjeta,
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

@Composable
private fun FilaReciente(texto: String, alElegir: () -> Unit, alQuitar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_RECIENTE_DE_BUSCAR + texto)
            .clickable(onClick = alElegir)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = Movi.colores.textoApagado, modifier = Modifier.size(18.dp))
        Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Quitar «$texto» de las recientes",
            tint = Movi.colores.textoApagado,
            modifier = Modifier.size(16.dp).clickable(onClick = alQuitar),
        )
    }
}

@Composable
private fun FilaDeResultado(
    resultado: ResultadoDeBusqueda,
    cuentas: List<com.jvillada.movi.shared.model.Account>,
    alTocar: () -> Unit,
) {
    val hoy = remember { epochMillisToAppDate(ahoraEnMs()) }
    val (detalle, derecha) = when (resultado) {
        is ResultadoDeBusqueda.Movimiento -> {
            val e = resultado.evento
            val cuenta = cuentas.firstOrNull { it.id == e.accountId }?.name
            val linea = listOfNotNull(e.category.takeIf { it.isNotBlank() }, etiquetaDeFecha(epochMillisToAppDate(e.timestamp), hoy), cuenta)
                .joinToString(" · ")
            val signo = if (e.type == TransactionType.INCOME) "+" else ""
            linea to (signo + formatMoney(e.amount, e.currency))
        }
        is ResultadoDeBusqueda.Comercio -> {
            val veces = when (resultado.enElPeriodo) {
                0 -> "Nada este período"
                1 -> "1 vez este período"
                else -> "${resultado.enElPeriodo} veces este período"
            }
            veces to resultado.totalDelPeriodo.takeIf { it > 0 }?.let { formatMoney(it, resultado.moneda) }
        }
        is ResultadoDeBusqueda.Persona -> {
            val d = resultado.destinoConocido
            (d.deQuien?.takeIf { it.isNotBlank() } ?: if (d.tipo?.name == "COMERCIO") "Comercio" else "Persona") to null
        }
        is ResultadoDeBusqueda.Categoria -> "Categoría" to null
        is ResultadoDeBusqueda.Cuenta -> resultado.cuenta.type.groupLabel to formatMoney(resultado.cuenta.balance, resultado.cuenta.currency)
        is ResultadoDeBusqueda.Pantalla -> resultado.donde to null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_RESULTADO_DE_BUSCAR + resultado.clave)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = alTocar)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (resultado) {
            is ResultadoDeBusqueda.Movimiento -> AvatarDelComercio(resultado.evento)
            is ResultadoDeBusqueda.Comercio -> AvatarDelComercio(resultado.ejemplo)
            is ResultadoDeBusqueda.Categoria -> IconoDeCategoria(resultado.nombre)
            is ResultadoDeBusqueda.Cuenta -> AvatarDeLaCuenta(resultado.cuenta)
            is ResultadoDeBusqueda.Persona -> CirculoConIcono { Icon(Icons.Rounded.Groups, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(18.dp)) }
            is ResultadoDeBusqueda.Pantalla -> CirculoConIcono { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(18.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(resultado.titulo, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (derecha != null) {
            Text(derecha, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1)
        }
    }
}

@Composable
private fun CirculoConIcono(contenido: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).background(Movi.colores.tarjeta, CircleShape).border(1.dp, Movi.colores.borde, CircleShape),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}
