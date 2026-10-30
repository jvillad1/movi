package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
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
 * Los colores de adentro de una tarjeta: **los del tema, con o sin marca** (vuelta 29). La tarjeta es
 * blanca y el color del banco va solo en su avatar ([AvatarDeLaMarca]): el amarillo de Bancolombia
 * de fondo se confundía con el `#FFC21A` de la marca de Movi, y el morado de Nu pesaba en el
 * carrusel. Pura; queda como función para que la prueba lo diga.
 */
@Suppress("UNUSED_PARAMETER")
fun coloresDeLaTarjetaDeCuenta(marca: Marca?, colores: ColoresDeMovi): ColoresDeMovi = colores

/**
 * **La tarjeta de una cuenta** del carrusel «Tus cuentas» (camino 1): blanca, con la marca de su banco en el avatar,
 * el nombre (en hasta dos renglones: nunca «Bancolombia Ah…»), el tipo («Ahorros», «Tarjeta») junto al
 * rótulo del saldo —salvo que el nombre ya lo diga—, y el saldo en [VarianteDeCifra.Grande], que se
 * achica antes que recortarse.
 *
 * - Alto [Medida.altoDeTarjetaDeCuenta], radio [Radio.xl], relleno [Medida.rellenoDeSuperficie], sin
 *   borde: **blanca** sobre el crema (vuelta 29; antes era del color del banco).
 * - Con [marca], el avatar de arriba lleva el color del banco con su letra ([AvatarDeLaMarca]): es el
 *   único lugar de la tarjeta con el color de la marca. Sin marca, el [icono] del tipo de cuenta en un
 *   [IconoEnCirculo].
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
    /**
     * Vuelta 64: una línea chica debajo del saldo —«$2.000.000 apartado»—, o `null`. El saldo sigue
     * siendo el del banco; la nota dice cuánto de él ya tiene dueño.
     */
    nota: String? = null,
    /**
     * Reserva el renglón de [nota] aunque esta tarjeta no la tenga: en un carrusel donde alguna cuenta
     * tiene algo apartado, todas miden lo mismo de alto.
     */
    reservarNota: Boolean = nota != null,
) {
    val colores = coloresDeLaTarjetaDeCuenta(marca, Movi.colores)
    CompositionLocalProvider(LocalColores provides colores) {
        Column(
            modifier = modifier
                // Un mínimo, no un alto fijo: con la letra del sistema agrandada la tarjeta crece en vez de cortar.
                .heightIn(min = Medida.altoDeTarjetaDeCuenta)
                .anilloDeFoco(RoundedCornerShape(Radio.xl))
                .alPresionar(onClick = onClick, onClickLabel = "Ver $nombre", velo = true, forma = RoundedCornerShape(Radio.xl))
                .clip(RoundedCornerShape(Radio.xl))
                .background(colores.tarjeta)
                .testTag(TAG_TARJETA_DE_CUENTA)
                .padding(Medida.rellenoDeSuperficie),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                if (marca != null) AvatarDeLaMarca(marca) else IconoEnCirculo(icono, tono = TonoDeIcono.Neutro)
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
                        style = Movi.textos.apoyoMedio,
                        color = colores.textoMedio,
                        modifier = Modifier.weight(1f),
                    )
                    if (tipo != null) {
                        Text(text = tipo, style = Movi.textos.apoyoMedio, color = colores.textoMedio, maxLines = 1)
                    }
                }
                Cifra(saldo, VarianteDeCifra.Grande, moneda = moneda)
                if (reservarNota) {
                    Text(
                        text = nota.orEmpty(),
                        style = Movi.textos.apoyo,
                        color = colores.textoMedio,
                        maxLines = 1,
                        minLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(TAG_NOTA_DE_LA_TARJETA_DE_CUENTA),
                    )
                }
            }
        }
    }
}

/** El `testTag` de la nota de una [TarjetaDeCuenta] («$2.000.000 apartado», vuelta 64). */
const val TAG_NOTA_DE_LA_TARJETA_DE_CUENTA: String = "tarjeta-de-cuenta:nota"

/**
 * **El avatar del banco**: su letra («B», «nu») en un círculo. Vuelta 79 · un solo amarillo por vista: iba en
 * el color del banco, y el amarillo de Bancolombia era el amarillo de la acción de Movi (en Inicio, «B»
 * era una mancha amarilla más). Ahora va **en la capa 3 con la letra en la tinta**, como un ícono neutro
 * ([coloresDelAvatarDelBanco]): el banco lo dice su letra, no un color que compite con la acción.
 */
@Composable
private fun AvatarDeLaMarca(marca: Marca) {
    val (fondo, letra) = coloresDelAvatarDelBanco(Movi.colores)
    Box(
        modifier = Modifier
            .size(Medida.avatar)
            .background(fondo, CircleShape)
            .semantics {
                contentDescription = marca.nombre
                tonoDeIcono = TonoDeIcono.Neutro
            }
            .testTag(TAG_MARCA_DE_LA_TARJETA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = marca.corta,
            style = Movi.textos.titulo,
            color = letra,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** El fondo y la letra del avatar de un banco (vuelta 79): la capa 3 y la tinta, nunca la marca. Pura. */
fun coloresDelAvatarDelBanco(colores: ColoresDeMovi): Pair<Color, Color> = colores.capa3 to colores.texto
