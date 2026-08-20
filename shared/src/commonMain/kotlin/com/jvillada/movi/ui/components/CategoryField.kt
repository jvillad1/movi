package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import kotlinx.coroutines.delay

/**
 * F35: filtra y ordena las sugerencias de categoría para [CategoryField]. Separada del
 * `@Composable` para poder testearla en `:shared:commonTest` sin arrancar Compose.
 *
 * Coincide por "contiene", sin tildes ni mayúsculas ("compu" encuentra "Computador",
 * "medic" encuentra "Médico"). Orden: primero las predefinidas ([PREDEFINED_CATEGORIES]
 * filtradas por [type] si se pasa, en su orden original), después las [usedCategories] que
 * no dupliquen a una predefinida (comparación también sin tildes/mayúsculas) — así una
 * categoría nueva escrita a mano no compite por el primer lugar con el catálogo fijo.
 * Sin recortar por defecto: el que llama decide si hace falta un scroll (ver [CategoryField]).
 */
fun suggestCategoryMatches(
    query: String,
    type: TransactionType? = null,
    usedCategories: Collection<String> = emptyList(),
): List<String> {
    val predefined = PREDEFINED_CATEGORIES
        .filter { type == null || it.type == type.name || it.type == "BOTH" }
        .map { it.name }
    val q = normalizeForMatch(query)
    val predefinedMatches = predefined.filter { normalizeForMatch(it).contains(q) }
    val usedMatches = usedCategories
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filterNot { used -> predefined.any { it.equals(used, ignoreCase = true) } }
        .filter { normalizeForMatch(it).contains(q) }
    return predefinedMatches + usedMatches
}

/** Minúsculas y sin tildes/eñe — no hay normalización Unicode común a los 3 targets acá. */
private fun normalizeForMatch(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ñ' -> 'n'
                else -> c
            },
        )
    }
}

/**
 * Campo de categoría compartido: texto libre con sugerencias, en vez de un picker de lista
 * fija o un texto libre sin ayuda. Usado en QuickAdd, Presupuestos (crear) y Recurrentes
 * (crear/editar) — así una categoría se llama igual en todos lados, lo que importa porque
 * presupuestos y gastos se cruzan por nombre. [ChangeCategorySheet] se queda como lista: ahí
 * se elige entre el catálogo para UN movimiento existente, no se escribe una categoría nueva.
 *
 * Tocar una sugerencia la elige y cierra la lista; escribir algo que no matchea se acepta tal
 * cual (el recorte de espacios lo hace quien guarda, como ya hacían estas pantallas).
 */
@Composable
fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    usedCategories: Set<String> = emptySet(),
    label: String? = "CATEGORÍA",
    placeholder: String = "Ej: Vivienda, Suscripción, Salud",
    /** Además de [onValueChange]: se dispara solo al tocar una sugerencia, nunca al tipear —
     *  para que quien tiene un sub-picker de pantalla completa (QuickAdd) pueda cerrarlo solo. */
    onSuggestionPicked: () -> Unit = {},
    /** Ola 2 #3: si se pasa, QuickAdd la usa para pedir foco al abrir el sub-picker (el campo
     *  arranca prellenado — sin esto nunca se veía el teclado ni la lista de sugerencias). */
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // F62: la lista NO puede desmontarse en el mismo frame en que el campo pierde el foco.
    // En táctil (web/PWA), el down del tap sobre una sugerencia desenfoca el campo ANTES de
    // que llegue el up: si la lista se condiciona a `focused` a secas, se desmonta entre el
    // down y el up y el tap muere en la nada — "toco la sugerencia y no pasa nada" (con
    // mouse no se reproduce porque el click no roba el foco en el down). La lista se oculta
    // con una pequeña demora tras perder el foco, para que el tap en vuelo se complete.
    var suggestionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            suggestionsVisible = true
        } else if (suggestionsVisible) {
            delay(350)
            suggestionsVisible = false
        }
    }
    val focusManager = LocalFocusManager.current
    // Estado interno de texto+selección: separado de [value] para poder seleccionar todo el
    // texto al enfocar (Ola 2 #3b) sin pelearse con el `value: String` que ya usan las 3
    // pantallas que llaman a este campo. Solo se resincroniza con [value] cuando el cambio vino
    // de AFUERA (p. ej. QuickAdd reseteando la categoría al cambiar Gasto/Ingreso) — si viene de
    // nuestro propio tipeo, `textFieldValue.text` ya coincide y no hace falta tocar el cursor.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    // Ola 2 #3a: con el campo prellenado (QuickAdd arranca en "Comida", Recurrentes en "Otros"),
    // la única sugerencia visible era la misma categoría ya escrita. Si lo que hay en el campo
    // coincide EXACTAMENTE con una sugerencia, o está vacío, se listan TODAS las disponibles.
    val allMatches = remember(type, usedCategories) { suggestCategoryMatches("", type, usedCategories) }
    val isExactMatch = value.isBlank() || allMatches.any { normalizeForMatch(it) == normalizeForMatch(value) }
    val matches = if (isExactMatch) allMatches else remember(value, type, usedCategories) {
        suggestCategoryMatches(value, type, usedCategories)
    }

    fun pick(name: String) {
        onValueChange(name)
        textFieldValue = TextFieldValue(name, selection = TextRange(name.length))
        // Ola 2 #3d: antes esto ponía `focused = false` a mano sin soltar el foco real del
        // campo — el cursor seguía ahí, así que un onFocusChanged posterior nunca volvía a
        // dispararse y las sugerencias no reaparecían al seguir tipeando. clearFocus() sí baja
        // el foco de verdad; `focused` se actualiza solo, vía onFocusChanged.
        focusManager.clearFocus()
        // F62: al elegir sí se cierra al instante — la demora es solo para taps en vuelo.
        suggestionsVisible = false
        onSuggestionPicked()
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(label, fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerLow)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onValueChange(it.text)
                },
                singleLine = true,
                cursorBrush = SolidColor(MinText),
                textStyle = TextStyle(color = MinText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { state ->
                        // Ola 2 #3b: al ganar el foco (no en cada recomposición), seleccionar
                        // todo el texto — con el campo prellenado, tipear reemplaza en vez de
                        // insertarse en medio de "Comida".
                        if (state.isFocused && !focused) {
                            textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
                        }
                        focused = state.isFocused
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = MinTextFaint)
                    inner()
                },
            )
        }
        // Visible mientras el campo tiene foco (más la demora de gracia de F62): tocar una
        // sugerencia la elige y lo quita del medio.
        if (suggestionsVisible && matches.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerHigh)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp)),
            ) {
                matches.forEachIndexed { i, name ->
                    Text(
                        name,
                        fontSize = 14.sp,
                        color = MinText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pick(name) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    if (i < matches.size - 1) Hairline()
                }
            }
        }
    }
}
