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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
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
 * `RecurrentesScreen.kt`, `private`. Mientras esa pantalla existió lo usaron las dos; el PR 4 la
 * borró, así que hoy el único llamador es Movimientos bajo el chip «Recurrentes», que es donde
 * el dueño lo ve. Se queda en su propio archivo igual: es una pieza con lógica de plata adentro,
 * no el interior de una pantalla.
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

@Composable
private fun statusColor(status: PaymentStatus): Color = when (status) {
    PaymentStatus.OVERDUE   -> Movi.colores.sale
    PaymentStatus.DUE_TODAY -> Movi.colores.aviso
    PaymentStatus.DUE_SOON  -> Movi.colores.aviso
    PaymentStatus.UPCOMING  -> Movi.colores.textoMedio
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
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            // **Un solo texto, no dos en una fila.** Antes eran dos `Text` sin reparto de ancho: el
            // del vencimiento («Vence el 16 de septiembre · en 1 día») se llevaba todo lo que
            // quería y a la categoría le quedaba un hueco de una letra, así que «Educación» se
            // dibujaba una letra por renglón y la fila crecía media pantalla. Visto en la web a
            // 390 dp con un nombre largo. Juntos, parten renglón donde corresponde.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = color)) { append(statusText(payment)) }
                    append("  ·  ")
                    append(rule.category)
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }

        Text(
            text = textoDelMonto(rule, conSigno = true),
            style = Movi.textos.monto,
            fontWeight = FontWeight.Medium,
            color = if (isIncome) Movi.colores.entra else Movi.colores.texto,
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
 * Tres salidas, y ninguna sella nada sin evidencia:
 *
 *  1. **«Sí, fue este»** — el emparejamiento exacto. El periodo queda cerrado *y anclado* a un
 *     movimiento que se puede mirar.
 *  2. **«No fue este»** — pasa a la propuesta siguiente, y el «no» se guarda (ver
 *     `rechazarOcurrencia`). Sin esto, una propuesta equivocada tapaba a la buena y el único
 *     camino era ignorarlas todas.
 *  3. **«Anotar el movimiento»**, cuando no hay ninguna propuesta: abre la hoja de Agregar con los
 *     datos del recurrente puestos. Pagó en efectivo, desde una cuenta que Movi no lleva, o el
 *     banco nunca avisó — y la salida es que ese movimiento EXISTA, no que se tilde una casilla.
 *
 * ## Lo que se fue de acá, y por qué
 *
 * Hasta hoy la tercera salida era **«Ya lo pagué» / «Ya me llegó»**: sellaba el periodo con
 * `eventId = null`, o sea sin ninguna evidencia. El dueño pidió cerrar esa puerta —*«no me debería
 * dejar hacer check sin que el movimiento asociado exista»*— y no alcanzaba con sacarla del
 * checklist: mientras viviera acá, el agujero seguía abierto un toque más allá. Se fue de las dos.
 * El server sigue aceptando `eventId = null` porque hay sellos viejos hechos así y romperlos sería
 * peor, pero la app ya no lo manda desde ningún lado.
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
    onAnotarMovimiento: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 14.dp)) {
        Text(
            text = tituloPropuesta(rule.type, estado.periodoDelDueno ?: estado.period),
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.texto,
        )
        if (propuesta != null) {
            Spacer(Modifier.height(4.dp))
            PropuestaDeMovimiento(
                propuesta = propuesta,
                aviso = avisoDeMontoDistinto(rule, propuesta),
                enVuelo = enVuelo,
                onConfirmar = { onConfirmar(propuesta) },
                onDescartar = { onDescartar(propuesta) },
            )
        } else {
            Spacer(Modifier.height(10.dp))
            AccionesDeLaFila(
                acciones = listOf(AccionDeOcurrencia(ETIQUETA_ANOTAR, primary = true, onClick = onAnotarMovimiento)),
                enVuelo = enVuelo,
            )
        }
    }
}

/**
 * **Una propuesta dibujada: qué fue, cuánto, el aviso si el monto no cuadra, y las dos salidas.**
 *
 * Es LA pieza que dibuja una propuesta, y es una sola a propósito. Nació al llevar la pregunta al
 * checklist del período: la alternativa era una segunda copia del mismo bloque, y este archivo ya
 * documenta —en su encabezado— cómo termina eso («la decisión de si una tarjeta muestra saldo o
 * cuota faltaba en uno de los cuatro renderers de un monto»). Acá el riesgo es peor, porque los
 * botones sellan un periodo con la plata del dueño adentro.
 *
 * @param aviso el texto de «no es el monto que anotaste», ya resuelto por [avisoDeMontoDistinto], o
 *   `null` si no hay nada que advertir. Lo decide quien llama porque depende de la REGLA (en una
 *   tarjeta el monto es el saldo y la comparación no significa nada) y acá solo llega el
 *   movimiento.
 */
@Composable
internal fun PropuestaDeMovimiento(
    propuesta: FinancialEvent,
    aviso: String?,
    enVuelo: Boolean,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
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
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(propuesta.amount, propuesta.currency),
            style = Movi.textos.monto,
            color = Movi.colores.texto,
            lineHeight = 16.sp,
        )
    }
    if (aviso != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = aviso,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 15.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    AccionesDeLaFila(
        acciones = listOf(
            AccionDeOcurrencia(ETIQUETA_SI_FUE_ESTE, primary = true, onClick = onConfirmar),
            AccionDeOcurrencia(ETIQUETA_NO_FUE_ESTE, primary = false, onClick = onDescartar),
        ),
        enVuelo = enVuelo,
    )
}

/** Los rótulos que la pantalla y sus pruebas tienen que nombrar igual. */
const val ETIQUETA_SI_FUE_ESTE = "Sí, fue este"
const val ETIQUETA_NO_FUE_ESTE = "No fue este"
const val ETIQUETA_ANOTAR = "Anotar el movimiento"
const val ETIQUETA_QUITAR_LA_MARCA = "Quitar la marca"

/** Una acción ofrecida sobre una ocurrencia: qué dice, si es la principal, y qué hace. */
internal data class AccionDeOcurrencia(
    val label: String,
    val primary: Boolean,
    val onClick: () -> Unit,
)

/**
 * La fila de botones de una ocurrencia, con **una sola** regla de «en vuelo» para todos.
 *
 * Mientras una escritura viaja, el botón principal dice «Guardando…» y ninguno acepta un toque. Se
 * dice una vez acá y no en cada llamador porque los tres lugares que ofrecen estas acciones
 * —«Próximos», «Sin confirmar» y el checklist— comparten el mismo `marcando` por regla: si uno de
 * ellos se olvidara de mirarlo, un doble toque mandaría dos veces el mismo sello.
 */
@Composable
internal fun AccionesDeLaFila(acciones: List<AccionDeOcurrencia>, enVuelo: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        acciones.forEachIndexed { i, accion ->
            val etiqueta = if (enVuelo && i == 0) "Guardando…" else accion.label
            ActionChip(label = etiqueta, primary = accion.primary) {
                if (!enVuelo) accion.onClick()
            }
        }
    }
}

/** Los botones chicos de esta familia — «Sí, fue este», «Confirmar», «No es». */
@Composable
internal fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) Movi.colores.texto else Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = if (primary) Movi.colores.fondo else Movi.colores.texto)
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
    onMarcar: (ruleId: String, period: String, eventId: String) -> Unit,
    onDescartarPropuesta: (ruleId: String, eventId: String) -> Unit,
    onAnotarMovimiento: (UpcomingPayment) -> Unit,
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
                Text("Nada vence en los próximos días", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
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
                            onAnotarMovimiento = { onAnotarMovimiento(payment) },
                        )
                    }
                    if (i < proximos.size - 1) Hairline()
                }
            }
        }
    }
}

/**
 * **«Sin confirmar»**: periodos abiertos que ya dejaron de urgir, con su misma pregunta.
 *
 * Es la puerta que faltaba. «Próximos» solo muestra lo que urge, y una regla deja de urgir apenas
 * pasan los días de gracia **aunque nadie haya confirmado nada** (ver
 * [ocurrenciasAbiertasSinUrgencia], que explica el mecanismo). Sin esta sección, el gimnasio del
 * día 5 no se podía confirmar desde el día ~10 hasta fin de mes: no urgía, no estaba sellado, y
 * el inventario donde antes vivía esa propuesta no se mudó a Movimientos.
 *
 * Va DEBAJO de «Próximos» y no mezclada con él a propósito: lo que vence pronto y lo que se pasó
 * de fecha piden cosas distintas —uno es un aviso, el otro es una cuenta pendiente de cerrar— y
 * juntarlos escondería el urgente entre los viejos.
 */
@Composable
fun SeccionSinConfirmar(
    abiertas: List<Pair<RecurringRule, OccurrenceState>>,
    descartadas: Set<String>,
    marcando: Set<String>,
    onMarcar: (ruleId: String, period: String, eventId: String) -> Unit,
    onDescartarPropuesta: (ruleId: String, eventId: String) -> Unit,
    onAnotarMovimiento: (RecurringRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (abiertas.isEmpty()) return
    Column(modifier = modifier) {
        MinSectionHeader(title = "Sin confirmar", count = abiertas.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            abiertas.forEachIndexed { i, (rule, estado) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.name,
                            style = Movi.textos.cuerpo,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                            letterSpacing = (-0.1).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Día ${rule.dayOfMonth} · sin confirmar",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                        )
                    }
                    Text(
                        text = textoDelMonto(rule, conSigno = true),
                        style = Movi.textos.monto,
                        fontWeight = FontWeight.Medium,
                        color = if (rule.type == TransactionType.INCOME) Movi.colores.entra else Movi.colores.texto,
                        letterSpacing = (-0.3).sp,
                    )
                }
                PropuestaOcurrencia(
                    estado = estado,
                    rule = rule,
                    propuesta = propuestaActual(estado, descartadas),
                    enVuelo = rule.id in marcando,
                    onConfirmar = { ev -> onMarcar(rule.id, estado.period, ev.id) },
                    onDescartar = { ev -> onDescartarPropuesta(rule.id, ev.id) },
                    onAnotarMovimiento = { onAnotarMovimiento(rule) },
                )
                if (i < abiertas.size - 1) Hairline()
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
 *
 * **No todas las filas se sellaron.** La cuota de un crédito y el pago de una tarjeta llegan acá
 * *derivadas* del movimiento que bajó la deuda: nadie las marcó y no hay nada que desmarcar. Esas
 * se pintan sin «Deshacer» (ver [sePuedeDeshacer]) y diciendo cómo se revierten de verdad. La
 * sección sigue siendo la misma —«qué se dio por ocurrido este mes»— con dos orígenes distintos
 * que la fila no esconde.
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
                            style = Movi.textos.cuerpo,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                            letterSpacing = (-0.1).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(textoYaOcurrio(estado), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    }
                    // El «Deshacer» solo existe donde hay un sello que borrar. Una cuota o un pago
                    // de tarjeta llega acá **derivado del movimiento** que bajó la deuda, y eso no
                    // se desmarca: el DELETE contestaría 404 y la fila se quedaría igual. Se
                    // muestra en su lugar cómo se revierte de verdad — borrando el movimiento —
                    // porque un control muerto es peor que no tener control (ver [sePuedeDeshacer]).
                    if (sePuedeDeshacer(estado)) {
                        Text(
                            text = if (rule.id in marcando) "Guardando…" else "Deshacer",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.marca,
                            modifier = Modifier.clickable {
                                if (rule.id !in marcando) onDeshacer(rule.id, estado.period)
                            },
                        )
                    } else {
                        Text(
                            text = "Se quita borrando\nel movimiento",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                if (i < selladas.size - 1) Hairline()
            }
        }
    }
}
