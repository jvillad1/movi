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

    // Las cuentas solo hacen falta en el alta (al editar términos no hay desembolso que registrar),
    // y se piden una vez al abrir la hoja: si fallan, la lista queda vacía y la hoja lo dice en vez
    // de ofrecer un selector con nada adentro.
    LaunchedEffect(editing) {
        if (editing != null) { cuentasCargadas = true; return@LaunchedEffect }
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
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
    val sinCuentasDestino = cuentasCargadas && cuentasDestino.isEmpty()
    val motivoDelDesembolso: String? = when {
        !preguntaVisible || recienRecibido != true -> null
        !cuentasCargadas -> "Cargando tus cuentas…"
        sinCuentasDestino -> SIN_CUENTA_PARA_EL_DESEMBOLSO
        else -> validateCreditDisbursement(principal ?: 0L, destinoDelDesembolso, montoDesembolso ?: 0L)
    }

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
        else if (newAccountMode) newAccountName.isNotBlank() && recienRecibido != null && motivoDelDesembolso == null
        else selectedAccountId != null
    val canSave = termsValid && accountValid && !saving

    // F24: el botón se ponía gris sin decir por qué. Debajo, la PRIMERA cosa que falta, en el
    // mismo orden en que aparecen los campos en la hoja.
    val missingFieldMessage = when {
        preguntaVisible && recienRecibido == null -> "Falta decir si acabas de recibir esta plata"
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
                    Text(editing.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
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
                            titulo = "No, ya lo venía pagando",
                            detalle = "Viene de antes y ya le has pagado cuotas.",
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
                                explicacion ?: "Por defecto es el capital del crédito. Cámbialo si " +
                                    "el banco te depositó menos porque descontó costos.",
                                fontSize = 11.5.sp,
                                color = MinTextMute,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // La cuota de este crédito entra al barrido de recordatorios salvo que el dueño
                // diga que no. Casilla, no diálogo: no interrumpe el alta.
                ReminderOptInField(
                    checked = remindMe,
                    onCheckedChange = { remindMe = it },
                    enabled = !saving,
                )

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = MinExpense)
                }
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
 * No dice nada cuando lo que entró es MAYOR que el capital: ese caso no es una explicación sino un
 * rechazo, y lo cubre [validateCreditDisbursement] con su propio mensaje debajo del botón. Dos
 * textos hablando del mismo error, uno diciendo que está bien, sería peor que uno solo.
 */
fun explicacionDelDesembolso(capital: Long?, entro: Long?, destino: String?): String? {
    if (capital == null || capital <= 0L || entro == null || entro <= 0L || entro > capital) return null
    val cuenta = destino ?: "tu cuenta"
    val diferencia = capital - entro
    val base = "Entran ${formatCOP(entro)} a $cuenta y el crédito arranca debiendo ${formatCOP(capital)}"
    return if (diferencia == 0L) "$base."
    else "$base: los ${formatCOP(diferencia)} de diferencia también los debes — es lo que pasa " +
        "cuando el banco descuenta costos del desembolso."
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
