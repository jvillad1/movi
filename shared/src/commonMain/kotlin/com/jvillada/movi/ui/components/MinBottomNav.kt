package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SwapVert
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
 * Destinos de la navegación principal. En el teléfono la barra muestra cinco
 * (Inicio · Movimientos · + · Cuentas · Más); en pantalla ancha el rail muestra además
 * Créditos y Presupuestos como entradas propias. Una pantalla declara UN destino
 * (ver `navTabFor` en Navigation.kt) y cada superficie decide cómo lo resalta: la barra del
 * teléfono pinta CREDITS y BUDGETS como "Más", que es por donde se llega a ellos ahí.
 */
enum class NavTab { HOME, TRANSACTIONS, ADD, ACCOUNTS, CREDITS, BUDGETS, MORE }

/** Qué ítem de la barra del teléfono se resalta para un destino dado. */
fun NavTab.asBottomBarTab(): NavTab = when (this) {
    NavTab.CREDITS, NavTab.BUDGETS -> NavTab.MORE
    else -> this
}

@Composable
fun MinBottomNav(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    // En pantalla ancha el rail de la raíz (MinNavRail) toma el lugar de la barra.
    // Desde la Ola 4 esta barra la pinta SOLO App.kt, una vez, debajo de la pantalla activa;
    // ninguna pantalla la llama por su cuenta.
    if (LocalWindowWidthClass.current == WindowWidthClass.Expanded) return
    val highlighted = active?.asBottomBarTab()
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
            NavItem(NavTab.HOME, "Inicio", Icons.Rounded.Home, highlighted, onTabSelected)
            NavItem(NavTab.TRANSACTIONS, "Movs", Icons.Rounded.SwapVert, highlighted, onTabSelected)

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

            // F19: Cuentas entra a la barra — antes el único camino era un "Ver todas +" del
            // Inicio que solo aparecía con al menos una cuenta creada. Presupuestos pasó a Más.
            NavItem(NavTab.ACCOUNTS, "Cuentas", Icons.Rounded.AccountBalanceWallet, highlighted, onTabSelected)
            NavItem(NavTab.MORE, "Más", Icons.Rounded.GridView, highlighted, onTabSelected)
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
                // La píldora del activo era un morado sólido propio (`MinPrimaryContainer`), un
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
            style = Movi.textos.rotulo.copy(
                letterSpacing = 0.2.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
        )
    }
}
