package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.CategoryField
import com.jvillada.movi.ui.components.categoriaPorDefectoPara
import com.jvillada.movi.ui.components.categoriaSirveParaTipo
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.VerTodasLasCuentas
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/**
 * La ÚNICA hoja para anotar algo que se repite todos los meses (Ola 8).
 *
 * Antes había dos —«Nuevo recurrente» y «Nueva suscripción»— y para elegir entre ellas el dueño
 * tenía que saber una distinción que solo existe adentro de Movi. Ahora hay una sola, y la
 * pregunta que decide dónde se guarda es una del mundo real, no del modelo de datos: **en qué
 * moneda te lo cobran**.
 *
 * - **En pesos** → una regla recurrente: ingreso o gasto, con categoría y con recordatorio.
 * - **En dólares** → una suscripción: las reglas recurrentes son COP puro (`RecurringRule` no
 *   tiene campo de moneda) y agregarle uno tocaría el modelo, la tabla, el barrido de avisos y
 *   los totales. El modelo de suscripciones YA es multi-moneda y ya convierte con la TRM del
 *   día (ver `resultFor` en `SubscriptionRoutes.kt`), así que un cobro en dólares va ahí.
 *
 * Lo que el dueño pierde al elegir dólares (categoría y recordatorio) se dice en la hoja, en
 * lugar de que los campos desaparezcan sin explicación.
 *
 * Editar es siempre editar una [RecurringRule] (las suscripciones no tienen hoja de edición,
 * se quitan desde la lista), así que en modo edición la moneda ni se muestra.
 */
@Composable
fun CreateRecurringRuleSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    existing: RecurringRule? = null,
    /**
     * Ola 9 · B: el formulario llega lleno con lo que el dueño acaba de anotar (nombre, monto,
     * categoría, tipo, cuenta y el día del mes tomado de la fecha del movimiento). **Todo se
     * puede corregir antes de guardar**: es un formulario prellenado, no una confirmación.
     */
    prefill: RecurringPrefill? = null,
) {
    val coroutine = rememberCoroutineScope()

    // Prefill state from existing rule when in edit mode
    var name by remember { mutableStateOf(existing?.name ?: prefill?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: prefill?.amount) }
    // Ola 11: el día se ELIGE, no se escribe (ver [DayOfMonthPicker]). Por eso es un `Int?` y no
    // una cadena: el estado ya no puede contener «45», así que no hay nada que validar después.
    var dayOfMonth by remember { mutableStateOf(existing?.dayOfMonth ?: prefill?.dayOfMonth) }
    var selectedType by remember {
        mutableStateOf(existing?.type ?: prefill?.type ?: TransactionType.EXPENSE)
    }
    // Ola 10 (revisión): una regla NUEVA arranca en «Otros» como siempre, salvo que el dueño la
    // haya escondido o fijado del otro lado — ahí se cae a la primera que sí se le va a ofrecer.
    // Mismo defecto que se corrigió en Agregar: un campo prellenado con una categoría escondida
    // se puede guardar de un toque, sin abrir el selector. Una regla que YA existe (o el prefill
    // de un movimiento) conserva la suya: esa la eligió una persona.
    var category by remember {
        val propia = existing?.category ?: prefill?.category
        val tipoInicial = existing?.type ?: prefill?.type ?: TransactionType.EXPENSE
        val prefsAhora = UsedCategoriesCache.prefs
        val usadasAhora = UsedCategoriesCache.used
        mutableStateOf(
            propia
                ?: "Otros".takeIf { categoriaSirveParaTipo(it, tipoInicial, usadasAhora, prefsAhora) }
                ?: categoriaPorDefectoPara(tipoInicial, usadasAhora, prefsAhora),
        )
    }
    // Ola 9 · D: ¿la categoría la eligió una persona? Con una regla que ya existe o con un
    // movimiento que la trae, sí — y entonces la sugerencia por nombre no la toca. Ver
    // [categoriaSugeridaPorNombre].
    var categoriaElegidaAMano by remember { mutableStateOf(existing != null || prefill != null) }
    // Ola 9 · D: a qué cuenta entra o de cuál sale. Opcional siempre (ver RecurringRule.accountId).
    var accountId by remember { mutableStateOf(existing?.accountId ?: prefill?.accountId) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var accountPickerOpen by remember { mutableStateOf(false) }
    // ¿El `accountId = null` de acá arriba lo puso el dueño tocando «Sin cuenta», o es que la
    // regla nunca tuvo cuenta? Es la diferencia entre «quítala» y «no la toques» en el wire —
    // ver [cuentaParaElWire], que es lo único que puede producir el `""` destructivo.
    var elDuenoEligioSinCuenta by remember { mutableStateOf(false) }
    // «Sin cuentas todavía» solo se afirma cuando la lista llegó y vino vacía — mismo criterio
    // que `accountsLoaded` en la hoja de Agregar. Antes una lectura que todavía estaba en vuelo,
    // o que falló, se contaba como «no tienes ninguna»: la misma clase de mentira que esta rama
    // vino a sacar, en chiquito.
    var cuentasLeidas by remember { mutableStateOf(false) }
    var fallaronLasCuentas by remember { mutableStateOf(false) }
    // Marcada por defecto al crear; al editar refleja lo que está guardado.
    var remindMe by remember { mutableStateOf(existing?.remindMe ?: true) }
    var currency by remember { mutableStateOf("COP") }
    // ¿Elegir dólares le cambió al dueño un «Ingreso» que ya había marcado? (V11: la regla se
    // explica igual, pero si además le pisamos una elección suya, eso se avisa aparte.)
    var tipoCambiadoPorLaMoneda by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Una sola llamada, y solo cuando el dueño abrió esta hoja: no hay lista de cuentas
    // cacheada en `:shared` y el selector necesita los nombres. Si falla, el selector se queda
    // sin nombres y la hoja lo dice así (ver [AccountPickerField]) — la cuenta que la regla ya
    // tenía se conserva y se manda de vuelta tal cual.
    //
    // **Acá había un `if (list.none { it.id == accountId }) accountId = null`** y era la mitad
    // cliente de la pérdida de datos: la lista que no corroboraba la cuenta borraba la elección
    // del dueño, y el guardado convertía ese null en el `""` que le pide al server que la quite.
    // No alcanza con que la lista ahora llegue completa (ver `LocalRepository.getAccounts`): una
    // lectura que falle no puede tener permiso para borrar nada. Si la cuenta de verdad ya no
    // existe, el server lo resuelve solo — `accountIdIfOwned` guarda null y la respuesta lo dice.
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { list -> accounts = list; cuentasLeidas = true }
            .onFailure { fallaronLasCuentas = true }
    }

    // Ola 9 · D: si el nombre coincide con una categoría del catálogo, se propone esa en vez de
    // dejar el genérico «Otros». Solo mientras nadie haya tocado la categoría a mano.
    //
    // La propuesta también se DESHACE. Escribir «Comida» y después cambiar el nombre a
    // «Netflix» dejaba la categoría en «Comida» —una que el dueño nunca eligió— porque la
    // sugerencia solo sabía asignar. Se recuerda cuál fue la última propuesta y, si el nombre
    // nuevo no propone nada y la categoría sigue siendo exactamente esa, se vuelve al genérico.
    var categoriaPropuesta by remember { mutableStateOf<String?>(null) }
    // Ola 10: lo que el dueño decidió en «Más → Categorías» entra en la propuesta. Se leen como
    // estado para que la hoja abierta antes de que las preferencias llegaran también las respete.
    val categoryPrefs = UsedCategoriesCache.prefs
    val usedCategories = UsedCategoriesCache.used
    LaunchedEffect(name, selectedType, categoryPrefs) {
        if (categoriaElegidaAMano) return@LaunchedEffect
        val sugerida = categoriaSugeridaPorNombre(name, selectedType, usedCategories, categoryPrefs)
        // El genérico sigue siendo «Otros», salvo que el dueño la haya escondido o fijado del otro
        // lado — ahí caer igual en ella sería volver a poner justo lo que sacó de la vista.
        val generica = if (categoriaSirveParaTipo("Otros", selectedType, usedCategories, categoryPrefs)) "Otros"
        else categoriaPorDefectoPara(selectedType, usedCategories, categoryPrefs)
        when {
            sugerida != null -> { category = sugerida; categoriaPropuesta = sugerida }
            category == categoriaPropuesta -> { category = generica; categoriaPropuesta = null }
        }
    }

    val isEditMode = existing != null
    // Editar es editar una regla; solo al crear se puede elegir dólares (ver KDoc).
    val enDolares = !isEditMode && currency == "USD"
    val canSave = name.isNotBlank() && (amount ?: 0L) > 0L && (dayOfMonth ?: 0) in 1..31 && !saving
    // F24: mismo patrón que las demás hojas de crear — la primera cosa que falta, no un botón
    // gris sin explicación.
    val missingFieldMessage = when {
        name.isBlank() -> "Falta el nombre"
        (amount ?: 0L) <= 0L -> "Falta el monto"
        // Ya no puede decir «entre 1 y 31»: con la cuadrícula, un día fuera de rango no existe.
        // Lo único que puede faltar es que el dueño todavía no haya tocado ninguno.
        (dayOfMonth ?: 0) !in 1..31 -> "Falta el día del mes"
        else -> null
    }

    fun save() {
        if (!canSave) return
        val day = dayOfMonth?.coerceIn(1, 31) ?: return
        val amt = amount ?: return
        saving = true
        error = null
        coroutine.launch {
            val result = when {
                // Dólares → suscripción (el único modelo multi-moneda que hay). Nace CONFIRMED
                // del lado del server: la escribió el dueño, no hay nada que confirmar.
                enDolares -> runCatching {
                    Repositories.wallets.createSubscription(
                        CreateSubscriptionRequest(
                            displayName = name.trim(),
                            amount = amt,
                            currency = currency,
                            dayOfMonth = day,
                        )
                    )
                }
                else -> {
                    val rule = RecurringRule(
                        id = existing?.id ?: "",
                        name = name.trim(),
                        category = category.trim().ifBlank { "Otros" },
                        amount = amt,
                        dayOfMonth = day,
                        type = selectedType,
                        // Un INGRESO nunca entra al barrido de avisos (`selectDueForReminder`
                        // solo mira gastos) y la hoja ni siquiera ofrece la casilla, así que
                        // guardar `true` dejaba en la fila una intención que nadie expresó — y
                        // que se leía como «pedí que me avisaran» desde cualquier consulta.
                        //
                        // Cambiar de tipo DENTRO de la hoja no pierde nada: `remindMe` es estado
                        // de la hoja y nadie lo toca al mover el chip, así que ir a Ingreso y
                        // volver a Gasto deja la casilla como estaba. Lo único que se pierde es
                        // al GUARDAR como ingreso: ahí queda `false` en la fila, y si más
                        // adelante se reabre y se pasa a Gasto, la casilla arranca desmarcada.
                        // Es un camino angosto y el precio de que la base no mienta.
                        remindMe = selectedType == TransactionType.EXPENSE && remindMe,
                        // Los tres estados del wire, uno a uno con los tres de la hoja: un id,
                        // `""` SOLO si el dueño tocó «Sin cuenta», y `null` («no lo toques») si
                        // acá no se habló de cuentas. Ver [cuentaParaElWire] — antes esto era
                        // `accountId ?: ""` y una lectura fallida bastaba para borrar la cuenta.
                        accountId = cuentaParaElWire(accountId, elDuenoEligioSinCuenta),
                    )
                    if (isEditMode) {
                        runCatching { Repositories.wallets.updateRecurringRule(existing!!.id, rule) }
                    } else {
                        runCatching { Repositories.wallets.createRecurringRule(rule) }
                    }
                }
            }
            saving = false
            result.onSuccess { onSaved() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    fun delete() {
        if (existing == null || saving) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteRecurringRule(existing.id) }
            saving = false
            result.onSuccess { onSaved() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    // La hoja crecía sin techo y el contenido que no entraba quedaba recortado, sin scroll: en
    // una laptop (1280×860) el caso más común —un GASTO, que suma las secciones de categoría y
    // recordatorio— dejaba «Crear recurrente» debajo del borde y el recurrente era, lisa y
    // llanamente, imposible de crear. Ahora la hoja se topa en el 92% de la altura disponible,
    // los campos scrollean, y la manija y el botón quedan FIJOS: el botón es siempre alcanzable
    // sin depender de que el usuario descubra que hay que rodar.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val altoMaximoDeLaHoja = maxHeight * 0.92f
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
                    .heightIn(max = altoMaximoDeLaHoja)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MinSurfaceContainerHigh)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {},
            ) {
                // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
                SheetHandleWithClose(onClose = onDismiss, enabled = !saving)

                // Solo los CAMPOS scrollean; `fill = false` para que una hoja corta siga
                // midiendo lo que ocupa en vez de estirarse hasta el tope.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Title row: sheet title + optional delete action in edit mode
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isEditMode) "Editar recurrente" else "Nuevo recurrente",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                            modifier = Modifier.weight(1f),
                        )
                        if (isEditMode) {
                            Text(
                                text = if (saving) "…" else "Eliminar",
                                fontSize = 13.sp,
                                color = MinExpense,
                                modifier = Modifier.clickable(enabled = !saving) { delete() },
                            )
                        }
                    }

                    // --- NOMBRE ---
                    SheetSectionLabel("NOMBRE")
                    Spacer(Modifier.height(8.dp))
                    SheetInputBox {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            cursorBrush = SolidColor(MinText),
                            textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text("Ej: Arriendo, Netflix, Gym", fontSize = 14.sp, color = MinTextMute)
                                }
                                inner()
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // --- MONEDA ---
                    // La única pregunta que decide dónde se guarda esto, y es una pregunta del mundo
                    // real: cualquiera sabe si le cobran en pesos o en dólares. Al editar no aparece —
                    // editar es siempre editar una regla (ver KDoc).
                    if (!isEditMode) {
                        SheetSectionLabel("MONEDA")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SheetChip(
                                label = "Pesos",
                                selected = currency == "COP",
                                onClick = { currency = "COP"; tipoCambiadoPorLaMoneda = false },
                            )
                            SheetChip(
                                label = "Dólares",
                                selected = currency == "USD",
                                onClick = {
                                    currency = "USD"
                                    // En dólares esto se guarda como suscripción, y una suscripción es
                                    // SIEMPRE un cobro. Forzar el tipo al tocar el chip (y no callarlo
                                    // al guardar) es lo que evita el peor caso: elegir Ingreso, después
                                    // Dólares, y que se guarde un gasto — el flujo libre se movía al
                                    // revés por el doble del monto, sin que nada lo dijera.
                                    // Si eso le pisó una elección al dueño, se le dice (ver la nota).
                                    tipoCambiadoPorLaMoneda = selectedType == TransactionType.INCOME
                                    selectedType = TransactionType.EXPENSE
                                },
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // --- MONTO ---
                    SheetSectionLabel("MONTO")
                    Spacer(Modifier.height(8.dp))
                    // V12: en Colombia "$20" se lee veinte pesos. Si el cobro es en dólares, el campo
                    // lo dice mientras se escribe — igual que después lo dice la fila de la lista.
                    MoneyField(
                        value = amount,
                        onValueChange = { amount = it },
                        prefix = if (enDolares) "US$" else "$",
                        placeholder = if (enDolares) "US$ 0" else "$ 0",
                    )

                    Spacer(Modifier.height(18.dp))

                    // --- DÍA DEL MES ---
                    SheetSectionLabel("DÍA DEL MES")
                    Spacer(Modifier.height(8.dp))
                    DayOfMonthPicker(
                        selected = dayOfMonth,
                        enabled = !saving,
                        onPick = { dayOfMonth = it },
                    )

                    Spacer(Modifier.height(18.dp))

                    // --- TIPO ---
                    // Siempre visible, también en dólares. Ahí «Ingreso» queda deshabilitado en vez de
                    // desaparecer: el dueño VE que la opción existe y que no aplica, en lugar de elegirla
                    // y que Movi la cambie por atrás. (Un ingreso recurrente en dólares no se puede
                    // registrar hoy — el modelo de suscripciones es de cobros. Ver la nota de abajo.)
                    SheetSectionLabel("TIPO")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SheetChip(
                            label = "Gasto",
                            selected = selectedType == TransactionType.EXPENSE,
                            onClick = { selectedType = TransactionType.EXPENSE },
                        )
                        SheetChip(
                            label = "Ingreso",
                            selected = selectedType == TransactionType.INCOME,
                            enabled = !enDolares,
                            onClick = { selectedType = TransactionType.INCOME },
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (enDolares) {
                        // No se ocultan los campos en silencio: en dólares esto se guarda como
                        // suscripción, y una suscripción es siempre un cobro, sin categoría y sin
                        // recordatorio. Decirlo es más honesto que hacer desaparecer tres secciones.
                        if (tipoCambiadoPorLaMoneda) {
                            Text(
                                text = "Cambiamos el tipo a Gasto: en dólares solo podemos anotar cobros.",
                                fontSize = 12.sp,
                                color = MinWarn,
                                lineHeight = 17.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = "En dólares solo podemos anotar cobros, y guardamos únicamente el " +
                                "nombre, el monto y el día: sin categoría y sin recordatorio. Para el " +
                                "total del mes lo convertimos a pesos con la tasa de cambio más " +
                                "reciente que pudimos consultar.",
                            fontSize = 12.sp,
                            color = MinTextMute,
                            lineHeight = 17.sp,
                        )
                    } else {
                        // --- CATEGORÍA ---
                        // F35: campo libre con sugerencias — antes arrancaba en "Otros" sin ninguna ayuda.
                        // Filtra por el tipo elegido arriba (Gasto/Ingreso) para no sugerir "Salario" en
                        // una regla de gasto.
                        CategoryField(
                            value = category,
                            onValueChange = { category = it; categoriaElegidaAMano = true },
                            type = selectedType,
                            usedCategories = usedCategories,
                            prefs = categoryPrefs,
                            label = "CATEGORÍA",
                            placeholder = "Ej: Vivienda, Suscripción, Salud",
                        )

                        Spacer(Modifier.height(18.dp))

                        // --- CUENTA (opcional) ---
                        AccountPickerField(
                            accounts = accounts,
                            // Ola 15: una regla de gasto se cobra de donde sale plata (efectivo,
                            // banco, tarjeta) y una de ingreso entra donde entra plata (efectivo,
                            // banco, inversión). Mismo criterio que la hoja de «Agregar», y por
                            // eso sale de la misma función de `:core` en vez de repetirse acá.
                            uso = if (selectedType == TransactionType.EXPENSE) {
                                UsoDeCuenta.ORIGEN_DE_GASTO
                            } else {
                                UsoDeCuenta.DESTINO_DE_INGRESO
                            },
                            cuentasLeidas = cuentasLeidas,
                            fallaronLasCuentas = fallaronLasCuentas,
                            selectedId = accountId,
                            open = accountPickerOpen,
                            enabled = !saving,
                            onToggle = { accountPickerOpen = !accountPickerOpen },
                            onPick = {
                                accountId = it
                                // Tocar «Sin cuenta» (it == null) es la ÚNICA forma de pedir que
                                // se quite la cuenta. Volver a elegir una cuenta lo deshace.
                                elDuenoEligioSinCuenta = it == null
                                accountPickerOpen = false
                            },
                        )

                        Spacer(Modifier.height(18.dp))

                        // --- RECORDATORIO ---
                        // Una regla de INGRESO no genera recordatorio (el barrido solo mira gastos, ver
                        // selectDueForReminder), así que ofrecer la casilla ahí sería prometer un aviso que
                        // nunca sale. Se muestra solo en Gasto.
                        if (selectedType == TransactionType.EXPENSE) {
                            ReminderOptInField(
                                checked = remindMe,
                                onCheckedChange = { remindMe = it },
                                enabled = !saving,
                            )
                        } else {
                            // V10: antes la casilla simplemente se esfumaba al tocar «Ingreso». Que un
                            // control desaparezca sin decir nada deja al dueño preguntándose si lo
                            // imaginó; se explica, igual que se explica lo que se pierde en dólares.
                            Text(
                                text = "Los recordatorios son para lo que tienes que pagar, así que un " +
                                    "ingreso no lleva aviso.",
                                fontSize = 12.sp,
                                color = MinTextMute,
                                lineHeight = 17.sp,
                            )
                        }
                    }

                }

                Spacer(Modifier.height(20.dp))

                // El error del guardado va FIJO, junto al botón, y no dentro del área que
                // scrollea. En el caso Gasto —el que desborda— el contenido queda rodado hacia
                // arriba y el dueño nunca baja, porque el botón ya es fijo: si el POST fallaba,
                // el texto rojo se agregaba al fondo, fuera de vista, y el botón volvía a decir
                // «Crear recurrente». Tocaba, no pasaba nada, y volvía a tocar.
                if (error != null) {
                    Text(
                        text = error!!,
                        fontSize = 12.sp,
                        color = MinExpense,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // --- CTA --- (fijo al pie, fuera del área que scrollea)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                        .clickable(enabled = canSave) { save() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            saving       -> if (isEditMode) "Guardando…" else "Creando…"
                            isEditMode   -> "Guardar cambios"
                            else         -> "Crear recurrente"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
                    )
                }
                if (!canSave && !saving && missingFieldMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = missingFieldMessage,
                        fontSize = 12.sp,
                        color = MinTextMute,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/**
 * Ola 9 · D — **de qué cuenta sale (o a cuál entra) esto todos los meses.**
 *
 * Un movimiento siempre tuvo cuenta y un recurrente no, así que Movi sabía que el salario entra
 * el 25 pero no dónde. El selector es **opcional a propósito**: «Sin cuenta» es una opción de
 * primera clase y no un error — las reglas que el dueño ya tiene nacieron sin cuenta y nadie
 * puede exigirle ahora un dato que nunca se le pidió.
 *
 * Se abre y se cierra en su lugar, sin hoja encima de la hoja: esta ya vive dentro de un
 * `verticalScroll` y una modal sobre otra modal, en el navegador angosto, es una trampa.
 */
@Composable
private fun AccountPickerField(
    accounts: List<Account>,
    /** Para qué se elige la cuenta: de una regla de gasto sale plata, a una de ingreso entra. */
    uso: UsoDeCuenta,
    /** ¿La lista de cuentas llegó? Con `false`, `accounts` vacía significa «no se sabe». */
    cuentasLeidas: Boolean,
    /** ¿La lectura falló? Distingue «todavía no llegó» de «no va a llegar». */
    fallaronLasCuentas: Boolean,
    selectedId: String?,
    open: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    // La regla tiene cuenta pero no pudimos resolverle el nombre (la lista no llegó). El campo
    // lo dice así en vez de mentir con «Sin cuenta»: la cuenta sigue puesta y se guarda igual.
    val cuentaSinNombre = selected == null && selectedId != null
    // Con una cuenta puesta el selector se abre aunque la lista esté vacía: es el único lugar
    // desde donde el dueño puede pedir «Sin cuenta», y esa elección no puede depender de que una
    // lectura de red haya salido bien.
    val sePuedeElegir = accounts.isNotEmpty() || selectedId != null
    // Ola 15 — las que sirven arriba, el resto detrás de «Ver todas». `conservar` es lo que hace
    // que una regla vieja apuntada a una cuenta que hoy no se ofrecería siga mostrando SU cuenta,
    // marcada: esta hoja ya perdió datos una vez por dejar de reconocer una cuenta puesta (ver el
    // comentario de la carga, más arriba), y esconderla del selector sería el mismo error con otra
    // cara.
    //
    // Se calcula acá arriba —y no adentro del `if (open)`— porque de él sale el estado inicial del
    // pie: ver la línea siguiente.
    val cuentas = cuentasPara(accounts, uso, conservar = selectedId)
    // Se pliega cada vez que el selector se abre o se cierra: la lista corta es la respuesta
    // normal, y la larga es una excepción que se pide, no un estado en el que uno se queda.
    //
    // **Salvo que arriba no quede ninguna**, igual que en la hoja de «Agregar»: si `principales`
    // está vacía, el selector abriría mostrando solo «Sin cuenta» y un renglón que parece un pie de
    // página. Y la llave `principales.isEmpty()` está por lo mismo que allá: sin ella el valor se
    // congela en la primera composición, y el caso real es justamente que las cuentas lleguen
    // tarde.
    var verTodas by remember(open, cuentas.principales.isEmpty()) {
        mutableStateOf(cuentas.principales.isEmpty())
    }
    SheetSectionLabel("CUENTA (OPCIONAL)")
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && sePuedeElegir, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    selected != null -> selected.name
                    cuentaSinNombre -> "Se conserva la cuenta elegida"
                    fallaronLasCuentas -> "No pudimos cargar tus cuentas"
                    !cuentasLeidas -> "Cargando tus cuentas…"
                    accounts.isEmpty() -> "Sin cuentas todavía"
                    else -> "Sin cuenta"
                },
                fontSize = 14.sp,
                color = if (selected != null) MinText else MinTextMute,
                modifier = Modifier.weight(1f),
            )
            if (sePuedeElegir) {
                Text(if (open) "Cerrar" else "Elegir", fontSize = 12.sp, color = MinTextMute)
            }
        }
    }
    if (open && sePuedeElegir) {
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerHigh)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp)),
        ) {
            AccountPickerRow(label = "Sin cuenta", selected = selectedId == null) { onPick(null) }
            val visibles = if (verTodas) cuentas.todas else cuentas.principales
            visibles.forEach { account ->
                AccountPickerRow(label = account.name, selected = account.id == selectedId) {
                    onPick(account.id)
                }
            }
            // **El mismo pie que la hoja de «Agregar», porque es el mismo componente.** Acá vivía
            // una copia pobre: no se podía volver a plegar, no traía la línea que explica por qué
            // esas cuentas no estaban arriba, y no contemplaba el caso de que arriba no quedara
            // ninguna. Dos versiones del mismo pie con comportamientos distintos es exactamente el
            // patrón que esta rama dice estar eliminando, un nivel más arriba.
            if (cuentas.hayOtras) {
                VerTodasLasCuentas(
                    expandido = verTodas,
                    cuantas = cuentas.otras.size,
                    uso = uso,
                    onToggle = { verTodas = !verTodas },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) MinOnPrimaryContainer else MinText,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MinPrimaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SheetInputBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun RowScope.SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(
                if (!selected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(10.dp)) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> MinTextFaint
                selected -> MinOnPrimaryContainer
                else     -> MinTextDim
            },
        )
    }
}

/**
 * **El día del mes se elige, no se escribe.**
 *
 * Era un campo de texto libre: se escribía «45», la hoja lo aceptaba, y el dueño se enteraba
 * recién al tocar «Crear recurrente» («El día del mes tiene que estar entre 1 y 31»). Con una
 * cuadrícula de 1 a 31 —un mes de calendario— el valor inválido **no existe**: no hay nada que
 * validar porque no hay nada que escribir, y se resuelve con un toque y sin teclado.
 *
 * ## Por qué siempre abierta, y no un desplegable
 *
 * La forma obvia sería un campo que se abre al tocarlo, como el de CUENTA acá al lado. Se
 * descartó por dos razones, y la segunda es la que manda:
 *
 * 1. **Un toque.** Con un desplegable, elegir el día son dos gestos (abrir y tocar); el punto
 *    entero del cambio es que sea uno.
 * 2. **La cuadrícula no se mueve, y casi nunca mueve nada.** Esta sección está en el MEDIO de una
 *    hoja anclada abajo: un control que crece ~200dp al abrirse empujaría TIPO, CATEGORÍA, CUENTA
 *    y RECORDATORIO hacia abajo justo mientras el dedo está apoyado. La cuadrícula mide siempre
 *    lo mismo —cinco filas de siete, haya o no día elegido—, así que **lo que está bajo el dedo
 *    nunca se mueve**.
 *
 *    Lo de abajo tampoco, salvo en un caso: elegir 29, 30 o 31 estrena la nota de los meses
 *    cortos y empuja lo que sigue **29 px** (medido: con el 25, TIPO/Gasto/CATEGORÍA quedan en
 *    y=216/246/281; con el 31, en 245/275/310). Es el precio de decir la verdad justo cuando
 *    corresponde, y ocurre por debajo del dedo, no bajo él.
 *
 *    El campo de CUENTA sí crece al abrirse, y **no es aire lo que empuja**: debajo suyo está
 *    toda la sección RECORDATORIO (casilla, líneas de entrega y, si aplica, el cartel ámbar).
 *    Es un patrón que ya estaba en esta hoja y no lo trajo esta rama; se nombra para que nadie
 *    lo cite como precedente de que «abrir en su lugar no molesta».
 *
 * El alto no queda librado a nada: cinco filas fijas dentro del `verticalScroll` de la hoja, con
 * el botón «Crear recurrente» fuera del scroll (ver el comentario del `BoxWithConstraints`).
 */
@Composable
private fun DayOfMonthPicker(
    selected: Int?,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 5 filas × 7 columnas = 35 casillas para 31 días. Las cuatro sobrantes van como huecos
        // del mismo ancho para que la última fila no se estire: una cuadrícula que cambia de paso
        // en la última línea se lee como otra cosa.
        for (fila in 0 until 5) {
            if (fila > 0) Spacer(Modifier.height(6.dp))
            // 4dp entre columnas y no 6: son seis huecos, y cada 2dp que se les saca va a parar
            // al ancho de la casilla, que es la dimensión que en un teléfono no sobra.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (columna in 0 until 7) {
                    val dia = fila * 7 + columna + 1
                    if (dia <= 31) {
                        DayCell(day = dia, selected = dia == selected, enabled = enabled, onPick = onPick)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // La nota de los meses cortos, SOLO cuando aplica (ver [diaCortoHint]). Va debajo de la
        // cuadrícula: arriba movería la cuadrícula misma bajo el dedo al elegir un 29, 30 o 31.
        diaCortoHint(selected)?.let { nota ->
            Spacer(Modifier.height(8.dp))
            Text(text = nota, fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp)
        }
    }
}

/**
 * Una casilla de la cuadrícula. Sin borde: 31 recuadros dibujados serían más ruido que ayuda.
 *
 * **44dp de alto, y el ancho es lo que dan siete columnas.** En un teléfono de 375px, con los
 * 20dp de margen de la hoja a cada lado y 4dp entre columnas, cada casilla mide ~44,4 × 44 — por
 * debajo de los 48dp que recomienda Material. No es un descuido ni es evitable: siete columnas de
 * 48dp necesitan 336dp de casillas sobre 335 disponibles, así que **ningún calendario entra en
 * 48dp en este ancho** (el propio DatePicker de Material3 usa casillas de 40dp). Lo que sí se
 * podía elegir es el alto, y por eso se le dio 44 en vez de los 38 con los que nació: es la
 * dimensión que no está limitada por la geometría. Verificado a ojo a 375×812.
 */
@Composable
private fun RowScope.DayCell(day: Int, selected: Boolean, enabled: Boolean, onPick: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .clickable(enabled = enabled) { onPick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> MinTextFaint
                selected -> MinOnPrimaryContainer
                else -> MinTextDim
            },
        )
    }
}

/**
 * **Qué pasa con el 29, el 30 y el 31**, dicho antes de guardar y no descubierto en febrero.
 *
 * No es comportamiento nuevo: el server ya recorta la fecha al largo real del mes
 * (`occurrenceInMonth` usa `coerceIn(1, month.lengthOfMonth())`) y guarda el día tal cual, así
 * que el mes siguiente vuelve a expandirse al día elegido. Lo único que faltaba era decirlo —
 * hasta ahora el dueño elegía «31» sin que nada le contara qué iba a pasar en febrero.
 *
 * `null` para los días 1–28, que caen en todos los meses y no necesitan ninguna aclaración: una
 * nota permanente sería ruido en el caso normal.
 */
fun diaCortoHint(day: Int?): String? =
    if (day == null || day < 29) null
    else "En los meses que no llegan al $day, se toma el último día del mes; el siguiente vuelve al $day."
