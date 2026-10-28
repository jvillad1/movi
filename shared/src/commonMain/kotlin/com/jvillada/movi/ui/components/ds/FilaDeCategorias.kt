package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/*
 * # «Categoría ……… Todas ›» y las pastillas: un solo componente (vuelta 46)
 *
 * La vuelta 34 dejó en Agregar el patrón de elegir categoría: el rótulo «Categoría» con **«Todas ›»
 * en su mismo renglón** —siempre a la vista, también en el teléfono— y debajo las pastillas de las
 * frecuentes con la elegida marcada y «Nueva» primera. Revisar aviso lo copió con «Todas ›» al final
 * de las pastillas, donde en el teléfono había que deslizar para encontrarlo (revisión del 28-oct,
 * r44-08). Ahora es este componente, y lo usan Agregar, Revisar aviso, la tarjeta de Por revisar,
 * «Repartir este gasto» y «Pagó otro».
 */

/** El rótulo del renglón. */
const val ROTULO_DE_LA_FILA_DE_CATEGORIAS: String = "Categoría"

/** «Todas ›», a la derecha del rótulo: abre el selector entero. */
const val TODAS_LAS_CATEGORIAS_EN_LA_FILA: String = "Todas"

/** Dónde va la fila: en una superficie de lista (pone el margen) o adentro de algo que ya lo tiene. */
enum class RellenoDeLaFilaDeCategorias { Lista, Ninguno }

/** Los `testTag` de la fila, para que cada pantalla conserve los suyos. */
data class TagsDeLaFilaDeCategorias(
    val encabezado: String = "categorias:encabezado",
    val todas: String = "categorias:todas",
    val nueva: String = "categorias:nueva",
    val pastillas: String? = null,
    val pastilla: (String) -> String? = { null },
)

/**
 * **Elegir la categoría** como en Agregar: «Categoría … [sugerencia] … Todas ›» en un renglón de 48 y
 * debajo las pastillas —«Nueva» primera (si hay [onNueva]), después [categorias]—, con la [elegida]
 * marcada y siempre a la vista: si queda fuera del desplazamiento, la fila se corre lo justo, solo en
 * horizontal (un `bringIntoView` correría también la hoja bajo el dedo).
 *
 * @param sugerencia «Movi la reconoce: …», entre el rótulo y «Todas ›»: es lo único que cede.
 */
@Composable
fun FilaDeCategorias(
    categorias: List<String>,
    elegida: String,
    onElegir: (String) -> Unit,
    onTodas: () -> Unit,
    modifier: Modifier = Modifier,
    onNueva: (() -> Unit)? = null,
    sugerencia: String? = null,
    relleno: RellenoDeLaFilaDeCategorias = RellenoDeLaFilaDeCategorias.Lista,
    tags: TagsDeLaFilaDeCategorias = TagsDeLaFilaDeCategorias(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EncabezadoDeLaFilaDeCategorias(sugerencia, onTodas, relleno, tags)
        PastillasDeLaFilaDeCategorias(categorias, elegida, onElegir, onNueva, relleno, tags)
    }
}

@Composable
private fun EncabezadoDeLaFilaDeCategorias(
    sugerencia: String?,
    onTodas: () -> Unit,
    relleno: RellenoDeLaFilaDeCategorias,
    tags: TagsDeLaFilaDeCategorias,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (relleno == RellenoDeLaFilaDeCategorias.Lista) {
                    Modifier.padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla - Espacio.xs)
                } else {
                    Modifier
                },
            )
            .testTag(tags.encabezado),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Text(ROTULO_DE_LA_FILA_DE_CATEGORIAS, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1, softWrap = false)
        Text(
            text = sugerencia.orEmpty(),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .heightIn(min = Medida.objetivoTactil)
                .clip(RoundedCornerShape(Radio.s))
                .clickable(role = Role.Button, onClickLabel = "Ver todas las categorías", onClick = onTodas)
                .padding(horizontal = Espacio.xs)
                .testTag(tags.todas),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(TODAS_LAS_CATEGORIAS_EN_LA_FILA, style = Movi.textos.cuerpo, color = Movi.colores.enlace, maxLines = 1, softWrap = false)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Movi.colores.enlace,
                modifier = Modifier.size(Icono.m),
            )
        }
    }
}

@Composable
private fun PastillasDeLaFilaDeCategorias(
    categorias: List<String>,
    elegida: String,
    onElegir: (String) -> Unit,
    onNueva: (() -> Unit)?,
    relleno: RellenoDeLaFilaDeCategorias,
    tags: TagsDeLaFilaDeCategorias,
) {
    val desplazamiento = rememberScrollState()
    // Dónde quedó cada pastilla, en píxeles de la fila (lo que no cambia al desplazar).
    val lugares = remember { mutableStateMapOf<String, IntRange>() }
    val margen = if (relleno == RellenoDeLaFilaDeCategorias.Lista) {
        with(LocalDensity.current) { Medida.margenDePantalla.roundToPx() }
    } else {
        0
    }
    val reducido = LocalMovimientoReducido.current
    val lugarDeLaElegida = lugares[elegida]
    LaunchedEffect(elegida, lugarDeLaElegida, desplazamiento.viewportSize) {
        val lugar = lugarDeLaElegida ?: return@LaunchedEffect
        val ventana = desplazamiento.viewportSize
        if (ventana <= 0) return@LaunchedEffect
        // `lugar` se mide adentro del margen de la fila; en el desplazamiento, el margen va antes.
        val izquierda = lugar.first + margen
        val derecha = lugar.last + margen
        val desde = desplazamiento.value
        val destino = when {
            izquierda < desde -> izquierda - margen
            derecha > desde + ventana -> derecha - ventana + margen
            else -> return@LaunchedEffect
        }.coerceIn(0, desplazamiento.maxValue)
        if (reducido) desplazamiento.scrollTo(destino) else desplazamiento.animateScrollTo(destino)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeFilaDePastillas)
            .then(if (tags.pastillas != null) Modifier.testTag(tags.pastillas) else Modifier)
            .horizontalScroll(desplazamiento)
            .then(if (relleno == RellenoDeLaFilaDeCategorias.Lista) Modifier.padding(horizontal = Medida.margenDePantalla) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        if (onNueva != null) {
            PildoraDeAccion(
                texto = "Nueva",
                onClick = onNueva,
                icono = Icons.Rounded.Add,
                descripcion = "Nueva categoría",
                modifier = Modifier.testTag(tags.nueva),
            )
        }
        categorias.forEach { nombre ->
            val tag = tags.pastilla(nombre)
            PildoraDeFiltro(
                texto = nombre,
                seleccionada = nombre == elegida,
                onClick = { onElegir(nombre) },
                alto = AltoDeFiltro.Compacto,
                modifier = Modifier
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                    .onPlaced { lugar ->
                        val x = lugar.positionInParent().x.toInt()
                        lugares[nombre] = x..(x + lugar.size.width)
                    },
            )
        }
    }
}
