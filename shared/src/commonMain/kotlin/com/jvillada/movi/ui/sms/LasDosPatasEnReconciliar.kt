package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.operacionEntre
import com.jvillada.movi.shared.model.resumenDeLasDosPatas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CuentaDelBanco
import com.jvillada.movi.ui.components.Hairline
import kotlin.math.roundToLong

/*
 * # Reconciliar: lo que confirmar va a crear cuando el aviso es de dos patas
 *
 * Arreglo 1 de la auditoría de la ingesta. Un aviso con destino propio —la tarjeta que se paga, el
 * crédito, la cuenta a la que fue el retiro de la Fiducuenta, la cuenta a la que entró un avance— se
 * confirma con las dos patas de una vez (`DosPatasDelAviso`, que el server escribe en una
 * transacción). Antes de confirmar, la tarjeta de resumen dice qué se va a crear: «Sale de
 * Bancolombia Ahorros · entra a Master Black 3684 como pago». Un aviso sin destino propio sigue como
 * siempre, con un solo movimiento.
 */

/**
 * Las dos puntas que el dueño va a confirmar, y qué hecho son. [entrante]: el aviso es del lado que
 * recibe la plata (un traspaso que entra), y queda enlazado a la pata de [destino].
 */
data class DosPatasPropuestas(
    val operacion: OperacionDelAviso,
    val origen: Account,
    val destino: Account,
    val entrante: Boolean = false,
) {
    /** «Sale de … · entra a … como pago»: lo que dice la tarjeta de resumen. */
    val resumen: String get() = resumenDeLasDosPatas(operacion, origen, destino)
}

/**
 * **¿Este aviso se confirma con dos patas, y cuáles?** `null` = un solo movimiento, como siempre.
 *
 * - **Pago de tarjeta o cuota** («Pago de tarjeta» o «Cuota de crédito», un gasto): con la deuda
 *   elegida en «¿A cuál crédito o tarjeta corresponde?» ([deudaElegida], Ola Y). Qué es lo decide el
 *   tipo de la deuda, no cuál de las dos categorías quedó: lo mismo que hace `vincular-deuda`. Una
 *   cuota entre monedas no (el reparto entre interés y capital sería inventado); una tarjeta sí, y el
 *   server la convierte con la TRM del día.
 * - **Traspaso** («Traspaso», un gasto): con la cuenta suya a la que fue la plata ([destinoElegido]).
 * - **Avance** (el «Avance de tarjeta» que lee el server, un ingreso en la tarjeta de donde salió):
 *   con la cuenta suya a la que entró ([destinoElegido]).
 * - **Traspaso que entra** («Traspaso», un ingreso): con la cuenta suya de la que vino la plata
 *   ([origenElegido]). La cuenta del aviso es el destino; el aviso queda en la pata que entra.
 */
fun dosPatasPropuestas(
    leido: ParsedSms?,
    categoria: String?,
    cuenta: Account?,
    deudaElegida: Account?,
    destinoElegido: Account?,
    origenElegido: Account? = null,
): DosPatasPropuestas? {
    if (leido == null || categoria == null || cuenta == null) return null
    fun si(operacion: OperacionDelAviso, origen: Account, destino: Account) =
        DosPatasPropuestas(operacion, origen, destino).takeIf { operacionEntre(origen, destino) == operacion }
    return when {
        esUnAvanceDeLaTarjeta(leido, categoria, cuenta) ->
            destinoElegido?.takeIf { it.currency == cuenta.currency }?.let { si(OperacionDelAviso.AVANCE, cuenta, it) }
        esUnTraspasoQueEntra(leido, categoria, cuenta) ->
            origenElegido?.takeIf { it.currency == cuenta.currency }
                ?.let { si(OperacionDelAviso.TRASPASO, it, cuenta)?.copy(entrante = true) }
        leido.type != TransactionType.EXPENSE -> null
        categoria == CARD_PAYMENT_CATEGORY || categoria == CUOTA_CATEGORY -> {
            val deuda = deudaElegida ?: return null
            when (deuda.type) {
                AccountType.CREDIT_CARD -> si(OperacionDelAviso.PAGO_DE_TARJETA, cuenta, deuda)
                AccountType.LOAN -> deuda.takeIf { it.currency == cuenta.currency }?.let { si(OperacionDelAviso.CUOTA, cuenta, it) }
                else -> null
            }
        }
        categoria == TRANSFER_CATEGORY ->
            destinoElegido?.takeIf { it.currency == cuenta.currency }?.let { si(OperacionDelAviso.TRASPASO, cuenta, it) }
        else -> null
    }
}

/**
 * **El avance que se lee en la tarjeta**: «Hiciste un avance de $6,200,000 … desde tu T.Credito *9208
 * a la cuenta *8133» cae, por el número, en la AMEX. Anotado así como un solo movimiento sería un
 * INGRESO en la tarjeta —le BAJARÍA la deuda—, así que sin la cuenta de destino no se confirma.
 */
fun esUnAvanceDeLaTarjeta(leido: ParsedSms?, categoria: String?, cuenta: Account?): Boolean =
    leido != null && categoria == AVANCE_DE_TARJETA_CATEGORY && leido.type == TransactionType.INCOME &&
        cuenta?.type == AccountType.CREDIT_CARD

/**
 * **El traspaso avisado del lado que entra**: un ingreso con «Traspaso» en una cuenta suya de plata o
 * de inversión. La otra punta —de dónde vino— la propone [origenPropuestoDelAviso] o la elige el
 * dueño; sin ella no se confirma, porque un «Traspaso» suelto no existe.
 */
fun esUnTraspasoQueEntra(leido: ParsedSms?, categoria: String?, cuenta: Account?): Boolean =
    leido?.type == TransactionType.INCOME && categoria == TRANSFER_CATEGORY &&
        cuenta != null && cuenta.type.group != AccountGroup.DEUDA && !cuenta.esBien

/** ¿La pantalla tiene que pedir «de qué cuenta vino»? En un traspaso que entra, sí. */
fun pideLaCuentaDeOrigen(leido: ParsedSms?, categoria: String?, cuenta: Account?): Boolean =
    esUnTraspasoQueEntra(leido, categoria, cuenta)

/**
 * **La cuenta de origen que queda propuesta** para un traspaso que entra: la cuenta propia que el
 * server reconoció en el aviso («Recibiste $X de tu cuenta \*9586» → la Fiducuenta,
 * [ParsedSms.traspasoDesdeId]). Nunca la misma cuenta del aviso, una deuda ni un bien. `null` si no
 * sabe: la elige el dueño, o el aviso sigue como un ingreso.
 */
fun origenPropuestoDelAviso(leido: ParsedSms?, resuelta: CuentaDelBanco, accounts: List<Account>): Account? {
    val id = leido?.traspasoDesdeId ?: return null
    return accounts.firstOrNull { it.id == id }
        ?.takeIf { it.id != resuelta.cuenta?.id && it.type.group != AccountGroup.DEUDA && !it.esBien }
}

/** ¿La pantalla tiene que pedir «a qué cuenta entra»? En un traspaso y en un avance, sí. */
fun pideLaCuentaDeDestino(leido: ParsedSms?, categoria: String?, cuenta: Account?): Boolean =
    esUnAvanceDeLaTarjeta(leido, categoria, cuenta) ||
        (leido?.type == TransactionType.EXPENSE && categoria == TRANSFER_CATEGORY && cuenta != null)

/**
 * **La cuenta de destino que queda propuesta** para un traspaso o un avance: la cuenta propia que
 * Movi reconoció en el aviso (el «Depósito a tu cuenta NU» de PSE, [destinoDelTraspaso]) o la que el
 * mensaje nombra por su número («hacia la cuenta *02955068133», [CuentaDelBanco.destino]). Nunca una
 * deuda ni un bien. `null` si no sabe: la elige el dueño.
 */
fun destinoPropuestoDelAviso(
    leido: ParsedSms?,
    resuelta: CuentaDelBanco,
    accounts: List<Account>,
): Account? {
    val origen = resuelta.cuenta ?: return null
    val delPse = leido?.traspasoHaciaId?.let { id -> accounts.firstOrNull { it.id == id } }
    return listOfNotNull(delPse, resuelta.destino).firstOrNull { cuenta ->
        cuenta.id != origen.id && cuenta.type.group != AccountGroup.DEUDA && !cuenta.esBien
    }
}

/** Las cuentas que se pueden elegir como destino: suyas, de dinero o inversión, distintas del origen. */
fun destinosElegibles(accounts: List<Account>, origen: Account?): List<Account> =
    accounts.filter { it.id != origen?.id && it.type.group != AccountGroup.DEUDA && !it.esBien }

/**
 * **¿Hay que preguntar cuánto bajó la deuda en su moneda?** Al pagar una tarjeta en otra moneda (la
 * Master Black en dólares desde Ahorros en pesos): el aviso dice lo que salió de la cuenta, no lo que
 * bajó la deuda. Sin la tasa del día el server no lo inventa (contesta 422 y lo pide); con ella, el
 * campo es opcional.
 */
fun pideElMontoEnLaMonedaDeLaDeuda(propuestas: DosPatasPropuestas?): Boolean =
    propuestas != null && propuestas.operacion == OperacionDelAviso.PAGO_DE_TARJETA &&
        propuestas.origen.currency != propuestas.destino.currency

/** «¿Cuánto bajó la deuda en USD?»: el rótulo del campo, con la moneda de la tarjeta. */
fun rotuloDelMontoEnLaDeuda(moneda: String): String = "¿Cuánto bajó la deuda en $moneda?"

/** Lo que dice debajo del campo: de dónde sale la cifra, y qué pasa si se deja vacío. */
const val AYUDA_DEL_MONTO_EN_LA_DEUDA: String =
    "La tarjeta y la cuenta están en monedas distintas. Escribe lo que bajó la deuda según el banco. " +
        "Si lo dejas vacío, Movi usa la tasa del día; si no la tiene, te lo pide."

/** El tag del campo «¿Cuánto bajó la deuda en USD?». */
const val TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA: String = "sms:dos-patas:monto-en-la-deuda"

/**
 * **El pedido que viaja al server**: las dos puntas, el monto que dice el aviso, cuándo llegó, la nota
 * del aviso (la «Descripción» de PSE) y tres ids nuevos, que hacen idempotente un reintento.
 *
 * [montoEnLaMonedaDeLaDeuda] es lo que el dueño escribió en «¿Cuánto bajó la deuda en USD?»; solo
 * viaja en un pago de tarjeta entre monedas ([pideElMontoEnLaMonedaDeLaDeuda]) y si es mayor que cero.
 * Sin él, el server convierte con la tasa del día.
 */
fun pedidoDeDosPatas(
    propuestas: DosPatasPropuestas,
    leido: ParsedSms,
    momento: Long,
    montoEnLaMonedaDeLaDeuda: Long? = null,
    nuevoId: (String) -> String,
): DosPatasDelAviso = DosPatasDelAviso(
    operacion = propuestas.operacion,
    origenId = propuestas.origen.id,
    destinoId = propuestas.destino.id,
    monto = leido.amount.roundToLong(),
    timestamp = momento,
    transferId = nuevoId("tr"),
    origenEventId = nuevoId("ev"),
    destinoEventId = nuevoId("ev"),
    nota = leido.nota,
    avisoDelLadoQueEntra = propuestas.entrante,
    montoEnLaMonedaDeLaDeuda = montoEnLaMonedaDeLaDeuda
        ?.takeIf { it > 0L && pideElMontoEnLaMonedaDeLaDeuda(propuestas) },
)

/** El tag del renglón «Sale de … · entra a …» en la tarjeta de resumen. */
const val TAG_RESUMEN_DE_LAS_DOS_PATAS: String = "sms:dos-patas:resumen"

/** El tag de «Cambiar» la cuenta a la que entra la plata. */
const val TAG_CAMBIAR_EL_DESTINO: String = "sms:dos-patas:cambiar-destino"

/** El tag de cada cuenta que se puede elegir como destino. */
fun tagDelDestinoElegible(id: String): String = "sms:dos-patas:destino:$id"

/** Lo que dice la tarjeta cuando un traspaso o un avance todavía no tiene la cuenta a la que entró. */
fun faltaLaCuentaDeDestino(esAvance: Boolean): String =
    if (esAvance) "Elige a qué cuenta tuya entró el avance: sin eso, la deuda de la tarjeta quedaría mal."
    else "Elige a qué cuenta tuya fue la plata."

/** Lo que dice la tarjeta cuando un traspaso que entra todavía no tiene la cuenta de la que vino. */
const val FALTA_LA_CUENTA_DE_ORIGEN: String = "Elige de qué cuenta tuya vino la plata."

/** Debajo de las categorías, cuando «Traspaso» sobre un ingreso no tiene todavía la cuenta de origen. */
const val AVISO_DEL_TRASPASO_QUE_ENTRA: String =
    "Un traspaso necesita la cuenta tuya de la que vino la plata. Si no está en Movi, créala, o elige otra categoría."

/**
 * **Lo que confirmar va a crear**, dentro de la tarjeta de resumen: «Sale de Bancolombia Ahorros ·
 * entra a Master Black 3684 como pago». En un traspaso y en un avance, además, la cuenta a la que
 * entra se puede cambiar acá mismo; en un pago o una cuota se cambia en «¿A cuál crédito o tarjeta
 * corresponde?», que ya estaba. Sin destino propio no dice nada: el aviso es un movimiento suelto.
 */
@Composable
internal fun LasDosPatasEnElResumen(
    propuestas: DosPatasPropuestas?,
    pideDestino: Boolean,
    destino: Account?,
    esAvance: Boolean,
    eligiendo: Boolean,
    habilitado: Boolean,
    onCambiar: () -> Unit,
    elegibles: List<Account>,
    onElegir: (Account) -> Unit,
    /** Un traspaso que entra: lo que se elige es la cuenta de la que vino, no a la que fue. */
    entrante: Boolean = false,
) {
    if (propuestas == null && !pideDestino) return
    Spacer(Modifier.height(12.dp))
    Hairline()
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            propuestas?.resumen ?: if (entrante) FALTA_LA_CUENTA_DE_ORIGEN else faltaLaCuentaDeDestino(esAvance),
            style = Movi.textos.apoyo,
            color = if (propuestas != null) Movi.colores.texto else Movi.colores.aviso,
            modifier = Modifier.weight(1f).testTag(TAG_RESUMEN_DE_LAS_DOS_PATAS),
        )
        if (pideDestino) {
            Text(
                if (eligiendo) "Cerrar" else if (destino == null) "Elegir" else "Cambiar",
                style = Movi.textos.apoyo,
                color = Movi.colores.marca,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(enabled = habilitado, role = Role.Button) { onCambiar() }
                    .testTag(TAG_CAMBIAR_EL_DESTINO)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
    if (pideDestino && eligiendo) {
        Spacer(Modifier.height(6.dp))
        elegibles.forEach { cuenta ->
            val esLaElegida = cuenta.id == destino?.id
            Text(
                cuenta.name,
                style = Movi.textos.cuerpo,
                color = if (esLaElegida) Movi.colores.marca else Movi.colores.texto,
                fontWeight = if (esLaElegida) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = habilitado, role = Role.Button) { onElegir(cuenta) }
                    .testTag(tagDelDestinoElegible(cuenta.id))
                    .padding(vertical = 10.dp),
            )
        }
    }
}
