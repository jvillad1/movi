package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra

/**
 * # Lo que cuelga de una fila (ola R4)
 *
 * Las filas de Plan, de Créditos y de un período pasado no son solo título, subtítulo y monto: una
 * fila del checklist ofrece «Anotar este pago», una suscripción «Editar» y «Quitar», una cuota trae
 * la estimación de cuánto es interés. Esas cosas van **debajo** de la [com.jvillada.movi.ui.components.ds.Fila],
 * alineadas con su texto —ni con el avatar ni con el borde—, para que se lean como algo de esa fila
 * y no como una fila más. Antes cada pantalla elegía su sangría (32, 40, 48 dp); ahora es una.
 */

/** Dónde empieza el texto de una [com.jvillada.movi.ui.components.ds.Fila] con inicio. */
val SANGRIA_DEL_TEXTO_DE_LA_FILA = Medida.margenDePantalla + Medida.avatar + Espacio.s

/**
 * **Lo que cuelga de una fila**: alineado con su texto, con [Medida.margenDePantalla] a la derecha y
 * [Espacio.xs] entre una cosa y la siguiente. Sin nada adentro no ocupa lugar más que su aire de
 * abajo, así que quien llama decide si lo pone.
 */
@Composable
fun BajoLaFila(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SANGRIA_DEL_TEXTO_DE_LA_FILA, end = Medida.margenDePantalla, bottom = Espacio.s),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        content = contenido,
    )
}

/**
 * **Un monto ya escrito**, con la letra de [VarianteDeCifra.Fila]: para los que la `Cifra` del sistema
 * no sabe decir —«−US$12», «−$369.900 al año», un saldo de tarjeta que se dice como saldo—. El texto
 * llega armado (y con «Ocultar los saldos» ya aplicado por `formatCOP` / `formatMoney`).
 */
@Composable
fun MontoDeFila(
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = Movi.colores.texto,
    variante: VarianteDeCifra = VarianteDeCifra.Fila,
) {
    Text(
        text = texto,
        style = estiloDeLaCifra(variante, Movi.textos),
        color = color,
        maxLines = 1,
        modifier = modifier.testTag(TAG_CIFRA),
    )
}

/**
 * **Un número en el círculo del inicio de una fila**: el día del cobro de una suscripción, el orden
 * de una deuda en el plan de salida. Mide [Medida.avatar], en la capa 2, con el número en `monto`.
 */
@Composable
fun NumeroEnCirculo(numero: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(Medida.avatar)
            .background(Movi.colores.capa2, CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(text = "$numero", style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
    }
}
