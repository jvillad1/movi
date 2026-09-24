package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.screenForTab

/**
 * Qué va a la izquierda del título (F60):
 * - [Avatar] en las pantallas RAÍZ (las cuatro pestañas: Hoy, Movimientos, Plan y Patrimonio).
 * - [Back] en las SUBPANTALLAS (todo lo que se abre desde una raíz): la flecha usa la pila
 *   real (F22) y cae a [fallback] si no hay historial (deep link, recarga de la web).
 */
sealed class HeaderLeading {
    /**
     * El avatar **abre Ajustes** (`Screen.Mas`), siempre. Ola C: «Más» dejó de ser pestaña y el
     * avatar pasó a ser su puerta — Perfil, Categorías, Documentos, Compartir, Movi AI y los
     * mensajes del banco se alcanzan desde ahí. Por eso recibe el `onNavigate` de la pantalla y no
     * un `onClick` suelto: no hay forma de armar un avatar que lleve a otro lado, y las cuatro
     * pestañas no pueden volver a discrepar sobre adónde lleva (antes cada una decía Perfil a mano).
     */
    data class Avatar(val onNavigate: (Screen) -> Unit) : HeaderLeading()
    data class Back(val fallback: Screen) : HeaderLeading()
}

/**
 * Regla única para el leading (revisión Ola 7): una pantalla lleva avatar solo cuando ES la
 * pantalla principal de una pestaña; si no, flecha atrás hacia [fallback]. Ola C: la barra del
 * teléfono y el rail muestran las mismas cuatro pestañas, así que la regla ya no depende del ancho
 * — Presupuestos (Plan) y Créditos (Patrimonio) llevan flecha en los dos.
 */
fun leadingFor(screen: Screen, onNavigate: (Screen) -> Unit, fallback: Screen): HeaderLeading {
    val tab = navTabFor(screen) ?: return HeaderLeading.Back(fallback)
    return if (screenForTab(tab) == screen) HeaderLeading.Avatar(onNavigate) else HeaderLeading.Back(fallback)
}

/**
 * F60: el encabezado único de TODAS las pantallas. Antes cada una armaba su propio Row
 * (26.sp acá, 17.sp allá, flecha `ArrowBack` o `ArrowBackIosNew`, con o sin avatar…) y el
 * dueño lo notó. Un solo componente: leading a la izquierda, título con el MISMO rótulo que
 * el menú, acción propia a la derecha si la hay, y una Hairline debajo.
 *
 * @param subtitle línea secundaria opcional (p.ej. «3 nuevas · 2 coincidencias» al revisar un extracto).
 * @param action   slot a la derecha (NewItemButton, lupa, campana…); null si la pantalla no
 *                 tiene acción propia.
 */
@Composable
fun MinScreenHeader(
    title: String,
    leading: HeaderLeading,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    // La flecha lleva 11.dp de padding alrededor (área tocable ≈44dp); el padding horizontal
    // del Row se reduce en la misma medida para que el título no se corra respecto del avatar.
    val backInset = if (leading is HeaderLeading.Back) 11.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Movi.espacios.margen - backInset, end = Movi.espacios.margen)
            .padding(top = Movi.espacios.corto, bottom = Movi.espacios.medio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        when (leading) {
            is HeaderLeading.Avatar -> AvatarButton(onClick = { leading.onNavigate(Screen.Mas) })
            is HeaderLeading.Back -> {
                val goBack = LocalGoBack.current
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = Movi.colores.texto,
                    modifier = Modifier
                        .clickable { goBack(leading.fallback) }
                        .padding(11.dp)
                        .size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            // `titular` es 19.sp contra los 17 de antes, y semibold contra medium. El título de
            // la pantalla es el ancla de toda la jerarquía: si no gana, no hay jerarquía.
            Text(
                text = title,
                style = Movi.textos.titular,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) action()
    }
    Hairline()
}
