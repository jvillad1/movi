package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.CATEGORY_RESERVED_SHORT
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Cuánto de una fila de la hoja puede ocupar el valor de la derecha (ver `rightMaxFraction` en
 * `CardRow`, donde está el porqué). 55 % deja ~167 dp para el valor y ~136 dp para la etiqueta y
 * su aviso a 375 dp — medido: «Bancolombia Ahorros» entra entero, «Última usada» también, y un
 * nombre más largo se corta con «…» en vez de partir la etiqueta letra por letra.
 */
private const val FRACCION_VALOR_FILA = 0.55f

private sealed class Picker {
    data object None : Picker()
    data object Category : Picker()
    data object Wallet : Picker()
    data object Note : Picker()
}

/**
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
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // F35: arranca en la primera categoría predefinida de Gastos, como antes arrancaba en
    // "Mercado" — ahora es texto libre con sugerencias (CategoryField), no una lista fija.
    //
    // Ola 10 (revisión): el valor inicial sale de [categoriaPorDefectoPara] y no de
    // `PREDEFINED_CATEGORIES.first { … }`. Con la línea vieja, esconder «Comida» en
    // «Más → Categorías» y abrir Agregar dejaba el campo diciendo **«Comida»**: un toque en
    // «Guardar movimiento», sin abrir siquiera el selector, y el gasto quedaba anotado en la
    // categoría que el dueño acababa de retirar. La pantalla donde más se equivoca es esta.
    var category by remember {
        mutableStateOf(
            categoriaPorDefectoPara(
                TransactionType.EXPENSE, UsedCategoriesCache.used, UsedCategoriesCache.prefs,
            ),
        )
    }
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
    var picker by remember { mutableStateOf<Picker>(Picker.None) }

    // ── Las tres medidas de la hoja, y el desplazamiento que las une ──────────────────
    //
    // `huecoVisiblePx` es lo que se VE (se mide afuera del scroll), `contenidoPx` lo que hay
    // (adentro, con altura infinita) y `bodyHeightPx` cuánto de eso es el cuerpo del editor.
    // Las tres viven acá arriba porque el modificador de la Column que se desplaza y el alto
    // fijado del sub-picker las usan de los dos lados.
    var huecoVisiblePx by remember { mutableStateOf(0) }
    var contenidoPx by remember { mutableStateOf(0) }
    var bodyHeightPx by remember { mutableStateOf(0) }
    val cuerpoCompuesto = picker == Picker.None
    val sheetScroll = rememberScrollState()

    // Dónde estaba la hoja ANTES de abrir un sub-picker. Ver [abrirPicker].
    var scrollAntesDelPicker by remember { mutableStateOf(0) }

    /**
     * Abrir un sub-picker guardando el desplazamiento — la mitad de la disciplina de la Ola 8
     * que el scroll podía romper.
     *
     * Con la hoja quieta, abrir y cerrar «Nota» devolvía el teclado al mismo píxel porque no
     * había otro lugar donde ponerlo. Ahora la hoja se puede desplazar: si el dueño bajó hasta
     * el botón, abrió un sub-picker y lo cerró, el teclado volvería ARRIBA (el sub-picker mide
     * lo que el hueco, así que el desplazamiento se recorta a 0) y la tecla que estaba bajo su
     * dedo sería otra — exactamente el «escribías 0 y salía 8» de la Ola 8. Por eso se guarda
     * acá, en el toque, y no en el efecto de abajo: para cuando el efecto corre, el recorte ya
     * pasó y el valor viejo ya no existe.
     */
    fun abrirPicker(destino: Picker) {
        scrollAntesDelPicker = sheetScroll.value
        picker = destino
    }

    LaunchedEffect(picker) {
        if (picker != Picker.None) {
            // Que el sub-picker se vea desde su encabezado —su título y su X— y no desde la
            // mitad. Casi siempre ya está en 0 porque el alto fijado deja el contenido del
            // tamaño del hueco; esto cubre el caso en que el sub-picker es más alto que el hueco.
            sheetScroll.scrollTo(0)
        } else if (scrollAntesDelPicker > 0) {
            // Un cuadro de espera: el cuerpo tiene que volver a medirse antes de restaurar, si
            // no el valor se recorta contra el máximo viejo (el del sub-picker) y no restaura nada.
            withFrameNanos { }
            sheetScroll.scrollTo(scrollAntesDelPicker)
            scrollAntesDelPicker = 0
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var accountsRefreshKey by remember { mutableStateOf(0) }
    // «No tienes cuentas» solo se afirma cuando la lista llegó y vino vacía. Antes de eso —o si
    // la llamada falló— no se sabe, y decírselo a alguien con cinco cuentas porque el server
    // tardó es la clase de mentira que esta ola vino a sacar.
    var accountsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(accountsRefreshKey) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { list ->
                accountsLoaded = true
                accounts = list
                // Ola 11: la que el dueño acaba de elegir con el dedo no se toca —salvo que ya
                // no exista—, y para todo lo demás decide [resolverCuenta]: contexto, después la
                // última usada, y recién después la primera de la lista. Antes acá había un
                // `list.firstOrNull()` a secas, y «la primera» no estaba definida en ninguna
                // parte (ni el server ni SQLDelight ordenaban): la cuenta preseleccionada podía
                // cambiar sola entre sesiones, sin que nada cambiara a la vista.
                // **Anotado, no arreglado (B2 de la revisión):** este efecto también corre
                // después de crear una cuenta desde esta misma hoja (`accountsRefreshKey++`), y
                // ahí puede MOVER la preselección — master conservaba la que estuviera. Pasa
                // solo si el dueño no había elegido a mano, y el cambio se ve (la fila dice el
                // nombre nuevo con su «Por defecto»), así que probablemente sea mejor así: quien
                // acaba de crear una cuenta suele querer estrenarla. Queda escrito porque es un
                // cambio de comportamiento que ningún otro comentario nombra.
                val eleccionFirme = origenCuenta == OrigenCuenta.ELEGIDA &&
                    list.any { it.id == selectedAccountId }
                if (!eleccionFirme) {
                    val elegida = resolverCuenta(
                        cuentas = list,
                        contexto = presetAccountId,
                        ultima = LastAccountStore.lastAccountId,
                    )
                    selectedAccountId = elegida.id
                    origenCuenta = elegida.origen
                }
            }
    }

    // Ola 10: las preferencias llegan del server (dentro del resumen del Inicio) y pueden
    // aparecer DESPUÉS de que esta hoja se compuso. Se leen como estado para que la
    // reconciliación de abajo vuelva a correr cuando lleguen — si no, la hoja abierta antes de
    // que cargaran se quedaría con lo que el dueño ya cambió.
    val categoryPrefs = UsedCategoriesCache.prefs
    val usedCategories = UsedCategoriesCache.used

    LaunchedEffect(typeIndex, categoryPrefs) {
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
        if (typeIndex > 1) return@LaunchedEffect
        val newType = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME
        if (!categoriaSirveParaTipo(category, newType, usedCategories, categoryPrefs)) {
            category = categoriaPorDefectoPara(newType, usedCategories, categoryPrefs)
        }
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

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        // Ola 2 #2: recortada — canSave ya exige no-vacío, pero "  Comida  " pasaba esa guarda
        // y se guardaba con espacios.
        val trimmedCategory = category.trim()
        coroutine.launch {
            val event = FinancialEvent(
                // Generado acá, no en blanco: en Android/iOS Repositories.wallets es
                // LocalRepository (offline-first), que inserta por PK id con INSERT OR REPLACE.
                // Con id = "" cada evento nuevo reemplazaba al anterior en el teléfono en vez de
                // agregarse (Hallazgo Crítico de la revisión de la Ola 1). Ver newId().
                id = newId("ev"),
                accountId = selectedAccountId ?: accounts.firstOrNull()?.id ?: "acc_1",
                type = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                amount = amount.toLongOrNull() ?: 0L,
                category = trimmedCategory,
                description = note.ifBlank { trimmedCategory },
                source = EventSource.MANUAL,
                // F12: lo anotado a mano ya está confirmado por definición — "por confirmar" es
                // solo para lo que entra solo (SMS, OCR, extracto), no para lo que el usuario
                // acaba de escribir con sus propios dedos. Sin esto caía en el default
                // UNCONFIRMED y desaparecía de "Gastos", que excluye lo pendiente.
                reconciliationStatus = ReconciliationStatus.RECONCILED,
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )
            val result = runCatching { Repositories.wallets.postEvent(event) }
            saving = false
            result.onSuccess {
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
                    .background(MinSurfaceContainerHigh)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {},
            ) {
                // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
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
                //   cuerpo del editor «Gasto»       678 dp
                //   + selector de tipo y respiro     63 dp  →  741 dp que se desplazan
                //   + manija con su X                52 dp  →  793 dp de hoja
                //   hueco = alto de la ventana − 56 dp de barra inferior − 52 dp de manija
                //
                //   navegador 800×1000  → hueco 741 dp → desborde   0 dp (entra justo)
                //   navegador 375×812   → hueco 704 dp → desborde  37 dp (cortaba «Falta el monto»)
                //   navegador 800×620   → hueco 512 dp → desborde 229 dp (corta en la fila «7 8 9»)
                //   AVD Movi_Sensor (411×731 dp) → hueco 551 dp → desborde 185 dp
                //
                // El AVD es el caso que importa: **la fila «7 8 9» es el último renglón visible y
                // «Guardar movimiento» queda entero afuera** — verificado a ojo en `Movi_Sensor`,
                // que es el AVD que manda usar la nota del proyecto. O sea que en el APK 1.7 que
                // el dueño ya tiene instalado, y en la PWA desplegada abierta desde un teléfono,
                // se podía llenar el formulario entero y quedarse sin forma de guardar. En el
                // iPhone donde se vio primero pasa lo mismo y con menos margen todavía, porque a
                // la barra inferior se le suman la barra de estado y el indicador de inicio.
                //
                // `verticalScroll` no mueve nada mientras el contenido entra: a 800×1000 el
                // `maxValue` del scroll es 0, así que no hay a dónde desplazarse.
                //
                // **El precio, dicho en voz alta.** La disciplina de la Ola 8 —la hoja no cambia
                // de alto, así que nada se mueve bajo el dedo— era estructural porque la hoja era
                // inamovible. Ahora lo que desborda se puede correr: 37 dp a 812 (tres cuartos de
                // una tecla, que miden 50 dp) y 185 dp en el AVD. Un ARRASTRE sobre el teclado que
                // pase el umbral desplaza en vez de teclear, y el toque siguiente en el mismo punto
                // cae en otra tecla: el modo de falla exacto de la Ola 8. Con toques no se
                // consiguió provocar un dígito equivocado (un toque no llega al umbral), así que
                // es RIESGO, no defecto observado. Lo que sí quedó cerrado con código es el viaje
                // de ida y vuelta a un sub-picker (ver [abrirPicker]), que era el camino seguro a
                // que el teclado se moviera. Si el arrastre llega a doler, el arreglo barato es
                // que el área del teclado no desplace (un `pointerInput` que consuma el arrastre
                // vertical ahí), no sacar el scroll y volver a dejar el botón inalcanzable.
                //
                // **Por qué el scroll va en una Column interna con `weight(1f, fill = false)`.**
                // Es el idioma de las otras cinco hojas que se desplazan (`EditProfileSheet`,
                // `CreditTermsSheet`, `CardTermsSheet`, `ChangePasswordSheet`,
                // `CreateRecurringRuleSheet`), y de paso deja la manija con su X afuera del
                // desplazamiento. **Ojo con el atajo de pegar ese modificador en la Column de la
                // hoja**: ahí NO es equivalente, porque su hermano es el `Box(weight(1f))` que la
                // empuja contra el borde, y dos hijos con peso se reparten el alto. Probado: con
                // el peso puesto en la Column de la hoja, a 800×1000 el hueco cae de 741 a 424 dp
                // —la mitad— y el teclado entero queda fuera de la pantalla en una ventana donde
                // hoy entra todo.
                //
                // Los dos sub-pickers que traen su propio scroll (la lista de cuentas de
                // [WalletPicker], `heightIn(max = 360.dp)`, y las sugerencias de `CategoryField`,
                // `heightIn(max = 220.dp)`) ya tienen alto acotado ANTES de su scroll, así que no
                // reciben la altura infinita de este contenedor.
                Column(
                    modifier = Modifier
                        // AFUERA del scroll: el alto que la hoja ocupa DE VERDAD en pantalla.
                        .onSizeChanged { huecoVisiblePx = it.height }
                        .verticalScroll(sheetScroll)
                        .weight(1f, fill = false)
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
                    //    hace nada. Es geometría garantizada, no un margen a ojo: mientras el
                    //    selector de tipo esté afuera, no hay control suyo bajo la X.
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
                        labels = listOf("Gasto", "Ingreso", "Traspaso"),
                        selected = typeIndex,
                        onSelect = { typeIndex = it },
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
                            .then(if (picker == Picker.None) Modifier else Modifier.heightIn(min = pinnedHeight)),
                    ) {
                    when (picker) {
                        // F35: campo libre con sugerencias en vez de una lista fija — tocar una
                        // sugerencia cierra el sub-picker igual que antes (onSuggestionPicked);
                        // escribir una categoría nueva la deja tal cual, sin forzar a elegir.
                        Picker.Category -> Column(modifier = Modifier.fillMaxWidth()) {
                            PickerHeader("Categoría", onClose = { picker = Picker.None })
                            // Ola 2 #3c: sin esto el sub-picker se abría con el campo prellenado
                            // ("Comida") pero sin foco — había que tocarlo a mano para ver las
                            // sugerencias o poder escribir.
                            val categoryFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) { categoryFocusRequester.requestFocus() }
                            CategoryField(
                                value = category,
                                onValueChange = { category = it },
                                type = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                                usedCategories = usedCategories,
                                prefs = categoryPrefs,
                                label = null,
                                onSuggestionPicked = { picker = Picker.None },
                                focusRequester = categoryFocusRequester,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Picker.Wallet -> WalletPicker(
                            accounts = accounts,
                            selectedId = selectedAccountId,
                            onPick = {
                                selectedAccountId = it
                                // Elegida a mano: la reconciliación de arriba ya no la pisa, y el
                                // aviso «Última usada» desaparece — ya no lo decidió la app.
                                origenCuenta = OrigenCuenta.ELEGIDA
                                picker = Picker.None
                            },
                            onClose = { picker = Picker.None },
                        )
                        Picker.Note -> NoteEditor(
                            initial = note,
                            onSave = { note = it; picker = Picker.None },
                            onClose = { picker = Picker.None },
                        )
                        Picker.None -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // El alto que los sub-pickers van a respetar (ver el comentario de
                                // arriba). Se mide acá y no se calcula a mano: así sigue siendo
                                // correcto si mañana el cuerpo gana o pierde una fila.
                                .onSizeChanged { bodyHeightPx = it.height },
                        ) {
                            if (typeIndex == 2) {
                                TransferBody(
                                    accounts = accounts,
                                    accountsLoaded = accountsLoaded,
                                    // Ola 11: si la hoja se abrió desde el detalle de una cuenta, ese
                                    // contexto vale también para el ORIGEN del traspaso — es la
                                    // cuenta que el dueño estaba mirando cuando tocó «Agregar».
                                    presetAccountId = presetAccountId,
                                    onSaved = onSaved,
                                )
                            } else {
                                EditorBody(
                            amount = amount,
                            onKey = ::onKey,
                            category = category,
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
                            onPickCategory = { abrirPicker(Picker.Category) },
                            onPickWallet = { abrirPicker(Picker.Wallet) },
                            onEditNote = { abrirPicker(Picker.Note) },
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
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isActive = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MinText else MinTextDim,
                    letterSpacing = 0.1.sp,
                )
            }
        }
    }
}

@Composable
private fun EditorBody(
    amount: String,
    onKey: (String) -> Unit,
    category: String,
    walletLabel: String,
    walletHint: String? = null,
    /** Si el renglón del aviso ocupa su lugar aunque hoy no diga nada — ver la fila «Cuenta». */
    walletHintReserved: Boolean = false,
    note: String,
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
    Spacer(Modifier.height(22.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // F14: mismo arreglo que Presupuestos — separador de miles mientras se escribe,
            // no solo al guardar (formatAmountKeypadDisplay respeta el "." decimal de este teclado).
            text = "$" + formatAmountKeypadDisplay(amount),
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            color = MinText,
            letterSpacing = (-2.2).sp,
            lineHeight = 56.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("COP", fontSize = 12.sp, color = MinTextMute, letterSpacing = 0.4.sp)
    }

    Spacer(Modifier.height(18.dp))

    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        CardRow(
            left = { Text("Categoría", fontSize = 14.5.sp, color = MinTextMute) },
            right = {
                Text(
                    text = category,
                    fontSize = 14.5.sp,
                    color = MinText,
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
        )
        CardRow(
            left = {
                Text("Cuenta", fontSize = 14.5.sp, color = MinTextMute)
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
                    fontSize = 14.5.sp,
                    color = MinText,
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
            left = { Text("Nota", fontSize = 14.5.sp, color = MinTextMute) },
            right = {
                Text(
                    text = note.ifBlank { "Agregar nota…" },
                    fontSize = 14.5.sp,
                    color = if (note.isBlank()) MinTextFaint else MinText,
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
            Text(error, fontSize = 12.sp, color = MinExpense)
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
                            Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = MinText, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = key,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                                color = MinText,
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
                fontSize = 13.sp,
                color = MinTextMute,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MinPrimaryContainer)
                    .clickable { onCreateAccount() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+ Crear cuenta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinOnPrimaryContainer,
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
                    .border(1.dp, MinBorderStrong, RoundedCornerShape(16.dp))
                    .clickable { onOcr() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Escanear recibo", tint = MinText, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                    .clickable(enabled = canSave) { onSave() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Guardando…" else "Guardar movimiento",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
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
                    fontSize = 12.sp,
                    color = MinTextMute,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
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
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MinTextFaint,
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
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MinText)
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MinSurfaceContainerLow)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = MinText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WalletPicker(
    accounts: List<com.jvillada.movi.shared.model.Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader("Cuenta", onClose)
        if (accounts.isEmpty()) {
            // F10: este picker ya no debería ser alcanzable sin cuentas (ver el onClick de la
            // fila "Cuenta" en EditorBody), pero el texto no miente si de todos modos se llega.
            Text("No tienes cuentas todavía.", fontSize = 14.sp, color = MinTextMute, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(accounts) { account ->
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
                                fontSize = 15.sp,
                                color = MinText,
                                fontWeight = if (account.id == selectedId) FontWeight.Medium else FontWeight.Normal,
                            )
                            Text(
                                // Ola 8: iba crudo — «$3500000» en vez de «$3.500.000». Es la
                                // pantalla donde el dueño elige de qué cuenta sale la plata, así
                                // que el saldo tiene que leerse de un vistazo.
                                formatCOP(account.balance),
                                fontSize = 12.sp,
                                color = MinTextMute,
                            )
                        }
                        if (account.id == selectedId) Icon(Icons.Rounded.Check, contentDescription = null, tint = MinText, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
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
                .background(MinSurfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                cursorBrush = SolidColor(MinText),
                textStyle = TextStyle(color = MinText, fontSize = 14.sp),
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
                    .focusRequester(noteFocusRequester)
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
                        Text("Concepto del movimiento", fontSize = 14.sp, color = MinTextMute)
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
                .background(MinPrimaryContainer)
                .clickable { onSave(value.text.trim()) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Guardar nota", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinOnPrimaryContainer)
        }
    }
}
