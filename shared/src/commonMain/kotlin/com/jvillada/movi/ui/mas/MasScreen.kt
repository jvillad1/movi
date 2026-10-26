package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.ai.TITULO_LO_QUE_MOVI_SABE
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.ds.Accion
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.CuadriculaDeAcciones
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.profile.BORRAR_MI_CUENTA
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco

/**
 * Una ficha de Ajustes. Ola R4: se pinta con [AccionRapida][com.jvillada.movi.ui.components.ds.AccionRapida]
 * del sistema —el mismo círculo, ícono y rótulo de dos renglones que la cuadrícula del Inicio—, así
 * que ya no lleva un color propio por ficha: la marca es acción, y todas las fichas son acciones.
 */
private data class MasItem(
    val label: String,
    val icon: ImageVector,
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
    MasItem("Perfil",       Icons.Rounded.ManageAccounts, Screen.Profile),
    // Ola 10: la única puerta a «Categorías» (además del acceso de cada campo de categoría).
    MasItem("Categorías",   Icons.AutoMirrored.Rounded.Label, Screen.Categorias),
    // Ola 18: los papeles. Ola B, tarea 7: absorbió a «Extractos» — el importador archiva ahí
    // lo que pasa por él, y ahora también «Importar movimientos» vive en cada fila de acá.
    MasItem("Documentos",   Icons.Rounded.Folder, Screen.Documentos),
    // Ola R4: una de las dos puertas a «Personas y comercios» (la otra es la fila al final de
    // Espacios), con el mismo nombre que el título de la pantalla.
    MasItem(PERSONAS_Y_COMERCIOS, Icons.Rounded.Groups, Screen.Destinos()),
    // Compartir con un tercero: el enlace de solo lectura para Caro o un asesor. Tiene además un
    // ícono en el encabezado del Hoy. El rótulo es el título de la pantalla, como en toda ficha.
    MasItem("Compartir",    Icons.Rounded.Share, Screen.Compartir),
    MasItem("Movi AI",      Icons.Rounded.AutoAwesome, Screen.AIChat()),
    // Ola 3: lo que Movi AI recuerda de ti, para verlo, corregirlo y borrarlo. Junto al asistente.
    MasItem(TITULO_LO_QUE_MOVI_SABE, Icons.Rounded.Psychology, Screen.LoQueMoviSabe),
    // Ola 7: mismo rótulo que el encabezado de la pantalla (título = rótulo del menú). Ola C: lo
    // pendiente se mudó a «Por revisar» (Movimientos) y acá quedó la configuración de la captura y
    // el historial — «Captura del banco» en Android, «Mensajes del banco» donde no hay captura.
    MasItem(tituloDeCapturaDelBanco, Icons.Rounded.Sms, Screen.CapturaDelBanco),
    // Ola 14: la guía de arranque, que se apaga sola en el Inicio y hasta acá no tenía forma de
    // volver a abrirse. Solo se ofrece mientras le quede algo por tildar (ver [MasScreen]).
    MasItem("Primeros pasos", Icons.Rounded.Checklist, Screen.PrimerosPasos),
    // Ola B, tarea 7: «Metas» y «Extractos» salieron del mosaico; en la R4 se borraron (los destinos
    // SDUI `"goals"` y `"extractos"` llevan a Patrimonio y a Documentos).
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones),
        ) {
            // Ola R4: las fichas son la cuadrícula de acciones del sistema (cuatro columnas, el
            // círculo de 56 y el rótulo de dos renglones), la misma del Inicio. Antes eran tarjetas
            // con un color por ficha y un radio y un relleno propios.
            CuadriculaDeAcciones(visibleItems.map { item -> Accion(item.label, item.icon, onClick = { onNavigate(item.screen) }) })
            // Abajo de todo, separado de las fichas: se usa una vez, si es que se usa, y no puede
            // quedar a un toque distraído de «Categorías». Es un botón de peligro del sistema:
            // el color de lo que sale sobre su lavado, nunca lleno de rojo.
            Boton(
                texto = BORRAR_MI_CUENTA,
                onClick = { onNavigate(Screen.BorrarLaCuenta) },
                variante = VarianteDeBoton.Peligro,
                anchoCompleto = true,
                modifier = Modifier.padding(top = Medida.entreSecciones),
            )
        }
    }
}
