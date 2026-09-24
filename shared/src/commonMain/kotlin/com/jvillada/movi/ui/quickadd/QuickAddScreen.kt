package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.CuentaMasUsadaCache
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.MemoriaDeCategoriasCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.CATEGORY_RESERVED_SHORT
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import com.jvillada.movi.shared.model.CuentasDelPicker
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.datetime.LocalDate
import com.jvillada.movi.ui.fecha.timestampParaFecha

/**
 * Cuánto de una fila de la hoja puede ocupar el valor de la derecha (ver `rightMaxFraction` en
 * `CardRow`, donde está el porqué). 55 % deja ~167 dp para el valor y ~136 dp para la etiqueta y
 * su aviso a 375 dp — medido: «Bancolombia Ahorros» entra entero, «Última usada» también, y un
 * nombre más largo se corta con «…» en vez de partir la etiqueta letra por letra.
 */
private const val FRACCION_VALOR_FILA = 0.55f

/**
 * El alto MÍNIMO de la fila de chips de categorías frecuentes (ver [CategoriaChipsRow]): a letra
 * normal la fila mide esto y nada más, así que no se mueve bajo el dedo; con la escala de letra de
 * Movi agrandada crece lo justo para que el chip entre entero (ver el porqué en
 * [CategoriaChipsRow]). No hay un token de
 * `Movi.*` para alturas de fila (`Tokens.kt` solo tiene `espacios`, `formas`, `textos` y
 * `colores`): es un tamaño de componente, no un espacio ni una forma, así que queda como literal
 * — mismo criterio que `ALTO_BARRA_INFERIOR` en los tests de esta hoja.
 */
private val ALTO_FILA_DE_CHIPS = 40.dp

/** La X del encabezado de un sub-picker. Ver el porqué en [PickerHeader]. */
internal const val TAG_CERRAR_SUB_PICKER = "quickadd:cerrar-sub-picker"

/** El campo de texto del sub-picker «Nota» — para encontrarlo en una prueba (Task 5). */
internal const val TAG_CAMPO_DE_NOTA = "quickadd:campo-de-nota"

/**
 * **En qué moneda está la plata de esta cuenta** — la del movimiento que se anote contra ella.
 *
 * Cae en pesos cuando la cuenta no está en la lista, que es el único caso en que esta hoja no
 * sabe la moneda: `getAccounts()` falló y la hoja se abrió con un `presetAccountId` (ver el
 * comentario «B3» del `walletLabel`). Ahí el default no miente más de lo que ya mentía: `"COP"` es
 * el default del modelo y, como `encodeDefaults` está apagado, la clave ni siquiera viaja — el
 * server la completa con la de la cuenta, que es lo que hacía antes de este arreglo para TODOS los
 * movimientos.
 */
internal fun monedaDeLaCuenta(
    cuentas: List<com.jvillada.movi.shared.model.Account>,
    cuentaId: String?,
): String = cuentas.firstOrNull { it.id == cuentaId }?.currency ?: MONEDA_POR_DEFECTO

/** La del modelo (`FinancialEvent.currency`), repetida acá para poder nombrarla. */
private const val MONEDA_POR_DEFECTO = "COP"

/**
 * **El movimiento que esta hoja va a guardar.** Función aparte y pura para poder afirmar sin
 * pantalla lo que antes solo existía adentro de `save()` — sobre todo **la moneda**, que es lo que
 * decide si «500.000» son pesos o dólares.
 *
 * Lo que arregla: el evento se armaba sin `currency`, o sea con el default `"COP"` del modelo,
 * **aunque la cuenta elegida fuera una tarjeta en dólares** (la Master Black lo es, y es un origen
 * de primera clase en el selector de un gasto). Y como `encodeDefaults` está apagado, la clave no
 * viajaba: el server la rellenaba con la moneda de la cuenta y guardaba **US$500.000** mientras la
 * hoja había dicho «$500.000 · COP». En el teléfono quedaba peor, porque el espejo local sí
 * guardaba el `"COP"` y `deltaDelEspejo` le restaba a la columna de pesos el número crudo de un
 * gasto en dólares — exactamente el caso que esa guarda existe para evitar.
 */
internal fun movimientoDeLaHoja(
    id: String,
    cuentaId: String,
    /** Las cuentas que la hoja tiene a mano; de acá sale la moneda. Ver [monedaDeLaCuenta]. */
    cuentas: List<com.jvillada.movi.shared.model.Account>,
    tipo: TransactionType,
    monto: Long,
    categoria: String,
    nota: String,
    timestamp: Long,
): FinancialEvent = FinancialEvent(
    // El id viene de afuera, no se genera acá: un reintento tras un fallo tiene que llevar el
    // mismo, o el server no tiene cómo saber que es el mismo movimiento. Ver `idDelBorrador`.
    id = id,
    accountId = cuentaId,
    type = tipo,
    amount = monto,
    currency = monedaDeLaCuenta(cuentas, cuentaId),
    category = categoria,
    description = nota.ifBlank { categoria },
    source = EventSource.MANUAL,
    // F12: lo anotado a mano ya está confirmado por definición — "por confirmar" es solo para lo
    // que entra solo (SMS, OCR, extracto), no para lo que el usuario acaba de escribir con sus
    // propios dedos. Sin esto caía en el default UNCONFIRMED y desaparecía de "Gastos", que
    // excluye lo pendiente.
    reconciliationStatus = ReconciliationStatus.RECONCILED,
    timestamp = timestamp,
)

/**
 * La fecha que viene prellenada, o `null` si no vino ninguna **o si no se entiende**.
 *
 * Se parsea acá y no en el llamador por lo de siempre en este repo: una fecha mal formada no puede
 * ser una excepción que tumbe la hoja, y tampoco puede inventarse. `null` significa «no sé», y
 * quien llama ya tiene un default honesto para eso — hoy.
 */
internal fun fechaDelPreset(iso: String?): LocalDate? {
    val texto = iso?.trim().orEmpty()
    if (texto.isEmpty()) return null
    return runCatching { LocalDate.parse(texto) }.getOrNull()
}

/**
 * **La sugerencia de categoría por nombre (Task 5) se aplica al confirmar la nota con «Guardar
 * nota», no con cada tecla.** La nota se edita en su propio sub-picker, que tapa la fila
 * «Categoría»: una sugerencia por tecla cambiaría la categoría detrás de una pantalla que el dueño
 * no está viendo, varias veces por palabra, sin que pueda leer ninguna. Al confirmar vuelve al
 * formulario y ve de una vez la categoría que quedó y la línea «Movi la reconoce: …» que dice
 * por qué. Ver el `LaunchedEffect(note, …)` de adentro.
 *
 * @param onDismiss cerrar sin guardar (la X, el fondo, el botón atrás).
 * @param onSaved se guardó algo. Distinto de [onDismiss] a propósito: la pantalla de atrás sigue
 *   viva detrás de esta hoja (es una modal, ver `opensAsOverlay`), así que además de cerrar hay
 *   que avisarle que sus datos quedaron viejos — si no, el movimiento recién registrado no
 *   aparece y la app parece decir que no se guardó nada. Por defecto cae en [onDismiss] para que
 *   un llamador viejo siga cerrando igual.
 */
@Composable
fun QuickAddScreen(
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss,
    onNavigate: (Screen) -> Unit = {},
    presetAccountId: String? = null,
    /**
     * **Lo que ya se sabe del movimiento que se viene a anotar** — ver [Screen.QuickAdd], donde
     * está el porqué largo. Llegan del checklist del período, cuando una fila ofrece «Anotar el
     * movimiento»: la app tiene el nombre, el monto, la categoría y la fecha del recurrente a la
     * vista, y hacérselos teclear de nuevo es a la vez trabajo y la forma más fácil de que el
     * emparejamiento automático después no los reconozca.
     *
     * Son valores INICIALES, no ataduras: se escriben una vez al abrir la hoja (en el `remember`
     * de cada campo) y desde ahí manda el dueño. Un preset vacío o `null` deja el campo como
     * siempre estuvo.
     */
    presetNota: String? = null,
    presetMonto: Long? = null,
    presetCategoria: String? = null,
    /** ISO `"2026-09-05"`. Algo que no se entienda deja la fecha en hoy, que es el default. */
    presetFecha: String? = null,
    presetEsIngreso: Boolean = false,
    /**
     * Ola 9 · B: el movimiento que se acaba de guardar, para que quien sobreviva a esta hoja
     * (App.kt) pueda ofrecer convertirlo en recurrente. Se llama **después** de que el POST
     * salió bien, junto con [onSaved] — nunca antes: primero se guarda, después se ofrece.
     *
     * No se dispara en un traspaso: `RecurringRule` no modela traspasos (ver
     * `shouldOfferRecurring`), así que ni siquiera se propone la pregunta.
     */
    onSavedEvent: (FinancialEvent) -> Unit = {},
) {
    val coroutine = rememberCoroutineScope()
    // El monto viaja como cadena de dígitos, que es lo que teclea el teclado numérico: un preset
    // se escribe igual que si lo hubiera tecleado él. Un cero o un negativo no se pone —«0» dejaría
    // el botón deshabilitado con un campo que parece lleno— y se cae al vacío de siempre.
    var amount by remember { mutableStateOf(presetMonto?.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf(presetNota?.trim().orEmpty()) }
    // F35: arranca en la primera categoría predefinida de Gastos, como antes arrancaba en
    // "Mercado" — y se cambia desde la cuadrícula de [SelectorDeCategoria] (Ola B).
    //
    // Ola 10 (revisión): el valor inicial sale de [categoriaPorDefectoPara] y no de
    // `PREDEFINED_CATEGORIES.first { … }`. Con la línea vieja, esconder «Comida» en
    // «Más → Categorías» y abrir Agregar dejaba el campo diciendo **«Comida»**: un toque en
    // «Guardar movimiento», sin abrir siquiera el selector, y el gasto quedaba anotado en la
    // categoría que el dueño acababa de retirar. La pantalla donde más se equivoca es esta.
    var category by remember {
        mutableStateOf(
            // Un preset gana: viene de un recurrente que el dueño ya categorizó, y es JUSTO el dato
            // del que depende que el emparejamiento automático reconozca después este movimiento
            // (ver `esConcluyente` en el server). Una reservada no se acepta ni de preset: la hoja
            // no deja guardarla, así que ponerla sería nacer con el botón bloqueado.
            presetCategoria?.trim()?.takeIf { it.isNotEmpty() && !isReservedCategory(it) }
                ?: categoriaPorDefectoPara(
                    if (presetEsIngreso) TransactionType.INCOME else TransactionType.EXPENSE,
                    UsedCategoriesCache.used,
                    UsedCategoriesCache.prefs,
                    UsedCategoriesCache.usosRecientes,
                ),
        )
    }

    /**
     * Ola A — **el dueño eligió esta categoría con el dedo**, no la puso la app. Lo llenan tres
     * caminos: tocar un chip de frecuentes o una celda del selector de «Categoría» (ver
     * [elegirCategoriaAMano]) y un [presetCategoria] válido, que viene de un recurrente que el dueño ya categorizó — es tan «a mano» como tocar
     * un chip, solo que lo hizo en otra pantalla.
     *
     * Existe para distinguir «esto lo eligió él» de «esto lo puso la app» antes de pisarlo con
     * una sugerencia por nombre (Task 5). Se baja solo cuando la reconciliación Gasto↔Ingreso
     * reemplaza la categoría por su cuenta — ver ese `LaunchedEffect`.
     */
    var categoriaElegidaAMano by remember {
        mutableStateOf(presetCategoria?.trim()?.let { it.isNotEmpty() && !isReservedCategory(it) } == true)
    }

    /**
     * Task 5 — **la sugerencia hoy vigente** (para la línea «Movi la reconoce: …» bajo la fila
     * «Categoría»), **la categoría que había antes de que esa sugerencia la pisara** —para poder
     * volver a ella si la sugerencia desaparece porque la NOTA cambió— y **con qué tipo (Gasto o
     * Ingreso) se aplicó esa sugerencia**. `null` en los tres = no hay ninguna sugerencia aplicada
     * ahora mismo, y los tres se mueven juntos: no hay combinación válida con solo uno o dos en
     * `null`.
     *
     * **Fix round 2 — por qué hace falta el tercero.** «Volver a lo de antes» solo es correcto
     * cuando lo que hizo desaparecer la sugerencia fue la NOTA (la borró, la cambió) en la MISMA
     * pestaña — ahí «lo de antes» es la categoría de esta misma pestaña. Si lo que cambió fue la
     * PESTAÑA (Gasto→Ingreso), «lo de antes» es la categoría de la OTRA pestaña, y ponerla acá
     * cuela una categoría que puede no servir para el tipo actual — el caso real: «Fútbol»
     * (propia, usada solo en gastos) sugerida en Gasto, sube a Ingreso, dejó de sugerirse por el
     * filtro de tipo, y sin este dato se restauraba «Comida» (la que había ANTES en Gasto) sobre
     * un ingreso. Comparar [tipoDeLaSugerenciaVigente] contra el tipo de la pestaña actual es lo
     * que distingue los dos casos — ver el `LaunchedEffect` de acá abajo.
     *
     * Se limpian los tres juntos, en los tres lugares donde algo que no es "la nota en esta misma
     * pestaña" decide la categoría: [elegirCategoriaAMano] (chips y selector de «Categoría») y la
     * reconciliación de tipo cuando pisa la categoría por su cuenta.
     */
    var sugerenciaVigente by remember { mutableStateOf<com.jvillada.movi.shared.model.RecuerdoDeCategoria?>(null) }
    var categoriaAntesDeLaSugerencia by remember { mutableStateOf<String?>(null) }
    var tipoDeLaSugerenciaVigente by remember { mutableStateOf<TransactionType?>(null) }

    var accounts by remember { mutableStateOf<List<com.jvillada.movi.shared.model.Account>>(emptyList()) }
    // F10: "+ Registrar el primero" desde el detalle de una cuenta trae esa cuenta ya elegida —
    // si no existiera (borrada entre medio), el efecto de abajo cae en la última usada y, si esa
    // tampoco está, en la primera de la lista (ver [resolverCuenta]).
    var selectedAccountId by remember { mutableStateOf(presetAccountId) }
    /**
     * **Ola 11 — por qué está elegida ESTA cuenta.** Con una sola cuenta esto no cambia nada;
     * con varias, es la diferencia entre un valor por defecto que se puede leer y uno que se
     * descubre después en Movimientos. Lo usan dos cosas: el aviso de la fila «Cuenta»
     * ([avisoDeCuenta]) y la reconciliación de abajo, que **no puede pisar una elección a mano**
     * cuando la lista de cuentas se vuelve a cargar (p. ej. tras crear una cuenta desde acá).
     */
    var origenCuenta by remember {
        mutableStateOf(if (presetAccountId != null) OrigenCuenta.CONTEXTO else OrigenCuenta.NINGUNA)
    }
    /**
     * Qué pestaña está elegida y qué sub-picker está abierto, en UN solo estado y con
     * transiciones puras — ver [PickersDeLaHoja], donde está el porqué (resumen: eran tres
     * variables sueltas, una de ellas espejo de estado de [TransferBody], y el espejo se quedaba
     * pegado en `true` al salir de Traspaso con su sub-picker abierto, matando la restauración
     * del desplazamiento en las tres pestañas). Las transiciones se afirman en
     * `PickersDeLaHojaTest`, sin teléfono.
     *
     * **No lo escribas a mano: pasa siempre por [pasarA]**, que es el que graba el
     * desplazamiento en el toque — antes de que el cambio de estado vuelva a medir la hoja.
     */
    var pickers by remember {
        // Un sueldo no se paga: llega. Abrir «Anotar el movimiento» de un recurrente de ingreso en
        // la pestaña «Gasto» lo anotaría con el signo al revés, que es el error más caro que esta
        // hoja puede cometer en silencio.
        mutableStateOf(PickersDeLaHoja(typeIndex = if (presetEsIngreso) 1 else 0))
    }

    // ── Las tres medidas de la hoja, y el desplazamiento que las une ──────────────────
    //
    // `huecoVisiblePx` es lo que se VE (se mide afuera del scroll), `contenidoPx` lo que hay
    // (adentro, con altura infinita) y `bodyHeightPx` cuánto de eso es el cuerpo del editor.
    // Las tres viven acá arriba porque el modificador de la Column que se desplaza y el alto
    // fijado del sub-picker las usan de los dos lados.
    var huecoVisiblePx by remember { mutableStateOf(0) }
    var contenidoPx by remember { mutableStateOf(0) }
    var bodyHeightPx by remember { mutableStateOf(0) }
    val sheetScroll = rememberScrollState()

    /** Hay un sub-picker abierto, sea de esta pestaña o el de la de traspaso. */
    val hayPicker = pickers.hayPicker

    /** El cuerpo del editor está compuesto y medible: no hay ningún sub-picker tapándolo. */
    val cuerpoCompuesto = pickers.cuerpoCompuesto

    // Dónde estaba la hoja ANTES de abrir un sub-picker. Ver [recordarScroll].
    var scrollAntesDelPicker by remember { mutableStateOf(0) }

    /**
     * Guardar el desplazamiento — la mitad de la disciplina de la Ola 8 que el scroll podía
     * romper.
     *
     * Con la hoja quieta, abrir y cerrar «Nota» devolvía el teclado al mismo píxel porque no
     * había otro lugar donde ponerlo. Ahora la hoja se puede desplazar: si el dueño bajó hasta
     * el botón, abrió un sub-picker y lo cerró, el teclado volvería ARRIBA (el sub-picker mide
     * lo que el hueco, así que el desplazamiento se recorta a 0) y la tecla que estaba bajo su
     * dedo sería otra — exactamente el «escribías 0 y salía 8» de la Ola 8. Por eso se guarda
     * en el TOQUE y no en el efecto de abajo: para cuando el efecto corre, el recorte ya pasó y
     * el valor viejo ya no existe.
     */
    fun recordarScroll() {
        scrollAntesDelPicker = sheetScroll.value
    }

    /**
     * El único camino por el que [pickers] cambia — el embudo donde vive la mitad «guardar» de
     * la disciplina.
     *
     * Graba el desplazamiento justo en el borde «no había ningún sub-picker → ahora sí», y lo
     * graba ANTES de escribir el estado nuevo: cuando el estado cambie, la hoja se vuelve a
     * medir y el valor viejo ya no existe. Que sea un embudo y no una línea repetida en cada
     * `onClick` es a propósito: los sub-pickers se abren desde cuatro sitios (tres filas de acá
     * y el aviso de [TransferBody]) y basta que uno se olvide de grabar para que el teclado se
     * mueva bajo el dedo en ese camino y nada más — el modo de falla que esta hoja repite.
     */
    fun pasarA(siguiente: PickersDeLaHoja) {
        if (siguiente.hayPicker && !pickers.hayPicker) recordarScroll()
        pickers = siguiente
    }

    LaunchedEffect(hayPicker) {
        if (hayPicker) {
            // Que el sub-picker se vea desde su encabezado —su título y su X— y no desde la
            // mitad. Casi siempre ya está en 0 porque el alto fijado deja el contenido del
            // tamaño del hueco; esto cubre el caso en que el sub-picker es más alto que el hueco.
            sheetScroll.scrollTo(0)
        } else {
            val objetivo = scrollAntesDelPicker
            // **Ola 14: se restaura SIEMPRE, también cuando el objetivo es 0.** Antes esta rama
            // pedía `> 0`, y estaba bien mientras ningún sub-picker se pudiera desplazar: el
            // desplazamiento no cambiaba durante la visita, así que volver a 0 era volver a donde
            // ya se estaba. Desde que la lista de categorías se estira (hoy la cuadrícula de
            // [SelectorDeCategoria], sin tope, en la rama Picker.Category), el sub-picker SÍ se desplaza — el dueño baja hasta
            // «Vivienda», elige, y al cerrarse el cuerpo vuelve a ser corto: el desplazamiento
            // heredado del picker se recorta contra el `maxValue` del editor y queda en cualquier
            // lado, no en 0. O sea el teclado movido bajo el dedo, otra vez, por el camino nuevo.
            // Restaurar el 0 explícitamente es lo que lo devuelve a su sitio.
            if (objetivo > 0) {
                // Se ESPERA a que el cuerpo vuelva a medirse, no se cuenta un cuadro: si se
                // restaura antes, el valor se recorta contra el `maxValue` del sub-picker y la
                // posición se pierde para siempre. El timeout es un seguro contra colgarse si el
                // contenido quedara más corto que el objetivo — ahí se restaura lo que se pueda.
                //
                // **Ola 14, para que se lea bien: esta espera ya no espera lo mismo en todos los
                // sub-pickers.** Se escribió cuando el `maxValue` de TODOS ellos era 0 (el alto
                // fijado los dejaba del tamaño del hueco), así que la espera era la única forma
                // de no perder la posición. Con la lista de categorías estirada, el `maxValue`
                // del picker de Categoría es MAYOR que el del editor, y en ese camino la
                // condición ya se cumple en el primer cuadro: la espera no espera nada y el
                // resultado sale igual de correcto. Sigue haciendo falta para Fecha, Nota y
                // Cuenta, que sí siguen midiendo lo que el hueco.
                withTimeoutOrNull(timeMillis = 1_000) {
                    snapshotFlow { sheetScroll.maxValue }.first { it >= objetivo }
                }
            }
            sheetScroll.scrollTo(objetivo)
            // Solo se olvida el valor si de verdad se restauró. Si no, queda para que el próximo
            // toque lo pise, y el defecto queda a la vista en vez de escondido en un cero.
            if (sheetScroll.value == objetivo) scrollAntesDelPicker = 0
        }
    }
    // Ola 13 — LA FECHA DEL MOVIMIENTO, con hoy por defecto.
    //
    // Antes esto no existía y el guardado sellaba `Clock.System.now()` a secas, así que TODO caía
    // bajo «HOY» en Movimientos: el dueño anotaba de una sentada el gimnasio, el mercado, un café,
    // un almuerzo y el fútbol, y varios no habían sido hoy. El default no cambia —quien anota en
    // el momento no toca nada— pero ahora se puede corregir antes de guardar.
    //
    // `hoy` se calcula UNA vez por apertura de la hoja y se comparte: si el selector, la etiqueta
    // y el guardado preguntaran cada uno por su cuenta, una hoja abierta a las 23:59:59 podría
    // decir «Hoy» y guardar la fecha de mañana.
    val hoy = remember { hoyEnAppZone() }
    // Un preset gana sobre «hoy», y no es un detalle: el checklist ofrece anotar una fila que pudo
    // haber vencido hace dos semanas, y con la fecha de hoy ese movimiento cae en el período
    // siguiente — o sea, la fila que se venía a tildar se quedaría sin tildar igual. Una fecha que
    // no se entienda no se inventa: se cae a hoy, que es el default de siempre.
    var fecha by remember { mutableStateOf(fechaDelPreset(presetFecha) ?: hoy) }
    /**
     * **El id del movimiento que se está escribiendo. Se genera una vez por borrador, no una vez
     * por toque de «Guardar».**
     *
     * Es el mismo reflejo que `TransferDraftIds` (ver su KDoc, en `TransferForm.kt`) y nació del
     * mismo escenario: el server commitea el movimiento, la respuesta se pierde —se cortó la
     * señal, la app se fue al fondo, venció el timeout—, el dueño lee «revisa tu conexión» y
     * vuelve a tocar Guardar. Con el `newId("ev")` adentro de `save()`, el segundo intento
     * llevaba un id NUEVO, así que la única defensa del server contra el duplicado —el reenvío
     * del mismo id, que `POST /api/events` trata como «este movimiento ya está» y contesta 200—
     * no tenía de dónde agarrarse: quedaban dos gastos idénticos que él nunca anotó dos veces.
     * En el teléfono el efecto es peor, porque el espejo local también inserta por PK: dos filas.
     *
     * Se renueva **solo después de un éxito**: mientras el anterior no haya llegado, cada
     * reintento tiene que ser el mismo pedido.
     */
    var idDelBorrador by remember { mutableStateOf(newId("ev")) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var accountsRefreshKey by remember { mutableStateOf(0) }
    // «No tienes cuentas» solo se afirma cuando la lista llegó y vino vacía. Antes de eso —o si
    // la llamada falló— no se sabe, y decírselo a alguien con cinco cuentas porque el server
    // tardó es la clase de mentira que esta ola vino a sacar.
    var accountsLoaded by remember { mutableStateOf(false) }

    /**
     * **Para qué se está eligiendo la cuenta en esta pestaña**, que es lo que decide qué se
     * ofrece: de un gasto la plata SALE (efectivo, banco, tarjeta) y a un ingreso ENTRA
     * (efectivo, banco, inversión). Ver [UsoDeCuenta], donde está el criterio entero.
     *
     * Las pestañas de traspaso y cuota no usan este valor —[TransferBody] tiene sus dos listas
     * propias, que salen del mismo archivo de `:core`—, así que ahí da igual cuál de los dos
     * quede; lo que importa es que al VOLVER a Gasto o Ingreso el valor sea el de la pestaña.
     */
    val usoDeCuenta = if (pickers.typeIndex == 1) {
        UsoDeCuenta.DESTINO_DE_INGRESO
    } else {
        UsoDeCuenta.ORIGEN_DE_GASTO
    }

    /**
     * **Qué cuenta queda elegida.** Se dispara por dos motivos —llegó la lista de cuentas, o
     * cambió la pestaña— y es a propósito **una sola función con un solo efecto que la llama**:
     * el momento en que esto se convierte en dos copias es el momento en que una se arregla y la
     * otra no, que es el defecto que este repo ya se comió dos veces.
     *
     * La lista que se le pasa a [resolverCuenta] son SOLO las principales y **sin `conservar`**:
     * lo que la app elige sola nunca puede ser una cuenta que quedó detrás del «Ver todas», o la
     * hoja se abriría con una cuenta ya elegida que no está a la vista. Eso vale también para
     * [presetAccountId]: si la hoja se abrió desde el detalle del crédito del vehículo, ese
     * contexto no alcanza para poner el crédito como origen de un gasto — cae en la última usada,
     * y de ahí en la primera de la lista.
     */
    fun reconciliarCuenta(lista: List<com.jvillada.movi.shared.model.Account>, uso: UsoDeCuenta) {
        // Ola 11: la que el dueño acaba de elegir con el dedo no se toca —salvo que ya no
        // exista—, y eso incluye una que él haya sacado del «Ver todas»: fue una decisión suya,
        // y esta hoja no revoca decisiones suyas.
        val eleccionFirme = origenCuenta == OrigenCuenta.ELEGIDA && lista.any { it.id == selectedAccountId }
        if (eleccionFirme) return
        val elegida = resolverCuenta(
            cuentas = cuentasPara(lista, uso).principales,
            contexto = presetAccountId,
            ultima = LastAccountStore.lastAccountId,
            masUsada = CuentaMasUsadaCache.id,
        )
        selectedAccountId = elegida.id
        origenCuenta = elegida.origen
    }

    // **El único llamador de [reconciliarCuenta].**
    //
    // Corre cuando llega (o cambia) la lista de cuentas —el camino de siempre: antes esto vivía
    // adentro del `onSuccess` de la carga— y **también cuando cambia la pestaña**. Lo segundo no
    // es cosmético: si la última cuenta usada fue la pensión voluntaria, en «Ingreso» queda
    // elegida (un rendimiento entra ahí) y al pasar a «Gasto» ya no sirve. Sin esta segunda
    // llave, la hoja se quedaría con una cuenta que su propio selector no ofrece — que es
    // exactamente el defecto que esta rama vino a cerrar, por otra puerta.
    //
    // Se espera a que la lista haya llegado: con `accounts` vacía esto pondría la cuenta en null
    // y se llevaría puesto el `presetAccountId` con el que se abrió la hoja (ver B3 más abajo,
    // que depende de que el preset sobreviva a un `getAccounts()` fallido).
    //
    // **Anotado, no arreglado (B2 de la revisión de la Ola 11):** esto también corre después de
    // crear una cuenta desde esta misma hoja (`accountsRefreshKey++` cambia la lista), y ahí
    // puede MOVER la preselección — master conservaba la que estuviera. Pasa solo si el dueño no
    // había elegido a mano, y el cambio se ve (la fila dice el nombre nuevo con su «Por
    // defecto»), así que probablemente sea mejor así: quien acaba de crear una cuenta suele
    // querer estrenarla.
    LaunchedEffect(accounts, accountsLoaded, usoDeCuenta) {
        if (accountsLoaded) reconciliarCuenta(accounts, usoDeCuenta)
    }

    LaunchedEffect(accountsRefreshKey) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { list ->
                accountsLoaded = true
                accounts = list
            }
    }

    // Ola 10: las preferencias llegan del server (dentro del resumen del Inicio) y pueden
    // aparecer DESPUÉS de que esta hoja se compuso. Se leen como estado para que la
    // reconciliación de abajo vuelva a correr cuando lleguen — si no, la hoja abierta antes de
    // que cargaran se quedaría con lo que el dueño ya cambió.
    val categoryPrefs = UsedCategoriesCache.prefs
    val usedCategories = UsedCategoriesCache.used
    // Ola A: los usos de los últimos 60 días, para las mismas dos cosas que [categoryPrefs] —
    // llenar los chips de frecuentes y decidir el valor por defecto — y leídos igual, como estado,
    // por si el Inicio todavía no había cargado cuando se abrió esta hoja.
    val usosRecientes = UsedCategoriesCache.usosRecientes

    LaunchedEffect(pickers.typeIndex, categoryPrefs) {
        // Con categoría libre (F35) ya no hay una lista fija de la que "salirse" al cambiar de
        // tipo — pero si la actual no sirve para el tipo elegido (p. ej. "Salario" al pasar a
        // Gasto), seguir mostrándola confundiría. Una categoría propia sin nada declarado se deja
        // tal cual: no hay forma de saber si tiene sentido para el nuevo tipo.
        //
        // Ola 10 (revisión): la decisión la toma [categoriaSirveParaTipo], NO el `type` clavado
        // del catálogo. Con la versión vieja, fijar «Otros» en «Ambos» y pasar de Gasto a Ingreso
        // se la reemplazaba en silencio por «Salario» — leía el EXPENSE del catálogo e ignoraba lo
        // que el dueño acababa de decidir, que es literalmente lo que esta ola vino a habilitar.
        // Y ahora también saca del campo una categoría ESCONDIDA (o reservada), en vez de dejarla
        // ahí lista para guardarse.
        //
        // **Ventana conocida, anotada y no cerrada (A6).** Al agregar `categoryPrefs` como key,
        // este efecto ya no corre solo al cambiar de pestaña: también cuando las preferencias
        // llegan del server. Si en ese instante exacto el campo tiene escrito el nombre COMPLETO
        // de una categoría del otro tipo, se lo reemplaza mientras escribe. No se pudo reproducir
        // (las prefs llegan con el resumen del Inicio, muy antes de que se abra esta hoja), pero
        // la ventana antes no existía. Cerrarla pide distinguir «lo escribió él» de «lo puso la
        // app», que es estado nuevo en la pantalla más delicada de la app — y el precio de
        // equivocarse ahí es peor que el de esta ventana.
        //
        // La pestaña Traspaso (índice 2) queda fuera: un traspaso no tiene categoría elegible —
        // la suya es reservada— así que no hay nada que reconciliar al entrar ni al salir.
        if (pickers.typeIndex > 1) return@LaunchedEffect
        val newType = if (pickers.typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME
        if (!categoriaSirveParaTipo(category, newType, usedCategories, categoryPrefs)) {
            category = categoriaPorDefectoPara(newType, usedCategories, categoryPrefs, usosRecientes)
            // Task 5: esto acaba de pisar por su cuenta lo que hubiera puesto una sugerencia —
            // seguir mostrando «Movi la reconoce: …» sobre una categoría que ya no es la suya
            // mentiría, y "volver a lo de antes" tampoco tendría sentido (lo de antes era de la
            // OTRA pestaña). Se limpia el estado en vez de arrastrarlo.
            sugerenciaVigente = null
            categoriaAntesDeLaSugerencia = null
            tipoDeLaSugerenciaVigente = null
            // Revisión final: y la categoría que quedó ya no es la que eligió el dueño — la puso
            // la app. Si la marca de «a mano» sobreviviera, la pestaña nueva nunca aceptaría una
            // sugerencia por nombre: elegir «Transporte» en Gasto, pasar a Ingreso (queda
            // «Salario») y escribir el nombre de un inquilino no sugería nada. Solo se baja ACÁ,
            // cuando la reconciliación pisó la categoría: si la elección a mano sirve para el tipo
            // nuevo y se conservó, sigue siendo de él y sigue sin pisarse.
            categoriaElegidaAMano = false
        }
    }

    // Task 5 — la primera vez que se abre «Agregar» en esta sesión: ver el KDoc de
    // [MemoriaDeCategoriasCache.cargarSiHaceFalta]. `Unit` como key: no se repite mientras la
    // hoja siga compuesta, y el propio caché es idempotente si dos hojas llegaran a competir.
    LaunchedEffect(Unit) {
        MemoriaDeCategoriasCache.cargarSiHaceFalta()
    }

    /**
     * Task 5 — **escribir el nombre sugiere la categoría.** Corre en cada cambio de [note] (la
     * nota solo cambia en este estado cuando el dueño cierra el sub-picker con «Guardar nota» —
     * ver `NoteEditor` — así que "escribir" acá es "cada nota distinta que el dueño confirmó"),
     * y también cuando llega la memoria del server o las preferencias de categoría, por si
     * cualquiera de las dos aparece DESPUÉS de que esta hoja ya se compuso con una nota puesta
     * (un preset, o el dueño escribió antes de que la memoria terminara de cargar).
     *
     * Las escondidas y las del otro tipo se filtran ACÁ, con [seOfreceParaTipo] — el mismo
     * criterio que ya usan los chips de frecuentes y el panel de sugerencias del campo (ver su
     * KDoc en `CategoryField.kt`), y NO [categoriaSirveParaTipo]: esa es permisiva con una
     * categoría propia sin tipo fijado, y una memoria de «Comida» (solo gasto, sin nada fijado)
     * se seguiría sugiriendo al anotar un ingreso. [sugerenciaPorNombre] es pura y solo sabe
     * filtrar reservadas (ver su KDoc) — por eso ese filtro no se repite acá.
     */
    LaunchedEffect(note, MemoriaDeCategoriasCache.recuerdos, categoryPrefs, pickers.typeIndex) {
        if (pickers.typeIndex > 1) return@LaunchedEffect // Traspaso y Cuota no tienen categoría.
        val tipoActual = if (pickers.typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME

        // Fix round 2 — **por qué esto va primero, y separado.** "Volver a lo de antes" cuando
        // una sugerencia desaparece solo es correcto si lo que la hizo desaparecer fue la NOTA,
        // en la MISMA pestaña donde se aplicó — ahí [categoriaAntesDeLaSugerencia] es la
        // categoría de ESTA pestaña. Si en cambio cambió la PESTAÑA desde que se aplicó (Gasto→
        // Ingreso), [categoriaAntesDeLaSugerencia] es la categoría de la OTRA pestaña, y ponerla
        // acá cuela una categoría que puede no servir para el tipo actual — el caso real:
        // «Fútbol» (propia, usada solo en gastos) sugerida en Gasto, sube a Ingreso con la nota
        // intacta, [seOfreceParaTipo] dice que ya no sirve, y sin este chequeo se restauraba
        // «Comida» (lo que había ANTES en Gasto) sobre un ingreso. Por eso, apenas la pestaña
        // cambia respecto de la que tenía la sugerencia vigente, esa sugerencia se da de baja ACÁ
        // —con el valor por defecto de la pestaña nueva, lo mismo que ya hace la reconciliación de
        // tipo para el resto de los casos— y el resto de la función corre limpio, como si no
        // hubiera habido ninguna sugerencia antes: si el mismo recuerdo también sirve para el tipo
        // nuevo, se vuelve a aplicar más abajo con un «antes» que sí es de esta pestaña.
        if (sugerenciaVigente != null && tipoDeLaSugerenciaVigente != tipoActual) {
            category = categoriaPorDefectoPara(tipoActual, usedCategories, categoryPrefs, usosRecientes)
            sugerenciaVigente = null
            categoriaAntesDeLaSugerencia = null
            tipoDeLaSugerenciaVigente = null
        }

        val recuerdosVisibles = MemoriaDeCategoriasCache.recuerdos.filter { r ->
            seOfreceParaTipo(r.categoria, tipoActual, usedCategories[r.categoria].orEmpty(), categoryPrefs)
        }
        val sugerencia = sugerenciaPorNombre(note, recuerdosVisibles)
        if (sugerencia == null) {
            // La sugerencia desapareció porque cambió la NOTA (el caso de la pestaña ya se
            // resolvió arriba) — pero solo si Movi fue quien la puso. Si el dueño ya la había
            // elegido a mano, [sugerenciaVigente] ya está en `null` (ver [elegirCategoriaAMano])
            // y acá no hay nada que deshacer.
            if (sugerenciaVigente != null) {
                categoriaAntesDeLaSugerencia?.let { category = it }
                sugerenciaVigente = null
                categoriaAntesDeLaSugerencia = null
                tipoDeLaSugerenciaVigente = null
            }
            return@LaunchedEffect
        }
        if (categoriaElegidaAMano) return@LaunchedEffect // el dueño ya eligió: no se pisa.
        if (sugerencia == sugerenciaVigente) return@LaunchedEffect // nada cambió.
        // Guarda el valor de ANTES la primera vez, no en cada re-sugerencia: si la nota pasa de
        // «Mora» a «Mora S» y las dos sugieren Fútbol, lo que había antes de la PRIMERA sigue
        // siendo lo correcto a donde volver si el dueño termina borrando todo. Con el reseteo de
        // arriba, "antes" nunca puede ser un valor de otra pestaña.
        if (categoriaAntesDeLaSugerencia == null) categoriaAntesDeLaSugerencia = category
        category = sugerencia.categoria
        sugerenciaVigente = sugerencia
        tipoDeLaSugerenciaVigente = tipoActual
    }

    /**
     * Ola A: hasta 6 chips con las categorías más frecuentes de esta pestaña — ver
     * [categoriasFrecuentes]. Vacía sin datos de uso, que es cuando la fila de chips no ocupa
     * lugar (ver [EditorBody]).
     */
    val categoriasFrecuentesDelTipo = if (pickers.typeIndex > 1) {
        emptyList()
    } else {
        categoriasFrecuentes(
            tipo = if (pickers.typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
            usadas = usedCategories,
            prefs = categoryPrefs,
            usos = usosRecientes,
        )
    }

    /**
     * Tocar un chip de frecuentes o una celda del selector de «Categoría»: pone la categoría Y la
     * marca como elegida a mano (ver [categoriaElegidaAMano]).
     */
    fun elegirCategoriaAMano(nombre: String) {
        category = nombre
        categoriaElegidaAMano = true
        // Task 5: eligió con el dedo — lo que Movi venía sugiriendo (o podía llegar a sugerir)
        // ya no tiene nada que pisar ni a qué volver.
        sugerenciaVigente = null
        categoriaAntesDeLaSugerencia = null
        tipoDeLaSugerenciaVigente = null
    }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    // Ola 10: **una categoría reservada no se puede guardar**, no solo «no se sugiere».
    // El campo de categoría ya avisaba, pero el aviso era un cartel: se cerraba el selector con
    // «Pago de tarjeta» escrito, el botón seguía habilitado, y el gasto quedaba anotado y FUERA
    // de «Gastos del mes» (isCashFlow lo excluye por nombre) sin que nada lo dijera. Plata que
    // salió de verdad, invisible. El server rechaza lo mismo (ver `POST /api/events`); acá se
    // corta antes para poder explicarlo en vez de devolver un error.
    val categoriaReservada = isReservedCategory(category)
    // Ola 2 #2: canSave no miraba la categoría — se podía guardar con la caja vacía.
    val canSave = parsedAmount > 0 && category.isNotBlank() && !categoriaReservada &&
        selectedAccountId != null && !saving
    // F24: mismo patrón que Presupuestos/Recurrentes — la primera cosa que falta.
    val missingFieldMessage = when {
        parsedAmount <= 0 -> "Falta el monto"
        category.isBlank() -> "Falta la categoría"
        categoriaReservada -> CATEGORY_RESERVED_SHORT
        selectedAccountId == null -> "Falta la cuenta"
        else -> null
    }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }

    /**
     * Lo que el selector de cuenta muestra: las que sirven para esta pestaña arriba, el resto
     * detrás del «Ver todas».
     *
     * `conservar = selectedAccountId` es lo que impide que la cuenta elegida se esconda: si el
     * dueño sacó una del «Ver todas» —o si un día esta hoja se abre sobre un movimiento viejo
     * anotado en una cuenta que hoy no se ofrecería—, la fila tiene que seguir a la vista y
     * marcada. Ojo: **acá sí va `conservar`, y en [reconciliarCuenta] no** — «mostrarla» y
     * «elegirla por su cuenta» son dos permisos distintos, y la app solo tiene el primero.
     */
    val cuentasDelPicker = cuentasPara(accounts, usoDeCuenta, conservar = selectedAccountId)

    fun save() {
        if (!canSave) return
        /*
         * **La misma guarda, dicha donde de verdad decide.**
         *
         * En el renglón del `accountId` vivía `?: accounts.firstOrNull()?.id ?: "acc_1"`: el mismo
         * respaldo que en el SMS y en el extracto terminaba anotando plata contra la primera
         * cuenta del abecedario —que en las cuentas del dueño puede ser el «Vehículo 4083»— y,
         * peor, un `"acc_1"` que no es ninguna cuenta suya: un id inventado que el server rechaza
         * y que en el teléfono (`LocalRepository`, offline-first) se guardaría igual, colgado de
         * nada.
         *
         * Hoy no se alcanza, porque `canSave` ya exige `selectedAccountId != null`. Ese es
         * justamente el motivo para sacarlo y no para dejarlo: el único renglón que decide contra
         * qué cuenta se guarda afirmaba que hay un plan B cuando no lo hay, y el día que alguien
         * afloje `canSave` —agregar un tipo de movimiento, mover la validación— el plan B vuelve
         * a la vida sin que nadie lo haya elegido. Con el `?: return` el compilador ya no deja
         * escribir un `accountId` que no salga de la elección del dueño.
         */
        val cuenta = selectedAccountId ?: return
        saving = true
        error = null
        // Ola 2 #2: recortada — canSave ya exige no-vacío, pero "  Comida  " pasaba esa guarda
        // y se guardaba con espacios.
        val trimmedCategory = category.trim()
        coroutine.launch {
            val event = movimientoDeLaHoja(
                // El id sale del BORRADOR, no de acá adentro: ver [idDelBorrador] más arriba.
                id = idDelBorrador,
                cuentaId = cuenta,
                cuentas = accounts,
                tipo = if (pickers.typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                monto = amount.toLongOrNull() ?: 0L,
                categoria = trimmedCategory,
                nota = note,
                // Ola 13: la fecha elegida, no «ahora» a secas. Con «Hoy» (el default) sigue
                // siendo `Clock.System.now()` exactamente como antes — ver [timestampParaFecha],
                // que explica por qué otro día va al mediodía de Bogotá y hoy no.
                timestamp = timestampParaFecha(fecha, hoy),
            )
            val result = runCatching { Repositories.wallets.postEvent(event) }
            saving = false
            result.onSuccess {
                // Id nuevo recién ACÁ: el movimiento siguiente es otro movimiento. Mismo reflejo
                // que `TransferForm` — ver [idDelBorrador].
                idDelBorrador = newId("ev")
                // F35: si escribió una categoría nueva a mano, que ya aparezca como sugerencia
                // "usada" en el resto de la sesión. Ola 9 · A3: con el tipo con que la usó.
                UsedCategoriesCache.record(trimmedCategory, event.type)
                // Ola 11: la próxima vez, «Agregar» arranca en esta cuenta. Va DESPUÉS del
                // guardado exitoso y no antes: un guardado que falló no movió plata de ninguna
                // cuenta, y no tiene por qué mover el valor por defecto de la próxima apertura.
                //
                // Precisión que importa en el teléfono: en Android `Repositories.wallets` es
                // `LocalRepository` (offline-first), así que «exitoso» acá significa **guardado
                // en la base local**, no confirmado por el server — el `SyncEngine` lo empuja
                // después. Es lo correcto para esta preferencia: el dueño anotó el gasto en esa
                // cuenta, y que el server todavía no se haya enterado no cambia en cuál lo anotó.
                LastAccountStore.recordAccount(event.accountId)
                // Task 5 (fix round 1): la memoria de categorías que trajo esta hoja puede quedar
                // vieja apenas se guarda este movimiento — se invalida, SIN pedir nada, para que
                // la próxima hoja de «Agregar» de esta sesión vuelva a preguntar. Síncrono y no un
                // `coroutine.launch { … }`: eso relanzaba la petición en el scope de ESTA hoja, que
                // `onSaved()` —dos líneas más abajo— cierra en el mismo instante; la petición se
                // cancelaba a mitad de camino y ninguna sugerencia posterior alcanzaba a llegar en
                // toda la sesión. Ver el KDoc de `MemoriaDeCategoriasCache` para la historia entera.
                MemoriaDeCategoriasCache.invalidar()
                // Ola 9 · B: el movimiento YA está guardado; recién ahora se ofrece el
                // recurrente, y quien lo ofrece es App.kt (esta hoja se cierra en este mismo
                // paso, así que un ofrecimiento suyo se iría con ella).
                onSavedEvent(event)
                onSaved()
            }
                .onFailure { error = it.toUserMessage() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = !saving, onClick = onDismiss),
        ) {
            Box(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Movi.colores.tarjeta)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {},
            ) {
                // F37: manija + X para cerrar, el mismo componente en toda la app — 16 sitios
                // llaman a `SheetHandleWithClose` (contado con grep el 2026-08-27; el «8 hojas»
                // que decía acá y que sigue copiado en otras pantallas ya no era cierto).
                // Queda FUERA de lo que se desplaza (ver el bloque de abajo): en iOS la X es la
                // única salida de esta hoja —no hay botón atrás, y el gesto de atrás cierra la
                // hoja entera perdiendo lo escrito— así que no puede irse de la pantalla solo
                // porque el dueño bajó hasta el botón de guardar.
                SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

                // ── Ola 12 — SI LA HOJA NO ENTRA, SE PUEDE LLEGAR IGUAL AL BOTÓN ────────────
                //
                // Esta hoja está anclada abajo y NO se podía desplazar: lo que no entraba en el
                // hueco quedaba cortado contra la barra inferior, sin ninguna forma de alcanzarlo.
                // **No era un arreglo de iOS: era un bug vivo en las tres plataformas**, y lo
                // único que cambiaba entre ellas era cuánto sobraba.
                //
                // Medido (no estimado) con la hoja instrumentada, cuenta elegida y el renglón
                // «Por defecto» reservado. En el navegador la densidad es 2, así que un dp es un
                // píxel CSS; en el AVD la densidad es 2,625:
                //
                //   cuerpo del editor «Gasto»       678,5 dp
                //   + selector de tipo y respiro     63,0 dp  →  741,5 dp que se desplazan
                //   + manija con su X                44,0 dp  →  785,5 dp de hoja
                //     (`SheetHandle.kt:40` es `height(44.dp)` y `MinBottomNav.kt:63` es
                //      `height(64.dp)`: los dos, leídos del código, no estimados a ojo)
                //
                //   hueco = alto de la ventana − 64 de barra inferior − 44 de manija
                //           − las barras del sistema, donde las haya
                //
                //   navegador 800×1000 → tope 892 dp, contenido 741,5 → SOBRAN 150,5 dp (entra
                //                        holgado, y el scroll no tiene a dónde ir: `maxValue` 0)
                //   navegador 375×812  → hueco 704 dp → desborde  37,5 dp (cortaba «Falta el monto»)
                //   navegador 800×620  → hueco 512 dp → desborde 229,5 dp (corta en «7 8 9»)
                //   AVD Movi_Sensor    → hueco 551 dp → desborde 190,5 dp
                //     (411×731 dp; ahí las barras del sistema se comen 72 dp más — 24 de estado y
                //      48 de navegación: 731 − 64 − 44 − 72 = 551, que es lo que midió la sonda)
                //
                // El AVD es el caso que importa: **la fila «7 8 9» es el último renglón visible y
                // «Guardar movimiento» queda entero afuera** — verificado a ojo en `Movi_Sensor`,
                // que es el AVD que manda usar la nota del proyecto. O sea que en el APK 1.7 que
                // el dueño ya tiene instalado se podía llenar el formulario entero y quedarse sin
                // forma de guardar. En la PWA depende del alto de la ventana: **a 375×812 el botón
                // SÍ se ve** —lo que se cortaba eran los 37 dp de «Falta el monto»— y hay que
                // bajar hasta ~620 de alto para que el botón se vaya de la pantalla. En iOS, que
                // es donde se vio primero, sobra todavía menos que en el navegador porque a la
                // barra inferior se le suman la barra de estado y el indicador de inicio; nadie
                // volvió a medirlo con este código.
                //
                // `verticalScroll` no mueve nada mientras el contenido entra: a 800×1000 el
                // `maxValue` del scroll es 0, así que no hay a dónde desplazarse.
                //
                // **El precio, dicho en voz alta.** La disciplina de la Ola 8 —la hoja no cambia
                // de alto, así que nada se mueve bajo el dedo— era estructural porque la hoja era
                // inamovible. Ahora lo que desborda se puede correr: 37 dp a 812 (tres cuartos de
                // una tecla, que miden 50 dp) y 190,5 dp en el AVD. Un ARRASTRE sobre el teclado que
                // pase el umbral desplaza en vez de teclear, y el toque siguiente en el mismo punto
                // cae en otra tecla: el modo de falla exacto de la Ola 8. Con toques no se
                // consiguió provocar un dígito equivocado (un toque no llega al umbral), así que
                // es RIESGO, no defecto observado. Lo que sí quedó cerrado con código es el viaje
                // de ida y vuelta a un sub-picker (ver [recordarScroll]), que era el camino seguro
                // a que el teclado se moviera — **en las tres pestañas**: la de traspaso tiene su
                // propio `picking` adentro de [TransferBody] y por eso se le pasan el tope del
                // alto fijado y el aviso de apertura, si no quedaba con el bug entero (medido a
                // 800×620: la hoja se corría 190 dp y no volvía). Si el arrastre llega a doler,
                // el arreglo barato es que el área del teclado no desplace (un `pointerInput` que
                // consuma el arrastre vertical ahí), no sacar el scroll y volver a dejar el botón
                // inalcanzable.
                //
                // **Lo que la restauración todavía no garantiza.** Al cerrar se espera a que el
                // cuerpo se vuelva a medir (`snapshotFlow` sobre `maxValue`) y recién ahí se
                // restaura, con un timeout de un segundo como seguro; si ese timeout venciera, la
                // posición se pierde y el teclado queda corrido. En el navegador no se pudo
                // provocar; **en iOS —donde el reloj de cuadros es más caprichoso— y en el AVD
                // nadie probó ese camino**. Y queda un fogonazo de un cuadro con la hoja saltada
                // al tope antes de volver a su lugar: se ve, no rompe nada, y arreglarlo pide
                // dibujar el sub-picker sin tocar el desplazamiento.
                //
                // **Por qué el scroll va en una Column interna con `weight(1f, fill = false)`.**
                // Es el idioma de las demás hojas que se desplazan —`EditProfileSheet:92`,
                // `ChangePasswordSheet:121`, `CreditTermsSheet:176`, `CardTermsSheet:140`,
                // `CreditBalanceSheet:88`, `CreateRecurringRuleSheet:284`, y los cuerpos que les
                // pasan los andamios de `CategorySheets.kt` y `CategoriasScreen.kt`— y de paso
                // deja la manija con su X afuera del desplazamiento. (No doy un total: los
                // andamios compartidos se usan desde varios llamadores y el número dependería de
                // cuál de ellos se cuente.) **Ojo con el atajo de pegar ese modificador en la Column de la
                // hoja**: ahí NO es equivalente, porque su hermano es el `Box(weight(1f))` que la
                // empuja contra el borde, y dos hijos con peso se reparten el alto. Probado: con
                // el peso puesto en la Column de la hoja, a 800×1000 el hueco cae de 741 a 424 dp
                // —la mitad— y el teclado entero queda fuera de la pantalla en una ventana donde
                // hoy entra todo.
                //
                // **Quién recibe la altura infinita de este contenedor, y quién no.** Hasta la
                // Ola 13 no la recibía nadie: los dos sub-pickers con scroll propio —la lista de
                // cuentas de [WalletPicker], `heightIn(max = 360.dp)`, y las sugerencias de
                // `CategoryField`, `heightIn(max = 220.dp)`— tenían el alto acotado ANTES de su
                // scroll. [WalletPicker] sigue así.
                //
                // **Las sugerencias de categoría ya NO** (Ola 14, y desde la Ola B la cuadrícula
                // de [SelectorDeCategoria]): no traen scroll propio y sí se estiran con la altura
                // infinita de acá — que es exactamente el arreglo, porque el tope de 220 dp era lo
                // que dejaba 4 categorías a la vista con una losa vacía debajo. Lo que las
                // contiene no es un tope propio sino el desplazamiento de la hoja, uno solo. Por
                // eso la cuadrícula no es una `LazyVerticalGrid` (ver el KDoc del selector).
                Column(
                    modifier = Modifier
                        // El peso va primero por lectura: no mide nada, solo le dice a la Column
                        // de la hoja que este hijo se lleva lo que sobre (y nada más).
                        .weight(1f, fill = false)
                        // AFUERA del scroll: el alto que la hoja ocupa DE VERDAD en pantalla.
                        .onSizeChanged { huecoVisiblePx = it.height }
                        .verticalScroll(sheetScroll)
                        // ADENTRO del scroll: el alto del contenido, que puede pasarse del hueco.
                        .onSizeChanged { if (cuerpoCompuesto) contenidoPx = it.height },
                ) {
                    // Ola 8 · V2 — LA HOJA NO CAMBIA DE ALTURA AL ABRIR UN SUB-PICKER, Y NINGÚN
                    // CONTROL APARECE DEBAJO DE LA X DEL SUB-PICKER.
                    //
                    // Esta hoja está anclada abajo (el `Box(weight(1f))` de arriba la empuja contra
                    // el borde inferior), así que **cualquier cambio de alto le mueve TODO el
                    // contenido bajo el dedo**. Y los sub-pickers son mucho más bajos que el
                    // editor: abrir «Nota» encogía la hoja a una franja y cerrarla la volvía a
                    // estirar de golpe, dejando la tecla «9» justo donde estaba la X.
                    //
                    // Son DOS problemas y hacen falta dos arreglos, porque el primero solo no
                    // alcanza (revisión de la Ola 8, N3):
                    //
                    // 1. **El alto.** Se le pone al sub-picker un alto MÍNIMO igual al del hueco
                    //    donde se ve el cuerpo, así la hoja mide siempre lo mismo y nada se
                    //    teletransporta. (Ola 12: ese mínimo era el alto del CUERPO, que desde que
                    //    la hoja se desplaza puede ser más grande que la pantalla — ver el cálculo
                    //    de `pinnedHeight` unas líneas más abajo.)
                    //
                    // 2. **La posición de la X.** Fijar el alto mató el salto pero no el
                    //    solapamiento: la X del `PickerHeader` quedaba sobre la fila
                    //    «Gasto · Ingreso · Traspaso», y un toque impaciente después de cerrar
                    //    saltaba a «Traspaso» y se llevaba el monto de la vista. Por eso
                    //    [TypeSegments] vive AHORA fuera de este `Box`: la franja de arriba es la
                    //    misma en los dos estados, el sub-picker empieza por debajo de ella y su X
                    //    cae sobre el monto — un `Text` sin `clickable`, donde un segundo toque no
                    //    hace nada.
                    //
                    //    **Ola 12: esto ya NO es geometría garantizada, y hay que decirlo.** Era
                    //    una garantía porque la hoja no se movía: la X del sub-picker caía siempre
                    //    en el mismo punto, y en ese punto había un `Text`. Ahora, al restaurar el
                    //    desplazamiento, ese punto puede caer sobre cualquier cosa: a scroll 459,
                    //    donde estaba la X queda la fila «Cuenta», y un toque ahí abre el selector
                    //    de cuentas. La revisión lo comprobó a esa altura (no en la x exacta de la
                    //    X, así que el «segundo toque impaciente» quedó como probable, no como
                    //    demostrado). Lo que sigue en pie es lo de siempre: el selector de tipo
                    //    está afuera del `Box`, así que ninguna de las tres pestañas se cambia
                    //    sola. Recuperar la garantía entera pediría no restaurar el
                    //    desplazamiento, que es peor: mueve el teclado, que es el bug caro.
                    //
                    //    **Ola 14 — y ahora la X del sub-picker se puede ir de la pantalla.**
                    //    Con la lista de categorías estirada, bajar hasta el final la saca de la
                    //    ventana: medido, su `top` pasa de 94 dp a −120,5 dp. Lo incómodo no es
                    //    que se vaya (se recupera subiendo) sino lo que queda en su lugar: la
                    //    única X visible arriba a la derecha pasa a ser la de LA HOJA ENTERA, así
                    //    que quien se arrepiente a media lista y va «a la X» descarta el
                    //    movimiento en vez de cerrar el sub-picker. Queda ANOTADO y no arreglado:
                    //    las salidas (fijar el encabezado fuera del desplazamiento, o volver a
                    //    acotar la lista) son cambios de disposición de esta hoja, que es la que
                    //    ya se llevó nueve rondas — y ninguna se toca de pasada. Ojo también con
                    //    lo que NO cubre `HojaAgregarGeometriaTest`: su prueba de la X mira el
                    //    sub-picker **al abrirlo**, no después de desplazarlo.
                    //
                    // Que el cuerpo no esté compuesto durante un picker (el `when` lo reemplaza)
                    // ya garantiza además que no haya teclado fantasma debajo: no hay eventos que
                    // atravesar porque no hay nada atrás.
                    //
                    // El selector de tipo elige entre DOS formularios distintos: un movimiento
                    // (gasto/ingreso) y un traspaso, que no tiene ni categoría ni tipo pero sí dos
                    // cuentas — por eso decide qué se dibuja abajo en vez de vivir en [EditorBody].
                    TypeSegments(
                        // «Gasto», no «Egreso»: es la palabra que la gente usa. Toda la app
                        // habla igual — Inicio y Movimientos también dicen «Gastos».
                        // «Cuota» y no «Pago de cuota»: son cuatro segmentos en una fila que en
                        // un teléfono de 375 px reparte ~90 px a cada uno. La palabra completa no
                        // entra; la corta se entiende en contexto y el formulario lo dice entero.
                        labels = listOf("Gasto", "Ingreso", "Traspaso", "Cuota"),
                        selected = pickers.typeIndex,
                        // Cambiar de pestaña saca de composición al formulario de la pestaña
                        // vieja: si era Traspaso, su sub-picker se fue con él y el estado tiene
                        // que enterarse. Eso lo hace `conTipo` — ver [PickersDeLaHoja].
                        onSelect = { pasarA(pickers.conTipo(it)) },
                        enabled = !saving,
                    )

                    val density = LocalDensity.current
                    // El alto que el sub-picker va a respetar: NO el del cuerpo entero, sino el
                    // del HUECO donde el cuerpo se ve. `bodyHeightPx` se mide con altura
                    // infinita (está adentro del scroll), así que en una pantalla corta vale más
                    // que la pantalla — fijarlo tal cual dejaba el sub-picker 229 dp más alto que
                    // el hueco en una ventana de 620, o sea una losa vacía: se abría «Cuenta»
                    // después de bajar hasta el botón y se veía la cola de la lista y nada más,
                    // sin el título ni su X. `contenidoPx - bodyHeightPx` es todo
                    // lo demás que hay adentro del scroll (el selector de tipo y el respiro de
                    // abajo), medido y no calculado a mano, así que sigue siendo correcto si
                    // mañana cambia. Cuando el contenido SÍ entra, `huecoVisiblePx` es el
                    // contenido entero y esto da exactamente `bodyHeightPx`: el comportamiento
                    // viejo, intacto.
                    val pinnedHeight = with(density) {
                        (huecoVisiblePx - (contenidoPx - bodyHeightPx)).coerceAtLeast(0).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Mira el picker DE ESTA PANTALLA y no `hayPicker` a propósito: el
                            // de traspaso no reemplaza este `Box`, vive adentro del cuerpo y se
                            // fija su propio alto con el mismo tope (`alturaVisible`).
                            .then(if (pickers.propio == Picker.None) Modifier else Modifier.heightIn(min = pinnedHeight)),
                    ) {
                    when (pickers.propio) {
                        // Ola B · Task 4: la cuadrícula de categorías, con la búsqueda SIN foco al
                        // abrir. Antes (Ola 2 #3c) este sub-picker pedía el foco del campo apenas se
                        // abría: elegir «Comida» con un toque levantaba el teclado del sistema y le
                        // partía la ventana al medio. Ver [SelectorDeCategoria].
                        //
                        // Tocar una celda elige y cierra, y cuenta como elección a mano — igual que
                        // un chip de frecuentes ([elegirCategoriaAMano]). Con la cuadrícula no hay
                        // cambios de texto que filtrar: lo único que llega acá es un toque.
                        Picker.Category -> Column(modifier = Modifier.fillMaxWidth()) {
                            PickerHeader("Categoría", onClose = { pasarA(pickers.cerrar()) })
                            SelectorDeCategoria(
                                elegida = category,
                                onElegir = {
                                    elegirCategoriaAMano(it)
                                    pasarA(pickers.cerrar())
                                },
                                tipo = if (pickers.typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                                usadas = usedCategories,
                                prefs = categoryPrefs,
                                usos = usosRecientes,
                            )
                            // La cuadrícula no tiene tope ni scroll propio: se estira y la
                            // desplaza la hoja, un solo desplazamiento (Ola 14 — «al hacer scroll
                            // desaparecen»). Ver el KDoc de [SelectorDeCategoria].
                            Spacer(Modifier.height(8.dp))
                            EnlaceAdministrarCategorias()
                            Spacer(Modifier.height(4.dp))
                        }
                        Picker.Wallet -> WalletPicker(
                            cuentas = cuentasDelPicker,
                            uso = usoDeCuenta,
                            selectedId = selectedAccountId,
                            onPick = {
                                selectedAccountId = it
                                // Elegida a mano: la reconciliación de arriba ya no la pisa, y el
                                // aviso «Última usada» desaparece — ya no lo decidió la app.
                                origenCuenta = OrigenCuenta.ELEGIDA
                                pasarA(pickers.cerrar())
                            },
                            onClose = { pasarA(pickers.cerrar()) },
                        )
                        // Ola 13: el selector de fecha entra como sub-picker, igual que Categoría,
                        // Cuenta y Nota — reemplaza el cuerpo adentro del Box de alto fijado, así que
                        // abrirlo no cambia el alto de la hoja ni corre el teclado. Elegir un día ES
                        // la acción completa (no hay nada más que decidir), así que cierra al toque,
                        // como una sugerencia de categoría.
                        Picker.Date -> Column(modifier = Modifier.fillMaxWidth()) {
                            PickerHeader("Fecha", onClose = { pasarA(pickers.cerrar()) })
                            SelectorDeFecha(
                                seleccionada = fecha,
                                hoy = hoy,
                                onPick = { fecha = it; pasarA(pickers.cerrar()) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Picker.Note -> NoteEditor(
                            initial = note,
                            onSave = { note = it; pasarA(pickers.cerrar()) },
                            onClose = { pasarA(pickers.cerrar()) },
                        )
                        Picker.None -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // El alto que los sub-pickers van a respetar (ver el comentario de
                                // arriba). Se mide acá y no se calcula a mano: así sigue siendo
                                // correcto si mañana el cuerpo gana o pierde una fila.
                                .onSizeChanged { if (cuerpoCompuesto) bodyHeightPx = it.height },
                        ) {
                            if (pickers.typeIndex >= TIPO_TRASPASO) {
                                TransferBody(
                                    accounts = accounts,
                                    accountsLoaded = accountsLoaded,
                                    // Las dos mitades de la disciplina, prestadas a la pestaña de
                                    // traspaso: el tope del alto fijado y el aviso de que se abrió
                                    // o cerró su sub-picker (su `picking` no se ve desde acá).
                                    alturaVisible = pinnedHeight,
                                    onPickerAbierto = { abierto ->
                                        pasarA(pickers.conPickerDeTraspaso(abierto))
                                    },
                                    // Ola 11: si la hoja se abrió desde el detalle de una cuenta, ese
                                    // contexto vale también para el ORIGEN del traspaso — es la
                                    // cuenta que el dueño estaba mirando cuando tocó «Agregar».
                                    presetAccountId = presetAccountId,
                                    // La misma hoja sirve las dos pestañas: un pago de cuota es un
                                    // traspaso con otras categorías y otro endpoint. Ver
                                    // [ModoDeTraspaso].
                                    modo = if (pickers.typeIndex == TIPO_PAGO_CUOTA) {
                                        ModoDeTraspaso.PAGO_DE_CUOTA
                                    } else {
                                        ModoDeTraspaso.TRASPASO
                                    },
                                    onSaved = onSaved,
                                )
                            } else {
                                EditorBody(
                            amount = amount,
                            moneda = monedaDeLaCuenta(accounts, selectedAccountId),
                            onKey = ::onKey,
                            category = category,
                            // Task 5: solo se muestra la sugerencia que Movi puso, no cualquier
                            // "Movi la reconoce" persistente — desaparece apenas el dueño elige a
                            // mano ([elegirCategoriaAMano] pone esto en `null`).
                            categoriaSugeridaHint = sugerenciaVigente?.let { "Movi la reconoce: ${it.nombre}" },
                            categoriasFrecuentes = categoriasFrecuentesDelTipo,
                            onPickCategoriaFrecuente = ::elegirCategoriaAMano,
                            // **Anotado, no arreglado (B3, y es de master):** si `getAccounts()`
                            // falla y la hoja se abrió con `presetAccountId`, `selectedAccount` es
                            // null —la lista está vacía— así que esto dice «Seleccionar cuenta»,
                            // pero `canSave` mira `selectedAccountId`, que SÍ tiene el preset: el
                            // botón queda habilitado y el movimiento se guarda en la cuenta
                            // correcta, sin que el dueño haya llegado a ver cuál era. Arreglarlo
                            // bien pide resolver el nombre sin la lista (o bloquear el guardado, que
                            // sería peor: hoy se guarda, y se guarda bien).
                            walletLabel = selectedAccount?.name ?: "Seleccionar cuenta",
                            // Ola 11: solo dice algo cuando el valor lo puso la app y hay más de una
                            // cuenta donde anotar (ver [avisoDeCuenta]).
                            walletHint = if (selectedAccount == null) null
                                else avisoDeCuenta(origenCuenta, accounts.size),
                            walletHintReserved = accounts.size > 1,
                            note = note,
                            dateLabel = etiquetaDeFecha(fecha, hoy),
                            onPickDate = { pasarA(pickers.abrir(Picker.Date)) },
                            onPickCategory = { pasarA(pickers.abrir(Picker.Category)) },
                            onPickWallet = { pasarA(pickers.abrir(Picker.Wallet)) },
                            onEditNote = { pasarA(pickers.abrir(Picker.Note)) },
                            onOcr = { onNavigate(Screen.OCRCapture) },
                            canSave = canSave,
                            missingFieldMessage = missingFieldMessage,
                            saving = saving,
                            error = error,
                            onSave = ::save,
                                    hasNoAccounts = accountsLoaded && accounts.isEmpty(),
                                    onCreateAccount = { showCreateSheet = true },
                                )
                            }
                        }
                    }
                    } // Box del alto fijado

                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = { showCreateSheet = false; accountsRefreshKey++ },
            )
        }
    }
}

/**
 * El selector de arriba de la hoja: Gasto · Ingreso · Traspaso.
 *
 * Vive fuera de [EditorBody] desde que existe la tercera opción: ya no elige una variante del
 * mismo formulario sino entre dos formularios distintos (ver [TransferBody]), así que el que lo
 * dibuja tiene que ser el que decide cuál se muestra.
 */
@Composable
internal fun TypeSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isActive = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) Movi.colores.tarjeta else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) Movi.colores.texto else Movi.colores.textoMedio,
                    letterSpacing = 0.1.sp,
                    // Una sola línea SIEMPRE. Con cuatro segmentos, cada uno se queda con ~82 dp
                    // en un teléfono de 375 px: «Traspaso» a 13 sp mide ~55, pero con la escala de
                    // fuente del sistema al 2× se pasa y envuelve, lo que crece la fila del
                    // selector y corre el formulario entero hacia abajo. Con tres segmentos el
                    // umbral estaba más lejos; con cuatro, no.
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun EditorBody(
    amount: String,
    /**
     * La moneda de la cuenta elegida — la del movimiento que se va a guardar. Manda el rótulo de
     * abajo del monto y el símbolo de adelante: con la Master Black elegida, esta hoja decía
     * «$500.000 · COP» y guardaba US$500.000. Ver [movimientoDeLaHoja].
     */
    moneda: String,
    onKey: (String) -> Unit,
    category: String,
    /**
     * Task 5: `"Movi la reconoce: <nombre>"` cuando la categoría de arriba la puso una sugerencia
     * automática — `null` el resto del tiempo, incluido mientras el dueño elige a mano. Se pasa
     * como `sub` de la fila «Categoría» (ver [CardRow]), con el mismo estilo que cualquier otro
     * texto de apoyo de esta hoja.
     */
    categoriaSugeridaHint: String? = null,
    /**
     * Ola A: hasta 6 categorías, las que más se usan para este tipo — ver [categoriasFrecuentes].
     * Vacía = la fila de chips no se dibuja y no ocupa lugar (a diferencia de la fila «Cuenta»,
     * que sí reserva su alto: acá no hace falta, porque esta fila no aparece y desaparece por su
     * cuenta en la misma sesión — el tipo elegido no cambia salvo que el dueño toque el segmento
     * de arriba, y ESE toque ya mueve todo el formulario).
     */
    categoriasFrecuentes: List<String> = emptyList(),
    onPickCategoriaFrecuente: (String) -> Unit = {},
    walletLabel: String,
    walletHint: String? = null,
    /** Si el renglón del aviso ocupa su lugar aunque hoy no diga nada — ver la fila «Cuenta». */
    walletHintReserved: Boolean = false,
    note: String,
    /** «Hoy», «Ayer» o «23 de agosto» — ver `etiquetaDeFecha`. Se muestra debajo del monto. */
    dateLabel: String,
    onPickDate: () -> Unit,
    onPickCategory: () -> Unit,
    onPickWallet: () -> Unit,
    onEditNote: () -> Unit,
    onOcr: () -> Unit,
    canSave: Boolean,
    missingFieldMessage: String? = null,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
    hasNoAccounts: Boolean = false,
    onCreateAccount: () -> Unit = {},
) {
    // Ola 13 — DE DÓNDE SALIERON ESTOS DOS SPACERS MÁS CHICOS (22→16 y 8→2).
    //
    // Son el presupuesto de alto de la pastilla de fecha de acá abajo.
    //
    // **Lo que se vio, a ojo, en la web local a 812 dp de alto con la barra inferior puesta:**
    // con la fecha agregada como una CUARTA fila de la tarjeta, «Guardar movimiento» quedaba
    // debajo del recorte y había que desplazar la hoja para verlo. Se llegaba —el
    // `verticalScroll` de la Ola 12 hace su trabajo— pero el botón de guardar de la pantalla
    // donde se anota la plata no puede pedir un gesto previo para aparecer. Después del cambio de
    // acá, y en la misma pantalla, el botón se ve entero sin tocar nada.
    //
    // **Lo que se puede contar, y por eso se cuenta en vez de estimarse.** Llamando H al alto de
    // un renglón de 12 sp (el mismo en los dos lados), el bloque del monto pasa de
    // `22 + 56 + 8 + H` a `16 + 56 + 2 + (H + 10)` — la pastilla es ese mismo renglón con 5 dp de
    // padding arriba y abajo. O sea **exactamente 2 dp menos que master**, sin depender de cuánto
    // mida H ni de la métrica de fuente de cada plataforma. Lo que sigue igual: el `Spacer(18)`
    // de abajo, el alto de las teclas, y los dos renglones de alto reservado que mantienen el
    // teclado quieto.
    //
    // No hay ningún número «medido» acá: los altos absolutos de la hoja y del hueco de contenido
    // no se midieron, se vieron. Lo medido es la comparación, y es la que decide.
    Spacer(Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // F14: mismo arreglo que Presupuestos — separador de miles mientras se escribe,
        // no solo al guardar (formatAmountKeypadDisplay respeta el "." decimal de este teclado).
        //
        // La protagonista de la hoja, como «Tu plata» y lo gastado en Presupuestos. Antes iba a
        // 56 sp en una sola línea sin achique: un monto de nueve cifras se salía del ancho del
        // teléfono. CifraProtagonista baja de tamaño hasta que entra.
        CifraProtagonista(simboloDeMoneda(moneda) + formatAmountKeypadDisplay(amount), color = Movi.colores.texto)
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // El rótulo de la moneda REAL, no un «COP» clavado. Ver el parámetro [moneda].
            Text(moneda, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, letterSpacing = 0.4.sp)
            Text("·", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            // La fecha, como pastilla tocable. Va acá y no en la tarjeta de abajo por el alto
            // (ver arriba), pero además queda donde tiene sentido leerla: pegada al monto, que
            // es lo primero que el ojo mira. Dice «Hoy» por defecto, así que quien anota en el
            // momento no tiene ni que interpretarla.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Movi.colores.tarjeta)
                    .clickable(onClick = onPickDate)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = dateLabel,
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Cambiar la fecha",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        CardRow(
            left = { Text("Categoría", style = Movi.textos.titulo, color = Movi.colores.textoMedio) },
            right = {
                Text(
                    text = category,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            // Ver el KDoc de [rightMaxFraction] en CardRow: una categoría propia larga
            // («Mantenimiento del carro») se llevaba la fila entera y partía la etiqueta.
            rightMaxFraction = FRACCION_VALOR_FILA,
            showChevron = true,
            onClick = onPickCategory,
            // La línea de la sugerencia va debajo, a lo ancho, y el hairline después de ella.
            isLast = categoriaSugeridaHint != null,
        )
        // Task 5: «Movi la reconoce: <nombre>» cuando lo de arriba lo puso una sugerencia
        // automática. Va en su propio renglón, a lo ancho de la tarjeta, y no como subtítulo de
        // «Categoría»: ahí le tocaba la columna izquierda (el valor se lleva el 55 %) y en el
        // Pixel del dueño «Movi la reconoce: Señor Gol + Mora Soccer» se leía «Movi la
        // reconoce: P…». Un renglón, con «…» si aun así no entra.
        if (categoriaSugeridaHint != null) {
            Text(
                text = categoriaSugeridaHint,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = Movi.espacios.corto),
            )
            if (categoriasFrecuentes.isEmpty()) Hairline()
        }
        // Ola A: los chips de frecuentes, entre «Categoría» y «Cuenta» — justo debajo de la
        // categoría que resumen, y antes de la fila que decide dónde sale la plata. Vacía = no
        // se dibuja nada y `CardRow` de arriba sigue con su hairline pegado al de «Cuenta», que
        // es exactamente el aspecto de hoy para quien no tiene ningún dato de uso todavía.
        if (categoriasFrecuentes.isNotEmpty()) {
            CategoriaChipsRow(
                categorias = categoriasFrecuentes,
                categoriaElegida = category,
                onPick = onPickCategoriaFrecuente,
            )
            Hairline()
        }
        CardRow(
            left = {
                Text("Cuenta", style = Movi.textos.titulo, color = Movi.colores.textoMedio)
                // Ola 11 — DE DÓNDE SALIÓ LA CUENTA QUE DICE AL LADO, Y POR QUÉ ESTE RENGLÓN
                // OCUPA SU LUGAR AUNQUE NO DIGA NADA.
                //
                // Lo primero: con varias cuentas, el valor por defecto es una decisión de la app
                // («la última que usaste», o «la primera de la lista» la primera vez), y esa
                // decisión tiene que poder leerse ANTES de tocar Guardar. La fila ya mostraba el
                // nombre, pero un nombre no dice si lo eligió el dueño o lo puso la app.
                //
                // Lo segundo es la disciplina de esta hoja: está anclada abajo, así que
                // **cualquier cambio de alto le mueve el teclado bajo el dedo** (ver el bloque de
                // la Ola 8 · V2 más arriba: 22 px de salto ya hicieron que se tipeara «8» en vez
                // de «0»). Este renglón aparece y desaparece por su cuenta —al cargar la lista,
                // al elegir una cuenta a mano— así que reserva su alto mientras haya más de una
                // cuenta, que es exactamente cuando puede llegar a decir algo. Cuesta 15 dp
                // fijos ahí, y garantiza que ni elegir una cuenta ni cambiar de pestaña corran
                // el teclado.
                //
                // Con UNA sola cuenta no reserva nada y la fila queda idéntica a como estaba
                // antes de esta rama: no hay decisión que confesar, no hay alternativa que
                // ofrecer, y no había ningún problema que arreglarle a quien tiene una cuenta.
                AvisoDeCuentaRow(walletHint, walletHintReserved)
            },
            right = {
                Text(
                    text = walletLabel,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            rightMaxFraction = FRACCION_VALOR_FILA,
            showChevron = true,
            // F10: sin cuentas no hay nada que elegir — abrir el selector solo mostraría la
            // mentira de "Cargando cuentas…" (no está cargando, no hay ninguna). En ese caso el
            // toque lleva directo a crear la cuenta, que es lo único que de verdad hace algo acá.
            onClick = if (hasNoAccounts) onCreateAccount else onPickWallet,
        )
        CardRow(
            left = { Text("Nota", style = Movi.textos.titulo, color = Movi.colores.textoMedio) },
            right = {
                Text(
                    text = note.ifBlank { "Agregar nota…" },
                    style = Movi.textos.titulo,
                    color = if (note.isBlank()) Movi.colores.textoApagado else Movi.colores.texto,
                    fontWeight = if (note.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            // La nota es texto libre: sin techo, «Almuerzo con el equipo de la oficina» hacía
            // exactamente lo mismo que un nombre de cuenta largo.
            rightMaxFraction = FRACCION_VALOR_FILA,
            isLast = true,
            onClick = onEditNote,
        )
    }

    // Ola 8 · V2 (N3 de la revisión) — el error de red TAMBIÉN reserva su alto.
    //
    // Quedó afuera del primer arreglo por un razonamiento equivocado: «está ARRIBA del teclado,
    // y en una hoja anclada abajo lo de arriba no mueve lo de abajo». Es cierto solo mientras
    // la hoja tenga aire por encima. En un teléfono (medido a 375×812) la hoja ya llega casi al
    // borde superior, así que no puede crecer hacia arriba: el renglón de error empuja el
    // teclado HACIA ABAJO ~18 dp y «Guardar movimiento» se corre de 683 a 701. Es exactamente
    // el mismo bug que «Falta el monto», y aparece en el peor momento — justo después de que
    // un guardado falló y la persona va a reintentar sobre el mismo monto.
    //
    // Dos renglones de alto: los mensajes de `toUserMessage()` más largos se parten en dos en
    // el ancho de un teléfono, y reservar de menos volvería a mover el teclado.
    Spacer(Modifier.height(8.dp))
    Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
        if (error != null) {
            Text(error, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
    }

    Spacer(Modifier.height(14.dp))

    Column {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            // Sin tecla decimal: en COP no hay centavos, y «2500.5» pasaba la validación como 2500.5
            // pero se guardaba como $0 (toLongOrNull). Ver revisión de la Ola 1.
            listOf("000", "0", "⌫"),
        ).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = Movi.colores.texto, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = key,
                                // Sin estilo a propósito: tecla del teclado numérico; `titular` (19) la achica y `cifra` (42) no cabe.
                                fontSize = 22.sp,
                                style = Movi.textos.monto,
                                fontWeight = FontWeight.Normal,
                                color = Movi.colores.texto,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (hasNoAccounts) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Primero crea una cuenta donde anotar este movimiento",
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Movi.colores.marca.copy(alpha = 0.16f))
                    .clickable { onCreateAccount() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+ Crear cuenta",
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.marca,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp, 54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(16.dp))
                    .clickable { onOcr() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Escanear recibo", tint = Movi.colores.texto, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canSave) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
                    .clickable(enabled = canSave) { onSave() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Guardando…" else "Guardar movimiento",
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = if (canSave) Movi.colores.marca else Movi.colores.textoApagado,
                )
            }
        }
        // Ola 8 · V2 — ESTE RENGLÓN SIEMPRE OCUPA SU LUGAR, DIGA ALGO O NO.
        //
        // Antes aparecía y desaparecía, y está **debajo** del teclado en una hoja anclada
        // abajo: al escribir el primer dígito «Falta el monto» se iba, la hoja se encogía y
        // el teclado entero bajaba de golpe. Medido en la web local: **22 px en pantalla, ~35
        // dp — el 70 % del alto de una tecla.** O sea que el segundo toque en el mismo punto
        // caía en la tecla de arriba: escribías «0» y salía «8», sin ningún aviso. Reservarle
        // el alto fijo cuesta 16 dp de aire y deja el teclado quieto mientras se tipea, que es
        // exactamente lo que hay que garantizar en la pantalla donde se anota la plata.
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!canSave && !saving && missingFieldMessage != null) {
                Text(
                    text = missingFieldMessage,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Ola A: hasta 6 chips con las categorías que más se usan para este tipo — ver
 * [categoriasFrecuentes], que es la que decide cuáles y en qué orden. Tocar uno la elige sin
 * abrir el sub-picker de «Categoría»; la elegida se ve activa.
 *
 * Mismo lenguaje visual que [SheetChip] de `CreateRecurringRuleSheet.kt` (fondo tenue de
 * `Movi.colores.marca` cuando está activo, borde cuando no) y no el de los chips de filtro de
 * Movimientos: esta fila vive DENTRO de una `MinCard`, cuyo fondo ya es `Movi.colores.tarjeta` —
 * un chip inactivo pintado con ese mismo color sería invisible contra su propio fondo.
 *
 * **Alto mínimo, no fijo, y sin relleno vertical en la fila.** La versión anterior era
 * `.height(40.dp)` con `padding(vertical = 8.dp)` en la fila Y en cada chip: quedaban 24 dp para
 * el chip, 8 dp para el texto, y una línea de 16 sp de `apoyo` salía **recortada a la mitad a
 * letra normal** — el nombre de la categoría se leía cortado por arriba y por abajo. Ahora el
 * único relleno vertical es el del chip (`corto`), el chip mide ~32 dp y entra con aire en los
 * 40 dp de [ALTO_FILA_DE_CHIPS]; si el dueño agranda la escala de letra de Movi, la fila crece
 * lo justo para que el texto se lea entero, que es preferible a un chip ilegible. Sigue siendo
 * una sola fila (`maxLines = 1` en cada chip) con desplazamiento horizontal propio: no empuja el
 * resto del formulario, la misma disciplina que ya rige toda esta hoja (ver el bloque «SI LA HOJA
 * NO ENTRA» más arriba). Lo mide `HojaAgregarChipsSeLeenEnterosTest`.
 */
@Composable
private fun CategoriaChipsRow(
    categorias: List<String>,
    categoriaElegida: String,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_FILA_DE_CHIPS)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        // "Entre elementos hermanos apretados: chips" es literalmente lo que dice el KDoc de
        // este token en Tokens.kt — este es el caso para el que existe.
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        categorias.forEach { nombre ->
            val activa = nombre == categoriaElegida
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    .background(if (activa) Movi.colores.marca.copy(alpha = 0.16f) else Color.Transparent)
                    .then(
                        if (!activa) Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.pleno))
                        else Modifier,
                    )
                    .clickable { onPick(nombre) }
                    // Horizontal: el 14dp original quedaba justo entre `medio` (12dp) y `amplio`
                    // (16dp) — misma distancia a los dos. Se eligió `medio`, "el respiro de
                    // adentro de una fila", que es justo lo que es esto: el respiro de adentro de
                    // una píldora angosta (`amplio` es el relleno de una tarjeta entera, de más
                    // aire del que necesita un chip). Vertical: `corto` (8dp) es el más cercano al
                    // 7dp original (a 1dp; `minimo`, 4dp, quedaba a 3dp).
                    .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.corto),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nombre,
                    style = Movi.textos.apoyo,
                    fontWeight = if (activa) FontWeight.Medium else FontWeight.Normal,
                    color = if (activa) Movi.colores.marca else Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * El renglón chiquito de «Última usada» / «Por defecto», debajo de la etiqueta de
 * una fila de cuenta. Lo usan la fila «Cuenta» del editor y las filas «Desde»/«Hacia» del
 * traspaso, con el mismo alto y el mismo criterio (ver [avisoDeCuenta]).
 *
 * `lineHeight` explícito y `Box` de alto fijo: el alto tiene que ser el mismo diga lo que diga,
 * y no depender de la métrica de la fuente que le toque a cada plataforma.
 */
@Composable
internal fun AvisoDeCuentaRow(aviso: String?, reservado: Boolean) {
    if (aviso == null && !reservado) return
    Box(modifier = Modifier.height(15.dp)) {
        if (aviso != null) {
            Text(
                text = aviso,
                style = Movi.textos.apoyo,
                lineHeight = 14.sp,
                color = Movi.colores.textoApagado,
                // Una sola línea, y con puntos suspensivos si no entra.
                //
                // **Esto solo no alcanzaba, y el comentario que estaba acá antes mentía.** El
                // renglón comparte la fila con el nombre de la cuenta, que hasta la Ola 11 se
                // llevaba TODO el ancho que quisiera (ver `rightMaxFraction` en `CardRow`): con
                // un nombre muy largo el aviso no se cortaba con «…», directamente no se veía, y
                // lo que se rompía era la etiqueta «Cuenta» de al lado, partida en una letra por
                // renglón. El arreglo de verdad es el techo del lado derecho; este `maxLines` es
                // la segunda mitad, para que con la fila ya acotada el aviso se corte con «…» en
                // vez de ocupar dos renglones y volver a mover el teclado.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PickerHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                // Lo único que este archivo cambió por los tests. La X de la hoja
                // (`SheetHandleWithClose`) y esta X dicen las dos «Cerrar», así que con un
                // sub-picker abierto hay DOS nodos con esa descripción y elegir «el segundo»
                // sería una prueba que se rompe sola el día que el orden cambie. Ver
                // `HojaAgregarGeometriaTest`, que abre y cierra sub-pickers para afirmar que el
                // teclado no se mueve.
                .testTag(TAG_CERRAR_SUB_PICKER)
                .clip(CircleShape)
                .background(Movi.colores.tarjeta)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Movi.colores.texto, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * El sub-picker de «Cuenta» de las pestañas Gasto e Ingreso.
 *
 * **Ola 15 — acá había `items(accounts)` sobre la lista entera, o sea ningún criterio.** El dueño
 * abría «¿de qué cuenta sale este gasto?» y veía «Vehículo 4083 · $177.200.000», que no es plata
 * suya: es lo que debe por un crédito ya desembolsado. Ahora la lista viene partida por
 * [cuentasPara] según [uso] — de un gasto la plata sale del efectivo, del banco o de una tarjeta;
 * a un ingreso entra al efectivo, al banco o a la inversión.
 *
 * **Lo excluido no desaparece**: queda detrás de «Ver todas», plegado. Es una regla del proyecto —
 * nada que solo se pueda destrabar tocando código, porque el resto de la gente no tiene a nadie al
 * lado para editarle un `filter`— y además es lo honesto: la app no sabe todo. Lo que sí no hace
 * nunca es *proponer* una de esas cuentas sola (ver `reconciliarCuenta`).
 */
@Composable
internal fun WalletPicker(
    cuentas: CuentasDelPicker,
    uso: UsoDeCuenta,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    // Se pliega de nuevo cada vez que se abre el sub-picker: la lista corta es la respuesta
    // normal, y quien necesitó la larga una vez no tiene por qué verla siempre.
    //
    // **Salvo que la corta esté vacía.** Quien solo tiene créditos e inversión abriría el
    // selector y vería nada, con la salida escondida detrás de un renglón que parece un pie de
    // página: un callejón sin salida disfrazado de lista. Ahí se abre ya desplegado.
    //
    // **La llave del `remember` no es decorativa.** Sin ella, ese valor inicial se congela en la
    // PRIMERA composición y no vuelve a mirarse: la fila «Cuenta» es alcanzable antes de que la
    // lista llegue (`hasNoAccounts` solo es cierto con `accountsLoaded`), así que con la red lenta
    // el selector abría vacío → `verTodas = true` → llegaban las cuentas y se dibujaban TODAS,
    // desplegadas, con «Vehículo 4083 · $177.200.000» en el medio. Exactamente la pantalla que
    // esta rama vino a arreglar, servida por el arreglo. Con la llave puesta, el día que
    // `principales` deja de estar vacía el pie se vuelve a plegar solo — y un toque del dueño en
    // «Ver todas» sigue sobreviviendo, porque tocar no cambia la llave.
    var verTodas by remember(cuentas.principales.isEmpty()) {
        mutableStateOf(cuentas.principales.isEmpty())
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader("Cuenta", onClose)
        if (cuentas.vacio) {
            // F10: este picker ya no debería ser alcanzable sin cuentas (ver el onClick de la
            // fila "Cuenta" en EditorBody), pero el texto no miente si de todos modos se llega.
            Text("No tienes cuentas todavía.", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            val visibles = if (verTodas) cuentas.todas else cuentas.principales
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(visibles) { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(account.id) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.name,
                                style = Movi.textos.titulo,
                                color = Movi.colores.texto,
                                fontWeight = if (account.id == selectedId) FontWeight.Medium else FontWeight.Normal,
                            )
                            Text(
                                saldoDeLaCuenta(account),
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                            )
                        }
                        if (account.id == selectedId) Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.texto, modifier = Modifier.size(16.dp))
                    }
                }
                // Va DENTRO del LazyColumn y no debajo: con muchas cuentas la lista llega a su
                // tope de 360 dp y todo lo que quedara afuera del scroll sería inalcanzable —
                // que es justamente el modo de falla que esta rama vino a arreglar, al revés.
                if (cuentas.hayOtras) {
                    item {
                        VerTodasLasCuentas(
                            expandido = verTodas,
                            cuantas = cuentas.otras.size,
                            uso = uso,
                            onToggle = { verTodas = !verTodas },
                        )
                    }
                }
            }
        }
    }
}

/**
 * El saldo que va debajo del nombre de la cuenta.
 *
 * Tres arreglos de la Ola 15, los tres del mismo renglón:
 *
 * 1. **La moneda.** Iba `formatCOP(account.balance)` a secas, y `balance` es el componente COP de
 *    la cuenta (lo deriva `enrichWith`): la «Master Black 3684 USD» del dueño se mostraba como
 *    «$0». Ahora sale en SU moneda — y con SU rótulo, que es lo que arregla [saldoEnSuMoneda]:
 *    sin red no hay `balancesByCurrency`, y el respaldo viejo escribía el componente en PESOS con
 *    el símbolo del dólar. Para una cuenta en pesos el texto es carácter por carácter el de antes
 *    (`signedMoney(x, "COP")` y `formatCOP(x)` producen lo mismo).
 * 2. **Deber no es tener.** Bajo una tarjeta, «$1.240.000» se lee como plata disponible cuando es
 *    exactamente lo contrario. Desde que las tarjetas se ofrecen para gastar (que es el pedido),
 *    ese renglón tiene que decir qué es: «Debes $1.240.000».
 * 3. **Y deber de menos es tener.** En una tarjeta un saldo NEGATIVO es plata a favor: si el dueño
 *    sobrepagó la Nu, «Debes −$50.000» dice dos cosas opuestas en cuatro palabras. La inversión
 *    del signo no se reescribe acá: sale de [saldoDeDeuda], donde también la lee la tarjeta grande
 *    del detalle de la cuenta.
 */
private fun saldoDeLaCuenta(account: com.jvillada.movi.shared.model.Account): String {
    val (monto, moneda) = saldoEnSuMoneda(account)
    if (!isDebtAccount(account.type)) return signedMoney(monto, moneda)
    val saldo = saldoDeDeuda(monto, moneda)
    return if (saldo.aFavor) "A favor ${saldo.magnitud}" else "Debes ${saldo.magnitud}"
}

/**
 * El texto entrante **recortado al tope de su columna**, con la selección dentro de lo que quedó.
 *
 * Se construye un [TextFieldValue] nuevo en vez de copiar el entrante: la `composition` del que
 * llega apunta a posiciones del texto largo, y dejarla colgando sobre un texto más corto es un
 * rango fuera de límites.
 */
internal fun recortadoAlTope(entrante: TextFieldValue, tope: Int): TextFieldValue {
    if (entrante.text.length <= tope) return entrante
    return TextFieldValue(
        text = entrante.text.take(tope),
        selection = TextRange(
            entrante.selection.start.coerceAtMost(tope),
            entrante.selection.end.coerceAtMost(tope),
        ),
    )
}

@Composable
private fun NoteEditor(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    // `TextFieldValue` y no `String`: hace falta poder decir DÓNDE queda el cursor.
    //
    // Ola 8 · V2 (N1 de la revisión): con un `String` pelado, el `requestFocus()` de abajo deja
    // el cursor en la posición 0, así que al reabrir una nota ya escrita lo nuevo se metía
    // ADELANTE de lo viejo. Reproducido: nota «agosto» → guardar → reabrir → tipear «Nomina »
    // → queda «Nomina agosto». Hay evidencia del bug en la base local de desarrollo: la fila
    // `AlmuerzoNomina agosto` de `financial_events` se guardó así durante la verificación de
    // esta misma ola, sin que nadie lo notara.
    //
    // Es el mismo problema que `CategoryField` ya tenía resuelto (Ola 2 #3b) y del que acá se
    // había copiado solo la mitad —el foco— y no la otra —la selección—. Mismo remedio y por
    // el mismo motivo: al ganar el foco se selecciona todo, así tipear REEMPLAZA en vez de
    // insertarse en medio de lo que ya había.
    var value by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var focused by remember { mutableStateOf(false) }
    // Ola 8 · V2: sin foco automático había que acertarle al campo con el dedo, y ver más
    // abajo por qué eso era imposible. Es el MISMO arreglo que el sub-picker de Categoría ya
    // tenía documentado (Ola 2 #3c) y que a Nota nunca se le hizo: la hoja se abre lista para
    // escribir «Nómina agosto», sin un toque previo que pueda caer en cualquier otro lado.
    val noteFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { noteFocusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        PickerHeader("Nota", onClose)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                // El mismo tope que la categoría, y por lo mismo: `description` es un
                // `varchar(255)`, y una nota más larga salía por el 500 genérico del server (en el
                // teléfono, por un reintento eterno y mudo). Se RECORTA en vez de descartarse,
                // para que pegar un texto largo deje lo que entra en lugar de no dejar nada.
                onValueChange = { value = recortadoAlTope(it, MAX_CONCEPTO_LENGTH) },
                cursorBrush = SolidColor(Movi.colores.texto),
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                // Ola 8 · V2: **sin `fillMaxWidth` el campo no se podía tocar.** El área
                // sensible de un BasicTextField es la que mide su contenido, y con el texto
                // vacío eso son cero píxeles de ancho: la caja gris se ve grande, pero el
                // campo de verdad es una raya invisible. Verificado en la web local sobre
                // master — clic en el centro de la caja y clic pegado al borde izquierdo, y en
                // los dos casos lo tecleado no entró en ninguna parte. O sea: la nota no se
                // podía escribir. Las otras siete hojas de la app (CreateAccountSheet,
                // VoidEventSheet, CreateSubscriptionSheet, CategoryField…) ya traían este
                // `fillMaxWidth`; esta era la única que se lo había saltado.
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CAMPO_DE_NOTA)
                    .focusRequester(noteFocusRequester)
                    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                    // [esAtajoDeSeleccionarTodo]. Es lo mismo que hace la línea de abajo al ganar
                    // el foco, pero disponible cuando ya se está escribiendo: sin esto, volver a
                    // reemplazar la nota entera con el teclado no tenía cómo.
                    .onPreviewKeyEvent { evento ->
                        if (esAtajoDeSeleccionarTodo(evento) && value.text.isNotEmpty()) {
                            value = conTodoSeleccionado(value)
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged { state ->
                        // Al GANAR el foco (no en cada recomposición, o sería imposible mover
                        // el cursor a mano después).
                        if (state.isFocused && !focused) {
                            value = value.copy(selection = TextRange(0, value.text.length))
                        }
                        focused = state.isFocused
                    },
                decorationBox = { inner ->
                    if (value.text.isEmpty()) {
                        Text("Concepto del movimiento", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Movi.colores.marca.copy(alpha = 0.16f))
                .clickable { onSave(value.text.trim()) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Guardar nota", style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.marca)
        }
    }
}
