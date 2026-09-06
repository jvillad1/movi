package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import com.jvillada.movi.shared.model.avisoDeMontoDeUnPar
import com.jvillada.movi.shared.model.PATA_NO_CAMBIA_DE_CUENTA
import com.jvillada.movi.shared.model.avisoDeCambioDeCuenta
import com.jvillada.movi.shared.model.OPENING_BALANCE_EXPLAINER
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.ORPHANED_LEG_EXPLAINER
import com.jvillada.movi.shared.model.TRANSFER_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.RecurringRule
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
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.recurrentes.claveDeNombre
import com.jvillada.movi.ui.recurrentes.equivalenteYaAnotado
import com.jvillada.movi.ui.recurrentes.nombresDeSuscripcionesQueYaSuman
import com.jvillada.movi.ui.recurrentes.prefillFrom
import com.jvillada.movi.ui.recurrentes.prefillNameFor
import com.jvillada.movi.ui.recurrentes.puedeOfrecerseComoRecurrenteDesdeElDetalle
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
     * Las cuentas del dueño, para poder **mover el movimiento de cuenta** (ver
     * [SeccionDelMovimiento]). Vacía por defecto y eso significa «todavía no llegaron»: sin
     * cuentas, el selector no se abre y el monto y el concepto se siguen pudiendo corregir. Una
     * lista de cuentas que no llegó no puede impedir arreglar una cifra.
     */
    cuentas: List<Account> = emptyList(),
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
    /**
     * **«Esto se repite todos los meses»**, pedido desde el detalle del movimiento. Recibe el
     * formulario ya lleno; quien llama abre [com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet]
     * con él (esta hoja no puede abrir otra encima de sí misma: la de arriba se cerraría con la
     * de abajo).
     *
     * El dueño: *«si no marqué algo recurrente pero lo es, poder hacerlo desde el movimiento
     * luego, y que se agregue el recurrente»*. Hasta hoy solo se ofrecía en una barra que aparece
     * 12 segundos después de guardar y se va sola — o sea, nunca «luego».
     *
     * `null` = quien abre la hoja no tiene dónde poner ese formulario, y entonces la sección **no
     * se dibuja**. Es a propósito: una fila que promete una acción que nadie va a atender es peor
     * que no tenerla.
     */
    onMarcarComoRecurrente: ((RecurringPrefill) -> Unit)? = null,
    /**
     * PR 1 del rediseño de Recurrentes: cuando este movimiento **ya** es la ocurrencia de una
     * [RecurringRule] existente, tocar la fila de [SeccionEstoSeRepite] pide editar esa regla acá
     * mismo en vez del mensaje mudo de antes («edítalo desde Recurrentes» — una promesa de
     * navegación que dejó de ser cierta en cuanto esa pantalla desapareció). Quien llama
     * abre [com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet] con `existing = regla`, el
     * mismo patrón con el que la pantalla «Recurrentes» editaba una regla mientras existió.
     *
     * `null` = no se ofrece la edición en el momento — la fila se explica igual, solo sin la
     * acción tappeable. Es el mismo criterio que [onMarcarComoRecurrente]: una acción sin dónde
     * caer es peor que no tenerla.
     */
    onEditarRecurrente: ((RecurringRule) -> Unit)? = null,
) {
    val coroutine = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /**
     * El error de guardar el monto / la cuenta / el concepto, **izado hasta acá a propósito**.
     *
     * Vive en el padre y no dentro de [SeccionDelMovimiento] porque se pinta **fuera del área que
     * scrollea** (ver [BarraDeError]). Este proyecto ya perdió un mensaje de error exactamente
     * así: renderizado adentro del `verticalScroll` de una hoja, veinte renglones más abajo de
     * donde estaba mirando el dueño, o sea en un lugar donde no existe.
     */
    var errorDeEdicion by remember(event.id) { mutableStateOf<String?>(null) }
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
                // El rótulo dice QUÉ es, y no «TRASPASO» para todo: la mitad de una cuota se abre
                // por esta misma rama —es una pata de un par— y llamarla traspaso contradice al
                // renglón de Movimientos, que desde esta ola dice «Cuota de crédito».
                SheetLabel(
                    when (event.category) {
                        CUOTA_CATEGORY -> "CUOTA DE CRÉDITO"
                        CARD_PAYMENT_CATEGORY -> "PAGO DE TARJETA"
                        else -> "TRASPASO"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(event.description, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(16.dp))
                Text(TRANSFER_RECATEGORIZE_BLOCKED, fontSize = 13.5.sp, color = MinTextMute)
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                // El MONTO y el CONCEPTO de una pata sí se corrigen, por el mismo argumento con
                // el que la fecha ya se corregía acá: el monto de un par es UN hecho con dos
                // anotaciones, y el server lo cascadea a las dos por `transferId`. Lo que no se
                // puede es moverle la CUENTA a una sola mitad — la sección lo dice y no ofrece el
                // selector (ver PATA_NO_CAMBIA_DE_CUENTA).
                SeccionDelMovimiento(
                    event = event,
                    cuentas = cuentas,
                    onError = { errorDeEdicion = it },
                    onGuardado = onEventChanged,
                )
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                // La FECHA de un traspaso sí se puede corregir, aunque su categoría no: no hay
                // ninguna contabilidad que romper —las dos patas se mueven juntas, el server
                // cascadea por `transferId`— y sin esto un traspaso mal fechado seguiría sin
                // arreglo posible que no fuera anularlo entero y rehacerlo.
                SeccionDeFecha(event = event, onFechaCambiada = onEventChanged)
                // **Y anular, que en esta rama se había perdido.**
                //
                // Hasta acá este `return` temprano se llevaba puesta la única salida que tiene una
                // pata. Cuando el detalle de la cuenta abría [com.jvillada.movi.ui.accounts.VoidEventSheet]
                // directo sobre la fila, anular una mitad se podía; al mandar las dos pantallas por
                // [HojaDelMovimiento] —que es lo correcto y no se deshace— esta rama quedó
                // devolviendo explicador, monto y fecha, sin pasar nunca por el bloque de abajo. O
                // sea que una cuota mal registrada, un traspaso o un pago de tarjeta **dejaron de
                // poder anularse desde ningún lado**.
                //
                // Y es la fila que MÁS necesita la salida: a una pata no se le puede cambiar ni la
                // cuenta ni la categoría, y [PATA_NO_CAMBIA_DE_CUENTA] remata con «anúlalo y vuelve
                // a registrarlo desde Agregar» — un consejo que apuntaba a un botón inexistente.
                //
                // Anular una pata es seguro y ya lo era: el server cascadea a las dos por
                // `transferId` dentro de la misma transacción, así que no hay forma de dejar medio
                // par anulado. Lo que se anula, y por cuánto en cada cuenta, lo dice la hoja de
                // anular ([loQuePasaAlAnular]).
                SeccionDeAnular(onAnular, habilitado = true)
                Spacer(Modifier.height(24.dp))
            }
            BarraDeError(errorDeEdicion)
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
            // El monto, la cuenta y el concepto van PRIMERO, arriba de la fecha y de la lista de
            // categorías: es lo que el dueño vino a arreglar cuando abre esta hoja para corregir
            // una cifra, y todo lo que quede debajo de veinte renglones de categorías es, en la
            // práctica, invisible (el mismo argumento que ya subió la fecha hasta acá).
            SeccionDelMovimiento(
                event = event,
                cuentas = cuentas,
                onError = { errorDeEdicion = it },
                onGuardado = onEventChanged,
            )
            // «Esto se repite todos los meses» — solo si quien abrió la hoja tiene dónde poner el
            // formulario, y solo sobre un movimiento al que la pregunta le aplica (ver
            // [puedeOfrecerseComoRecurrenteDesdeElDetalle]).
            if (onMarcarComoRecurrente != null && puedeOfrecerseComoRecurrenteDesdeElDetalle(event)) {
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                SeccionEstoSeRepite(
                    event = event,
                    onError = { errorDeEdicion = it },
                    onAbrirFormulario = onMarcarComoRecurrente,
                    onEditarRecurrente = onEditarRecurrente,
                )
            }
            Spacer(Modifier.height(20.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
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

            SeccionDeAnular(onAnular, habilitado = !saving)
            Spacer(Modifier.height(20.dp))
        }
        BarraDeError(errorDeEdicion)
    }
}

/**
 * **Anular, al final de la hoja y separado por una línea.**
 *
 * Es la única acción de esta hoja que quita plata de las cifras, y no comparte lugar con las que
 * solo la reclasifican. `null` en [onAnular] = quien abrió la hoja no ofrece anular.
 *
 * Un componente y no dos copias porque son **dos** las ramas que lo pintan —el movimiento común y
 * la mitad de un par— y la segunda llegó a existir justamente porque este bloque vivía suelto
 * dentro de una sola de ellas. Con la acción acá, agregar una rama nueva mañana y olvidarse de la
 * salida cuesta una línea visible en vez de un `return` temprano que no se ve.
 */
@Composable
private fun SeccionDeAnular(onAnular: (() -> Unit)?, habilitado: Boolean) {
    if (onAnular == null) return
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
            .clickable(enabled = habilitado) { onAnular() }
            .padding(vertical = 12.dp),
    )
    // «Anular», no «borrar»: el movimiento no se pierde, deja de contar. Decirlo acá evita que
    // alguien no lo toque por miedo a perder el registro.
    Text(
        text = "Deja de contar en tus cifras. El registro no se pierde.",
        fontSize = 11.5.sp,
        color = MinTextMute,
    )
}

// `BarraDeError` se mudó a `ui/components/ErrorMessages.kt` (llega por el import con `*` de
// arriba): la hoja de anular necesitaba la misma barra, y hasta esta rama tenía su error pintado
// *adentro* del scroll — o sea el defecto exacto que este componente existe para no volver a tener.

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
 * **El monto, la cuenta y el concepto de un movimiento ya guardado, editables acá mismo.**
 *
 * ## Por qué existe
 *
 * El dueño: *«Necesito editar el valor del movimiento de Hija porque voy a pagar 3 millones desde
 * NU y 1 millón desde Bancolombia, quiero que quede bien. ¿Me ayudas a que sea editable el
 * movimiento?»*. Hasta esta ola la única salida era **anular y volver a crear** — perder el id del
 * movimiento (y con él su sello de recurrente y su descarte de «no es pago de tarjeta») para
 * arreglar una cifra que el dueño escribió mal.
 *
 * ## Cerrada por defecto, y por qué
 *
 * Mismo patrón que [SeccionDeFecha]: se ve el monto y la cuenta actuales, y hay que tocar
 * «Cambiar» para editar — el concepto también, y **el rótulo tiene que decirlo**: se llamó
 * «MONTO Y CUENTA» y el concepto quedó invisible para quien venía a renombrar. La enorme mayoría de las veces esta hoja se abre para recategorizar, y
 * tres campos de formulario desplegados sobre un movimiento ya guardado invitan a tocar lo que
 * nadie vino a tocar.
 *
 * ## Las tres cosas que esta sección decide sola
 *
 * 1. **Solo se ofrecen cuentas de la MISMA moneda** que el movimiento. El server rechaza el resto
 *    (ver `mensajeDeMonedaDistinta`) porque un movimiento en pesos dentro de una cuenta en dólares
 *    se sale del saldo que el dueño lee arriba. Ofrecer una opción que va a fallar es peor que no
 *    ofrecerla, así que se filtran — y se **dice** cuántas quedaron fuera, para que la ausencia no
 *    parezca un bug.
 * 2. **Una pata de un par no muestra selector de cuenta**, muestra la explicación
 *    ([PATA_NO_CAMBIA_DE_CUENTA]). El monto sí se edita y el aviso dice que se mueve en las dos
 *    mitades ([avisoDeMontoDeUnPar]).
 * 3. **Cambiar de cuenta puede sacar el movimiento del mes** —`isCashFlow` decide por tipo de
 *    cuenta— y eso se **avisa antes de guardar** ([avisoDeCambioDeCuenta]), nunca después. Es la
 *    misma regla que el aviso de cambio de mes al corregir la fecha: lo que mueve plata en otra
 *    pantalla se anuncia en esta.
 *
 * El error de guardado NO se pinta acá: sube al padre y se dibuja fuera del scroll (ver
 * [BarraDeError]).
 */
@Composable
private fun SeccionDelMovimiento(
    event: FinancialEvent,
    cuentas: List<Account>,
    onError: (String?) -> Unit,
    onGuardado: (FinancialEvent) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val esPataDeUnPar = event.transferId != null

    var abierto by remember(event.id) { mutableStateOf(false) }
    var selectorDeCuenta by remember(event.id) { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var monto by remember(event.amount) { mutableStateOf<Long?>(event.amount) }
    var cuentaId by remember(event.accountId) { mutableStateOf(event.accountId) }
    var concepto by remember(event.description) { mutableStateOf(event.description) }

    // Solo las de la misma moneda (ver el KDoc). Se cuenta lo que quedó fuera para poder decirlo.
    val elegibles = remember(cuentas, event.currency) {
        cuentas.filter { it.currency == event.currency }
    }
    val ocultasPorMoneda = cuentas.size - elegibles.size
    val cuentaActual = cuentas.firstOrNull { it.id == event.accountId }
    val cuentaElegida = cuentas.firstOrNull { it.id == cuentaId }

    val conceptoLimpio = concepto.trim()
    val hayCambios = monto != event.amount || cuentaId != event.accountId || conceptoLimpio != event.description
    val esValido = (monto ?: 0L) > 0L && conceptoLimpio.isNotEmpty()
    val puedeGuardar = hayCambios && esValido && !guardando

    fun guardar() {
        if (!puedeGuardar) return
        guardando = true
        onError(null)
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.updateEvent(
                    event.id,
                    EdicionDeMovimiento(
                        amount = monto,
                        accountId = cuentaId,
                        description = conceptoLimpio,
                    ),
                )
            }
            guardando = false
            result.onSuccess { onGuardado(it) }.onFailure { onError(it.toUserMessage()) }
        }
    }

    // Los TRES campos nombrados en el rótulo, no dos. Decía «MONTO Y CUENTA» y el dueño reportó
    // que «no puedo editar los nombres de los movimientos»: el concepto estaba detrás de un
    // rótulo que hablaba solo de plata, así que quien buscaba renombrar no tenía ningún motivo
    // para tocar «Cambiar». Un campo que existe pero nadie encuentra es un campo que no existe.
    SheetLabel("MONTO, CUENTA Y CONCEPTO")
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !guardando) { abierto = !abierto },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatMoney(event.amount, event.currency),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                fontFamily = FontFamily.Monospace,
            )
            // El nombre de la cuenta y no su id: si la lista todavía no llegó no se inventa nada.
            cuentaActual?.let {
                Text(it.name, fontSize = 12.sp, color = MinTextMute)
            }
        }
        Text(
            text = if (abierto) "Cerrar" else "Cambiar",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MinPrimary,
        )
    }

    if (!abierto) return

    Spacer(Modifier.height(14.dp))
    MoneyField(
        value = monto,
        onValueChange = { monto = it },
        label = "MONTO",
        prefix = if (event.currency == "COP") "$" else "US$",
    )

    Spacer(Modifier.height(16.dp))
    SheetLabel("CONCEPTO")
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
        // Este campo llega prellenado con la descripción del movimiento, así que reescribirlo
        // entero con el teclado es el gesto normal, no el raro.
        val campo = rememberCampoConSeleccion(concepto) {
            // El tope es el de la COLUMNA, no uno inventado más corto: con 120 aquí, abrir un
            // movimiento cuya descripción viene de un extracto largo y tocar una tecla le habría
            // recortado el texto en silencio.
            concepto = it.take(MAX_CONCEPTO_LENGTH)
        }
        BasicTextField(
            value = campo.valor,
            onValueChange = campo::alCambiar,
            enabled = !guardando,
            cursorBrush = SolidColor(MinText),
            textStyle = TextStyle(color = MinText, fontSize = 14.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            decorationBox = { inner ->
                if (concepto.isEmpty()) {
                    Text("Ej: Mesada de la hija", fontSize = 14.sp, color = MinTextMute)
                }
                inner()
            },
        )
    }

    Spacer(Modifier.height(16.dp))
    if (esPataDeUnPar) {
        SheetLabel("CUENTA")
        Spacer(Modifier.height(8.dp))
        Text(
            text = cuentaActual?.name ?: "La cuenta de este movimiento",
            fontSize = 14.sp,
            color = MinText,
        )
        Spacer(Modifier.height(6.dp))
        Text(PATA_NO_CAMBIA_DE_CUENTA, fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp)
    } else {
        SelectorDeCuentaDelMovimiento(
            elegibles = elegibles,
            ocultasPorMoneda = ocultasPorMoneda,
            moneda = event.currency,
            seleccionada = cuentaElegida,
            abierto = selectorDeCuenta,
            enabled = !guardando,
            onToggle = { selectorDeCuenta = !selectorDeCuenta },
            onPick = { cuentaId = it; selectorDeCuenta = false },
        )
    }

    // Los avisos, antes del botón. El de la cuenta primero porque su costo es que una cifra del
    // mes cambie sin que el dueño lo haya pedido; el del par lo acompaña.
    val avisos = listOfNotNull(
        avisoDeCambioDeCuenta(
            event = event,
            tipoActual = cuentaActual?.type,
            tipoNuevo = cuentaElegida?.type?.takeIf { cuentaId != event.accountId },
        ),
        // El texto depende de QUE CLASE de par es: en un traspaso las dos mitades valen lo mismo,
        // en la cuota de un credito la de la deuda vale solo el capital. Ver [avisoDeMontoDeUnPar].
        avisoDeMontoDeUnPar(event.category).takeIf { esPataDeUnPar && monto != event.amount },
    )
    avisos.forEach { aviso ->
        Spacer(Modifier.height(12.dp))
        Text(aviso, fontSize = 12.5.sp, color = MinWarn, lineHeight = 17.sp)
    }

    Spacer(Modifier.height(14.dp))
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
                !esValido -> "Escribe un monto y un concepto"
                !hayCambios -> "Sin cambios"
                else -> "Guardar cambios"
            },
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (puedeGuardar) MinOnPrimaryContainer else MinTextFaint,
        )
    }
}

/**
 * **«¿Esto se repite todos los meses?», preguntado cuando el dueño vuelve a mirar el movimiento.**
 *
 * ## Por qué no alcanzaba con lo que ya había
 *
 * Movi ya ofrecía convertir un movimiento en recurrente, pero **solo en los 12 segundos
 * siguientes a guardarlo** (la barra de `App.kt`, ver [RecurringOffer]). Esa barra está bien
 * pensada para lo que hace —no molestar— pero deja afuera el caso que el dueño nombró: *«si no
 * marqué algo recurrente pero lo es, poder hacerlo desde el movimiento luego»*. «Luego» es
 * exactamente cuando la barra ya se fue, y hasta hoy la única salida era ir a Recurrentes y
 * volver a escribir el nombre, el monto, la categoría, la cuenta y el día.
 *
 * ## Las TRES preguntas que necesitan red, y por qué se hacen AL TOCAR
 *
 * Tres cosas convertirían esto en un duplicado y ninguna se puede saber sin preguntar (la lista
 * la resuelve [equivalenteYaAnotado], que es donde está el porqué de cada una):
 *
 * 1. **Este movimiento ya es la ocurrencia de un recurrente** (`GET /api/events/{id}/occurrence`).
 * 2. **Ya hay una regla con este nombre** (`GET /api/recurring-rules`).
 * 3. **Ya hay una SUSCRIPCIÓN con este nombre** (`GET /api/subscriptions`). Es la que más fácil se
 *    cruza, porque las suscripciones se auto-descubren: el dueño puede tener «Netflix» sin
 *    haberlo escrito nunca, y Recurrentes suma reglas y suscripciones **juntas**. La barra de
 *    después de guardar ya la miraba; esta hoja no, y era la otra puerta al mismo doble conteo
 *    que este cambio vino a cerrar.
 *
 * Se preguntan **al tocar la fila** y no al abrir la hoja, por lo mismo que [SeccionDeFecha] pide
 * el sello recién cuando se abre el selector: la enorme mayoría de las veces esta hoja se abre
 * para otra cosa, y no hay por qué gastarle tres viajes de red a cada movimiento que el dueño
 * toque. El precio es que la respuesta llega después del toque; a cambio, la fila aparece siempre
 * y no depende de que tres lecturas hayan salido bien.
 *
 * ## Qué pasa si la red falla
 *
 * El formulario **se abre igual**, con lo que el movimiento ya sabe. Bloquear el alta porque una
 * comprobación anti-duplicado no se pudo hacer sería negarle al dueño la acción que pidió por un
 * problema que no es suyo; y el duplicado, si ocurre, se ve y se borra en Recurrentes. Es el
 * mismo criterio que ya toma `getEventOccurrenceMark`: un aviso que no se pudo cargar no puede
 * impedir la acción.
 *
 * ## PR 1 del rediseño de Recurrentes: el mensaje de «ya existe» dejó de ser un punto muerto
 *
 * Hasta acá, cuando [equivalenteYaAnotado] encontraba un equivalente, la sección se limitaba a
 * decir «edítalo desde Recurrentes» — una instrucción que manda a una pantalla que el dueño pidió
 * que dejara de existir como destino propio (ver el pedido en el PR que agrega esto). Ahora, si el
 * equivalente es una **REGLA**, se ofrece editarla en el momento con
 * [com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet].
 *
 * [equivalenteYaAnotado] solo devuelve el NOMBRE con el que ya está anotado — no dice por cuál de
 * sus tres puertas entró (el sello de ocurrencia, una regla, o una suscripción). Así que acá se
 * busca ese nombre, normalizado, en las `reglas` que la propia función ya acaba de leer: si hay
 * una regla con ese nombre, ESA es la que se edita (el sello de ocurrencia, cuando es el que
 * matcheó, señala igualmente el nombre de una regla real — las dos leídas juntas en la misma
 * consulta). Si no hay ninguna, el equivalente es una suscripción (o una regla que se renombró
 * justo entre el sello y esta lectura, un caso angosto): ahí no hay nada seguro que editar desde
 * acá, así que se explica y no se ofrece una acción que no tiene a dónde ir — la revisión completa
 * de suscripciones desde Movimientos queda para una próxima entrega de este mismo rediseño.
 */
@Composable
private fun SeccionEstoSeRepite(
    event: FinancialEvent,
    onError: (String?) -> Unit,
    onAbrirFormulario: (RecurringPrefill) -> Unit,
    onEditarRecurrente: ((RecurringRule) -> Unit)? = null,
) {
    val coroutine = rememberCoroutineScope()
    var consultando by remember(event.id) { mutableStateOf(false) }
    /** El equivalente es una REGLA de verdad: esta es la fila para editar. */
    var reglaExistente by remember(event.id) { mutableStateOf<RecurringRule?>(null) }
    /** El equivalente es una suscripción (o una regla que ya no se encuentra por nombre). */
    var nombreSinReglaQueEditar by remember(event.id) { mutableStateOf<String?>(null) }

    fun intentar() {
        if (consultando) return
        consultando = true
        onError(null)
        coroutine.launch {
            // Si alguna lectura falla se sigue de largo con `null`: ver «Qué pasa si la red
            // falla» en el KDoc.
            val sello = runCatching { Repositories.wallets.getEventOccurrenceMark(event.id) }.getOrNull()
            val reglas = runCatching { Repositories.wallets.getRecurringRules() }.getOrNull().orEmpty()
            // Las suscripciones, con el MISMO filtro que la barra de después de guardar
            // (`RecurringOfferGate`): solo las que de verdad suman. Sin esta lectura, quien ya
            // tenía la suscripción auto-descubierta «Netflix» podía crearle encima la regla
            // «Netflix» y ver el cobro dos veces en «Próximos pagos» y dos veces en el total.
            val cobros = runCatching { Repositories.wallets.getSubscriptions().subscriptions }
                .getOrNull().orEmpty()
            consultando = false
            val equivalente = equivalenteYaAnotado(
                selloDeOcurrencia = sello?.ruleName,
                reglas = reglas,
                suscripcionesQueYaSuman = nombresDeSuscripcionesQueYaSuman(cobros),
                nombre = prefillNameFor(event),
            )
            when {
                equivalente == null -> onAbrirFormulario(prefillFrom(event))
                else -> {
                    val clave = claveDeNombre(equivalente)
                    val regla = reglas.firstOrNull { claveDeNombre(it.name) == clave }
                    if (regla != null) reglaExistente = regla else nombreSinReglaQueEditar = equivalente
                }
            }
        }
    }

    SheetLabel("¿SE REPITE TODOS LOS MESES?")
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Movi puede anotarlo en Recurrentes con lo que ya tiene este movimiento: el " +
            "concepto, el monto, la categoría, la cuenta y el día. El primer recordatorio será " +
            "el mes que viene, porque este pago ya lo hiciste.",
        fontSize = 12.sp,
        color = MinTextMute,
        lineHeight = 17.sp,
    )
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (!consultando) MinPrimaryContainer else MinSurfaceContainerLow)
            .clickable(enabled = !consultando) { intentar() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (consultando) "Revisando…" else "Sí, se repite todos los meses",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (!consultando) MinOnPrimaryContainer else MinTextFaint,
        )
    }
    // No es un error: es la respuesta correcta a la pregunta que acaba de hacer. Va acá, pegada
    // al botón que tocó, y no en la barra de errores de abajo.
    reglaExistente?.let { regla ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Ya lo tienes anotado como «${regla.name}», así que este movimiento no necesita otro.",
            fontSize = 12.sp,
            color = MinTextMute,
            lineHeight = 17.sp,
        )
        if (onEditarRecurrente != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Editar este recurrente",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MinPrimary,
                modifier = Modifier.clickable { onEditarRecurrente(regla) },
            )
        }
    }
    nombreSinReglaQueEditar?.let { nombre ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Ya lo tienes anotado como «$nombre» (una suscripción confirmada), así que " +
                "este movimiento no necesita otro recurrente.",
            fontSize = 12.sp,
            color = MinTextMute,
            lineHeight = 17.sp,
        )
    }
}

/**
 * El selector de «¿de qué cuenta salió (o entró) esta plata?» para un movimiento ya guardado.
 *
 * Propio de esta hoja y no importado de `CreateRecurringRuleSheet` —que tiene uno parecido y
 * privado— por la misma razón por la que esta hoja trae su propio [BottomSheetScaffold]: cada
 * pantalla se arma sus helpers. Y dos diferencias que no son cosméticas: acá la cuenta **no es
 * opcional** (un movimiento siempre tiene una) y la lista viene filtrada por moneda, con el
 * recuento de lo que quedó fuera para poder explicarlo.
 *
 * Se abre y se cierra en su lugar, sin una hoja encima de la hoja: esta ya vive dentro de un
 * `verticalScroll` y una modal sobre otra modal, en el navegador angosto, es una trampa.
 */
@Composable
private fun SelectorDeCuentaDelMovimiento(
    elegibles: List<Account>,
    ocultasPorMoneda: Int,
    moneda: String,
    seleccionada: Account?,
    abierto: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
) {
    SheetLabel("CUENTA")
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && elegibles.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Sin la lista de cuentas no se sabe el nombre — se dice, en vez de inventarlo o
                // de mostrar el id. El monto y el concepto se siguen pudiendo guardar igual.
                text = seleccionada?.name ?: "No pudimos cargar tus cuentas",
                fontSize = 14.sp,
                color = if (seleccionada != null) MinText else MinTextMute,
                modifier = Modifier.weight(1f),
            )
            if (elegibles.isNotEmpty()) {
                Text(if (abierto) "Cerrar" else "Elegir", fontSize = 12.sp, color = MinTextMute)
            }
        }
    }
    if (ocultasPorMoneda > 0) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Solo se listan tus cuentas en $moneda: un movimiento no cambia de moneda al " +
                "cambiar de cuenta.",
            fontSize = 11.5.sp,
            color = MinTextMute,
            lineHeight = 16.sp,
        )
    }
    if (abierto && elegibles.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerHigh)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp)),
        ) {
            elegibles.forEach { cuenta ->
                val elegida = cuenta.id == seleccionada?.id
                Text(
                    text = cuenta.name,
                    fontSize = 14.sp,
                    fontWeight = if (elegida) FontWeight.Medium else FontWeight.Normal,
                    color = if (elegida) MinOnPrimaryContainer else MinText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (elegida) Modifier.background(MinPrimaryContainer) else Modifier)
                        .clickable { onPick(cuenta.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
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
