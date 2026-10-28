package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import com.jvillada.movi.ui.components.ds.Encabezado
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab

/**
 * Qué va a la izquierda del título (F60):
 * - [Avatar] en las pantallas RAÍZ (Inicio, Actividad y Pagos; «Tú» no lo lleva: ya es Tú).
 * - [Back] en las SUBPANTALLAS (todo lo que se abre desde una raíz): la flecha usa la pila
 *   real (F22) y cae a [fallback] si no hay historial (deep link, recarga de la web).
 */
sealed class HeaderLeading {
    /**
     * El avatar **abre «Tú»** (`Screen.Tu`), siempre. Vuelta 38: lo que abría (Ajustes) se mudó a la
     * pestaña Tú, y el avatar lleva ahí. Por eso recibe el `onNavigate` de la pantalla y no un
     * `onClick` suelto: no hay forma de armar un avatar que lleve a otro lado, y las pestañas no
     * pueden volver a discrepar sobre adónde lleva.
     */
    data class Avatar(val onNavigate: (Screen) -> Unit) : HeaderLeading()
    data class Back(val fallback: Screen) : HeaderLeading()

    /**
     * Nada a la izquierda del título. Ola W2: el detalle de un período, cuando va al lado de su
     * lista en la web, no tiene a dónde volver — la lista ya está a la vista —, así que no lleva la
     * flecha. La lista, a su izquierda, conserva la suya.
     */
    data object Ninguno : HeaderLeading()
}

/**
 * Regla única para el leading (revisión Ola 7): una pantalla lleva avatar solo cuando ES la
 * pantalla principal de una pestaña; si no, flecha atrás hacia [fallback]. Ola C: la barra del
 * teléfono y el rail muestran las mismas cuatro pestañas, así que la regla ya no depende del ancho
 * — Presupuestos (Pagos) y Créditos (Tú) llevan flecha en los dos. Vuelta 38: «Tú» no lleva nada (el
 * avatar llevaría a sí misma; su tarjeta de arriba ya es el avatar).
 */
fun leadingFor(screen: Screen, onNavigate: (Screen) -> Unit, fallback: Screen): HeaderLeading {
    if (screen == Screen.Tu) return HeaderLeading.Ninguno
    val tab = navTabFor(screen) ?: return HeaderLeading.Back(fallback)
    return if (screenForTab(tab) == screen) HeaderLeading.Avatar(onNavigate) else HeaderLeading.Back(fallback)
}

/**
 * F60: el encabezado único de TODAS las pantallas. Antes cada una armaba su propio Row
 * (26.sp acá, 17.sp allá, flecha `ArrowBack` o `ArrowBackIosNew`, con o sin avatar…) y el
 * dueño lo notó. Un solo componente: leading a la izquierda, título con el MISMO rótulo que
 * el menú y acción propia a la derecha si la hay.
 *
 * **Ola R0:** delega en [com.jvillada.movi.ui.components.ds.Encabezado], que le pone el alto
 * (`Medida.altoDeEncabezado`, 56), los márgenes (`Medida.margenDePantalla`, 16) y la flecha en un
 * área de toque de 48. Ya no lleva la línea de abajo: superficies por capas, sin bordes duros.
 *
 * @param subtitle línea secundaria opcional (p.ej. «3 nuevas · 2 coincidencias» al revisar un extracto).
 * @param action   slot a la derecha para íconos (lupa, campana), una píldora o «Actualizando…»;
 *                 null si no hay nada de eso.
 * @param accion   la acción principal de la pantalla («Nueva cuenta»): siempre con la forma de
 *                 [com.jvillada.movi.ui.components.ds.AccionDelEncabezado] (vuelta de diseño 4).
 */
@Composable
fun MinScreenHeader(
    title: String,
    leading: HeaderLeading,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    accion: AccionDeEncabezado? = null,
) {
    val goBack = LocalGoBack.current
    Encabezado(
        titulo = title,
        subtitulo = subtitle,
        onVolver = (leading as? HeaderLeading.Back)?.let { back -> { goBack(back.fallback) } },
        inicio = (leading as? HeaderLeading.Avatar)?.let { avatar ->
            { AvatarButton(onClick = { avatar.onNavigate(Screen.Tu) }) }
        },
        acciones = action,
        accion = accion,
    )
}
