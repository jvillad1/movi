package com.jvillada.movi.ui.legal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.theme.Movi

/*
 * Vuelta 109 · **la privacidad y los términos, desde la app.** Las páginas las sirve el server
 * (`/privacidad`, `/terminos`, `/borrar-cuenta`: las URL que piden Google Play y el App Store); la app
 * solo las abre en el navegador con el `LocalUriHandler` de siempre. En la web `apiBaseUrl` es el
 * mismo origen; en el teléfono, producción.
 */

/** La política de privacidad (Ley 1581 de 2012). */
fun urlDePrivacidad(): String = "$apiBaseUrl/privacidad"

/** Los términos de uso. */
fun urlDeTerminos(): String = "$apiBaseUrl/terminos"

/** Los títulos de las filas de Tú → Cuenta. */
const val TITULO_PRIVACIDAD: String = "Privacidad"
const val TITULO_TERMINOS: String = "Términos de uso"

/** Lo que dice la línea del registro, sin los enlaces (para las pruebas). */
const val LINEA_DE_ACEPTACION: String = "Al crear tu cuenta aceptas los Términos y la Política de privacidad."

const val TAG_LINEA_DE_ACEPTACION: String = "registro:linea-de-aceptacion"

/**
 * **«Al crear tu cuenta aceptas los Términos y la Política de privacidad»**, debajo de «Crear cuenta».
 * Sin casilla obligatoria: crear la cuenta es aceptar, y la línea lo dice junto al botón. Los dos
 * nombres son enlaces (`LinkAnnotation.Url`: los abre el `LocalUriHandler`, también con el lector de
 * pantalla), en el color de los enlaces, nunca en el amarillo.
 */
@Composable
fun LineaDeAceptacion(modifier: Modifier = Modifier) {
    // El color de los enlaces y subrayado: sin cambiar el peso de la letra.
    val estilo = TextLinkStyles(SpanStyle(color = Movi.colores.enlace, textDecoration = TextDecoration.Underline))
    val texto = buildAnnotatedString {
        append("Al crear tu cuenta aceptas los ")
        withLink(LinkAnnotation.Url(urlDeTerminos(), estilo)) { append("Términos") }
        append(" y la ")
        withLink(LinkAnnotation.Url(urlDePrivacidad(), estilo)) { append("Política de privacidad") }
        append(".")
    }
    Text(
        texto,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag(TAG_LINEA_DE_ACEPTACION),
    )
}
