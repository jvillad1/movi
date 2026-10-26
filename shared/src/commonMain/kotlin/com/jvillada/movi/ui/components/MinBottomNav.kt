package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
 *
 * [MOVI_AI] es un destino **solo del rail** (desde 600 dp, ver [destinosDelRail]): en el teléfono la
 * barra sigue con sus cuatro y Movi AI se abre desde Hoy.
 */
enum class NavTab { HOY, MOVIMIENTOS, ADD, PLAN, ESPACIOS, PATRIMONIO, MOVI_AI }

/**
 * Un destino principal: pestaña + rótulo + ícono. [rotuloCorto] es el del rail compacto de una
 * ventana mediana (80 dp de ancho, ver [MinNavRail]): ahí «Movimientos» no entra.
 */
data class DestinoPrincipal(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector,
    val rotuloCorto: String = label,
)

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
    DestinoPrincipal(NavTab.MOVIMIENTOS, "Movimientos", Icons.Rounded.SwapVert, rotuloCorto = "Movs"),
    DestinoPrincipal(NavTab.PLAN, "Plan", Icons.AutoMirrored.Rounded.EventNote),
    // R3 · lo grupal («Casa con Caro») tiene casa propia, entre Plan y Patrimonio.
    DestinoPrincipal(NavTab.ESPACIOS, "Espacios", Icons.Rounded.Groups),
    DestinoPrincipal(NavTab.PATRIMONIO, "Patrimonio", Icons.Rounded.AccountBalance),
)

/** Cuántas pestañas van antes del «+» en la barra del teléfono: Hoy y Movimientos. */
const val PESTANAS_ANTES_DEL_MAS: Int = 2

/**
 * **Movi AI, como destino propio del rail.** La promesa central del producto —preguntarle a Movi— no
 * tenía lugar en la navegación ancha: en la web había que volver a Hoy o abrir Ajustes para llegar.
 * Lleva las chispas que ya usa Hoy en «Pregúntale a Movi».
 *
 * **Solo en el rail** (mediano y expandido): la barra del teléfono se queda con sus cuatro y el «+»
 * —un quinto lugar ahí apretaría los rótulos que `LasCuatroPestanasTest` mide—, y en el teléfono
 * Movi AI se sigue abriendo desde Hoy.
 */
val destinoMoviAi: DestinoPrincipal =
    DestinoPrincipal(NavTab.MOVI_AI, "Movi AI", Icons.Rounded.AutoAwesome)

/** Lo que pinta el rail, en orden: las cuatro pestañas y Movi AI. La barra del teléfono, no. */
val destinosDelRail: List<DestinoPrincipal> = destinosPrincipales + destinoMoviAi

@Composable
fun MinBottomNav(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
    /** Ola 5: mantener presionado el «+» abre sus atajos (ver [BotonDeAgregarConAtajos]). */
    onAtajo: ((com.jvillada.movi.ui.quickadd.AtajoDeAgregar) -> Unit)? = null,
) {
    // Desde 600 dp el rail de la raíz (MinNavRail, compacto en una ventana mediana) toma el lugar
    // de la barra: la barra es solo del teléfono (Ola W1).
    // Desde la Ola 4 esta barra la pinta SOLO App.kt, una vez, debajo de la pantalla activa;
    // ninguna pantalla la llama por su cuenta.
    if (LocalWindowWidthClass.current != WindowWidthClass.Compact) return
    val antes = destinosPrincipales.take(PESTANAS_ANTES_DEL_MAS)
    val despues = destinosPrincipales.drop(PESTANAS_ANTES_DEL_MAS)
    // R3 · con Espacios son cinco pestañas y el «+»: a 390 dp entran todas con su rótulo, a 360 dp
    // (o con la letra del sistema grande) no. Los rótulos NUNCA se achican por debajo del token:
    // si no entran todos, se ve solo el de la pestaña activa y las demás quedan con su ícono (su
    // nombre lo sigue diciendo el lector de pantalla). Ver [rotulosQueEntran].
    val medidor = rememberTextMeasurer()
    val estilo = estiloDelRotulo(activo = true)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val densidad = LocalDensity.current
        val anchos = destinosPrincipales.map { d ->
            with(densidad) { medidor.measure(d.label, estilo, maxLines = 1).size.width.toDp() }
        }
        val todos = rotulosQueEntran(anchos, maxWidth)
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
                    .height(Medida.altoDeBarraDePestanas),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                antes.forEach { NavItem(it.tab, it.label, it.icon, active, onTabSelected, conRotulo = todos || it.tab == active) }

                // Center FAB. Tocarlo abre «Agregar»; mantenerlo presionado, sus atajos (Ola 5).
                BotonDeAgregarConAtajos(
                    alTocar = { onTabSelected(NavTab.ADD) },
                    alElegirAtajo = onAtajo,
                    modifier = Modifier
                        .size(Medida.botonFlotante)
                        .shadow(Espacio.xs, CircleShape)
                        .clip(CircleShape)
                        .background(Movi.colores.marca),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Agregar",
                        // Era un lila oscuro escrito a mano acá, el único lugar de la app que lo
                        // decía. Ese valor ES el rol `sobreMarca`, y ahora se llama así: da 8,47:1
                        // contra el lavanda, y la prueba de contraste no deja que baje.
                        tint = Movi.colores.sobreMarca,
                        modifier = Modifier.size(Icono.l),
                    )
                }

                despues.forEach { NavItem(it.tab, it.label, it.icon, active, onTabSelected, conRotulo = todos || it.tab == active) }
            }
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
    /** `false`: solo el ícono, y el lugar del rótulo queda vacío (los íconos no saltan). */
    conRotulo: Boolean = true,
) {
    val isActive = tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
        modifier = Modifier
            .clickable { onTabSelected(tab) }
            .padding(horizontal = Espacio.xxs, vertical = Espacio.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(Movi.formas.pleno))
                // La píldora del activo era un morado sólido propio, un color que existía solo para
                // esto. Es la MARCA lavada: el sistema tiene un lavanda, no dos, y el ícono encima va
                // del mismo lavanda a plena fuerza. 44 × 28, como la del camino 1.
                .background(
                    if (isActive) Movi.colores.lavado(Movi.colores.marca) else Color.Transparent,
                )
                .padding(horizontal = Espacio.s, vertical = Espacio.xxs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Movi.colores.marca else Movi.colores.textoApagado,
                modifier = Modifier.size(Icono.m),
            )
        }
        if (conRotulo) {
            Text(
                text = label,
                // Un renglón siempre: un rótulo que se partiera en dos haría esa pestaña más alta que
                // las otras y correría su ícono hacia arriba. Ver [destinosPrincipales].
                maxLines = 1,
                style = estiloDelRotulo(isActive),
                color = if (isActive) Movi.colores.texto else Movi.colores.textoApagado,
            )
        } else {
            Spacer(Modifier.height(with(LocalDensity.current) { estiloDelRotulo(false).lineHeight.toDp() }))
        }
    }
}

/** El estilo del rótulo de una pestaña: el `rotulo` del sistema, más firme si está activa. */
@Composable
private fun estiloDelRotulo(activo: Boolean): TextStyle = Movi.textos.rotulo.copy(
    letterSpacing = 0.2.sp,
    fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal,
)

/**
 * **¿Entran los cinco rótulos enteros en [ancho]?** Cada pestaña ocupa lo más ancho entre su rótulo
 * ([anchosDeRotulos], medidos con el motor de texto) y la píldora del ícono, más su relleno; el «+»,
 * su botón. Si no entran, la barra muestra solo el rótulo de la activa (ver [MinBottomNav]).
 * Pura, para probar el umbral sin montar la barra.
 */
fun rotulosQueEntran(anchosDeRotulos: List<Dp>, ancho: Dp): Boolean {
    val pildora = Icono.m + Espacio.s * 2
    var suma = Medida.botonFlotante
    anchosDeRotulos.forEach { rotulo -> suma += maxOf(rotulo, pildora) + Espacio.xxs * 2 }
    return suma <= ancho
}
