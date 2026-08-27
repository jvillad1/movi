package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinExpense
import com.jvillada.movi.theme.MinHairline
import com.jvillada.movi.theme.MinIncome
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinSurfaceContainer
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinSurfaceContainerHighest
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextDim
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.theme.MinWarn
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.leadingFor
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
 *
 * Renombrar y unificar **reescriben tres tablas** (`financial_events`, `budgets`,
 * `recurring_rules`) en una sola transacción del server; ver `CategoryRoutes.rewriteCategory`.
 */

/** En qué está la hoja abierta, si hay alguna. */
private sealed class Hoja {
    data class Detalle(val categoria: CategoryUsage) : Hoja()
    data class Renombrar(val categoria: CategoryUsage) : Hoja()
    data class Unificar(val categoria: CategoryUsage) : Hoja()
}

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

    suspend fun recargar() {
        runCatching { Repositories.wallets.getCategories() }
            .onSuccess { categorias = it; error = null }
            .onFailure { error = it.toUserMessage() }
    }

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshTick) {
        loading = true
        recargar()
        loading = false
    }

    val escondidas = categorias.count { it.hidden }
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

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(
                title = "Categorías",
                leading = leadingFor(
                    Screen.Categorias,
                    onProfile = { onNavigate(Screen.Profile) },
                    fallback = Screen.Mas,
                ),
                subtitle = when {
                    loading -> null
                    escondidas == 1 -> "${categorias.size} categorías · 1 escondida"
                    escondidas > 1 -> "${categorias.size} categorías · $escondidas escondidas"
                    else -> "${categorias.size} categorías"
                },
            )

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
                    fontSize = 12.5.sp,
                    color = MinExpense,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            if (confirmacion != null) {
                Text(
                    confirmacion!!,
                    fontSize = 12.5.sp,
                    color = MinIncome,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (visibles.isEmpty() && !loading) {
                    item {
                        Text(
                            if (busqueda.isNotBlank()) "Ninguna categoría se llama así."
                            else "Nada por aquí todavía.",
                            fontSize = 13.sp,
                            color = MinTextMute,
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
                        runCatching {
                            Repositories.wallets.setCategoryPrefs(
                                h.categoria.name, escondida, h.categoria.pinnedType,
                            )
                        }.onSuccess {
                            UsedCategoriesCache.applyPref(
                                it.name, CategoryPref(it.hidden, it.pinnedType),
                            )
                            confirmacion = if (escondida)
                                "«${h.categoria.name}» ya no se te va a sugerir. Sus movimientos siguen ahí."
                            else "«${h.categoria.name}» vuelve a sugerirse."
                            error = null
                            hoja = null
                            recargar()
                        }.onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                    }
                },
                onFijarTipo = { tipo ->
                    scope.launch {
                        runCatching {
                            Repositories.wallets.setCategoryPrefs(h.categoria.name, h.categoria.hidden, tipo)
                        }.onSuccess {
                            UsedCategoriesCache.applyPref(it.name, CategoryPref(it.hidden, it.pinnedType))
                            error = null
                            recargar()
                            // La hoja se queda abierta con el dato fresco: fijar el tipo es un
                            // ajuste, no un trámite, y cerrarla obligaría a volver a entrar para
                            // ver el resultado.
                            hoja = Hoja.Detalle(
                                categorias.firstOrNull { c -> c.name == h.categoria.name } ?: h.categoria,
                            )
                        }.onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                    }
                },
            )

            is Hoja.Renombrar -> HojaRenombrar(
                categoria = h.categoria,
                existentes = categorias,
                onDismiss = { hoja = null },
                onConfirmar = { nuevoNombre, esUnificacion ->
                    scope.launch {
                        runCatching {
                            if (esUnificacion) Repositories.wallets.mergeCategory(h.categoria.name, nuevoNombre)
                            else Repositories.wallets.renameCategory(h.categoria.name, nuevoNombre)
                        }.onSuccess { r ->
                            UsedCategoriesCache.applyRename(h.categoria.name, r.name)
                            confirmacion = textoDeResultado(r.movements, r.budgets, r.recurringRules, r.budgetsMerged, r.name)
                            error = null
                            hoja = null
                            recargar()
                        }.onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                    }
                },
            )

            is Hoja.Unificar -> HojaUnificar(
                categoria = h.categoria,
                existentes = categorias,
                onDismiss = { hoja = null },
                onConfirmar = { destino ->
                    scope.launch {
                        runCatching { Repositories.wallets.mergeCategory(h.categoria.name, destino) }
                            .onSuccess { r ->
                                UsedCategoriesCache.applyRename(h.categoria.name, r.name)
                                confirmacion = textoDeResultado(r.movements, r.budgets, r.recurringRules, r.budgetsMerged, r.name)
                                error = null
                                hoja = null
                                recargar()
                            }
                            .onFailure { error = it.toUserMessage(); confirmacion = null; hoja = null }
                    }
                },
            )
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

// ── Piezas de la lista ────────────────────────────────────────────────────────

@Composable
private fun Pastilla(texto: String, activa: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = if (activa) MinBg else MinTextDim,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (activa) MinPrimary else MinSurfaceContainer)
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
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = valor,
            onValueChange = onValorCambia,
            singleLine = true,
            cursorBrush = SolidColor(MinText),
            textStyle = TextStyle(color = MinText, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (valor.isEmpty()) Text("Buscar categoría", fontSize = 14.sp, color = MinTextFaint)
                inner()
            },
        )
    }
}

@Composable
private fun FilaDeCategoria(categoria: CategoryUsage, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinSurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = categoria.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (categoria.hidden) MinTextMute else MinText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (categoria.reserved) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = "Reservada de Movi",
                    tint = MinTextFaint,
                    modifier = Modifier.size(13.dp),
                )
            }
            Box(modifier = Modifier.weight(1f))
            Etiqueta(etiquetaDeTipo(categoria), tinteDeTipo(categoria))
        }
        Row(
            modifier = Modifier.padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resumenDeUso(categoria),
                fontSize = 11.5.sp,
                color = MinTextMute,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        val etiquetasExtra = buildList {
            if (categoria.hidden) add("Escondida" to MinWarn)
            if (categoria.pinnedType != null) add("Tipo fijado" to MinPrimary)
            if (categoria.scope == CategoryScope.CUSTOM && !categoria.reserved) add("Tuya" to MinTextMute)
        }
        if (etiquetasExtra.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                etiquetasExtra.forEach { (texto, color) -> Etiqueta(texto, color) }
            }
        }
    }
}

private fun tinteDeTipo(categoria: CategoryUsage): Color = when (etiquetaDeTipo(categoria)) {
    "Gasto" -> MinExpense
    "Ingreso" -> MinIncome
    "Ambos" -> MinPrimary
    else -> MinTextFaint
}

@Composable
private fun Etiqueta(texto: String, color: Color) {
    Text(
        text = texto,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
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
                .background(MinSurfaceContainerHigh)
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
) {
    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(
                categoria.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                resumenDeUso(categoria),
                fontSize = 12.5.sp,
                color = MinTextMute,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
            resumenDelMes(categoria)?.let {
                Text(
                    it,
                    fontSize = 12.5.sp,
                    color = MinTextMute,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            if (categoria.reserved) {
                // Sin acciones y con el motivo: `isCashFlow` reconoce estas categorías por su
                // nombre exacto, así que tocarlas rompería las cifras de todos los meses.
                Column(modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)) {
                    Text(
                        "Categoría reservada de Movi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinWarn,
                    )
                    Text(
                        "La escribe la app sola para traspasos, saldos iniciales y pagos de tarjeta, " +
                            "y de su nombre exacto dependen las cifras de tu mes. No se puede renombrar, " +
                            "unificar ni esconder.",
                        fontSize = 12.5.sp,
                        color = MinTextMute,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                return@Column
            }

            // ── Tipo ──────────────────────────────────────────────────────────
            Text(
                "TIPO",
                fontSize = 11.sp,
                color = MinTextMute,
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
                fontSize = 11.5.sp,
                color = MinTextMute,
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
                        fontSize = 11.5.sp,
                        color = MinTextMute,
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

@Composable
private fun OpcionDeTipo(texto: String, activa: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = texto,
        // 11.sp y casi sin padding lateral: son cuatro opciones repartidas a partes iguales, y a
        // 390 dp de ancho «Automático» se cortaba en «Automáti…» — un rótulo cortado en el
        // control que decide el tipo de la categoría es justo donde no se puede adivinar.
        fontSize = 11.sp,
        letterSpacing = (-0.1).sp,
        fontWeight = FontWeight.Medium,
        color = if (activa) MinBg else MinTextDim,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (activa) MinPrimary else MinSurfaceContainerHighest)
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
        Text(titulo, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
        Text(detalle, fontSize = 11.5.sp, color = MinTextMute, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
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
    onDismiss: () -> Unit,
    onConfirmar: (String, Boolean) -> Unit,
) {
    var nombre by remember { mutableStateOf(categoria.name) }
    var guardando by remember { mutableStateOf(false) }
    val limpio = nombre.trim()
    val colision = existentes.firstOrNull {
        it.name != categoria.name && it.name.equals(limpio, ignoreCase = true)
    }
    val sinCambio = limpio == categoria.name
    val puedeGuardar = limpio.isNotEmpty() && !sinCambio && !guardando && colision?.reserved != true

    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(
                "Renombrar «${categoria.name}»",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    singleLine = true,
                    enabled = !guardando,
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (nombre.isEmpty()) Text("Nombre nuevo", fontSize = 15.sp, color = MinTextFaint)
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
                fontSize = 11.5.sp,
                color = if (colision?.reserved == true) MinExpense else MinTextMute,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            BotonDeHoja(
                texto = when {
                    colision != null && colision.reserved != true -> "Unificar en «${colision.name}»"
                    else -> "Renombrar"
                },
                habilitado = puedeGuardar,
                onClick = {
                    guardando = true
                    onConfirmar(colision?.name ?: limpio, colision != null)
                },
            )
        }
    }
}

/** Unificar: se elige el destino de una lista, no se escribe — juntar con algo que no existe es renombrar. */
@Composable
private fun HojaUnificar(
    categoria: CategoryUsage,
    existentes: List<CategoryUsage>,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit,
) {
    var busqueda by remember { mutableStateOf("") }
    // La categoría destino ENTERA, no su nombre: el aviso previo necesita saber si ella también
    // tiene presupuesto para poder avisar de la suma antes de aplicarla (ver [avisoDeUnificacion]).
    var elegida by remember { mutableStateOf<CategoryUsage?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val candidatas = remember(existentes, busqueda, categoria) {
        filtrarCategorias(existentes, CategoryFilter.TODAS, busqueda)
            .filter { !it.reserved && it.name != categoria.name }
    }

    HojaBase(onDismiss = onDismiss) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                "Unificar «${categoria.name}» en…",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
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
                            .fillMaxWidth()
                            .clickable(enabled = !guardando) { elegida = c }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, fontSize = 14.sp, color = MinText)
                            Text(resumenDeUso(c), fontSize = 11.sp, color = MinTextFaint, fontFamily = FontFamily.Monospace)
                        }
                        if (elegida?.name == c.name) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MinHairline))
                }
                if (candidatas.isEmpty()) {
                    Text(
                        "No hay otra categoría con ese nombre.",
                        fontSize = 12.5.sp,
                        color = MinTextMute,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            Text(
                text = elegida?.let { avisoDeUnificacion(categoria, it) }
                    ?: "Elige la categoría que se queda. Los movimientos de «${categoria.name}» " +
                    "pasan a decir ese nombre; no se borra ninguno.",
                fontSize = 11.5.sp,
                color = MinTextMute,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            BotonDeHoja(
                texto = elegida?.let { "Unificar en «${it.name}»" } ?: "Unificar",
                habilitado = elegida != null && !guardando,
                onClick = {
                    guardando = true
                    onConfirmar(elegida!!.name)
                },
            )
        }
    }
}

@Composable
private fun BotonDeHoja(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium,
        color = if (habilitado) MinBg else MinTextFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 18.dp, bottom = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (habilitado) MinPrimary else MinSurfaceContainerHighest)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 16.dp),
    )
}
