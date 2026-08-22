package com.jvillada.movi.ui.sdui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.ScreenTaxonomy
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.toUserMessage
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SLUG = "dashboard"

private val SECTION_TYPE_LABELS = mapOf(
    "HERO_BALANCE" to "Balance neto",
    "UPCOMING_PAYMENTS" to "Próximos pagos",
    "ALERTS" to "Alertas",
    "QUICK_LINKS_WITH_TOTALS" to "Accesos con cifra",
    "CARD_ROW" to "Fila de tarjetas",
    "CARD_LIST" to "Lista de tarjetas",
    "LINK_LIST" to "Lista de enlaces",
    "BANNER" to "Aviso",
)

private val NAVIGATE_TARGET_LABELS = mapOf(
    "dashboard" to "Inicio",
    "transactions" to "Movimientos",
    "quickadd" to "Agregar",
    "budgets" to "Presupuestos",
    "mas" to "Más",
    "accounts" to "Cuentas",
    "credits" to "Créditos",
    "goals" to "Metas",
    // F61: sin pantalla propia — el cliente lo manda a Cuentas (ver SduiRenderer.screenForTarget).
    "investments" to "Cuentas (inversión)",
    "subscriptions" to "Suscripciones",
    "recurrentes" to "Recurrentes",
    "extractos" to "Extractos",
    "aichat" to "Movi AI",
    "profile" to "Perfil",
)

/**
 * Extrae el mensaje legible del server para un 422 (`{"error": "..."}`, ver
 * ScreenValidation.kt). Ktor client lanza [ResponseException] en no-2xx (no hay safeCall
 * en este repo, ver WalletRepositoryImpl.getScreen). Si la extracción falla por cualquier
 * razón, cae al mensaje genérico de [toUserMessage].
 */
private suspend fun Throwable.toScreenSaveMessage(): String {
    if (this is ResponseException && response.status == HttpStatusCode.UnprocessableEntity) {
        val extracted = runCatching {
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        if (!extracted.isNullOrBlank()) return extracted
    }
    return toUserMessage()
}

/**
 * Verifica si alguna tarjeta en las secciones tiene una acción OPEN_URL con un enlace
 * inválido (que no empieza con "https://").
 */
private fun sectionsHaveInvalidUrl(sections: List<ScreenSection>): Boolean {
    return sections.any { section ->
        section.cards.any { card ->
            val action = card.action
            action != null && action.type == "OPEN_URL" && !action.target.startsWith("https://")
        }
    }
}

@Composable
fun ScreenEditorScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()

    var sections by remember { mutableStateOf<List<ScreenSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var showAddTypePicker by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val def = Repositories.wallets.getScreen(SLUG)
            sections = def?.sections ?: emptyList()
        } catch (e: Throwable) {
            error = e.toScreenSaveMessage()
        }
        loading = false
    }

    // El mensaje "Guardado" se desvanece solo tras un momento.
    LaunchedEffect(saved) {
        if (saved) {
            delay(2500)
            saved = false
        }
    }

    fun updateSection(index: Int, updated: ScreenSection) {
        sections = sections.toMutableList().apply { this[index] = updated }
        saved = false
    }

    fun moveSection(index: Int, delta: Int) {
        val target = index + delta
        if (target !in sections.indices) return
        sections = sections.toMutableList().apply {
            val tmp = this[index]; this[index] = this[target]; this[target] = tmp
        }
        saved = false
    }

    fun removeSection(index: Int) {
        sections = sections.filterIndexed { i, _ -> i != index }
        saved = false
    }

    fun addSection(type: String) {
        sections = sections + ScreenSection(type = type)
        showAddTypePicker = false
        saved = false
    }

    fun save() {
        if (saving) return
        saving = true
        error = null
        coroutine.launch {
            try {
                val def = Repositories.wallets.putScreen(SLUG, sections)
                sections = def.sections
                saved = true
                // La versión que teníamos cacheada quedó obsoleta -- forzar refetch al volver.
                ScreenDefCache.dashboard = null
            } catch (e: Throwable) {
                error = e.toScreenSaveMessage()
            }
            saving = false
        }
    }

    fun restore() {
        if (saving) return
        saving = true
        error = null
        showRestoreConfirm = false
        coroutine.launch {
            try {
                val def = Repositories.wallets.restoreScreen(SLUG)
                sections = def.sections
                ScreenDefCache.dashboard = null
            } catch (e: Throwable) {
                error = e.toScreenSaveMessage()
            }
            saving = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
        // F60 · F22: encabezado único; el editor se abre desde Perfil (F47/F48) — Perfil como
        // destino de reserva si no hay historial.
        MinScreenHeader(
            title = "Editor de pantallas",
            leading = HeaderLeading.Back(fallback = Screen.Profile),
            action = { if (saved) Text("Guardado", fontSize = 12.sp, color = MinIncome, fontWeight = FontWeight.Medium) },
        )
        Spacer(Modifier.height(12.dp))

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MinPrimary,
                trackColor = MinSurfaceContainerHigh,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(sections) { index, section ->
                MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated) {
                    SectionHeader(
                        label = SECTION_TYPE_LABELS[section.type] ?: section.type,
                        canMoveUp = index > 0,
                        canMoveDown = index < sections.lastIndex,
                        onMoveUp = { moveSection(index, -1) },
                        onMoveDown = { moveSection(index, 1) },
                        onRemove = { removeSection(index) },
                    )
                    Spacer(Modifier.height(12.dp))
                    SectionBody(section = section, onUpdate = { updateSection(index, it) })
                }
            }

            item {
                if (showAddTypePicker) {
                    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Default) {
                        Text("Tipo de sección", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScreenTaxonomy.SECTION_TYPES.forEach { type ->
                                SelectRow(
                                    label = SECTION_TYPE_LABELS[type] ?: type,
                                    selected = false,
                                    onClick = { addSection(type) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cancelar",
                            fontSize = 13.sp, color = MinTextMute,
                            modifier = Modifier.clickable { showAddTypePicker = false }.padding(vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        "+ Agregar sección",
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                            .clickable { showAddTypePicker = true }
                            .padding(vertical = 14.dp),
                    )
                }
            }

            error?.let { msg ->
                item { Text(msg, fontSize = 12.sp, color = MinExpense) }
            }

            item {
                Spacer(Modifier.height(4.dp))
                val canSave = !saving && !sectionsHaveInvalidUrl(sections)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canSave) MinText else MinTextFaint)
                        .clickable(enabled = canSave) { save() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (saving) "Guardando…" else "Guardar", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            item {
                if (showRestoreConfirm) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "¿Restaurar al original? Sí",
                            fontSize = 13.sp, color = MinExpense, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(enabled = !saving) { restore() }.padding(8.dp),
                        )
                        Text(
                            "No",
                            fontSize = 13.sp, color = MinTextMute,
                            modifier = Modifier.clickable { showRestoreConfirm = false }.padding(8.dp),
                        )
                    }
                } else {
                    Text(
                        "Restaurar original",
                        fontSize = 13.sp, color = MinExpense,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { showRestoreConfirm = true }.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, modifier = Modifier.weight(1f))
        // Ola 2 #5 (F11): glifos escritos como texto → íconos Material (salían como ▯ en la web).
        Icon(
            Icons.Rounded.ArrowUpward, contentDescription = "Subir",
            tint = if (canMoveUp) MinTextDim else MinTextFaint,
            modifier = Modifier.size(15.dp).clickable(enabled = canMoveUp, onClick = onMoveUp).padding(6.dp),
        )
        Icon(
            Icons.Rounded.ArrowDownward, contentDescription = "Bajar",
            tint = if (canMoveDown) MinTextDim else MinTextFaint,
            modifier = Modifier.size(15.dp).clickable(enabled = canMoveDown, onClick = onMoveDown).padding(6.dp),
        )
        Icon(
            Icons.Rounded.Close, contentDescription = "Quitar",
            tint = MinExpense,
            modifier = Modifier.size(14.dp).clickable(onClick = onRemove).padding(6.dp),
        )
    }
}

@Composable
private fun SectionBody(section: ScreenSection, onUpdate: (ScreenSection) -> Unit) {
    when (section.type) {
        "BANNER" -> {
            FieldBox("Título (opcional)", section.title ?: "", { onUpdate(section.copy(title = it.ifBlank { null })) })
            Spacer(Modifier.height(8.dp))
            FieldBox("Texto", section.text ?: "", { onUpdate(section.copy(text = it)) })
            Spacer(Modifier.height(12.dp))
            val bannerAction = section.cards.firstOrNull()?.action
            ActionEditor(bannerAction) { newAction ->
                val newCards = if (newAction == null) emptyList() else listOf(ScreenCard(title = "", action = newAction))
                onUpdate(section.copy(cards = newCards))
            }
        }

        "CARD_ROW", "CARD_LIST", "LINK_LIST", "QUICK_LINKS_WITH_TOTALS" -> {
            if (section.type == "QUICK_LINKS_WITH_TOTALS") {
                FieldBox("Título (opcional)", section.title ?: "", { onUpdate(section.copy(title = it.ifBlank { null })) })
                Spacer(Modifier.height(8.dp))
                // La cifra de cada tarjeta la calcula el cliente según el destino de su acción
                // (Cuentas → activos, Créditos → deuda, Presupuestos → gastado del mes…); acá solo
                // se eligen y ordenan las tarjetas. Un subtítulo escrito acá reemplaza al calculado.
                Text("La cifra de cada acceso sale de su destino; no se edita.", fontSize = 12.sp, color = MinTextMute)
                Spacer(Modifier.height(10.dp))
            }
            section.cards.forEachIndexed { cardIndex, card ->
                if (cardIndex > 0) Spacer(Modifier.height(12.dp))
                CardEditor(
                    index = cardIndex,
                    card = card,
                    onUpdate = { updated ->
                        val newCards = section.cards.toMutableList().apply { this[cardIndex] = updated }
                        onUpdate(section.copy(cards = newCards))
                    },
                    onRemove = {
                        val newCards = section.cards.filterIndexed { i, _ -> i != cardIndex }
                        onUpdate(section.copy(cards = newCards))
                    },
                )
            }
            Spacer(Modifier.height(if (section.cards.isEmpty()) 0.dp else 10.dp))
            Text(
                "+ Agregar tarjeta",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinPrimary,
                modifier = Modifier.clickable { onUpdate(section.copy(cards = section.cards + ScreenCard(title = ""))) }.padding(vertical = 6.dp),
            )
        }

        "HERO_BALANCE", "UPCOMING_PAYMENTS", "ALERTS" -> {
            // Secciones de datos: el contenido lo pone el cliente con lo que carga del server;
            // solo el título se ajusta desde acá (vacío = el nombre por defecto).
            FieldBox("Título (opcional)", section.title ?: "", { onUpdate(section.copy(title = it.ifBlank { null })) })
            Spacer(Modifier.height(8.dp))
            Text(
                when (section.type) {
                    "UPCOMING_PAYMENTS" -> "Se muestra solo cuando hay pagos en los próximos 7 días."
                    "ALERTS" -> "Se muestra solo cuando hay algo por resolver."
                    else -> "Balance neto, ingresos, egresos y flujo del mes: sin más campos."
                },
                fontSize = 12.sp, color = MinTextMute,
            )
        }

        else -> {
            Text("Tipo de sección desconocido: ${section.type}", fontSize = 13.sp, color = MinExpense)
        }
    }
}

@Composable
private fun CardEditor(index: Int, card: ScreenCard, onUpdate: (ScreenCard) -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tarjeta ${index + 1}", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, contentDescription = "Quitar", tint = MinExpense, modifier = Modifier.size(15.dp).clickable(onClick = onRemove).padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        FieldBox("Título", card.title, { onUpdate(card.copy(title = it)) })
        Spacer(Modifier.height(8.dp))
        FieldBox("Subtítulo (opcional)", card.subtitle ?: "", { onUpdate(card.copy(subtitle = it.ifBlank { null })) })
        Spacer(Modifier.height(8.dp))
        FieldBox("Badge (opcional)", card.badge ?: "", { onUpdate(card.copy(badge = it.ifBlank { null })) })
        Spacer(Modifier.height(10.dp))
        ActionEditor(card.action) { onUpdate(card.copy(action = it)) }
    }
}

@Composable
private fun ActionEditor(action: ScreenAction?, onChange: (ScreenAction?) -> Unit) {
    Column {
        Text("ACCIÓN", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionModeChip("Ninguna", action == null) { onChange(null) }
            ActionModeChip("Navegar", action?.type == "NAVIGATE") {
                val target = action?.target?.takeIf { it in ScreenTaxonomy.NAVIGATE_TARGETS } ?: ScreenTaxonomy.NAVIGATE_TARGETS.first()
                onChange(ScreenAction("NAVIGATE", target))
            }
            ActionModeChip("Enlace", action?.type == "OPEN_URL") {
                val target = action?.target?.takeIf { it.startsWith("https://") } ?: "https://"
                onChange(ScreenAction("OPEN_URL", target))
            }
        }
        when (action?.type) {
            "NAVIGATE" -> {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScreenTaxonomy.NAVIGATE_TARGETS.forEach { target ->
                        SelectRow(
                            label = NAVIGATE_TARGET_LABELS[target] ?: target,
                            selected = action.target == target,
                            onClick = { onChange(ScreenAction("NAVIGATE", target)) },
                        )
                    }
                }
            }
            "OPEN_URL" -> {
                Spacer(Modifier.height(8.dp))
                val isValidUrl = action.target.startsWith("https://")
                FieldBox("https://", action.target, { onChange(ScreenAction("OPEN_URL", it)) })
                if (!isValidUrl) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Los enlaces deben empezar con https://",
                        fontSize = 12.sp,
                        color = MinExpense,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ActionModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (selected) MinBg else MinText)
    }
}

@Composable
private fun FieldBox(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainer)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 14.sp, color = MinText),
            cursorBrush = SolidColor(MinText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinSurfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) MinText else MinBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.5.sp, color = MinText, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
