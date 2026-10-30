package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.*
import androidx.compose.material3.HorizontalDivider
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.alPresionar
import com.jvillada.movi.ui.components.ds.anilloDeFoco
import com.jvillada.movi.ui.components.ds.hundimientoAlPresionar

/**
 * Los destinos de la navegación principal.
 *
 * Vuelta 38 (la opción 3): **cuatro lugares**, los mismos en el teléfono y en la web — Inicio ·
 * Actividad · Pagos · Tú. Cada pestaña contesta una pregunta (ver `navTabFor` en Navigation.kt). Lo que
 * eran las pestañas Patrimonio y Espacios, y lo que abría el avatar (Ajustes), vive en «Tú».
 *
 * Los nombres del enum son los de siempre (`MOVIMIENTOS` es Actividad, `PLAN` es Pagos) para no
 * renombrar cada pila, prueba y destino que ya los nombra; lo que se lee sale de [destinosPrincipales].
 *
 * [ADD] no es un lugar: es la hoja de «Agregar», que se abre encima de la pantalla actual. Desde la
 * vuelta 38 solo la ofrece el rail: en el teléfono no hay «+», anotar vive en las acciones de Inicio.
 *
 * [MOVI_AI] es un destino **solo del rail** (desde 600 dp, ver [destinosDelRail]): en el teléfono la
 * barra sigue con sus cuatro y Movi AI se abre desde Inicio.
 */
enum class NavTab { HOY, MOVIMIENTOS, ADD, PLAN, TU, MOVI_AI }

/**
 * Un destino principal: pestaña + rótulo + ícono. [rotuloCorto] es el del rail compacto de una
 * ventana mediana (80 dp de ancho, ver [MinNavRail]); con los rótulos de la opción 3 todos entran.
 */
data class DestinoPrincipal(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector,
    val rotuloCorto: String = label,
)

/**
 * Las cuatro pestañas, en orden, con los íconos de la maqueta (la casa, la lista, el calendario con
 * el visto y la persona). Es la ÚNICA fuente: la barra del teléfono ([MinBottomNav]) y el rail de la
 * web ([MinNavRail]) pintan las cuatro. Que las dos lean esta lista es lo que garantiza que no
 * vuelvan a separarse.
 */
val destinosPrincipales: List<DestinoPrincipal> = listOf(
    DestinoPrincipal(NavTab.HOY, "Inicio", Icons.Rounded.Home),
    DestinoPrincipal(NavTab.MOVIMIENTOS, "Actividad", Icons.AutoMirrored.Rounded.FormatListBulleted),
    DestinoPrincipal(NavTab.PLAN, "Pagos", Icons.Rounded.EventAvailable),
    DestinoPrincipal(NavTab.TU, "Tú", Icons.Rounded.Person),
)

/**
 * **Movi AI, como destino propio del rail.** La promesa central del producto —preguntarle a Movi— no
 * tenía lugar en la navegación ancha: en la web había que volver a Inicio para llegar. Lleva las
 * chispas que ya usa Inicio en «Pregúntale a Movi».
 *
 * **Solo en el rail** (mediano y expandido): la barra del teléfono se queda con sus cuatro, y en el
 * teléfono Movi AI se sigue abriendo desde Inicio.
 */
val destinoMoviAi: DestinoPrincipal =
    DestinoPrincipal(NavTab.MOVI_AI, "Movi AI", Icons.Rounded.AutoAwesome)

/** Lo que pinta el rail, en orden: las cuatro pestañas y Movi AI. La barra del teléfono, no. */
val destinosDelRail: List<DestinoPrincipal> = destinosPrincipales + destinoMoviAi

/**
 * **La barra de pestañas del teléfono** (vuelta 38, la de la opción 3): blanca, de
 * [Medida.altoDeBarraDePestanas] (76) con el borde de arriba, y **las cuatro con su rótulo siempre**,
 * a 360 dp también (cada una se lleva un cuarto del ancho). La activa lleva la píldora amarilla de
 * 56 × 30 detrás del ícono y el rótulo en 12/700; las demás, 12/500 en el texto medio.
 *
 * **Sin «+».** Anotar vive en las 8 acciones de Inicio (vuelta 39); el «+» con su
 * menú queda en el rail, donde no hay un Inicio siempre a la vista.
 */
@Composable
fun MinBottomNav(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
) {
    // Desde 600 dp el rail de la raíz (MinNavRail, compacto en una ventana mediana) toma el lugar
    // de la barra: la barra es solo del teléfono (Ola W1).
    // Desde la Ola 4 esta barra la pinta SOLO App.kt, una vez, debajo de la pantalla activa;
    // ninguna pantalla la llama por su cuenta.
    if (LocalWindowWidthClass.current != WindowWidthClass.Compact) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Movi.colores.tarjeta)
            .navigationBarsPadding(),
    ) {
        // La barra se despega del contenido con un BORDE, no con otro gris: blanca con el borde
        // de arriba en `borde`, como la de la opción 3 (vuelta 29).
        HorizontalDivider(thickness = Medida.trazo, color = Movi.colores.borde)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Medida.altoDeBarraDePestanas)
                .testTag(TAG_BARRA_DE_PESTANAS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinosPrincipales.forEach {
                NavItem(it.tab, it.label, it.icon, active, onTabSelected, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** El `testTag` de la barra de pestañas del teléfono. */
const val TAG_BARRA_DE_PESTANAS: String = "barra-de-pestanas"

/** El `testTag` de la píldora de una pestaña de la barra (vuelta 79: ahí va lo que se ve del toque). */
const val TAG_PILDORA_DE_PESTANA: String = "barra-de-pestanas:pildora"

/** El `testTag` de cada pestaña de la barra del teléfono. */
fun tagDePestana(tab: NavTab): String = "barra-de-pestanas:${tab.name}"

@Composable
private fun NavItem(
    tab: NavTab,
    label: String,
    icon: ImageVector,
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = tab == active
    val toque = remember { MutableInteractionSource() }
    val pildora = RoundedCornerShape(Movi.formas.pleno)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxHeight()
            // Vuelta 55: el foco del teclado se ve (hacia adentro: la barra llega al borde).
            .anilloDeFoco(RoundedCornerShape(Radio.m), adentro = true)
            // Un lector de pantalla oye el rótulo (el ícono no dice nada) y sabe cuál está elegida.
            // Vuelta 79: la celda entera se toca, pero **sin ripple ni hover propio**: lo que se ve del
            // toque (la escala y el velo) va solo en la píldora, nunca un rectángulo de toda la celda.
            .alPresionar(onClick = { onTabSelected(tab) }, rol = Role.Tab, escala = 1f, fuente = toque)
            .semantics { selected = isActive }
            .testTag(tagDePestana(tab)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // La píldora del activo (opción 3, vuelta 29): el amarillo de la marca detrás del
                // ícono, 56 × 30, con el ícono en la tinta encima. Las demás, sin píldora y en el
                // texto medio.
                .size(Medida.anchoDePildoraDePestana, Medida.altoDePildoraDePestana)
                // Vuelta 79: la píldora se hunde al presionar y, sin ser la activa, lleva el velo en
                // hover (la web) recortado a ella. La activa no: el velo la ensuciaba.
                .hundimientoAlPresionar(toque, velo = !isActive, forma = pildora)
                .testTag(TAG_PILDORA_DE_PESTANA)
                .clip(pildora)
                .background(if (isActive) Movi.colores.marca else Color.Transparent),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Movi.colores.sobreMarca else Movi.colores.textoMedio,
                modifier = Modifier.size(Icono.m),
            )
        }
        Text(
            text = label,
            // Un renglón siempre: un rótulo que se partiera en dos haría esa pestaña más alta que
            // las otras y correría su ícono hacia arriba.
            maxLines = 1,
            style = if (isActive) Movi.textos.pestanaActiva else Movi.textos.pestana,
            color = if (isActive) Movi.colores.texto else Movi.colores.textoMedio,
        )
    }
}
