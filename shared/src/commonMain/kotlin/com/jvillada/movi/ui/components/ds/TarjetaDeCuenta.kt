package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.LocalColores
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio

/** Tag estable de una [TarjetaDeCuenta]. */
const val TAG_TARJETA_DE_CUENTA: String = "ds-tarjeta-de-cuenta"

/** Tag estable del círculo de la marca dentro de una [TarjetaDeCuenta]. */
const val TAG_MARCA_DE_LA_TARJETA: String = "ds-marca-de-la-tarjeta"

/**
 * Cuánto del ancho de un carrusel ocupa una [TarjetaDeCuenta]: lo justo para que **la siguiente
 * asome** y se entienda que hay más deslizando (camino 1). Una fracción, no un ancho fijo: a 390 dp
 * son unas 290, y en una tableta no queda una tarjeta enana en medio del lienzo.
 */
const val FRACCION_DE_TARJETA_DE_CUENTA: Float = 0.82f

/**
 * Los colores de adentro de una tarjeta: los de la marca del banco (su fondo y el texto que pasa AA
 * contra él, de `MarcasConocidas`), o la capa 2 y el texto del tema si la cuenta no tiene marca. Pura.
 *
 * El texto de apoyo («Ahorros», «Saldo») va en el mismo color que el principal: la tarjeta es de un
 * color fuerte y un gris encima no pasaría el contraste con todas las marcas.
 */
fun coloresDeLaTarjetaDeCuenta(marca: Marca?, colores: ColoresDeMovi): ColoresDeMovi {
    if (marca == null) return colores.copy(tarjeta = colores.capa2)
    val texto = Color(marca.texto)
    return colores.copy(tarjeta = Color(marca.fondo), texto = texto, textoMedio = texto, textoApagado = texto)
}

/**
 * **La tarjeta de una cuenta** del carrusel «Tus cuentas» (camino 1): el color y la marca de su banco,
 * el nombre (en hasta dos renglones: nunca «Bancolombia Ah…»), el tipo («Ahorros», «Tarjeta») junto al
 * rótulo del saldo —salvo que el nombre ya lo diga—, y el saldo en [VarianteDeCifra.Grande], que se
 * achica antes que recortarse.
 *
 * - Alto [Medida.altoDeTarjetaDeCuenta], radio [Radio.xl], relleno [Medida.rellenoDeSuperficie], sin
 *   borde: la marca es el color de la superficie.
 * - Con [marca], el círculo de arriba va invertido —el texto de la marca de fondo y su color en la
 *   letra— para que se vea sobre el color del banco. Sin marca, el [icono] del tipo de cuenta en un
 *   [IconoEnCirculo] y la tarjeta en la capa 2.
 * - La [Cifra] lleva «Ocultar los saldos» incluido. El signo lo decide quien llama: una deuda se
 *   muestra en positivo con [rotuloDelSaldo] = «Debes».
 * - Toda la tarjeta se toca ([onClick]): abre el detalle de la cuenta.
 *
 * ```
 * TarjetaDeCuenta(
 *     nombre = "Bancolombia", tipo = "Ahorros", rotuloDelSaldo = "Saldo", saldo = 13_845_800,
 *     marca = marcaDelBanco("Bancolombia"), icono = Icons.Filled.AccountBalance,
 *     onClick = { abrir(cuenta) },
 *     modifier = Modifier.fillParentMaxWidth(FRACCION_DE_TARJETA_DE_CUENTA),
 * )
 * ```
 */
@Composable
fun TarjetaDeCuenta(
    nombre: String,
    /** «Ahorros», «Tarjeta»; `null` si el nombre ya lo dice (ver `tipoSiElNombreNoLoDice`). */
    tipo: String?,
    rotuloDelSaldo: String,
    saldo: Long,
    marca: Marca?,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    moneda: String = "COP",
) {
    val colores = coloresDeLaTarjetaDeCuenta(marca, Movi.colores)
    CompositionLocalProvider(LocalColores provides colores) {
        Column(
            modifier = modifier
                // Un mínimo, no un alto fijo: con la letra del sistema agrandada la tarjeta crece en vez de cortar.
                .heightIn(min = Medida.altoDeTarjetaDeCuenta)
                .clip(RoundedCornerShape(Radio.xl))
                .background(colores.tarjeta)
                .clickable(onClickLabel = "Ver $nombre", onClick = onClick)
                .testTag(TAG_TARJETA_DE_CUENTA)
                .padding(Medida.rellenoDeSuperficie),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                if (marca != null) MarcaInvertida(marca) else IconoEnCirculo(icono, tono = TonoDeIcono.Neutro)
                // Dos renglones: «Bancolombia Ahorros» se lee entero (la revisión de la R1 vio
                // «Bancolombia Ah…»). El «…» queda solo para un nombre que no entra ni en dos.
                Text(
                    text = nombre,
                    style = Movi.textos.titulo,
                    color = colores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                // El tipo baja junto al rótulo del saldo, para dejarle el renglón de arriba al nombre.
                // Sin tipo (el nombre ya lo dice: «Bancolombia Ahorros», vuelta de diseño 16) el
                // renglón queda con el rótulo solo: mide lo mismo, así que ninguna tarjeta del
                // carrusel o de la grilla cambia de alto.
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Text(
                        text = rotuloDelSaldo,
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = colores.textoMedio,
                        modifier = Modifier.weight(1f),
                    )
                    if (tipo != null) {
                        Text(text = tipo, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = colores.textoMedio, maxLines = 1)
                    }
                }
                Cifra(saldo, VarianteDeCifra.Grande, moneda = moneda)
            }
        }
    }
}

/** El logotipo tipográfico de la marca, invertido para ir encima de su propio color. */
@Composable
private fun MarcaInvertida(marca: Marca) {
    Box(
        modifier = Modifier
            .size(Medida.avatar)
            .background(Color(marca.texto), CircleShape)
            .semantics { contentDescription = marca.nombre }
            .testTag(TAG_MARCA_DE_LA_TARJETA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = marca.corta,
            style = Movi.textos.titulo,
            color = Color(marca.fondo),
            maxLines = 1,
            softWrap = false,
        )
    }
}
