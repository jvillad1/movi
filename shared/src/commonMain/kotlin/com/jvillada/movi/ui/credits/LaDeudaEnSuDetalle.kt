package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.recurrentes.BajoLaFila
import com.jvillada.movi.ui.recurrentes.MontoDeFila
import kotlinx.datetime.Clock

/*
 * # La deuda en su detalle (vuelta de diseño 4)
 *
 * La lista de Créditos dejó de ser un formulario: cada deuda es una fila que abre el detalle de su
 * cuenta. Lo que la tarjeta de antes tenía adentro —la cuota, el plazo, cuánto de la cuota es
 * interés, cómo va la deuda, la barra— y **todos sus botones** («Editar», «Ajustar saldo», «¿Y si
 * abonas de más?», «Registrar pago de …», «Registrar descuento») viven acá, en una sección de ese
 * detalle. Ninguna acción se perdió en la mudanza: lo vigila `LaDeudaEnSuDetalleTest`.
 */

/** Qué hoja de la deuda está abierta. */
enum class HojaDeLaDeuda { Condiciones, Saldo, Abono }

/** El título de la acción que abre la hoja de condiciones. */
const val EDITAR_LAS_CONDICIONES: String = "Editar las condiciones"

/** El título de la acción que abre la hoja de ajustar el saldo de un préstamo. */
const val AJUSTAR_EL_SALDO: String = "Ajustar saldo"

/** La fila de una deuda sin términos, en su detalle. */
const val FALTAN_LAS_CONDICIONES: String = "Faltan las condiciones"

/** El tag de la sección de la deuda en el detalle de la cuenta. */
const val TAG_SECCION_DE_LA_DEUDA: String = "seccion-de-la-deuda"

/**
 * **Lo que el detalle de una cuenta de deuda sabe de ella**: el préstamo o la tarjeta con sus
 * términos, el plan del préstamo, el período del dueño y qué hoja está abierta.
 *
 * Se lee con las mismas lecturas que Créditos ([ClaveDeLectura.Creditos], [ClaveDeLectura.Tarjetas]):
 * viniendo de ahí, la sección se pinta en el primer cuadro.
 */
class DeudaDelDetalle internal constructor(
    val prestamo: CreditSummary?,
    val tarjeta: CardSummary?,
    val plan: PlanDelCredito?,
    val periodoActual: PeriodoFinanciero,
    private val abierto: LoAbiertoDeLaDeuda,
) {
    /** La hoja abierta. Vive aparte: releer la deuda (otro objeto) no cierra la hoja. */
    var hoja: HojaDeLaDeuda?
        get() = abierto.hoja
        set(valor) { abierto.hoja = valor }

    /** El préstamo cuyo pago de un tercero o descuento de nómina se está registrando. */
    var registrando: CreditSummary?
        get() = abierto.registrando
        set(valor) { abierto.registrando = valor }
}

/** Lo que el dueño abrió en la deuda: sobrevive a que la deuda se relea. */
internal class LoAbiertoDeLaDeuda {
    var hoja: HojaDeLaDeuda? by mutableStateOf(null)
    var registrando: CreditSummary? by mutableStateOf(null)
}

/**
 * La [DeudaDelDetalle] de la cuenta [accountId], o `null` si [tipo] no es una deuda (o todavía no se
 * sabe). [reintento] es el del detalle: cuando una hoja guarda algo, el detalle lo sube y esto relee.
 */
@Composable
fun rememberDeudaDelDetalle(accountId: String, tipo: AccountType?, reintento: Int): DeudaDelDetalle? {
    val esPrestamo = tipo == AccountType.LOAN
    val esTarjeta = tipo == AccountType.CREDIT_CARD
    val creditos = rememberLectura(ClaveDeLectura.Creditos, reintento = reintento, activa = esPrestamo) {
        Repositories.wallets.getCredits()
    }
    val tarjetas = rememberLectura(ClaveDeLectura.Tarjetas, reintento = reintento, activa = esTarjeta) {
        Repositories.wallets.getCards()
    }
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = reintento, activa = esPrestamo) {
        Repositories.wallets.getUserProfile()
    }
    if (!esPrestamo && !esTarjeta) return null
    val prestamo = if (esPrestamo) creditos.valor?.firstOrNull { it.account.id == accountId } else null
    val tarjeta = if (esTarjeta) tarjetas.valor?.firstOrNull { it.account.id == accountId } else null
    // El mes en curso con el período del dueño (ver el KDoc de `ajustesDelPeriodo` en Créditos): sin
    // perfil, el de calendario. Solo nombra el mes de la última cuota.
    val ajustes = perfil.valor?.ajustesDelPeriodo() ?: PeriodSettings()
    val periodoActual = remember(ajustes) { periodoDe(Clock.System.now().toEpochMilliseconds(), ajustes) }
    val plan = remember(prestamo) { prestamo?.let { planDelCredito(it, hoyEnAppZone()) } }
    val abierto = remember(accountId) { LoAbiertoDeLaDeuda() }
    val deuda = remember(prestamo, tarjeta, plan, periodoActual, abierto) {
        DeudaDelDetalle(prestamo, tarjeta, plan, periodoActual, abierto)
    }
    return deuda
}

/**
 * **La sección de la deuda en el detalle de su cuenta**: sus condiciones y sus acciones, cada una una
 * [Fila]. Nada si la deuda todavía no llegó.
 */
@Composable
fun SeccionDeLaDeuda(deuda: DeudaDelDetalle, modifier: Modifier = Modifier) {
    val prestamo = deuda.prestamo
    val tarjeta = deuda.tarjeta
    if (prestamo == null && tarjeta == null) return
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_SECCION_DE_LA_DEUDA)) {
        val completar = { deuda.hoja = HojaDeLaDeuda.Condiciones }
        Seccion(titulo = if (prestamo != null) "El préstamo" else "La tarjeta")
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            if (prestamo != null) LoQueDiceElPrestamo(prestamo, deuda.plan, deuda.periodoActual, completar)
            if (tarjeta != null) LoQueDiceLaTarjeta(tarjeta, completar)
        }
        val acciones = accionesDeLaDeuda(deuda)
        if (acciones.isEmpty()) return@Column
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = "Qué puedes hacer")
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            acciones.forEachIndexed { i, accion ->
                if (i > 0) Separador()
                Fila(
                    titulo = accion.titulo,
                    subtitulo = accion.subtitulo,
                    inicio = { IconoEnCirculo(accion.icono) },
                    final = { Chevron() },
                    onClick = accion.onClick,
                )
            }
        }
    }
}

/** Una acción de la deuda, como fila: lo que antes era un botón dentro de la tarjeta de la lista. */
private class AccionDeLaDeuda(
    val titulo: String,
    val subtitulo: String?,
    val icono: ImageVector,
    val onClick: () -> Unit,
)

private fun accionesDeLaDeuda(deuda: DeudaDelDetalle): List<AccionDeLaDeuda> = buildList {
    val prestamo = deuda.prestamo
    val terms = prestamo?.terms
    if (prestamo != null) {
        if (deuda.plan?.comoVa?.seProyecta == true) {
            add(AccionDeLaDeuda(ACCION_SIMULAR_ABONO, "Cuánto te ahorras y cuándo terminas", Icons.Rounded.Savings) { deuda.hoja = HojaDeLaDeuda.Abono })
        }
        val quienPaga = terms?.paidBy?.takeIf { it.isNotBlank() }
        if (quienPaga != null && terms.payrollDeduction != true) {
            add(AccionDeLaDeuda("Registrar pago de $quienPaga", "La cuota de este mes", Icons.Rounded.TaskAlt) { deuda.registrando = prestamo })
        }
        if (terms?.payrollDeduction == true) {
            add(AccionDeLaDeuda("Registrar descuento", "La cuota que te descontaron de la nómina", Icons.Rounded.TaskAlt) { deuda.registrando = prestamo })
        }
        add(AccionDeLaDeuda(AJUSTAR_EL_SALDO, "Cuando no cuadra con lo que dice el banco", Icons.Rounded.Tune) { deuda.hoja = HojaDeLaDeuda.Saldo })
    }
    if (deuda.prestamo?.terms != null || deuda.tarjeta?.terms != null) {
        add(AccionDeLaDeuda(EDITAR_LAS_CONDICIONES, null, Icons.Rounded.Edit) { deuda.hoja = HojaDeLaDeuda.Condiciones })
    }
}

/** Un préstamo: la cuota con quién la paga, el avance y lo que se sabe del plan. */
@Composable
private fun LoQueDiceElPrestamo(
    credit: CreditSummary,
    plan: PlanDelCredito?,
    periodoActual: PeriodoFinanciero,
    onCompletar: () -> Unit,
) {
    val t = credit.terms
    if (t == null) {
        FilaDeFaltanLasCondiciones(onCompletar)
        return
    }
    val hoy = hoyEnAppZone()
    val tasa = if (t.sinIntereses) "Sin intereses" else "${t.rateEa}% EA"
    Fila(
        titulo = "Cuota",
        subtitulo = "Día ${t.dayOfMonth} · la próxima el ${fechaEnPalabras(proximaFechaDelDia(t.dayOfMonth, hoy), hoy)}",
        // Al lado de la cuota, a 360 dp, la fecha no entra en un renglón: baja al segundo.
        lineasDelSubtitulo = 2,
        inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
        final = { Cifra(t.installment) },
    )
    BajoLaFila {
        // Quién la paga, si no es el dueño: un estado de la cuota, en una píldora.
        when {
            t.payrollDeduction -> Pildora("De tu nómina", TonoDePildora.Info)
            !t.paidBy.isNullOrBlank() -> Pildora("La paga ${t.paidBy}", TonoDePildora.Info)
        }
        Text(listOfNotNull(t.bank.takeIf { it.isNotBlank() }, tasa).joinToString(" · "), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Text(
            "Plazo pactado ${t.termMonths} meses · desde el ${fechaEnPalabras(t.startDate, hoy)}",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )
        plan?.let { p ->
            Text(textoDelInteres(p), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            comoVaEstaDeuda(p, periodoActual)?.let { comoVa ->
                Text(
                    comoVa.texto,
                    style = if (comoVa.esAlerta) Movi.textos.apoyoMedio else Movi.textos.apoyo,
                    color = if (comoVa.esAlerta) Movi.colores.sale else Movi.colores.textoApagado,
                )
            }
        }
        val progreso = progresoDeCredito(credit)
        if (progreso.esAviso) {
            Text(progreso.etiqueta, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        } else {
            prestamoEnLaLista(credit, plan, periodoActual, hoy).let { enLaLista ->
                val fraccion = enLaLista.fraccionPagada
                if (fraccion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                        Box(Modifier.weight(1f).testTag(TAG_BARRA_DE_PROGRESO)) {
                            BarraDeProgreso(fraccion = fraccion, tono = TonoDeBarra.Neutro)
                        }
                        Text(enLaLista.avance.orEmpty(), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
                    }
                }
            }
        }
        t.notes?.takeIf { it.isNotBlank() }?.let { nota ->
            Text(text = nota, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

/** Una tarjeta: el pago (mínimo y día), el corte y el cupo disponible, en su moneda. */
@Composable
private fun LoQueDiceLaTarjeta(card: CardSummary, onCompletar: () -> Unit) {
    val t = card.terms
    if (t == null) {
        FilaDeFaltanLasCondiciones(onCompletar)
        return
    }
    val hoy = hoyEnAppZone()
    val moneda = card.account.currency
    val pago = fechaEnPalabras(proximaFechaDelDia(t.paymentDay, hoy), hoy)
    Fila(
        titulo = if (t.pagoMinimo != null) "Pago mínimo" else "Pago",
        subtitulo = t.cutoffDay?.let { "El $pago · corte el día $it" } ?: "El $pago",
        lineasDelSubtitulo = 2,
        inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
        final = t.pagoMinimo?.let { minimo -> { MontoDeFila(formatMoney(minimo, moneda)) } },
    )
    card.available?.let { disponible ->
        Separador()
        Fila(
            titulo = "Disponible",
            subtitulo = t.creditLimit?.let { "De un cupo de ${formatMoney(it, moneda)}" },
            inicio = { IconoEnCirculo(Icons.Rounded.AccountBalanceWallet, tono = TonoDeIcono.Neutro) },
            final = { MontoDeFila(formatMoney(disponible, moneda)) },
        )
    }
}

/** Sin términos: la fila que lleva a completarlos. La misma frase de aviso que la lista. */
@Composable
private fun FilaDeFaltanLasCondiciones(onCompletar: () -> Unit) {
    Fila(
        titulo = FALTAN_LAS_CONDICIONES,
        subtitulo = FALTAN_DATOS_DE_LA_DEUDA,
        tonoDelSubtitulo = TonoDeSubtitulo.Aviso,
        lineasDelSubtitulo = 2,
        inicio = { IconoEnCirculo(Icons.Rounded.Edit, tono = TonoDeIcono.Aviso) },
        final = { Chevron() },
        onClick = onCompletar,
    )
}

/**
 * **Las hojas de la deuda**, fuera de la lista (una hoja dentro de un ítem perezoso se iría con él):
 * editar las condiciones, ajustar el saldo, simular un abono, y registrar el pago de un tercero o el
 * descuento de nómina —sin hoja: el monto es la cuota y el server lo hace idempotente por mes, así
 * que un doble toque no baja la deuda dos veces—. [onCambiada] relee el detalle; [onError] lo dice.
 */
@Composable
fun HojasDeLaDeuda(deuda: DeudaDelDetalle, onCambiada: () -> Unit, onError: (String) -> Unit) {
    val prestamo = deuda.prestamo
    val tarjeta = deuda.tarjeta
    when (deuda.hoja) {
        HojaDeLaDeuda.Condiciones -> when {
            prestamo != null -> CreditTermsSheet(
                editing = prestamo,
                candidates = emptyList(),
                onDismiss = { deuda.hoja = null },
                onSaved = { deuda.hoja = null; onCambiada() },
            )
            tarjeta != null -> CardTermsSheet(
                editing = tarjeta,
                onDismiss = { deuda.hoja = null },
                onSaved = { deuda.hoja = null; onCambiada() },
            )
        }
        HojaDeLaDeuda.Saldo -> if (prestamo != null) {
            CreditBalanceSheet(
                credit = prestamo,
                onDismiss = { deuda.hoja = null },
                onSaved = { deuda.hoja = null; onCambiada() },
            )
        }
        HojaDeLaDeuda.Abono -> if (prestamo != null) {
            SimuladorDeAbonoSheet(credit = prestamo, periodoActual = deuda.periodoActual, onDismiss = { deuda.hoja = null })
        }
        null -> Unit
    }
    deuda.registrando?.let { credit ->
        LaunchedEffect(credit.account.id) {
            runCatching { Repositories.wallets.registerPayrollDeduction(credit.account.id) }
                .onSuccess { deuda.registrando = null; onCambiada() }
                .onFailure { deuda.registrando = null; onError(it.toUserMessage()) }
        }
    }
}
