package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.TextosDeMovi
import com.jvillada.movi.ui.components.LocalSaldosOcultos
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.signedMoneySinOcultar
import kotlin.math.abs

/** Tag estable de una [Cifra]. */
const val TAG_CIFRA: String = "ds-cifra"

/**
 * **El papel de una cifra en la pantalla**, que decide su tamaño y su letra.
 *
 * - [Heroe]: la cifra protagonista. **Una por pantalla, o ninguna.** Martian Mono a 42 sp, en un
 *   renglón; si no entra se achica hasta la de [Grande], y ahí para.
 * - [Grande]: la de una tarjeta de cuenta o un widget. Martian Mono a 24 sp, en un renglón; si no
 *   entra (un widget de media pantalla) se achica hasta la de [Fila].
 * - [Fila]: el monto al final de una [Fila]. Martian Mono a 12,5 sp con cifras tabulares (ola R1:
 *   toda cifra de plata va en la misma familia), para que la columna no baile.
 * - [Chica]: un monto dentro de un subtítulo o un desglose.
 */
enum class VarianteDeCifra { Heroe, Grande, Fila, Chica }

/**
 * **Qué dice el color de una cifra.** El color nunca es la única pista: un monto lleva «+» solo
 * cuando entra plata, y el subtítulo de la fila dice el verbo.
 */
enum class TonoDeCifra {
    /** Un gasto, un saldo, una cifra cualquiera: en el color del texto. Gastar no es una alarma. */
    Neutro,

    /** Plata que entró: en `entra`, con «+». */
    Ingreso,

    /** Algo que se pasó o se debe: en `sale`. Solo para alertas, no para cada gasto. */
    Alerta,

    /** Una cifra de apoyo, en gris. */
    Apagado,

    /**
     * Plata que cambió de cuenta (un traspaso, la cuota o el pago de tarjeta que se ven como uno): en
     * `entreCuentas` y **sin signo**, aunque llegue negativo. No entró ni salió del bolsillo; ponerle
     * «+» o «−» sería elegir el punto de vista de una de las dos cuentas (ola R4).
     */
    EntreCuentas,
}

/**
 * **La variante de una [Cifra], en su semántica** (vuelta de diseño 14). La regla «una sola
 * [VarianteDeCifra.Heroe] por pantalla» se vigila contando este dato en el árbol de semántica
 * (`UnaSolaCifraHeroePorPantallaTest`): no depende del texto, del tamaño de la letra ni de un tag por
 * variante, y no le cambia nada al lector de pantalla. `CifraProtagonista` (la cifra ya escrita, en
 * `ui/components`) lo marca como [VarianteDeCifra.Heroe], porque lo es.
 */
val VarianteDeCifraKey: SemanticsPropertyKey<VarianteDeCifra> = SemanticsPropertyKey("VarianteDeCifra")

/** Ver [VarianteDeCifraKey]. */
var SemanticsPropertyReceiver.varianteDeCifra: VarianteDeCifra by VarianteDeCifraKey

/**
 * **El tono de una [Cifra], en su semántica** (vuelta de diseño 16), como [VarianteDeCifraKey]: deja
 * vigilar «un gasto no es una alarma» sin leer colores (el widget «En qué se va» pintaba el monto de
 * Mercado en `sale`). No le cambia nada al lector de pantalla.
 */
val TonoDeCifraKey: SemanticsPropertyKey<TonoDeCifra> = SemanticsPropertyKey("TonoDeCifra")

/** Ver [TonoDeCifraKey]. */
var SemanticsPropertyReceiver.tonoDeCifra: TonoDeCifra by TonoDeCifraKey

/** El estilo de una variante, en la escala de textos. Pura. */
fun estiloDeLaCifra(variante: VarianteDeCifra, textos: TextosDeMovi): TextStyle = when (variante) {
    VarianteDeCifra.Heroe -> textos.cifra
    VarianteDeCifra.Grande -> textos.cifraDeTarjeta
    VarianteDeCifra.Fila -> textos.monto.copy(fontWeight = FontWeight.Medium)
    VarianteDeCifra.Chica -> textos.apoyo.copy(fontFeatureSettings = "tnum")
}

/** El color de un tono, en la paleta del tema. Pura. */
fun colorDeLaCifra(tono: TonoDeCifra, colores: ColoresDeMovi): Color = when (tono) {
    TonoDeCifra.Neutro -> colores.texto
    TonoDeCifra.Ingreso -> colores.entra
    TonoDeCifra.Alerta -> colores.sale
    TonoDeCifra.Apagado -> colores.textoMedio
    TonoDeCifra.EntreCuentas -> colores.entreCuentas
}

/**
 * El texto de una cifra: `−$27.500`, `+$4.500.000`, `US$181`. Con [ocultos], [PLATA_OCULTA] y
 * nada más (sin signo: un «−» suelto ya diría que la cuenta va en rojo). Pura, para probarla.
 */
fun textoDeLaCifra(monto: Long, moneda: String, tono: TonoDeCifra, ocultos: Boolean): String = when {
    ocultos -> PLATA_OCULTA
    tono == TonoDeCifra.Ingreso && monto > 0 -> "+" + signedMoneySinOcultar(monto, moneda)
    tono == TonoDeCifra.EntreCuentas -> signedMoneySinOcultar(abs(monto), moneda)
    else -> signedMoneySinOcultar(monto, moneda)
}

/**
 * **Una cifra de plata.** El signo, el color y «Ocultar los saldos» vienen incluidos: la pantalla
 * pasa el monto con su signo y dice qué papel juega.
 *
 * Se llama igual que el `Cifra(texto: String, …)` de `ui/components`, que recibe el texto ya
 * armado; esta recibe el **monto**, y por eso no hay ambigüedad entre las dos. La vieja se va
 * migrando a esta.
 *
 * ```
 * Cifra(-27_500)                                           // −$27.500, en una fila
 * Cifra(4_500_000, tono = TonoDeCifra.Ingreso)             // +$4.500.000, en verde
 * Cifra(tuPlata, VarianteDeCifra.Heroe)                    // la cifra de la pantalla
 * Cifra(181, moneda = "USD", variante = VarianteDeCifra.Chica)
 * Cifra(500_000, tono = TonoDeCifra.EntreCuentas)          // $500.000, en el azul de un traspaso
 * ```
 */
@Composable
fun Cifra(
    monto: Long,
    variante: VarianteDeCifra = VarianteDeCifra.Fila,
    modifier: Modifier = Modifier,
    tono: TonoDeCifra = TonoDeCifra.Neutro,
    moneda: String = "COP",
) {
    val texto = textoDeLaCifra(monto, moneda, tono, LocalSaldosOcultos.current)
    val estilo = estiloDeLaCifra(variante, Movi.textos).copy(color = colorDeLaCifra(tono, Movi.colores))
    val conTag = modifier.testTag(TAG_CIFRA).semantics {
        varianteDeCifra = variante
        tonoDeCifra = tono
    }
    // Un renglón siempre: Martian Mono es ancha y a 42 sp «$8.550.000» se partía en un teléfono de
    // 390 dp. La héroe se achica hasta el tamaño de la cifra de tarjeta, no más; la grande (ola R1),
    // hasta el de un monto de fila, porque va en un widget de media pantalla.
    val piso = when (variante) {
        VarianteDeCifra.Heroe -> Movi.textos.cifraDeTarjeta.fontSize
        VarianteDeCifra.Grande -> Movi.textos.monto.fontSize
        else -> null
    }
    if (piso != null) {
        BasicText(
            text = texto,
            modifier = conTag,
            style = estilo,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = piso, maxFontSize = estilo.fontSize),
        )
    } else {
        BasicText(text = texto, modifier = conTag, style = estilo, maxLines = 1)
    }
}
