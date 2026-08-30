package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.OPENING_BALANCE_EXPLAINER
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.ORPHANED_LEG_EXPLAINER
import com.jvillada.movi.shared.model.TRANSFER_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.ui.fecha.avisoDeCambioDeMes
import com.jvillada.movi.ui.fecha.avisoDeSelloSuelto
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.fecha.timestampParaFecha
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Mismo armazón visual que [com.jvillada.movi.ui.credits.CreditBalanceSheet]: fondo oscuro
 * clickeable para cerrar, panel con esquinas redondeadas arriba y [SheetHandleWithClose] (F37).
 * Se duplica acá en vez de importarse — mismo criterio que [com.jvillada.movi.ui.accounts.CreateAccountSheet],
 * cada pantalla trae sus propios helpers de hoja.
 */
@Composable
private fun BottomSheetScaffold(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = dismissEnabled, onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss, enabled = dismissEnabled)
            content()
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MinTextMute,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun CategoryRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /** Segunda línea opcional: hoy solo la usa «Pago de tarjeta», para decir qué implica elegirla. */
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Ola 2 #5 (F11): el catálogo solo trae un emoji por categoría (Category.icon) — en la
        // web sale como ▯. No hay un mapa a íconos Material por categoría, así que se usa uno
        // genérico y uniforme en vez de intentar mapear 15+ emojis uno a uno.
        Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null, tint = MinTextMute, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.5.sp, color = MinText)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.5.sp, color = MinTextMute, lineHeight = 15.sp)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * ¿Se dibuja el botón «Usar "…"» del campo libre de [ChangeCategorySheet]?
 *
 * Función aparte del `@Composable` para poder testearla, y porque la tercera condición es una
 * **guarda de plata**, no un detalle de dibujo.
 *
 * Las dos primeras estaban desde siempre: hay algo escrito, y no es la categoría que el
 * movimiento ya tiene. La tercera —**no es una categoría reservada**— es de la Ola 16, y la
 * encontró la revisión de esta rama. Hasta acá el campo mostraba el cartel de arriba («"Saldo
 * inicial" la usa Movi sola — es una categoría reservada») y **dejaba el botón habilitado justo
 * debajo**: tocarlo mandaba `PUT /api/events/{id}/category`, que no bloqueaba [OPENING_CATEGORY],
 * y un gasto real de $50.000 quedaba anotado con esa categoría y FUERA de «Gastos del mes» —
 * medido: de $165.289 a $115.289, en silencio.
 *
 * Es **literalmente el bug que la Ola 10 cerró en QuickAdd**, palabra por palabra: «el aviso era
 * un cartel: se cerraba el selector con la categoría puesta, el botón seguía habilitado, y el
 * gasto quedaba anotado y FUERA de "Gastos del mes" sin que nada lo dijera» (ver
 * `QuickAddScreen.kt`). Ahí la puerta era `POST /api/events`, que sí quedó cerrada; esta era la
 * otra puerta, por `PUT`, y quedó abierta.
 *
 * Y entra **en esta rama** y no en una siguiente porque es esta rama la que sube el precio del
 * error: desde que [showsInMovements] saca las aperturas de la lista, la fila envenenada ya no se
 * queda a la vista sin signo — **desaparece**.
 *
 * La guarda es de la UI. La del server y la del espejo local van aparte, en la misma ola: acá se
 * corta antes para no ofrecer una acción que iba a fallar, allá se corta porque la app no es la
 * única que puede llamar a esa ruta.
 */
fun ofreceCategoriaEscritaAMano(freeText: String, currentCategory: String): Boolean {
    val escrita = freeText.trim()
    return escrita.isNotEmpty() && escrita != currentCategory && !isReservedCategory(escrita)
}

/**
 * Cambia la categoría de un movimiento ya registrado.
 *
 * Lista las [PREDEFINED_CATEGORIES] del mismo lado que el movimiento (gasto vs. ingreso):
 * cambiar un gasto a una categoría de ingreso no significa nada en Movi. Elegir una llama a
 * [com.jvillada.movi.shared.repository.WalletRepository.updateEventCategory] — el server
 * recalcula `countsAsCashFlow`, esta hoja nunca lo manda.
 *
 * Ola 2 #7: la lista del catálogo sigue arriba como atajo, PERO abajo también hay un
 * [com.jvillada.movi.ui.components.CategoryField] (texto libre con sugerencias) — si el dueño
 * ya creó "Colegio" a mano desde QuickAdd, tiene que poder mover ahí un gasto que entró por SMS
 * o extracto, no solo elegir entre el catálogo fijo. Elegir de la lista o escribir en el campo
 * hacen lo mismo ([choose]). El caso que el campo libre no resuelve solo — la categoría actual
 * del movimiento cuando no está en el catálogo (viene de un extracto importado, ver
 * `currentIsKnown` abajo) — lo sigue resolviendo la lista, agregándola como opción marcada.
 */
@Composable
fun ChangeCategorySheet(
    event: FinancialEvent,
    onDismiss: () -> Unit,
    /**
     * El movimiento quedó modificado — hoy por un cambio de categoría o **de fecha** (Ola 13).
     * Se llamaba `onCategoryChanged` y se renombró cuando dejó de ser solo eso: quien la
     * recibe cierra la hoja y recarga, que es lo mismo en los dos casos.
     */
    onEventChanged: (FinancialEvent) -> Unit,
    /**
     * Ola 16: llevar al detalle de la cuenta de este movimiento. Solo lo usa la rama del saldo
     * inicial de abajo, y es opcional porque quien abre la hoja puede no saber a qué grupo va
     * ese detalle (la lista de cuentas todavía no llegó): sin destino confiable no se ofrece el
     * botón, en vez de mandarlo a la pantalla equivocada.
     */
    onVerCuenta: (() -> Unit)? = null,
    /**
     * Anular este movimiento. El dueño: *«No tengo cómo eliminar un movimiento que fue un
     * error»* — y tenía razón desde Movimientos, que es donde uno mira sus gastos: anular
     * existía **solo** en el detalle de la cuenta, donde tocar una fila abre directamente la
     * hoja de anular y NO deja cambiar categoría ni fecha. O sea, dos hojas distintas para el
     * mismo movimiento según por dónde se llegara, y a cada una le faltaba lo de la otra.
     *
     * Se ofrece como acción aparte y no como una fila más de la lista de categorías: anular
     * saca la plata de todas las cifras, y eso no puede quedar a un toque de distancia de
     * «Comida».
     */
    onAnular: (() -> Unit)? = null,
) {
    val coroutine = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Ola 2 #7: el campo libre de abajo no comete nada al tipear — recién se guarda con el
    // botón "Usar esta categoría" (mismo criterio que la lista de arriba, que sí guarda al
    // toque porque ahí elegir ES la acción completa).
    var freeText by remember { mutableStateOf("") }

    // Ola 10: el atajo del catálogo respeta lo que el dueño decidió en «Más → Categorías» —
    // misma regla única que las sugerencias ([effectiveCategoryTypes]). Sin esto, una categoría
    // escondida seguiría apareciendo acá y «esconder» habría sido media promesa.
    val categoryPrefs = UsedCategoriesCache.prefs
    val options = remember(event.type, categoryPrefs) {
        PREDEFINED_CATEGORIES.filter { cat ->
            val pref = categoryPrefs.entries
                .firstOrNull { it.key.trim().equals(cat.name, ignoreCase = true) }?.value
            if (pref?.hidden == true) return@filter false
            val efectivos = effectiveCategoryTypes(cat.name, pref?.pinnedType)
            efectivos.isEmpty() || event.type in efectivos
        }
            // La tercera lista de categorías que ve el dueño, y la misma queja: el catálogo se
            // mostraba en el orden en que alguien lo escribió. Alfabético, igual que las
            // sugerencias de `CategoryField` y que «Más → Categorías».
            .sortedWith(compareBy(CATEGORY_NAME_ORDER) { it.name })
    }
    // Los extractos importados traen categorías libres del parser (ver ClaudeStatementParser)
    // que pueden no estar en el catálogo. Si la actual no aparece en `options`, se agrega igual
    // como la opción ya marcada — perderla acá sería más confuso que una entrada de más.
    val currentIsKnown = options.any { it.name == event.category }

    fun choose(category: String) {
        if (category == event.category || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.updateEventCategory(event.id, category) }
            saving = false
            result.onSuccess { onEventChanged(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    // Un traspaso no se recategoriza: sacarlo de su categoría reservada lo devolvería al gasto
    // del mes —el gasto fantasma que la feature vino a matar— y dejaría a la otra pata adentro,
    // contando la mitad de un movimiento que nunca ocurrió. El server también lo rechaza (422);
    // acá se explica en vez de ofrecer una lista que iba a fallar al tocarla.
    if (isTransferLeg(event)) {
        BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SheetLabel("TRASPASO")
                Spacer(Modifier.height(8.dp))
                Text(event.description, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(16.dp))
                Text(TRANSFER_RECATEGORIZE_BLOCKED, fontSize = 13.5.sp, color = MinTextMute)
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                // La FECHA de un traspaso sí se puede corregir, aunque su categoría no: no hay
                // ninguna contabilidad que romper —las dos patas se mueven juntas, el server
                // cascadea por `transferId`— y sin esto un traspaso mal fechado seguiría sin
                // arreglo posible que no fuera anularlo entero y rehacerlo.
                SeccionDeFecha(event = event, onFechaCambiada = onEventChanged)
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }

    // Ola 16 · un saldo inicial tampoco se recategoriza desde acá, y esta rama es la mitad
    // indispensable del cambio que lo sacó de la lista de Movimientos.
    //
    // Dos motivos, y el segundo es el que obliga:
    //
    // 1. **No hay nada que recategorizar.** La apertura no es un gasto ni un ingreso mal
    //    clasificado: es el ancla del saldo. Ofrecerle veinte categorías es ofrecerle una acción
    //    que no responde a ninguna pregunta que el dueño se esté haciendo frente a esta fila.
    //
    // 2. **La acción que se ofrecía era destructiva y silenciosa.** Medido leyendo el server:
    //    `PUT /api/events/{id}/category` bloquea TRANSFER_CATEGORY y ORPHANED_LEG_CATEGORY, pero
    //    NO OPENING_CATEGORY — el opening se puede sacar de su categoría reservada. Y sacarlo es
    //    justo lo que `isCashFlow` mira: en una cuenta de activo, mover un «Saldo inicial» de
    //    $9.000.000 a «Otros ingresos» lo convierte de golpe en +$9.000.000 de «Ingresos del
    //    mes», sin un solo aviso. La puerta estaba abierta y esta hoja era la única forma de
    //    llegar a ella; se cierra donde se puede explicar, que es acá.
    //
    // La fecha SÍ se sigue pudiendo corregir, igual que en la rama del traspaso de arriba: un
    // opening mal fechado no rompe ningún saldo (el saldo suma todos los eventos sin mirar el
    // día) pero sí manda la fila a un día que no es, y no hay otro lugar donde arreglarlo.
    if (isOpeningBalance(event)) {
        BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SheetLabel("SALDO INICIAL")
                Spacer(Modifier.height(8.dp))
                Text(event.description, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(16.dp))
                Text(OPENING_BALANCE_EXPLAINER, fontSize = 13.5.sp, color = MinTextMute, lineHeight = 19.sp)
                if (onVerCuenta != null) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MinPrimaryContainer)
                            .clickable(onClick = onVerCuenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Ver la cuenta",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinOnPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                SeccionDeFecha(event = event, onFechaCambiada = onEventChanged)
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }

    BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(event.description, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
            Spacer(Modifier.height(18.dp))
            // Ola 15 · la pata huérfana se explica sola, acá y no en la fila.
            //
            // La fila de Movimientos no tiene lugar: su subtítulo es «categoría · cuenta» con una
            // línea y ellipsis (verificado en el navegador cuando se descartó partir esta
            // categoría en dos por dirección). Y esta hoja es donde el dueño llega justo cuando se
            // pregunta qué es esa fila — la abre para tocarle la categoría.
            //
            // Va ARRIBA de la fecha y de la lista, no al final, por lo mismo que la fecha: lo que
            // queda debajo de veinte renglones de categorías, en la práctica, no existe.
            if (isOrphanedTransferLeg(event)) {
                SheetLabel("ERA UN TRASPASO")
                Spacer(Modifier.height(8.dp))
                Text(ORPHANED_LEG_EXPLAINER, fontSize = 13.5.sp, color = MinTextMute)
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
            }
            // La fecha va ARRIBA de la lista de categorías, no al final: la lista mide veinte
            // renglones y todo lo que quede debajo de ella es, en la práctica, invisible.
            SeccionDeFecha(event = event, onFechaCambiada = onEventChanged)
            Spacer(Modifier.height(20.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            SheetLabel("CAMBIAR CATEGORÍA")
            Spacer(Modifier.height(12.dp))

            if (!currentIsKnown) {
                CategoryRow(name = event.category, selected = true, enabled = false, onClick = {})
                Hairline()
            }
            options.forEachIndexed { i, cat ->
                CategoryRow(
                    name = cat.name,
                    selected = cat.name == event.category,
                    enabled = !saving,
                    onClick = { choose(cat.name) },
                    // Ola 10: «Pago de tarjeta» sigue estando acá a propósito —es el camino real
                    // para arreglar un «No es» tocado por error— pero elegirla saca el movimiento
                    // de las cifras del mes, y eso no se puede dejar mudo: en el campo de
                    // categoría la misma palabra está prohibida, así que sin este renglón la
                    // asimetría se lee como un descuido en vez de una decisión.
                    subtitle = if (cat.name == CARD_PAYMENT_CATEGORY) {
                        "Deja de contar en tus gastos del mes: la compra ya se contó al usar la tarjeta"
                    } else null,
                )
                if (i < options.size - 1) Hairline()
            }

            Spacer(Modifier.height(16.dp))
            SheetLabel("O ESCRIBE OTRA")
            Spacer(Modifier.height(8.dp))
            // Ola 2 #7: campo libre con sugerencias, para categorías propias del dueño (creadas
            // a mano en QuickAdd/Presupuestos/Recurrentes) que no están en el catálogo de arriba.
            CategoryField(
                value = freeText,
                onValueChange = { freeText = it },
                type = event.type,
                usedCategories = UsedCategoriesCache.used,
                prefs = UsedCategoriesCache.prefs,
                label = null,
                placeholder = "Ej: Colegio",
            )
            val trimmedFreeText = freeText.trim()
            if (ofreceCategoriaEscritaAMano(freeText, event.category)) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!saving) MinPrimaryContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !saving) { choose(trimmedFreeText) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Usar \"$trimmedFreeText\"",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!saving) MinOnPrimaryContainer else MinTextFaint,
                    )
                }
            }

            if (saving) {
                Spacer(Modifier.height(10.dp))
                Text("Guardando…", fontSize = 12.sp, color = MinTextMute)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }

            // Va al FINAL y separado por una línea: es la única acción de esta hoja que quita
            // plata de las cifras, y no comparte lugar con las que solo la reclasifican.
            if (onAnular != null) {
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Anular este movimiento",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinExpense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !saving) { onAnular() }
                        .padding(vertical = 12.dp),
                )
                // «Anular», no «borrar»: el movimiento no se pierde, deja de contar. Decirlo acá
                // evita que alguien no lo toque por miedo a perder el registro.
                Text(
                    text = "Deja de contar en tus cifras. El registro no se pierde.",
                    fontSize = 11.5.sp,
                    color = MinTextMute,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Candidatos que el server propone como pago de extracto de tarjeta (`looksLikeCardPayment`
 * en el server) pero que todavía no están marcados con [CARD_PAYMENT_CATEGORY]. **Cada uno se
 * confirma por separado** — no hay botón de "marcar todos": es la promesa central de esta
 * feature (el dueño decide, Movi no adivina en bloque).
 *
 * Cada fila tiene dos botones: "Marcar" (confirma que sí es el pago, recategoriza) y "No es"
 * (descarta el candidato para siempre — ver [com.jvillada.movi.shared.repository.WalletRepository.dismissCardPaymentCandidate]
 * — **sin tocar la categoría**: el gasto sigue contando como flujo de caja del mes). Si "No es"
 * fue un error, no hay forma de deshacerlo acá: el movimiento sigue en Movimientos y se
 * recategoriza a mano desde ahí con [ChangeCategorySheet].
 */
@Composable
fun CardPaymentCandidatesSheet(
    candidates: List<FinancialEvent>,
    onDismiss: () -> Unit,
    /** Recibe el id confirmado: quien llama tiene que poder descartarlo aunque el refetch falle. */
    onConfirmed: (String) -> Unit,
    /** Recibe el id de un "No es": mismo motivo que [onConfirmed] — el refetch puede fallar. */
    onDismissedCandidate: (String) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var remaining by remember(candidates) { mutableStateOf(candidates) }
    var savingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun confirm(event: FinancialEvent) {
        if (savingId != null) return
        savingId = event.id
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.updateEventCategory(event.id, CARD_PAYMENT_CATEGORY) }
            savingId = null
            result.onSuccess {
                remaining = remaining.filterNot { it.id == event.id }
                onConfirmed(event.id)
            }.onFailure { error = it.toUserMessage() }
        }
    }

    fun dismiss(event: FinancialEvent) {
        if (savingId != null) return
        savingId = event.id
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.dismissCardPaymentCandidate(event.id) }
            savingId = null
            result.onSuccess {
                remaining = remaining.filterNot { it.id == event.id }
                onDismissedCandidate(event.id)
            }.onFailure { error = it.toUserMessage() }
        }
    }

    BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = savingId == null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            SheetLabel("PAGOS DE TARJETA SIN MARCAR")
            Spacer(Modifier.height(8.dp))
            Text(
                "Marcarlos como \"$CARD_PAYMENT_CATEGORY\" evita contar esta plata dos veces: " +
                    "ya se contó como gasto el día que se compró con la tarjeta.",
                fontSize = 12.sp,
                color = MinTextMute,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (remaining.isEmpty()) {
                Text("Ya no quedan pagos por confirmar.", fontSize = 13.sp, color = MinTextMute)
            }

            remaining.forEachIndexed { i, event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("hoy: ${event.category}", fontSize = 12.sp, color = MinTextMute)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatCOP(event.amount),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinText,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    val isSavingThis = savingId == event.id
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MinBorderStrong, RoundedCornerShape(10.dp))
                                .clickable(enabled = savingId == null) { dismiss(event) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (isSavingThis) "…" else "No es",
                                color = MinTextMute,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSavingThis) MinTextFaint else MinText)
                                .clickable(enabled = savingId == null) { confirm(event) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (isSavingThis) "Marcando…" else "Marcar",
                                color = MinBg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (i < remaining.size - 1) Hairline()
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MinExpense)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * **La fecha de un movimiento ya guardado, editable acá mismo.**
 *
 * ## Por qué en esta hoja y no en una pantalla nueva
 *
 * Porque esta hoja YA es «el movimiento que tocaste» — es lo que se abre al tocar un renglón en
 * Movimientos, y hasta ahora lo único que dejaba cambiar era la categoría. Hasta esta rama, la
 * única forma de arreglarle la fecha a un gasto era **anularlo y volver a crearlo**: perder su id
 * —y con él la ocurrencia de recurrente que lo señalara, y su renglón de historia— para corregir
 * un dato que el dueño nunca había podido elegir.
 *
 * ## Por qué hay un paso de confirmación acá y no en la hoja de Agregar
 *
 * Al anotar, elegir la fecha ES la acción y no hay nada que romper: el movimiento todavía no está
 * en ningún mes. Al **corregir**, en cambio, la fecha ya está contada en algún lado, y cambiarla
 * de mes mueve plata entre meses — puede sacarla de un mes que el dueño ya dio por cerrado. Por
 * eso acá elegir un día NO guarda: muestra el aviso ([avisoDeCambioDeMes]) y recién después se
 * confirma. Mismo criterio que la unificación de categorías, que también avisa antes con las
 * palabras exactas.
 */
@Composable
private fun SeccionDeFecha(
    event: FinancialEvent,
    onFechaCambiada: (FinancialEvent) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val hoy = remember { hoyEnAppZone() }
    val actual = remember(event.timestamp) { fechaDeEpoch(event.timestamp) }
    var abierto by remember { mutableStateOf(false) }
    var elegida by remember(event.timestamp) { mutableStateOf(actual) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /**
     * El sello de «esto ya ocurrió» que algún recurrente puso sobre este movimiento, si lo hay.
     *
     * Se pide **al abrir el selector** y no al componer la hoja: la enorme mayoría de las veces
     * el dueño abre esta hoja para cambiar una categoría y nunca toca la fecha, y no hay por qué
     * gastarle un viaje de red en eso. Si la llamada falla queda en `null` y el aviso extra
     * simplemente no aparece — un aviso que no se pudo cargar no puede impedir corregir una fecha
     * (ver el KDoc de `getEventOccurrenceMark`).
     */
    var sello by remember(event.id) { mutableStateOf<EventOccurrenceMark?>(null) }
    LaunchedEffect(event.id, abierto) {
        if (abierto && sello == null) {
            sello = runCatching { Repositories.wallets.getEventOccurrenceMark(event.id) }.getOrNull()
        }
    }

    fun guardar() {
        if (guardando || elegida == actual) return
        guardando = true
        error = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.updateEventTimestamp(
                    event.id,
                    timestampParaFecha(elegida, hoy),
                )
            }
            guardando = false
            result.onSuccess { onFechaCambiada(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    SheetLabel("FECHA")
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !guardando) { abierto = !abierto },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = etiquetaDeFecha(actual, hoy),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MinText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (abierto) "Cerrar" else "Cambiar",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MinPrimary,
        )
    }

    if (abierto) {
        Spacer(Modifier.height(12.dp))
        SelectorDeFecha(
            seleccionada = elegida,
            hoy = hoy,
            // Elegir NO guarda: deja la fecha pendiente para que el aviso de abajo pueda decir
            // qué implica antes de que se confirme.
            onPick = { elegida = it },
            enabled = !guardando,
        )
        // Los dos avisos, y en este orden: el del sello va PRIMERO porque es el único cuyo
        // costo es plata (un pago que deja de recordarse), y el del mes lo acompaña.
        val avisos = listOfNotNull(
            avisoDeSelloSuelto(sello, elegida),
            avisoDeCambioDeMes(actual, elegida, hoy),
        )
        avisos.forEach { aviso ->
            Spacer(Modifier.height(12.dp))
            Text(aviso, fontSize = 12.5.sp, color = MinWarn, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        val puedeGuardar = elegida != actual && !guardando
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (puedeGuardar) MinPrimaryContainer else MinSurfaceContainerLow)
                .clickable(enabled = puedeGuardar) { guardar() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    guardando -> "Guardando…"
                    elegida == actual -> "Elige otro día"
                    else -> "Mover a ${etiquetaDeFecha(elegida, hoy).lowercase()}"
                },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (puedeGuardar) MinOnPrimaryContainer else MinTextFaint,
            )
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 12.sp, color = MinExpense)
        }
    }
}
