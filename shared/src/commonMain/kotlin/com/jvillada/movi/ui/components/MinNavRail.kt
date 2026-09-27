package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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

/**
 * Contraparte de [MinBottomNav] desde 600 dp: un rail a la izquierda, pintado una vez en la raíz de
 * App.kt — ancho (216 dp) en escritorio y [compacto] (80 dp) en una ventana mediana. Ola C:
 * muestra **las mismas cuatro pestañas** que la barra del teléfono ([destinosPrincipales]) y
 * «Agregar» — sin Créditos, Presupuestos ni Más, que ahora viven adentro de Patrimonio, de Plan y
 * del avatar.
 */
@Composable
fun MinNavRail(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
    compacto: Boolean = false,
) {
    if (compacto) {
        RailCompacto(active, onTabSelected)
        return
    }
    Column(
        modifier = Modifier
            .width(216.dp)
            .fillMaxHeight()
            .background(Movi.colores.tarjeta)
            .windowInsetsPadding(barrasDelSistemaDelRail)
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.margen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "movi",
            // Tamaño suelto a propósito: es el logotipo del riel, no un título; ningún estilo de la escala es ese papel.
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Movi.colores.marca,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
        )

        destinosPrincipales.forEach { dest ->
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

/**
 * Las barras del sistema que el rail descuenta: arriba (la de estado, que si no tapa el «+»), abajo y
 * al costado del rail (la de navegación de Android en horizontal). En la web son cero.
 */
private val barrasDelSistemaDelRail: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/** El ancho del rail compacto de una ventana mediana. */
val ANCHO_DEL_RAIL_COMPACTO = 80.dp

/**
 * Ola W1: el rail de una ventana **mediana** (600–999 dp: tablet vertical, teléfono horizontal,
 * ventana angosta de escritorio). 80 dp: el «+» arriba —es lo que más se usa— y debajo cada pestaña
 * con su ícono y su rótulo corto ([DestinoPrincipal.rotuloCorto]). El rail ancho de 216 dp se come
 * un tercio de una tablet vertical; la barra inferior, estirada a 800 dp, deja los destinos lejos
 * del pulgar y del mouse.
 */
@Composable
private fun RailCompacto(active: NavTab?, onTabSelected: (NavTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Movi.colores.tarjeta)
            // Afuera del ancho: la barra de navegación en horizontal (a la izquierda) ensancha el
            // fondo en vez de comerse los 80 dp del rail.
            .windowInsetsPadding(barrasDelSistemaDelRail)
            .width(ANCHO_DEL_RAIL_COMPACTO)
            .padding(vertical = Movi.espacios.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Movi.colores.marca)
                .clickable { onTabSelected(NavTab.ADD) },
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Movi.colores.sobreMarca,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        destinosPrincipales.forEach { dest ->
            RailCompactoItem(dest, active, onTabSelected)
        }
    }
}

@Composable
private fun RailCompactoItem(dest: DestinoPrincipal, active: NavTab?, onTabSelected: (NavTab) -> Unit) {
    val isActive = dest.tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTabSelected(dest.tab) }
            // Un lector de pantalla dice el nombre entero UNA vez («Movimientos»), no el ícono y
            // después el rótulo corto («Movimientos, Movs»).
            .clearAndSetSemantics {
                contentDescription = dest.label
                role = Role.Tab
                selected = isActive
                onClick { onTabSelected(dest.tab); true }
            }
            .padding(horizontal = RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(Movi.formas.pleno))
                .background(if (isActive) Movi.colores.marca.copy(alpha = 0.16f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                tint = if (isActive) Movi.colores.marca else Movi.colores.textoApagado,
                modifier = Modifier.size(22.dp),
            )
        }
        RotuloDelRailCompacto(dest.rotuloCorto, isActive)
    }
}

/** El relleno a cada lado de un ítem del rail compacto: el rótulo tiene 80 − 2×2 = 76 dp. */
internal val RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO = 2.dp

/**
 * El rótulo de un ítem del rail compacto. **Nunca se parte ni se corta**: en un renglón y, si no
 * entra —«Patrimonio» con la letra del sistema al 130 % mide ~87 dp contra 76—, se achica de a
 * cuartos de punto hasta que entre. Es el mismo modo de falla que `LasCuatroPestanasTest` protege en
 * la barra del teléfono; ahí sobra lugar, acá no.
 */
@Composable
internal fun RotuloDelRailCompacto(texto: String, activo: Boolean, modifier: Modifier = Modifier) {
    val estilo = Movi.textos.rotulo.copy(
        letterSpacing = 0.2.sp,
        fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal,
        color = if (activo) Movi.colores.texto else Movi.colores.textoApagado,
    )
    BasicText(
        text = texto,
        modifier = modifier,
        style = estilo,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = estilo.fontSize, stepSize = 0.25.sp),
    )
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
            style = Movi.textos.cuerpo,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
        )
    }
}
