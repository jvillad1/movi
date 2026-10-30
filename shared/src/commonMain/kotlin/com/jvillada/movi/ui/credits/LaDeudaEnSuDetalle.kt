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
import androidx.compose.material.icons.rounded.Payments
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
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
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

/** El título de la acción que abre la hoja de ajustar el saldo de un préstamo o (vuelta 33) una tarjeta. */
const val AJUSTAR_EL_SALDO: String = "Ajustar saldo"

/** La acción de la tarjeta que abre «Agregar» para anotar su pago (vuelta de diseño 32). */
const val REGISTRAR_UN_PAGO_DE_LA_TARJETA: String = "Registrar un pago"

/** El botón del aviso de arriba del detalle cuando a la deuda le faltan sus datos (vuelta 31). */
const val COMPLETAR_DATOS: String = "Completar datos"

/** El tag del aviso de que faltan datos, arriba del detalle de la deuda. */
const val TAG_FALTAN_DATOS_DE_LA_DEUDA: String = "faltan-datos-de-la-deuda"

/** Vuelta 62 · el tag de la fila del extracto de la tarjeta en su ficha. */
const val TAG_EXTRACTO_DE_LA_TARJETA: String = "extracto-de-la-tarjeta"

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
    /**
     * **El mes de la primera cuota sin pagar** (vuelta 59): de ahí se cuentan las que faltan y la última
     * («la última en noviembre de 2030»), no del mes en curso. Ver [mesDeLaPrimeraCuota].
     */
    val primeraCuota: PeriodoFinanciero = periodoActual,
    /** Vuelta 77: los ajustes del período del dueño, para saber cuál es la cuota de este período. */
    val ajustes: PeriodSettings = PeriodSettings(),
) {
    /** La hoja abierta. Vive aparte: releer la deuda (otro objeto) no cierra la hoja. */
    var hoja: HojaDeLaDeuda?
        get() = abierto.hoja
        set(valor) { abierto.hoja = valor }

    /** El préstamo cuyo pago de un tercero o descuento de nómina se está registrando. */
    var registrando: CreditSummary?
        get() = abierto.registrando
        set(valor) { abierto.registrando = valor }

    /** Vuelta 77: la cifra con que se abre la hoja del saldo («Usar este», la del plan), o `null`. */
    var cifraPropuesta: Long?
        get() = abierto.cifraPropuesta
        set(valor) { abierto.cifraPropuesta = valor }
}

/** Lo que el dueño abrió en la deuda: sobrevive a que la deuda se relea. */
internal class LoAbiertoDeLaDeuda {
    var hoja: HojaDeLaDeuda? by mutableStateOf(null)
    var registrando: CreditSummary? by mutableStateOf(null)
    var cifraPropuesta: Long? by mutableStateOf(null)
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
    val primeraCuota = remember(prestamo, periodoActual) {
        prestamo?.let { mesDeLaPrimeraCuota(it, hoyEnAppZone()) } ?: periodoActual
    }
    val deuda = remember(prestamo, tarjeta, plan, periodoActual, abierto, primeraCuota, ajustes) {
        DeudaDelDetalle(prestamo, tarjeta, plan, periodoActual, abierto, primeraCuota, ajustes)
    }
    return deuda
}

/**
 * **La sección de la deuda en el detalle de su cuenta**: sus condiciones y sus acciones, cada una una
 * [Fila]. Nada si la deuda todavía no llegó.
 */
@Composable
fun SeccionDeLaDeuda(
    deuda: DeudaDelDetalle,
    modifier: Modifier = Modifier,
    /**
     * Vuelta de diseño 32: abre «Agregar» en la pestaña del pago con esta tarjeta en «Hacia» (lo que
     * ya hace «Anotar este pago» desde Plan). `null` = sin esa acción.
     */
    onRegistrarPagoDeLaTarjeta: (() -> Unit)? = null,
    /**
     * Vuelta 77 · **«Pagar la cuota»**, la acción principal del préstamo: abre Agregar → Pagar con el
     * crédito y su cuota ([pantallaDePagarLaCuota]). `null` = sin el botón.
     */
    onPagarLaCuota: ((com.jvillada.movi.ui.Screen) -> Unit)? = null,
) {
    val prestamo = deuda.prestamo
    val tarjeta = deuda.tarjeta
    if (prestamo == null && tarjeta == null) return
    val acciones = accionesDeLaDeuda(deuda, onRegistrarPagoDeLaTarjeta)
    val conCondiciones = !faltanLosDatos(deuda)
    val laCuota = if (prestamo != null && onPagarLaCuota != null) {
        laCuotaEnLaFicha(prestamo, deuda.primeraCuota, hoyEnAppZone(), deuda.ajustes)
    } else {
        null
    }
    if (!conCondiciones && acciones.isEmpty() && laCuota == null) return
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_SECCION_DE_LA_DEUDA)) {
        // Vuelta 77: la acción principal de un préstamo es pagar su cuota («Pagar la cuota de noviembre ·
        // $770.000»); si la de este período ya está saldada, lo dice («Cuota de noviembre · pagada»).
        if (laCuota != null && prestamo != null) {
            if (laCuota.pagada) {
                Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_CUOTA_PAGADA), relleno = RellenoDeSuperficie.Lista) {
                    Fila(
                        titulo = textoDeLaCuotaEnLaFicha(laCuota),
                        inicio = { IconoEnCirculo(Icons.Rounded.TaskAlt, tono = TonoDeIcono.Exito) },
                    )
                }
            } else {
                Boton(
                    texto = textoDeLaCuotaEnLaFicha(laCuota),
                    onClick = { onPagarLaCuota?.invoke(pantallaDePagarLaCuota(prestamo, laCuota)) },
                    anchoCompleto = true,
                    modifier = Modifier.testTag(TAG_PAGAR_LA_CUOTA),
                )
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
        // Sin términos no hay nada que decir de la cuota ni del pago: lo dice el aviso de arriba del
        // detalle ([AvisoDeFaltanDatos]) con «Completar datos», y acá queda lo que sí se puede hacer.
        if (conCondiciones) {
            Seccion(titulo = if (prestamo != null) "El préstamo" else "La tarjeta")
            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                if (prestamo != null) LoQueDiceElPrestamo(prestamo, deuda.plan, deuda.primeraCuota, deuda.periodoActual)
                if (tarjeta != null) LoQueDiceLaTarjeta(tarjeta)
            }
        }
        if (acciones.isEmpty()) return@Column
        if (conCondiciones) Spacer(Modifier.height(Medida.entreSecciones))
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

private fun accionesDeLaDeuda(deuda: DeudaDelDetalle, onRegistrarPagoDeLaTarjeta: (() -> Unit)?): List<AccionDeLaDeuda> = buildList {
    // Vuelta de diseño 32: la tarjeta también tiene «Qué puedes hacer», empezando por anotar lo que le
    // pagaste (el traspaso de dos patas de la pestaña del pago de Agregar). Con o sin sus datos.
    if (deuda.tarjeta != null && onRegistrarPagoDeLaTarjeta != null) {
        add(AccionDeLaDeuda(REGISTRAR_UN_PAGO_DE_LA_TARJETA, "Lo que le pagaste desde otra cuenta", Icons.Rounded.Payments, onRegistrarPagoDeLaTarjeta))
    }
    // Vuelta 33: la tarjeta también se cuadra contra el banco, con la misma hoja que un préstamo
    // ([CardBalanceSheet]). Con o sin sus datos: lo que se debe no depende del corte ni del cupo.
    if (deuda.tarjeta != null) {
        // Vuelta 77: sin saldo, la ficha ya ofrece arriba «Cuadrar» con la misma hoja: una sola acción.
        if (!laFichaOfreceCargarElSaldo(deuda)) {
            add(AccionDeLaDeuda(AJUSTAR_EL_SALDO, "Cuando no cuadra con lo que dice el banco", Icons.Rounded.Tune) { deuda.hoja = HojaDeLaDeuda.Saldo })
        }
    }
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
        // Vuelta 77: «Cargarlo» (sin saldo, arriba de la ficha) y «Ajustar saldo» abren la misma hoja y no
        // conviven: sin saldo, «Cargarlo»; con saldo, «Ajustar saldo».
        if (!laFichaOfreceCargarElSaldo(deuda)) {
            add(AccionDeLaDeuda(AJUSTAR_EL_SALDO, "Cuando no cuadra con lo que dice el banco", Icons.Rounded.Tune) { deuda.hoja = HojaDeLaDeuda.Saldo })
        }
    }
    if (deuda.prestamo?.terms != null || deuda.tarjeta?.terms != null) {
        add(AccionDeLaDeuda(EDITAR_LAS_CONDICIONES, null, Icons.Rounded.Edit) { deuda.hoja = HojaDeLaDeuda.Condiciones })
    }
}

/**
 * **¿El encabezado de la ficha ya ofrece cargar el saldo?** (vuelta 77) Sí cuando la deuda no tiene saldo
 * en Movi (`saldoDeUnaDeuda(...).conSaldo` falso: en negativo, o un préstamo en cero sin movimientos): ahí
 * va «Cargarlo» (o «Cuadrar»), y «Ajustar saldo» sería el mismo botón dos veces. Pura.
 */
fun laFichaOfreceCargarElSaldo(deuda: DeudaDelDetalle): Boolean {
    val cuenta = deuda.prestamo?.account ?: deuda.tarjeta?.account ?: return false
    val (monto, _) = com.jvillada.movi.ui.components.cardDebt(cuenta)
    return !saldoDeUnaDeuda(monto, pagada = deuda.prestamo?.hasMovements ?: true).conSaldo
}

/** Un préstamo: la cuota con quién la paga, el avance y lo que se sabe del plan. */
@Composable
private fun LoQueDiceElPrestamo(
    credit: CreditSummary,
    plan: PlanDelCredito?,
    /** El mes de la primera cuota sin pagar: de ahí se cuenta la última (vuelta 59). */
    primeraCuota: PeriodoFinanciero,
    periodoActual: PeriodoFinanciero,
) {
    val t = credit.terms ?: return
    val hoy = hoyEnAppZone()
    val tasa = if (t.sinIntereses) "Sin intereses" else "${t.rateEa}% EA"
    Fila(
        titulo = "Cuota",
        // Vuelta 64: de la MISMA primera cuota sin pagar con que se cuentan las que faltan; si ya
        // venció, lo dice («Cuota de octubre vencida desde el 5 oct») en vez de saltar a la próxima.
        subtitulo = subtituloDeLaCuota(t.dayOfMonth, primeraCuota, hoy),
        tonoDelSubtitulo = if (laCuotaEstaVencida(t.dayOfMonth, primeraCuota, hoy)) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
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
            comoVaEstaDeuda(p, primeraCuota)?.let { comoVa ->
                Text(
                    comoVa.texto,
                    style = if (comoVa.esAlerta) Movi.textos.apoyoMedio else Movi.textos.apoyo,
                    color = if (comoVa.esAlerta) Movi.colores.sale else Movi.colores.textoApagado,
                )
            }
        }
        val progreso = progresoDeCredito(credit)
        if (progreso.esAviso) {
            // Vuelta 59: «Movi no tiene su saldo…» ya lo dice el encabezado de la ficha, con «Cuadrar».
            if (progreso.etiqueta != FALTA_EL_SALDO_DE_LA_DEUDA) {
                Text(progreso.etiqueta, style = Movi.textos.apoyo, color = Movi.colores.aviso)
            }
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
private fun LoQueDiceLaTarjeta(card: CardSummary) {
    val t = card.terms ?: return
    val hoy = hoyEnAppZone()
    val moneda = card.account.currency
    val pago = fechaEnPalabras(proximaFechaDelDia(t.paymentDay, hoy), hoy)
    val extracto = card.extracto
    if (extracto != null) {
        // Vuelta 62 · **el extracto, por fechas**: lo facturado al corte (menos lo ya pagado), el mínimo y
        // cuándo pagarlo; debajo, lo que entró después del corte y va al próximo extracto.
        val pagado = extractoPagado(extracto)
        if (pagado) {
            // Revisión: con el extracto pagado no hay mínimo, ni fecha, ni diferidos que advertir.
            Fila(
                titulo = tituloDelExtractoPagado(extracto),
                inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
                modifier = Modifier.testTag(TAG_EXTRACTO_DE_LA_TARJETA),
            )
        } else {
            Fila(
                titulo = tituloDelExtracto(extracto),
                subtitulo = detalleDelExtracto(extracto),
                lineasDelSubtitulo = 2,
                inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
                final = { MontoDeFila(montoDelExtracto(extracto.facturado, extracto.moneda)) },
                modifier = Modifier.testTag(TAG_EXTRACTO_DE_LA_TARJETA),
            )
        }
        val yaPagado = if (pagado) null else loYaPagadoDelExtracto(extracto)
        val despues = lineaDespuesDelCorte(extracto)
        val diferidos = extracto.puedeIncluirDiferidos && !pagado
        if (yaPagado != null || despues != null || diferidos) {
            BajoLaFila {
                yaPagado?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) }
                despues?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) }
                if (diferidos) {
                    Text(PUEDE_INCLUIR_DIFERIDOS, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
            }
        }
    } else {
        Fila(
            titulo = if (t.pagoMinimo != null) "Pago mínimo" else "Pago",
            subtitulo = t.cutoffDay?.let { "El $pago · corte el día $it" } ?: "El $pago",
            lineasDelSubtitulo = 2,
            inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
            final = t.pagoMinimo?.let { minimo -> { MontoDeFila(formatMoney(minimo, moneda)) } },
        )
    }
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

/** ¿A esta deuda le faltan sus términos? La misma condición que «Faltan datos» en la lista. */
fun faltanLosDatos(deuda: DeudaDelDetalle): Boolean =
    (deuda.prestamo != null && deuda.prestamo.terms == null) || (deuda.tarjeta != null && deuda.tarjeta.terms == null)

/**
 * **Faltan datos, arriba del detalle** (vuelta 31). Antes, tocar en Créditos una deuda sin términos
 * («Nu», «Vehículo 4083») abría directo el formulario: era la única deuda que no se podía consultar.
 * Ahora siempre se abre el detalle, y si faltan datos lo dice primero, como una propuesta de Movi en
 * el tono de aviso, con «Completar datos» que abre el mismo formulario de siempre. Nada si no faltan.
 */
@Composable
fun AvisoDeFaltanDatos(deuda: DeudaDelDetalle, modifier: Modifier = Modifier) {
    if (!faltanLosDatos(deuda)) return
    val esTarjeta = deuda.tarjeta != null
    Box(modifier = modifier.fillMaxWidth().testTag(TAG_FALTAN_DATOS_DE_LA_DEUDA)) {
        TarjetaDePropuesta(
            icono = Icons.Rounded.Edit,
            titulo = if (esTarjeta) "Faltan los datos de esta tarjeta" else "Faltan los datos de este préstamo",
            cuerpo = if (esTarjeta) {
                "Con el corte y el día de pago, Movi te dice cuándo vence y cuánto tienes disponible."
            } else {
                "Con la cuota, la tasa y el plazo, Movi te dice cuánto pagas de intereses y cuándo terminas."
            },
            accion = COMPLETAR_DATOS,
            onAccion = { deuda.hoja = HojaDeLaDeuda.Condiciones },
            tono = TonoDeIcono.Aviso,
            // Vuelta 82: sin condiciones no hay «Pagar la cuota»: «Completar datos» es la única principal.
            varianteDeLaAccion = com.jvillada.movi.ui.components.ds.VarianteDeBoton.Principal,
        )
    }
}

/**
 * **Las hojas de la deuda**, fuera de la lista (una hoja dentro de un ítem perezoso se iría con él):
 * editar las condiciones, ajustar el saldo, simular un abono, y registrar el pago de un tercero o el
 * descuento de nómina —sin hoja: el monto es la cuota y el server lo hace idempotente por mes, así
 * que un doble toque no baja la deuda dos veces—. [onCambiada] relee el detalle; [onError] lo dice.
 */
@Composable
fun HojasDeLaDeuda(
    deuda: DeudaDelDetalle,
    onCambiada: () -> Unit,
    onError: (String) -> Unit,
    /** Vuelta 62 · «Anotar este abono» del simulador: la pantalla de Agregar que abre. `null` = sin el botón. */
    onAnotarAbono: ((com.jvillada.movi.ui.Screen) -> Unit)? = null,
) {
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
        HojaDeLaDeuda.Saldo -> when {
            prestamo != null -> CreditBalanceSheet(
                credit = prestamo,
                onDismiss = { deuda.hoja = null; deuda.cifraPropuesta = null },
                onSaved = { deuda.hoja = null; deuda.cifraPropuesta = null; onCambiada() },
                cifraPropuesta = deuda.cifraPropuesta,
            )
            tarjeta != null -> CardBalanceSheet(
                card = tarjeta,
                onDismiss = { deuda.hoja = null },
                onSaved = { deuda.hoja = null; onCambiada() },
            )
        }
        HojaDeLaDeuda.Abono -> if (prestamo != null) {
            // Vuelta 59: las fechas del simulador se cuentan desde la primera cuota sin pagar.
            SimuladorDeAbonoSheet(
                credit = prestamo,
                periodoActual = deuda.primeraCuota,
                onDismiss = { deuda.hoja = null },
                onAnotarAbono = onAnotarAbono,
            )
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
