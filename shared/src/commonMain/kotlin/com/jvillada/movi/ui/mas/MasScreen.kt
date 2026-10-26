package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.ai.TITULO_LO_QUE_MOVI_SABE
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco
import com.jvillada.movi.ui.profile.BORRAR_MI_CUENTA

/**
 * Una ficha de Ajustes. [color] es una clave del catálogo de colores de categoría
 * (`COLORES_DEL_CATALOGO`): el ícono va en ese color sobre su círculo, en los dos temas. Ola R0:
 * eran nueve pares de colores escritos a mano, pensados solo para el tema oscuro.
 */
private data class MasItem(
    val label: String,
    val icon: ImageVector,
    val color: String,
    val screen: Screen,
)

/**
 * Las fichas de **Ajustes**, en orden.
 *
 * Ola C (2026-09): «Más» dejó de ser pestaña y pasó a ser Ajustes, la pantalla que abre el avatar.
 * Salió de acá todo lo que ahora es (o vive en) una pestaña: Cuentas, el cuadre de saldos, Créditos
 * y «Cuentas de otros» son Patrimonio; Presupuestos es Plan. Quedaron las cosas que se ajustan de
 * vez en cuando, no las que se miran todos los días.
 */
private val items = listOf(
    // F40: "Análisis" no analizaba — era un índice con cifras, y eso ahora es el Inicio. Perfil va
    // primero: es lo que el avatar abría hasta la ola C, y quien lo toca por costumbre lo busca.
    MasItem("Perfil",       Icons.Rounded.ManageAccounts,   "azul", Screen.Profile),
    // Ola 10: la única puerta a «Categorías» (además del acceso de cada campo de categoría).
    MasItem("Categorías",   Icons.AutoMirrored.Rounded.Label, "verde", Screen.Categorias),
    // Ola 18: los papeles. Ola B, tarea 7: absorbió a «Extractos» — el importador archiva ahí
    // lo que pasa por él, y ahora también «Importar movimientos» vive en cada fila de acá.
    MasItem("Documentos",   Icons.Rounded.Folder,           "azul", Screen.Documentos),
    // Ola V: una puerta secundaria a «Personas y comercios» (la principal es Movimientos desde el
    // 4-oct-2026), con el mismo nombre que el título de la pantalla.
    MasItem(PERSONAS_Y_COMERCIOS, Icons.Rounded.Groups,     "naranja", Screen.Destinos()),
    // Compartir con un tercero: el enlace de solo lectura para Caro o un asesor. Tiene además un
    // ícono en el encabezado del Hoy. El rótulo es el título de la pantalla, como en toda ficha.
    MasItem("Compartir",    Icons.Rounded.Share,            "verde", Screen.Compartir),
    MasItem("Movi AI",      Icons.Rounded.AutoAwesome,      "violeta", Screen.AIChat()),
    // Ola 3: lo que Movi AI recuerda de ti, para verlo, corregirlo y borrarlo. Junto al asistente.
    MasItem(TITULO_LO_QUE_MOVI_SABE, Icons.Rounded.Psychology, "violeta", Screen.LoQueMoviSabe),
    // Ola 7: mismo rótulo que el encabezado de la pantalla (título = rótulo del menú). Ola C: lo
    // pendiente se mudó a «Por revisar» (Movimientos) y acá quedó la configuración de la captura y
    // el historial — «Captura del banco» en Android, «Mensajes del banco» donde no hay captura.
    MasItem(tituloDeCapturaDelBanco, Icons.Rounded.Sms,           "celeste", Screen.CapturaDelBanco),
    // Ola 14: la guía de arranque, que se apaga sola en el Inicio y hasta acá no tenía forma de
    // volver a abrirse. Solo se ofrece mientras le quede algo por tildar (ver [MasScreen]).
    MasItem("Primeros pasos", Icons.Rounded.Checklist,      "ambar", Screen.PrimerosPasos),
    // Ola B, tarea 7: «Metas» y «Extractos» salieron del mosaico. Ninguna de las dos pantallas se
    // borró —siguen ahí, solo sin puerta— así que no hay nada que restaurar si el dueño las extraña.
)

/** El título de la pantalla que abre el avatar. Ver [MasScreen]. */
const val TITULO_DE_AJUSTES: String = "Ajustes"

/**
 * **Ajustes** — la pantalla que abre el avatar de cualquier pestaña (ver `HeaderLeading.Avatar`).
 *
 * Sigue llamándose `MasScreen` / `Screen.Mas` porque el destino SDUI `"mas"` y las pilas ya lo
 * nombran así; lo que el dueño ve es «Ajustes». No es una pestaña, así que lleva flecha y no avatar:
 * se vuelve a donde se estaba, o al Hoy si no hay historial.
 */
@Composable
fun MasScreen(onNavigate: (Screen) -> Unit) {
    // F47 · F48: "Editor de pantallas" vivía acá, agregado a la grilla después de que
    // isScreenAdmin() resolvía — eso hacía que la grilla "saltara" al cargar. Se mudó al final de
    // Perfil, en una sección "Administración" visible solo para quien administra el Inicio.

    // Ola B, tarea 7: «Primeros pasos» solo se ofrece mientras la guía del Inicio tenga algo por
    // tildar — la MISMA condición que decide si el Inicio la pinta (`DashboardData.guiaIncompleta`,
    // reusada y no copiada). Se lee de `DashboardDataCache` —lo mismo que ya hacen
    // `PrimerosPasosScreen` y `AIChatScreen`— en vez de pedir las diez respuestas del Inicio: si
    // todavía no cargó (`data == null`), `guiaIncompleta` da `false` —mismo defecto que
    // `puedeAfirmarVacio`— así que la ficha no se ofrece con datos que Ajustes nunca pidió.
    //
    // Ola C: ya no se filtra lo que el rail muestra en pantalla ancha — nada de esta lista es una
    // pestaña, así que el teléfono y la web ven las mismas fichas.
    val guiaIncompleta = DashboardDataCache.data?.guiaIncompleta == true
    val visibleItems = remember(guiaIncompleta) {
        if (guiaIncompleta) items else items.filterNot { it.screen == Screen.PrimerosPasos }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Movi.colores.fondo),
    ) {
        MinScreenHeader(
            title = TITULO_DE_AJUSTES,
            leading = HeaderLeading.Back(fallback = Screen.Dashboard),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(Movi.espacios.amplio),
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            modifier = Modifier.weight(1f),
        ) {
            items(visibleItems) { item ->
                MasCard(item, onNavigate)
            }
            // Abajo de todo y a lo ancho, separado de las fichas: se usa una vez, si es que se usa,
            // y no puede quedar a un toque distraído de «Categorías».
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilaBorrarLaCuenta(onNavigate)
            }
        }
    }
}

@Composable
private fun MasCard(item: MasItem, onNavigate: (Screen) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
        modifier = Modifier
            // Ola 8 · V13: sin `fillMaxWidth` cada ficha medía lo que midiera su rótulo, así
            // que la fila quedaba con tarjetas de anchos distintos.
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.amplia))
            .background(Movi.colores.tarjeta)
            .clickable { onNavigate(item.screen) }
            .padding(vertical = Movi.espacios.amplio, horizontal = Movi.espacios.corto),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Movi.formas.normal))
                .background(Movi.colores.circuloDeCategoria(item.color)),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = Movi.colores.categoria(item.color),
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.label,
            // `apoyo` y no un `fontSize` suelto, y no por prolijidad: sin estilo, el texto
            // heredaba el alto de línea de `bodyLarge`, 24 sp para una letra de 11. Con los dos
            // renglones reservados de abajo, cada ficha llevaba 48 sp de rótulo: «Cuentas» quedaba
            // flotando con un renglón vacío abajo y «Mensajes del banco» con los dos renglones
            // separados como en un poema. Visto en la web a 390 dp.
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.textoMedio,
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

/**
 * «Borrar mi cuenta», al pie de Ajustes. Texto en el color de lo que sale y sin ficha: no es un
 * ajuste más, es la salida. Abre [Screen.BorrarLaCuenta], que primero ofrece descargar los datos.
 */
@Composable
private fun FilaBorrarLaCuenta(onNavigate: (Screen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Movi.espacios.seccion)
            .clip(RoundedCornerShape(Movi.formas.normal))
            .clickable { onNavigate(Screen.BorrarLaCuenta) }
            .padding(vertical = Movi.espacios.amplio),
        contentAlignment = Alignment.Center,
    ) {
        Text(BORRAR_MI_CUENTA, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.sale)
    }
}
