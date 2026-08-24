package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
) {
    val coroutine = rememberCoroutineScope()
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // F35: arranca en la primera categoría predefinida de Gastos, como antes arrancaba en
    // "Mercado" — ahora es texto libre con sugerencias (CategoryField), no una lista fija.
    var category by remember { mutableStateOf(PREDEFINED_CATEGORIES.first { it.type == "EXPENSE" }.name) }
    var accounts by remember { mutableStateOf<List<com.jvillada.movi.shared.model.Account>>(emptyList()) }
    // F10: "+ Registrar el primero" desde el detalle de una cuenta trae esa cuenta ya elegida —
    // si no existiera (borrada entre medio) el efecto de abajo cae al primer accountId disponible.
    var selectedAccountId by remember { mutableStateOf(presetAccountId) }
    var picker by remember { mutableStateOf<Picker>(Picker.None) }
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
                if (selectedAccountId == null || list.none { it.id == selectedAccountId }) {
                    selectedAccountId = list.firstOrNull()?.id
                }
            }
    }

    LaunchedEffect(typeIndex) {
        // Con categoría libre (F35) ya no hay una lista fija de la que "salirse" al cambiar de
        // tipo — pero si la categoría actual SÍ es una predefinida del otro tipo (p. ej.
        // "Salario" al pasar a Gasto), seguir mostrándola confundiría. Una categoría escrita a
        // mano, o "BOTH", se deja tal cual: no hay forma de saber si tiene sentido para el
        // nuevo tipo.
        //
        // La pestaña Traspaso (índice 2) queda fuera: un traspaso no tiene categoría elegible —
        // la suya es reservada— así que no hay nada que reconciliar al entrar ni al salir.
        if (typeIndex > 1) return@LaunchedEffect
        val newType = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME
        val matched = PREDEFINED_CATEGORIES.find { it.name == category }
        if (matched != null && matched.type != newType.name && matched.type != "BOTH") {
            category = PREDEFINED_CATEGORIES.first { it.type == newType.name }.name
        }
    }

    fun onKey(key: String) {
        amount = when (key) {
            "⌫" -> if (amount.isNotEmpty()) amount.dropLast(1) else amount
            else -> if (amount.length < 12) amount + key else amount
        }
    }

    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    // Ola 2 #2: canSave no miraba la categoría — se podía guardar con la caja vacía.
    val canSave = parsedAmount > 0 && category.isNotBlank() && selectedAccountId != null && !saving
    // F24: mismo patrón que Presupuestos/Recurrentes — la primera cosa que falta.
    val missingFieldMessage = when {
        parsedAmount <= 0 -> "Falta el monto"
        category.isBlank() -> "Falta la categoría"
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
            // F35: si escribió una categoría nueva a mano, que ya aparezca como sugerencia
            // "usada" en el resto de la sesión sin esperar a que otra pantalla la cargue.
            result.onSuccess { UsedCategoriesCache.record(listOf(trimmedCategory)); onSaved() }
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
                SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

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
                // 1. **El alto.** Se recuerda el alto del cuerpo y se le pone como alto MÍNIMO
                //    al sub-picker, así la hoja mide siempre lo mismo y nada se teletransporta.
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

                var bodyHeightPx by remember { mutableStateOf(0) }
                val density = LocalDensity.current
                val pinnedHeight = with(density) { bodyHeightPx.toDp() }
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
                            usedCategories = UsedCategoriesCache.categories,
                            label = null,
                            onSuggestionPicked = { picker = Picker.None },
                            focusRequester = categoryFocusRequester,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Picker.Wallet -> WalletPicker(
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onPick = { selectedAccountId = it; picker = Picker.None },
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
                                onSaved = onSaved,
                            )
                        } else {
                            EditorBody(
                        amount = amount,
                        onKey = ::onKey,
                        category = category,
                        walletLabel = selectedAccount?.name ?: "Seleccionar cuenta",
                        note = note,
                        onPickCategory = { picker = Picker.Category },
                        onPickWallet = { picker = Picker.Wallet },
                        onEditNote = { picker = Picker.Note },
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
            right = { Text(category, fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
            showChevron = true,
            onClick = onPickCategory,
        )
        CardRow(
            left = { Text("Cuenta", fontSize = 14.5.sp, color = MinTextMute) },
            right = { Text(walletLabel, fontSize = 14.5.sp, color = MinText, fontWeight = FontWeight.Medium) },
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
                )
            },
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
