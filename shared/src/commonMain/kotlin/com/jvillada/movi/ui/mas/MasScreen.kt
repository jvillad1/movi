package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.railDestinations
import com.jvillada.movi.ui.screenForTab

private data class MasItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val screen: Screen,
)

private val items = listOf(
    // F19: Cuentas era invisible sin al menos una cuenta ya creada (el "Ver todas +" del Inicio
    // solo aparece con la lista no vacía) — entra acá como primer acceso, incondicional.
    MasItem("Cuentas",      Icons.Rounded.AccountBalanceWallet, Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.Accounts),
    // Ola 4: Presupuestos dejó su lugar en la barra inferior a Cuentas y entra acá, justo después.
    MasItem("Presupuestos", Icons.Rounded.PieChart,         Color(0xFF7DDDB0), Color(0x1A7DDDB0), Screen.Budgets),
    MasItem("Créditos",     Icons.Rounded.CreditCard,      Color(0xFFFFB4AB), Color(0x1FFFB4AB), Screen.Credits),
    // F61: Inversiones ya no es sección — las cuentas de inversión se ven en Cuentas.
    MasItem("Metas",        Icons.Rounded.Flag,             Color(0xFFFFD479), Color(0x24FFD479), Screen.Goals),
    MasItem("Extractos",    Icons.Rounded.UploadFile,       Color(0xFFC7BCFF), Color(0x24C7BCFF), Screen.Extractos),
    // Ola 7: mismo rótulo que el encabezado de la pantalla (título = rótulo del menú).
    MasItem("Mensajes del banco", Icons.Rounded.Sms,              Color(0xFF81D4FA), Color(0x2481D4FA), Screen.SMSInbox),
    MasItem("Movi AI",      Icons.Rounded.AutoAwesome,      Color(0xFFE8BBF8), Color(0x24E8BBF8), Screen.AIChat),
    // Rediseño de Recurrentes (2026-09): sin entrada propia — «Flujo libre», las candidatas por
    // confirmar y los próximos pagos se mudaron a Movimientos (chip «Recurrentes») y editar un
    // recurrente existente ya se hacía desde el detalle de un movimiento. `Screen.Recurrentes`
    // ya no existe; el destino equivalente es `Screen.Transactions(CHIP_RECURRENTES)`.
    // Ola 10: la única puerta a «Categorías». Va junto a Presupuestos —la otra pantalla que se
    // cruza con el gasto POR NOMBRE DE CATEGORÍA—, que es donde el dueño va a acordarse de que
    // quería arreglar un nombre.
    MasItem("Categorías",   Icons.AutoMirrored.Rounded.Label, Color(0xFF7DDDB0), Color(0x1A7DDDB0), Screen.Categorias),
    // Ola 18: los papeles. Va PEGADO a «Extractos» porque el importador archiva ahí lo que pasa
    // por él — quien sube un extracto y después se pregunta «¿dónde quedó el PDF?» busca al lado.
    MasItem("Documentos",   Icons.Rounded.Folder,           Color(0xFFB3C8FF), Color(0x1AB3C8FF), Screen.Documentos),
    // Ola 14: la guía de arranque, que se apaga sola en el Inicio y hasta acá no tenía forma de
    // volver a abrirse. Va en Más y no en el Inicio a propósito: el dueño pidió *poder volver*,
    // no que la guía le reaparezca (ver PrimerosPasosScreen).
    MasItem("Primeros pasos", Icons.Rounded.Checklist,      Color(0xFFFFD479), Color(0x24FFD479), Screen.PrimerosPasos),
    // F40: "Análisis" no analizaba — era un índice con cifras, y eso ahora es el Inicio.
    MasItem("Perfil",       Icons.Rounded.ManageAccounts,   Color(0xFFB3C8FF), Color(0x24B3C8FF), Screen.Profile),
)

@Composable
fun MasScreen(onNavigate: (Screen) -> Unit) {
    // F47 · F48: "Editor de pantallas" vivía acá, agregado a la grilla después de que
    // isScreenAdmin() resolvía — eso hacía que la grilla "saltara" al cargar, y además era
    // una herramienta de administración mezclada con Créditos y Metas. Se mudó al final de
    // Perfil, en una sección "Administración" visible solo para quien administra el Inicio.

    // F59: en pantalla ancha el rail de la izquierda ya muestra Inicio, Movimientos, Cuentas,
    // Créditos, Presupuestos y Más — repetirlos acá era ruido. La lista sale de la MISMA
    // fuente que pinta el rail (railDestinations), no de una copia a mano. En el teléfono la
    // barra tiene menos destinos, así que Más sigue completo.
    val widthClass = LocalWindowWidthClass.current
    val visibleItems = remember(widthClass) {
        if (widthClass == WindowWidthClass.Expanded) {
            val railScreens = railDestinations.map { screenForTab(it.tab) }
            items.filterNot { it.screen in railScreens }
        } else items
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg),
    ) {
        MinScreenHeader(
            title = "Más",
            leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(visibleItems) { item ->
                MasCard(item, onNavigate)
            }
        }
    }
}

@Composable
private fun MasCard(item: MasItem, onNavigate: (Screen) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            // Ola 8 · V13: sin `fillMaxWidth` cada ficha medía lo que midiera su rótulo, así
            // que la fila quedaba con tarjetas de anchos distintos.
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MinSurfaceContainer)
            .clickable { onNavigate(item.screen) }
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.bg),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MinTextDim,
            // V13: «Mensajes del banco» ocupa dos renglones y su ficha quedaba más alta que
            // las demás, desalineando la fila entera. Reservando SIEMPRE dos renglones, todas
            // las fichas miden lo mismo — y el rótulo que se parte se centra en vez de
            // quedar volcado a la izquierda.
            minLines = 2,
            maxLines = 2,
            // Un rótulo que necesitara TRES renglones se cortaría; con `Ellipsis` al menos lo
            // dice («Mensajes del ban…») en vez de recortar en silencio, que es lo que hace el
            // `Clip` por defecto. Hoy el más largo —«Mensajes del banco»— entra en dos.
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
