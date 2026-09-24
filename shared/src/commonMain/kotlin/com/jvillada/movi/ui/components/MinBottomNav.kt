package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

/**
 * Los destinos de la navegación principal.
 *
 * Ola C (2026-09): **cuatro lugares y el botón de agregar**, los mismos en el teléfono y en la web.
 * Hasta acá la barra del teléfono decía «Inicio · Movs · + · Cuentas · Más» y el rail de la web
 * agregaba Créditos y Presupuestos, así que la misma pantalla se resaltaba distinto según el ancho y
 * «Más» era un cajón de todo lo que no entraba. Ahora cada pestaña contesta una pregunta (ver
 * `navTabFor` en Navigation.kt) y lo que antes vivía en «Más» se abre tocando el avatar (Ajustes).
 *
 * [ADD] no es un lugar: es la hoja de «Agregar», que se abre encima de la pantalla actual.
 */
enum class NavTab { HOY, MOVIMIENTOS, ADD, PLAN, PATRIMONIO }

/** Un destino principal: pestaña + rótulo + ícono. */
data class DestinoPrincipal(val tab: NavTab, val label: String, val icon: ImageVector)

/**
 * Las cuatro pestañas, en orden. Es la ÚNICA fuente: la barra del teléfono ([MinBottomNav]) pinta
 * las dos primeras, el «+» y las dos últimas; el rail de la web ([MinNavRail]) las cuatro y debajo
 * «Agregar». Que las dos lean esta lista es lo que garantiza que no vuelvan a separarse.
 *
 * «Movimientos» va entero también en el teléfono (antes «Movs»): entra a 390 dp junto a las otras
 * tres y el «+», medido con el motor de texto real en `LasCuatroPestanasTest`.
 */
val destinosPrincipales: List<DestinoPrincipal> = listOf(
    DestinoPrincipal(NavTab.HOY, "Hoy", Icons.Rounded.Today),
    DestinoPrincipal(NavTab.MOVIMIENTOS, "Movimientos", Icons.Rounded.SwapVert),
    DestinoPrincipal(NavTab.PLAN, "Plan", Icons.AutoMirrored.Rounded.EventNote),
    DestinoPrincipal(NavTab.PATRIMONIO, "Patrimonio", Icons.Rounded.AccountBalance),
)

@Composable
fun MinBottomNav(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    // En pantalla ancha el rail de la raíz (MinNavRail) toma el lugar de la barra.
    // Desde la Ola 4 esta barra la pinta SOLO App.kt, una vez, debajo de la pantalla activa;
    // ninguna pantalla la llama por su cuenta.
    if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) return
    val (antes, despues) = destinosPrincipales.chunked(2).let { it[0] to it[1] }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Movi.colores.tarjeta)
            .navigationBarsPadding(),
    ) {
        // La barra se despega del contenido con un BORDE, no con otro gris. Es la misma decisión
        // que la de las tarjetas: con planos tan cercanos, el borde es el que hace el trabajo.
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            antes.forEach { NavItem(it.tab, it.label, it.icon, active, onTabSelected) }

            // Center FAB
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Movi.colores.marca)
                    .clickable { onTabSelected(NavTab.ADD) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Agregar",
                    // Era un `Color(0xFF1A1040)` escrito a mano acá, el único lugar de la app que
                    // lo decía. Ese valor ES el rol `sobreMarca`, y ahora se llama así: da 8,47:1
                    // contra el lavanda, y la prueba de contraste no deja que baje.
                    tint = Movi.colores.sobreMarca,
                    modifier = Modifier.size(26.dp),
                )
            }

            despues.forEach { NavItem(it.tab, it.label, it.icon, active, onTabSelected) }
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    val isActive = tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(Movi.formas.pleno))
                // La píldora del activo era un morado sólido propio (`Movi.colores.marca.copy(alpha = 0.16f)`), un
                // color que existía solo para esto. Ahora es la MARCA lavada: el sistema tiene un
                // lavanda, no dos, y el ícono encima va del mismo lavanda a plena fuerza.
                .background(
                    if (isActive) Movi.colores.marca.copy(alpha = 0.16f) else Color.Transparent,
                )
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Movi.colores.marca else Movi.colores.textoApagado,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            // Un renglón siempre: un rótulo que se partiera en dos haría esa pestaña más alta que
            // las otras y correría su ícono hacia arriba. Ver [destinosPrincipales].
            maxLines = 1,
            style = Movi.textos.rotulo.copy(
                letterSpacing = 0.2.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
        )
    }
}
