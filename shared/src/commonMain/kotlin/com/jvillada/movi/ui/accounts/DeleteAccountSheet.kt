package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.formatMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F55: confirmación de "Eliminar cuenta" — mismo esqueleto de hoja que [VoidEventSheet], pero
 * acá no hay reintento posible desde la app: si `deleteAccount` falla se muestra un mensaje
 * fijo en vez de [com.jvillada.movi.ui.components.toUserMessage] (ver el porqué en
 * [com.jvillada.movi.shared.repository.LocalRepository.deleteAccount]) — la causa casi
 * siempre es de red, así que un mensaje genérico ("Algo salió mal") no ayudaría tanto como
 * decir directamente qué revisar.
 */
@Composable
fun DeleteAccountSheet(
    accountId: String,
    accountName: String,
    eventCount: Int,
    /**
     * Cuántos de esos movimientos son **patas de traspaso con otras cuentas** (ver
     * [transferWarningLabel]). Con default en 0 para que un call site que no lo sepa —o una
     * cuenta sin traspasos— siga viendo la hoja de siempre.
     */
    transferCount: Int = 0,
    /**
     * Cuánta plata suman esas patas, en la moneda de la cuenta. Va en el aviso porque **el número
     * es la mitad del aviso**: desde que un crédito puede ser una punta de un traspaso, «1 de esos
     * movimientos es un traspaso» puede querer decir $50.000 o $257.000.000, y de eso depende
     * cuánto de lo que el dueño ve en Movimientos va a quedar rotulado como suelto. (Ya no depende
     * de eso el mes: desde la ola 15 esas patas no vuelven a contarse — ver [ORPHANED_LEG_CATEGORY]
     * — y por eso este número dejó de ser el más importante de la hoja: lo es [accountBalance].)
     * Con default en 0 = no se dice el monto, que es el aviso de antes.
     */
    transferAmount: Long = 0L,
    /**
     * **El saldo de la cuenta que se está por borrar**, y con él la cifra que de verdad cambia el
     * patrimonio.
     *
     * La ola 15 sacó las patas huérfanas del flujo de caja, así que el mes ya no se mueve por
     * borrar una cuenta. Lo que sí se mueve —y no se puede evitar sin tocarle el saldo a una
     * cuenta que el dueño no tocó— es el patrimonio: **borrar un crédito hace desaparecer su
     * deuda** y deja el efectivo prestado del lado de los activos. En el escenario que motivó la
     * rama eso son $257.000.000 de patrimonio que aparecen de la nada.
     *
     * Va por separado de [transferAmount] justamente porque **no son el mismo número**: aquel
     * suma las patas de traspaso, y en el caso típico —deuda contada dos veces, borrar para
     * rehacer— no coincide con la deuda que se va. Con default en 0 no se dice nada, que es lo
     * correcto para una cuenta vacía.
     */
    accountBalance: Long = 0L,
    /** ¿La cuenta que se borra es deuda (LOAN/CREDIT_CARD)? Cambia el aviso de [accountBalance]. */
    accountIsDebt: Boolean = false,
    /**
     * Moneda de [accountBalance], **aparte** de [transferCurrency]: la deuda de una tarjeta en
     * USD entra al patrimonio por su estimado en COP (ver `assetsDebtsNet`), así que la cifra del
     * aviso puede estar en otra moneda que la de la cuenta. Con las dos cuentas de producción en
     * COP hoy son la misma, pero rotular pesos como dólares en la hoja del botón rojo es
     * exactamente el error que no se puede cometer.
     */
    accountBalanceCurrency: String = "COP",
    /** Moneda de [transferAmount] — la de la cuenta que se está por borrar. */
    transferCurrency: String = "COP",
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doDelete() {
        if (deleting) return
        deleting = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.deleteAccount(accountId)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo eliminar — revisa tu conexión"
                deleting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !deleting, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
            SheetHandleWithClose(onClose = onDismiss, enabled = !deleting)

            Text(
                text = "Eliminar cuenta",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )

            // F55: la consecuencia real, con el conteo real — no un genérico "¿estás seguro?".
            Text(
                text = "Se borra \"$accountName\" y ${eventCountLabel(eventCount)}. " +
                    "Esto no se puede deshacer.",
                fontSize = 14.sp,
                color = MinTextMute,
                lineHeight = 19.sp,
            )

            // Y la consecuencia que no cabe en la frase de arriba, porque no ocurre en esta
            // cuenta sino en otra: la mitad del traspaso que sobrevive. Solo aparece si la hay.
            //
            // Se despega del párrafo rutinario a propósito —bloque con fondo propio, mismo cuerpo
            // de letra, color de texto pleno— porque es lo que justifica la pausa. Con el gris
            // apagado y un punto más chico, los dos párrafos se leían como uno solo y el aviso se
            // perdía justo en la hoja donde hay que leerlo.
            //
            // Ola 15: son DOS avisos posibles y van en UN solo bloque, separados por un renglón en
            // blanco. Dos cajas seguidas competirían entre sí justo donde hay que leer despacio, y
            // el orden importa: primero lo que le pasa al patrimonio (la cifra grande, la que no
            // se puede deshacer), después lo que le pasa a los movimientos de las otras cuentas.
            val avisos = listOfNotNull(
                balanceWarningLabel(accountBalance, accountIsDebt, accountBalanceCurrency),
                if (transferCount > 0) {
                    transferWarningLabel(transferCount, transferAmount, transferCurrency)
                } else {
                    null
                },
            )
            if (avisos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = avisos.joinToString("\n\n"),
                    fontSize = 14.sp,
                    color = MinText,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MinSurfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = error!!, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinSurfaceContainerLow)
                        .clickable(enabled = !deleting, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
                }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!deleting) MinExpenseContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !deleting) { doDelete() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (deleting) "Eliminando…" else "Eliminar cuenta",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!deleting) MinExpense else MinTextFaint,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

private fun eventCountLabel(count: Int): String =
    if (count == 1) "su 1 movimiento" else "sus $count movimientos"

/**
 * **El aviso del patrimonio: qué desaparece con la cuenta.** `null` si no hay saldo que decir.
 *
 * Existe porque la ola 15 dejó el salto de patrimonio **fuera de alcance a propósito** —evitarlo
 * sería no dejar borrar, o tocarle el saldo a una cuenta que el dueño no tocó— y una consecuencia
 * que se decide no evitar hay que decirla. Sin esto, el KDoc de `ORPHANED_LEG_CATEGORY` prometía
 * una mitigación que la hoja no cumplía: los dos textos que renderizaba hablaban de los
 * movimientos de las OTRAS cuentas y ninguno del saldo de esta.
 *
 * **Dos textos y no uno**, porque el hecho es distinto según de qué cuenta se trate:
 * - **Deuda** (crédito, tarjeta): el patrimonio **sube** sin que se haya pagado nada, y la plata
 *   prestada se queda del lado de los activos. Es el caso caro y el que motivó la rama.
 * - **Activo**: el patrimonio **baja**. También hay que decirlo, pero no sorprende a nadie.
 *
 * Se dice la cifra y no «tu saldo» porque el número es el aviso: «$257.000.000 que hoy te
 * aparecen como deuda» pide una decisión que «el saldo de esta cuenta» no pide.
 */
internal fun balanceWarningLabel(balance: Long, isDebt: Boolean, currency: String): String? {
    if (balance == 0L) return null
    val cuanto = formatMoney(balance, currency)
    return if (isDebt) {
        "Con la cuenta desaparece su deuda de $cuanto, así que tu patrimonio va a subir esa misma " +
            "cifra sin que hayas pagado nada: la plata prestada sigue en tus otras cuentas. Si el " +
            "banco te la sigue cobrando, vuelve a crear el crédito."
    } else {
        "Con la cuenta desaparecen sus $cuanto, así que tu patrimonio va a bajar esa misma cifra."
    }
}

/**
 * El aviso de los traspasos, **antes** de borrar y no después.
 *
 * Un traspaso tiene una pata en cada cuenta. Al borrar esta, la pata de la OTRA cuenta sobrevive
 * —tiene que sobrevivir: la plata salió de verdad y el saldo de esa cuenta lo refleja— pero se
 * queda sin la mitad que la explicaba, así que el server la suelta del par y le pone una
 * categoría propia (ver `desenlazarPatasHermanas` en `AccountRoutes.kt` y
 * `ORPHANED_LEG_CATEGORY` en `:core`).
 *
 * **Qué dice el aviso, después de la ola 15.** Antes prometía que ese movimiento «vuelve a contar
 * en los gastos o ingresos de un mes que ya diste por cerrado». Eso ya no pasa —`isCashFlow`
 * excluye la categoría, justamente porque con un crédito de por medio esa promesa fabricaba un
 * ingreso de $257.000.000— así que el aviso tiene que decir lo que sí pasa, que es distinto y
 * también hay que saberlo antes de tocar el botón rojo: los **saldos** de las otras cuentas no se
 * mueven y esos movimientos quedan sueltos y rotulados. Prometer un cambio en los totales del mes
 * que ya no ocurre sería peor que no avisar: manda al dueño a revisar meses viejos que están
 * intactos.
 *
 * Lo que sí desaparece —el saldo de ESTA cuenta, que en un crédito es la deuda— tiene su propio
 * aviso y su propia cifra, en [balanceWarningLabel]. No va acá porque no es lo mismo ni es el
 * mismo número.
 *
 * **Se nombra la categoría de destino** porque sin ella el aviso no es accionable: es el renglón
 * con el que va a encontrarse esa fila en Movimientos, y el nombre que tiene que buscar.
 *
 * Nota de alcance (L2): [transferCount] se cuenta sobre los movimientos que esta pantalla ya
 * tiene cargados. En Android eso sale del espejo local, y el `SyncEngine` **solo empuja** — nada
 * baja del server. Si un traspaso se creó en otro dispositivo y nunca pasó por este teléfono, no
 * entra en el número. Es arquitectura preexistente (no hay pull de eventos en ninguna pantalla),
 * no algo que este aviso introduzca; el borrado del server igual desenlaza todas las patas, así
 * que el dato que puede quedar corto es el aviso, nunca la base.
 */
internal fun transferWarningLabel(count: Int, amount: Long, currency: String): String {
    // Ola 14 — el monto entra al aviso. Antes decía cuántos movimientos eran, no cuánta plata:
    // suficiente cuando un traspaso era entre dos cuentas de dinero, insuficiente desde que una
    // punta puede ser un crédito y ese renglón puede valer el crédito entero. «1 movimiento» y
    // «$257.000.000» piden decisiones distintas.
    // Sin coma de cierre: la frase sigue con un punto, y con ella el texto salía «…con otra
    // cuenta, por $257.000.000 en total,. Esa cuenta…». Preexistente, y visible en la única hoja
    // donde el dueño lee con lupa.
    val cuanto = if (amount > 0L) ", por ${formatMoney(amount, currency)} en total" else ""
    return if (count == 1) {
        "1 de esos movimientos es un traspaso con otra cuenta$cuanto. Esa cuenta conserva su " +
            "mitad y su saldo no cambia, pero ese movimiento deja de ser un traspaso: queda " +
            "suelto, en la categoría «$ORPHANED_LEG_CATEGORY». No suma a tus gastos ni a tus " +
            "ingresos del mes; si esa plata sí se movió de verdad, puedes cambiarle la categoría " +
            "después."
    } else {
        "$count de esos movimientos son traspasos con otras cuentas$cuanto. Esas cuentas " +
            "conservan su mitad y sus saldos no cambian, pero esos movimientos dejan de ser " +
            "traspasos: quedan sueltos, en la categoría «$ORPHANED_LEG_CATEGORY». No suman a tus " +
            "gastos ni a tus ingresos del mes; si esa plata sí se movió de verdad, puedes " +
            "cambiarles la categoría después."
    }
}
