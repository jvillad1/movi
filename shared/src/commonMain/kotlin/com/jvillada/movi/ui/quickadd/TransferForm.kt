package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.ui.components.ds.PildoraDeAccion
import com.jvillada.movi.ui.credits.pastillasDelPagoDeLaTarjeta
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.jvillada.movi.ui.credits.extractoPagado
import com.jvillada.movi.ui.credits.mesDelPagoDeLaTarjeta
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.DensidadDeFila
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.formatAmountKeypadDisplay
import androidx.compose.ui.focus.onFocusChanged
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.data.CuentaMasUsadaCache
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DesgloseDeCuota
import com.jvillada.movi.shared.model.MotivoDelDesglose
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.desglosarCuotaRegistrada
import com.jvillada.movi.shared.model.validarInteresReal
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.TransferKind
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.transferKindFor
import com.jvillada.movi.shared.model.validarPagoDeCuota
import com.jvillada.movi.shared.model.validateTransfer
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.signedMoney
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.components.rememberVibrador
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.fecha.timestampParaFecha
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate

/**
 * Hoy, en la zona de la app, como "AAAA-MM-DD".
 *
 * Nació como el valor inicial del campo de fecha del traspaso; ese campo ya no existe (lo
 * reemplazó el selector) pero la función sigue viva y usada: Movimientos la usa para saber qué
 * día es «Hoy» al armar sus encabezados. Vive acá por historia, no por pertenencia.
 */
fun todayIsoInAppZone(clock: Clock = Clock.System, zone: TimeZone = AppTimeZone.zone): String =
    clock.now().toLocalDateTime(zone).date.toString()

/**
 * Los tres ids del traspaso que se está escribiendo. **Se generan una vez por borrador, no una
 * vez por toque de Guardar.**
 *
 * El escenario que esto arregla: el server commitea las dos patas, la respuesta se pierde
 * (timeout, cambio de red, la app al fondo), el dueño ve «revisa tu conexión» y vuelve a tocar
 * Guardar. Si el segundo intento lleva ids nuevos, el server no tiene cómo saber que es el mismo
 * traspaso y crea uno segundo entero: origen −2×monto, destino +2×monto, y el dueño con dos
 * renglones idénticos que no pidió. Con los mismos ids, el INSERT choca contra la PK de las patas,
 * el server relee y —si las dos están— devuelve el traspaso real con 200, o sea que el reintento
 * termina por el camino normal, sin error que mostrar.
 *
 * Se renuevan **solo después de un éxito**: recién ahí el traspaso siguiente es otro traspaso.
 */
data class TransferDraftIds(
    val transferId: String,
    val fromEventId: String,
    val toEventId: String,
) {
    companion object {
        fun new() = TransferDraftIds(
            transferId = newId("tr"),
            fromEventId = newId("ev"),
            toEventId = newId("ev"),
        )
    }
}

/** El pedido que se manda al server. Función aparte para que un reintento sea, literalmente, el mismo pedido. */
fun transferRequestFor(
    ids: TransferDraftIds,
    from: Account,
    to: Account,
    amount: Long,
    timestamp: Long,
    note: String,
): CreateTransferRequest = CreateTransferRequest(
    transferId = ids.transferId,
    fromEventId = ids.fromEventId,
    toEventId = ids.toEventId,
    fromAccountId = from.id,
    toAccountId = to.id,
    amount = amount,
    timestamp = timestamp,
    note = note.trim().ifBlank { null },
)

/**
 * ¿Este fallo de `createTransfer` es en realidad «el traspaso ya está guardado»?
 *
 * Red de seguridad, no el camino habitual: este server relee las patas ante el choque de PK y
 * devuelve el traspaso real con 200, así que un reintento sale por el camino de éxito. El 409
 * queda contemplado para un server anterior a ese cambio (una versión vieja todavía desplegada):
 * también significa que las dos patas existen, y mostrarlo como error sería mentirle al dueño y
 * empujarlo a tocar Guardar una tercera vez.
 *
 * **Para que quede sin ambigüedad (revisión de la Ola 11): el `TransferRoutes` de hoy NUNCA
 * responde 409.** El reintento idempotente da 200 y un `transferId` reusado para otro traspaso da
 * 422 (`TRANSFER_ID_ALREADY_USED`), que sí es un error de verdad y se muestra como tal. O sea que
 * esta rama —y el `recordTransfer` que cuelga de ella— está muerta contra el server actual y
 * existe solo para el cliente que le pegue a uno viejo.
 */
fun isAlreadyRegistered(error: Throwable): Boolean =
    error is ApiException && error.status == 409

/**
 * Cuentas que se pueden poner en cualquiera de las dos puntas: **todo menos las tarjetas de
 * crédito**.
 *
 * Se filtran del selector y no solo de la validación: ofrecer una tarjeta para después decir que
 * no se puede es peor que no ofrecerla. Pagar el extracto de la tarjeta se anota como un gasto
 * normal con la categoría «Pago de tarjeta» (ver [TRANSFER_CARD_BLOCKED]).
 *
 * **Ola 14 — los préstamos entran.** Antes esta lista sacaba el grupo DEUDA entero, y con eso se
 * iba el desembolso: un crédito nuevo entraba a Créditos como deuda y la plata que el banco
 * depositaba en la cuenta corriente no existía para Movi. Ahora los créditos aparecen en el
 * selector, en su propio grupo y al final (ver [TransferAccountPicker]), para las dos direcciones
 * reales — desembolso y abono extraordinario.
 *
 * **Ola 15 — la regla ya no vive acá.** Decía `it.type != AccountType.CREDIT_CARD` escrito a mano,
 * y era una de cuatro copias del mismo criterio repartidas entre este archivo y la hoja de
 * «Agregar». Hoy pregunta por [UsoDeCuenta.PUNTA_DE_TRASPASO] en `:core`, donde está el criterio
 * entero y donde se prueba. Lo que se ofrece no cambió ni un renglón: acá se toma `principales`,
 * o sea el filtro duro de siempre — un traspaso a una tarjeta no existe, y el «Ver todas» del
 * selector de un gasto es otra pantalla y otra pregunta.
 */
fun transferableAccounts(accounts: List<Account>): List<Account> =
    cuentasPara(accounts, UsoDeCuenta.PUNTA_DE_TRASPASO).principales

/**
 * De dónde puede salir la cuenta **preseleccionada** de cada lado: solo cuentas de dinero o
 * inversión, nunca un crédito.
 *
 * Es la mitad menos vistosa de dejar entrar los préstamos, y la que evita el accidente: si un
 * crédito pudiera quedar elegido solo —porque es el último que se usó, o porque quedó primero en
 * la lista—, dos toques distraídos anotarían un desembolso de $257.000.000 que nadie pidió. Un
 * crédito en un traspaso se elige **siempre con el dedo**; la app no lo propone jamás.
 *
 * Consecuencia buscada: quien tiene una sola cuenta de dinero y un crédito ve «Desde
 * Bancolombia» y «Hacia · Elegir cuenta», con el botón apagado hasta que elija. Antes ni siquiera
 * veía el formulario.
 */
fun defaultTransferAccounts(accounts: List<Account>): List<Account> =
    cuentasPara(accounts, UsoDeCuenta.DINERO_PROPIO).principales

/**
 * El renglón que dice **en qué queda la deuda del crédito** si se guarda este traspaso, o `null`
 * si ninguna punta es un crédito (o falta el monto).
 *
 * Es la pieza que hace que el error caro sea imposible de no ver. Un desembolso **sube** la
 * deuda, y el dueño que ya creó el crédito en Créditos con su deuda actual no tiene por qué
 * saber que registrarlo otra vez acá se la deja al doble. Un aviso genérico se lee y se olvida;
 * la aritmética con sus dos cifras, no: «Deuda de Libranza: $257.000.000 pasa a $514.000.000» se
 * discute sola con lo que él sabe que debe. (Dice «pasa a» y no «→» porque la flecha sale como ▯
 * en wasm — la fuente del canvas no trae el glifo, ver [transferRowSubtitle].)
 *
 * El saldo de una cuenta LOAN es deuda positiva y sale derivado de los eventos
 * (`enrichWith`/`computeBalances`), así que el «después» es una suma, no una predicción: es
 * exactamente lo que [signedDelta] le va a aplicar a esa pata.
 *
 * **Y sirve igual sobre un crédito con el saldo ajustado a mano.** «Ajustar saldo» (la deuda real
 * según el banco) no sobrescribe nada: registra un evento más, así que la deuda derivada ya
 * incluye el ajuste y este renglón parte de ahí. Lo que hay que saber es que la suma no mira
 * fechas —el saldo de una cuenta es la suma de TODOS sus eventos, en cualquier orden—, así que
 * anotar un desembolso VIEJO después de haber cuadrado la deuda contra el banco la sube por
 * encima de lo que el banco dice: ese desembolso ya estaba dentro de la cifra que se cuadró. Es
 * exactamente lo que este renglón deja ver antes de guardar, y si se guarda igual se arregla con
 * otro «Ajustar saldo». La alternativa —ignorar los eventos anteriores a un ajuste— sería
 * convertir el ajuste en un saldo escrito a mano, que es justo lo que este sistema no hace.
 */
fun deudaDespuesDelTraspaso(from: Account?, to: Account?, amount: Long?): String? {
    if (from == null || to == null || amount == null || amount <= 0L) return null
    val credito = listOf(from, to).firstOrNull { it.type == AccountType.LOAN } ?: return null
    // El saldo EN LA MONEDA DEL CRÉDITO, no `account.balance` a secas: ese campo es el componente
    // COP (ver `enrichWith`), así que sobre un préstamo en dólares el renglón leía «US$0 pasa a
    // US$1.000» — un rótulo en una moneda sobre una cifra de otra. Antes se callaba; decir el
    // número equivocado con confianza es peor. `balancesByCurrency` viene derivado junto al saldo,
    // y para COP vale exactamente lo mismo que `balance`.
    val moneda = credito.currency
    val deudaActual = credito.balancesByCurrency[moneda] ?: credito.balance
    val despues = deudaActual + if (credito.id == from.id) amount else -amount
    // [signedMoney] y no [formatCOP] por dos razones, las dos de la revisión de esta rama:
    //
    // 1. **Un crédito en otra moneda también tiene derecho a la aritmética.** La primera versión
    //    devolvía `null` cuando el préstamo no era COP, o sea que el renglón desaparecía justo
    //    donde más falta hace: en la cuenta cuyo monto el dueño NO puede verificar de memoria.
    //    Hoy solo se llega ahí por `POST /api/accounts` (la UI de Créditos crea únicamente
    //    cuentas COP), pero el silencio era la peor respuesta posible para ese caso.
    // 2. **El signo.** [formatMoney] lo descarta, y una deuda que queda negativa —abonar de más,
    //    que es exactamente lo que hace quien cancela un crédito— tiene que leerse «−$500.000»
    //    y no «$500.000».
    // Vuelta 73 · **palabras de deuda positiva**: «Debes $980.000 · después del abono, $480.000». Con
    // «Deuda de X: −$980.000 pasa a −$1.480.000» (la revisión `r4`) el abono se leía como si la
    // deuda creciera. La cuenta no tenía un error de signo: el abono baja la deuda (−monto) y el
    // desembolso la sube (+monto); lo que estaba mal era el punto de partida, un crédito sin su saldo
    // en Movi, y eso ahora lo ataja quien llama con [creditoSinSaldoEnMovi] antes de hacer la cuenta.
    val que = if (credito.id == from.id) "del desembolso" else "del abono"
    return "Debes ${signedMoney(deudaActual, moneda)} · después $que, ${signedMoney(despues, moneda)}"
}

/**
 * El saldo de una cuenta **en su propia moneda**.
 *
 * `account.balance` es el componente COP (ver `enrichWith`), así que sobre una deuda en dólares
 * devuelve una cifra de otra moneda. Mismo hallazgo y misma solución que en
 * [deudaDespuesDelTraspaso], extraído acá porque ahora lo necesitan dos cosas.
 */
private fun saldoEnSuMoneda(cuenta: Account): Long =
    cuenta.balancesByCurrency[cuenta.currency] ?: cuenta.balance

/**
 * **Cuánto de esta cuota baja de verdad la deuda**, o `null` si todavía no hay con qué decirlo.
 *
 * Es la MISMA función de `:core` que usa el server para escribir las patas ([desglosarCuota]) — no
 * una segunda copia de la regla en la pantalla. La diferencia es de dónde sale el saldo: acá, del
 * que llegó con la cuenta; allá, derivado de los eventos vivos en el instante de guardar. Si algo
 * se movió en el medio, la respuesta del server trae el desglose que de verdad quedó escrito.
 */
fun desgloseDelPago(
    deuda: Account?,
    terms: CreditTerms?,
    monto: Long?,
    /**
     * El interés que el dueño escribió del extracto, o `null` para estimarlo. Se ignora si no
     * pasa [validarInteresReal] — el renglón de error lo dice aparte, y mientras tanto la hoja
     * sigue mostrando la estimación en vez de un capital negativo.
     */
    interesReal: Long? = null,
): DesgloseDeCuota? {
    if (deuda == null || monto == null || monto <= 0L) return null
    if (deuda.type != AccountType.LOAN && deuda.type != AccountType.CREDIT_CARD) return null
    val interesValido = interesReal?.takeIf {
        // `yaCobradoEnElMes = 0L`, el mismo que usa el desglose de abajo: la hoja no tiene los
        // pagos del mes a mano, así que valida y estima como si este fuera el primero. Si ya hubo
        // otro pago parcial, el server —que sí los tiene— acepta lo que acá se vería apretado.
        validarInteresReal(it, monto, deuda.type, terms?.insuranceMonthly, terms?.otrosCargosMensuales, yaCobradoEnElMes = 0L) == null
    }
    return desglosarCuotaRegistrada(
        cuota = monto,
        tipoDeLaDeuda = deuda.type,
        saldoDeLaDeuda = saldoEnSuMoneda(deuda),
        rateEa = terms?.rateEa,
        seguroMensual = terms?.insuranceMonthly,
        otrosCargosMensuales = terms?.otrosCargosMensuales,
        interesReal = interesValido,
        sinIntereses = terms?.sinIntereses ?: false,
        // La hoja no tiene los pagos del mes a mano: estima como si fuera el primero. Si ya hubo
        // otro pago parcial, el server descuenta lo cobrado y devuelve el desglose que escribió.
        yaCobradoEnElMes = 0L,
    )
}

/**
 * **El interés estimado de este mes**, con el que se prellena el campo de la hoja, o `null` si no
 * hay con qué estimarlo (no es un crédito, o no tiene tasa).
 *
 * No depende del monto —el interés es `saldo × tasa`, la cuota no entra— y por eso el campo se
 * puede prellenar apenas se elige el crédito, antes de escribir el monto. Es la MISMA aritmética
 * de [desglosarCuota]; se le pasa una cuota de cero porque solo interesa el interés que devuelve.
 */
fun interesEstimadoDelMes(deuda: Account?, terms: CreditTerms?): Long? {
    if (deuda == null || deuda.type != AccountType.LOAN) return null
    val d = desglosarCuota(
        cuota = 0L,
        tipoDeLaDeuda = deuda.type,
        saldoDeLaDeuda = saldoEnSuMoneda(deuda),
        rateEa = terms?.rateEa,
        seguroMensual = terms?.insuranceMonthly,
        otrosCargosMensuales = terms?.otrosCargosMensuales,
        yaCobradoEnElMes = 0L,
        sinIntereses = terms?.sinIntereses ?: false,
    )
    return d.interes.takeIf { d.motivo == MotivoDelDesglose.AMORTIZA }
}

/** La frase corta debajo del campo de interés. Dice que es estimado y qué hacer si tiene el extracto. */
const val INTERES_ESTIMADO_AVISO: String =
    "Estimado sobre tu deuda de hoy. Si tienes el extracto, escribe el interés real."

/** Lo mismo, para un crédito sin tasa: no hay estimación, pero el extracto sí sirve. */
const val INTERES_SIN_TASA_AVISO: String =
    "Este crédito no tiene tasa registrada, así que no podemos estimar el interés. Si tienes el " +
        "extracto, escríbelo aquí; si lo dejas vacío, la deuda baja por el monto completo."

/**
 * **La frase que le muestra al dueño en qué se le va la cuota, antes de guardar.**
 *
 * «De tus $1.286.548, $363.905 son intereses, $108.800 el seguro, y $813.843 bajan la deuda.»
 *
 * Es plata suya y tiene que poder **verificar** el número, no confiar en él: sin esta frase, Movi
 * le restaría a la deuda una cifra distinta de la que él escribió y no habría en toda la app dónde
 * enterarse de por qué. Mismo recurso que [deudaDespuesDelTraspaso] y por el mismo motivo — un
 * aviso genérico se lee y se olvida; la aritmética con sus propias cifras se discute sola.
 *
 * Devuelve `null` cuando no hay nada que explicar: una tarjeta baja exactamente por lo que se
 * pagó, y decirlo sería ruido.
 */
fun textoDelDesglose(desglose: DesgloseDeCuota, moneda: String): String? {
    fun plata(v: Long) = formatMoney(v, moneda)
    /**
     * Los renglones fijos de la cuota que no son interés: el seguro y los otros cargos. Cada uno
     * se nombra con la palabra del extracto y **por separado**, no sumados en un «otros $114.100»
     * que no cuadraría contra ninguna línea del papel del banco.
     */
    val fijos = buildString {
        if (desglose.seguro > 0L) append(", ${plata(desglose.seguro)} el seguro")
        if (desglose.otrosCargos > 0L) append(", ${plata(desglose.otrosCargos)} otros cargos")
    }
    return when (desglose.motivo) {
        MotivoDelDesglose.TARJETA -> null
        // El caso sin tasa **se dice, no se calla**: la deuda va a bajar por todo, que es lo que
        // pasaba antes de esta ola, y el dueño tiene que poder ver por qué y cómo arreglarlo.
        MotivoDelDesglose.SIN_TASA ->
            "Este crédito no tiene tasa registrada, así que no podemos separar el interés: la " +
                "deuda va a bajar los ${plata(desglose.cuota)} completos. Agrega la tasa % EA en " +
                "las condiciones del crédito para verlo separado."
        MotivoDelDesglose.AMORTIZA -> {
            // **La limitación, dicha donde el dueño la ve.** El interés se calcula sobre la deuda
            // de HOY, no sobre la que había el mes de la cuota: en Movi la deuda es la suma de
            // todos los eventos sin mirar fechas, así que «el saldo al 15 de julio» no existe.
            // Anotando en orden cronológico el resultado se parece a una amortización real;
            // anotando una cuota vieja después de una nueva, el interés sale bajo y el capital
            // alto —del orden de $100.000 por mes desfasado con los seis créditos, siempre
            // subestimando la deuda. Estaba anotado solo en el KDoc de `DesgloseDeCuota`, o sea en
            // ningún lado que él pueda leer.
            val sobreHoy = " Calculado sobre tu deuda de hoy."
            if (desglose.interes == 0L) {
                // Sin interés que nombrar —un crédito que no cobra intereses (`CreditTerms.sinIntereses`)—
                // no se dice «$0 son intereses» ni «calculado sobre tu deuda de hoy»: el saldo no
                // entró en ninguna cuenta.
                when {
                    desglose.capital <= 0L ->
                        "Tus ${plata(desglose.cuota)} se van enteros en${fijos.removePrefix(",")}: " +
                            "nada de este pago baja la deuda."
                    fijos.isEmpty() -> "Tus ${plata(desglose.cuota)} bajan la deuda completos."
                    else -> "De tus ${plata(desglose.cuota)}$fijos, y ${plata(desglose.capital)} bajan la deuda."
                }
            } else if (desglose.capital <= 0L) {
                // Existe de verdad: un pago parcial a la libranza ·4818 del dueño —$3.000.000
                // contra un interés de $3.646.011— es 100 % interés.
                "Tus ${plata(desglose.cuota)} no alcanzan a cubrir los ${plata(desglose.interes)} " +
                    "de intereses$fijos de este mes: nada de este pago baja la deuda.$sobreHoy"
            } else {
                "De tus ${plata(desglose.cuota)}, ${plata(desglose.interes)} son intereses" +
                    "$fijos, y ${plata(desglose.capital)} bajan la deuda.$sobreHoy"
            }
        }
        // El interés lo escribió él del extracto: no hay «calculado sobre tu deuda de hoy» porque
        // no hubo cálculo, y la frase lo dice para que se lea distinto de la estimación.
        MotivoDelDesglose.INTERES_REAL -> {
            if (desglose.capital <= 0L) {
                "Tus ${plata(desglose.cuota)} se van enteros en los ${plata(desglose.interes)} " +
                    "de intereses que escribiste$fijos: nada de este pago baja la deuda."
            } else {
                "De tus ${plata(desglose.cuota)}, ${plata(desglose.interes)} son intereses según " +
                    "tu extracto$fijos, y ${plata(desglose.capital)} bajan la deuda."
            }
        }
    }
}

/**
 * **Lo que hay que decirle al dueño cuando el server repartió la cuota distinto de lo que la hoja
 * le había prometido**, o `null` cuando coinciden — que es el camino de todos los días.
 *
 * ### Por qué existe
 *
 * La hoja calcula el desglose con el saldo que llegó cargado en pantalla; el server lo recalcula
 * contra los eventos vivos en el instante de guardar. Si entre abrir la hoja y tocar Guardar entró
 * un SMS del banco o se ajustó el saldo, **el reparto que se escribió no es el que se leyó**. El
 * server ya devolvía el que de verdad quedó ([PagoDeCuotaResult.desglose]), pero la pantalla
 * descartaba la respuesta entera: era una respuesta que nadie leía, o sea una promesa a medias.
 *
 * ### Qué se compara, y qué no
 *
 * La **cuota** y el **capital**, que son las dos cifras que el dueño vio escritas. El interés y el
 * seguro no se comparan por separado a propósito: en el camino del reintento el server no puede
 * separarlos de lo guardado (ver `desgloseDeLoGuardado`) y avisar por una diferencia entre dos
 * cifras que él nunca vio sería ruido.
 *
 * Un `guardado` en `null` es un server que todavía no manda el campo: no se afirma nada.
 */
fun avisoDeDesgloseDistinto(
    mostrado: DesgloseDeCuota?,
    guardado: DesgloseDeCuota?,
    moneda: String,
): String? {
    if (guardado == null) return null
    if (mostrado != null && mostrado.cuota == guardado.cuota && mostrado.capital == guardado.capital) return null
    fun plata(v: Long) = formatMoney(v, moneda)
    val queda = "De tus ${plata(guardado.cuota)}, ${plata(guardado.capital)} bajaron la deuda."
    if (mostrado == null) return "Pago registrado. $queda"
    return "Pago registrado, pero el reparto quedó distinto del que te mostramos: tu deuda cambió " +
        "entre que abriste esta pantalla y guardaste. $queda " +
        "Te habíamos dicho ${plata(mostrado.capital)}."
}

/** «Deuda de Vehículo: $177.200.000 pasa a $175.466.095» — el número que el dueño vino a ver bajar. */
fun deudaDespuesDelPago(deuda: Account?, desglose: DesgloseDeCuota?): String? {
    if (deuda == null || desglose == null) return null
    val moneda = deuda.currency
    val actual = saldoEnSuMoneda(deuda)
    // «pasa a» y no «→»: la flecha sale como ▯ en wasm (ver [deudaDespuesDelTraspaso]).
    return "Deuda de ${deuda.name}: ${signedMoney(actual, moneda)} " +
        "pasa a ${signedMoney(actual - desglose.capital, moneda)}"
}

/**
 * **Vuelta 49 · Movi no sabe cuánto se debe de este crédito**: su saldo en Movi es $0 (o a favor), o el
 * pago lo dejaría por debajo de cero. Pasaba con la demo: un crédito con términos pero sin
 * movimientos decía «Deuda de Vehículo 4083: $0 pasa a −$980.000». Una deuda nunca se muestra
 * negativa: en vez de la cuenta, el renglón dice [SIN_EL_SALDO_DEL_CREDITO] y ofrece cuadrarlo.
 *
 * Solo un préstamo: abonarle de más a una tarjeta sí la deja a favor, y eso es un dato real.
 *
 * @param bajaLaDeuda lo que el pago le quita a la deuda —el capital de la cuota, o el abono entero—, o
 *   `null` si todavía no se sabe (entonces solo cuenta el saldo de hoy).
 */
fun creditoSinSaldoEnMovi(deuda: Account?, bajaLaDeuda: Long?): Boolean {
    if (deuda == null || deuda.type != AccountType.LOAN) return false
    val actual = saldoEnSuMoneda(deuda)
    return actual <= 0L || (bajaLaDeuda != null && bajaLaDeuda > 0L && actual - bajaLaDeuda < 0L)
}

/**
 * Lo que dice el renglón de la deuda cuando Movi no tiene su saldo (vuelta 49). Vuelta 73: las mismas
 * palabras que la ficha del crédito y Patrimonio ([com.jvillada.movi.ui.credits.FALTA_EL_SALDO_DEL_CREDITO]).
 */
const val SIN_EL_SALDO_DEL_CREDITO: String = com.jvillada.movi.ui.credits.FALTA_EL_SALDO_DEL_CREDITO

/** La acción de ese renglón: abre el crédito, donde está su ajuste de saldo de siempre. */
const val CUADRAR_EL_CREDITO: String = com.jvillada.movi.ui.credits.CARGAR_EL_SALDO_DEL_CREDITO + " ›"

/** «Deuda de Nu: $1.683.000» — la deuda de hoy, antes de que haya monto con qué decir en qué queda. */
fun deudaDeHoy(deuda: Account?): String? {
    if (deuda == null) return null
    return "Deuda de ${deuda.name}: ${signedMoney(saldoEnSuMoneda(deuda), deuda.currency)}"
}

// ── Vuelta 48 · en Cuota, tocar un crédito es pagar su cuota ───────────────────────────────────

/**
 * **La cuota del mes que se propone al elegir un crédito en Cuota**: la de sus condiciones
 * (`CreditTerms.installment`, la misma que arma la fila sintética `credit_<id>` de Pagos), o `null`
 * si no es un crédito o no tiene cuota cargada. Una tarjeta no propone nada: su «monto» es el
 * saldo, no lo que se va a pagar (ver `hojaParaAnotar`).
 */
fun cuotaPropuesta(deuda: Account?, terms: CreditTerms?): Long? =
    terms?.installment?.takeIf { deuda?.type == AccountType.LOAN && it > 0L }

/**
 * La cuenta de la que el banco **debita solo** la cuota de este crédito
 * (`CreditTerms.debitoAutomaticoDesde`), si es una de las que se pueden poner en «Sale de».
 */
fun cuentaDelDebitoAutomatico(terms: CreditTerms?, elegibles: List<Account>): String? =
    terms?.debitoAutomaticoDesde?.takeIf { id -> elegibles.any { it.id == id } }

/**
 * **El encabezado de la hoja de Cuota** (vuelta 48), que dice qué se está anotando: «Cuota de Libre
 * inversión 9695 · octubre», «Abono extra a Libre inversión 9695», «Pago de Master Black · octubre»,
 * o —sin nada elegido— [CUOTA_SIN_ELEGIR]. Va siempre en un renglón: elegir qué pagar cambia el
 * texto, no el alto.
 */
fun encabezadoDeLaCuota(deuda: Account?, abonoExtra: Boolean, mes: String): String {
    if (deuda == null) return CUOTA_SIN_ELEGIR
    val delMes = if (mes.isBlank()) "" else " · $mes"
    return when {
        deuda.type == AccountType.LOAN && abonoExtra -> "Abono extra a ${deuda.name}"
        deuda.type == AccountType.LOAN -> "Cuota de ${deuda.name}$delMes"
        else -> "Pago de ${deuda.name}$delMes"
    }
}

/** El encabezado de Cuota sin nada elegido todavía. */
const val CUOTA_SIN_ELEGIR: String = "La cuota de un crédito o el pago de una tarjeta"

/**
 * El mes del período del dueño en que cae [hoy], en palabras («octubre»). Con corte, el 27 de
 * octubre ya es el período de noviembre. Sin ajustes (no se pudieron leer), el mes de calendario.
 */
fun mesDelPeriodoDeHoy(ajustes: com.jvillada.movi.shared.model.PeriodSettings?, hoy: Long): String {
    val periodo = com.jvillada.movi.shared.model.periodoDe(hoy, ajustes ?: com.jvillada.movi.shared.model.PeriodSettings())
    return com.jvillada.movi.ui.recurrentes.nombreDelMes(periodo.prefijo)
}

/** La ayuda de la sección «Créditos» del selector de «¿Qué estás pagando?» (vuelta 48). */
const val AYUDA_DE_CREDITOS_EN_CUOTA: String = "Elige el crédito para anotar su cuota de este mes."

/** La de la misma sección en Traspaso, donde un crédito sí es un desembolso o un abono. */
const val AYUDA_DE_CREDITOS_EN_TRASPASO: String =
    "Elige uno para registrar el desembolso que te entró a la cuenta, o un abono extraordinario que sale de ella."

/** La opción secundaria de la hoja de un crédito: pasar de la cuota a un abono extra, y volver. */
const val ES_UN_ABONO_EXTRA: String = "Es un abono extra, no la cuota"
const val ES_LA_CUOTA_DEL_MES: String = "Es la cuota del mes"

/** Lo que dice la hoja en modo abono: un traspaso puro, que no salda la cuota del período. */
const val QUE_ES_UN_ABONO_EXTRA: String =
    "Abono extra: plata de más que baja el capital. No es la cuota: no la marca como pagada."

/** Los tags del encabezado de Cuota, del renglón de la deuda y del enlace del abono (vuelta 48). */
internal const val TAG_ENCABEZADO_DE_LA_CUOTA = "cuota:encabezado"
internal const val TAG_RENGLON_DE_LA_DEUDA = "cuota:renglon-de-la-deuda"
internal const val TAG_ENLACE_DEL_ABONO = "cuota:enlace-del-abono"

/** Vuelta 62 · el renglón de las pastillas «Total del extracto» · «Mínimo» · «Otro monto» al pagar una tarjeta. */
internal const val TAG_PASTILLAS_DEL_PAGO_DE_LA_TARJETA = "cuota:pastillas-de-la-tarjeta"

/** Lo que se dice cuando no se pudieron cargar las condiciones del crédito para armar el desglose. */
const val SIN_CONDICIONES_PARA_EL_DESGLOSE: String =
    "No pudimos cargar las condiciones de este crédito, así que no podemos mostrarte cuánto de " +
        "la cuota baja la deuda. El pago se registra bien igual: Movi separa el interés al guardarlo."

/**
 * Igual que [FRACCION_DEL_VALOR_DE_LA_HOJA] en la hoja de un movimiento, y por el mismo motivo. Un poco más
 * generoso porque acá las etiquetas son más cortas («Desde», «Hacia») y no llevan más que su
 * aviso debajo.
 */
private const val FRACCION_VALOR_FILA_TRASPASO = 0.6f

/**
 * **Qué cuenta proponer del otro lado del traspaso.**
 *
 * El recuerdo natural del destino es [LastAccountStore.lastTransferToId] — salvo cuando el origen
 * que quedó elegido ES esa cuenta. Ahí lo que el dueño está armando es el traspaso de vuelta
 * (siempre fue Bancolombia→Nequi y ahora puso Desde=Nequi), así que el destino que tiene sentido
 * proponerle es el origen viejo, no la primera cuenta del alfabeto.
 *
 * Sin esto, el par recordado quedaba excluido de su propia sugerencia: el destino se resolvía con
 * `ultima = lastTransferToId` y `excluir = origen`, que son la misma cuenta, y caía en `PRIMERA`.
 * Se veía —con su «Por defecto»—, pero era una trampa para el dedo rápido: dos toques y el
 * traspaso salía hacia una cuenta que nadie eligió.
 */
private fun destinoSugerido(origenElegido: String?): String? =
    if (origenElegido != null && origenElegido == LastAccountStore.lastTransferToId) {
        LastAccountStore.lastTransferFromId
    } else {
        LastAccountStore.lastTransferToId
    }

/**
 * El cuerpo de la pestaña **Traspaso** de la hoja de Agregar.
 *
 * Es una hoja aparte de [EditorBody] y no un modo más del mismo formulario: un traspaso no tiene
 * categoría (la suya es reservada y nadie la elige) ni tipo (es siempre salida de una cuenta y
 * entrada a otra), y en cambio necesita dos cuentas en vez de una. Compartir el formulario habría
 * significado media docena de `if (esTraspaso)` adentro de cada fila.
 */
/**
 * Las dos cosas que este formulario sabe hacer. Son la MISMA operación —dos patas enlazadas, una
 * cuenta que da y otra que recibe— y por eso comparten toda la maquinaria de selectores, alturas y
 * reintentos, que ya se llevó nueve rondas de arreglos.
 *
 * Lo que cambia es: qué cuentas se ofrecen de cada lado, cómo se llaman los dos lados, y qué
 * endpoint recibe el resultado. Nada más.
 */
internal enum class ModoDeTraspaso {
    /** Mover plata entre cuentas propias, o el desembolso/abono de un crédito. */
    TRASPASO,

    /**
     * Pagar la cuota de un crédito o el extracto de una tarjeta.
     *
     * Nace de un pedido del dueño: *«necesito poder agregar un tipo de movimiento que sea pago de
     * cuota, que pueda asociar a un crédito o tarjeta, y que vos sepas cómo manejarlo por
     * debajo»*. Antes la misma acción tenía dos mecánicas incompletas: un crédito se pagaba con un
     * traspaso (la deuda bajaba pero la cuota no aparecía en los gastos del mes) y una tarjeta ni
     * siquiera admitía traspaso — se anotaba como gasto y la deuda quedaba igual.
     */
    PAGO_DE_CUOTA,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransferBody(
    accounts: List<Account>,
    accountsLoaded: Boolean,
    presetAccountId: String? = null,
    /**
     * El hueco donde este cuerpo se VE, que en una pantalla corta es más chico que el cuerpo.
     * Es el tope del alto que se le fija al sub-picker de cuentas: sin él, el mínimo era el alto
     * del formulario medido con altura infinita y el sub-picker quedaba más alto que la pantalla
     * — la losa vacía. `Dp.Unspecified` (el default) = sin tope, que es el comportamiento viejo.
     */
    alturaVisible: Dp = Dp.Unspecified,
    /**
     * Avisa que se abrió (`true`) o se cerró (`false`) el sub-picker de cuentas, para que
     * `QuickAddScreen` guarde y restaure el desplazamiento de la hoja igual que en Gasto/Ingreso.
     * `picking` es estado de acá adentro, así que sin este aviso el efecto de allá no se entera
     * y la hoja queda corrida bajo el dedo al volver.
     *
     * También se llama con `false` cuando este cuerpo SALE DE COMPOSICIÓN con el sub-picker
     * abierto — el `DisposableEffect` de más abajo—, porque ahí `picking` deja de existir sin
     * que nadie lo cierre.
     */
    onPickerAbierto: (Boolean) -> Unit = {},
    /**
     * Sube cada vez que la hoja pide cerrar el sub-picker abierto (Escape en la web). Un contador y
     * no un `Boolean`: dos Escape seguidos son dos pedidos, y ninguno queda «pegado».
     */
    pedidosDeCerrarPicker: Int = 0,
    modo: ModoDeTraspaso = ModoDeTraspaso.TRASPASO,
    /**
     * La deuda que se viene a pagar, cuando la hoja se abrió desde la fila de una cuota o una
     * tarjeta del checklist («Anotar este pago»). Solo vale en [ModoDeTraspaso.PAGO_DE_CUOTA] y
     * solo si esa deuda está entre las que se pueden pagar; si no, el lado «Hacia» arranca vacío
     * como siempre.
     */
    presetDestinoId: String? = null,
    /**
     * Vuelta 47 · **el monto, como en Gasto**: los dígitos escritos (los guarda la hoja, que los
     * escribe con [onKey] desde el teclado en pantalla y el físico), en la moneda de la cuenta de
     * origen. Era un campo de texto chico.
     */
    monto: String = "",
    onKey: (String) -> Unit = {},
    /** Sin teclado en pantalla (frente a un computador): la cifra con el cursor, como en Gasto. */
    conTecladoEnPantalla: Boolean = true,
    /** Sube con cada Enter del teclado físico: guarda, si se puede (como el botón). */
    pedidosDeGuardar: Int = 0,
    /** Un campo de texto de esta hoja (el interés, el monto en la moneda de la deuda) tomó o soltó el foco. */
    onCampoDeTextoConFoco: (Boolean) -> Unit = {},
    /**
     * Vuelta 48 · el mes de la cuota que se viene a pagar, cuando la hoja se abrió desde su fila de
     * Pagos («Anotar pago»): el encabezado dice «Cuota de X · septiembre» aunque hoy ya sea octubre.
     * `null` = el mes del período de hoy.
     */
    presetMesDeLaCuota: String? = null,
    /**
     * Vuelta 48 · la hoja propone un monto (la cuota del crédito elegido): lo escribe en la cifra,
     * que vive en [QuickAddScreen] como los dígitos tecleados.
     */
    onMontoPropuesto: (String) -> Unit = {},
    /**
     * Vuelta 71 (revisión) · **la cifra que la hoja puso sola** (la cuota del crédito elegido), aparte
     * de [onMontoPropuesto] (lo que el dueño eligió tocando una pastilla). La hoja de Agregar no la
     * cuenta como escrita: no viaja a Gasto ni pregunta «¿Descartar?». `null` = la misma que
     * [onMontoPropuesto].
     */
    onMontoPropuestoPorLaHoja: ((String) -> Unit)? = null,
    /**
     * Vuelta 49 · «Cuadrar ›» del renglón de un crédito cuyo saldo Movi no tiene: lleva a donde se
     * ajusta (Créditos). `null` deja el renglón sin acción.
     */
    onCuadrarElCredito: (() -> Unit)? = null,
    /**
     * Vuelta 63 · **«Anotar este abono»**: la ocurrencia del abono mensual que este guardado salda.
     * Guardado el traspaso (o el pago de la tarjeta), se sella con la pata que salió de la cuenta —la
     * única puerta por la que el server acepta un traspaso como ocurrencia—. Si el sello no se puede
     * poner, el traspaso queda igual: Movi lo empareja solo si el monto está a ±5 %.
     */
    abonoQueSalda: AbonoDeLaRegla? = null,
    /**
     * Vuelta 71 · **la fecha y la nota son las de la hoja**, no de esta pestaña: viajan con el monto al
     * cambiar entre Gasto, Ingreso, Traspaso y Pagar. `null` = esta pestaña las lleva sola (como antes).
     */
    fechaDeLaHoja: LocalDate? = null,
    onFecha: (LocalDate) -> Unit = {},
    notaDeLaHoja: String? = null,
    onNota: (String) -> Unit = {},
    /** Vuelta 71: la moneda de la cifra (la de la cuenta de origen), para «¿Descartar …?» de la hoja. */
    onMonedaDelMonto: (String) -> Unit = {},
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    // Guardar un traspaso o una cuota vibra, igual que un gasto (ver [Vibrador]).
    val vibrador = rememberVibrador()
    val esPago = modo == ModoDeTraspaso.PAGO_DE_CUOTA
    // En un pago, los dos lados ofrecen cosas distintas: de dónde sale la plata son cuentas de
    // dinero, y lo que se paga son deudas. En un traspaso, la misma lista para los dos.
    val elegibles = remember(accounts, esPago) {
        if (esPago) cuentasPara(accounts, UsoDeCuenta.DINERO_PROPIO).principales
        else transferableAccounts(accounts)
    }
    /** Lo que se puede pagar: préstamos y tarjetas. Vacío si el dueño no tiene ninguno. */
    val deudas = remember(accounts) {
        cuentasPara(accounts, UsoDeCuenta.DEUDA_QUE_SE_PAGA).principales
    }
    /** El lado «hacia» ofrece deudas en un pago, y las cuentas normales en un traspaso. */
    val elegiblesDestino = if (esPago) deudas else elegibles
    // Ola 14: de acá salen los valores POR DEFECTO, y a propósito no incluye los créditos — ver
    // [defaultTransferAccounts]. `elegibles` (que sí los trae) es lo que se ofrece en el selector
    // y lo que valida el botón.
    val paraDefecto = remember(elegibles) { defaultTransferAccounts(elegibles) }

    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var origenFrom by remember { mutableStateOf(OrigenCuenta.NINGUNA) }
    var origenTo by remember { mutableStateOf(OrigenCuenta.NINGUNA) }

    /**
     * **Ola 11 — el traspaso también recuerda su par, y también deja de depender del orden.**
     *
     * Antes esto era `elegibles.firstOrNull()` y `elegibles.getOrNull(1)`, o sea las dos
     * primeras cuentas de una lista que nadie ordenaba: el mismo problema que la fila «Cuenta»
     * del editor, duplicado, y con la vuelta de tuerca de que acá **dos cuentas equivocadas**
     * salen mal de una y entran mal en otra.
     *
     * El par origen→destino se repite (siempre el mismo banco al mismo bolsillo), así que se
     * recuerda entero y aparte del de un movimiento — ver `LastAccountStore`, donde está escrito
     * por qué un traspaso no puede definir «la última cuenta usada» de un gasto.
     *
     * El destino se resuelve **excluyendo** el origen: no hay traspaso de una cuenta a sí misma,
     * y ofrecerlo para después bloquear el botón sería peor que no ofrecerlo.
     *
     * Corre cuando cambia la lista de cuentas (que llega después de que esta pestaña se compuso)
     * y respeta lo que el dueño haya elegido a mano, salvo que esa cuenta ya no exista.
     */
    // Se reejecuta también cuando cambia el DESTINO elegible: al pasar de Traspaso a Cuota la
    // lista del lado «hacia» cambia entera, y lo que había quedado seleccionado ya no sirve.
    // Vuelta 62: el destino que trajo la hoja en Traspaso (un abono a un crédito), si es elegible.
    val destinoDelTraspaso = if (esPago) null else elegiblesDestino.firstOrNull { it.id == presetDestinoId }
    LaunchedEffect(elegibles, elegiblesDestino) {
        // Vuelta 48: la cuenta del débito automático del crédito elegido tampoco se pisa.
        val origenFirme = (origenFrom == OrigenCuenta.ELEGIDA || origenFrom == OrigenCuenta.DEBITO_AUTOMATICO) &&
            elegibles.any { it.id == fromId }
        if (!origenFirme) {
            val elegida = resolverCuenta(
                cuentas = paraDefecto,
                contexto = presetAccountId,
                ultima = LastAccountStore.lastTransferFromId,
                // Ola A: tiene sentido solo del lado «Desde» — es de ahí que sale la plata, que
                // es justo lo que mide `cuentaMasUsada` (gastos de los últimos 30 días). El
                // destino («Hacia») no tiene equivalente: no hay «cuenta más recibida».
                masUsada = CuentaMasUsadaCache.id,
            )
            fromId = elegida.id
            origenFrom = elegida.origen
        }
        // **El destino se valida contra SU lista, no contra la del origen.**
        //
        // Decía `elegibles.any { it.id == toId }`, y en un pago el destino es una deuda — que por
        // definición NUNCA está en `elegibles`. O sea que `destinoFirme` era siempre false, con
        // dos consecuencias que la revisión midió: al entrar, `toId` quedaba seteado en una cuenta
        // de dinero que no está en la lista del destino, así que la fila decía «Elegir cuenta»
        // **con el aviso «Por defecto» al lado** —contradiciéndose sola—; y cualquier
        // reejecución del efecto borraba la deuda que el dueño ya había elegido.
        val destinoFirme = origenTo == OrigenCuenta.ELEGIDA &&
            elegiblesDestino.any { it.id == toId } && toId != fromId
        if (!destinoFirme) {
            val elegida = resolverCuenta(
                // En un pago no hay «par recordado» que proponer: la deuda que se paga la elige el
                // dueño con el dedo. `defaultTransferAccounts` excluye las deudas justamente para
                // que un crédito no quede preseleccionado por accidente, y esa disciplina vale
                // igual acá — un pago de $9.147.408 que nadie pidió sería peor que un toque más.
                //
                // La única excepción es que el dueño YA la eligió en otra pantalla: tocó «Anotar
                // este pago» en la fila de ESA cuota. Ahí la deuda llega por [presetDestinoId], y
                // solo esa entra a la lista.
                //
                // Vuelta 62: en Traspaso también, cuando la hoja se abrió con un destino («Anotar este
                // abono» desde el simulador): el crédito va en «Hacia» aunque los valores por defecto
                // nunca propongan uno.
                cuentas = when {
                    esPago -> elegiblesDestino.filter { it.id == presetDestinoId }
                    destinoDelTraspaso != null -> listOf(destinoDelTraspaso)
                    else -> paraDefecto
                },
                contexto = if (esPago || destinoDelTraspaso != null) presetDestinoId else null,
                ultima = if (esPago) null else destinoSugerido(fromId),
                excluir = fromId,
            )
            toId = elegida.id
            origenTo = elegida.origen
        }
    }

    // ── Las condiciones de los créditos, para poder mostrar el desglose de la cuota ───────────
    //
    // Solo se piden en la pestaña Cuota, y una sola vez: `getCredits()` pasa por la caché del
    // repositorio local, así que en el teléfono no cuesta un viaje por cada vez que se abre.
    //
    // **«No pudimos cargar» y «este crédito no tiene tasa» no son lo mismo**, y confundirlos acá
    // sería mentirle sobre sus condiciones — el mismo error que `CreditTermsSheet` ya tuvo que
    // separar con `falloCargarCuentas`. Sin este estado aparte, un corte de red le habría dicho
    // «este crédito no tiene tasa registrada» sobre un crédito que sí la tiene.
    var terminosPorCuenta by remember { mutableStateOf<Map<String, CreditTerms>>(emptyMap()) }
    var terminosCargados by remember { mutableStateOf(false) }
    var falloCargarTerminos by remember { mutableStateOf(false) }
    // Vuelta 48: los ajustes del período, para decir de qué mes es la cuota («Cuota de X · octubre»).
    // Del cache de lecturas si está; si no, se piden. Sin ellos, el mes de calendario.
    var ajustesDelPeriodo by remember { mutableStateOf<com.jvillada.movi.shared.model.PeriodSettings?>(null) }
    LaunchedEffect(esPago) {
        if (!esPago) return@LaunchedEffect
        falloCargarTerminos = false
        runCatching { Repositories.wallets.getCredits() }
            .onSuccess { creditos ->
                terminosPorCuenta = creditos.mapNotNull { it.terms }.associateBy { it.accountId }
            }
            .onFailure { falloCargarTerminos = true }
        terminosCargados = true
        if (presetMesDeLaCuota.isNullOrBlank()) {
            val ahora = Clock.System.now().toEpochMilliseconds()
            val perfil = com.jvillada.movi.data.CacheDeLecturas.ultima(com.jvillada.movi.data.ClaveDeLectura.Perfil, ahora)
                ?: runCatching { Repositories.wallets.getUserProfile() }.getOrNull()
            ajustesDelPeriodo = perfil?.ajustesDelPeriodo()
        }
    }

    // Vuelta 62 · las tarjetas, por su extracto del corte: las pastillas «Total del extracto $X» y
    // «Mínimo $Y» al pagar una. Si no se pueden leer, la hoja sigue igual (sin pastillas).
    var tarjetasPorCuenta by remember { mutableStateOf<Map<String, CardSummary>>(emptyMap()) }
    LaunchedEffect(esPago) {
        if (!esPago) return@LaunchedEffect
        runCatching { Repositories.wallets.getCards() }
            .onSuccess { tarjetas -> tarjetasPorCuenta = tarjetas.associateBy { it.account.id } }
    }

    val amount: Long? = monto.toLongOrNull()?.takeIf { it > 0 }
    /** Lo que bajó la deuda en su moneda, cuando se paga una tarjeta desde una cuenta de otra moneda. */
    var montoEnLaDeuda by remember { mutableStateOf<Long?>(null) }
    // Ola 13 — LA FECHA DEL TRASPASO SE ELIGE, NO SE ESCRIBE.
    //
    // Acá había un campo de texto donde se tecleaba «AAAA-MM-DD» a mano, con su
    // `filterDateInput` y su «La fecha tiene que ser AAAA-MM-DD» cuando no se acertaba. Era la
    // única forma de poner una fecha en toda la app, y dejarla convivir en la MISMA hoja con el
    // selector nuevo de Gasto/Ingreso habría sido peor que no tocar nada: dos maneras distintas
    // de decir lo mismo, a dos toques de distancia.
    //
    // Con el selector, `transferTimestampFor` y `transferMissingMessage` quedaron sin llamador y
    // se fueron con el campo: la rama «La fecha tiene que ser AAAA-MM-DD» de esa validación era
    // inalcanzable —el selector no puede producir una fecha inválida— y un mensaje de formato
    // sobre un formato correcto es peor que no tener mensaje. Lo que queda valida el traspaso en
    // sí ([validateTransfer], el mismo texto que devuelve el server en su 422).
    val hoy = remember { hoyEnAppZone() }
    var fechaPropia by remember { mutableStateOf(hoy) }
    var notaPropia by remember { mutableStateOf("") }
    // Vuelta 71: con la hoja dueña de la fecha y la nota, se leen y se escriben allá.
    val fecha: LocalDate = fechaDeLaHoja ?: fechaPropia
    val note: String = notaDeLaHoja ?: notaPropia
    fun ponerFecha(valor: LocalDate) { if (fechaDeLaHoja != null) onFecha(valor) else fechaPropia = valor }
    fun ponerNota(valor: String) { if (notaDeLaHoja != null) onNota(valor) else notaPropia = valor }
    // Vuelta 47: la nota se escribe en su sub-editor, como en Gasto (la fila «Nota · Agregar nota…»).
    var editandoNota by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<TransferSide?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    // El aviso va ANTES de cambiar `picking`, en el toque: para cuando el estado cambie y la hoja
    // se vuelva a medir, el desplazamiento viejo ya está guardado del otro lado.
    fun abrirPicker(lado: TransferSide) {
        onPickerAbierto(true)
        picking = lado
    }

    fun cerrarPicker() {
        picking = null
        onPickerAbierto(false)
    }

    // Ola 13: el sub-picker de fecha pasa por el MISMO aviso que el de cuentas, y por el mismo
    // motivo. No es adorno: es otro sub-picker que reemplaza este cuerpo adentro del `Box` de
    // alto fijado, así que abrirlo con la hoja desplazada recorta el desplazamiento igual que
    // «Desde». Sin el aviso, `hayPicker` no cambia allá, el efecto no guarda ni restaura, y el
    // teclado vuelve corrido bajo el dedo — el bug caro, por un camino nuevo.
    //
    // Los dos reflejos no se pisan porque los dos sub-pickers no pueden estar abiertos a la vez:
    // la fecha se abre desde el formulario, que solo se ve con `picking == null`.
    fun abrirFecha() {
        onPickerAbierto(true)
        pickingDate = true
    }

    fun cerrarFecha() {
        pickingDate = false
        onPickerAbierto(false)
    }

    fun abrirNota() {
        onPickerAbierto(true)
        editandoNota = true
    }

    fun cerrarNota() {
        editandoNota = false
        onPickerAbierto(false)
    }

    // ── El segundo cerrojo: si este cuerpo desaparece, su picker desaparece con él ──────
    //
    // `picking` muere cuando este `@Composable` sale de composición, pero el reflejo que
    // [QuickAddScreen] mantiene NO se entera solo — y un reflejo pegado en «abierto» mata la
    // restauración del desplazamiento en las tres pestañas (ver [PickersDeLaHoja]). El camino
    // que se midió es tocar «Gasto» con «Desde» abierto, y ese ya lo cierra `conTipo` allá,
    // que es donde tiene que estar: el estado se arregla en la misma composición y se puede
    // afirmar en una prueba. Esto es el cinturón además de los tirantes, para cualquier otra
    // salida de composición que alguien agregue mañana (una condición nueva alrededor de este
    // cuerpo, un formulario que se reemplace mientras carga) sin acordarse de este reflejo.
    //
    // `rememberUpdatedState` porque `onDispose` corre fuera de la composición y no puede
    // depender de qué instancia del lambda quedó capturada.
    LaunchedEffect(pedidosDeCerrarPicker) {
        if (pedidosDeCerrarPicker == 0) return@LaunchedEffect
        when {
            picking != null -> cerrarPicker()
            pickingDate -> cerrarFecha()
            editandoNota -> cerrarNota()
        }
    }

    val avisar by rememberUpdatedState(onPickerAbierto)
    DisposableEffect(Unit) { onDispose { avisar(false) } }
    // Los ids viven en el borrador, NO adentro de `save()`: un reintento tras un fallo tiene que
    // llevar los mismos, o el server crea un traspaso duplicado (ver [TransferDraftIds]).
    var ids by remember { mutableStateOf(TransferDraftIds.new()) }

    val from = elegibles.firstOrNull { it.id == fromId }
    val to = elegiblesDestino.firstOrNull { it.id == toId }

    // ── El interés de este mes: prellenado con la estimación, corregible con el extracto ──────
    //
    // Dos estados y no uno, porque «no lo tocó» y «lo borró» tienen que mandar cosas distintas
    // al server. `interesEscrito` es lo que hay en el campo desde que el dueño lo tocó;
    // `interesTocado` dice si lo tocó. Mientras no lo toque, el campo muestra la estimación y
    // la petición lleva `interesReal = null` —que el server estime, con su saldo vivo—: NO se
    // manda la estimación redondeada de la hoja como si fuera un dato del dueño, porque el server
    // la recalcula contra los eventos del instante de guardar y la de la hoja puede estar vieja.
    //
    // Si lo tocó y lo dejó vacío, también va `null`: vacío es «no sé», y «no sé» es «estímalo».
    // Se resetea al cambiar de deuda —el interés de un crédito no sirve para otro— pero NO al
    // cambiar el monto: el interés real depende del saldo del banco, no de cuánto pagó.
    var interesEscrito by remember { mutableStateOf<Long?>(null) }
    var interesTocado by remember { mutableStateOf(false) }
    // ── Vuelta 48 · un crédito en Cuota es su cuota del mes; el abono extra es la opción secundaria ──
    //
    // `abonoExtra` vale solo sobre un crédito: el enlace «Es un abono extra, no la cuota» lo prende y
    // la hoja pasa a guardar un TRASPASO puro (el de siempre: baja el capital, no salda la cuota del
    // período, no cuenta en el mes). Se apaga al cambiar de deuda: el abono es de ESE crédito.
    var abonoExtra by remember { mutableStateOf(false) }
    LaunchedEffect(toId) {
        interesEscrito = null
        interesTocado = false
        abonoExtra = false
    }
    val termsDelDestino = to?.let { terminosPorCuenta[it.id] }
    val esCredito = esPago && to?.type == AccountType.LOAN
    val esAbono = esCredito && abonoExtra
    /** Lo que se guarda: la cuota (dos patas, salda el período) o, con el abono extra, un traspaso. */
    val guardaUnaCuota = esPago && !esAbono

    // **La cuota del mes, propuesta.** Al elegir un crédito la cifra pasa a ser su cuota
    // (`cuotaPropuesta`) y «Sale de», la cuenta del débito automático si la tiene. Se respeta lo que
    // el dueño ya escribió: la cifra solo se pisa si estaba vacía o si era la que propuso la hoja
    // (así cambiar de crédito cambia la cuota, y pasar al abono la limpia). Las condiciones llegan
    // después de las cuentas, así que la propuesta se rehace cuando llegan.
    var montoPropuesto by remember { mutableStateOf<String?>(null) }
    val montoActual by rememberUpdatedState(monto)
    val proponerMonto by rememberUpdatedState(onMontoPropuestoPorLaHoja ?: onMontoPropuesto)
    LaunchedEffect(toId, terminosCargados, abonoExtra) {
        if (!esPago) return@LaunchedEffect
        val deuda = elegiblesDestino.firstOrNull { it.id == toId } ?: return@LaunchedEffect
        if (deuda.type != AccountType.LOAN) return@LaunchedEffect
        val terms = terminosPorCuenta[deuda.id]
        val escritoPorLaHoja = montoActual.isBlank() || montoActual == montoPropuesto
        val cuota = if (abonoExtra) null else cuotaPropuesta(deuda, terms)?.toString()
        if (escritoPorLaHoja) {
            if (cuota != null && cuota != montoActual) proponerMonto(cuota)
            else if (cuota == null && montoActual.isNotBlank()) proponerMonto("")
            montoPropuesto = cuota
        }
        val debito = cuentaDelDebitoAutomatico(terms, elegibles)
        if (debito != null && origenFrom != OrigenCuenta.ELEGIDA && origenFrom != OrigenCuenta.CONTEXTO) {
            fromId = debito
            origenFrom = OrigenCuenta.DEBITO_AUTOMATICO
        }
    }
    val interesEstimado = interesEstimadoDelMes(to, termsDelDestino)
    /** Lo que va en la petición: el que escribió, o `null` para que el server estime. */
    val interesEfectivo = if (interesTocado) interesEscrito else null
    /**
     * El rechazo del interés, con las mismas palabras que daría el server. Se muestra junto al
     * campo y apaga el botón; NO va en `missing` porque ese renglón mide una línea y este mensaje
     * trae tres cifras.
     */
    val errorDeInteres = if (guardaUnaCuota && to != null) {
        validarInteresReal(
            interesEfectivo,
            amount ?: 0L,
            to.type,
            termsDelDestino?.insuranceMonthly,
            termsDelDestino?.otrosCargosMensuales,
            // Ver `desgloseDelPago`: la hoja no sabe qué se cobró ya este mes, así que valida como
            // si este fuera el primer pago de la cuota.
            yaCobradoEnElMes = 0L,
        )
    } else {
        null
    }

    val missing = if (guardaUnaCuota) {
        validarPagoDeCuota(
            pagoRequestFor(ids, from?.id.orEmpty(), to?.id.orEmpty(), amount ?: 0L, 0L, note, null, montoEnLaDeuda),
            from,
            to,
        )
    } else {
        validateTransfer(from, to, amount ?: 0L)
    }
    val canSave = missing == null && errorDeInteres == null && !saving

    /**
     * El aviso de que el server repartió la cuota distinto de lo que esta hoja mostró, cuando lo
     * hubo. Mientras vale algo, este cuerpo dibuja **la confirmación en vez del formulario**: el
     * pago ya se guardó, así que dejar el formulario a mano invitaría a guardarlo dos veces.
     */
    var avisoDelDesglose by remember { mutableStateOf<String?>(null) }

    fun save() {
        val origen = from ?: return
        val destino = to ?: return
        // Lo que la hoja le PROMETIÓ, capturado antes de salir: si el server escribe otro reparto
        // hay que poder decir cuál era el que él leyó. Se calcula igual que el renglón de abajo.
        val desgloseMostrado = if (guardaUnaCuota) desgloseDelPago(destino, terminosPorCuenta[destino.id], if (origen.currency != destino.currency) montoEnLaDeuda else amount, interesEfectivo) else null
        // Con «Hoy» (el default) queda la hora real, como siempre; cualquier otro día va al
        // mediodía de Bogotá — ver [timestampParaFecha] y [epochAlMediodia].
        val timestamp = timestampParaFecha(fecha, hoy)
        saving = true
        error = null
        // Vuelta 63: los ids de ESTE guardado, para sellar el abono con la pata que salió.
        val idsDelGuardado = ids
        coroutine.launch {
            val result = runCatching {
                // El abono extra de la vuelta 48 es el traspaso de siempre (`transferLegsFor`).
                if (guardaUnaCuota) {
                    Repositories.wallets.payInstallment(
                        pagoRequestFor(ids, origen.id, destino.id, amount ?: 0L, timestamp, note, interesEfectivo, montoEnLaDeuda),
                    )
                } else {
                    Repositories.wallets.createTransfer(
                        transferRequestFor(ids, origen, destino, amount ?: 0L, timestamp, note),
                    )
                }
            }
            // Vuelta 63: guardado (o ya guardado antes, un 409), el abono mensual queda sellado.
            if (abonoQueSalda != null && (result.isSuccess || result.exceptionOrNull()?.let(::isAlreadyRegistered) == true)) {
                sellarElAbono(abonoQueSalda, idsDelGuardado.fromEventId)
            }
            saving = false
            result
                .onSuccess { respuesta ->
                    // Ids nuevos recién ACÁ: el traspaso siguiente es otro traspaso. Mientras el
                    // anterior no haya llegado, cada reintento tiene que ser el mismo pedido.
                    ids = TransferDraftIds.new()
                    // Ola 11: el próximo traspaso arranca con este mismo par. Igual que en el
                    // editor de un movimiento, solo después de que el server lo confirmó.
                    //
                    // Un PAGO no lo recuerda, y no es un olvido: pisaría el par del traspaso con
                    // una deuda. Medido por la revisión — si siempre traspasás Bancolombia→Nequi y
                    // pagás la cuota del carro, el destino recordado pasa a ser la Libranza, que
                    // `defaultTransferAccounts` excluye, así que la sugerencia se pierde y el
                    // traspaso vuelve a proponer la primera cuenta de la lista.
                    if (!esPago) LastAccountStore.recordTransfer(origen.id, destino.id)
                    // **El desglose que de verdad quedó escrito.** El server lo recalcula contra
                    // los eventos vivos, así que puede no ser el que esta hoja mostró — y hasta
                    // acá nadie leía esa respuesta: la promesa de «trae el que de verdad quedó»
                    // no llegaba a ninguna pantalla. Ver [avisoDeDesgloseDistinto].
                    val aviso = (respuesta as? PagoDeCuotaResult)?.let {
                        avisoDeDesgloseDistinto(desgloseMostrado, it.desglose, destino.currency)
                    }
                    // Sin diferencia, el camino de siempre: se cierra la hoja y listo. Con
                    // diferencia, se muestra y él la cierra con el dedo — un cartel que se va solo
                    // no es un aviso sobre plata.
                    vibrador.confirmar()
                    if (aviso != null) avisoDelDesglose = aviso else onSaved()
                }
                .onFailure { fallo ->
                    // Un 409 no es un fallo: quiere decir que el traspaso YA quedó registrado
                    // (esta es la respuesta del server a un reintento con los mismos ids). Se
                    // cierra la hoja igual que en el camino feliz — decirle «revisa tu conexión»
                    // a alguien cuyo traspaso sí se guardó es lo que lo empuja a guardarlo otra vez.
                    if (isAlreadyRegistered(fallo)) {
                        ids = TransferDraftIds.new()
                        // El traspaso SÍ quedó registrado, así que este par cuenta como usado
                        // igual que en el camino feliz. Un pago, por lo mismo de arriba, no.
                        if (!esPago) LastAccountStore.recordTransfer(origen.id, destino.id)
                        onSaved()
                    } else {
                        error = fallo.toUserMessage()
                    }
                }
        }
    }

    // Vuelta 47: Enter del teclado físico guarda, como el botón (y solo si el botón se puede tocar).
    LaunchedEffect(pedidosDeGuardar) {
        if (pedidosDeGuardar > 0 && canSave && avisoDelDesglose == null) save()
    }

    // Ola 8 · V2 (N4 de la revisión) — la pestaña Traspaso recibe LOS MISMOS DOS ARREGLOS.
    //
    // Se los había perdido por un detalle de estructura: este `TransferAccountPicker` y su
    // `return` viven ADENTRO de la rama `Picker.None` de QuickAddScreen, así que el `Box` de
    // alto fijado que arregla los sub-pickers de Gasto/Ingreso no llegaba hasta acá. El bug
    // original seguía intacto una pestaña más allá: abrir «Desde» encogía la hoja de altura
    // completa a una franja, y cerrarla la estiraba de golpe.
    //
    // Mismo remedio, aplicado localmente: se mide el alto del formulario y se le pone como
    // mínimo al picker de cuentas, así la hoja no cambia de alto al elegir una cuenta.
    var formHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // Ola 12 (revisión): el mínimo se acota al hueco visible. `formHeightPx` se mide con altura
    // infinita —este cuerpo vive adentro del scroll de la hoja—, así que en una pantalla corta
    // vale más que la pantalla: fijarlo tal cual dejaba el sub-picker de cuentas más alto que el
    // hueco y se abría mostrando el final de la lista, sin el título «Desde»/«Hacia» ni su X.
    // Cuando el formulario entra, `alturaVisible` es su propio alto y esto no cambia nada.
    val altoFijado = with(density) { formHeightPx.toDp() }
        .let { if (alturaVisible == Dp.Unspecified) it else minOf(it, alturaVisible) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (picking == null && !pickingDate && !editandoNota) Modifier
                else Modifier.heightIn(min = altoFijado),
            ),
    ) {
        val avisoGuardado = avisoDelDesglose
        if (avisoGuardado != null) {
            // El pago YA se guardó y el reparto no fue el que esta hoja mostró. Se dibuja la
            // confirmación **en vez del formulario** para que no exista la posibilidad de tocar
            // «Registrar pago» otra vez sobre un pago que ya está hecho.
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(Espacio.m))
                Text(avisoGuardado, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                Spacer(Modifier.height(Espacio.l))
                Boton("Entendido", onClick = { avisoDelDesglose = null; onSaved() }, anchoCompleto = true)
                Spacer(Modifier.height(Espacio.m))
            }
        } else if (editandoNota) {
            NoteEditor(
                initial = note,
                onSave = { ponerNota(it); cerrarNota() },
                onVolver = { cerrarNota() },
            )
        } else if (pickingDate) {
            // Mismo sub-picker que la pestaña Gasto/Ingreso, adentro del mismo Box de alto
            // fijado: abrirlo no cambia el alto de la hoja.
            Column(modifier = Modifier.fillMaxWidth()) {
                PickerHeader("Fecha", onVolver = { cerrarFecha() })
                SelectorDeFecha(
                    seleccionada = fecha,
                    hoy = hoy,
                    onPick = { ponerFecha(it); cerrarFecha() },
                    enabled = !saving,
                )
                Spacer(Modifier.height(Espacio.xs))
            }
        } else if (picking != null) {
            TransferAccountPicker(
                title = when {
                    picking == TransferSide.FROM && esPago -> "¿De dónde sale la plata?"
                    picking == TransferSide.FROM -> "Desde"
                    esPago -> QUE_PAGAS
                    else -> "Hacia"
                },
                accounts = if (picking == TransferSide.FROM) elegibles else elegiblesDestino,
                selectedId = if (picking == TransferSide.FROM) fromId else toId,
                // Vuelta 48: en Cuota, un crédito es su cuota del mes — no un desembolso.
                ayudaDeCreditos = if (esPago) AYUDA_DE_CREDITOS_EN_CUOTA else AYUDA_DE_CREDITOS_EN_TRASPASO,
                onPick = { id ->
                    if (picking == TransferSide.FROM) {
                        fromId = id
                        origenFrom = OrigenCuenta.ELEGIDA
                        // Ola 11: si el destino era justo esa cuenta, se corre a otra en vez de
                        // dejar el formulario trabado en «Elige dos cuentas distintas» con el
                        // dueño teniendo que adivinar cuál de las dos filas arreglar. El cambio
                        // se ve —la fila «Hacia» pasa a decir otro nombre, con su aviso de que
                        // lo eligió la app— así que no es una decisión escondida.
                        if (toId == id) {
                            val reemplazo = resolverCuenta(
                                // Sobre `paraDefecto` y no sobre `elegibles`: lo que la app elige
                                // sola nunca puede ser un crédito (ver [defaultTransferAccounts]).
                                cuentas = paraDefecto,
                                // [destinoSugerido]: si la cuenta que acaba de pasar a origen era
                                // el destino habitual, lo que se propone es el traspaso de vuelta.
                                ultima = destinoSugerido(id),
                                excluir = id,
                            )
                            toId = reemplazo.id
                            origenTo = reemplazo.origen
                        }
                    } else {
                        // **Anotado, no arreglado (revisión de la Ola 12; es de master).** Esta
                        // rama NO tiene la guarda simétrica de la de arriba: si en «Hacia» se
                        // elige la MISMA cuenta que está en «Desde», las dos filas quedan iguales
                        // y el formulario se traba en «Elige dos cuentas distintas», con el dueño
                        // teniendo que adivinar cuál de las dos cambiar. Arreglarlo pide decidir
                        // qué se mueve —¿el origen, como hace la otra rama al revés?— y eso es
                        // producto, no geometría; no entra en la tanda del scroll.
                        toId = id
                        origenTo = OrigenCuenta.ELEGIDA
                    }
                    cerrarPicker()
                },
                onClose = { cerrarPicker() },
            )
        } else {
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { formHeightPx = it.height }) {

        // `paraDefecto.isEmpty()` además del conteo: con dos créditos y ninguna cuenta de dinero
        // hay dos elegibles, pero [validateTransfer] rechaza crédito↔crédito, así que el
        // formulario se abría con las dos puntas vacías y sin salida. El mensaje de abajo dice
        // exactamente lo que falta.
        // En modo pago la condición es OTRA: hace falta UNA cuenta de dinero y UNA deuda, no dos
        // cuentas propias. Con la condición del traspaso, alguien con Bancolombia + AMEX +
        // Libranza tenía `elegibles.size == 1` y la pestaña no se dibujaba — mostrando además el
        // mensaje que dice «el pago de la tarjeta se anota como gasto», o sea lo contrario de lo
        // que esta pestaña vino a hacer.
        val faltanCuentas = if (esPago) elegibles.isEmpty() || deudas.isEmpty()
        else elegibles.size < 2 || paraDefecto.isEmpty()
        if (accountsLoaded && faltanCuentas) {
            Spacer(Modifier.height(Espacio.m))
            Text(
                text = when {
                    esPago && deudas.isEmpty() ->
                        "Para pagar una cuota necesitas tener cargado el crédito o la tarjeta. " +
                            "Se cargan en Créditos."
                    esPago ->
                        "Para pagar una cuota necesitas una cuenta de dinero de donde salga la plata."
                    else ->
                        "Un traspaso necesita dos cuentas tuyas. Un crédito sirve de punta " +
                            "—para el desembolso o para un abono extraordinario— pero la tarjeta no: el pago " +
                            "de la tarjeta se anota como gasto."
                },
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Espacio.m))
        return@Column
        }

        Spacer(Modifier.height(Espacio.m))

        // Entre monedas (pagar la tarjeta en dólares desde pesos) el monto es lo que SALIÓ de la
        // cuenta, en su moneda, y abajo se pide lo que bajó la deuda. La app no convierte: el tipo
        // de cambio lo puso el banco. Ver `CreatePagoDeCuotaRequest.montoEnLaMonedaDeLaDeuda`.
        val pagoEntreMonedas = esPago && from != null && to != null && from.currency != to.currency
        // Vuelta 47 · **la cifra protagonista y la píldora de la fecha, como en Gasto** (la cabeza
        // de Agregar, [CabezaDelMonto]). Eran un campo de monto chico y una caja «Hoy».
        val monedaDelMonto = from?.currency ?: "COP"
        val avisarLaMoneda by rememberUpdatedState(onMonedaDelMonto)
        LaunchedEffect(monedaDelMonto) { avisarLaMoneda(monedaDelMonto) }
        if (esPago) {
            // Vuelta 48 · **qué se está anotando**, en un renglón: «Cuota de Libre inversión 9695 ·
            // octubre». Desde la fila de Pagos, el mes es el de esa cuota.
            val mesDeHoy = remember(ajustesDelPeriodo) { mesDelPeriodoDeHoy(ajustesDelPeriodo, Clock.System.now().toEpochMilliseconds()) }
            // Vuelta 62 (revisión): una tarjeta con su extracto se nombra por su corte, no por «octubre».
            val extractoDelDestino = to?.takeIf { it.type == AccountType.CREDIT_CARD }
                ?.let { tarjetasPorCuenta[it.id]?.extracto }
                ?.takeIf { !extractoPagado(it) }
            val mes = extractoDelDestino?.let { mesDelPagoDeLaTarjeta(it) }
                ?: presetMesDeLaCuota?.takeIf { it.isNotBlank() && to?.id == presetDestinoId } ?: mesDeHoy
            Text(
                encabezadoDeLaCuota(to, abonoExtra, mes),
                style = Movi.textos.cuerpoFuerte,
                color = if (to == null) Movi.colores.textoMedio else Movi.colores.texto,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(TAG_ENCABEZADO_DE_LA_CUOTA),
            )
            Spacer(Modifier.height(Espacio.xs))
        }
        CabezaDelMonto(
            montoEscrito = simboloDeMoneda(monedaDelMonto) + formatAmountKeypadDisplay(monto),
            moneda = monedaDelMonto,
            conTecladoEnPantalla = conTecladoEnPantalla,
            dateLabel = etiquetaDeFecha(fecha, hoy),
            onPickDate = { if (!saving) abrirFecha() },
            tagDeLaFecha = TAG_FECHA_DEL_TRASPASO,
        )
        Spacer(Modifier.height(Espacio.m))

        // Vuelta 47 · «Desde» y «Hacia» con la marca de la cuenta y su nombre entero (dos renglones
        // si hace falta), y el rótulo con su porqué debajo —«Desde · Por defecto»—: el nombre ya no
        // compite con el rótulo por el renglón y no se corta en «Bancolombia Ahorros …». Y la nota,
        // la fila de Gasto. Todo en la superficie de Agregar.
        Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_TRASPASO), relleno = RellenoDeSuperficie.Lista) {
            // Ola 11: mismo aviso que la fila «Cuenta» del editor (ver [avisoDeCuenta]): el valor por
            // defecto es una decisión de la app y tiene que poder leerse antes de guardar.
            FilaDeCuentaDelTraspaso(
                rotulo = if (esPago) ROTULO_SALE_DE else "Desde",
                cuenta = from,
                aviso = avisoDeCuenta(origenFrom, elegibles.size),
                onClick = { abrirPicker(TransferSide.FROM) },
                modifier = Modifier.testTag(TAG_FILA_DESDE),
            )
            Separador(SangriaDeSeparador.Margen)
            // Menos una: el destino no puede ser el origen (con dos cuentas no hay decisión que confesar).
            FilaDeCuentaDelTraspaso(
                rotulo = if (esPago) ROTULO_PAGAS else "Hacia",
                // Vuelta 71: sin deuda elegida, la fila pregunta «¿Qué pagas?» (no «Elegir cuenta · Pagas»).
                vacia = if (esPago) QUE_PAGAS else ELEGIR_CUENTA,
                cuenta = to,
                aviso = avisoDeCuenta(origenTo, elegibles.size - 1),
                onClick = { abrirPicker(TransferSide.TO) },
                modifier = Modifier.testTag(TAG_FILA_HACIA),
            )
            Separador(SangriaDeSeparador.Margen)
            FilaDeLaHoja(
                titulo = "Nota",
                valor = note.ifBlank { "Agregar nota…" },
                valorVacio = note.isBlank(),
                onClick = { if (!saving) abrirNota() },
            )
        }

        if (pagoEntreMonedas) {
            Spacer(Modifier.height(Espacio.s))
            MoneyField(
                value = montoEnLaDeuda,
                onValueChange = { montoEnLaDeuda = it },
                label = "Lo que bajó la deuda",
                prefix = simboloDeMoneda(to!!.currency),
                modifier = Modifier.testTag(TAG_CAMPO_DE_MONTO_EN_LA_DEUDA).onFocusChanged { onCampoDeTextoConFoco(it.hasFocus) },
            )
            Spacer(Modifier.height(Espacio.xs))
            Text(
                "Arriba va lo que salió de ${from!!.name}. Escribe los dos montos como aparecen en el banco: Movi no convierte.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }

        // ── Ola 14 · lo que este traspaso le hace al crédito ────────────────────────────────
        //
        // Solo aparece cuando una de las dos puntas ES un crédito, y entonces aparece SIEMPRE:
        // primero qué se está registrando (desembolso o abono extraordinario, con la frase que lo
        // separa de la cuota mensual) y, apenas hay monto, la deuda antes y después.
        //
        // El alto NO se reserva a propósito, al revés que el error y el «falta el monto» de más
        // abajo: esas dos cajas van pegadas al botón de guardar y su aparición lo corría bajo el
        // dedo. Este bloque vive arriba de la fecha, en medio del formulario, y solo cambia
        // cuando el dueño acaba de elegir un crédito con el dedo —o sea, mirando esta zona— así
        // que reservarle 60 dp a todo el mundo por un caso que la mayoría no usa habría dejado un
        // hueco permanente en la hoja para no mover nada que el dedo esté tocando.
        // «Exactamente uno» y no «alguno»: con un crédito de los dos lados el traspaso está
        // rechazado (ver validateTransfer) y explicarlo como desembolso sería contradecir el
        // motivo que ya se muestra debajo del botón.
        val unSoloCredito = from != null && to != null &&
            (from.type == AccountType.LOAN) != (to.type == AccountType.LOAN)
        // Los avisos de desembolso/abono son del TRASPASO. En la pestaña Cuota, el de abono
        // decía literalmente «la cuota mensual no va aquí — esa se anota como gasto en Agregar»:
        // la pantalla contradecía a la pestaña en la que estaba.
        val claseDeTraspaso = if (unSoloCredito && !esPago) transferKindFor(from!!, to!!) else null
        if (claseDeTraspaso == TransferKind.DESEMBOLSO || claseDeTraspaso == TransferKind.ABONO_EXTRAORDINARIO) {
            Spacer(Modifier.height(Espacio.s))
            Text(
                text = if (claseDeTraspaso == TransferKind.DESEMBOLSO) {
                    "Desembolso: la plata que el banco te prestó entra a tu cuenta y suma en «Entró» " +
                        "del período de su fecha; la deuda del crédito sube, no baja."
                } else {
                    "Abono extraordinario: plata extra que baja el capital. La cuota del mes no va " +
                        "aquí: esa se anota en Cuota."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            // Vuelta 73: el abono sobre un crédito sin su saldo en Movi no hace la cuenta («−$980.000
            // pasa a −$1.480.000» parecía una deuda que crecía): dice que falta el saldo y ofrece
            // cargarlo, como la pestaña Cuota desde la vuelta 49.
            val abonoSinSaldo = claseDeTraspaso == TransferKind.ABONO_EXTRAORDINARIO && creditoSinSaldoEnMovi(to, bajaLaDeuda = amount)
            if (abonoSinSaldo) {
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    if (onCuadrarElCredito != null) "$SIN_EL_SALDO_DEL_CREDITO · $CUADRAR_EL_CREDITO" else SIN_EL_SALDO_DEL_CREDITO,
                    style = Movi.textos.apoyoMedio,
                    color = if (onCuadrarElCredito != null) Movi.colores.enlace else Movi.colores.texto,
                    modifier = Modifier
                        .then(
                            if (onCuadrarElCredito != null) {
                                Modifier.clickable(onClickLabel = "Cargar el saldo de ${to?.name.orEmpty()}", onClick = onCuadrarElCredito)
                            } else Modifier,
                        )
                        .testTag(TAG_RENGLON_DE_LA_DEUDA),
                )
            } else {
                deudaDespuesDelTraspaso(from, to, amount)?.let { renglon ->
                    Spacer(Modifier.height(Espacio.xs))
                    Text(renglon, style = Movi.textos.apoyoMedio, color = Movi.colores.texto, modifier = Modifier.testTag(TAG_RENGLON_DE_LA_DEUDA))
                }
            }
        }

        // ── Cuota: en qué se va, y en qué queda la deuda (vuelta 48: sin saltos) ───────────────
        //
        // **La hoja de Cuota no salta.** Antes, el bloque del desglose (con «Deuda de X: $A pasa a $B»)
        // existía recién con una deuda elegida: elegir «Nu» estiraba la hoja ~70 px hacia arriba
        // (r47-04 contra r47-06). Ahora **el renglón de la deuda está siempre**, con su alto de dos
        // renglones reservado (`minLines = 2`): vacío hasta que se elige qué pagar, «Deuda de Nu:
        // $1.683.000» al elegirla, y «… pasa a $1.583.000» al llegar el monto. Cambia el texto, no el
        // alto. Lo mide `LaHojaDeCuotaNoSaltaTest`.
        //
        // Sobre un crédito se suma lo suyo —el interés de este mes y la frase del desglose, que
        // reserva tres renglones (`minLines = 3`) para que escribir o borrar el monto no la mueva—, y
        // el enlace al abono extra. Eso aparece al ELEGIR el crédito, que es cambiar de modo («Cuota
        // de X · mes»), no al escribir.
        //
        // Es plata suya: tiene que poder VERIFICAR el número, no confiar en él. Ver [textoDelDesglose].
        if (esPago) {
            val desglose = if (guardaUnaCuota && to != null) {
                desgloseDelPago(to, termsDelDestino, if (from != null && from.currency != to.currency) montoEnLaDeuda else amount, interesEfectivo)
            } else {
                null
            }
            Spacer(Modifier.height(Espacio.s))

            if (esAbono) {
                // El abono extra es el traspaso de siempre: lo dice antes de guardar.
                Text(QUE_ES_UN_ABONO_EXTRA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xs))
            }

            // ── El interés de este mes, editable ─────────────────────────────────────────────
            //
            // La estimación se queda corta contra el extracto por más de $100.000 en una sola
            // cuota (ver `CreatePagoDeCuotaRequest.interesReal`), así que el interés es un campo,
            // **prellenado con la estimación**, y el capital de la frase se recalcula en vivo con lo
            // que él escriba. Aparece apenas hay un crédito elegido y sus condiciones cargadas —el
            // interés es `saldo × tasa`, no necesita el monto—. Sobre una tarjeta o un abono no.
            val muestraElInteres = esCredito && !esAbono && terminosCargados && !falloCargarTerminos
            if (muestraElInteres && to != null) {
                MoneyField(
                    // Sin tocar: la estimación (o vacío en un crédito sin tasa). Tocado: lo suyo.
                    // `MoneyField` se resincroniza solo cuando el valor cambia desde afuera, así
                    // que la estimación entra al prellenar y no vuelve a pisar lo que escribió.
                    value = if (interesTocado) interesEscrito else interesEstimado,
                    onValueChange = { interesTocado = true; interesEscrito = it },
                    label = "Intereses de este mes",
                    prefix = when (to.currency) { "COP" -> "$"; "USD" -> "US$"; else -> to.currency },
                    modifier = Modifier.testTag(TAG_CAMPO_DE_INTERES).onFocusChanged { onCampoDeTextoConFoco(it.hasFocus) },
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    if (interesEstimado != null) INTERES_ESTIMADO_AVISO else INTERES_SIN_TASA_AVISO,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Spacer(Modifier.height(Espacio.s))
            }

            if (esCredito && !esAbono && to != null) {
                // La frase del desglose. Mientras no se sepa, no se afirma nada: un «este crédito no
                // tiene tasa» sobre una carga a medias sería el número plausible y falso que la Ola
                // del desglose vino a matar. Un interés que no cabe en la cuota se dice acá, con las
                // palabras del server, y el botón queda apagado.
                val (frase, color) = when {
                    (amount ?: 0L) <= 0L ->
                        "Escribe el monto para ver cuánto de la cuota baja la deuda." to Movi.colores.textoMedio
                    !terminosCargados -> "Calculando cuánto baja la deuda…" to Movi.colores.textoMedio
                    falloCargarTerminos -> SIN_CONDICIONES_PARA_EL_DESGLOSE to Movi.colores.textoMedio
                    errorDeInteres != null -> errorDeInteres to Movi.colores.sale
                    else -> (desglose?.let { textoDelDesglose(it, to.currency) }.orEmpty()) to Movi.colores.textoMedio
                }
                Text(frase, style = Movi.textos.apoyo, color = color, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Espacio.xs))
            }

            // El renglón de la deuda, siempre, con su alto reservado (ver arriba).
            // Vuelta 49: si Movi no tiene el saldo del crédito (o el pago lo dejaría negativo), no se
            // hace la cuenta: se dice y se ofrece cuadrarlo. Una deuda nunca se muestra negativa.
            val sinSaldo = creditoSinSaldoEnMovi(to, bajaLaDeuda = if (esAbono) amount else desglose?.capital)
            val renglonDeLaDeuda = when {
                to == null -> null
                sinSaldo -> SIN_EL_SALDO_DEL_CREDITO
                esAbono -> deudaDespuesDelTraspaso(from, to, amount) ?: deudaDeHoy(to)
                to.type == AccountType.LOAN && (!terminosCargados || falloCargarTerminos || errorDeInteres != null) -> deudaDeHoy(to)
                else -> deudaDespuesDelPago(to, desglose) ?: deudaDeHoy(to)
            }
            Text(
                if (sinSaldo && onCuadrarElCredito != null) "$SIN_EL_SALDO_DEL_CREDITO · $CUADRAR_EL_CREDITO" else renglonDeLaDeuda.orEmpty(),
                style = Movi.textos.apoyoMedio,
                color = if (sinSaldo && onCuadrarElCredito != null) Movi.colores.enlace else Movi.colores.texto,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (sinSaldo && onCuadrarElCredito != null) {
                            Modifier.clickable(onClickLabel = "Cuadrar el saldo de ${to?.name.orEmpty()}", onClick = onCuadrarElCredito)
                        } else Modifier,
                    )
                    .testTag(TAG_RENGLON_DE_LA_DEUDA),
            )

            // Vuelta 62 · **cuánto pagar de la tarjeta**: «Total del extracto $X», «Mínimo $Y», «Otro
            // monto». El renglón se reserva desde antes de elegir (como el de la deuda): elegir la
            // tarjeta cambia lo que dice, no el alto. Entre monedas no se ofrecen (la cifra de arriba
            // es la de la cuenta que paga, no la de la tarjeta).
            if (to == null || to.type == AccountType.CREDIT_CARD) {
                val pastillas = if (to != null && !pagoEntreMonedas) pastillasDelPagoDeLaTarjeta(tarjetasPorCuenta[to.id]) else emptyList()
                Spacer(Modifier.height(Espacio.xs))
                // Revisión: a 390 dp «Otro monto» se salía de la pantalla y «Mínimo» quedaba cortada.
                // Textos cortos («Extracto $X», «Mínimo $Y», «Otro») que caben en un renglón, y un
                // FlowRow por si una letra grande no los deja caber: bajan, no se salen.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medida.altoChico)
                        .testTag(TAG_PASTILLAS_DEL_PAGO_DE_LA_TARJETA),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    pastillas.forEach { pastilla ->
                        PildoraDeAccion(
                            pastilla.texto,
                            onClick = { onMontoPropuesto(pastilla.monto?.toString() ?: "") },
                            habilitada = !saving,
                        )
                    }
                }
            }

            // **El abono extra, como opción secundaria** de la misma hoja: un enlace, no una pestaña.
            if (esCredito) {
                Boton(
                    texto = if (esAbono) ES_LA_CUOTA_DEL_MES else ES_UN_ABONO_EXTRA,
                    onClick = { abonoExtra = !abonoExtra },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = !saving,
                    modifier = Modifier.testTag(TAG_ENLACE_DEL_ABONO),
                )
            }
        }

        // Ola 8 · V2 (N4): igual que el error del editor de un movimiento — alto reservado,
        // dos renglones, para que un fallo de red no corra el botón de guardar bajo el dedo.
        // Mínimo y no fijo: el 422 del interés que no cabe trae tres cifras y ocupa tres
        // renglones en un teléfono; recortado a dos, el dueño leía media explicación. Crece solo
        // en ese caso, y después de un toque en Guardar, no bajo el dedo que escribe.
        Spacer(Modifier.height(Espacio.s))
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            if (error != null) {
                Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
        }

        // Vuelta 47: el teclado de Gasto (sin teclado en pantalla, el físico escribe el monto).
        if (conTecladoEnPantalla) {
            TecladoNumericoDeAgregar(onKey)
            Spacer(Modifier.height(Espacio.s))
        } else {
            Spacer(Modifier.height(Espacio.m))
        }
        Boton(
            texto = when {
                saving -> "Guardando…"
                esAbono -> "Registrar abono"
                esPago -> "Registrar pago"
                else -> "Guardar traspaso"
            },
            onClick = { save() },
            habilitado = canSave,
            anchoCompleto = true,
        )

        // Vuelta 47 · **una sola línea de ayuda** debajo del botón: lo que falta, o —con todo listo—
        // que se guarda en línea. Eran dos renglones, uno de ellos de dos líneas. Reserva su alto
        // (Ola 8 · V2): aparecer al escribir el monto no puede correr el botón bajo el dedo.
        Spacer(Modifier.height(Espacio.xs))
        Box(modifier = Modifier.fillMaxWidth().height(16.dp).testTag(TAG_AYUDA_DEL_TRASPASO)) {
            Text(
                text = if (!saving && missing != null) missing else ayudaDelTraspaso(guardaUnaCuota),
                style = Movi.textos.apoyo,
                color = if (!saving && missing != null) Movi.colores.textoMedio else Movi.colores.textoApagado,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        } // Column del formulario
        } // else de `picking != null`
    } // Box del alto fijado
}

internal enum class TransferSide { FROM, TO }

/**
 * El sub-picker de «Desde»/«Hacia».
 *
 * **Ola 14 — dos grupos, no una lista larga.** Al dejar entrar los préstamos, el dueño con cinco
 * créditos reales pasaba de ver tres cuentas a ver ocho renglones donde antes solo había plata
 * disponible, sin nada que dijera cuáles eran cuáles. Las cuentas de dinero e inversión van
 * primero (es lo que se elige el 99 % de las veces) y los créditos al final, bajo un rótulo con
 * una línea que explica para qué sirven ahí — que es la pregunta que se hace quien los ve
 * aparecer por primera vez en esta hoja.
 *
 * El orden dentro de cada grupo es el que trae la lista (`GET /api/accounts` ordena por nombre):
 * agrupar no reordena nada más.
 */
@Composable
private fun TransferAccountPicker(
    title: String,
    accounts: List<Account>,
    selectedId: String?,
    /** La línea bajo «Créditos»: qué hace elegir uno en esta pestaña (vuelta 48). */
    ayudaDeCreditos: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val creditos = accounts.filter { it.type == AccountType.LOAN }
    val cuentas = accounts.filter { it.type != AccountType.LOAN }
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader(title, onClose)
        if (cuentas.isNotEmpty()) {
            GrupoDeCuentas(cuentas, selectedId, onPick)
        }
        if (creditos.isNotEmpty()) {
            Spacer(Modifier.height(Espacio.s))
            Text(
                "Créditos",
                style = Movi.textos.apoyoMedio,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Espacio.xxs))
            Text(
                ayudaDeCreditos,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            Spacer(Modifier.height(Espacio.xs))
            GrupoDeCuentas(creditos, selectedId, onPick)
        }
        Spacer(Modifier.height(Espacio.xs))
    }
}

/**
 * Un grupo del selector de «Desde»/«Hacia». **Vuelta de diseño 15: como el de «Cuenta»** de Gasto e
 * Ingreso (vuelta 13) —y con su misma fila, [FilaDeCuentaEnAgregar]—: la marca del banco, el nombre,
 * el saldo con [Cifra] en su moneda, «Debes» / «A favor» en una deuda y la elegida marcada, sobre
 * una superficie de capa 2. Antes era el nombre pelado y un «Elegida» en texto al final, sin saldo:
 * para pasar plata había que saber de memoria cuánto tenía cada cuenta.
 */
@Composable
private fun GrupoDeCuentas(
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
) {
    Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
        accounts.forEachIndexed { i, account ->
            if (i > 0) Separador()
            FilaDeCuentaEnAgregar(
                account = account,
                elegida = account.id == selectedId,
                onClick = { onPick(account.id) },
            )
        }
    }
}


/**
 * Arma la petición de un pago de cuota a partir del borrador. Espeja a `transferRequestFor` y
 * comparte sus tres ids: son lo que hace idempotente el reintento del dedo.
 */
private fun pagoRequestFor(
    ids: TransferDraftIds,
    fromAccountId: String,
    debtAccountId: String,
    amount: Long,
    timestamp: Long,
    note: String,
    /** `null` = que el server estime. Sin default a propósito: olvidarlo no compila. */
    interesReal: Long?,
    /** Lo que bajó la deuda en su moneda; solo se manda si las monedas difieren. */
    montoEnLaDeuda: Long?,
) = com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest(
    fromAccountId = fromAccountId,
    debtAccountId = debtAccountId,
    amount = amount,
    timestamp = timestamp,
    note = note.trim().ifBlank { null },
    transferId = ids.transferId,
    fromEventId = ids.fromEventId,
    toEventId = ids.toEventId,
    interesReal = interesReal,
    montoEnLaMonedaDeLaDeuda = montoEnLaDeuda,
)

/**
 * El símbolo con el que se muestra un monto en esa moneda. `internal` y no `private` porque la
 * hoja de «Agregar» ([QuickAddScreen]) la usa para lo mismo: un gasto sobre una cuenta en dólares
 * tiene que decir «US$», no «$».
 */
internal fun simboloDeMoneda(moneda: String): String = when (moneda) { "COP" -> "$"; "USD" -> "US$"; else -> moneda }

/** Etiquetas de prueba de los dos campos de monto de esta hoja, para que Robolectric los distinga. */
internal const val TAG_CAMPO_DE_MONTO = "campo_de_monto_del_traspaso"
internal const val TAG_CAMPO_DE_INTERES = "campo_de_interes_de_la_cuota"
internal const val TAG_CAMPO_DE_MONTO_EN_LA_DEUDA = "campo_de_monto_en_la_moneda_de_la_deuda"

/**
 * **La línea de ayuda de Traspaso y Cuota con todo listo** (vuelta 47): un traspaso exige conexión
 * (ver `LocalRepository.createTransfer`) —las dos patas nacen juntas en el server o no nacen—, y
 * decirlo antes es más honesto que un «revisa tu conexión» después. Corta, para un renglón a 360.
 */
internal fun ayudaDelTraspaso(esPago: Boolean): String =
    if (esPago) "Se guarda en línea: la salida y el pago, juntos." else "Se guarda en línea: las dos puntas, juntas."

/** «Sale de» y «Pagas»: los rótulos de las dos filas en Pagar. */
internal const val ROTULO_SALE_DE: String = "Sale de"
internal const val ROTULO_PAGAS: String = "Pagas"

/** Vuelta 71: el destino de Pagar sin deuda elegida. */
const val QUE_PAGAS: String = "¿Qué pagas?"

/** Una punta del traspaso sin cuenta. */
internal const val ELEGIR_CUENTA: String = "Elegir cuenta"

/** Las filas de las dos cuentas, la tarjeta, la fecha y la ayuda de Traspaso y Cuota (vuelta 47). */
internal const val TAG_FILA_DESDE = "traspaso:fila-desde"
internal const val TAG_FILA_HACIA = "traspaso:fila-hacia"
internal const val TAG_TARJETA_DEL_TRASPASO = "traspaso:tarjeta"
internal const val TAG_FECHA_DEL_TRASPASO = "traspaso:fecha"
internal const val TAG_AYUDA_DEL_TRASPASO = "traspaso:ayuda"

/**
 * **Una punta del traspaso** (vuelta 47): la marca de la cuenta, su **nombre entero** como título (dos
 * renglones si hace falta, como cualquier [Fila]) y debajo el rótulo con su porqué —«Desde · Por
 * defecto»—. Sin cuenta, «Elegir cuenta». El selector es el de siempre (`TransferAccountPicker`, con la
 * fila de cuenta de Agregar).
 */
@Composable
internal fun FilaDeCuentaDelTraspaso(
    rotulo: String,
    cuenta: Account?,
    aviso: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Vuelta 71 · lo que dice la fila sin cuenta: «Elegir cuenta», o «¿Qué pagas?» en el destino de
     * Pagar. Con una pregunta, el rótulo no se repite debajo.
     */
    vacia: String = ELEGIR_CUENTA,
) {
    Fila(
        titulo = cuenta?.name ?: vacia,
        subtitulo = (if (cuenta == null && vacia != ELEGIR_CUENTA) listOfNotNull(aviso) else listOfNotNull(rotulo, aviso))
            .joinToString(" · ").ifEmpty { null },
        inicio = cuenta?.let { c -> { AvatarDeLaCuenta(nombre = c.name, tipo = c.type) } },
        final = { Chevron() },
        onClick = onClick,
        modifier = modifier,
        densidad = DensidadDeFila.Compacta,
    )
}
