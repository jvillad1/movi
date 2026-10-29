package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.platform.testTag
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
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.anilloDeFoco

/**
 * Contraparte de [MinBottomNav] desde 600 dp: un rail a la izquierda, pintado una vez en la raíz de
 * App.kt — ancho (216 dp) en escritorio y [compacto] (80 dp) en una ventana mediana. Vuelta 38:
 * muestra **las mismas cuatro pestañas** que la barra del teléfono ([destinosPrincipales]: Inicio ·
 * Actividad · Pagos · Tú), debajo **Movi AI** ([destinoMoviAi]), que solo el rail tiene, y
 * «Agregar» con su menú: en el escritorio no hay un Inicio siempre a la vista con sus acciones, y el
 * teclado físico de Agregar (vuelta 34) se usa ahí.
 *
 * **Un solo amarillo lleno**: «Agregar». El destino activo del rail ancho va en el amarillo suave
 * (`tinte`, `#FFF2C6`) con la tinta encima; el del rail compacto, con la píldora chica detrás del
 * ícono, como la barra.
 */
@Composable
fun MinNavRail(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
    compacto: Boolean = false,
    /** Ola 5: mantener presionado el «+» abre sus atajos (ver [BotonDeAgregarConAtajos]). */
    onAtajo: ((com.jvillada.movi.ui.quickadd.AtajoDeAgregar) -> Unit)? = null,
) {
    if (compacto) {
        RailCompacto(active, onTabSelected, onAtajo)
        return
    }
    Column(
        modifier = Modifier
            .width(ANCHO_DEL_RAIL_ANCHO)
            .fillMaxHeight()
            .testTag(TAG_RAIL_DE_NAVEGACION)
            .background(Movi.colores.tarjeta)
            .windowInsetsPadding(barrasDelSistemaDelRail)
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.margen),
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        Text(
            "movi",
            // Tamaño suelto a propósito: es el logotipo del riel, no un título; ningún estilo de la escala es ese papel.
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            // En tinta: el amarillo de la marca no se lee como letra sobre el blanco (vuelta 29).
            color = Movi.colores.texto,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = Espacio.s, bottom = Espacio.m),
        )

        destinosDelRail.forEach { dest ->
            RailItem(dest.tab, dest.label, dest.icon, active, onTabSelected)
        }

        Spacer(Modifier.height(Espacio.s))

        // Primary action — mirrors the bottom-nav center FAB (y sus atajos, Ola 5). Vuelta de diseño
        // 26: con la flecha «▾» al final, que abre los atajos de un clic (en el escritorio mantener
        // presionado con el mouse no es natural).
        BotonDeAgregarConAtajos(
            alTocar = { onTabSelected(NavTab.ADD) },
            alElegirAtajo = onAtajo,
            conFlecha = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radio.pill))
                .background(Movi.colores.marca),
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            modifier = Modifier
                .fillMaxWidth()
                .height(Medida.objetivoTactil)
                .padding(horizontal = Espacio.m),
        ) {
            // El mismo lila oscuro a mano que estaba en la barra del teléfono, repetido dos veces
            // más acá. Tres copias de un color sin nombre: ahora es `sobreMarca`.
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Movi.colores.sobreMarca,
                modifier = Modifier.size(Icono.m),
            )
            Text(
                "Agregar",
                style = Movi.textos.cuerpoFuerte,
                color = Movi.colores.sobreMarca,
            )
        }
        }
    }
}

/**
 * Las barras del sistema que el rail descuenta: arriba (la de estado, que si no tapa el «+»), abajo y
 * al costado del rail (la de navegación de Android en horizontal). En la web son cero.
 */
private val barrasDelSistemaDelRail: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/** El `testTag` del rail (ancho y compacto). */
const val TAG_RAIL_DE_NAVEGACION: String = "rail-de-navegacion"

/** El ancho del rail compacto de una ventana mediana. */
val ANCHO_DEL_RAIL_COMPACTO = 80.dp

/** El ancho del rail de escritorio (expandido). Lo descuenta [anchoDelPanelEnLaCascara]. */
val ANCHO_DEL_RAIL_ANCHO = 216.dp

/**
 * Ola W1: el rail de una ventana **mediana** (600–999 dp: tablet vertical, teléfono horizontal,
 * ventana angosta de escritorio). 80 dp: el «+» arriba —es lo que más se usa— y debajo cada pestaña
 * con su ícono y su rótulo corto ([DestinoPrincipal.rotuloCorto]). El rail ancho de 216 dp se come
 * un tercio de una tablet vertical; la barra inferior, estirada a 800 dp, deja los destinos lejos
 * del pulgar y del mouse.
 */
@Composable
private fun RailCompacto(
    active: NavTab?,
    onTabSelected: (NavTab) -> Unit,
    onAtajo: ((com.jvillada.movi.ui.quickadd.AtajoDeAgregar) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .testTag(TAG_RAIL_DE_NAVEGACION)
            .background(Movi.colores.tarjeta)
            // Afuera del ancho: la barra de navegación en horizontal (a la izquierda) ensancha el
            // fondo en vez de comerse los 80 dp del rail.
            .windowInsetsPadding(barrasDelSistemaDelRail)
            .width(ANCHO_DEL_RAIL_COMPACTO)
            .padding(vertical = Movi.espacios.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        BotonDeAgregarConAtajos(
            alTocar = { onTabSelected(NavTab.ADD) },
            alElegirAtajo = onAtajo,
            modifier = Modifier
                .size(Medida.botonFlotante)
                .clip(CircleShape)
                .background(Movi.colores.marca),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Agregar",
                tint = Movi.colores.sobreMarca,
                modifier = Modifier.size(Icono.l),
            )
        }
        Spacer(Modifier.height(Espacio.s))
        destinosDelRail.forEach { dest ->
            RailCompactoItem(dest, active, onTabSelected)
        }
    }
}

@Composable
private fun RailCompactoItem(dest: DestinoPrincipal, active: NavTab?, onTabSelected: (NavTab) -> Unit) {
    val isActive = dest.tab == active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
        modifier = Modifier
            .fillMaxWidth()
            .anilloDeFoco(RoundedCornerShape(Radio.m), adentro = true)
            .clickable { onTabSelected(dest.tab) }
            // Un lector de pantalla dice el nombre entero UNA vez («Movimientos»), no el ícono y
            // después el rótulo corto («Movimientos, Movs»).
            .clearAndSetSemantics {
                contentDescription = dest.label
                role = Role.Tab
                selected = isActive
                onClick { onTabSelected(dest.tab); true }
            }
            .padding(horizontal = RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO, vertical = Espacio.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // La misma píldora amarilla de 56 × 30 que la barra del teléfono (vuelta 29).
                .size(Medida.anchoDePildoraDePestana, Medida.altoDePildoraDePestana)
                .clip(RoundedCornerShape(Movi.formas.pleno))
                .background(if (isActive) Movi.colores.marca else Color.Transparent),
        ) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                tint = if (isActive) Movi.colores.sobreMarca else Movi.colores.textoMedio,
                modifier = Modifier.size(Icono.m),
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
 * la barra del teléfono; ahí sobra lugar, acá no (con «Actividad» ya no hace falta, pero un rótulo
 * nuevo no tiene por qué saberlo).
 */
@Composable
internal fun RotuloDelRailCompacto(texto: String, activo: Boolean, modifier: Modifier = Modifier) {
    val estilo = (if (activo) Movi.textos.pestanaActiva else Movi.textos.pestana).copy(
        color = if (activo) Movi.colores.texto else Movi.colores.textoMedio,
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
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medida.objetivoTactil)
            // La activa, en el amarillo SUAVE con la tinta encima (vuelta 38): el amarillo lleno es
            // solo de «Agregar», y dos bloques amarillos iguales competían en el rail.
            .anilloDeFoco(RoundedCornerShape(Radio.pill))
            .clip(RoundedCornerShape(Radio.pill))
            .background(fondoDelDestinoDelRailAncho(isActive, Movi.colores))
            // Revisión de la vuelta 38: sobre el activo, el velo del hover (la tinta con alfa) volvía el
            // amarillo suave un beige oliva. El activo no lleva hover —ya dice dónde estás—; los demás sí.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (elHoverSeVeEnElDestino(isActive)) LocalIndication.current else null,
            ) { onTabSelected(tab) }
            .padding(horizontal = Espacio.s),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Movi.colores.sobreMarca else Movi.colores.textoMedio,
            modifier = Modifier.size(Icono.m),
        )
        Text(
            text = label,
            style = if (isActive) Movi.textos.cuerpoFuerte else Movi.textos.cuerpoRegular,
            color = if (isActive) Movi.colores.texto else Movi.colores.textoMedio,
        )
    }
}

/**
 * **El fondo de un destino del rail ancho** (vuelta 38): el activo en el amarillo suave ([ColoresDeMovi.tinte],
 * `#FFF2C6`), los demás sin fondo. El amarillo lleno ([ColoresDeMovi.marca]) es solo de «Agregar»: con
 * los dos en la marca, dos bloques amarillos iguales competían en el rail.
 */
fun fondoDelDestinoDelRailAncho(activo: Boolean, colores: ColoresDeMovi): Color =
    if (activo) colores.tinte else Color.Transparent

/**
 * **¿El destino del rail ancho muestra el velo del hover (y el de la pulsación)?** Solo el que no está
 * activo: sobre el amarillo suave del activo, el velo en tinta lo ensuciaba (beige oliva).
 */
fun elHoverSeVeEnElDestino(activo: Boolean): Boolean = !activo
