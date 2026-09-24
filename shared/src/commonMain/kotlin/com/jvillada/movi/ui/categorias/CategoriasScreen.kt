package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.COLORES_DEL_CATALOGO
import com.jvillada.movi.theme.ColorDelCatalogo
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.data.FormaDeCategorias
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.CirculoEsqueleto
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.TAG_TITULO_DE_FILA_ESQUELETO
import com.jvillada.movi.ui.components.columnasDeLaCuadricula
import com.jvillada.movi.ui.components.leadingFor
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/**
 * **«Más → Categorías»** — la pantalla para ver, arreglar y ordenar las categorías.
 *
 * El dueño preguntó si las listas de categorías deberían estar discriminadas por tipo, y si me
 * imaginaba una lista de categorías comunes. La respuesta que implementa esta pantalla es la
 * segunda: **una sola lista, con el tipo como filtro**. Cuatro catálogos separados se
 * desincronizan —de eso salió que «Otros» y «Otros ingresos» sean la misma idea partida en dos— y
 * obligan a elegir el tipo antes de poder buscar la categoría, que es al revés de como piensa
 * quien está anotando un gasto.
 *
 * Lo que se puede hacer acá, y por qué cada cosa:
 *
 * - **Ver el uso real.** Cuántos movimientos la llevan, cuánto suman, este mes y en total. Sin
 *   eso, la lista sería una fila de nombres y no habría forma de decidir qué sobra.
 * - **Renombrar** — el arreglo del error de tipeo. Hoy, si escribió «Trasnporte» una vez, queda
 *   como sugerencia para siempre y su gasto en transporte se parte en dos sin que nada lo avise.
 * - **Unificar** — el arreglo de los duplicados que ya tenga, incluido juntar «Otros ingresos»
 *   dentro de «Otros» si decide que son la misma.
 * - **Esconder** — que deje de sugerirse **sin tocar la historia**. No es borrar: los movimientos
 *   viejos la siguen diciendo y siguen contando donde contaban.
 * - **Fijar el tipo** — gasto, ingreso o ambos, por encima de lo que diga el catálogo o de lo
 *   aprendido del uso.
 * - **Elegir ícono y color** (Ola B, tarea 5) — la hoja de detalle deja corregir lo que
 *   [aparienciaDe] adivinó por el nombre, ahí mismo, sin pasar por otra pantalla.
 *
 * Las reservadas (`isReservedCategory`: traspasos, saldos iniciales, ajustes…) no aparecen en
 * ninguna fila — las escribe Movi sola y no se pueden tocar — pero la pantalla lo explica en un
 * pie si el dueño tiene alguna, en vez de mostrar renglones muertos.
 *
 * Renombrar y unificar **reescriben tres tablas** (`financial_events`, `budgets`,
 * `recurring_rules`) en una sola transacción del server; ver `CategoryRoutes.rewriteCategory`.
 */

/** En qué está la hoja abierta, si hay alguna. */
private sealed class Hoja {
    data class Detalle(val categoria: CategoryUsage) : Hoja()
    data class Renombrar(val categoria: CategoryUsage) : Hoja()
    /**
     * [soloVisibles] (fix round 1, hallazgo 3): la tarjeta de propuestas de orden abre esta misma
     * hoja para «un solo uso» y pide `true` — nada de ofrecer como destino una categoría que el
     * dueño ya escondió, cuando la está eligiendo desde una sugerencia automática y no desde su
     * propio «Unificar en otra». El detalle de una categoría sigue mandando `false` (default):
     * ahí el dueño ya sabe qué está buscando.
     */
    data class Unificar(val categoria: CategoryUsage, val soloVisibles: Boolean = false) : Hoja()
}

/**
 * Unificar [origen] en [destino]: la llamada al server más el espejo en `UsedCategoriesCache`,
 * **en un solo lugar**. La hoja de detalle (unir con otra, y la colisión al renombrar) y la
 * tarjeta de propuestas de orden (Ola B, tarea 6) hacen exactamente lo mismo con el resultado, así
 * que las dos llaman acá en vez de repetir el `runCatching` + `applyRename`.
 */
private suspend fun unificarCategoria(origen: String, destino: String): Result<CategoryRewriteResult> =
    runCatching { Repositories.wallets.mergeCategory(origen, destino) }
        .onSuccess { r -> UsedCategoriesCache.applyRename(origen, r.name, escondeElOrigen = true) }

/**
 * Esconder o mostrar [categoria]: mismo criterio que [unificarCategoria] — la hoja de detalle y la
 * tarjeta de propuestas de orden llaman acá en vez de repetir el `runCatching` + `applyPref`.
 */
private suspend fun ponerVisibilidad(categoria: CategoryUsage, escondida: Boolean): Result<CategoryUsage> =
    runCatching { Repositories.wallets.setCategoryPrefs(categoria.name, escondida, categoria.pinnedType) }
        .onSuccess { p -> UsedCategoriesCache.applyPref(p.name, CategoryPref(p.hidden, p.pinnedType, p.icono, p.color)) }

@Composable
fun CategoriasScreen(onNavigate: (Screen) -> Unit) {
    var categorias by remember { mutableStateOf<List<CategoryUsage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filtro by remember { mutableStateOf(CategoryFilter.TODAS) }
    var busqueda by remember { mutableStateOf("") }
    var hoja by remember { mutableStateOf<Hoja?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Lo que acaba de pasar, dicho con números («12 movimientos ahora dicen Transporte»). Un
    // «listo» mudo después de reescribir la historia no alcanza para confiar en que salió bien.
    var confirmacion by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Lo que falló dentro de una hoja de renombrar/unificar, y si hay una en vuelo. Viven acá y no
    // en la hoja porque es acá donde termina la llamada: antes un fallo cerraba la hoja y el dueño
    // tenía que volver a escribir el nombre. Mismo criterio que presupuestos, metas y recurrentes.
    var errorDeHoja by remember { mutableStateOf<String?>(null) }
    var guardandoHoja by remember { mutableStateOf(false) }
    // Revisión final de la Ola B: lo mismo que `guardandoHoja`, para los toques de la hoja de
    // detalle (ícono, color, tipo). El server guarda la preferencia borrando e insertando la fila;
    // dos toques rápidos mandaban dos de esas en paralelo, y la segunda chocaba con la llave
    // primaria de la primera — un 500 que el dueño leía como «no se guardó».
    var guardandoPrefs by remember { mutableStateOf(false) }
    // Ver [NoSePudoLeer]: «0 categorías · Nada por aquí todavía» solo si la lectura contestó.
    var leidas by remember { mutableStateOf(false) }

    // ── Ola B · tarea 6: «Ordena tus categorías» ─────────────────────────────
    // Los «Ahora no» viven en el aparato (PropuestasDescartadasStore), no en Compose — se leen acá
    // una vez y de ahí en más esta variable es la fuente de verdad de la pantalla, igual que
    // `DiasPlegadosStore.alternar` se refleja en el `remember` de Movimientos.
    var propuestasDescartadas by remember { mutableStateOf(PropuestasDescartadasStore.descartadas()) }
    var revisandoOrden by remember { mutableStateOf(false) }
    var errorDeOrden by remember { mutableStateOf<String?>(null) }
    var guardandoOrden by remember { mutableStateOf(false) }

    // La forma de la última carga que salió bien (ver `FormaRecordada`): si había tarjeta de
    // «ordenar» y cuántas filas. Se lee una vez, al montar; el esqueleto la copia.
    val formaRecordada = remember { FormaRecordada.delAparato.categorias(SessionManager.userId) }
    // Los renglones que ocupó el texto de la tarjeta de «ordenar», medidos al dibujarse. 0 = sin medir.
    var renglonesDeLaTarjetaDeOrden by remember { mutableStateOf(0) }

    suspend fun recargar() {
        runCatching { Repositories.wallets.getCategories() }
            .onSuccess { categorias = it; error = null; leidas = true }
            .onFailure { error = it.toUserMessage() }
    }

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshTick) {
        loading = true
        recargar()
        loading = false
    }

    // Ola B, tarea 2: el título contaba TODAS las que trae el server, reservadas incluidas —
    // «35 categorías» cuando la lista de abajo (que ya las saca, ver `filtrarCategorias`) muestra
    // menos. Cuenta lo que la pantalla de verdad enumera.
    val categoriasVisibles = categorias.count { !it.reserved }
    // Fix round 1: una reservada nunca debería venir marcada `hidden`, pero si algún día lo
    // trajera el server, no puede contarse acá — ya no cuenta en `categoriasVisibles`.
    val escondidas = categorias.count { it.hidden && !it.reserved }
    // Destapar la ÚLTIMA escondida hacía desaparecer su pastilla y dejaba el filtro apuntando a
    // un conjunto vacío: «17 categorías» arriba, «Nada por aquí todavía» abajo y ninguna pastilla
    // marcada, sin nada que indicara cómo salir. El filtro cae solo a «Todas» cuando deja de tener
    // sentido — y se corrige también la variable, no solo lo que se muestra, para que la pastilla
    // marcada y la lista no puedan contradecirse.
    LaunchedEffect(escondidas) {
        if (escondidas == 0 && filtro == CategoryFilter.ESCONDIDAS) filtro = CategoryFilter.TODAS
    }
    val visibles = remember(categorias, filtro, busqueda) {
        filtrarCategorias(categorias, filtro, busqueda)
    }
    // Recalculada apenas cambian las categorías o algún «Ahora no» — la propuesta que se acaba de
    // resolver (unificada, escondida) desaparece sola en el próximo `recargar()`, sin que haga
    // falta tocar `propuestasDescartadas` para eso.
    //
    // Fix round 1, hallazgo 1: `propuestasDescartadas` se le pasa a `propuestasDeOrden` para que
    // filtre ANTES de cortar en 12 — filtrar acá afuera, después del corte, dejaba sin lugar a la
    // propuesta 13ª apenas el dueño decía «Ahora no» a alguna de las primeras 12.
    val propuestasPendientes = remember(categorias, propuestasDescartadas) {
        propuestasDeOrden(categorias, propuestasDescartadas)
    }
    // Si la última propuesta se resolvió o se descartó con la hoja abierta, se cierra sola: no hay
    // nada más que revisar y quedaría una hoja vacía esperando un toque que no sirve para nada.
    LaunchedEffect(revisandoOrden, propuestasPendientes.isEmpty()) {
        if (revisandoOrden && propuestasPendientes.isEmpty()) revisandoOrden = false
    }
    // Se escribe con una lectura que salió bien, y con la tarjeta de «ordenar» ya medida si está.
    // Las filas son las del filtro de siempre («Todas», sin búsqueda): es lo que se ve al abrir.
    LaunchedEffect(leidas, categorias, propuestasPendientes.isNotEmpty(), renglonesDeLaTarjetaDeOrden) {
        if (!leidas) return@LaunchedEffect
        val hayTarjeta = propuestasPendientes.isNotEmpty()
        if (hayTarjeta && renglonesDeLaTarjetaDeOrden == 0) return@LaunchedEffect
        FormaRecordada.delAparato.guardarCategorias(
            SessionManager.userId,
            FormaDeCategorias(
                renglonesDeLaTarjetaDeOrden = if (hayTarjeta) renglonesDeLaTarjetaDeOrden else 0,
                filas = filtrarCategorias(categorias, CategoryFilter.TODAS, "").size,
            ),
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(
                title = "Categorías",
                leading = leadingFor(
                    Screen.Categorias,
                    onProfile = { onNavigate(Screen.Profile) },
                    fallback = Screen.Mas,
                ),
                subtitle = when {
                    loading || !leidas -> null
                    escondidas == 1 -> "$categoriasVisibles categorías · 1 escondida"
                    escondidas > 1 -> "$categoriasVisibles categorías · $escondidas escondidas"
                    else -> "$categoriasVisibles categorías"
                },
            )

            // Ola B · tarea 6: la tarjeta va ARRIBA de los filtros y la búsqueda, y solo si hay
            // algo pendiente — nada se ofrece dos veces, y un «Ahora no» la saca de acá.
            if (propuestasPendientes.isNotEmpty()) {
                TarjetaDeOrden(
                    cantidad = propuestasPendientes.size,
                    onRevisar = { errorDeOrden = null; revisandoOrden = true },
                    onRenglones = { renglonesDeLaTarjetaDeOrden = it },
                )
            } else if (loading && !leidas && (formaRecordada?.renglonesDeLaTarjetaDeOrden ?: 0) > 0) {
                // La última carga la tenía: se reserva su lugar para que la lista no baje cuando
                // llegue. Ver `FormaRecordada`.
                TarjetaDeOrdenEsqueleto(formaRecordada!!.renglonesDeLaTarjetaDeOrden)
            }

            // Las pastillas de filtro son, literalmente, la respuesta a la pregunta: el tipo
            // filtra una sola lista, no la parte en varias.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryFilter.entries.forEach { f ->
                    if (f == CategoryFilter.ESCONDIDAS && escondidas == 0) return@forEach
                    Pastilla(
                        texto = if (f == CategoryFilter.ESCONDIDAS) "${etiquetaDeFiltro(f)} ($escondidas)"
                        else etiquetaDeFiltro(f),
                        activa = filtro == f,
                        onClick = { filtro = f },
                    )
                }
            }

            CampoDeBusqueda(
                valor = busqueda,
                onValorCambia = { busqueda = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (error != null) {
                Text(
                    error!!,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.sale,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            if (confirmacion != null) {
                Text(
                    confirmacion!!,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.entra,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading && !leidas) {
                    // Ola B, tarea 9: antes de la primera lectura buena la pantalla quedaba en
                    // blanco entre los filtros/la búsqueda y la primera fila — ver
                    // [categoriasEsqueleto]. Con `leidas` ya en `true` (una recarga con la lista
                    // en pantalla) no vuelve a mostrarse: la lista de siempre sigue ahí.
                    categoriasEsqueleto(formaRecordada?.filas ?: FILAS_DE_CATEGORIA_ESQUELETO)
                } else if (!leidas && !loading) {
                    item {
                        NoSePudoLeer(
                            "No pudimos cargar tus categorías",
                            onReintentar = { scope.launch { loading = true; recargar(); loading = false } },
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else if (visibles.isEmpty() && !loading) {
                    item {
                        Text(
                            if (busqueda.isNotBlank()) "Ninguna categoría se llama así."
                            else "Nada por aquí todavía.",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(visibles, key = { it.name }) { categoria ->
                    FilaDeCategoria(categoria) {
                        // Abrir otra categoría es empezar otra cosa: el «Listo: …» de la anterior
                        // ya no describe lo que está pasando y se iba quedando indefinidamente.
                        confirmacion = null
                        hoja = Hoja.Detalle(categoria)
                    }
                }
                // Las reservadas ya no salen como filas (ver `filtrarCategorias`): sin este pie,
                // el dueño vería menos categorías de las que el server tiene y no sabría por qué.
                if (categorias.any { it.reserved }) {
                    item {
                        Text(
                            "Movi también usa categorías propias para traspasos, saldos " +
                                "iniciales y ajustes; no se editan.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }

        // ── Las hojas ─────────────────────────────────────────────────────────
        // Cada acción vuelve a cargar la lista del server y, además, le avisa al caché que lee el
        // campo de categoría de «Agregar» (UsedCategoriesCache): sin ese aviso, esconder una
        // categoría no se notaría hasta el próximo paso por el Inicio y se leería como que el
        // botón no hizo nada.
        when (val h = hoja) {
            null -> Unit
            is Hoja.Detalle -> HojaDetalle(
                categoria = h.categoria,
                onDismiss = { hoja = null },
                onRenombrar = { hoja = Hoja.Renombrar(h.categoria) },
                onUnificar = { hoja = Hoja.Unificar(h.categoria) },
                onCambiarVisibilidad = { escondida ->
                    scope.launch {
                        ponerVisibilidad(h.categoria, escondida)
                            .onSuccess {
                                confirmacion = if (escondida)
                                    "«${h.categoria.name}» ya no se te va a sugerir. Sus movimientos siguen ahí."
                                else "«${h.categoria.name}» vuelve a sugerirse."
                                error = null
                                hoja = null
                                recargar()
                            }
                            .onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                    }
                },
                onFijarTipo = { tipo ->
                    if (guardandoPrefs) return@HojaDetalle
                    guardandoPrefs = true
                    scope.launch {
                        runCatching {
                            Repositories.wallets.setCategoryPrefs(h.categoria.name, h.categoria.hidden, tipo)
                        }.onSuccess {
                            UsedCategoriesCache.applyPref(
                                it.name, CategoryPref(it.hidden, it.pinnedType, it.icono, it.color),
                            )
                            error = null
                            recargar()
                            // La hoja se queda abierta con el dato fresco: fijar el tipo es un
                            // ajuste, no un trámite, y cerrarla obligaría a volver a entrar para
                            // ver el resultado.
                            hoja = Hoja.Detalle(
                                categorias.firstOrNull { c -> c.name == h.categoria.name } ?: h.categoria,
                            )
                        }.onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                        guardandoPrefs = false
                    }
                },
                // Ola B · tarea 5: elegir ícono/color, o «Volver al de Movi» (que manda `""` en
                // los dos campos). Mismo criterio que `onFijarTipo`: la hoja se queda abierta con
                // el dato fresco, así el dueño ve el cambio sin tener que reabrir nada.
                onCambiarApariencia = { icono, color ->
                    if (guardandoPrefs) return@HojaDetalle
                    guardandoPrefs = true
                    scope.launch {
                        runCatching {
                            Repositories.wallets.setCategoryPrefs(
                                h.categoria.name, h.categoria.hidden, h.categoria.pinnedType, icono, color,
                            )
                        }.onSuccess {
                            UsedCategoriesCache.applyPref(
                                it.name, CategoryPref(it.hidden, it.pinnedType, it.icono, it.color),
                            )
                            error = null
                            recargar()
                            hoja = Hoja.Detalle(
                                categorias.firstOrNull { c -> c.name == h.categoria.name } ?: h.categoria,
                            )
                        }.onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                        guardandoPrefs = false
                    }
                },
            )

            is Hoja.Renombrar -> HojaRenombrar(
                categoria = h.categoria,
                existentes = categorias,
                error = errorDeHoja,
                guardando = guardandoHoja,
                onDismiss = { hoja = null; errorDeHoja = null },
                onConfirmar = { nuevoNombre, esUnificacion ->
                    if (guardandoHoja) return@HojaRenombrar
                    guardandoHoja = true
                    errorDeHoja = null
                    scope.launch {
                        runCatching {
                            if (esUnificacion) Repositories.wallets.mergeCategory(h.categoria.name, nuevoNombre)
                            else Repositories.wallets.renameCategory(h.categoria.name, nuevoNombre)
                        }.onSuccess { r ->
                            // `escondeElOrigen` = lo mismo que hizo el server: unificar esconde la
                            // de origen si venía del catálogo, renombrar no (ver `rewriteCategory`).
                            UsedCategoriesCache.applyRename(h.categoria.name, r.name, escondeElOrigen = esUnificacion)
                            confirmacion = textoDeResultado(r.movements, r.budgets, r.recurringRules, r.budgetsMerged, r.name)
                            error = null
                            hoja = null
                            recargar()
                        }.onFailure { errorDeHoja = it.toUserMessage() }
                        guardandoHoja = false
                    }
                },
            )

            is Hoja.Unificar -> HojaUnificar(
                categoria = h.categoria,
                existentes = categorias,
                soloVisibles = h.soloVisibles,
                error = errorDeHoja,
                guardando = guardandoHoja,
                onDismiss = { hoja = null; errorDeHoja = null },
                onConfirmar = { destino ->
                    if (guardandoHoja) return@HojaUnificar
                    guardandoHoja = true
                    errorDeHoja = null
                    scope.launch {
                        unificarCategoria(h.categoria.name, destino)
                            .onSuccess { r ->
                                confirmacion = textoDeResultado(r.movements, r.budgets, r.recurringRules, r.budgetsMerged, r.name)
                                error = null
                                hoja = null
                                recargar()
                            }
                            .onFailure { errorDeHoja = it.toUserMessage() }
                        guardandoHoja = false
                    }
                },
            )
        }

        // ── Ola B · tarea 6: la hoja de «Ordena tus categorías» ──────────────────
        // Independiente de `hoja` (arriba): las dos pueden coexistir mientras una termina de
        // cerrarse, y mezclarlas en el mismo `sealed class` obligaría a las otras ramas a saber
        // de propuestas que no les incumben.
        if (revisandoOrden) {
            val actual = propuestasPendientes.firstOrNull()
            if (actual != null) {
                HojaDeOrden(
                    propuesta = actual,
                    restantes = propuestasPendientes.size,
                    guardando = guardandoOrden,
                    error = errorDeOrden,
                    onUnificar = { origen, destino ->
                        if (guardandoOrden) return@HojaDeOrden
                        guardandoOrden = true
                        errorDeOrden = null
                        scope.launch {
                            unificarCategoria(origen, destino)
                                .onSuccess { r ->
                                    // Fix round 1, hallazgo 6: el mismo texto de resultado que ve
                                    // quien unifica desde el detalle de una categoría — no un
                                    // «listo» distinto según por dónde entró.
                                    confirmacion = textoDeResultado(r.movements, r.budgets, r.recurringRules, r.budgetsMerged, r.name)
                                    errorDeOrden = null
                                    recargar()
                                }
                                .onFailure { errorDeOrden = it.toUserMessage() }
                            guardandoOrden = false
                        }
                    },
                    onEsconder = { categoria ->
                        if (guardandoOrden) return@HojaDeOrden
                        guardandoOrden = true
                        errorDeOrden = null
                        scope.launch {
                            ponerVisibilidad(categoria, true)
                                .onSuccess { errorDeOrden = null; recargar() }
                                .onFailure { errorDeOrden = it.toUserMessage() }
                            guardandoOrden = false
                        }
                    },
                    // Fix round 1, hallazgo 3: «un solo uso» ya no unifica con un toque adentro de
                    // esta hoja — abre la MISMA `Hoja.Unificar` que usa el detalle de una
                    // categoría (búsqueda, aviso previo, botón de confirmar), con `soloVisibles`
                    // para que una categoría escondida no aparezca como destino sugerido.
                    onUnificarConOtra = { categoria ->
                        revisandoOrden = false
                        errorDeOrden = null
                        hoja = Hoja.Unificar(categoria, soloVisibles = true)
                    },
                    onAhoraNo = {
                        propuestasDescartadas = PropuestasDescartadasStore.marcar(claveDePropuesta(actual))
                        errorDeOrden = null
                    },
                    onDismiss = { revisandoOrden = false; errorDeOrden = null },
                )
            }
        }
    }
}

/** El resultado con números. Ver [CategoriasLogic] para el aviso PREVIO, que es el que importa. */
private fun textoDeResultado(
    movimientos: Int,
    presupuestos: Int,
    recurrentes: Int,
    presupuestosSumados: Boolean,
    nombre: String,
): String {
    val partes = mutableListOf<String>()
    if (movimientos > 0) partes += if (movimientos == 1) "1 movimiento" else "$movimientos movimientos"
    if (presupuestos > 0) partes += "su presupuesto"
    if (recurrentes > 0) partes += if (recurrentes == 1) "1 recurrente" else "$recurrentes recurrentes"
    val base = if (partes.isEmpty()) "Listo: ahora se llama «$nombre»."
    else "Listo: ${partes.joinToString(", ")} ahora dicen «$nombre»."
    return if (presupuestosSumados) "$base Los dos presupuestos se sumaron en uno." else base
}

// ── Ola B · tarea 6: «Ordena tus categorías» ────────────────────────────────────

/**
 * «Movi encontró N cosas para ordenar», arriba de los filtros y la búsqueda — solo aparece cuando
 * [propuestasDeOrden] tiene algo pendiente. Un borde y no el color de marca de lleno: es una
 * sugerencia que el dueño puede ignorar del todo, no una alerta.
 */
@Composable
private fun TarjetaDeOrden(cantidad: Int, onRevisar: () -> Unit, onRenglones: (Int) -> Unit = {}) {
    val forma = RoundedCornerShape(Movi.formas.amplia)
    Row(
        modifier = Modifier
            .testTag(TAG_TARJETA_DE_ORDEN)
            .fillMaxWidth()
            .padding(start = Movi.espacios.amplio, top = Movi.espacios.medio, end = Movi.espacios.amplio)
            .clip(forma)
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, forma)
            .clickable(onClick = onRevisar)
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.amplio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        Text(
            text = textoDeLaTarjetaDeOrden(cantidad),
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
            // Cuántos renglones ocupó, para que la próxima carga reserve ese alto (ver
            // `FormaRecordada`).
            onTextLayout = { onRenglones(it.lineCount) },
        )
        Text(
            "Revisar",
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.marca,
        )
    }
}

/** La tarjeta de «ordenar», cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DE_ORDEN: String = "tarjeta-de-orden"

/**
 * [TarjetaDeOrden] mientras la lista no llegó, **solo si la última carga la tenía** (ver
 * `FormaRecordada`): mismos rellenos, borde y forma, un bloque de [renglones] renglones de
 * `Movi.textos.cuerpo` donde va el texto y otro del alto de `Movi.textos.apoyo` donde va
 * «Revisar». Sin él, la tarjeta aparecía con los datos y empujaba la lista ~50 dp.
 */
@Composable
private fun TarjetaDeOrdenEsqueleto(renglones: Int) {
    val forma = RoundedCornerShape(Movi.formas.amplia)
    Row(
        modifier = Modifier
            .testTag(TAG_TARJETA_DE_ORDEN)
            .fillMaxWidth()
            .padding(start = Movi.espacios.amplio, top = Movi.espacios.medio, end = Movi.espacios.amplio)
            .clip(forma)
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, forma)
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.amplio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        Box(Modifier.weight(1f)) {
            BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.cuerpo) * renglones, ancho = null)
        }
        BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 52.dp)
    }
}

/**
 * La hoja que revisa las propuestas **una por una**: [propuesta] es siempre la primera pendiente
 * (la quita `propuestasPendientes` de `CategoriasScreen` apenas se resuelve o se descarta), así
 * que esta hoja nunca decide cuál mostrar, solo cómo mostrarla y qué hacer con el botón.
 *
 * Para [PropuestaDeOrden.UnificarParecidas] se muestra el mismo aviso previo que
 * [HojaUnificar] ([avisoDeUnificacion]) — fix round 1, hallazgo 2: sin él, un par con presupuesto
 * de los dos lados se unificaba sin avisar que los límites se suman y que eso no se puede deshacer,
 * la única advertencia que sí ve quien unifica a mano desde el detalle de una categoría.
 *
 * Para [PropuestaDeOrden.UnUso], «Unificar con…» ya **no** unifica desde acá (fix round 1,
 * hallazgo 3): `onUnificarConOtra` le pasa la categoría a `CategoriasScreen`, que abre la MISMA
 * [Hoja.Unificar] que usa el detalle — con su búsqueda, su aviso y su botón de confirmar — en vez
 * de comprometer la historia con el primer toque en una cuadrícula.
 */
@Composable
private fun HojaDeOrden(
    propuesta: PropuestaDeOrden,
    restantes: Int,
    guardando: Boolean,
    error: String?,
    onUnificar: (origen: String, destino: String) -> Unit,
    onEsconder: (CategoryUsage) -> Unit,
    onUnificarConOtra: (CategoryUsage) -> Unit,
    onAhoraNo: () -> Unit,
    onDismiss: () -> Unit,
) {
    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(
                "Ordena tus categorías",
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = Movi.espacios.minimo),
            )
            Text(
                if (restantes == 1) "Queda 1 por revisar." else "Quedan $restantes por revisar.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.padding(top = Movi.espacios.minimo, bottom = Movi.espacios.amplio),
            )

            Text(
                explicacionDePropuesta(propuesta),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            if (propuesta is PropuestaDeOrden.UnificarParecidas) {
                Text(
                    avisoDeUnificacion(propuesta.origen, propuesta.destino),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(top = Movi.espacios.corto),
                )
            }
            MensajeDeErrorDeHoja(error)
            when (propuesta) {
                is PropuestaDeOrden.UnificarParecidas -> BotonDeHoja(
                    texto = "Unificar en «${propuesta.destino.name}»",
                    habilitado = !guardando,
                    onClick = { onUnificar(propuesta.origen.name, propuesta.destino.name) },
                )
                is PropuestaDeOrden.EsconderNuncaUsada -> BotonDeHoja(
                    texto = "Esconder",
                    habilitado = !guardando,
                    onClick = { onEsconder(propuesta.categoria) },
                )
                is PropuestaDeOrden.UnUso -> BotonDeHoja(
                    texto = "Unificar con…",
                    habilitado = !guardando,
                    onClick = { onUnificarConOtra(propuesta.categoria) },
                )
            }
            Text(
                "Ahora no",
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.textoMedio,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Movi.espacios.margen)
                    .clickable(enabled = !guardando, onClick = onAhoraNo),
            )
        }
    }
}

// ── Piezas de la lista ────────────────────────────────────────────────────────

@Composable
private fun Pastilla(texto: String, activa: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = if (activa) Movi.colores.fondo else Movi.colores.textoMedio,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (activa) Movi.colores.marca else Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun CampoDeBusqueda(valor: String, onValorCambia: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
        val campo = rememberCampoConSeleccion(valor, onValorCambia)
        BasicTextField(
            value = campo.valor,
            onValueChange = campo::alCambiar,
            singleLine = true,
            cursorBrush = SolidColor(Movi.colores.texto),
            textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            decorationBox = { inner ->
                if (valor.isEmpty()) Text("Buscar categoría", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                inner()
            },
        )
    }
}

/** El `testTag` de [FilaDeCategoria], para medir su alto real en una prueba (fix round 1). */
fun tagDeFilaDeCategoria(nombre: String): String = "categoria:fila:$nombre"

/**
 * Ola B · tarea 5: la fila compacta. Reemplaza a la de dos etiquetas y una oración — «Tuya» y el
 * tipo («Ambos») salieron de acá: el tipo se dice solo cuando ayuda, y eso pasó a la hoja de
 * detalle (ver [etiquetaDeTipo]). Lo que queda es lo mínimo para reconocer una categoría y decidir
 * si vale la pena abrirla: el ícono, el nombre, la cifra de este mes si tiene, y cuánto la usó en
 * total, sin adornos.
 *
 * **Alto MÍNIMO** ([ALTO_DE_FILA_DE_CATEGORIA], vía `heightIn(min = …)`, no `height(…)`) — fix
 * round 1. `App.kt` multiplica la escala de letra ambiente por 1,12 en TODA la app (el «tamaño de
 * Movi» no es el tamaño del sistema tal cual), así que un `.height()` fijo medido a escala 1 se
 * queda corto: `titulo` (línea de 20 sp) + 2 dp + `apoyo` (línea de 16 sp) necesitan ≈42,3 dp a
 * escala 1,12, y con el relleno vertical de antes (10 dp arriba y abajo → 40 dp libres) el texto
 * se recortaba. El relleno bajó a 8 dp (44 dp libres, con margen) y el alto pasó a ser un PISO: a
 * la escala por defecto de Movi la fila mide exactamente [ALTO_DE_FILA_DE_CATEGORIA] (el contenido
 * entra con margen de sobra y el mínimo gana), y con una escala de letra más grande todavía la fila
 * crece en vez de recortar — la tarea 9 arma un esqueleto que tiene que medir lo mismo que esta
 * fila **a la escala por defecto**, no un alto que esta fila pueda superar.
 */
@Composable
private fun FilaDeCategoria(categoria: CategoryUsage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .testTag(tagDeFilaDeCategoria(categoria.name))
            .fillMaxWidth()
            .heightIn(min = ALTO_DE_FILA_DE_CATEGORIA)
            .clip(RoundedCornerShape(14.dp))
            .background(Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconoDeCategoria(categoria.name)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = categoria.name,
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = if (categoria.hidden) Movi.colores.textoMedio else Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                cifraDelMes(categoria)?.let {
                    Text(
                        text = it,
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = resumenDeUsoCorto(categoria),
                color = Movi.colores.textoMedio,
                style = Movi.textos.apoyo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * El alto MÍNIMO de [FilaDeCategoria], en el medio del rango que pide la tarea (56-64 dp). A la
 * escala de letra por defecto de Movi es también el alto exacto — ver el KDoc de
 * [FilaDeCategoria], fix round 1. `internal` y no `private`: la tarea 9 arma un esqueleto que
 * tiene que medir esto, y que lo importe de acá es menos frágil que duplicar el número.
 */
internal val ALTO_DE_FILA_DE_CATEGORIA = 60.dp

/** Cuántas filas pinta [categoriasEsqueleto] mientras la lista no llegó ni una vez y no hay forma recordada. */
private const val FILAS_DE_CATEGORIA_ESQUELETO = 6

/** Tope de filas esqueleto: la lista es perezosa, pero un número absurdo no tiene por qué llegar acá. */
private const val MAX_FILAS_DE_CATEGORIA_ESQUELETO = 60

/**
 * **Categorías mientras carga, con la forma de la fila compacta** (Ola B, tarea 9). Antes de esta
 * tarea la pantalla quedaba en blanco entre los filtros/la búsqueda y la primera fila real — ni
 * una rueda, nada. Esto imita [FilaDeCategoria]: mismo círculo de 36 dp
 * ([TamanoDeIconoDeCategoria.Normal]), mismo alto mínimo ([ALTO_DE_FILA_DE_CATEGORIA]), mismo
 * `Movi.textos.titulo` para el nombre y `Movi.textos.apoyo` para el resumen de uso — sin la cifra
 * del mes, porque todavía no se sabe si esta categoría tuvo gasto este mes.
 *
 * [filas] es la cantidad de la última carga que salió bien en este aparato (ver `FormaRecordada`),
 * o [FILAS_DE_CATEGORIA_ESQUELETO] la primera vez.
 */
private fun LazyListScope.categoriasEsqueleto(filas: Int) {
    items(filas.coerceIn(0, MAX_FILAS_DE_CATEGORIA_ESQUELETO)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_DE_FILA_DE_CATEGORIA)
                .clip(RoundedCornerShape(14.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(TAG_FILA_DE_LISTA_ESQUELETO),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CirculoEsqueleto(TamanoDeIconoDeCategoria.Normal.circulo)
            Column(modifier = Modifier.weight(1f)) {
                LineaEsqueleto(
                    fraccionDelAncho = 0.5f,
                    estilo = Movi.textos.titulo,
                    modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                )
                Spacer(Modifier.height(2.dp))
                LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.apoyo)
            }
        }
    }
}

// ── Las hojas ─────────────────────────────────────────────────────────────────

@Composable
private fun HojaBase(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss)
            content()
        }
    }
}

@Composable
private fun HojaDetalle(
    categoria: CategoryUsage,
    onDismiss: () -> Unit,
    onRenombrar: () -> Unit,
    onUnificar: () -> Unit,
    onCambiarVisibilidad: (Boolean) -> Unit,
    onFijarTipo: (String?) -> Unit,
    /**
     * Ola B · tarea 5. Ícono y color se eligen cada uno por su lado: `icono` no nulo cambia solo
     * el ícono, `color` no nulo cambia solo el color, y «Volver al de Movi» manda los dos en
     * blanco (`""`) — ver el KDoc de `CategoryPrefsRequest`, que es la semántica que respeta este
     * request de principio a fin.
     */
    onCambiarApariencia: (icono: String?, color: String?) -> Unit,
) {
    // Las reservadas ya no llegan acá: `filtrarCategorias` las saca de la lista antes de que se
    // pueda tocar una fila (ver el pie de la pantalla, que explica por qué son menos de las que
    // tiene el server). Sin una reservada que dibujar, esta hoja no necesita su propia rama.
    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(
                categoria.name,
                style = Movi.textos.titular,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                resumenDeUso(categoria),
                color = Movi.colores.textoMedio,
                style = Movi.textos.apoyo,
                modifier = Modifier.padding(top = 6.dp),
            )
            resumenDelMes(categoria)?.let {
                Text(
                    it,
                    color = Movi.colores.textoMedio,
                    style = Movi.textos.apoyo,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // El tipo se dice acá y no en la fila (Ola B · tarea 5) — y solo cuando ayuda: «Sin
            // usar» no aporta nada que el dueño pueda hacer con eso.
            etiquetaDeTipo(categoria).takeIf { it != "Sin usar" }?.let {
                Text(
                    it,
                    color = Movi.colores.textoMedio,
                    style = Movi.textos.apoyo,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            SeccionApariencia(
                categoria = categoria,
                onElegirIcono = { onCambiarApariencia(it, null) },
                onElegirColor = { onCambiarApariencia(null, it) },
                onVolverAlDeMovi = { onCambiarApariencia("", "") },
            )

            // ── Tipo ──────────────────────────────────────────────────────────
            Text(
                "TIPO",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                val opciones = listOf(
                    null,
                    TransactionType.EXPENSE.name,
                    TransactionType.INCOME.name,
                    CATEGORY_TYPE_BOTH,
                )
                opciones.forEach { opcion ->
                    OpcionDeTipo(
                        texto = etiquetaDeTipoFijado(opcion),
                        activa = categoria.pinnedType == opcion,
                        modifier = Modifier.weight(1f),
                        onClick = { if (categoria.pinnedType != opcion) onFijarTipo(opcion) },
                    )
                }
            }
            Text(
                if (categoria.pinnedType == null)
                    "«Automático» usa lo que dice el catálogo de Movi o, si es tuya, los tipos con " +
                        "los que ya la usaste. Fija uno para decidirlo tú."
                else "Fijado por ti: manda sobre el catálogo y sobre el uso.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)) {
                Hairline()
                if (categoria.scope == CategoryScope.CUSTOM) {
                    AccionDeHoja(
                        titulo = "Renombrar",
                        detalle = "Cambia el nombre en todos tus movimientos, tu presupuesto y tus recurrentes.",
                        onClick = onRenombrar,
                    )
                    Hairline()
                } else {
                    Text(
                        "Las categorías del catálogo de Movi no se renombran: el catálogo es el " +
                            "mismo para todos y volvería a sugerirte el nombre viejo. Si quieres " +
                            "juntarla con otra, únela; si no la usas, escóndela.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Hairline()
                }
                AccionDeHoja(
                    titulo = "Unificar en otra",
                    detalle = "Todo lo que dice «${categoria.name}» pasa a decir la que elijas. No se borra nada.",
                    onClick = onUnificar,
                )
                Hairline()
                AccionDeHoja(
                    titulo = if (categoria.hidden) "Volver a sugerirla" else "Esconder",
                    detalle = if (categoria.hidden)
                        "Vuelve a aparecer al escribir una categoría."
                    else "Deja de sugerirse al escribir. Tus movimientos viejos no se tocan.",
                    onClick = { onCambiarVisibilidad(!categoria.hidden) },
                )
                Hairline()
            }
        }
    }
}

/**
 * Ola B · tarea 5: **Ícono y Color, editables ahí mismo** en la hoja de detalle — hasta acá una
 * categoría se veía siempre con lo que [aparienciaDe] adivinaba por su nombre, sin forma de
 * corregirlo si adivinaba mal.
 *
 * La vista previa arriba usa la apariencia YA RESUELTA ([apariencia]): lo que el dueño eligió
 * ([CategoryUsage.icono]/[CategoryUsage.color]) si eligió algo, o si no, lo mismo que ya pinta esa
 * categoría en toda la app. Así la cuadrícula marca de entrada el que está activo — nunca arranca
 * en blanco — y «Volver al de Movi» solo se ofrece si de verdad hay algo elegido que deshacer.
 */
@Composable
private fun SeccionApariencia(
    categoria: CategoryUsage,
    onElegirIcono: (String) -> Unit,
    onElegirColor: (String) -> Unit,
    onVolverAlDeMovi: () -> Unit,
) {
    val apariencia = remember(categoria.name, categoria.icono, categoria.color) {
        aparienciaDe(categoria.name, CategoryPref(icono = categoria.icono, color = categoria.color))
    }
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoDeCategoria(apariencia = apariencia)
            Text(
                "${rotuloDeIcono(apariencia.icono)} · ${rotuloDeColor(apariencia.color)}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }

        Text(
            "ÍCONO",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        CuadriculaDeApariencia(ICONOS_DEL_CATALOGO) { item, modifier ->
            CeldaDeIcono(item = item, elegido = item.clave == apariencia.icono, onClick = { onElegirIcono(item.clave) }, modifier = modifier)
        }

        Text(
            "COLOR",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        // Fila que se desplaza y no la misma cuadrícula que el ícono: un círculo sin rótulo no
        // necesita el ancho de celda de 80 dp que pide un ícono con dos renglones de texto debajo
        // — con ese ancho los diez círculos quedaban con huecos enormes entre uno y otro.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
        ) {
            COLORES_DEL_CATALOGO.forEach { item ->
                CeldaDeColor(item = item, elegido = item.clave == apariencia.color, onClick = { onElegirColor(item.clave) })
            }
        }

        // Solo si el dueño de verdad eligió algo: sin esto, el link aparecería siempre y
        // «volver» a lo que ya se está mostrando no tiene sentido.
        if (categoria.icono != null || categoria.color != null) {
            Text(
                "Volver al de Movi",
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.marca,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(onClick = onVolverAlDeMovi),
            )
        }
    }
}

/**
 * La cuadrícula que comparten el selector de ícono y el de color: misma cuenta de columnas que
 * [com.jvillada.movi.ui.components.SelectorDeCategoria] ([columnasDeLaCuadricula]), para que las
 * celdas midan lo mismo en toda la app y no haya dos criterios de «cuántas entran por fila».
 */
@Composable
private fun <T> CuadriculaDeApariencia(items: List<T>, celda: @Composable (T, Modifier) -> Unit) {
    val espacio = Movi.espacios.minimo
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnas = columnasDeLaCuadricula(maxWidth, espacio)
        Column(verticalArrangement = Arrangement.spacedBy(espacio)) {
            items.chunked(columnas).forEach { fila ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(espacio)) {
                    fila.forEach { item -> celda(item, Modifier.weight(1f)) }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Testtag de una celda de [ICONOS_DEL_CATALOGO] en la hoja de detalle, para encontrarla en una prueba. */
fun tagDeIconoDelCatalogo(clave: String): String = "categoria:icono:$clave"

/** Testtag de una celda de [COLORES_DEL_CATALOGO] en la hoja de detalle. Ver [tagDeIconoDelCatalogo]. */
fun tagDeColorDelCatalogo(clave: String): String = "categoria:color:$clave"

@Composable
private fun CeldaDeIcono(item: IconoDelCatalogo, elegido: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(Movi.formas.normal)
    Column(
        modifier = modifier
            .testTag(tagDeIconoDelCatalogo(item.clave))
            .clip(forma)
            .then(if (elegido) Modifier.border(2.dp, Movi.colores.marca, forma) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = Movi.espacios.corto, horizontal = Movi.espacios.minimo),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(TamanoDeIconoDeCategoria.Normal.circulo)
                .background(Movi.colores.fondo, RoundedCornerShape(Movi.formas.pleno)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.imagen,
                contentDescription = null,
                tint = if (elegido) Movi.colores.marca else Movi.colores.textoMedio,
                modifier = Modifier.size(TamanoDeIconoDeCategoria.Normal.icono),
            )
        }
        Text(
            item.rotulo,
            style = Movi.textos.apoyo,
            fontWeight = if (elegido) FontWeight.Medium else FontWeight.Normal,
            color = if (elegido) Movi.colores.marca else Movi.colores.texto,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Movi.espacios.minimo),
        )
    }
}

/**
 * Un color, como un círculo lleno — el color de verdad, sin el alfa bajo de
 * [com.jvillada.movi.theme.ColoresDeMovi.circuloDeCategoria] que usa el ícono: acá el color ES el
 * dato, y aclararlo lo haría casi imposible de distinguir del vecino.
 *
 * El elegido se marca con un **anillo alrededor**, no con un check encima: un check necesita un
 * tinte que contraste contra el color de fondo, y con diez colores de fondo distintos no hay un
 * tinte único que sirva para los diez a la vez sin agregar tokens nuevos.
 *
 * **Fix round 1: `contentDescription` y estado de selección.** Un círculo de color no dice nada
 * por sí solo a un lector de pantalla — acá lleva el rótulo en español del catálogo
 * ([ColorDelCatalogo.rotulo], p. ej. «Naranja») y, con [selectable] en vez de `clickable`, el
 * estado «seleccionado» que antes solo se veía (el anillo).
 */
@Composable
private fun CeldaDeColor(item: ColorDelCatalogo, elegido: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(Movi.formas.pleno)
    Box(
        modifier = Modifier
            .testTag(tagDeColorDelCatalogo(item.clave))
            .clip(forma)
            .then(if (elegido) Modifier.border(2.dp, Movi.colores.texto, forma) else Modifier)
            .selectable(selected = elegido, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = item.rotulo }
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TamanoDeIconoDeCategoria.Normal.circulo)
                .clip(forma)
                .background(Movi.colores.categoria(item.clave)),
        )
    }
}

@Composable
private fun OpcionDeTipo(texto: String, activa: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = texto,
        // 11.sp y casi sin padding lateral: son cuatro opciones repartidas a partes iguales, y a
        // 390 dp de ancho «Automático» se cortaba en «Automáti…» — un rótulo cortado en el
        // control que decide el tipo de la categoría es justo donde no se puede adivinar.
        style = Movi.textos.apoyo,
        letterSpacing = (-0.1).sp,
        fontWeight = FontWeight.Medium,
        color = if (activa) Movi.colores.fondo else Movi.colores.textoMedio,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (activa) Movi.colores.marca else Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
    )
}

@Composable
private fun AccionDeHoja(titulo: String, detalle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Text(titulo, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
        Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * Renombrar. **Si el nombre escrito ya existe, la hoja no falla: ofrece unificar.**
 *
 * Sin esto, el arreglo más común —«Trasnporte» hacia «Transporte», que está en el catálogo—
 * chocaría contra un 409 que le diría al dueño «ya existe, usa Unificar»: un error en el que el
 * único camino es cerrar, volver a entrar y repetir todo por otra puerta. La colisión no es un
 * error del dueño, es información: significa que lo que quiere hacer se llama unificar.
 */
@Composable
private fun HojaRenombrar(
    categoria: CategoryUsage,
    existentes: List<CategoryUsage>,
    error: String?,
    guardando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (String, Boolean) -> Unit,
) {
    var nombre by remember { mutableStateOf(categoria.name) }
    val limpio = nombre.trim()
    val colision = colisionAlRenombrar(categoria, limpio, existentes)
    val sinCambio = limpio == categoria.name
    val puedeGuardar = limpio.isNotEmpty() && !sinCambio && !guardando && colision?.reserved != true

    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(
                "Renombrar «${categoria.name}»",
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Movi.colores.tarjeta)
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                // [esAtajoDeSeleccionarTodo]. Acá pesa más que en otros campos: el nombre llega
                // prellenado con el actual, y renombrar es justamente reemplazarlo entero.
                val campo = rememberCampoConSeleccion(nombre) { nombre = it }
                BasicTextField(
                    value = campo.valor,
                    onValueChange = campo::alCambiar,
                    singleLine = true,
                    enabled = !guardando,
                    cursorBrush = SolidColor(Movi.colores.texto),
                    textStyle = Movi.textos.titulo.copy(color = Movi.colores.texto),
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    decorationBox = { inner ->
                        if (nombre.isEmpty()) Text("Nombre nuevo", style = Movi.textos.titulo, color = Movi.colores.textoApagado)
                        inner()
                    },
                )
            }

            Text(
                text = when {
                    colision?.reserved == true ->
                        "«${colision.name}» es una categoría reservada de Movi: no puedes usar ese nombre."
                    colision != null -> avisoDeUnificacion(categoria, colision)
                    else -> "El cambio se aplica a tus movimientos, a tu presupuesto y a tus " +
                        "recurrentes al mismo tiempo. No se borra nada."
                },
                style = Movi.textos.apoyo,
                color = if (colision?.reserved == true) Movi.colores.sale else Movi.colores.textoMedio,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            MensajeDeErrorDeHoja(error)
            BotonDeHoja(
                texto = when {
                    colision != null && colision.reserved != true -> "Unificar en «${colision.name}»"
                    else -> "Renombrar"
                },
                habilitado = puedeGuardar,
                onClick = { onConfirmar(colision?.name ?: limpio, colision != null) },
            )
        }
    }
}

/**
 * El `testTag` de una fila candidata en [HojaUnificar], para encontrarla en una prueba sin
 * ambigüedad con la fila de la misma categoría en la lista principal (que usa
 * [tagDeFilaDeCategoria] y puede seguir montada, tapada, detrás de esta hoja).
 */
fun tagDeCandidataDeUnificar(nombre: String): String = "categoria:unificar-candidata:$nombre"

/** Unificar: se elige el destino de una lista, no se escribe — juntar con algo que no existe es renombrar. */
@Composable
private fun HojaUnificar(
    categoria: CategoryUsage,
    existentes: List<CategoryUsage>,
    error: String?,
    guardando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit,
    /** Ver el KDoc de [Hoja.Unificar]. */
    soloVisibles: Boolean = false,
) {
    var busqueda by remember { mutableStateOf("") }
    // La categoría destino ENTERA, no su nombre: el aviso previo necesita saber si ella también
    // tiene presupuesto para poder avisar de la suma antes de aplicarla (ver [avisoDeUnificacion]).
    var elegida by remember { mutableStateOf<CategoryUsage?>(null) }

    val candidatas = remember(existentes, busqueda, categoria, soloVisibles) {
        filtrarCategorias(existentes, CategoryFilter.TODAS, busqueda)
            .filter { !it.reserved && it.name != categoria.name && (!soloVisibles || !it.hidden) }
    }

    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                "Unificar «${categoria.name}» en…",
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            CampoDeBusqueda(valor = busqueda, onValorCambia = { busqueda = it })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                candidatas.forEach { c ->
                    Row(
                        modifier = Modifier
                            .testTag(tagDeCandidataDeUnificar(c.name))
                            .fillMaxWidth()
                            .clickable(enabled = !guardando) { elegida = c }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                            Text(resumenDeUso(c), color = Movi.colores.textoApagado, style = Movi.textos.apoyo)
                        }
                        if (elegida?.name == c.name) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Movi.colores.hilo))
                }
                if (candidatas.isEmpty()) {
                    Text(
                        "No hay otra categoría con ese nombre.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            Text(
                text = elegida?.let { avisoDeUnificacion(categoria, it) }
                    ?: "Elige la categoría que se queda. Los movimientos de «${categoria.name}» " +
                    "pasan a decir ese nombre; no se borra ninguno.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            MensajeDeErrorDeHoja(error)
            BotonDeHoja(
                texto = elegida?.let { "Unificar en «${it.name}»" } ?: "Unificar",
                habilitado = elegida != null && !guardando,
                onClick = { elegida?.let { onConfirmar(it.name) } },
            )
        }
    }
}

/** El motivo de un fallo, dentro de la hoja y arriba del botón: la hoja no se cierra al fallar. */
@Composable
private fun MensajeDeErrorDeHoja(error: String?) {
    if (error == null) return
    Text(
        error,
        style = Movi.textos.apoyo,
        color = Movi.colores.sale,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun BotonDeHoja(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        style = Movi.textos.titulo,
        fontWeight = FontWeight.Medium,
        color = if (habilitado) Movi.colores.fondo else Movi.colores.textoApagado,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 18.dp, bottom = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (habilitado) Movi.colores.marca else Movi.colores.tarjeta)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 16.dp),
    )
}
