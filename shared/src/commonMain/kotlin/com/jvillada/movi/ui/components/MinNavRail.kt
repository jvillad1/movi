package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*

/** Un destino del rail: pestaña + rótulo + ícono. */
data class RailDestination(val tab: NavTab, val label: String, val icon: ImageVector)

/**
 * Lo que el rail muestra, en orden. Es la ÚNICA fuente: el rail la pinta y Más (F59) la usa
 * para no repetir en pantalla ancha los destinos que ya están a la izquierda. Agregar una
 * entrada acá alcanza para que aparezca en el rail y desaparezca de Más.
 */
val railDestinations: List<RailDestination> = listOf(
    RailDestination(NavTab.HOME, "Inicio", Icons.Rounded.Home),
    RailDestination(NavTab.TRANSACTIONS, "Movimientos", Icons.Rounded.SwapVert),
    RailDestination(NavTab.ACCOUNTS, "Cuentas", Icons.Rounded.AccountBalanceWallet),
    RailDestination(NavTab.CREDITS, "Créditos", Icons.Rounded.CreditCard),
    RailDestination(NavTab.BUDGETS, "Presupuestos", Icons.Rounded.PieChart),
    // Rediseño de Recurrentes (2026-09): Recurrentes dejó de ser un destino propio del rail —
    // «Flujo libre», las candidatas por confirmar y los próximos pagos viven ahora en
    // Movimientos (chip «Recurrentes»), y editar un recurrente existente se hace desde el
    // detalle de un movimiento. Ya no hay `NavTab.RECURRING` ni `Screen.Recurrentes`: quien
    // quiera llevar ahí navega a `Screen.Transactions(CHIP_RECURRENTES)`.
    RailDestination(NavTab.MORE, "Más", Icons.Rounded.GridView),
)

/**
 * Wide-window counterpart of MinBottomNav: a left rail rendered once at the
 * App root. Same active-pill language; en pantalla ancha hay lugar para mostrar
 * además Créditos y Presupuestos como entradas propias (en el teléfono viven en Más).
 */
@Composable
fun MinNavRail(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(216.dp)
            .fillMaxHeight()
            .background(Movi.colores.tarjeta)
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.margen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "movi",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Movi.colores.marca,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
        )

        railDestinations.forEach { dest ->
            RailItem(dest.tab, dest.label, dest.icon, active, onTabSelected)
        }

        Spacer(Modifier.height(12.dp))

        // Primary action — mirrors the bottom-nav center FAB
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Movi.colores.marca)
                .clickable { onTabSelected(NavTab.ADD) }
                .padding(horizontal = Movi.espacios.amplio, vertical = 10.dp),
        ) {
            // El mismo `Color(0xFF1A1040)` a mano que estaba en la barra del teléfono, repetido
            // dos veces más acá. Tres copias de un color sin nombre: ahora es `sobreMarca`.
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Movi.colores.sobreMarca,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Agregar",
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.SemiBold,
                color = Movi.colores.sobreMarca,
            )
        }
    }
}

@Composable
private fun RailItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    val isActive = tab == active
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.normal))
            .background(if (isActive) Movi.colores.marca.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Movi.colores.marca else Movi.colores.textoApagado,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
            letterSpacing = 0.2.sp,
        )
    }
}
