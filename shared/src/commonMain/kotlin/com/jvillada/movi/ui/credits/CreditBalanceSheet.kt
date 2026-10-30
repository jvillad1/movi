package com.jvillada.movi.ui.credits

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Radio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.MAX_CREDIT_DEBT_COP
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja

/**
 * Cuadra la deuda de un crédito con el saldo que reporta el banco.
 *
 * Va aparte de [CreditTermsSheet] a propósito: los términos son el contrato (tasa, cuota,
 * plazo) y casi nunca cambian; la deuda es estado y se mueve todos los días por intereses.
 * Mezclarlos haría creer que editar la tasa recalcula lo que se debe.
 *
 * No edita un número: manda el saldo objetivo y el server registra la diferencia como un
 * movimiento visible de la cuenta, que es de donde sale la deuda.
 */
@Composable
fun CreditBalanceSheet(
    credit: CreditSummary,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    /**
     * Vuelta 77 · **la cifra del plan, ya escrita** («Usar este»): el campo llega lleno con ella y debajo
     * dice que es la del plan y que, si el banco dice otra, se escribe ahí mismo. Igual se guarda con
     * «Registrar ajuste»: nunca solo. `null` = el campo vacío, como siempre.
     */
    cifraPropuesta: Long? = null,
) {
    HojaDeAjustarLaDeuda(
        inicial = cifraPropuesta,
        nombre = credit.account.name,
        deudaEnMovi = credit.account.balance,
        moneda = "COP",
        pagada = credit.hasMovements,
        rotuloDelCampo = "Deuda real según el banco (COP)",
        ayuda = "Copia el saldo que muestra la banca en línea hoy.",
        ajustar = { objetivo -> Repositories.wallets.adjustCreditBalance(credit.account.id, objetivo) },
        onDismiss = onDismiss,
        onSaved = onSaved,
    )
}

/** El rótulo del campo de la hoja de ajustar el saldo de una tarjeta (vuelta 33). */
const val CUANTO_DEBES_HOY_SEGUN_EL_BANCO: String = "Cuánto debes hoy según el banco"

/**
 * **Cuadra la deuda de una tarjeta** con lo que dice el banco (vuelta 33). Hasta acá no había dónde:
 * la ruta de Créditos solo acepta préstamos y la de cuentas rechazaba toda deuda. Ahora la de
 * cuentas acepta tarjetas (`POST /api/accounts/{id}/balance-adjustment`, con la convención de la
 * deuda), y esta es su hoja: la MISMA que la del préstamo, en la moneda de la tarjeta.
 *
 * La banca en línea muestra cuatro cifras de una tarjeta (saldo actual, saldo a pagar, cupo
 * disponible, compras sin facturar); la ayuda dice cuál es la que va: **la deuda total de hoy**.
 * Escribirla es todo lo que hace el dueño —Movi calcula la diferencia contra el saldo sin anulados—,
 * y nunca se ajusta solo.
 */
@Composable
fun CardBalanceSheet(
    card: CardSummary,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val (deuda, moneda) = saldoEnSuMoneda(card.account)
    HojaDeAjustarLaDeuda(
        nombre = card.account.name,
        deudaEnMovi = deuda,
        moneda = moneda,
        rotuloDelCampo = if (moneda == "COP") CUANTO_DEBES_HOY_SEGUN_EL_BANCO else "$CUANTO_DEBES_HOY_SEGUN_EL_BANCO ($moneda)",
        ayuda = "Escribe la deuda total de hoy, no el pago mínimo ni el cupo disponible.",
        ajustar = { objetivo -> Repositories.wallets.adjustAccountBalance(card.account.id, objetivo) },
        onDismiss = onDismiss,
        onSaved = onSaved,
    )
}

/**
 * La hoja de ajustar el saldo de una deuda, préstamo o tarjeta: la deuda en Movi, el campo con lo que
 * dice el banco, qué se va a registrar y «Registrar ajuste». [ajustar] manda el saldo objetivo; el
 * server escribe la diferencia.
 */
@Composable
private fun HojaDeAjustarLaDeuda(
    /** Vuelta 77: la cifra con que llega el campo (la del plan), o `null`. */
    inicial: Long? = null,
    nombre: String,
    deudaEnMovi: Long,
    moneda: String,
    /** ¿Un cero es «pagada»? Un préstamo sin movimientos en cero está sin saldo (ver [saldoDeUnaDeuda]). */
    pagada: Boolean = true,
    rotuloDelCampo: String,
    ayuda: String,
    ajustar: suspend (Long) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val current = deudaEnMovi

    var target by remember { mutableStateOf(inicial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // El techo se espeja del server (MAX_CREDIT_DEBT_COP, en :core) para poder explicar el
    // dedazo acá mismo en vez de mandarlo y traducir un 400. MoneyField ya descarta lo que no
    // quepa en un Long al escribir, así que ese caso ya no llega acá con `parsed == null`.
    val parsed = target
    val overCap = parsed != null && parsed > MAX_CREDIT_DEBT_COP
    val delta = parsed?.let { it - current }
    val canSave = parsed != null && !overCap && delta != null && delta != 0L && !saving

    fun adjust() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { ajustar(parsed!!) }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            // Vuelta de diseño 18: el nombre como título y qué es la hoja debajo (era un rótulo chico
            // arriba y el nombre debajo).
            EncabezadoDeHoja(nombre, subtitulo = AJUSTAR_EL_SALDO)
            Spacer(Modifier.height(Espacio.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Deuda en Movi", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                // Vuelta 59: un solo signo. Lo que se debe, sin signo; en negativo no es «−$980.000»
                // sino una deuda a la que Movi no le tiene el saldo — que es lo que esta hoja arregla.
                val saldo = saldoDeUnaDeuda(current, moneda, pagada = pagada)
                if (saldo.conSaldo) {
                    Cifra(current, moneda = moneda)
                } else {
                    Text(SIN_SALDO_EN_MOVI, style = Movi.textos.cuerpo, color = Movi.colores.aviso)
                }
            }
            Spacer(Modifier.height(Espacio.s))

            MoneyField(
                value = target,
                onValueChange = { target = it },
                placeholder = rotuloDelCampo,
            )

            // Vuelta 77: la cifra que trajo «Usar este» es la del plan; otra cifra se escribe acá mismo.
            if (inicial != null && target == inicial) {
                Spacer(Modifier.height(Espacio.xs))
                Text(ES_LA_CIFRA_DE_TU_PLAN, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = when {
                    overCap        -> "Saldo fuera de rango — revisa el monto."
                    parsed == null -> ayuda
                    delta == 0L    -> "Ya coincide con Movi — no hay nada que registrar."
                    delta!! > 0L   -> "Se registrará un cargo de ${formatMoney(delta, moneda)} para subir la deuda."
                    else           -> "Se registrará un abono de ${formatMoney(-delta, moneda)} para bajar la deuda."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Espacio.xs))
            Text(
                "Queda como un movimiento visible en la cuenta, no como un número editado a mano.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )

            error?.let {
                Spacer(Modifier.height(Espacio.xs))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
        }

        Spacer(Modifier.height(Espacio.m))
        Boton(
            texto = if (saving) "Registrando…" else "Registrar ajuste",
            onClick = { adjust() },
            habilitado = canSave,
            anchoCompleto = true,
        )
        Spacer(Modifier.height(Espacio.l))
    }
}

/** Vuelta 77: debajo del campo lleno con la cifra del plan («Usar este»). */
const val ES_LA_CIFRA_DE_TU_PLAN: String = "Es la cifra de tu plan. Si el banco dice otra, escríbela aquí."
