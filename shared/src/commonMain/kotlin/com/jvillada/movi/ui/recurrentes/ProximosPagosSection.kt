package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import kotlinx.datetime.LocalDate

/**
 * # «Próximos pagos» y «¿esto ya ocurrió?», en una pieza que no pertenece a ninguna pantalla
 *
 * PR 3 del rediseño de Recurrentes (2026-09). Lo de acá adentro vivía dentro de
 * `RecurrentesScreen.kt`, `private`, y ahora lo usan **dos** pantallas: la vieja (que sigue viva
 * hasta que el PR 4 la borre) y Movimientos bajo el chip «Recurrentes», que es donde el dueño lo
 * va a ver de ahora en adelante.
 *
 * **Se mudó, no se copió.** Copiar y pegar esto era la salida fácil y ya se sabe cómo termina: la
 * decisión de si una tarjeta muestra «saldo» o «cuota» faltaba en uno de los cuatro renderers de
 * un monto, y por eso `textoDelMonto` existe. Acá el riesgo es peor —hay tres botones que sellan
 * un periodo con la plata del dueño adentro— así que la lógica vive en UN archivo y las dos
 * pantallas la llaman.
 */

// ── El renglón de un vencimiento ─────────────────────────────────────────────────

private fun dueDateDay(dueDate: String): Int =
    runCatching { LocalDate.parse(dueDate).dayOfMonth }.getOrElse {
        dueDate.takeLast(2).toIntOrNull()
            ?: dueDate.substringAfterLast('-').toIntOrNull()
            ?: 0
    }

private fun statusColor(status: PaymentStatus): Color = when (status) {
    PaymentStatus.OVERDUE   -> MinExpense
    PaymentStatus.DUE_TODAY -> MinWarn
    PaymentStatus.DUE_SOON  -> MinWarn
    PaymentStatus.UPCOMING  -> MinTextMute
}

/**
 * El estado de un vencimiento, **con el mes cuando nombra un día**.
 *
 * Decía «Vence el 1 · en 5 días» a secas, y ese renglón puede hablar del mes que viene: la ventana
 * de gracia rueda el vencimiento de una regla de día bajo a fin de mes. Justo debajo puede quedar
 * la tarjeta «¿Ya pagaste el de agosto?», que sí nombra su mes — y entonces el único mes escrito
 * en pantalla era el de la tarjeta mientras la fila de arriba hablaba de otro. Nombrar los dos es
 * la mitad que le faltaba al arreglo de textos.
 *
 * «Vencido hace N días» y «Vence hoy» no llevan mes: no nombran ningún día, así que no hay nada
 * que confundir.
 */
internal fun statusText(payment: UpcomingPayment): String {
    val n = payment.daysUntil
    val day = dueDateDay(payment.dueDate)
    val mes = nombreDelMes(payment.dueDate)
    val cuando = if (mes.isEmpty()) "Vence el $day" else "Vence el $day de $mes"
    return when (payment.status) {
        PaymentStatus.OVERDUE   -> "Vencido hace ${-n} ${if (-n == 1) "día" else "días"}"
        PaymentStatus.DUE_TODAY -> "Vence hoy"
        PaymentStatus.DUE_SOON  -> "$cuando · en $n ${if (n == 1) "día" else "días"}"
        PaymentStatus.UPCOMING  -> "$cuando · en $n ${if (n == 1) "día" else "días"}"
    }
}

@Composable
internal fun UpcomingPaymentRow(payment: UpcomingPayment, onClick: () -> Unit) {
    val rule = payment.rule
    val isIncome = rule.type == TransactionType.INCOME
    val color = statusColor(payment.status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = statusText(payment), fontSize = 11.sp, color = color)
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MinTextFaint),
                )
                Text(rule.category, fontSize = 11.sp, color = MinTextMute)
            }
        }

        Text(
            text = textoDelMonto(rule, conSigno = true),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isIncome) MinIncome else MinText,
            letterSpacing = (-0.3).sp,
        )
    }
}

/**
 * **«Parece que esto ya ocurrió»** — la propuesta, debajo del renglón que lo dio por vencido.
 *
 * Es un OFRECIMIENTO, no una pregunta que haya que resolver: se puede ignorar y la pantalla sigue
 * funcionando igual. Nada se marca solo. La app **propone** y el dueño **confirma**, porque la
 * asimetría del riesgo manda: dar por ocurrido algo que no ocurrió apaga el aviso de una deuda
 * real, y eso cuesta plata; el ruido de hoy cuesta un toque.
 *
 * Tres salidas, en orden de certeza:
 *
 *  1. **«Sí, fue este»** — el emparejamiento exacto. El periodo queda cerrado *y anclado* a un
 *     movimiento que se puede mirar.
 *  2. **«No fue este»** — pasa a la propuesta siguiente. Sin esto, una propuesta equivocada
 *     tapaba a la buena y el único camino era ignorarlas todas.
 *  3. **«Ya lo pagué» / «Ya me llegó»** — cierra el periodo sin movimiento que emparejar (pagó en
 *     efectivo, todavía no lo anotó, lo anotó en otra cuenta). Está siempre, también cuando no
 *     hay ninguna propuesta: es la salida que hace que la función sirva aunque el emparejamiento
 *     no encuentre nada.
 *
 * **El monto se muestra aunque no coincida, y se dice que no coincide.** El monto de un recurrente
 * es un estimado —«otros meses puede ser menos o más dependiendo de retenciones»—, así que no
 * filtra candidatos; pero por eso mismo confirmar a ciegas podría sellar el mes con otra cosa. La
 * diferencia se pinta: es lo que convierte el «sí» en una decisión.
 */
@Composable
internal fun PropuestaOcurrencia(
    estado: OccurrenceState,
    rule: RecurringRule,
    propuesta: FinancialEvent?,
    enVuelo: Boolean,
    onConfirmar: (FinancialEvent) -> Unit,
    onDescartar: (FinancialEvent) -> Unit,
    onCerrarSinMovimiento: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 14.dp)) {
        Text(
            text = tituloPropuesta(rule.type, estado.period),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MinText,
        )
        if (propuesta != null) {
            Spacer(Modifier.height(4.dp))
            // Alineado arriba y con aire entre las dos columnas: en un teléfono angosto (390 px)
            // la descripción se envuelve en dos líneas, y con `CenterVertically` y sin separación
            // el monto quedaba pegado al texto — dos datos distintos leyéndose como uno.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = descripcionPropuesta(propuesta),
                    fontSize = 12.sp,
                    color = MinTextMute,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatMoney(propuesta.amount, propuesta.currency),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MinText,
                    lineHeight = 16.sp,
                )
            }
            // `avisaMontoDistinto` y no `difiereDelEsperado` a secas: en una tarjeta el monto de
            // la regla es el SALDO, no un pago esperado, así que la comparación daba `true` todos
            // los meses y esta advertencia salía siempre — repitiéndole al dueño como «lo que
            // anotaste» justamente la cifra que el resto de la pantalla dejó de mostrar como su
            // pago. Ver `RecurringRule.montoEsSaldo`.
            if (avisaMontoDistinto(rule, propuesta.amount)) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "No es el monto que anotaste (${formatCOP(rule.amount)}). " +
                        "Puede ser: revísalo antes de confirmar.",
                    fontSize = 11.sp,
                    color = MinTextMute,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (propuesta != null) {
                ActionChip(label = if (enVuelo) "Guardando…" else "Sí, fue este", primary = true) {
                    if (!enVuelo) onConfirmar(propuesta)
                }
                ActionChip(label = "No fue este", primary = false) {
                    if (!enVuelo) onDescartar(propuesta)
                }
            } else {
                ActionChip(
                    label = if (enVuelo) "Guardando…" else etiquetaCierreManual(rule.type),
                    primary = true,
                ) { if (!enVuelo) onCerrarSinMovimiento() }
            }
        }
        if (propuesta != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = etiquetaCierreManual(rule.type) + ", sin emparejar ningún movimiento",
                fontSize = 11.sp,
                color = MinPrimary,
                modifier = Modifier.clickable { if (!enVuelo) onCerrarSinMovimiento() },
            )
        }
    }
}

/** Los botones chicos de esta familia — «Sí, fue este», «Confirmar», «No es». */
@Composable
internal fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
    }
}

// ── Las dos secciones enteras ────────────────────────────────────────────────────

/**
 * **«Próximos»**: lo que urge, y debajo de cada cosa la pregunta de si ya ocurrió.
 *
 * La sección completa —encabezado, tarjeta, filas, propuestas y separadores— porque es la unidad
 * que las dos pantallas necesitan igual. Si solo se compartieran las filas, el orden, el estado
 * vacío y la regla de «con `ocurrenciasOk` en false no se pregunta nada» quedarían escritos dos
 * veces, que es exactamente lo que este archivo existe para evitar.
 *
 * @param proximos ya filtrado por [proximosQueUrgen] — esta función no decide qué urge.
 * @param ocurrenciasOk `false` mientras `GET /api/payments/occurrences` no haya contestado. Con la
 *   fuente a medias no se pregunta nada: una propuesta incompleta —o peor, un «ya ocurrió» que en
 *   realidad no se pudo leer— sería una afirmación sin respaldo.
 * @param conteoVisible si el número del encabezado se puede afirmar (la lista de vencimientos ya
 *   llegó alguna vez). Mismo criterio que el resto: sin el dato no se dice un número.
 */
@Composable
fun SeccionProximosPagos(
    proximos: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    ocurrenciasOk: Boolean,
    descartadas: Set<String>,
    marcando: Set<String>,
    cargando: Boolean,
    conteoVisible: Boolean,
    onAbrirPago: (UpcomingPayment) -> Unit,
    onMarcar: (ruleId: String, period: String, eventId: String?) -> Unit,
    onDescartarPropuesta: (ruleId: String, eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MinSectionHeader(
            title = "Próximos",
            count = if (conteoVisible && proximos.isNotEmpty()) proximos.size else null,
        )
        // Cargando y sin nada todavía: no se pinta NADA. La rama de abajo con la lista vacía
        // dibujaba un MinCard sin filas — una astilla de 4dp bajo el rótulo, que junto con la
        // otra sección hacía ver la pantalla rota.
        if (proximos.isEmpty() && cargando) {
            Unit
        } else if (proximos.isEmpty()) {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text("Nada vence en los próximos días", fontSize = 14.sp, color = MinTextMute)
            }
        } else {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
            ) {
                proximos.forEachIndexed { i, payment ->
                    UpcomingPaymentRow(payment = payment, onClick = { onAbrirPago(payment) })
                    val estado = if (ocurrenciasOk) ocurrenciaDe(ocurrencias, payment.rule.id) else null
                    if (hayQuePreguntar(estado)) {
                        PropuestaOcurrencia(
                            estado = estado!!,
                            rule = payment.rule,
                            propuesta = propuestaActual(estado, descartadas),
                            enVuelo = payment.rule.id in marcando,
                            onConfirmar = { ev -> onMarcar(payment.rule.id, estado.period, ev.id) },
                            onDescartar = { ev -> onDescartarPropuesta(payment.rule.id, ev.id) },
                            onCerrarSinMovimiento = { onMarcar(payment.rule.id, estado.period, null) },
                        )
                    }
                    if (i < proximos.size - 1) Hairline()
                }
            }
        }
    }
}

/**
 * **«Ya ocurrieron»**: los periodos que el dueño selló, con su «Deshacer».
 *
 * Existe porque sellar es una acción con plata adentro y **tiene que poder revertirse sin
 * ceremonia**. Apenas se sella, el recurrente desaparece de «Próximos» —su vencimiento vigente ya
 * es el del mes que viene— así que si el «Deshacer» viviera solo ahí, marcar por error sería un
 * error sin vuelta atrás hasta el mes siguiente. En la pantalla vieja el «Deshacer» vivía en el
 * inventario «Por día del mes», que no se mudó a Movimientos (esa lista ahora la hacen el filtro
 * del chip y el resumen de flujo libre): esta sección es su reemplazo, y solo aparece cuando de
 * verdad hay algo sellado.
 *
 * Cada fila dice el mes y si quedó respaldada por un movimiento — ver [textoYaOcurrio]: un sello a
 * mano es una palabra suya, uno con movimiento está anclado a una plata que se puede ver.
 */
@Composable
fun SeccionYaOcurrieron(
    selladas: List<Pair<RecurringRule, OccurrenceState>>,
    marcando: Set<String>,
    onDeshacer: (ruleId: String, period: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selladas.isEmpty()) return
    Column(modifier = modifier) {
        MinSectionHeader(title = "Ya ocurrieron", count = selladas.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            selladas.forEachIndexed { i, (rule, estado) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                            letterSpacing = (-0.1).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(textoYaOcurrio(estado), fontSize = 11.sp, color = MinTextMute)
                    }
                    Text(
                        text = if (rule.id in marcando) "Guardando…" else "Deshacer",
                        fontSize = 12.sp,
                        color = MinPrimary,
                        modifier = Modifier.clickable {
                            if (rule.id !in marcando) onDeshacer(rule.id, estado.period)
                        },
                    )
                }
                if (i < selladas.size - 1) Hairline()
            }
        }
    }
}
