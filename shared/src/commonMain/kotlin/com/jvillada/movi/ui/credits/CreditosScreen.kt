package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jvillada.movi.ui.LocalRefreshTick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * F20 — Créditos es «todo lo que debes»: préstamos (cuota, tasa, plazo) y tarjetas de crédito
 * (cupo, corte, día de pago), con **un solo total de deuda** arriba que suma ambos — la misma
 * suma que muestra el acceso «Créditos» del Inicio (ver [totalDebtCop]).
 */
@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    var credits by remember { mutableStateOf<List<CreditSummary>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CardSummary>>(emptyList()) }
    var showTypeChooser by remember { mutableStateOf(false) }
    var showLoanSheet by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<CreditSummary?>(null) }
    var showCardSheet by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<CardSummary?>(null) }
    var adjusting by remember { mutableStateOf<CreditSummary?>(null) }
    // El crédito cuyo descuento de nómina se está registrando. No abre hoja: es un solo dato
    // (la cuota, que ya está en los términos) y el server lo hace idempotente por mes, así que
    // pedir confirmación sería ceremonia sobre algo que no se puede duplicar.
    var descontando by remember { mutableStateOf<CreditSummary?>(null) }
    var errorDescuento by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los créditos reales.
    var loading by remember { mutableStateOf(true) }
    // Ola 14 — Créditos también escucha el «se guardó algo» de la hoja de Agregar.
    //
    // Hasta esta rama no hacía falta: nada de lo que se podía guardar desde Agregar movía la
    // deuda de un crédito, así que `reloadKey` (los cambios hechos EN esta pantalla) alcanzaba.
    // Ahora un desembolso o un abono extraordinario sí la mueven, y sin este tick la pantalla
    // se quedaba mostrando la deuda vieja —verificado en el navegador: se guardó el abono de
    // $5.000.000 y Créditos siguió diciendo $70.000.000 hasta salir y volver a entrar—. Mismo
    // mecanismo que ya usaba Movimientos, y por el mismo motivo: la hoja es una modal y esta
    // pantalla nunca sale de la composición.
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(reloadKey, refreshTick) {
        loading = true
        val loans = launch { runCatching { Repositories.wallets.getCredits() }.onSuccess { credits = it } }
        val tarjetas = launch { runCatching { Repositories.wallets.getCards() }.onSuccess { cards = it } }
        loans.join()
        tarjetas.join()
        loading = false
    }
    val isEmpty = credits.isEmpty() && cards.isEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(MinBg)
        ) {
            // F60: encabezado único — avatar en ancho (Créditos está en el rail), flecha a Más
            // en el teléfono (se llega por Más; F22: reserva si no hay historial). Con deudas ya
            // creadas, el alta compacta a la derecha (F18).
            MinScreenHeader(
                title = "Créditos",
                leading = leadingFor(Screen.Credits, onProfile = { onNavigate(Screen.Profile) }, fallback = Screen.Mas),
                action = if (!isEmpty) {
                    { NewItemButton(label = "Nuevo crédito", onClick = { showTypeChooser = true }) }
                } else null,
            )
            if (isEmpty && !loading) {
                NewItemButton(
                    label = "Nuevo crédito",
                    onClick = { showTypeChooser = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
                )
            } else {
                Spacer(Modifier.height(14.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Deuda total", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        // F20: préstamos + tarjetas — la MISMA función que usa el Inicio.
                        Text(formatCOP(totalDebtCop(credits, cards)), fontSize = 36.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-1.4).sp, lineHeight = 36.sp)
                    }
                }

                if (isEmpty) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Mis créditos")
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text(
                                    "Sin créditos registrados",
                                    fontSize = 14.sp, color = MinTextMute,
                                )
                            }
                        }
                    }
                }

                if (credits.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Préstamos", count = credits.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                credits.forEach { c ->
                                    LoanCard(
                                        credit = c,
                                        onOpen = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                                        onEdit = { editingLoan = c; showLoanSheet = true },
                                        onAdjust = { adjusting = c },
                                        onPayrollDeduction = { descontando = c },
                                    )
                                }
                            }
                        }
                    }
                }

                if (cards.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Tarjetas", count = cards.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                cards.forEach { c ->
                                    CreditCardCard(
                                        card = c,
                                        onOpen = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                                        onEdit = { editingCard = c; showCardSheet = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showTypeChooser) {
            DebtTypeChooserSheet(
                onDismiss = { showTypeChooser = false },
                onLoan = { showTypeChooser = false; editingLoan = null; showLoanSheet = true },
                onCard = { showTypeChooser = false; editingCard = null; showCardSheet = true },
            )
        }
        if (showLoanSheet) {
            CreditTermsSheet(
                editing = editingLoan,
                candidates = credits.filter { it.terms == null }.map { it.account },
                onDismiss = { showLoanSheet = false },
                onSaved = { showLoanSheet = false; reloadKey++ },
            )
        }
        if (showCardSheet) {
            CardTermsSheet(
                editing = editingCard,
                onDismiss = { showCardSheet = false },
                onSaved = { showCardSheet = false; reloadKey++ },
            )
        }
        adjusting?.let { credit ->
            CreditBalanceSheet(
                credit = credit,
                onDismiss = { adjusting = null },
                onSaved = { adjusting = null; reloadKey++ },
            )
        }
        // El descuento de nómina se registra sin hoja: no hay nada que preguntar —el monto es la
        // cuota— y el server lo hace idempotente por mes, así que un doble toque no baja la deuda
        // dos veces.
        descontando?.let { credit ->
            LaunchedEffect(credit.account.id) {
                runCatching { Repositories.wallets.registerPayrollDeduction(credit.account.id) }
                    .onSuccess { descontando = null; reloadKey++ }
                    .onFailure { descontando = null; errorDescuento = it.toUserMessage() }
            }
        }
    }
}

/**
 * Tarjeta de un préstamo: cuota, tasa, plazo y progreso — lo que Créditos mostraba desde siempre.
 * Ola 7 (F61): como las deudas ya no se listan en Cuentas, tocar la tarjeta abre el historial
 * de la cuenta ([onOpen] → AccountDetail); editar términos pasa a ser el lápiz de la derecha.
 */
@Composable
private fun LoanCard(
    credit: CreditSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onPayrollDeduction: () -> Unit,
) {
    // Ola 14: no siempre es un porcentaje. Un crédito recién creado en $0 —el paso 1 de registrar
    // un desembolso— decía «100% pagado» con la barra llena. Ver [progresoDeCredito].
    val progreso = progresoDeCredito(credit)
    val pct = progreso.fraccion
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(credit.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
            Text(credit.terms?.let { "${it.rateEa}% EA" } ?: "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
            EditTermsIcon(onEdit)
        }
        Text(credit.terms?.bank ?: "Sin términos registrados", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatCOP(credit.account.balance), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
            // Sin monoespaciada cuando no es una cifra (ver [ProgresoDeCredito.esAviso]).
            Text(
                progreso.etiqueta,
                fontSize = 12.sp,
                fontFamily = if (progreso.esAviso) FontFamily.Default else FontFamily.Monospace,
                color = MinTextMute,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MinHairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MinText.copy(alpha = 0.9f))
            )
        }
        credit.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Quién paga va PEGADO a la cuota, no en una fila aparte: es lo que decide si
                // ese número sale del bolsillo del dueño, y leerlo suelto —«$9.147.408» a secas—
                // es exactamente el malentendido que esta feature vino a evitar. Sin esto, la
                // pantalla de Créditos sumaría $13,1 millones al mes de cuotas que él no paga.
                Text(
                    text = when {
                        t.payrollDeduction -> "Cuota · día ${t.dayOfMonth} · de tu nómina"
                        !t.paidBy.isNullOrBlank() -> "Cuota · día ${t.dayOfMonth} · la paga ${t.paidBy}"
                        else -> "Cuota · día ${t.dayOfMonth}"
                    },
                    fontSize = 12.sp,
                    color = MinTextMute,
                )
                Text(formatCOP(t.installment), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText)
            }
            // Plazo y fecha de desembolso: son los dos datos que uno compara contra el extracto,
            // y estaban solo dentro de la hoja de edición.
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${t.termMonths} meses · desde ${t.startDate}", fontSize = 11.5.sp, color = MinTextFaint)
                Text(t.bank, fontSize = 11.5.sp, color = MinTextFaint)
            }
            // La NOTA, que es donde vive lo que falta confirmar con el banco («plazo estimado»,
            // «la deuda subió», «preguntar el capital original»). Estaba escondida detrás del
            // lápiz: había que entrar a cada crédito para recordar qué tenía pendiente, que es
            // justo lo contrario de para qué sirve una nota.
            t.notes?.takeIf { it.isNotBlank() }?.let { nota ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = nota,
                    fontSize = 11.5.sp,
                    color = MinTextMute,
                    lineHeight = 16.sp,
                )
            }
        }
        // La deuda es estado (se mueve a diario por intereses), no
        // contrato: por eso cuadrarla con el banco vive fuera de la
        // hoja de términos.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // Una libranza no se «paga»: ya se descontó del sueldo. Lo único que falta es que la
            // deuda lo refleje, y eso es un toque — no anotar un gasto que no existió.
            // Y una cuota que paga OTRO tampoco se «paga» desde acá: la giró Skandia, o la pagó
            // Caro. Lo único que falta es que la deuda lo refleje. Mismo endpoint que la
            // libranza (ver POST /credits/{id}/payroll-deduction), distinto rótulo — el rótulo
            // es lo que le dice al dueño qué está confirmando.
            val quienPaga = credit.terms?.paidBy?.takeIf { it.isNotBlank() }
            if (quienPaga != null && credit.terms?.payrollDeduction != true) {
                Text(
                    "Registrar pago de $quienPaga",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableSimple(onPayrollDeduction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            if (credit.terms?.payrollDeduction == true) {
                Text(
                    "Registrar descuento",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableSimple(onPayrollDeduction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "Ajustar saldo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinTextMute,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickableSimple(onAdjust)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Tarjeta de una tarjeta de crédito (F20): deuda actual, cupo disponible si hay, corte y día de
 * pago. La deuda se muestra en la moneda de la tarjeta ([formatMoney]) — una Mastercard en USD
 * debe dólares, y su componente COP sería $0, un número que miente.
 */
@Composable
private fun CreditCardCard(card: CardSummary, onOpen: () -> Unit, onEdit: () -> Unit) {
    val currency = card.account.currency
    val debt = card.account.balancesByCurrency[currency] ?: card.account.balance
    // Ola 7 (F61): tocar la tarjeta abre su historial (AccountDetail); el lápiz edita corte y pago.
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(card.account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
            if (currency != "COP") {
                Text(currency, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
            }
            EditTermsIcon(onEdit)
        }
        Text(card.terms?.bank ?: "Sin corte ni pago — edítalos con el lápiz", fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatMoney(debt, currency), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
            card.available?.let {
                Text("Disponible ${formatMoney(it, currency)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
            }
        }
        card.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(t.cutoffDay?.let { "Corte · día $it" } ?: "Sin día de corte", fontSize = 12.sp, color = MinTextMute)
                Text("Pago · día ${t.paymentDay}", fontSize = 12.sp, color = MinTextMute)
            }
        }
    }
}

/**
 * Selector previo al alta (F20): un préstamo y una tarjeta no se crean igual — el préstamo
 * tiene capital, tasa y plazo; la tarjeta, cupo, corte y día de pago. Preguntar primero evita
 * una hoja única llena de campos que no aplican.
 */
@Composable
private fun DebtTypeChooserSheet(
    onDismiss: () -> Unit,
    onLoan: () -> Unit,
    onCard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss)
            // El contenido de la hoja se desplaza.
            //
            // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
            // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
            // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
            // propia hoja. Sin manera de llegar a él.
            //
            // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
            // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
            // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                Text("¿Qué deuda quieres registrar?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(14.dp))
                DebtTypeOption(
                    title = "Préstamo",
                    subtitle = "Cuota fija, tasa y plazo — libranza, libre inversión, vehículo",
                    onClick = onLoan,
                )
                Spacer(Modifier.height(8.dp))
                DebtTypeOption(
                    title = "Tarjeta de crédito",
                    subtitle = "Cupo, día de corte y día de pago",
                    onClick = onCard,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DebtTypeOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = MinTextMute)
    }
}

/** Lápiz de «editar términos» a la derecha de la fila — acción secundaria explícita (Ola 7). */
@Composable
private fun EditTermsIcon(onEdit: () -> Unit) {
    Icon(
        Icons.Rounded.Edit,
        contentDescription = "Editar términos",
        tint = MinTextMute,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickableSimple(onEdit)
            .padding(6.dp)
            .size(16.dp),
    )
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
