package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditDisbursement
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.validateCreditDisbursement
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.recurrentes.ReminderOptInField
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch

/**
 * Crea o edita los términos de un crédito.
 * - [editing] != null → modo edición sobre ese crédito (cuenta fija, campos precargados, permite eliminar).
 * - [editing] == null → modo creación: crear una cuenta nueva es el flujo por defecto. Solo si
 *   existen cuentas LOAN sin términos en [candidates] aparece, arriba, una línea discreta que
 *   ofrece adjuntar los términos a una de esas cuentas en vez de crear una nueva (F25).
 *
 * ## Ola 16 — la hoja pregunta primero, y cambia de forma según la respuesta
 *
 * Al crear una cuenta nueva, lo PRIMERO es «¿acabas de recibir la plata de este crédito?», y de
 * ahí salen dos hojas distintas:
 *
 * - **«No, ya lo venía pagando»** → el camino de siempre, sin un solo cambio: pide la deuda
 *   actual (opcional) y no registra ningún movimiento. Es la foto de cómo entra a Movi un crédito
 *   que ya existía.
 * - **«Sí, me la acaban de depositar»** → desaparece «Deuda actual» y al final de la hoja aparece
 *   a qué cuenta entró la plata y cuánto (con el capital puesto por defecto). Las dos cosas —el
 *   crédito y su desembolso— se guardan **en un solo POST atómico**.
 *
 * El porqué de la atomicidad, y el de que la diferencia entre el capital y lo que entró se cargue
 * como deuda, están en `CreateCreditRequest.disbursement`. El porqué de la redacción está donde se
 * pinta la pregunta, más abajo.
 */
@Composable
fun CreditTermsSheet(
    editing: CreditSummary?,
    candidates: List<Account>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val existingTerms = editing?.terms

    var selectedAccountId by remember { mutableStateOf<String?>(editing?.account?.id) }
    // F25: antes el default era "elegir una cuenta existente" cuando había candidatas, y solo
    // caía en "cuenta nueva" si no había ninguna — así que quien creaba un crédito por primera
    // vez veía primero un selector de cuentas que ni sabía que existían. Ahora "cuenta nueva"
    // es SIEMPRE el default al crear; las candidatas, si existen, se ofrecen aparte y discretas.
    var newAccountMode by remember { mutableStateOf(editing == null) }
    var newAccountName by remember { mutableStateOf("") }
    // El nombre en modo EDICIÓN. No existía: era el único dato de un crédito que no se podía
    // corregir, y el dueño cargó «Libranza 4817» donde iba «4818» —un dígito, pero es el que
    // identifica la obligación contra el extracto— y tuvo que pedir que se arreglara por base de
    // datos. Un dato que solo un desarrollador puede corregir no es un dato del usuario.
    var nombreEditado by remember(editing) { mutableStateOf(editing?.account?.name ?: "") }
    var newAccountDebt by remember { mutableStateOf<Long?>(null) }

    // ── Ola 16 — la pregunta que reemplaza al «déjala en blanco» ──────────────────────────
    //
    // La ola 14 dejó las dos formas de dar de alta un crédito, pero para la del crédito recién
    // desembolsado le pedía al dueño entender el mecanismo: «deja la Deuda actual vacía y después
    // anota el traspaso». Él lo dijo mejor que nosotros: *«en vez de deuda actual vacío me
    // gustaría que me preguntes si es un desembolso o no y con eso vos aplicar la lógica»*.
    //
    // `null` = **todavía no contestó**, y el botón no se prende hasta que conteste. No hay valor
    // por defecto a propósito: cualquiera de los dos que eligiéramos por él sería la respuesta que
    // el dedo rápido nunca corrige, y las dos llevan a cifras distintas de deuda y de efectivo.
    // Una pregunta que se puede saltear no es una pregunta.
    var recienRecibido by remember { mutableStateOf<Boolean?>(null) }
    var cuentaDelDesembolso by remember { mutableStateOf<String?>(null) }
    // `null` = «usa el capital». El monto del desembolso no es siempre el capital —muchos créditos
    // desembolsan neto de costos— pero casi siempre lo es, así que el caso común es no tocar nada.
    // Ver [montoDelDesembolso].
    var montoDesembolsoEditado by remember { mutableStateOf<Long?>(null) }
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    var cuentasCargadas by remember { mutableStateOf(false) }
    // **«No pudimos cargar» y «no tienes ninguna» no son lo mismo, y confundirlos es cruel.**
    // La primera versión hacía `runCatching { … }.onSuccess { … }` sin rama de falla: un corte de
    // red, un 401 o un timeout dejaban la lista vacía y la hoja le decía a alguien que SÍ tiene su
    // cuenta de Bancolombia que se fuera a crearla. Con este estado aparte, el caso de red dice lo
    // que pasó y ofrece reintentar; el de «no tienes ninguna» sigue mandando a Cuentas.
    var falloCargarCuentas by remember { mutableStateOf(false) }
    var intentoDeCarga by remember { mutableStateOf(0) }

    // Las cuentas solo hacen falta en el alta: al editar términos no hay desembolso que registrar.
    LaunchedEffect(editing, intentoDeCarga) {
        if (editing != null) { cuentasCargadas = true; return@LaunchedEffect }
        cuentasCargadas = false
        falloCargarCuentas = false
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { cuentas = it }
            .onFailure { falloCargarCuentas = true }
        cuentasCargadas = true
    }

    var bank by remember { mutableStateOf(existingTerms?.bank ?: "") }
    var principal by remember { mutableStateOf(existingTerms?.principal) }
    // F23: la tasa aceptaba "12%" y no se leía como número — el filtro de abajo (solo dígitos y
    // un único punto) hace que el "%" nunca llegue a este estado; el campo lo pinta aparte.
    var rateEa by remember { mutableStateOf(existingTerms?.rateEa?.toString() ?: "") }
    var termMonths by remember { mutableStateOf(existingTerms?.termMonths?.toString() ?: "") }
    var installment by remember { mutableStateOf(existingTerms?.installment) }
    var dayOfMonth by remember { mutableStateOf(existingTerms?.dayOfMonth?.toString() ?: "") }
    // F23: aceptaba cualquier cosa como fecha — el filtro de abajo (solo dígitos y guiones) más
    // isValidCreditDate son la validación real hasta que exista un selector de calendario
    // (pendiente, anotado en el KDoc de más abajo).
    var startDate by remember { mutableStateOf(existingTerms?.startDate ?: "") }
    var notes by remember { mutableStateOf(existingTerms?.notes ?: "") }
    // Marcada por defecto al crear; al editar refleja lo que está guardado.
    var remindMe by remember { mutableStateOf(existingTerms?.remindMe ?: true) }
    // Libranza: la cuota la retiene el empleador del sueldo antes de depositarlo. Ver
    // PAYROLL_DEDUCTION_CATEGORY en :core para por qué cambia el modelo entero.
    var esLibranza by remember { mutableStateOf(existingTerms?.payrollDeduction ?: false) }
    // La otra forma de «esta cuota no sale de mi cuenta»: la paga otro. Ver
    // THIRD_PARTY_PAYMENT_CATEGORY en :core — de los nueve créditos del dueño, TRES son así.
    var quienPaga by remember { mutableStateOf(existingTerms?.paidBy ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val termsValid = bank.isNotBlank() &&
        (principal ?: 0L) > 0L &&
        (rateEa.toDoubleOrNull() != null) &&
        (termMonths.toIntOrNull() ?: 0) > 0 &&
        (installment ?: 0L) > 0L &&
        (dayOfMonth.toIntOrNull() in 1..31) &&
        isValidCreditDate(startDate)
    // Las cuentas a las que puede entrar la plata de un desembolso. Se filtran del selector y no
    // solo de la validación, mismo criterio que [transferableAccounts]: ofrecer una cuenta para
    // después decir que no se puede es peor que no ofrecerla.
    val cuentasDestino = remember(cuentas) { cuentasParaDesembolso(cuentas) }
    val destinoDelDesembolso = cuentasDestino.firstOrNull { it.id == cuentaDelDesembolso }
    val montoDesembolso = montoDelDesembolso(montoDesembolsoEditado, principal)
    // Solo se pregunta al crear una cuenta nueva: adjuntar términos a una cuenta LOAN que ya
    // existe (la rama de [candidates]) no crea plata en ningún lado, así que no hay desembolso
    // posible que registrar.
    val preguntaVisible = editing == null && newAccountMode
    val sinCuentasDestino = cuentasCargadas && !falloCargarCuentas && cuentasDestino.isEmpty()

    // **El callejón sin salida se dice apenas se sabe, no al final.**
    //
    // Esto se parte en dos a propósito. Que el dueño no tenga NINGUNA cuenta en pesos donde
    // pudiera haber entrado la plata —o que no hayamos podido cargar sus cuentas— se sabe en el
    // instante en que contesta «Sí»: no depende de un solo campo del formulario. Antes caía junto
    // con el resto al final, y la hoja paseaba a alguien con cuentas solo en dólares por los ocho
    // campos diciéndole «Falta el nombre de la cuenta» para recién entonces avisarle que no podía
    // seguir. Ahora sale primero, al lado de la pregunta que lo provocó.
    //
    // Lo que SÍ se queda al final es [motivoDelDesembolso]: la cuenta elegida y el monto viven en
    // el bloque de abajo, y el monto necesita el capital escrito para tener su valor por defecto.
    val bloqueoDeCuentas: String? = when {
        !preguntaVisible || recienRecibido != true -> null
        !cuentasCargadas -> "Cargando tus cuentas…"
        falloCargarCuentas -> NO_PUDIMOS_CARGAR_TUS_CUENTAS
        sinCuentasDestino -> SIN_CUENTA_PARA_EL_DESEMBOLSO
        else -> null
    }
    val motivoDelDesembolso: String? =
        if (!preguntaVisible || recienRecibido != true || bloqueoDeCuentas != null) null
        else validateCreditDisbursement(principal ?: 0L, destinoDelDesembolso, montoDesembolso ?: 0L)

    // Ola 14 — la deuda actual dejó de ser obligatoria, y sigue sin serlo: en la rama «ya lo venía
    // pagando» se puede dar de alta un crédito sin saber todavía cuánto se debe hoy.
    //
    // Ola 16 — lo que sí es obligatorio ahora es **contestar la pregunta** ([recienRecibido]), y
    // si la respuesta es «acabo de recibirla», que el desembolso esté completo y sea coherente
    // ([motivoDelDesembolso]). Sin esas dos cosas el crédito quedaría a medias en una dirección u
    // otra, que es exactamente lo que esta hoja existe para impedir.
    //
    // Del lado de la deuda actual NO hay una guarda de «no puede ser negativa»: `MoneyField` no
    // produce negativos (el filtro solo deja pasar dígitos), así que sería una condición que
    // aparenta cubrir algo y nunca se evalúa a false. El server sí la tiene, que es donde importa
    // — ahí sí llegan cuerpos escritos a mano.
    val accountValid = if (editing != null) true
        else if (newAccountMode) newAccountName.isNotBlank() && recienRecibido != null &&
            bloqueoDeCuentas == null && motivoDelDesembolso == null
        else selectedAccountId != null
    val canSave = termsValid && accountValid && !saving

    // F24: el botón se ponía gris sin decir por qué. Debajo, la PRIMERA cosa que falta, en el
    // mismo orden en que aparecen los campos en la hoja.
    val missingFieldMessage = when {
        preguntaVisible && recienRecibido == null -> "Falta decir si acabas de recibir esta plata"
        // Arriba de todo lo demás: si no hay a dónde poner la plata, ningún campo de abajo va a
        // cambiar eso, y hacérselo descubrir al final es una tomadura de pelo.
        bloqueoDeCuentas != null -> bloqueoDeCuentas
        editing == null && newAccountMode && newAccountName.isBlank() -> "Falta el nombre de la cuenta"
        editing == null && !newAccountMode && selectedAccountId == null -> "Elige una cuenta"
        bank.isBlank() -> "Falta el banco"
        (principal ?: 0L) <= 0L -> "Falta el capital original"
        rateEa.toDoubleOrNull() == null -> "Falta la tasa"
        (termMonths.toIntOrNull() ?: 0) <= 0 -> "Falta el plazo en meses"
        (installment ?: 0L) <= 0L -> "Falta la cuota mensual"
        dayOfMonth.toIntOrNull() !in 1..31 -> "El día de pago tiene que estar entre 1 y 31"
        startDate.isBlank() -> "Falta la fecha de desembolso"
        !isValidCreditDate(startDate) -> "La fecha de desembolso tiene que ser AAAA-MM-DD"
        // Va al final porque el bloque del desembolso va al final de la hoja: el capital tiene que
        // estar escrito antes para que el monto pueda venir con él puesto por defecto.
        motivoDelDesembolso != null -> motivoDelDesembolso
        else -> null
    }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val terms = CreditTerms(
                    accountId = "",
                    bank = bank.trim(),
                    principal = principal!!,
                    rateEa = rateEa.toDouble(),
                    termMonths = termMonths.toInt(),
                    installment = installment!!,
                    dayOfMonth = dayOfMonth.toInt(),
                    startDate = startDate.trim(),
                    notes = notes.trim().ifBlank { null },
                    remindMe = remindMe,
                    payrollDeduction = esLibranza,
                    // Una libranza no puede además «pagarla otro»: son dos respuestas a la misma
                    // pregunta y la casilla de arriba esconde este campo. Se limpia al guardar
                    // para que marcar libranza sobre un crédito que decía «la paga Skandia» no
                    // deje el rótulo viejo escrito en la base.
                    paidBy = if (esLibranza) null else quienPaga.trim().takeIf { it.isNotBlank() },
                )
                if (editing == null && newAccountMode) {
                    // Alta atómica server-side: cuenta + deuda inicial + términos —**y el
                    // desembolso, si lo hay**— en una sola operación. Ola 16: eso último es el
                    // punto entero de esta rama. Con el desembolso en un segundo paso, el estado
                    // intermedio es un crédito de $257.000.000 que la app declara «100% pagado»;
                    // naciendo juntos, esa ventana no existe.
                    val desembolso = if (recienRecibido == true && destinoDelDesembolso != null && montoDesembolso != null)
                        CreditDisbursement(toAccountId = destinoDelDesembolso.id, amount = montoDesembolso)
                    else null
                    Repositories.wallets.createCredit(
                        CreateCreditRequest(
                            name = newAccountName.trim(),
                            // Con desembolso va SIEMPRE en 0 y el server deriva la apertura del
                            // capital menos lo que entró: mandar los dos números es, literalmente,
                            // cómo se cuenta la deuda dos veces (el server lo rechaza con un 400).
                            // Sin desembolso es el camino de siempre: lo que el dueño escribió, o
                            // $0 si prefirió no decirlo todavía.
                            initialDebt = if (desembolso != null) 0L else (newAccountDebt ?: 0L),
                            terms = terms,
                            disbursement = desembolso,
                        )
                    )
                } else {
                    val accountId = editing?.account?.id ?: selectedAccountId!!
                    // El nombre va primero: si falla, el `runCatching` corta antes de guardar los
                    // términos y el dueño ve el error en vez de un guardado a medias.
                    val nombreNuevo = nombreEditado.trim()
                    if (editing != null && nombreNuevo.isNotBlank() && nombreNuevo != editing.account.name) {
                        Repositories.wallets.renameAccount(accountId, nombreNuevo)
                    }
                    Repositories.wallets.putCreditTerms(terms.copy(accountId = accountId))
                }
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun deleteTerms() {
        if (editing == null || saving) return
        saving = true
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteCreditTerms(editing.account.id) }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

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

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                if (editing != null) {
                    SectionLabel("CRÉDITO")
                    Spacer(Modifier.height(8.dp))
                    // Editable, no un rótulo: ver [nombreEditado].
                    FieldBox("Nombre del crédito", nombreEditado, { nombreEditado = it })
                    Spacer(Modifier.height(16.dp))
                } else {
                    // F25: el selector "CUENTA DEL PRÉSTAMO · + Nueva cuenta de préstamo"
                    // desapareció del flujo normal — era la estructura interna (cuenta +
                    // términos) asomándose, ruido para quien solo quiere anotar un crédito
                    // nuevo. Nombre y deuda actual pasan a ser los primeros campos, sin
                    // sección aparte. Solo si existen cuentas LOAN sin términos aparece,
                    // arriba y discreta, la opción de adjuntar los términos a una de ellas.
                    if (candidates.isNotEmpty()) {
                        Text(
                            "Ya tienes una deuda cargada como cuenta, ¿es esta?",
                            fontSize = 12.sp,
                            color = MinTextMute,
                        )
                        Spacer(Modifier.height(8.dp))
                        candidates.forEach { acc ->
                            SelectRow(
                                label = acc.name,
                                selected = !newAccountMode && selectedAccountId == acc.id,
                                onClick = {
                                    if (!newAccountMode && selectedAccountId == acc.id) {
                                        // Tocar de nuevo la ya elegida vuelve a "cuenta nueva" —
                                        // sin esto no había forma de deshacer la selección.
                                        newAccountMode = true
                                        selectedAccountId = null
                                    } else {
                                        newAccountMode = false
                                        selectedAccountId = acc.id
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (newAccountMode) {
                        // ── La pregunta, y va PRIMERA ────────────────────────────────────
                        //
                        // Antes acá había un campo «Deuda actual (COP, opcional)» con una ayuda
                        // debajo que decía «si te acaban de desembolsar este crédito, déjala en
                        // blanco». Esa frase le pedía al dueño entender el mecanismo interno
                        // (qué evento crea la deuda) para contestar bien. Esta pregunta le pide
                        // un hecho que él sabe sin pensarlo: ¿esta plata te acaba de llegar?
                        //
                        // Redacción: «recibir la plata», no «desembolso» ni «traspaso». El
                        // dueño usa la palabra desembolso, pero la hoja no puede depender de
                        // que la use. Las dos opciones son afirmaciones completas y no un
                        // «sí/no» suelto: cada una se entiende sola, leída sin la pregunta.
                        //
                        // **Ninguna de las dos afirma un hecho que no sabemos.** La primera
                        // versión de la segunda opción decía «No, ya lo venía pagando · Viene de
                        // antes y ya le has pagado cuotas», y eso es falso para un caso real: un
                        // crédito desembolsado hace tres meses al que todavía no se le pagó
                        // ninguna cuota entra igual por acá, y las dos líneas le decían que ya
                        // había pagado. Lo único que esta pregunta necesita separar es si la
                        // plata acaba de entrar a una cuenta suya, así que eso es lo único que
                        // dicen las opciones.
                        Text(
                            "¿Acabas de recibir la plata de este crédito?",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinText,
                        )
                        Spacer(Modifier.height(8.dp))
                        OpcionDeAlta(
                            titulo = "Sí, me la acaban de depositar",
                            detalle = "Es un crédito nuevo y el banco ya giró la plata.",
                            selected = recienRecibido == true,
                            enabled = !saving,
                            onClick = { recienRecibido = true },
                        )
                        Spacer(Modifier.height(6.dp))
                        OpcionDeAlta(
                            titulo = "No, ya lo tenía desde antes",
                            detalle = "Viene de antes; la plata no acaba de entrar a tu cuenta.",
                            selected = recienRecibido == false,
                            enabled = !saving,
                            onClick = { recienRecibido = false },
                        )
                        Spacer(Modifier.height(12.dp))

                        FieldBox("Nombre (p.ej. Crédito Vehículo Santander)", newAccountName, { newAccountName = it })
                        // La deuda actual solo tiene sentido en el crédito que ya venía: en el
                        // recién recibido la deuda la arma el desembolso, y pedir las dos cosas
                        // es exactamente cómo se contaba dos veces. Sigue siendo opcional, igual
                        // que desde la ola 14: se puede anotar un crédito sin saber todavía el
                        // saldo exacto y cuadrarlo después con «Ajustar saldo».
                        if (recienRecibido == false) {
                            Spacer(Modifier.height(8.dp))
                            MoneyField(newAccountDebt, { newAccountDebt = it }, placeholder = "Deuda actual (COP, opcional)")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Lo que tu banco dice que debes hoy. Si no lo tienes a mano, " +
                                    "déjalo en blanco y cuádralo después con «Ajustar saldo».",
                                fontSize = 11.5.sp,
                                color = MinTextMute,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionLabel("TÉRMINOS")
                Spacer(Modifier.height(8.dp))
                FieldBox("Banco", bank, { bank = it })
                Spacer(Modifier.height(8.dp))
                MoneyField(principal, { principal = it }, placeholder = "Capital original (COP)")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        // F23/F24: solo dígitos y un único punto — el "%" lo pinta RateFieldBox,
                        // nunca lo escribe la persona.
                        RateFieldBox("Tasa % EA", rateEa, { rateEa = filterRateInput(it) })
                    }
                    Box(Modifier.weight(1f)) { FieldBox("Plazo (meses)", termMonths, { termMonths = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { MoneyField(installment, { installment = it }, placeholder = "Cuota mensual (COP)") }
                    Box(Modifier.weight(1f)) { FieldBox("Día de pago", dayOfMonth, { dayOfMonth = it.filter { ch -> ch.isDigit() } }, KeyboardType.Number) }
                }
                Spacer(Modifier.height(8.dp))
                // F23/F24: solo dígitos y guiones — sin selector de calendario todavía
                // (pendiente, ver KDoc de isValidCreditDate más abajo).
                FieldBox("Desembolso (AAAA-MM-DD)", startDate, { startDate = filterDateInput(it) })
                Spacer(Modifier.height(8.dp))
                FieldBox("Notas (opcional)", notes, { notes = it })

                // ── El desembolso ────────────────────────────────────────────────────────
                //
                // Va DESPUÉS de los términos, y no pegado a la pregunta, por una razón concreta:
                // el monto viene con el capital puesto por defecto, y un valor por defecto que
                // no se ve no es un valor por defecto. Acá el capital ya está escrito, así que
                // el campo aparece con la cifra correcta y el caso común —desembolso completo—
                // es no tocar nada. La pregunta, arriba, ya anunció que esto venía.
                if (preguntaVisible && recienRecibido == true) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("LA PLATA QUE TE ENTRÓ")
                    Spacer(Modifier.height(8.dp))
                    when {
                        !cuentasCargadas -> Text("Cargando tus cuentas…", fontSize = 12.sp, color = MinTextMute)
                        // Un fallo de red NO es «no tienes cuentas»: acá se dice lo que pasó y se
                        // ofrece volver a intentar, en vez de mandar a crear una cuenta que ya
                        // existe. Ver [falloCargarCuentas].
                        falloCargarCuentas -> {
                            Text(NO_PUDIMOS_CARGAR_TUS_CUENTAS, fontSize = 12.sp, color = MinTextMute)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Reintentar",
                                fontSize = 13.sp,
                                color = MinText,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable(enabled = !saving) { intentoDeCarga++ }
                                    .padding(vertical = 4.dp),
                            )
                        }
                        // Sin ninguna cuenta en pesos no hay dónde poner la plata, y el alta se
                        // detiene acá a propósito. La alternativa —crear el crédito igual, sin
                        // desembolso— dejaría exactamente el estado que esta rama vino a matar:
                        // un crédito en $0 que la tarjeta anuncia como «100% pagado», y encima
                        // después de que el dueño dijo que la plata sí le entró.
                        sinCuentasDestino -> Text(
                            SIN_CUENTA_PARA_EL_DESEMBOLSO,
                            fontSize = 12.sp,
                            color = MinTextMute,
                        )
                        else -> {
                            Text("¿A qué cuenta te entró?", fontSize = 12.sp, color = MinTextMute)
                            Spacer(Modifier.height(8.dp))
                            cuentasDestino.forEach { acc ->
                                SelectRow(
                                    label = acc.name,
                                    selected = cuentaDelDesembolso == acc.id,
                                    onClick = { cuentaDelDesembolso = acc.id },
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            MoneyField(
                                montoDesembolso,
                                { montoDesembolsoEditado = it },
                                placeholder = "Cuánto te entró (COP)",
                            )
                            Spacer(Modifier.height(6.dp))
                            // La aritmética, antes de guardar y con las dos cifras a la vista.
                            // Mismo recurso que `deudaDespuesDelTraspaso` en la hoja de Agregar,
                            // y por el mismo motivo: un aviso genérico se lee y se olvida; dos
                            // números se discuten solos contra lo que el dueño sabe que le
                            // entró. Acá además es lo único que explica de dónde sale la
                            // diferencia cuando el banco desembolsa neto de costos.
                            val explicacion = explicacionDelDesembolso(
                                capital = principal,
                                entro = montoDesembolso,
                                destino = destinoDelDesembolso?.name,
                            )
                            Text(
                                explicacion?.texto ?: "Por defecto es el capital del crédito. Cámbialo si " +
                                    "el banco te depositó menos porque descontó costos.",
                                fontSize = 11.5.sp,
                                // Una brecha implausible se pinta distinto: es lo único que
                                // separa a la vista «esto está bien» de «revisa lo que
                                // escribiste». Ver [ExplicacionDelDesembolso].
                                color = if (explicacion?.esAdvertencia == true) MinExpense else MinTextMute,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Libranza. Va ANTES del recordatorio porque lo desactiva: a una cuota que el
                // empleador ya descontó no tiene sentido recordarla.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !saving) { esLibranza = !esLibranza }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (esLibranza) MinPrimary else MinSurfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Ícono, no el carácter «✓».
                        //
                        // La fuente que usa Compose en el navegador no trae U+2713, así que la
                        // casilla marcada salía como un rectángulo vacío — el dueño mandó la foto.
                        // Es el mismo problema que ya está documentado en `CategoryRow` con los
                        // emojis del catálogo («en la web sale como ▯»), y la misma solución: un
                        // ícono de Material, que viaja en el binario y se ve igual en las tres
                        // plataformas.
                        if (esLibranza) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MinBg,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Se descuenta de mi nómina", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText)
                        Text(
                            text = "La cuota se retiene del sueldo antes de que la plata llegue a tu " +
                                "cuenta. Movi deja de pedirte que la registres como gasto —tu sueldo ya " +
                                "viene neto— y en su lugar te ofrece bajar la deuda con un toque.",
                            fontSize = 11.5.sp,
                            color = MinTextMute,
                            lineHeight = 16.sp,
                        )
                    }
                }

                // ¿La paga otro? Va justo debajo de la libranza porque contesta la MISMA pregunta
                // —«¿esta cuota sale de tu cuenta?»— con la otra respuesta posible, y se esconde
                // cuando la libranza ya la contestó: ofrecer las dos a la vez invitaría a marcar
                // ambas y a tener que decidir después cuál gana.
                //
                // Texto libre y no un menú: quién paga la cuota de alguien es una lista que nadie
                // puede enumerar de antemano. El dueño tiene tres casos y ninguno se parece —una
                // pensión voluntaria, su esposa, su papá.
                if (!esLibranza) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "¿LA PAGA ALGUIEN MÁS?",
                        fontSize = 11.sp,
                        color = MinTextMute,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    // El tope es el de la columna (`varchar(60)`). Sin él, un nombre más largo
                    // hacía fallar el INSERT en Postgres y se caía el guardado ENTERO del
                    // crédito con un 500 sin mensaje —no hay StatusPages—: el dueño perdía la
                    // edición completa por haber escrito de más en un rótulo. El server recorta
                    // igual, por si llega de otro cliente; acá se corta antes para que ni
                    // siquiera se pueda escribir de más.
                    // El texto de ayuda NO puede empezar con «Yo».
                    //
                    // Decía «Yo — o escribe quién: Skandia, Caro…», y el dueño escribió
                    // literalmente «Yo» en el campo de su crédito del carro. Es la respuesta
                    // correcta a la pregunta y la incorrecta para este campo: un nombre acá
                    // significa que la cuota la paga UN TERCERO, así que Movi dejó de contar sus
                    // $4.215.223 mensuales como gasto suyo y de recordarle el pago.
                    //
                    // El campo tiene dos estados —vacío o un nombre— y el vacío es el normal. Un
                    // marcador que empieza nombrando el caso normal invita a escribirlo.
                    FieldBox(
                        "Skandia, Caro, mi papá…",
                        quienPaga,
                        { quienPaga = it.take(60) },
                    )
                    // El texto de abajo cambia según el estado, en vez de aparecer solo cuando ya
                    // se escribió algo: la duda («¿esto qué hace?») llega ANTES de escribir.
                    Spacer(Modifier.height(6.dp))
                    if (quienPaga.isBlank()) {
                        Text(
                            text = "Déjalo vacío si la pagas tú. Escribe un nombre solo si la cuota la " +
                                "paga otra persona o sale de otra bolsa — una pensión voluntaria, tu " +
                                "pareja, un familiar.",
                            fontSize = 11.5.sp,
                            color = MinTextMute,
                            lineHeight = 16.sp,
                        )
                    } else {
                        Text(
                            text = "La deuda sigue siendo tuya y suma entera en tu deuda total. Lo que " +
                                "cambia es que la cuota deja de contar como gasto tuyo del mes y Movi " +
                                "deja de recordártela — en su lugar te ofrece bajar la deuda con un toque.",
                            fontSize = 11.5.sp,
                            color = MinTextMute,
                            lineHeight = 16.sp,
                        )
                    }
                }

                // El recordatorio no aplica a una libranza ni a una cuota que paga otro: en los
                // dos casos ya se pagó sola.
                if (!esLibranza && quienPaga.isBlank()) {
                    Spacer(Modifier.height(16.dp))
                    // La cuota de este crédito entra al barrido de recordatorios salvo que el dueño
                    // diga que no. Casilla, no diálogo: no interrumpe el alta.
                    ReminderOptInField(
                        checked = remindMe,
                        onCheckedChange = { remindMe = it },
                        enabled = !saving,
                    )
                }

            }

            // **El error del guardado vive junto al botón, no adentro del scroll.**
            //
            // El dueño: «le doy guardar y no pasa nada». Pasaba: el guardado fallaba y el mensaje
            // se pintaba al final del bloque que se DESPLAZA, o sea debajo de lo que se ve. El
            // botón —que está afuera del scroll, fijo abajo— no cambiaba, así que desde donde él
            // miraba el toque no había hecho nada.
            //
            // El aviso de «falta tal campo» de acá abajo ya vivía afuera y por eso sí se veía.
            // Que el error del server, que es MÁS importante, estuviera adentro era la asimetría.
            // Mismo defecto que la hoja de editar documentos, y la misma cura.
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MinExpense,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) MinText else MinTextFaint)
                    .clickable(enabled = canSave) { save() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (saving) "Guardando…" else "Guardar crédito", color = MinBg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            // F24: antes el botón se apagaba en silencio. Ahora dice la primera cosa que falta.
            if (!canSave && !saving && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (editing?.terms != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Eliminar términos",
                    fontSize = 13.sp,
                    color = MinExpense,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { deleteTerms() }.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun FieldBox(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        // Una sola línea SIEMPRE, igual que en [MoneyField] y por lo mismo: estos campos se usan
        // a media fila (los dos días de la tarjeta) y un placeholder que se parte en dos renglones
        // infla la caja y desalinea al campo de al lado. Visto a ojo a 360dp.
        if (value.isEmpty()) {
            Text(
                placeholder,
                fontSize = 14.sp,
                color = MinTextFaint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 14.sp, color = MinText),
            cursorBrush = SolidColor(MinText),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Una de las dos respuestas a «¿acabas de recibir la plata de este crédito?».
 *
 * Es [SelectRow] con una segunda línea, y esa línea no es decorativa: el título dice qué eligió
 * y el detalle dice **de qué situación está hablando**, que es lo que hace que la pregunta se
 * conteste sin ayuda. Ninguna de las dos filas nace marcada — ver [CreditTermsSheet].
 */
@Composable
private fun OpcionDeAlta(
    titulo: String,
    detalle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinSurfaceContainerLow else Color.Transparent)
            .border(1.dp, if (selected) MinText else MinBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            titulo,
            fontSize = 13.5.sp,
            color = MinText,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Text(detalle, fontSize = 11.5.sp, color = MinTextMute)
    }
}

/**
 * Lo que se le dice a quien contesta «me la acaban de depositar» sin tener todavía una sola
 * cuenta en pesos donde pudiera haber entrado.
 *
 * Vive como constante porque hacen falta las mismas palabras en dos lugares de la misma hoja: el
 * hueco donde iría el selector de cuentas, y el renglón de «lo que falta» debajo del botón.
 */
const val SIN_CUENTA_PARA_EL_DESEMBOLSO =
    "Todavía no tienes ninguna cuenta en pesos donde pueda haber entrado esta plata. Créala " +
        "primero en Cuentas y vuelve a este crédito."

/**
 * Lo que se le dice cuando **no se pudieron leer** sus cuentas.
 *
 * Es un mensaje aparte de [SIN_CUENTA_PARA_EL_DESEMBOLSO] y no un detalle: decirle «créala primero
 * en Cuentas» a alguien cuya cuenta de Bancolombia existe y solo se cayó la red es mandarlo a
 * duplicar una cuenta que ya tiene. Los dos casos dejan la lista vacía; solo el estado los separa.
 */
const val NO_PUDIMOS_CARGAR_TUS_CUENTAS =
    "No pudimos cargar tus cuentas. Revisa tu conexión e inténtalo de nuevo."

/**
 * A qué cuentas puede entrar la plata de un desembolso: **de dinero o inversión, y en pesos**.
 *
 * Fuera quedan las cuentas de deuda (otro crédito, una tarjeta) porque un desembolso a otra deuda
 * no es un desembolso —[validateTransfer] lo rechaza igual del otro lado— y las cuentas en otra
 * moneda porque la cuenta del crédito nace siempre en COP y todavía no existe el traspaso entre
 * monedas. Se filtran del selector y no solo de la validación, mismo criterio que
 * `transferableAccounts`: ofrecer una cuenta para después decir que no se puede es peor que no
 * ofrecerla.
 */
fun cuentasParaDesembolso(accounts: List<Account>): List<Account> =
    accounts.filter { it.type.group != AccountGroup.DEUDA && it.currency == "COP" }

/**
 * Cuánto entró a la cuenta: lo que el dueño escribió, o el capital del crédito si no escribió
 * nada.
 *
 * El default es el capital porque el caso común es el desembolso completo, y así ese caso es cero
 * toques. Pero **es un default y no una regla**: muchos créditos desembolsan neto de costos, y ahí
 * las dos cifras son distintas y las dos son ciertas. Borrar el campo vuelve al capital en vez de
 * dejarlo vacío — el efectivo es siempre lo que se está viendo en el campo, que es lo que hace
 * que la línea de aritmética de abajo nunca hable de un número que no está a la vista.
 */
fun montoDelDesembolso(editado: Long?, capital: Long?): Long? = editado ?: capital

/**
 * La línea que dice, con las dos cifras, en qué queda el crédito si se guarda así.
 *
 * @property texto lo que se lee debajo del monto.
 * @property esAdvertencia si la brecha entre el capital y lo que entró es demasiado grande para
 *   ser costos financiados. Lo decide esta función y no la pantalla, por el mismo motivo que
 *   `ProgresoDeCredito.esAviso`: la pantalla solo elige el color, el juicio vive donde están las
 *   cifras y se puede probar.
 */
data class ExplicacionDelDesembolso(val texto: String, val esAdvertencia: Boolean)

/**
 * **Debajo de qué fracción del capital una brecha deja de ser «costos financiados».**
 *
 * Los costos que un banco descuenta del desembolso —estudio, seguros, papeleo— son porcentajes de
 * un dígito: 70% es holgadísimo para el caso que la frase tranquilizadora describe. Por debajo de
 * eso, la explicación más probable ya no es «el banco descontó costos» sino que uno de los dos
 * números está mal tecleado.
 */
private const val FRACCION_PLAUSIBLE_DEL_DESEMBOLSO = 0.70

/**
 * La línea que dice, con las dos cifras, **en qué queda el crédito si se guarda así**. `null` si
 * todavía no hay con qué armarla.
 *
 * Mismo recurso que `deudaDespuesDelTraspaso` en la hoja de Agregar, y por el mismo motivo escrito
 * allá: un aviso genérico se lee y se olvida; dos números se discuten solos contra lo que el dueño
 * sabe que le entró.
 *
 * Cuando lo que entró es MENOS que el capital, esta línea es además lo único que explica de dónde
 * sale la diferencia. Y explicarlo importa: la alternativa a cargarla como deuda sería que el
 * crédito naciera debiendo solo lo que entró, y entonces la tarjeta diría «2% pagado» sobre un
 * crédito que nadie pagó todavía. Ver `CreateCreditRequest.disbursement`.
 *
 * ## La guarda que faltaba: el dedo que se come dígitos
 *
 * [validateCreditDisbursement] rechaza que entre MÁS plata que el capital porque «uno de los dos
 * números está mal tecleado». **El mismo error en la otra dirección —comerse dígitos, que es más
 * común que agregarlos— no lo atrapa nadie**, y esta línea llegaba a decir, sobre un desembolso de
 * $2 en un crédito de $257.000.000: *«los $256.999.998 de diferencia también los debes — es lo que
 * pasa cuando el banco descuenta costos del desembolso»*. La aritmética seguía coherente (la deuda
 * vale el capital), pero el efectivo quedaba mal **y encima con una justificación que le decía al
 * dueño que era normal.**
 *
 * **Avisa, no bloquea**, y eso es a propósito: una brecha enorme puede ser real. En una compra de
 * cartera el banco gira la mayor parte directo al otro acreedor y a la cuenta del dueño le entra
 * solo el resto — capital $257.000.000, a la cuenta $57.000.000, y las dos cifras son ciertas.
 * Bloquearlo le impediría registrar un crédito que sí existe. Así que por debajo de
 * [FRACCION_PLAUSIBLE_DEL_DESEMBOLSO] se cambia el texto: se le quita la atribución a los costos
 * (que es la parte que tranquiliza), se nombra la consecuencia sobre la plata, y se le pide que
 * revise. El color lo pone la hoja a partir de [ExplicacionDelDesembolso.esAdvertencia].
 *
 * No dice nada cuando lo que entró es MAYOR que el capital: ese caso no es una explicación sino un
 * rechazo, y lo cubre [validateCreditDisbursement] con su propio mensaje debajo del botón. Dos
 * textos hablando del mismo error, uno diciendo que está bien, sería peor que uno solo.
 *
 * Tampoco dice nada **mientras no haya cuenta elegida**: decir «Entran $257.000.000 a tu cuenta»
 * se lee como una frase cerrada, y quedaba debajo de un botón apagado justamente porque falta
 * elegir la cuenta. Ahí se muestra en su lugar la ayuda del valor por defecto, que es lo que toca
 * hacer en ese momento.
 */
fun explicacionDelDesembolso(capital: Long?, entro: Long?, destino: String?): ExplicacionDelDesembolso? {
    if (capital == null || capital <= 0L || entro == null || entro <= 0L || entro > capital) return null
    if (destino == null) return null
    val diferencia = capital - entro
    val base = "Entran ${formatCOP(entro)} a $destino y el crédito arranca debiendo ${formatCOP(capital)}"
    return when {
        diferencia == 0L -> ExplicacionDelDesembolso("$base.", esAdvertencia = false)
        entro >= capital * FRACCION_PLAUSIBLE_DEL_DESEMBOLSO -> ExplicacionDelDesembolso(
            "$base: los ${formatCOP(diferencia)} de diferencia también los debes — es lo que pasa " +
                "cuando el banco descuenta costos del desembolso.",
            esAdvertencia = false,
        )
        else -> ExplicacionDelDesembolso(
            "$base. Revisa el monto: quedarían ${formatCOP(diferencia)} de deuda que nunca te " +
                "entraron a la cuenta.",
            esAdvertencia = true,
        )
    }
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinSurfaceContainerLow else Color.Transparent)
            .border(1.dp, if (selected) MinText else MinBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.5.sp, color = MinText, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/**
 * Como [FieldBox], pero para la tasa: pinta un "%" fijo después del número — la persona nunca
 * lo escribe, así que nunca puede terminar en el estado ("12%") que rompía el parseo (F23/F24).
 */
@Composable
private fun RateFieldBox(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(fontSize = 14.sp, color = MinText),
                    cursorBrush = SolidColor(MinText),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) Text("%", fontSize = 14.sp, color = MinTextMute)
        }
    }
}

/**
 * F23: la tasa aceptaba "12%" y no se leía como número. Filtra todo lo que no sea dígito o un
 * único punto decimal — el "%" queda a cargo de [RateFieldBox], nunca del texto escrito.
 */
fun filterRateInput(input: String): String {
    var sawDot = false
    // La coma cuenta como punto decimal: en Colombia se escribe «12,5», y el teclado decimal de
    // Android en español muestra «,». Descartarla en silencio convertía «12,5» en «125» — una
    // tasa diez veces mayor, guardada sin aviso. Justo el número que miente que esta ola vino a matar.
    return input.replace(',', '.').filter { ch ->
        when {
            ch.isDigit() -> true
            ch == '.' && !sawDot -> { sawDot = true; true }
            else -> false
        }
    }
}

/** F23: la fecha de desembolso aceptaba cualquier texto. Deja pasar solo dígitos y guiones. */
// La barra cuenta como guion: «2026/06/17» era el caso exacto que dejaba el botón en gris (F24).
fun filterDateInput(input: String): String = input.replace('/', '-').filter { it.isDigit() || it == '-' }

/**
 * F23/F24: AAAA-MM-DD con año/mes/día en rango razonable — la validación real hasta que exista
 * un selector de calendario (pendiente; anotado en el ítem F23 del plan, no se agregó acá).
 * No valida días por mes (el 31 de febrero pasa) a propósito: es un chequeo de forma para que
 * el botón no se quede en gris sin explicar por qué, no una validación de calendario completa.
 */
fun isValidCreditDate(input: String): Boolean {
    val parts = input.split("-")
    if (parts.size != 3) return false
    val (y, m, d) = parts
    // Mes y día de dos dígitos exactos: el server guarda el texto tal cual (varchar), así que
    // «2026-6-7» quedaría almacenado en un formato que después nadie parsea igual.
    if (y.length != 4 || m.length != 2 || d.length != 2) return false
    val year = y.toIntOrNull() ?: return false
    val month = m.toIntOrNull() ?: return false
    val day = d.toIntOrNull() ?: return false
    return year in 1900..2100 && month in 1..12 && day in 1..31
}
