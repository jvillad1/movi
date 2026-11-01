package com.jvillada.movi.ui.sms

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.quickadd.simboloDeMoneda
import kotlin.math.roundToLong

/*
 * # «Anotarlo igual» en Reconciliar
 *
 * Ver `AnotarloIgual.kt` en `:core`. Cuando Movi no entendió el aviso pero el server encontró un monto,
 * Reconciliar lo dice y ofrece «Anotarlo igual»: eso arma una lectura con lo que trae el aviso
 * ([com.jvillada.movi.shared.model.comoLectura]) y el resto de la pantalla —comercio, cuenta,
 * categoría, «¿Ya lo anotaste?», confirmar— funciona como con cualquier aviso. Lo único que se agrega
 * es esto: el monto, que se puede corregir, y gasto o ingreso, que elige el dueño.
 */

/** El botón principal mientras el aviso no se entendió. */
const val ANOTARLO_IGUAL: String = "Anotarlo igual"

/** El `testTag` del campo del monto de un aviso que se anota igual. */
const val TAG_MONTO_DE_ANOTARLO_IGUAL: String = "sms:anotarlo-igual:monto"

/** El `testTag` de la pastilla «Gasto» o «Ingreso». */
fun tagDelTipoDeAnotarloIgual(tipo: TransactionType): String = "sms:anotarlo-igual:tipo:${tipo.name}"

/** «Movi no entendió este aviso, pero trae $1.000. Puedes anotarlo igual.» */
fun textoDeAnotarloIgual(sinLeer: AvisoSinLeer): String {
    val monto = formatMoney(sinLeer.monto.roundToLong(), sinLeer.moneda)
    // Vuelta 91: un depósito tuyo es un traspaso desde otra cuenta tuya, nunca un ingreso.
    if (sinLeer.deposito) return "Movi no entendió este aviso, pero es un depósito de $monto a tu cuenta. Anótalo como traspaso desde otra cuenta tuya."
    return "Movi no entendió este aviso, pero trae $monto. Puedes anotarlo igual."
}

/** Vuelta 91 · el botón principal cuando el aviso sin leer es un depósito tuyo ([AvisoSinLeer.deposito]). */
const val ANOTARLO_COMO_TRASPASO: String = "Anotarlo como traspaso"

/** El botón principal de un aviso sin leer: [ANOTARLO_COMO_TRASPASO] si es un depósito tuyo, si no [ANOTARLO_IGUAL]. */
fun botonDeAnotarloIgual(sinLeer: AvisoSinLeer?): String = if (sinLeer?.deposito == true) ANOTARLO_COMO_TRASPASO else ANOTARLO_IGUAL

/** Debajo, ya con el formulario abierto: qué falta. */
const val AYUDA_DE_ANOTARLO_IGUAL: String = "Revisa el monto, di si salió o entró, y elige la categoría."

/**
 * El monto (ya puesto, editable) y gasto o ingreso. La cuenta, el comercio y la categoría son las filas
 * de siempre de Reconciliar.
 */
@Composable
internal fun FormularioDeAnotarloIgual(
    monto: Long?,
    moneda: String,
    tipo: TransactionType,
    habilitado: Boolean,
    onMonto: (Long?) -> Unit,
    onTipo: (TransactionType) -> Unit,
) {
    Column {
        Text(AYUDA_DE_ANOTARLO_IGUAL, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            PastillaDeTipo("Gasto", TransactionType.EXPENSE, tipo, habilitado, onTipo)
            PastillaDeTipo("Ingreso", TransactionType.INCOME, tipo, habilitado, onTipo)
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        MoneyField(
            value = monto,
            onValueChange = { if (habilitado) onMonto(it) },
            label = "Monto",
            placeholder = simboloDeMoneda(moneda) + " 0",
            prefix = simboloDeMoneda(moneda),
            modifier = Modifier.testTag(TAG_MONTO_DE_ANOTARLO_IGUAL),
        )
    }
}

@Composable
private fun PastillaDeTipo(
    texto: String,
    este: TransactionType,
    elegido: TransactionType,
    habilitado: Boolean,
    onTipo: (TransactionType) -> Unit,
) {
    // Gasto o ingreso: un filtro del sistema que se elige.
    PildoraDeFiltro(
        texto = texto,
        seleccionada = este == elegido,
        onClick = { if (habilitado) onTipo(este) },
        modifier = Modifier.testTag(tagDelTipoDeAnotarloIgual(este)),
    )
}
