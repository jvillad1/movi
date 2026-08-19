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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
) {
    var focused by remember { mutableStateOf(false) }
    val matches = remember(value, type, usedCategories) { suggestCategoryMatches(value, type, usedCategories) }

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
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(MinText),
                textStyle = TextStyle(color = MinText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = MinTextFaint)
                    inner()
                },
            )
        }
        // Solo mientras el campo tiene foco: tocar una sugerencia ya lo quita del medio.
        if (focused && matches.isNotEmpty()) {
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
                            .clickable {
                                onValueChange(name)
                                focused = false
                                onSuggestionPicked()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    if (i < matches.size - 1) Hairline()
                }
            }
        }
    }
}
