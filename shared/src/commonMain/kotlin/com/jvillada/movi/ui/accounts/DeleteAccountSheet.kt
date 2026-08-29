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
     * movimientos es un traspaso» puede querer decir $50.000 o $257.000.000, y de eso depende si
     * el mes que vuelve a contarlo queda irreconocible. Con default en 0 = no se dice el monto,
     * que es el aviso de antes.
     */
    transferAmount: Long = 0L,
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
            if (transferCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = transferWarningLabel(transferCount, transferAmount, transferCurrency),
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
 * El aviso de los traspasos, **antes** de borrar y no después.
 *
 * Un traspaso tiene una pata en cada cuenta. Al borrar esta, la pata de la OTRA cuenta sobrevive
 * —tiene que sobrevivir: la plata salió de verdad y el saldo de esa cuenta lo refleja— pero se
 * queda sin la mitad que la explicaba, así que el server la suelta del par y le pone una
 * categoría propia (ver `desenlazarPatasHermanas` en `AccountRoutes.kt` y
 * `ORPHANED_LEG_CATEGORY` en `:core`). Eso cambia las cifras de un mes que el dueño ya daba por cerrado, y esa es justo
 * la clase de cosa de la que no puede enterarse después: se dice acá, con el número real, antes
 * de tocar el botón rojo.
 *
 * **Se nombra la categoría de destino** porque sin ella el aviso no es accionable: quien tenga un
 * presupuesto necesita saber en qué renglón va a aparecer ese monto. Y se dice **«un mes que ya
 * diste por cerrado»** en vez de «el mes en que ocurrió»: las dos son ciertas, pero solo la
 * primera dice lo que importa — que lo que va a cambiar son totales viejos, no los de este mes.
 *
 * Nota de alcance (L2): [transferCount] se cuenta sobre los movimientos que esta pantalla ya
 * tiene cargados. En Android eso sale del espejo local, y el `SyncEngine` **solo empuja** — nada
 * baja del server. Si un traspaso se creó en otro dispositivo y nunca pasó por este teléfono, no
 * entra en el número. Es arquitectura preexistente (no hay pull de eventos en ninguna pantalla),
 * no algo que este aviso introduzca; el borrado del server igual desenlaza todas las patas, así
 * que el dato que puede quedar corto es el aviso, nunca la base.
 */
private fun transferWarningLabel(count: Int, amount: Long, currency: String): String {
    // Ola 14 — el monto entra al aviso. Antes decía cuántos movimientos eran, no cuánta plata:
    // suficiente cuando un traspaso era entre dos cuentas de dinero, insuficiente desde que una
    // punta puede ser un crédito y ese renglón puede valer el crédito entero. «1 movimiento» y
    // «$257.000.000» piden decisiones distintas.
    val cuanto = if (amount > 0L) ", por ${formatMoney(amount, currency)} en total," else ""
    return if (count == 1) {
        "1 de esos movimientos es un traspaso con otra cuenta$cuanto. Esa cuenta conserva su " +
            "mitad y su saldo no cambia, pero ese movimiento deja de ser un traspaso: pasa a la " +
            "categoría «$ORPHANED_LEG_CATEGORY» y vuelve a contar en los gastos o ingresos de un " +
            "mes que ya diste por cerrado. Puedes cambiarle la categoría después."
    } else {
        "$count de esos movimientos son traspasos con otras cuentas$cuanto. Esas cuentas " +
            "conservan su mitad y sus saldos no cambian, pero esos movimientos dejan de ser " +
            "traspasos: pasan a la categoría «$ORPHANED_LEG_CATEGORY» y vuelven a contar en los " +
            "gastos o ingresos de meses que ya diste por cerrados. Puedes cambiarles la " +
            "categoría después."
    }
}
