package com.jvillada.movi.ui.transactions

import com.jvillada.movi.ui.components.ds.fondoDeLaFilaElegida
import kotlin.math.abs
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.Cifra
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.DESEMBOLSO_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import com.jvillada.movi.shared.model.avisoDeMontoDeUnPar
import com.jvillada.movi.shared.model.PATA_NO_CAMBIA_DE_CUENTA
import com.jvillada.movi.shared.model.avisoDeCambioDeCuenta
import com.jvillada.movi.shared.model.OPENING_BALANCE_EXPLAINER
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.esCategoriaQueNoSeElige
import com.jvillada.movi.shared.model.ORPHANED_LEG_EXPLAINER
import com.jvillada.movi.shared.model.TRANSFER_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.porQueLaPataNoSeRecategoriza
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.effectiveCategoryTypes
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.ofreceVincularDeuda
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.ui.destinos.FilaDelTercero
import com.jvillada.movi.ui.destinos.FilaGuardarElDestino
import com.jvillada.movi.ui.destinos.nombreParaLaFila
import com.jvillada.movi.ui.destinos.preguntaDelDestino
import com.jvillada.movi.ui.destinos.rememberDestinosParaGuardar
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import com.jvillada.movi.shared.model.CuotaPorSaldar
import com.jvillada.movi.shared.model.CuotaYaPagada
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.shared.model.cuotaQueSeParece
import com.jvillada.movi.shared.model.cuotaYaPagadaDe
import com.jvillada.movi.shared.model.cuotasPorSaldar
import com.jvillada.movi.shared.model.fraseDeLaCuotaYaPagada
import com.jvillada.movi.shared.model.preguntaDeLaCuotaQueSeParece
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.ui.fecha.avisoDeCambioDeMes
import com.jvillada.movi.ui.fecha.avisoDeSelloSuelto
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.fecha.timestampParaFecha
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.shared.model.marcaDe
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.recurrentes.claveDeNombre
import com.jvillada.movi.ui.recurrentes.equivalenteYaAnotadoDe
import com.jvillada.movi.ui.recurrentes.nombresDeSuscripcionesQueYaSuman
import com.jvillada.movi.ui.recurrentes.prefillFrom
import com.jvillada.movi.ui.recurrentes.puedeOfrecerseComoRecurrenteDesdeElDetalle
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto

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
    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = dismissEnabled) {
        content()
    }
}

/**
 * El marco del cuerpo de un movimiento según [lugar]: la hoja modal de siempre, o el panel de la
 * derecha de Movimientos (Ola W4). El panel lleva la misma X arriba a la derecha —cierra el panel y
 * deja la invitación a elegir— y el resto del alto para el contenido, que trae su propio scroll.
 */
@Composable
private fun MarcoDelMovimiento(
    lugar: LugarDelMovimiento,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (lugar) {
        LugarDelMovimiento.Hoja -> BottomSheetScaffold(onDismiss = onDismiss, dismissEnabled = dismissEnabled, content = content)
        LugarDelMovimiento.Panel -> Column(modifier = Modifier.fillMaxSize().padding(horizontal = Medida.margenDePantalla)) {
            SheetHandleWithClose(onClose = onDismiss, enabled = dismissEnabled, conManija = false)
            content()
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    // Ola R4: el título de una parte de la hoja es un [Seccion] del sistema —en frase, nunca en
    // mayúsculas— y trae su aire de abajo.
    Seccion(titulo = text)
}

@Composable
private fun CategoryRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /** Segunda línea opcional: hoy solo la usa «Pago de tarjeta», para decir qué implica elegirla. */
    subtitle: String? = null,
    /**
     * Ola N (fix round): con «Pago de tarjeta» y el respaldo de la actual arriba de la cuadrícula
     * en vez de debajo de ella, su padding de siempre (14 dp) empujaba la búsqueda fuera de la
     * vista en un teléfono de 731 dp — ver `la_busqueda_esta_a_la_vista_sin_buscarla_debajo_de_una_lista_larga`.
     * Estas dos filas no necesitan tanto aire (no son una lista larga que hay que poder tocar sin
     * errar): `true` las achica a la mitad.
     */
    compacto: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            // El check dibujado no dice nada por sí solo: la fila declara que está elegida.
            .semantics { this.selected = selected }
            // El alto de una fila del sistema; la compacta, el del objetivo táctil.
            .heightIn(min = if (compacto) Medida.objetivoTactil else Medida.altoDeFila)
            .padding(vertical = Espacio.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        // Task 3 (Ola B): reemplaza el ícono genérico de etiqueta por el de la categoría —
        // resuelto por `aparienciaDe`, el mismo que ya pinta Movimientos y el Inicio — así que la
        // fila de «la categoría actual» y cada opción de la lista se ven con su propio ícono.
        IconoDeCategoria(name, tamano = TamanoDeIconoDeCategoria.Chico)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = Movi.textos.titulo, color = Movi.colores.texto)
            if (subtitle != null) {
                Text(subtitle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.texto, modifier = Modifier.size(Icono.s))
    }
}

/**
 * ¿Se le ofrece a este movimiento marcarse como «Pago de tarjeta»? Las mismas dos condiciones que
 * tenía el catálogo de la hoja de recategorizar: que el dueño no la haya escondido, y que el tipo
 * del movimiento esté entre los que la categoría sirve ([effectiveCategoryTypes] — lo fijado por él
 * gana sobre el catálogo). Aparte del `@Composable` para poder probarla.
 */
/** Vuelta 51: la fila especial del detalle que convierte un gasto suelto en la cuota de un crédito. */
const val ES_LA_CUOTA_DE_UN_CREDITO: String = "Es la cuota de un crédito"

/** El tag del selector de crédito que abre «Es la cuota de un crédito». */
const val TAG_ELEGIR_EL_CREDITO: String = "movimiento:elegir-el-credito"

/** El tag de la pregunta «La cuota de octubre de X ya está pagada. ¿Este pago es un abono extra?» (vuelta 53). */
const val TAG_CUOTA_YA_PAGADA: String = "movimiento:cuota-ya-pagada"

/** El botón de esa pregunta: el abono extra de siempre (un traspaso que no salda ninguna cuota). */
const val UN_ABONO_EXTRA: String = "Un abono extra"

/**
 * Lo que explica esa pregunta. **No hay «la cuota del mes que viene (adelantada)»**: un pago salda la
 * cuota del período en que se hizo (`periodoQueSalda`, decisión del dueño), así que Movi no tiene cómo
 * adelantar una cuota sin una regla de plata nueva, y no la inventa.
 */
const val QUE_PASA_CON_EL_ABONO_EXTRA: String =
    "Un abono extra baja el capital por el monto entero y no marca otra cuota como pagada. " +
        "Movi no adelanta la cuota del mes que viene: un pago salda la cuota del período en que se hizo."

/** El tag de por qué una pata no se recategoriza, en su detalle (vuelta 57). */
const val TAG_POR_QUE_NO_SE_RECATEGORIZA: String = "movimiento:por-que-no-se-recategoriza"

/** «La cuota de octubre de Vehículo 4083 ya está pagada. ¿Este pago es un abono extra?» */
fun preguntaDeLaCuotaYaPagada(ya: CuotaYaPagada): String =
    "${fraseDeLaCuotaYaPagada(ya.credito, ya.periodo)} ¿Este pago es un abono extra?"

/**
 * **¿El detalle ofrece «Es la cuota de un crédito»?** (vuelta 51). Un gasto suelto —sin `transferId`—,
 * con una categoría que no sea de las que escribe Movi (la cuota y el pago de tarjeta ya ofrecen
 * vincular en «Más»), y con al menos un crédito (LOAN) donde ponerlo. Pura.
 */
fun puedeMarcarseCuotaDeCredito(event: FinancialEvent, cuentas: List<Account>): Boolean =
    event.type == TransactionType.EXPENSE && event.transferId == null &&
        !esCategoriaQueNoSeElige(event.category) && cuentas.any { it.type == AccountType.LOAN }

internal fun puedeMarcarsePagoDeTarjeta(tipo: TransactionType, prefs: Map<String, CategoryPref>): Boolean {
    val pref = prefs.entries
        .firstOrNull { it.key.trim().equals(CARD_PAYMENT_CATEGORY, ignoreCase = true) }?.value
    if (pref?.hidden == true) return false
    val efectivos = effectiveCategoryTypes(CARD_PAYMENT_CATEGORY, pref?.pinnedType)
    return efectivos.isEmpty() || tipo in efectivos
}

/**
 * ¿Se guarda lo que el dueño eligió en el campo de [ChangeCategorySheet]? (Hasta la revisión final
 * de la Ola B era «¿se dibuja el botón "Usar …"?»: ahora tocar la celda ya elige, y esta es la
 * guarda que decide si ese toque llega al `PUT`.)
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
    return escrita.isNotEmpty() && escrita != currentCategory && !esCategoriaQueNoSeElige(escrita)
}

/**
 * Cambia la categoría de un movimiento ya registrado.
 *
 * **Ola L — una sola forma de elegir categoría.** La categoría se elige con el mismo
 * [SelectorDeCategoria] que usan «Agregar», Presupuestos y Recurrentes: la actual marcada, la
 * búsqueda arriba, las frecuentes primero, todas las propias del dueño del mismo lado que el
 * movimiento (gasto vs. ingreso: cambiar un gasto a una categoría de ingreso no significa nada en
 * Movi) y «Crear "…"» para una nueva. Antes esta hoja tenía su propia lista, armada con
 * [com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES]: no traía «Fútbol», «Hija» ni ninguna
 * categoría propia, y el campo de buscar o crear quedaba plegado debajo de veinte filas.
 *
 * Tocar una celda llama a [com.jvillada.movi.shared.repository.WalletRepository.updateEventCategory]
 * — el server recalcula `countsAsCashFlow`, esta hoja nunca lo manda. La categoría actual se ve
 * marcada aunque no esté en ningún catálogo (viene de un extracto importado). «Pago de tarjeta»
 * es la única reservada que se ofrece, en su propia fila ([puedeMarcarsePagoDeTarjeta]).
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
    /** Ola 2b: la sección que se abre de entrada (ver [AccionDelMovimiento]); `null`, ninguna. */
    accionInicial: AccionDelMovimiento? = null,
) {
    ContenidoDelMovimiento(
        event = event,
        lugar = LugarDelMovimiento.Hoja,
        onDismiss = onDismiss,
        cuentas = cuentas,
        onEventChanged = onEventChanged,
        onVerCuenta = onVerCuenta,
        onAnular = onAnular,
        onMarcarComoRecurrente = onMarcarComoRecurrente,
        onEditarRecurrente = onEditarRecurrente,
        accionInicial = accionInicial,
    )
}

/**
 * Dónde se dibuja el cuerpo de un movimiento: en su hoja modal (el teléfono, y toda pantalla que
 * no tenga lugar al lado), o en el panel de la derecha de Movimientos en pantalla ancha (Ola W4,
 * ver `MovimientosListaYDetalle`).
 */
internal enum class LugarDelMovimiento { Hoja, Panel }

/**
 * [event] con el nombre que le pone su tercero (la regla de `NombreDelTercero.kt`, la misma que
 * acaba de aplicar el server), o `null` si no cambia.
 */
internal fun elMovimientoConElNombreDe(event: FinancialEvent, tercero: DestinoConocido): FinancialEvent? {
    val descripcion = com.jvillada.movi.shared.model.nombreDelMovimientoParaElDestino(event, tercero)
    val merchant = com.jvillada.movi.shared.model.merchantDelMovimientoParaElDestino(event, tercero)
    if (descripcion == null && merchant == null) return null
    return event.copy(description = descripcion ?: event.description, merchant = merchant ?: event.merchant)
}

/**
 * **El cuerpo de [ChangeCategorySheet]**, sacado de la hoja para poder dibujarse también en el
 * panel de al lado de la lista (Ola W4) — el mismo criterio con el que la Ola W2 sacó
 * `ContenidoDelDetalleDePeriodo`. Las cuatro ramas (traspaso, saldo inicial, la pregunta de los
 * parecidos y el movimiento común) son las mismas en los dos lugares; lo único que cambia es el
 * marco ([MarcoDelMovimiento]).
 *
 * En la hoja, [onEventChanged] la cierra. **En el panel no se cierra nada**: quien lo muestra relee
 * el movimiento y el panel sigue abierto con la versión nueva. Por eso lo que en la hoja se iba con
 * ella —el editor del monto abierto, la pregunta de los parecidos— acá se cierra a mano al terminar.
 */
@Composable
internal fun ContenidoDelMovimiento(
    event: FinancialEvent,
    lugar: LugarDelMovimiento,
    onDismiss: () -> Unit,
    cuentas: List<Account>,
    onEventChanged: (FinancialEvent) -> Unit,
    onVerCuenta: (() -> Unit)?,
    onAnular: (() -> Unit)?,
    onMarcarComoRecurrente: ((RecurringPrefill) -> Unit)?,
    onEditarRecurrente: ((RecurringRule) -> Unit)?,
    /**
     * Ola 2b · la sección que se abre de entrada. `null` (lo normal): el detalle se lee primero y
     * nada está abierto. Deslizar una fila de Movimientos a la izquierda abre con [AccionDelMovimiento.Categoria].
     */
    accionInicial: AccionDelMovimiento? = null,
    /** «4 veces este período · $142.800». Solo lo pasa quien tiene los movimientos del período a mano. */
    enEsteComercio: EnEsteComercio? = null,
    /** Abrir Movimientos con esta búsqueda. `null`: la línea se muestra sin acción. */
    onVerElComercio: ((String) -> Unit)? = null,
) {
    val coroutine = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    /**
     * Ola 2b · la sección abierta del detalle ([AccionDelMovimiento]), o ninguna. Una sola a la vez:
     * el detalle se lee primero, y cada sección de edición se abre desde su acción.
     */
    var accion by remember(event.id) { mutableStateOf(accionInicial) }
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
    // Ver [SeccionDelMovimiento]: tocar el nombre abre el editor, así que el estado vive acá.
    var edicionAbierta by remember(event.id) { mutableStateOf(false) }
    // Ola L: la lista de categorías ya no sale del catálogo fijo. Sale del mismo [SelectorDeCategoria]
    // que usan «Agregar», Presupuestos y Recurrentes —las propias del dueño, respetando lo que
    // escondió y el tipo—, así que las tres pantallas ofrecen lo mismo. Ver más abajo.
    val categoryPrefs = UsedCategoriesCache.prefs

    /**
     * **Los otros movimientos del mismo destinatario**, con la categoría que tienen hoy. Se piden
     * al abrir la hoja y no al elegir: cuando el dueño toca una categoría, la oferta tiene que
     * estar lista — una pausa de red en ese momento se siente como que la app se colgó.
     *
     * Si la lectura falla, la lista queda vacía y no se ofrece nada: es una ayuda de más, y no
     * puede impedir lo que el dueño vino a hacer.
     */
    var parecidos by remember(event.id) { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    LaunchedEffect(event.id) {
        parecidos = runCatching { Repositories.wallets.getParecidos(event.id) }.getOrDefault(emptyList())
    }
    /** El segundo paso: no nulo mientras se pregunta si el cambio va también para los parecidos. */
    var oferta by remember(event.id) { mutableStateOf<OfertaDeLote?>(null) }
    // **En el panel, dejar la pregunta sin contestar también avisa** (revisión final de la W4,
    // I-1). Cuando se pregunta por los parecidos, la categoría de ESTE movimiento ya quedó
    // guardada. En la hoja las únicas salidas son contestar o cerrarla, y las dos avisan
    // ([onEventChanged]); en el panel hay una tercera —tocar otra fila de la lista, o achicar la
    // ventana— que lo desmonta sin pasar por ahí, y la lista se quedaba con la categoría vieja. Al
    // desmontarse con la pregunta pendiente, se avisa con el movimiento ya cambiado: los parecidos
    // quedan como están, que es lo mismo que «No, solo este».
    if (lugar == LugarDelMovimiento.Panel) {
        val ofertaPendiente by rememberUpdatedState(oferta)
        val avisar by rememberUpdatedState(onEventChanged)
        DisposableEffect(Unit) {
            onDispose { ofertaPendiente?.let { avisar(it.movimiento) } }
        }
    }

    fun choose(category: String) {
        if (category == event.category || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.updateEventCategory(event.id, category) }
            saving = false
            result
                .onSuccess { actualizado ->
                    // Una categoría creada acá tiene que ser conocida al abrir el siguiente
                    // movimiento: la hoja también se abre desde «Por revisar» y desde el detalle de
                    // una cuenta, que no recargan el caché.
                    UsedCategoriesCache.record(category, actualizado.type)
                    // Arreglar uno puede arreglar a los parecidos — pero no sin preguntar: son
                    // movimientos que el dueño no está mirando, y cambiarlos solos sería mover
                    // cifras suyas a sus espaldas.
                    val otros = parecidosQueCambiarian(parecidos, category)
                    // Elegida, el selector se cierra: el chip ya dice la nueva. Si la nueva es de
                    // deuda (Ola Y), se abre «Más», donde está vincularla. En la hoja no se nota —se
                    // cierra entera—; en el panel el movimiento sigue a la vista.
                    accion = if (ofreceVincularDeuda(actualizado.type, actualizado.category, actualizado.transferId)) {
                        AccionDelMovimiento.Mas
                    } else {
                        null
                    }
                    if (otros.isEmpty()) onEventChanged(actualizado)
                    else oferta = OfertaDeLote(categoria = category, movimiento = actualizado, otros = otros)
                }
                .onFailure { error = it.toUserMessage() }
        }
    }

    // A quién fue (o de quién vino) este movimiento, según el texto del banco. Solo se leen las
    // cuentas guardadas si hay algo que preguntar, y nunca para una pata de traspaso ni un saldo
    // inicial (esas ramas salen antes de pintar la fila).
    val aQuienFue = remember(event.id, event.rawPayload, event.merchant, event.description) {
        identificadorDelDestinoEn(event)
    }
    val destinosParaGuardar = rememberDestinosParaGuardar(
        hacenFalta = aQuienFue != null && !isTransferLeg(event) && !isOpeningBalance(event),
    )
    var destinoGuardadoAca by remember(event.id) { mutableStateOf<DestinoConocido?>(null) }

    // Ola Y: «¿A cuál crédito o tarjeta corresponde?» — completar en el traspaso de deuda un
    // gasto suelto ya categorizado. Aparte de `saving`/`error` (que son de la categoría) porque
    // las dos acciones pueden pasar en momentos distintos: primero se elige la categoría, y solo
    // DESPUÉS —con la fila ya recomposed con `event.category` actualizado— aparece este paso.
    var vinculando by remember(event.id) { mutableStateOf(false) }
    var errorDeVinculo by remember(event.id) { mutableStateOf<String?>(null) }

    // Vuelta 51 · «Es la cuota de un crédito»: abierta, la hoja pide el crédito.
    var eligiendoElCredito by remember(event.id) { mutableStateOf(false) }

    // Vuelta 53 · la segunda cuota del mismo período: el server contestó 409 (`CUOTA_YA_PAGADA`) y la
    // hoja pregunta si es un abono extra, con el crédito que se eligió. La pregunta va donde se tocó:
    // arriba si vino de la sugerencia, en la sección abierta si vino de «Es la cuota de un crédito».
    var cuotaYaPagada by remember(event.id) { mutableStateOf<Pair<Account, CuotaYaPagada>?>(null) }
    var desdeLaSugerencia by remember(event.id) { mutableStateOf(false) }

    // Vuelta 53 · «¿Es la cuota de Vehículo 4083?»: si el gasto se parece a la cuota de un crédito sin
    // saldar (`cuotaQueSeParece`, en `:core`). Se pide al abrir, solo si el gasto puede ser una cuota; si
    // algo falla no se sugiere nada y la fila de siempre sigue en Categoría y en «Más».
    val puedeSerCuota = puedeMarcarseCuotaDeCredito(event, cuentas)
    var cuotaSugerida by remember(event.id) { mutableStateOf<CuotaPorSaldar?>(null) }
    // Vuelta 56 · mientras se pide, el lugar: con lo que el Inicio ya leyó, si el gasto se parece a una
    // cuota, la hoja guarda el alto de la sugerencia (`cuotaPrevistaParaElDetalle`) y las acciones no
    // saltan cuando llega. Un gasto que no se parece a ninguna no reserva nada.
    val cuotaPrevista = remember(event.id, puedeSerCuota) {
        if (puedeSerCuota) cuotaPrevistaParaElDetalle(event, cuentas, DashboardDataCache.data) else null
    }
    var cargandoLaSugerencia by remember(event.id) { mutableStateOf(puedeSerCuota) }
    LaunchedEffect(event.id, puedeSerCuota) {
        cuotaSugerida = null
        try {
            if (!puedeSerCuota) return@LaunchedEffect
            val vencimientos = runCatching { Repositories.wallets.getUpcomingPayments() }.getOrNull() ?: return@LaunchedEffect
            // La cuota del período vencida y sin pago la dice `occurrences` (`upcoming` ya rodó al mes que viene).
            val ocurrencias = runCatching { Repositories.wallets.getOccurrenceStates() }.getOrDefault(emptyList())
            val ajustes = runCatching { Repositories.wallets.getUserProfile().ajustesDelPeriodo() }.getOrDefault(PeriodSettings())
            cuotaSugerida = cuotaQueSeParece(event, cuotasPorSaldar(vencimientos, cuentas, ocurrencias), ajustes)
        } finally {
            cargandoLaSugerencia = false
        }
    }

    /**
     * Vincula el gasto con [cuenta] por `vincular-deuda`. Con un crédito va marcado `esLaCuota` (vuelta
     * 53: así el server pregunta antes de saldar dos veces el mismo período); [comoAbono] es «Un abono
     * extra» —el traspaso de siempre—.
     */
    fun vincular(cuenta: Account, comoAbono: Boolean = false) {
        if (vinculando) return
        vinculando = true
        errorDeVinculo = null
        coroutine.launch {
            val result = runCatching {
                Repositories.wallets.vincularPagoDeDeuda(
                    event.id,
                    VincularPagoDeDeudaRequest(
                        debtAccountId = cuenta.id,
                        transferId = newId("tr"),
                        toEventId = newId("ev"),
                        esLaCuota = cuenta.type == AccountType.LOAN && !comoAbono,
                        comoAbono = comoAbono,
                    ),
                )
            }
            vinculando = false
            result
                .onSuccess { resultado ->
                    cuotaYaPagada = null
                    // La pata del dinero es la que comparte id con ESTE movimiento — la otra es
                    // la de la deuda, recién creada. Si por lo que sea no viene (no debería), la
                    // hoja se queda como estaba: no hay nada que romper mostrando el mismo evento.
                    resultado.patas.firstOrNull { it.id == event.id }?.let(onEventChanged)
                }
                .onFailure { e ->
                    val ya = (e as? ApiException)?.let { cuotaYaPagadaDe(it.status, it.serverMessage) }
                    if (ya != null) cuotaYaPagada = cuenta to ya else errorDeVinculo = e.toUserMessage()
                }
        }
    }

    fun aplicarElLote(o: OfertaDeLote) {
        if (saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.recategorizarEnLote(o.otros.map { it.id }, o.categoria) }
            saving = false
            // Salga bien o mal, el movimiento que el dueño abrió YA quedó cambiado: la hoja se
            // cierra igual y el error del lote se muestra en la pantalla de atrás. Dejarlo acá
            // adentro obligaría a distinguir dos éxitos parciales en la misma hoja.
            result
                .onSuccess {
                    oferta = null
                    onEventChanged(o.movimiento)
                }
                .onFailure { error = it.toUserMessage(); saving = false }
        }
    }

    // Un traspaso no se recategoriza: sacarlo de su categoría reservada lo devolvería al gasto
    // del mes —el gasto fantasma que la feature vino a matar— y dejaría a la otra pata adentro,
    // contando la mitad de un movimiento que nunca ocurrió. El server también lo rechaza (422);
    // acá se explica en vez de ofrecer una lista que iba a fallar al tocarla.
    if (isTransferLeg(event)) {
        MarcoDelMovimiento(lugar, onDismiss = onDismiss, dismissEnabled = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                // El rótulo dice QUÉ es, y no «TRASPASO» para todo: la mitad de una cuota se abre
                // por esta misma rama —es una pata de un par— y llamarla traspaso contradice al
                // renglón de Movimientos, que desde esta ola dice «Cuota de crédito».
                SheetLabel(
                    when (event.category) {
                        CUOTA_CATEGORY -> "Cuota de crédito"
                        CARD_PAYMENT_CATEGORY -> "Pago de tarjeta"
                        DESEMBOLSO_CATEGORY -> "Desembolso de crédito"
                        else -> "Traspaso"
                    },
                )
                // Vuelta de diseño 36: el título de su fila; al editar, el campo trae lo guardado.
                NombreQueSeEdita(tituloDelMovimiento(event, cuentas.firstOrNull { it.id == event.accountId }?.name)) {
                    edicionAbierta = true
                }
                Spacer(Modifier.height(Espacio.s))
                PorConfirmar(event, onError = { errorDeEdicion = it }, onConfirmado = onEventChanged)
                Spacer(Modifier.height(Espacio.m))
                // El texto de «traspaso» dice «no es un gasto ni un ingreso», y un desembolso SÍ es
                // plata que entró: para él se dice lo suyo. Vuelta 57: y una cuota, que cuenta como
                // gasto, tampoco es un traspaso: «Es la cuota de Vehículo 4083: cuenta como gasto…».
                Text(
                    porQueLaPataNoSeRecategoriza(event, cuentas),
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.testTag(TAG_POR_QUE_NO_SE_RECATEGORIZA),
                )
                Spacer(Modifier.height(Espacio.l))
                Separador(SangriaDeSeparador.Ninguna)
                Spacer(Modifier.height(Espacio.m))
                // El MONTO y el CONCEPTO de una pata sí se corrigen, por el mismo argumento con
                // el que la fecha ya se corregía acá: el monto de un par es UN hecho con dos
                // anotaciones, y el server lo cascadea a las dos por `transferId`. Lo que no se
                // puede es moverle la CUENTA a una sola mitad — la sección lo dice y no ofrece el
                // selector (ver PATA_NO_CAMBIA_DE_CUENTA).
                SeccionDelMovimiento(
                    event = event,
                    cuentas = cuentas,
                    onError = { errorDeEdicion = it },
                    onGuardado = { edicionAbierta = false; onEventChanged(it) },
                    abierto = edicionAbierta,
                    onAbiertoChange = { edicionAbierta = it },
                )
                Spacer(Modifier.height(Espacio.l))
                Separador(SangriaDeSeparador.Ninguna)
                Spacer(Modifier.height(Espacio.m))
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
                //
                // Vuelta 27 · un traspaso o una cuota que en verdad pagó varias cosas se reparte desde
                // la pata que sale: el server anula las dos y escribe las partes.
                if (sePuedeRepartirEnPartes(event, cuentas)) {
                    // Vuelta 57: con su aire respecto de «Fecha · Hoy», como las demás secciones (iba pegado).
                    Spacer(Modifier.height(Espacio.l))
                    Separador(SangriaDeSeparador.Ninguna)
                    Spacer(Modifier.height(Espacio.m))
                    SeccionDeRepartirEnPartes(evento = event, cuentas = cuentas, onRepartido = onEventChanged)
                    Spacer(Modifier.height(Espacio.m))
                }
                SeccionDeAnular(onAnular, habilitado = true)
                Spacer(Modifier.height(Espacio.xl))
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
        MarcoDelMovimiento(lugar, onDismiss = onDismiss, dismissEnabled = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SheetLabel("Saldo inicial")
                Text(
                    tituloDelMovimiento(event, cuentas.firstOrNull { it.id == event.accountId }?.name),
                    style = Movi.textos.tituloMedio,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Espacio.m))
                Text(OPENING_BALANCE_EXPLAINER, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                if (onVerCuenta != null) {
                    Spacer(Modifier.height(Espacio.m))
                    Boton(
                        texto = "Ver la cuenta",
                        onClick = onVerCuenta,
                        variante = VarianteDeBoton.Principal,
                        habilitado = true,
                        anchoCompleto = true,
                    )
                }
                Spacer(Modifier.height(Espacio.l))
                Separador(SangriaDeSeparador.Ninguna)
                Spacer(Modifier.height(Espacio.m))
                SeccionDeFecha(event = event, onFechaCambiada = onEventChanged)
                Spacer(Modifier.height(Espacio.xl))
            }
        }
        return
    }

    // **El segundo paso**, cuando hay parecidos: el movimiento ya quedó cambiado y lo único que
    // falta es decidir por los otros. Reemplaza el contenido de la hoja en vez de abrir otra
    // encima —una hoja no puede abrir otra sobre sí misma— y cerrarla por afuera equivale a «solo
    // este», que es la opción conservadora.
    oferta?.let { o ->
        // En el panel la pregunta no se va sola con la hoja: se cierra al contestarla.
        val terminar = {
            oferta = null
            onEventChanged(o.movimiento)
        }
        MarcoDelMovimiento(lugar, onDismiss = terminar, dismissEnabled = !saving) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SheetLabel("¿Y los parecidos?")
                Text(
                    tituloDeLaOferta(o),
                    style = Movi.textos.tituloMedio,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "Movi los reconoce como el mismo lugar. Cámbialos también y no tienes que abrirlos uno por uno.",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                )
                Spacer(Modifier.height(Espacio.m))
                // Se listan todos los que se van a mover, con la categoría que tienen hoy: una
                // confirmación sobre cifras propias que no deja ver qué cambia no es una
                // confirmación. Por eso el lote está topeado (ver MAXIMO_DE_PARECIDOS).
                o.otros.forEach { otro ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.xs)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tituloDelMovimiento(otro), style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${etiquetaDeFecha(fechaDeEpoch(otro.timestamp), hoyEnAppZone())} · ${otro.category}",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                            )
                        }
                        Cifra(abs(otro.amount), moneda = otro.currency, tono = TonoDeCifra.Apagado)
                    }
                }
                Spacer(Modifier.height(Espacio.m))
                Boton(
                    texto = if (saving) "Cambiando…" else "Sí, ponlos en «${o.categoria}»",
                    onClick = { aplicarElLote(o) },
                    variante = VarianteDeBoton.Secundario,
                    habilitado = !saving,
                    anchoCompleto = true,
                )
                Spacer(Modifier.height(Espacio.xs))
                Boton(
                    texto = "No, solo este",
                    onClick = { terminar() },
                    variante = VarianteDeBoton.Fantasma,
                    habilitado = !saving,
                    anchoCompleto = true,
                )
                Spacer(Modifier.height(Espacio.l))
            }
            BarraDeError(error)
        }
        return
    }

    // El nombre que el server le acaba de poner al guardar su tercero desde esta hoja (misma regla
    // que el server, `NombreDelTercero.kt`). Al cerrar, quien la abrió relee la lista.
    val renombradoAca = destinoGuardadoAca?.let { elMovimientoConElNombreDe(event, it) }
    val alCerrar: () -> Unit = { if (renombradoAca != null) onEventChanged(renombradoAca) else onDismiss() }

    // 4-oct-2026: si ya es de un tercero guardado, su nombre lleva a su ficha («Ver todo lo de
    // Caro»): lo que le enviaste, lo que te envió y sus movimientos, sin salir de acá.
    val tercero = destinoGuardadoAca ?: destinosParaGuardar.deQuienEs(aQuienFue)
        ?.takeIf { !isTransferLeg(event) && !isOpeningBalance(event) }
    // «¿De quién es la cuenta ·0756? Guardar como…» — la misma fila de «Reconciliar movimiento».
    val ofreceGuardarElTercero = tercero == null && aQuienFue != null && destinosParaGuardar.ofrece(aQuienFue, cuentas)
    // «¿Se repite?» solo si quien abrió la hoja tiene dónde poner el formulario, y solo sobre un
    // movimiento al que la pregunta le aplica (ver [puedeOfrecerseComoRecurrenteDesdeElDetalle]).
    val ofreceRepetir = onMarcarComoRecurrente != null && puedeOfrecerseComoRecurrenteDesdeElDetalle(event)
    val ofreceVincular = ofreceVincularDeuda(event.type, event.category, event.transferId)
    // Ola 2b · **las acciones son las secciones que ya existían, reubicadas.** Solo aparece la que
    // tiene algo que abrir: sin tercero no hay «Tercero», sin dónde poner el formulario no hay
    // «Repetir».
    // R3 · «Repartir»: un gasto propio pasa a un espacio y cuenta solo tu parte. Desde la vuelta de
    // diseño 17 es una acción de la fila (con el ícono de Espacios), no una tarjeta debajo de la
    // categoría de cada gasto.
    val espacios = com.jvillada.movi.ui.espacios.rememberEspaciosParaRepartir(event)
    val ofreceRepartir = com.jvillada.movi.ui.espacios.ofreceRepartir(event, espacios)
    // Vuelta 27 · «Repartir en partes»: un gasto que pagó varias cosas se anula y queda en partes.
    val ofreceEnPartes = sePuedeRepartirEnPartes(event, cuentas)
    val acciones = accionesDelMovimiento(
        ofreceRepetir = ofreceRepetir,
        ofreceRepartir = ofreceRepartir,
        conTercero = tercero != null || ofreceGuardarElTercero,
        // Vuelta 53: «Es la cuota de un crédito» también vive en «Más».
        conMas = onAnular != null || ofreceVincular || ofreceEnPartes || puedeSerCuota,
    )

    /** Vuelta 53 · «La cuota de octubre de X ya está pagada. ¿Este pago es un abono extra?» */
    @Composable
    fun PreguntaDelAbonoExtra() {
        val (credito, ya) = cuotaYaPagada ?: return
        Spacer(Modifier.height(Espacio.xs))
        ConfirmacionEnLinea(
            pregunta = preguntaDeLaCuotaYaPagada(ya),
            detalle = QUE_PASA_CON_EL_ABONO_EXTRA,
            textoConfirmar = UN_ABONO_EXTRA,
            ocupado = vinculando,
            onConfirmar = { vincular(credito, comoAbono = true) },
            onCancelar = { cuotaYaPagada = null },
            varianteConfirmar = VarianteDeBoton.Principal,
            modifier = Modifier.testTag(TAG_CUOTA_YA_PAGADA),
        )
    }

    /**
     * Vuelta 51 · «Es la cuota de un crédito» (y desde la vuelta 53 también en «Más»): elegido el
     * crédito, hace lo de la Ola Y —`vincular-deuda` con las mismas dos patas—.
     */
    @Composable
    fun EsLaCuotaDeUnCredito() {
        CategoryRow(
            name = ES_LA_CUOTA_DE_UN_CREDITO,
            selected = eligiendoElCredito,
            enabled = !saving && !vinculando,
            onClick = { eligiendoElCredito = !eligiendoElCredito },
            subtitle = "Elige el crédito: queda como su cuota y baja la deuda",
            compacto = true,
        )
        if (eligiendoElCredito) {
            Spacer(Modifier.height(Espacio.xs))
            SelectorDeCuentaDeDeuda(
                cuentas = cuentas.filter { it.type == AccountType.LOAN },
                seleccionada = null,
                onSeleccionar = { credito -> credito?.let { desdeLaSugerencia = false; vincular(it) } },
                titulo = "¿De cuál crédito?",
                modifier = Modifier.testTag(TAG_ELEGIR_EL_CREDITO),
            )
            if (vinculando) {
                Spacer(Modifier.height(Espacio.xs))
                Text("Vinculando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            errorDeVinculo?.takeIf { !desdeLaSugerencia }?.let {
                Spacer(Modifier.height(Espacio.xs))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
            if (!desdeLaSugerencia) PreguntaDelAbonoExtra()
        }
    }
    val repartirEnMas = repartirVaEnMas(ofreceRepartir, acciones)
    val reducido = LocalMovimientoReducido.current
    fun alternar(a: AccionDelMovimiento) {
        accion = if (accion == a) null else a
    }

    MarcoDelMovimiento(lugar, onDismiss = alCerrar, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            // ── Lo que se lee primero: quién, cuánto, cuándo y en qué categoría. ──
            EncabezadoDelMovimiento(
                evento = renombradoAca ?: event,
                cuentas = cuentas,
                onEditar = { accion = AccionDelMovimiento.Nota },
                espacios = espacios,
            )
            Spacer(Modifier.height(Espacio.s))
            ChipDeCategoria(
                nombre = event.category,
                abierto = accion == AccionDelMovimiento.Categoria,
                habilitado = !saving,
                onClick = { alternar(AccionDelMovimiento.Categoria) },
            )
            if (enEsteComercio != null) {
                Spacer(Modifier.height(Espacio.xs))
                LineaEnEsteComercio(enEsteComercio, onVer = onVerElComercio?.let { ver -> { ver(enEsteComercio.busqueda) } })
            }
            // Un gasto ya repartido dice dónde y cuánto es tuyo: eso es información, se ve al abrir.
            if (event.espacioId != null) {
                Spacer(Modifier.height(Espacio.s))
                com.jvillada.movi.ui.espacios.BloqueDeRepartido(evento = event, espacios = espacios, onCambiado = onEventChanged)
            }
            // Lo que espera una decisión no se esconde detrás de una acción: confirmarlo es lo que
            // el dueño vino a hacer cuando abre un aviso pendiente.
            if (event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                Spacer(Modifier.height(Espacio.m))
                PorConfirmar(event, onError = { errorDeEdicion = it }, onConfirmado = onEventChanged)
            }
            // «¿De quién es la cuenta ·0756?» también es una pregunta pendiente: se ve al abrir, en
            // una línea, con la misma condición de siempre (nombra una cuenta o llave que nadie
            // tiene guardada). Tocarla abre «Tercero». Con el tercero ya conocido no se ve nada acá.
            // Con «Tercero» abierto se calla: la fila completa ya está debajo.
            if (ofreceGuardarElTercero && aQuienFue != null && accion != AccionDelMovimiento.Tercero) {
                Spacer(Modifier.height(Espacio.s))
                PreguntaDelTercero(
                    pregunta = preguntaDelDestino(aQuienFue),
                    onAbrir = { accion = AccionDelMovimiento.Tercero },
                )
            }
            // Ola 15 · la pata huérfana se explica sola, acá y no en la fila: la fila no tiene
            // lugar, y esta hoja es donde el dueño llega justo cuando se pregunta qué es.
            if (isOrphanedTransferLeg(event)) {
                Spacer(Modifier.height(Espacio.m))
                SheetLabel("Era un traspaso")
                Text(ORPHANED_LEG_EXPLAINER, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            }
            // Vuelta 53 · «¿Es la cuota de Vehículo 4083? · Sí, es la cuota», arriba de las acciones. Con la
            // pregunta del abono abierta se calla: lo que se decide es eso.
            val sugerida = cuotaSugerida
            val lugarGuardado = if (sugerida == null && cargandoLaSugerencia) cuotaPrevista else null
            if (lugarGuardado != null) {
                Spacer(Modifier.height(Espacio.s))
                SugerenciaDeLaCuota(
                    pregunta = preguntaDeLaCuotaQueSeParece(lugarGuardado),
                    detalle = "Cuota de ${formatMoney(lugarGuardado.cuota, lugarGuardado.moneda)}",
                    ocupado = false,
                    onSi = {},
                    reserva = true,
                )
            }
            if (sugerida != null && !(desdeLaSugerencia && cuotaYaPagada != null)) {
                Spacer(Modifier.height(Espacio.s))
                SugerenciaDeLaCuota(
                    pregunta = preguntaDeLaCuotaQueSeParece(sugerida),
                    detalle = "Cuota de ${formatMoney(sugerida.cuota, sugerida.moneda)}",
                    ocupado = vinculando && desdeLaSugerencia,
                    onSi = {
                        cuentas.firstOrNull { it.id == sugerida.creditoId }?.let {
                            desdeLaSugerencia = true
                            vincular(it)
                        }
                    },
                )
            }
            if (desdeLaSugerencia) {
                PreguntaDelAbonoExtra()
                errorDeVinculo?.let {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }
            }
            Spacer(Modifier.height(Espacio.m))
            FilaDeAcciones(acciones = acciones, abierta = accion, onElegir = ::alternar)

            // ── La sección abierta, debajo de la fila. Cada una guarda como siempre. ──
            Column(modifier = Modifier.fillMaxWidth().animateContentSize(Movimiento.alEntrar(reducido))) {
                when (accion) {
                    AccionDelMovimiento.Categoria -> {
                        Spacer(Modifier.height(Espacio.s))
                        Separador(SangriaDeSeparador.Ninguna)
                        Spacer(Modifier.height(Espacio.s))
                        SheetLabel("Categoría · ${event.category}")
                        // Ola N: la fila de texto con la categoría actual queda SOLO cuando la
                        // cuadrícula no tiene ninguna celda para marcar: una categoría escondida, o
                        // importada de un extracto y fuera de cualquier catálogo. Sin esta fila esos
                        // dos casos se quedarían sin ninguna marca, ni visual ni de TalkBack.
                        //
                        // `remember`: es el mismo cálculo que hace [SelectorDeCategoria] por dentro.
                        val laActualTieneCeldaPropia = remember(event.category, event.type, categoryPrefs) {
                            contenidoDelSelectorDeCategoria(
                                busqueda = "",
                                tipo = event.type,
                                usadas = UsedCategoriesCache.used,
                                prefs = categoryPrefs,
                                usos = UsedCategoriesCache.usosRecientes,
                            ).celdas.any { esLaCeldaElegida(it, event.category) }
                        }
                        if (!laActualTieneCeldaPropia) {
                            CategoryRow(name = event.category, selected = true, enabled = false, onClick = {}, compacto = true)
                            Spacer(Modifier.height(Espacio.xs))
                        }
                        // Ola L · **Una sola forma de elegir categoría**: la cuadrícula de siempre,
                        // con la búsqueda arriba y las frecuentes primero, solo las del tipo del
                        // movimiento. Una celda toca y guarda; «Crear "…"» crea una nueva.
                        //
                        // La guarda de reservadas se queda: el selector no ofrece ninguna, pero esta
                        // hoja es la puerta del `PUT` y no confía en eso.
                        SelectorDeCategoria(
                            elegida = event.category,
                            onElegir = { elegida ->
                                if (!saving && ofreceCategoriaEscritaAMano(elegida, event.category)) choose(elegida.trim())
                            },
                            tipo = event.type,
                            usadas = UsedCategoriesCache.used,
                            prefs = categoryPrefs,
                            usos = UsedCategoriesCache.usosRecientes,
                        )
                        // Ola 2b · «Pago de tarjeta» va AL FINAL, después de las del tipo del
                        // movimiento: es una especial, y encabezar el selector de un almuerzo con
                        // ella era ofrecer primero lo que casi nunca se busca. Mismas condiciones de
                        // siempre ([puedeMarcarsePagoDeTarjeta]); sigue fuera de la cuadrícula
                        // porque es reservada (nada reservado se ofrece ahí).
                        if (event.category != CARD_PAYMENT_CATEGORY && puedeMarcarsePagoDeTarjeta(event.type, categoryPrefs)) {
                            Spacer(Modifier.height(Espacio.xs))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.xs))
                            CategoryRow(
                                name = CARD_PAYMENT_CATEGORY,
                                selected = false,
                                enabled = !saving,
                                onClick = { choose(CARD_PAYMENT_CATEGORY) },
                                subtitle = "Deja de contar en tus gastos del mes: la compra ya se contó al usar la tarjeta",
                                compacto = true,
                            )
                        }
                        // Vuelta 51 · «Es la cuota de un crédito», junto a «Pago de tarjeta»: desde que
                        // «Cuota de crédito» no se elige en el selector, esta es la puerta para convertir
                        // en cuota un gasto suelto (el SMS o el correo del pago). Elegido el crédito, hace
                        // lo de la Ola Y: `vincular-deuda` con las mismas dos patas.
                        if (puedeSerCuota) {
                            Spacer(Modifier.height(Espacio.xs))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.xs))
                            EsLaCuotaDeUnCredito()
                        }
                        Spacer(Modifier.height(Espacio.xxs))
                        EnlaceAdministrarCategorias()
                    }
                    AccionDelMovimiento.Nota -> {
                        Spacer(Modifier.height(Espacio.s))
                        Separador(SangriaDeSeparador.Ninguna)
                        Spacer(Modifier.height(Espacio.s))
                        // El monto, la cuenta y el concepto: la misma sección, ya abierta (tocar la
                        // acción ES pedir el editor). «Cerrar» la vuelve a plegar.
                        SeccionDelMovimiento(
                            event = event,
                            cuentas = cuentas,
                            onError = { errorDeEdicion = it },
                            onGuardado = { accion = null; onEventChanged(it) },
                            abierto = true,
                            onAbiertoChange = { abierto -> if (!abierto) accion = null },
                        )
                    }
                    AccionDelMovimiento.Fecha -> {
                        Spacer(Modifier.height(Espacio.s))
                        Separador(SangriaDeSeparador.Ninguna)
                        Spacer(Modifier.height(Espacio.s))
                        SeccionDeFecha(
                            event = event,
                            onFechaCambiada = { accion = null; onEventChanged(it) },
                            abiertaDeEntrada = true,
                        )
                    }
                    AccionDelMovimiento.Repetir -> if (ofreceRepetir && onMarcarComoRecurrente != null) {
                        Spacer(Modifier.height(Espacio.s))
                        Separador(SangriaDeSeparador.Ninguna)
                        Spacer(Modifier.height(Espacio.s))
                        SeccionEstoSeRepite(
                            event = event,
                            onError = { errorDeEdicion = it },
                            onAbrirFormulario = onMarcarComoRecurrente,
                            onEditarRecurrente = onEditarRecurrente,
                            onGuardado = onEventChanged,
                        )
                    }
                    AccionDelMovimiento.Repartir -> if (ofreceRepartir) {
                        Spacer(Modifier.height(Espacio.s))
                        Separador(SangriaDeSeparador.Ninguna)
                        Spacer(Modifier.height(Espacio.s))
                        com.jvillada.movi.ui.espacios.SeccionDeRepartir(evento = event, espacios = espacios, onCambiado = onEventChanged)
                    }
                    AccionDelMovimiento.Tercero -> {
                        if (tercero != null) {
                            Spacer(Modifier.height(Espacio.s))
                            FilaDelTercero(
                                tercero = tercero,
                                recienGuardado = destinoGuardadoAca != null,
                            )
                        } else if (aQuienFue != null && ofreceGuardarElTercero) {
                            Spacer(Modifier.height(Espacio.s))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.s))
                            // Guardarla no toca este movimiento: desde ahí «Cuentas de otros» lo
                            // cuenta, porque el texto del banco no se reescribe nunca.
                            FilaGuardarElDestino(
                                identificador = aQuienFue,
                                nombreSugerido = nombreParaLaFila(aQuienFue, event.merchant),
                                textoDelAviso = event.rawPayload,
                                destinos = destinosParaGuardar.guardados.orEmpty(),
                                onGuardado = { guardado ->
                                    destinosParaGuardar.alGuardar(guardado)
                                    destinoGuardadoAca = guardado
                                    // 20-oct-2026: el server ya le puso su nombre a este movimiento. En
                                    // el panel se relee ya; en la hoja se muestra acá (ver
                                    // [renombradoAca]) y se relee al cerrarla.
                                    if (lugar == LugarDelMovimiento.Panel) {
                                        elMovimientoConElNombreDe(event, guardado)?.let(onEventChanged)
                                    }
                                },
                            )
                        }
                    }
                    AccionDelMovimiento.Mas -> {
                        // «Repartir» que no cupo en la fila (con «Tercero» ya son siete acciones).
                        if (repartirEnMas) {
                            Spacer(Modifier.height(Espacio.s))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.s))
                            com.jvillada.movi.ui.espacios.SeccionDeRepartir(evento = event, espacios = espacios, onCambiado = onEventChanged)
                        }
                        // Ola Y: «¿A cuál crédito o tarjeta corresponde?», solo con las dos
                        // categorías de deuda sobre un gasto que todavía no es la mitad de un
                        // traspaso — ver `ofreceVincularDeuda`.
                        if (ofreceVincular) {
                            Spacer(Modifier.height(Espacio.s))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.s))
                            SelectorDeCuentaDeDeuda(
                                // Vuelta 58: primero las del tipo que pide la categoría (las tarjetas, en «Pago de tarjeta»).
                                cuentas = deudasConLasDeLaCategoriaPrimero(cuentas, event.category),
                                seleccionada = null,
                                onSeleccionar = { elegida -> elegida?.let { desdeLaSugerencia = false; vincular(it) } },
                            )
                            if (vinculando) {
                                Spacer(Modifier.height(Espacio.xs))
                                Text("Vinculando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            }
                            errorDeVinculo?.takeIf { !desdeLaSugerencia }?.let {
                                Spacer(Modifier.height(Espacio.xs))
                                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                            }
                            if (!desdeLaSugerencia) PreguntaDelAbonoExtra()
                        }
                        // Vuelta 53: quien busca cómo marcar un gasto como la cuota lo busca acá, en «Más».
                        if (puedeSerCuota) {
                            Spacer(Modifier.height(Espacio.s))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.s))
                            EsLaCuotaDeUnCredito()
                        }
                        if (ofreceEnPartes) {
                            Spacer(Modifier.height(Espacio.s))
                            Separador(SangriaDeSeparador.Ninguna)
                            Spacer(Modifier.height(Espacio.s))
                            SeccionDeRepartirEnPartes(evento = event, cuentas = cuentas, onRepartido = onEventChanged)
                        }
                        SeccionDeAnular(onAnular, habilitado = !saving)
                    }
                    null -> Unit
                }
            }

            if (saving) {
                Spacer(Modifier.height(Espacio.s))
                Text("Guardando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            error?.let {
                Spacer(Modifier.height(Espacio.s))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
            Spacer(Modifier.height(Espacio.l))
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
    Spacer(Modifier.height(Espacio.l))
    Separador(SangriaDeSeparador.Ninguna)
    Spacer(Modifier.height(Espacio.xs))
    Boton(
        texto = "Anular este movimiento",
        onClick = onAnular,
        variante = VarianteDeBoton.Peligro,
        habilitado = habilitado,
        anchoCompleto = true,
    )
    Spacer(Modifier.height(Espacio.xs))
    // «Anular», no «borrar»: el movimiento no se pierde, deja de contar. Decirlo acá evita que
    // alguien no lo toque por miedo a perder el registro.
    Text(
        text = "Deja de contar en tus cifras. El registro no se pierde.",
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
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
            SheetLabel("Pagos de tarjeta sin marcar")
            Text(
                "Marcarlos como \"$CARD_PAYMENT_CATEGORY\" evita contar esta plata dos veces: " +
                    "ya se contó como gasto el día que se compró con la tarjeta.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Espacio.s))

            if (remaining.isEmpty()) {
                Text("Ya no quedan pagos por confirmar.", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            }

            remaining.forEachIndexed { i, event ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Medida.altoDeFilaDoble).padding(vertical = Espacio.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tituloDelMovimiento(event),
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.texto,
                        )
                        Spacer(Modifier.height(Espacio.xxs))
                        Text("hoy: ${event.category}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    }
                    Spacer(Modifier.width(Espacio.xs))
                    // Vuelta de diseño 19: la `Cifra` del sistema (letra tabular, «Ocultar los saldos»).
                    Cifra(event.amount, modifier = Modifier.padding(end = Espacio.xs))
                    val isSavingThis = savingId == event.id
                    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                        Boton(
                            texto = if (isSavingThis) "…" else "No es",
                            onClick = { dismiss(event) },
                            variante = VarianteDeBoton.Secundario,
                            tamano = TamanoDeBoton.S,
                            habilitado = savingId == null,
                        )
                        Boton(
                            texto = if (isSavingThis) "Marcando…" else "Marcar",
                            onClick = { confirm(event) },
                            tamano = TamanoDeBoton.S,
                            habilitado = savingId == null,
                        )
                    }
                }
                if (i < remaining.size - 1) Separador(SangriaDeSeparador.Ninguna)
            }

            error?.let {
                Spacer(Modifier.height(Espacio.s))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }

            Spacer(Modifier.height(Espacio.l))
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
/** El nombre del movimiento, tocable: abre el editor del concepto. */
@Composable
private fun NombreQueSeEdita(nombre: String, evento: FinancialEvent? = null, onEditar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ola 2a: la marca del comercio junto al título, si Movi la reconoce. Desde la Ola 2b solo la
        // usa la rama de un traspaso: el movimiento común lleva `EncabezadoDelMovimiento`, con la
        // marca a 48 dp (el selector ya no está a la vista al abrir, así que no empuja su búsqueda).
        // Sin marca no se pone nada.
        val marca = remember(evento?.merchant, evento?.description) { evento?.let(::marcaDe) }
        if (marca != null) {
            CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea)
            Spacer(Modifier.width(Espacio.s))
        }
        Text(
            nombre,
            style = Movi.textos.tituloMedio,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        Text("Editar", style = Movi.textos.cuerpo, color = Movi.colores.enlace)
    }
}

/**
 * **«Por confirmar» con salida.** Un movimiento que entró solo (extracto, carga del banco) llegaba
 * acá desde el chip «Por confirmar» y la hoja solo dejaba mirarlo: no había forma de confirmarlo, así
 * que seguía pendiente y fuera de «Gastos» e «Ingresos». Solo aparece si está pendiente.
 */
@Composable
private fun PorConfirmar(event: FinancialEvent, onError: (String?) -> Unit, onConfirmado: (FinancialEvent) -> Unit) {
    if (event.reconciliationStatus != ReconciliationStatus.UNCONFIRMED) return
    val coroutine = rememberCoroutineScope()
    var confirmando by remember(event.id) { mutableStateOf(false) }
    SheetLabel("Por confirmar")
    Text(
        "Entró solo y todavía no cuenta en tus gastos ni ingresos. Revisa el monto, la categoría y el " +
            "nombre, y confírmalo.",
        style = Movi.textos.cuerpo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Espacio.s))
    Boton(
        texto = if (confirmando) "Confirmando…" else "Confirmar",
        onClick = {
                confirmando = true
                onError(null)
                coroutine.launch {
                    runCatching { Repositories.wallets.confirmEvent(event.id) }
                        .onSuccess { onConfirmado(it) }
                        .onFailure { onError(it.toUserMessage()) }
                    confirmando = false
                }
        },
        variante = VarianteDeBoton.Principal,
        habilitado = !confirmando,
        anchoCompleto = true,
    )
}

@Composable
private fun SeccionDelMovimiento(
    event: FinancialEvent,
    cuentas: List<Account>,
    onError: (String?) -> Unit,
    onGuardado: (FinancialEvent) -> Unit,
    /**
     * Si el editor está abierto. **Izado a la hoja** para que tocar el NOMBRE de arriba lo abra:
     * el dueño volvió a decir «sigo sin poder editar los nombres de los movimientos» después de que
     * el rótulo ya nombraba el concepto. Lo que se toca para renombrar es el nombre, no un «Cambiar»
     * al lado del monto.
     */
    abierto: Boolean,
    onAbiertoChange: (Boolean) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val esPataDeUnPar = event.transferId != null

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
    SheetLabel("Monto, cuenta y concepto")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !guardando) { onAbiertoChange(!abierto) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // El monto que encabeza la hoja, en la `Cifra` de una fila (vuelta de diseño 21: era un
            // `Text` en `titulo` con `tnum`, sin «Ocultar los saldos» y en la letra de la interfaz).
            Cifra(abs(event.amount), moneda = event.currency)
            // El nombre de la cuenta y no su id: si la lista todavía no llegó no se inventa nada.
            cuentaActual?.let {
                Text(it.name, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        Text(
            text = if (abierto) "Cerrar" else "Cambiar",
            style = Movi.textos.cuerpo,
            color = Movi.colores.enlace,
        )
    }

    if (!abierto) return

    Spacer(Modifier.height(Espacio.s))
    CampoDeMonto(
        monto, { monto = it },
        rotulo = "Monto",
        moneda = if (event.currency == "COP") "$" else "US$",
    )

    Spacer(Modifier.height(Espacio.m))
    // Este campo llega prellenado con la descripción del movimiento, así que reescribirlo entero
    // con el teclado (⌘A, que el [Campo] trae) es el gesto normal, no el raro.
    Campo(
        concepto,
        // El tope es el de la COLUMNA, no uno inventado más corto: con 120 aquí, abrir un
        // movimiento cuya descripción viene de un extracto largo y tocar una tecla le habría
        // recortado el texto en silencio.
        { concepto = it.take(MAX_CONCEPTO_LENGTH) },
        rotulo = "Concepto",
        guia = "Ej: Mesada de la hija",
        habilitado = !guardando,
    )

    Spacer(Modifier.height(Espacio.m))
    if (esPataDeUnPar) {
        SheetLabel("Cuenta")
        Text(
            text = cuentaActual?.name ?: "La cuenta de este movimiento",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(PATA_NO_CAMBIA_DE_CUENTA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
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
        Spacer(Modifier.height(Espacio.s))
        Text(aviso, style = Movi.textos.apoyo, color = Movi.colores.aviso)
    }

    Spacer(Modifier.height(Espacio.s))
    Boton(
        texto = when {
                guardando -> "Guardando…"
                !esValido -> "Escribe un monto y un concepto"
                !hayCambios -> "Sin cambios"
                else -> "Guardar cambios"
            },
        onClick = { guardar() },
        variante = VarianteDeBoton.Principal,
        habilitado = puedeGuardar,
        anchoCompleto = true,
    )
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
 * la resuelve [equivalenteYaAnotadoDe], que es donde está el porqué de cada una):
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
 * Hasta acá, cuando [equivalenteYaAnotadoDe] encontraba un equivalente, la sección se limitaba a
 * decir «edítalo desde Recurrentes» — una instrucción que manda a una pantalla que el dueño pidió
 * que dejara de existir como destino propio (ver el pedido en el PR que agrega esto). Ahora, si el
 * equivalente es una **REGLA**, se ofrece editarla en el momento con
 * [com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet].
 *
 * [equivalenteYaAnotadoDe] solo devuelve el NOMBRE con el que ya está anotado — no dice por cuál de
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
    onGuardado: (FinancialEvent) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    /** Está en vuelo el `PUT /repeats` — el de marcar «no se repite» o el de arrepentirse. */
    var marcando by remember(event.id) { mutableStateOf(false) }

    fun marcarQueSeRepite(seRepite: Boolean) {
        if (marcando) return
        marcando = true
        onError(null)
        coroutine.launch {
            runCatching { Repositories.wallets.updateEventRepeats(event.id, seRepite) }
                .onSuccess { onGuardado(it) }
                .onFailure { onError(it.toUserMessage()) }
            marcando = false
        }
    }

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
            // Por el EVENTO y no por su nombre: el cargo del banco dice «COMPRA NETFLIX.COM
            // BOGOTA» y la suscripción se llama «Netflix», así que comparar solo la nota dejaba
            // crear la regla duplicada. Ver [clavesDeCobroDe].
            val equivalente = equivalenteYaAnotadoDe(
                event = event,
                selloDeOcurrencia = sello?.ruleName,
                reglas = reglas,
                suscripcionesQueYaSuman = nombresDeSuscripcionesQueYaSuman(cobros),
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

    SheetLabel("¿Se repite todos los meses?")
    // **Dos estados, y el segundo existe porque el primero no tenía vuelta.** Movimientos
    // reconoce recurrentes por NOMBRE, así que una compra suelta en un comercio del que además
    // hay una suscripción quedaba marcada como recurrente para siempre: la única puerta era
    // «Sí, se repite todos los meses». Ver [FinancialEvent.noSeRepite].
    if (event.noSeRepite) {
        Text(
            text = "Marcaste que este movimiento no se repite, así que Movi no lo marca como recurrente. " +
                "Esto vale solo para este movimiento: si tienes un cobro mensual con el mismo " +
                "nombre, ese sigue igual.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Espacio.s))
        Boton(
            texto = if (marcando) "Guardando…" else "Sí se repite, después de todo",
            onClick = { marcarQueSeRepite(true) },
            variante = VarianteDeBoton.Principal,
            habilitado = !marcando,
            anchoCompleto = true,
        )
        return
    }
    Text(
        text = "Movi puede anotarlo en Plan · Pagos del mes con lo que ya tiene este movimiento: el " +
            "concepto, el monto, la categoría, la cuenta y el día. El primer recordatorio será " +
            "el mes que viene, porque este pago ya lo hiciste.",
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Espacio.s))
    Boton(
        texto = if (consultando) "Revisando…" else "Sí, se repite todos los meses",
        onClick = { intentar() },
        variante = VarianteDeBoton.Principal,
        habilitado = !consultando && !marcando,
        anchoCompleto = true,
    )
    // La otra respuesta a la misma pregunta, y va discreta: la mayoría de las veces que alguien
    // abre esta sección es para marcar, no para desmarcar.
    Spacer(Modifier.height(Espacio.s))
    Text(
        text = if (marcando) "Guardando…" else "No, este no se repite",
        style = Movi.textos.apoyoMedio,
        color = if (marcando) Movi.colores.textoApagado else Movi.colores.enlace,
        modifier = Modifier.clickable(enabled = !marcando && !consultando) { marcarQueSeRepite(false) },
    )
    // No es un error: es la respuesta correcta a la pregunta que acaba de hacer. Va acá, pegada
    // al botón que tocó, y no en la barra de errores de abajo.
    reglaExistente?.let { regla ->
        Spacer(Modifier.height(Espacio.s))
        Text(
            text = "Ya lo tienes anotado como «${regla.name}», así que este movimiento no necesita otro.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        if (onEditarRecurrente != null) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = "Editar este recurrente",
                style = Movi.textos.apoyoMedio,
                color = Movi.colores.enlace,
                modifier = Modifier.clickable { onEditarRecurrente(regla) },
            )
        }
    }
    nombreSinReglaQueEditar?.let { nombre ->
        Spacer(Modifier.height(Espacio.s))
        Text(
            text = "Ya lo tienes anotado como «$nombre» (una suscripción confirmada), así que " +
                "este movimiento no necesita otro recurrente.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
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
    SheetLabel("Cuenta")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medida.objetivoTactil)
            .clip(RoundedCornerShape(Radio.s))
            .background(Movi.colores.capa2)
            .clickable(enabled = enabled && elegibles.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = Espacio.s, vertical = Espacio.s),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Sin la lista de cuentas no se sabe el nombre — se dice, en vez de inventarlo o
                // de mostrar el id. El monto y el concepto se siguen pudiendo guardar igual.
                text = seleccionada?.name ?: "No pudimos cargar tus cuentas",
                style = Movi.textos.cuerpo,
                color = if (seleccionada != null) Movi.colores.texto else Movi.colores.textoMedio,
                modifier = Modifier.weight(1f),
            )
            if (elegibles.isNotEmpty()) {
                Text(if (abierto) "Cerrar" else "Elegir", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
    if (ocultasPorMoneda > 0) {
        Spacer(Modifier.height(Espacio.xs))
        Text(
            text = "Solo se listan tus cuentas en $moneda: un movimiento no cambia de moneda al " +
                "cambiar de cuenta.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
    }
    if (abierto && elegibles.isNotEmpty()) {
        Spacer(Modifier.height(Espacio.xs))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radio.s))
                .background(Movi.colores.capa2),
        ) {
            elegibles.forEach { cuenta ->
                val elegida = cuenta.id == seleccionada?.id
                Text(
                    text = cuenta.name,
                    style = if (elegida) Movi.textos.cuerpo else Movi.textos.cuerpoRegular,
                    color = Movi.colores.texto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (elegida) Modifier.background(fondoDeLaFilaElegida(Movi.colores)) else Modifier)
                        .clickable { onPick(cuenta.id) }
                        .heightIn(min = Medida.objetivoTactil)
                        .padding(horizontal = Espacio.s, vertical = Espacio.s),
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
    /** Ola 2b: desde la acción «Fecha» el selector ya viene abierto — tocarla ES pedirlo. */
    abiertaDeEntrada: Boolean = false,
) {
    val coroutine = rememberCoroutineScope()
    val hoy = remember { hoyEnAppZone() }
    val actual = remember(event.timestamp) { fechaDeEpoch(event.timestamp) }
    var abierto by remember { mutableStateOf(abiertaDeEntrada) }
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
    // El período del dueño, para que el aviso de «cambia de mes» hable del mismo mes que muestran
    // Movimientos y Presupuestos (con corte 25, el 27 de septiembre ya es octubre). Se pide al
    // abrir, igual que el sello; si falla queda el mes de calendario y el aviso sigue funcionando.
    var periodo by remember { mutableStateOf(PeriodSettings()) }
    LaunchedEffect(abierto) {
        if (abierto) {
            runCatching { Repositories.wallets.getUserProfile() }
                .onSuccess { periodo = it.ajustesDelPeriodo() }
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
            // Cerrado al guardar: en la hoja no se notaba (se cerraba entera), pero en el panel de
            // Movimientos el movimiento sigue a la vista y el selector quedaba abierto con
            // «Mover a …» habilitado hasta que llegaba la relectura.
            result.onSuccess { abierto = false; onFechaCambiada(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    SheetLabel("Fecha")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !guardando) { abierto = !abierto },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = etiquetaDeFecha(actual, hoy),
            style = Movi.textos.tituloMedio,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (abierto) "Cerrar" else "Cambiar",
            style = Movi.textos.cuerpo,
            color = Movi.colores.enlace,
        )
    }

    if (abierto) {
        Spacer(Modifier.height(Espacio.s))
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
            avisoDeCambioDeMes(actual, elegida, hoy, periodo),
        )
        avisos.forEach { aviso ->
            Spacer(Modifier.height(Espacio.s))
            Text(aviso, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        }
        Spacer(Modifier.height(Espacio.s))
        val puedeGuardar = elegida != actual && !guardando
        Boton(
            texto = when {
                    guardando -> "Guardando…"
                    elegida == actual -> "Elige otro día"
                    else -> "Mover a ${etiquetaDeFecha(elegida, hoy).lowercase()}"
                },
            onClick = { guardar() },
            variante = VarianteDeBoton.Principal,
            habilitado = puedeGuardar,
            anchoCompleto = true,
        )
        error?.let {
            Spacer(Modifier.height(Espacio.s))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
    }
}

/**
 * **El segundo paso de [ChangeCategorySheet]**: el movimiento que el dueño abrió ya quedó en
 * [categoria], y estos [otros] son del mismo destinatario y están en otra.
 *
 * Es un estado y no dos variables sueltas porque las tres cosas nacen y mueren juntas: mientras
 * haya oferta, la hoja muestra la pregunta y nada más.
 */
internal data class OfertaDeLote(
    val categoria: String,
    /** El movimiento ya actualizado, para devolverlo al cerrar elija lo que elija. */
    val movimiento: FinancialEvent,
    val otros: List<FinancialEvent>,
)

/**
 * **Cuáles de los parecidos cambiarían de verdad.** El server manda los del mismo destinatario con
 * la categoría que tienen hoy; los que ya están en la que el dueño acaba de elegir no se ofrecen —
 * preguntar por un cambio que no cambia nada gasta la única atención que esta pregunta tiene.
 */
internal fun parecidosQueCambiarian(parecidos: List<FinancialEvent>, categoria: String): List<FinancialEvent> =
    parecidos.filter { it.category != categoria }

/**
 * «4 movimientos más de Zelo Group». En singular cuando es uno solo: «1 movimientos» es de las
 * cosas que hacen que una app se sienta escrita por una máquina.
 */
internal fun tituloDeLaOferta(oferta: OfertaDeLote): String {
    val cuantos = oferta.otros.size
    val nombre = oferta.movimiento.description.trim().ifBlank { "este lugar" }
    return if (cuantos == 1) "Hay 1 movimiento más de «$nombre»"
    else "Hay $cuantos movimientos más de «$nombre»"
}
