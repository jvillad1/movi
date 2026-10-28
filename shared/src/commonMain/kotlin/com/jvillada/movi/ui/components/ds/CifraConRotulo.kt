package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.LineaEsqueleto

/** Tag estable de una [CifraConRotulo] (y de un [RotuloConValor]). */
const val TAG_CIFRA_CON_ROTULO: String = "ds-cifra-con-rotulo"

/** Lo que se pinta en lugar de una cifra que todavía no se sabe. */
const val CIFRA_SIN_DATO: String = "—"

/**
 * **Dónde va el rótulo de una [CifraConRotulo].**
 *
 * - [Apilada]: el rótulo arriba y la cifra debajo. Lo normal: dos o tres cifras lado a lado
 *   («Tienes · Debes», «Entró · Salió · Neto del período», «Ingresos recurrentes · Gastos recurrentes»).
 * - [EnRenglon]: el rótulo a la izquierda y la cifra a la derecha, en un renglón: un desglose que
 *   se lee de arriba abajo, una cifra por renglón («Tuyo … $1.250.000» en Créditos). El rótulo se
 *   parte en dos renglones antes de empujar la cifra: la cifra se mide primero y nunca se recorta.
 */
enum class DisposicionDeCifraConRotulo { Apilada, EnRenglon }

/**
 * **Un rótulo y su valor**, la base de [CifraConRotulo]. Para el valor que no es plata y va en el
 * mismo lugar que una cifra (la fecha de la última cuota en el resumen de Créditos); lo que es plata
 * usa [CifraConRotulo], que trae el signo, el tono y «Ocultar los saldos».
 *
 * El rótulo va en `apoyo`, en `textoMedio` y **como se escribe** (nunca en mayúsculas); entre el
 * rótulo y el valor, [Espacio.xxs] apilados o [Espacio.xs] en renglón.
 */
@Composable
fun RotuloConValor(
    rotulo: String,
    modifier: Modifier = Modifier,
    alFinal: Boolean = false,
    disposicion: DisposicionDeCifraConRotulo = DisposicionDeCifraConRotulo.Apilada,
    valor: @Composable () -> Unit,
) {
    when (disposicion) {
        DisposicionDeCifraConRotulo.Apilada -> Column(
            modifier = modifier.testTag(TAG_CIFRA_CON_ROTULO),
            horizontalAlignment = if (alFinal) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = rotulo,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                textAlign = if (alFinal) TextAlign.End else TextAlign.Start,
            )
            Spacer(Modifier.height(Espacio.xxs))
            valor()
        }
        DisposicionDeCifraConRotulo.EnRenglon -> Row(
            modifier = modifier.fillMaxWidth().testTag(TAG_CIFRA_CON_ROTULO),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rotulo,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Espacio.xs))
            valor()
        }
    }
}

/**
 * **Una cifra con su rótulo**: «Tienes $1.528.700.000», «Entró $15,6M», «Tuyo $1.250.000». Una sola
 * forma para lo que cuatro pantallas escribían cada una con su alto y su espaciado (vuelta de diseño
 * 19: el patrimonio de Hoy, Tus períodos, Créditos y el flujo libre de Plan).
 *
 * La cifra es la [Cifra] del sistema: el signo, el [tono], la letra tabular y «Ocultar los saldos»
 * vienen con ella. [monto] `null` es «todavía no se sabe»: se pinta [CIFRA_SIN_DATO] con la misma
 * letra, del mismo alto.
 *
 * ```
 * Row {
 *     CifraConRotulo("Tienes", tienes, Modifier.weight(1f))
 *     CifraConRotulo("Debes", debes, alFinal = true)
 * }
 * CifraConRotulo("Neto del período", neto, tono = TonoDeCifra.Ingreso, forma = FormaDeCifra.Compacta, alFinal = true)
 * CifraConRotulo("Tuyo", interes, disposicion = DisposicionDeCifraConRotulo.EnRenglon)
 * ```
 *
 * @param alFinal alinea rótulo y cifra a la derecha: la última de una fila de cifras.
 */
@Composable
fun CifraConRotulo(
    rotulo: String,
    monto: Long?,
    modifier: Modifier = Modifier,
    variante: VarianteDeCifra = VarianteDeCifra.Fila,
    tono: TonoDeCifra = TonoDeCifra.Neutro,
    moneda: String = "COP",
    forma: FormaDeCifra = FormaDeCifra.Entera,
    alFinal: Boolean = false,
    disposicion: DisposicionDeCifraConRotulo = DisposicionDeCifraConRotulo.Apilada,
) {
    RotuloConValor(rotulo = rotulo, modifier = modifier, alFinal = alFinal, disposicion = disposicion) {
        if (monto != null) {
            Cifra(monto, variante, tono = tono, moneda = moneda, forma = forma)
        } else {
            Text(
                text = CIFRA_SIN_DATO,
                style = estiloDeLaCifra(variante, Movi.textos),
                color = colorDeLaCifra(TonoDeCifra.Apagado, Movi.colores),
                maxLines = 1,
            )
        }
    }
}

/**
 * [CifraConRotulo] mientras no llegó: un renglón del alto del rótulo y otro del de la cifra, con el
 * mismo aire entre los dos. Para que un esqueleto reserve lo mismo que la cifra que va a llegar.
 */
@Composable
fun EsqueletoDeCifraConRotulo(
    modifier: Modifier = Modifier,
    variante: VarianteDeCifra = VarianteDeCifra.Fila,
    alFinal: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alFinal) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        LineaEsqueleto(fraccionDelAncho = FRACCION_DEL_ROTULO_ESQUELETO, estilo = Movi.textos.apoyo)
        LineaEsqueleto(fraccionDelAncho = FRACCION_DE_LA_CIFRA_ESQUELETO, estilo = estiloDeLaCifra(variante, Movi.textos))
    }
}

private const val FRACCION_DEL_ROTULO_ESQUELETO = 0.5f
private const val FRACCION_DE_LA_CIFRA_ESQUELETO = 0.7f
