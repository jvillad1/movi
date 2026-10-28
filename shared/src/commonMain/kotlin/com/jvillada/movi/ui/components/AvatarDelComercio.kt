package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.marcaDe
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.theme.COLOR_DE_CATEGORIA_RESPALDO
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.theme.Medida

/**
 * # El comercio, protagonista de la fila
 *
 * Cuando Movi reconoce la marca de un movimiento (`marcaDe`, el catálogo propio de `:core`: nada sale
 * del teléfono), la fila lleva su logotipo tipográfico —el círculo con el color de la marca y su
 * marca corta— en vez del ícono de la categoría. Sin marca, la fila queda **exactamente** como antes:
 * el círculo de la categoría ([IconoDeCategoria]).
 */

/** El `testTag` del círculo de una marca, para que una prueba lo encuentre en una fila. */
const val TAG_AVATAR_DE_MARCA: String = "avatar-de-marca"

/** El `testTag` del círculo de una cuenta sin marca (el ícono del tipo de cuenta). */
const val TAG_AVATAR_DE_CUENTA_SIN_MARCA: String = "avatar-de-cuenta-sin-marca"

/**
 * Los tamaños del avatar. [Fila] (36 dp) es el de una fila de lista, el mismo círculo que el de la
 * categoría para que la fila no cambie de alto; [Encabezado] (48 dp) el de arriba de un detalle, y
 * [Linea] (20 dp) el que va junto a un nombre en una línea de texto chica.
 */
enum class TamanoDeAvatar(val circulo: Dp) {
    Linea(20.dp),
    Fila(36.dp),
    Encabezado(48.dp),
}

/** El avatar de un movimiento: su marca si Movi la reconoce, o el ícono de su categoría. */
@Composable
fun AvatarDelComercio(
    evento: FinancialEvent,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) {
    val marca = remember(evento.merchant, evento.description) { marcaDe(evento) }
    if (marca != null) {
        CirculoDeMarca(marca, modifier, tamano)
    } else {
        IconoDeCategoria(
            evento.category,
            modifier = modifier,
            tamano = if (tamano == TamanoDeAvatar.Linea) TamanoDeIconoDeCategoria.Chico else TamanoDeIconoDeCategoria.Normal,
        )
    }
}

/** El avatar de una cuenta: la marca de su banco, o el ícono de su tipo en un círculo neutro. */
@Composable
fun AvatarDeLaCuenta(
    cuenta: Account,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) = AvatarDeLaCuenta(nombre = cuenta.name, tipo = cuenta.type, modifier = modifier, tamano = tamano)

/** Lo mismo con el nombre y el tipo sueltos, para una fila que no tiene la cuenta entera. */
@Composable
fun AvatarDeLaCuenta(
    nombre: String,
    tipo: AccountType,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) {
    val marca = remember(nombre) { marcaDelBanco(nombre) }
    if (marca != null) {
        CirculoDeMarca(marca, modifier, tamano)
    } else {
        val (icono, rotulo) = iconoDelTipoDeCuenta(tipo)
        Box(
            modifier = modifier
                .testTag(TAG_AVATAR_DE_CUENTA_SIN_MARCA)
                .size(tamano.circulo)
                .background(Movi.colores.circuloDeCategoria(COLOR_DE_CATEGORIA_RESPALDO), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icono,
                contentDescription = rotulo,
                tint = Movi.colores.textoMedio,
                modifier = Modifier.size(tamano.circulo * 0.55f),
            )
        }
    }
}

/**
 * El logotipo tipográfico: el círculo del color de la marca y su marca corta en Space Grotesk
 * semibold, ajustada al tamaño (una letra se ve más grande que tres). El borde fino es para el
 * negro de Uber o de Zara sobre el fondo oscuro: sin él, el círculo no tiene orilla.
 */
@Composable
fun CirculoDeMarca(marca: Marca, modifier: Modifier = Modifier, tamano: TamanoDeAvatar = TamanoDeAvatar.Fila) {
    // En dp y no en sp: el logotipo es un dibujo del tamaño del círculo, no texto que deba crecer
    // con la letra del sistema (con letra grande, tres letras se salían del círculo).
    //
    // Vuelta 40: tres letras o más van a 0,34 del círculo (era 0,29): en el círculo de una fila (36)
    // daba 10,4 px, por debajo del mínimo de 12 que se ve en Actividad y Pagos; ahora 12,2.
    val fraccion = fraccionDeLaMarca(marca.corta.length)
    val tamanoDeLetra = with(LocalDensity.current) { (tamano.circulo * fraccion).toSp() }
    Box(
        modifier = modifier
            .testTag(TAG_AVATAR_DE_MARCA)
            .semantics { contentDescription = marca.nombre }
            .size(tamano.circulo)
            .background(Color(marca.fondo), CircleShape)
            .border(Medida.trazo, Movi.colores.hilo, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = marca.corta,
            style = Movi.textos.titulo.copy(
                fontSize = tamanoDeLetra,
                lineHeight = tamanoDeLetra,
                letterSpacing = (-0.02).em,
                color = Color(marca.texto),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Cuánto del círculo mide la letra de una marca de [letras] letras (una se ve más grande que tres). */
internal fun fraccionDeLaMarca(letras: Int): Float = when (letras) {
    1 -> 0.46f
    2 -> 0.38f
    else -> 0.34f
}

/** El ícono de siempre de cada tipo de cuenta, con su nombre para el lector de pantalla. */
internal fun iconoDelTipoDeCuenta(type: AccountType): Pair<ImageVector, String> = when (type) {
    AccountType.CASH -> Icons.Filled.Payments to "Efectivo"
    AccountType.SAVINGS -> Icons.Filled.AccountBalance to "Ahorros"
    AccountType.CHECKING -> Icons.Filled.AccountBalanceWallet to "Corriente"
    AccountType.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp to "Inversión"
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to "Crédito"
    AccountType.LOAN -> Icons.Filled.RequestQuote to "Préstamo"
}
