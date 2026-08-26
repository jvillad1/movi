package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*

// Amber warning color reused from the existing MinWarn palette entry.
private val MinAmber = MinWarn

/**
 * Todo lo que se repite mes a mes, en una sola pantalla (Ola 8).
 *
 * Suscripciones dejó de ser una pantalla hermana: una suscripción ES un recurrente —el mismo
 * cobro, el mismo día del mes— y tenerlas separadas obligaba al dueño a saber en cuál de las
 * dos anotar Netflix. Ahora hay una sola lista, con dos grupos que sí significan algo para él:
 *
 * 1. **Por día del mes** — lo confirmado: sus reglas recurrentes MÁS las suscripciones activas.
 *    Las que salieron del detector llevan la marca «la encontró Movi».
 * 2. **Detectadas · por confirmar** — lo que el detector propuso y nadie aceptó todavía, aparte
 *    y con Confirmar / No es. Nunca mezclado con lo confirmado.
 *
 * Es el mismo movimiento que la Ola 7 hizo con Inversiones dentro de Cuentas (F61).
 */
@Composable
fun RecurrentesScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<RecurringRule>>(emptyList()) }
    var upcoming by remember { mutableStateOf<List<UpcomingPayment>>(emptyList()) }
    var subs by remember { mutableStateOf(SubscriptionsResult(emptyList(), 0)) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // loadKey increments after every create/update/delete to trigger a reload.
    var loadKey by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    // Qué fuentes llegaron alguna vez. Una cifra solo se pinta cuando llegó TODO lo que la
    // compone; con datos a medias sale plausible y equivocada, que es peor que no salir.
    //
    // Son banderas por fuente y no una sola «datosCompletos» porque las cifras no dependen de
    // las mismas cosas: el flujo libre sale de reglas + cobros, y `/api/payments/upcoming` no
    // entra ahí — exigirlo escondía un total que se podía calcular perfectamente.
    //
    // Solo van de false a true: una recarga posterior NO las vuelve a bajar. Bajarlas hacía
    // parpadear el número a «—» y desaparecer los conteos después de cada Confirmar, Quitar o
    // Guardar; mientras se refresca, lo último bueno que se supo sigue siendo lo más cierto que
    // hay para mostrar.
    var reglasOk by remember { mutableStateOf(false) }
    var cobrosOk by remember { mutableStateOf(false) }
    var vencimientosOk by remember { mutableStateOf(false) }

    // Hoja de crear/editar. Guarda el ID y NO la fila: el objeto de una fila puede ser de un
    // snapshot viejo, y prellenar el formulario con eso hace que «Guardar cambios» reescriba
    // datos que el dueño ya había corregido. El prellenado se resuelve contra `rules` al
    // renderizar, y `editar()` se asegura de que `rules` esté al día antes de abrir.
    var sheetRuleId by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    // Ola 9 · D: id → nombre de cuenta, para poder decir de dónde sale cada pago. Vacío mientras
    // ninguna regla tenga cuenta: la fila simplemente no dice nada, que es lo correcto.
    var accountNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Estado del opt-in de push, para el aviso de "tus recordatorios no te van a llegar".
    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
    var pushRefreshTick by remember { mutableStateOf(0) }

    // Ola 9 · B: además de su propio `loadKey`, la señal de que se guardó algo desde una hoja
    // que vive por encima de esta pantalla. Ahora se puede crear un recurrente sin salir de acá
    // —anotar un movimiento y aceptar el ofrecimiento— y sin esto la lista seguiría mostrando lo
    // de antes, o sea diciéndole al dueño que no se guardó nada. Mismo patrón que Presupuestos
    // y Movimientos (ver [LocalRefreshTick]).
    val refreshTick = LocalRefreshTick.current

    LaunchedEffect(loadKey, refreshTick) {
        loading = true
        // En PARALELO, como ya las hace el Inicio. En serie eran tres viajes encadenados contra
        // el server —y el de suscripciones trae la TRM adentro (`FxRateService`), así que puede
        // incluir una llamada externa—: la ventana de «pantalla a medias» duraba más de un
        // segundo contra Postgres local, y contra Railway más.
        //
        // Cada una publica su resultado apenas vuelve. Retenerlas para asignarlas juntas parecía
        // más prolijo, pero alargaba la ventana en la que las FILAS visibles eran de un snapshot
        // viejo — y una fila no es solo texto: es el botón de editar (ver `editar`).
        coroutineScope {
            val porReglas = async { runCatching { Repositories.wallets.getRecurringRules() } }
            val porVencer = async { runCatching { Repositories.wallets.getUpcomingPayments() } }
            val porCobros = async { runCatching { Repositories.wallets.getSubscriptions() } }

            var fallo: String? = null
            // F35: de paso, alimenta el caché de "categorías ya usadas" que lee CategoryField —
            // esta pantalla ya carga las reglas, no hace falta un fetch nuevo.
            porReglas.await()
                .onSuccess {
                    rules = it
                    // Ola 9 · A3: una regla recurrente sabe si es gasto o ingreso.
                    UsedCategoriesCache.recordAll(it.map { r -> r.category to r.type })
                    reglasOk = true
                    // Ola 9 · D: los nombres de las cuentas, y SOLO si alguna regla tiene
                    // cuenta. Una cuarta llamada fija en esta pantalla sería un viaje por
                    // gusto para quien todavía no usa el campo (o sea, todo el mundo hasta
                    // hoy); así la paga únicamente quien la va a ver.
                    if (it.any { r -> r.accountId != null }) {
                        runCatching { Repositories.wallets.getAccounts() }
                            .onSuccess { list -> accountNames = list.associate { a -> a.id to a.name } }
                    }
                }
                .onFailure { fallo = it.toUserMessage() }
            porVencer.await()
                .onSuccess { upcoming = it; vencimientosOk = true }
                .onFailure { if (fallo == null) fallo = it.toUserMessage() }
            porCobros.await()
                .onSuccess { subs = it; cobrosOk = true }
                .onFailure { if (fallo == null) fallo = it.toUserMessage() }
            error = fallo
            // Ola 9 · B: el ofrecimiento «¿esto se repite?» necesita saber qué recurrentes ya
            // existen. Esta pantalla acaba de cargarlos, así que se los deja al gate: le ahorra
            // sus llamadas y —lo que de verdad importa— lo mantiene al día. Sin esto, crear
            // «Gimnasio» acá y anotar el pago después terminaba ofreciendo crear el recurrente
            // que el dueño acababa de crear.
            RecurringOfferGate.recordarLoQueYaHay(
                reglas = if (reglasOk) rules else null,
                suscripciones = if (cobrosOk) subs.subscriptions else null,
            )
        }
        loading = false
    }

    // Volver a barrer los movimientos buscando cobros que se repiten. El detector deja fuera la
    // categoría «Traspaso» (SubscriptionDetector.kt) y respeta lo ya descartado: lo que el dueño
    // dijo que no era, no vuelve a proponerse.
    fun rescan() {
        if (scanning) return
        scanning = true
        error = null
        coroutine.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                // `cobrosOk` también se marca acá: un re-escaneo devuelve la lista completa de
                // cobros, así que sirve igual que la carga normal. Sin esto, quien entraba con
                // `/api/subscriptions` caído y tocaba «Buscar cobros» veía aparecer los cobros
                // pero se quedaba con «Flujo libre —» y sin conteos hasta salir y volver.
                .onSuccess { subs = it; cobrosOk = true; error = null }
                .onFailure { error = it.toUserMessage() }
            scanning = false
        }
    }

    /**
     * Abrir la hoja para editar una fila.
     *
     * Una fila de la lista NO es solo una descripción: es el botón de editar, y su contenido
     * prellena el formulario. Si la lista visible es de un snapshot viejo —porque hay una
     * recarga en vuelo— la hoja abre con valores viejos y «Guardar cambios» los vuelve a
     * escribir: el dueño apaga un recordatorio, toca la fila otra vez para corregir el monto, y
     * al guardar se le vuelve a encender el recordatorio sin que nada lo diga.
     *
     * Con los datos al día se abre directo (el caso normal, sin ningún viaje extra). Si hay una
     * recarga en vuelo, o las reglas nunca llegaron, se relee ANTES de prellenar: más vale un
     * instante de espera que pisar en silencio lo que el dueño acaba de corregir.
     */
    fun editar(rule: RecurringRule) {
        if (reglasOk && !loading) {
            sheetRuleId = rule.id
            sheetOpen = true
            return
        }
        coroutine.launch {
            runCatching { Repositories.wallets.getRecurringRules() }
                .onSuccess { frescas ->
                    rules = frescas
                    reglasOk = true
                    if (frescas.any { it.id == rule.id }) {
                        sheetRuleId = rule.id
                        sheetOpen = true
                    } else {
                        error = "Ese recurrente ya no existe."
                    }
                }
                .onFailure { error = it.toUserMessage() }
        }
    }

    fun setStatus(sub: Subscription, status: SubStatus) {
        coroutine.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { loadKey++ }
                .onFailure { error = it.toUserMessage() }
        }
    }

    /**
     * «Quitar» una suscripción de la lista. Qué significa quitar depende de quién la puso:
     *
     * - **La escribió el dueño** (`manual_`): se BORRA. Marcarla DISMISSED la dejaba en un
     *   limbo — invisible en la lista, imposible de recuperar desde ninguna pantalla, y
     *   todavía chocando con el alta si volvía a contratar el servicio («Ya tienes una
     *   suscripción llamada "Claude"» sobre algo que no ve). El detector nunca produce esa
     *   clave, así que no hay ningún barrido al que haga falta decirle «esta no».
     * - **La encontró el detector**: sigue siendo DISMISSED, que ahí sí significa algo — es el
     *   «no me la propongas más» que respeta SubscriptionSync en cada re-scan. Borrarla haría
     *   que el próximo barrido la volviera a proponer.
     */
    fun quitar(sub: Subscription) {
        coroutine.launch {
            val laEscribioElDueno = sub.merchantKey.startsWith(MANUAL_SUB_PREFIX)
            val resultado = if (laEscribioElDueno) {
                runCatching { Repositories.wallets.deleteSubscription(sub.id) }
            } else {
                runCatching {
                    Repositories.wallets.updateSubscription(sub.id, sub.copy(status = SubStatus.DISMISSED))
                }
            }
            resultado.onSuccess { loadKey++ }.onFailure { error = it.toUserMessage() }
        }
    }

    if (PushOptIn.supported) {
        LaunchedEffect(pushRefreshTick) {
            // El flujo de permisos del navegador es async (moviPush.js): refrescar unas
            // veces tras cada acción para que el aviso desaparezca sin necesidad de reabrir la app.
            // Solo donde el push existe: en Android/iOS status() es una constante y esto
            // sería un bucle inútil (mismo gate que usa PerfilScreen).
            repeat(20) {
                kotlinx.coroutines.delay(600)
                pushStatus = PushOptIn.status()
            }
        }
    }

    val candidatas = subs.subscriptions.filter { it.status == SubStatus.CANDIDATE }
    // Nombres que el dueño ya tiene anotados a mano — para avisar en una candidata que va a
    // duplicar algo que ya existe ANTES de que la confirme.
    val clavesDeReglas = remember(rules) { rules.map { claveDeNombre(it.name) }.toSet() }

    // Todo el cálculo (lista mezclada, ingresos, gastos sin doble conteo) vive en
    // `resumenRecurrentes`, que es puro y testeado — y es el mismo que usa el acceso
    // «Recurrentes» del Inicio, así que las dos cifras no pueden discrepar.
    val resumen = remember(rules, subs) { resumenRecurrentes(rules, subs) }
    val ordered = resumen.items
    // Las CIFRAS —el flujo libre y el conteo de la lista— solo se pintan cuando llegaron las dos
    // fuentes que las componen: un total a medias es indistinguible de un total real.
    //
    // Las FILAS sí se pintan con lo que haya, pero NO porque «cada una sea cierta por separado»:
    // una fila vieja se ve idéntica a una nueva y no hay forma de que el dueño lo note. Se
    // pintan porque una lista de hace un segundo sigue siendo lo mejor que se puede mostrar
    // mientras llega la siguiente, y porque el único uso donde esa diferencia hace daño —abrir
    // la hoja de editar y reescribir con datos viejos— está cubierto aparte, en `editar()`.
    val cifras = if (reglasOk && cobrosOk) resumen else null

    // «Próximos» muestra lo que vence PRONTO, no todas las reglas: el server manda una entrada
    // por regla, así que pintarlas todas repetía la lista entera de abajo (el mismo «Arriendo»
    // dos veces en la misma pantalla, justo debajo del número «Flujo libre» — una invitación a
    // creer que se contó dos veces). Con el filtro, esta sección vuelve a ser lo que su nombre
    // promete y solo coincide con la de abajo en lo que de verdad urge, que sí conviene ver dos
    // veces: una como alerta y otra en el inventario. El corte es el mismo del barrido de avisos
    // (`leadDays`, ver DueDates.kt): UPCOMING es «todavía falta».
    val proximos = proximosQueUrgen(upcoming)

    // El aviso ámbar habla de una promesa rota («no vamos a poder avisarte»), así que mira lo
    // que se PIDIÓ, no lo que existe: solo un GASTO con `remindMe` entra al barrido de avisos
    // (`selectDueForReminder`). Con un recurrente de ingreso —que ni siquiera ofrece la casilla—
    // el aviso salía igual, prometiendo el incumplimiento de algo que nadie pidió.
    val hayRecordatoriosPedidos = hayRecordatoriosPedidos(upcoming)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MinBg)) {
            // F60: encabezado único; con reglas ya creadas el alta pasa a botón compacto a la
            // derecha (F18), vacío va a todo el ancho debajo. Recurrentes ya es pestaña propia:
            // el leading lo decide el layout (avatar en el rail, flecha en el teléfono, donde se
            // sigue llegando por Más).
            MinScreenHeader(
                title = "Recurrentes",
                leading = leadingFor(Screen.Recurrentes, onProfile = { onNavigate(Screen.Profile) }, fallback = Screen.Mas),
                // Ola 8: el alta es UNA sola («Nuevo recurrente», ver CreateRecurringRuleSheet).
                // El re-escaneo NO comparte este espacio: dos acciones acá no dejaban ancho para
                // el título y en un teléfono angosto se leía «Re…». Vive en el encabezado de «Por
                // día del mes», que es donde aparece lo que encuentra.
                //
                // Mientras carga el botón se muestra igual: la lista todavía está vacía, y sin él
                // la pantalla quedaba más de un segundo sin ninguna salida.
                action = if (ordered.isNotEmpty() || loading) {
                    { NewItemButton(label = "Nuevo recurrente", onClick = { sheetRuleId = null; sheetOpen = true }) }
                } else null,
            )
            if (ordered.isEmpty() && !loading) {
                NewItemButton(
                    label = "Nuevo recurrente",
                    onClick = { sheetRuleId = null; sheetOpen = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // ── Flujo libre card ────────────────────────────────────────────
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Flujo libre", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = cifras?.let { formatCOP(it.flujoLibre) } ?: "—",
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinText,
                            letterSpacing = (-1.4).sp,
                            lineHeight = 36.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Ingresos recurrentes − Gastos recurrentes",
                            fontSize = 12.sp,
                            color = MinTextMute,
                        )
                        Spacer(Modifier.height(18.dp))
                        Hairline()
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ingresos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = cifras?.let { formatCOP(it.ingresos) } ?: "—",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinIncome,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gastos recurrentes", fontSize = 11.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = cifras?.let { formatCOP(it.gastos) } ?: "—",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MinText,
                                    letterSpacing = (-0.3).sp,
                                )
                            }
                        }
                        // Solo cuando de verdad hay algo en otra moneda: si todo está en pesos,
                        // la nota sobraría y ensuciaría la tarjeta.
                        // Un total al que le faltan filas se dice, no se disimula: es la
                        // diferencia entre «me queda esto libre» y «me queda esto libre, que no
                        // incluye dos cobros». Pasa si el server todavía no manda la tasa (el
                        // APK se instala a mano y el server se despliega aparte, así que el
                        // desfase existe) o si algún día entra una moneda que no sabemos pasar.
                        if (cifras != null && cifras.sinConvertir > 0) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (cifras.sinConvertir == 1) {
                                    "Este total no incluye 1 cobro en otra moneda: no pudimos " +
                                        "convertirlo a pesos."
                                } else {
                                    "Este total no incluye ${cifras.sinConvertir} cobros en otra " +
                                        "moneda: no pudimos convertirlos a pesos."
                                },
                                fontSize = 11.sp,
                                color = MinAmber,
                                lineHeight = 15.sp,
                            )
                        } else if (cifras != null && cifras.hayMonedaExtranjera) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                // No promete «la tasa del día»: FxRateService cae a la última tasa
                                // que consiguió, a USD_COP_RATE o a una constante si la TRM no
                                // responde, y el server no dice cuál de las tres usó.
                                text = "Lo que te cobran en dólares entra al total convertido a " +
                                    "pesos con la tasa de cambio más reciente que pudimos consultar.",
                                fontSize = 11.sp,
                                color = MinTextMute,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                // ── Aviso: recordatorios sin canal de entrega ───────────────────
                if (shouldShowReminderWarning(pushStatus, hayRecordatoriosPedidos)) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ReminderWarningBanner(
                                pushStatus = pushStatus,
                                onEnable = {
                                    PushOptIn.enable()
                                    pushRefreshTick++
                                },
                            )
                        }
                    }
                }

                // ── Cualquier carga que haya fallado (ver LaunchedEffect) ───────
                error?.let { msg ->
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(msg, fontSize = 12.sp, color = MinExpense, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }

                // ── Detectadas · por confirmar ──────────────────────────────────
                // F39: nada nace activo. Lo que el detector encuentra cae acá primero, en su
                // propio grupo — NUNCA mezclado con lo confirmado— y el dueño lo acepta o lo
                // descarta de a uno. Lo descartado no se vuelve a proponer (SubscriptionSync
                // respeta DISMISSED en cada barrido).
                if (candidatas.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Detectadas · por confirmar", count = candidatas.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                candidatas.forEach { s ->
                                    MinCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        variant = MinCardVariant.Elevated,
                                        padding = PaddingValues(18.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(s.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                            // Cada fila en SU moneda — el dólar se muestra como dólar.
                                            Text(
                                                text = formatMoney(s.amount, s.currency),
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                            )
                                        }
                                        Text(
                                            text = "Visto ${s.occurrences} ${if (s.occurrences == 1) "mes" else "meses"} · día ${s.dayOfMonth}",
                                            fontSize = 12.sp,
                                            color = MinTextMute,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        // Avisar ANTES de confirmar, no después: si el dueño ya
                                        // lo tiene anotado como recurrente, lo más probable es
                                        // que esta candidata sea el mismo cobro visto por el
                                        // detector. Se avisa y se deja decidir — confirmarla no
                                        // rompe el total (queda excluida, ver resumenRecurrentes).
                                        val yaEsRegla = claveDeNombre(s.displayName) in clavesDeReglas
                                        if (yaEsRegla) {
                                            Text(
                                                text = "Ya lo tienes como recurrente",
                                                fontSize = 12.sp,
                                                color = MinAmber,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ActionChip(
                                                label = if (yaEsRegla) "Confirmar igual" else "Confirmar",
                                                primary = true,
                                            ) { setStatus(s, SubStatus.CONFIRMED) }
                                            ActionChip("No es", primary = false) { setStatus(s, SubStatus.DISMISSED) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Próximos section ────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(
                            title = "Próximos",
                            count = if (vencimientosOk && proximos.isNotEmpty()) proximos.size else null,
                        )
                        // Cargando y sin nada todavía: no se pinta NADA. La rama de abajo con la
                        // lista vacía dibujaba un MinCard sin filas — una astilla de 4dp bajo el
                        // rótulo, que junto con la otra sección hacía ver la pantalla rota.
                        if (proximos.isEmpty() && loading) {
                            Unit
                        } else if (proximos.isEmpty()) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text("Nada vence en los próximos días", fontSize = 14.sp, color = MinTextMute)
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                proximos.forEachIndexed { i, payment ->
                                    UpcomingPaymentRow(
                                        payment = payment,
                                        onClick = {
                                            // F20: el pago de una tarjeta (card_) no es una regla
                                            // editable acá — se gestiona en Créditos, como las cuotas.
                                            if (payment.rule.id.startsWith(CREDIT_RULE_PREFIX) ||
                                                payment.rule.id.startsWith(CARD_RULE_PREFIX)
                                            ) {
                                                onNavigate(Screen.Credits)
                                            } else {
                                                editar(payment.rule)
                                            }
                                        },
                                    )
                                    if (i < proximos.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }

                // ── Por día del mes section ─────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(
                            title = "Por día del mes",
                            count = if (cifras != null && ordered.isNotEmpty()) ordered.size else null,
                            // El re-escaneo vive acá y no en el encabezado de la pantalla: es
                            // donde aparece lo que encuentra, y deja el título respirar en
                            // pantallas angostas.
                            action = if (scanning) "Buscando…" else "Buscar cobros",
                            onAction = { rescan() },
                        )
                        if (ordered.isEmpty() && loading) {
                            Unit
                        } else if (ordered.isEmpty()) {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                // Con la base vacía esta es de las primeras pantallas que se ven,
                                // así que dice qué hacer en vez de solo constatar el vacío. Los
                                // dos caminos, como los ofrece el encabezado: anotarlo a mano o
                                // dejar que Movi lo encuentre. (La pantalla vieja de
                                // Suscripciones daba esta pista y la fusión la había perdido.)
                                Text(
                                    text = "Sin recurrentes aún",
                                    fontSize = 14.sp,
                                    color = MinText,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Anota lo que se repite todos los meses —el arriendo, " +
                                        "el sueldo, Netflix— con «Nuevo recurrente». Si ya " +
                                        "importaste dos o tres meses de extractos, toca «Buscar " +
                                        "cobros» y Movi busca sola los que se repiten.",
                                    fontSize = 13.sp,
                                    color = MinTextMute,
                                    lineHeight = 18.sp,
                                )
                            }
                        } else {
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                ordered.forEachIndexed { i, item ->
                                    RecurrenteRow(
                                        item = item,
                                        accountNames = accountNames,
                                        onEditRule = { editar(it) },
                                        onRemoveSub = { quitar(it) },
                                    )
                                    if (i < ordered.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom sheet overlay
        if (sheetOpen) {
            CreateRecurringRuleSheet(
                onDismiss = { sheetOpen = false },
                onSaved = {
                    sheetOpen = false
                    loadKey++
                },
                // Se resuelve contra `rules` en el momento de pintar, no contra un objeto
                // guardado al tocar: `editar()` ya garantizó que la lista esté al día.
                existing = sheetRuleId?.let { id -> rules.firstOrNull { it.id == id } },
            )
        }
    }
}

// ── La lista única ────────────────────────────────────────────────────────────

// El tipo [Recurrente] y todo el cálculo viven en RecurrentesLogic.kt — puros y testeados.

@Composable
private fun RecurrenteRow(
    item: Recurrente,
    accountNames: Map<String, String>,
    onEditRule: (RecurringRule) -> Unit,
    onRemoveSub: (Subscription) -> Unit,
) {
    // Misma anatomía para las dos formas: día en un círculo, nombre + una línea de contexto, y
    // el monto a la derecha. Lo que cambia es el contexto y qué pasa al tocar.
    val nombre: String
    val contexto: String
    val monto: String
    val esIngreso: Boolean
    val onClick: (() -> Unit)?
    when (item) {
        is Recurrente.Regla -> {
            nombre = item.rule.name
            // Ola 9 · D: la cuenta, si la regla tiene una y sabemos su nombre. Si no la tiene
            // —lo normal en todo lo anotado antes de hoy— la fila se ve igual que siempre: no
            // hay «sin cuenta» ni un hueco que llenar, porque no falta nada.
            val cuenta = item.rule.accountId?.let { accountNames[it] }
            contexto = if (cuenta != null) "${item.rule.category} · $cuenta" else item.rule.category
            esIngreso = item.rule.type == TransactionType.INCOME
            monto = "${if (esIngreso) "+" else "−"}${formatCOP(item.rule.amount)}"
            onClick = { onEditRule(item.rule) }
        }
        is Recurrente.Suscripcion -> {
            nombre = item.sub.displayName
            contexto = when {
                // Lo primero que hay que decir: esta fila NO está sumando al total, porque el
                // mismo cobro ya entra por la regla que el dueño escribió. Sin esta línea, el
                // «Flujo libre» de arriba parecería no cuadrar con la lista de abajo.
                item.yaEsRegla -> "Ya lo tienes como recurrente · no se suma dos veces"
                // Heredada de antes de F39: se activó sin que el dueño la confirmara nunca.
                // La pantalla vieja lo marcaba con «· auto» y conviene que se siga notando.
                item.seActivoSola -> "Suscripción · la encontró Movi y la activó sola"
                // La marca discreta que pidió el dueño: que se note cuáles no anotó él.
                item.laEncontroMovi -> "Suscripción · la encontró Movi"
                else -> "Suscripción"
            }
            esIngreso = false
            // En SU moneda, sin convertir: una suscripción en dólares se lee "−US$12". Solo el
            // total de arriba pasa por la TRM, y lo dice.
            monto = "−" + formatMoney(item.sub.amount, item.sub.currency)
            // Una suscripción no tiene hoja de edición (el detector es su dueño); lo único que
            // se puede hacer con ella es quitarla, y para eso está el enlace de la derecha.
            onClick = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MinSurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${item.dayOfMonth}",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MinText,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nombre,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(contexto, fontSize = 12.sp, color = MinTextMute)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = monto,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (esIngreso) MinIncome else MinText,
                letterSpacing = (-0.3).sp,
            )
            if (item is Recurrente.Suscripcion) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Quitar",
                    fontSize = 12.sp,
                    color = MinExpense,
                    modifier = Modifier.clickable { onRemoveSub(item.sub) },
                )
            }
        }
    }
}

/** Los botones de «Confirmar» / «No es» del grupo de detectadas. */
@Composable
private fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (primary) MinBg else MinText)
    }
}

// ── Status badge helpers ──────────────────────────────────────────────────────

private fun dueDateDay(dueDate: String): Int =
    runCatching { LocalDate.parse(dueDate).dayOfMonth }.getOrElse {
        dueDate.takeLast(2).toIntOrNull()
            ?: dueDate.substringAfterLast('-').toIntOrNull()
            ?: 0
    }

private fun statusColor(status: PaymentStatus): Color = when (status) {
    PaymentStatus.OVERDUE   -> MinExpense
    PaymentStatus.DUE_TODAY -> MinAmber
    PaymentStatus.DUE_SOON  -> MinAmber
    PaymentStatus.UPCOMING  -> MinTextMute
}

private fun statusText(payment: UpcomingPayment): String {
    val n = payment.daysUntil
    val day = dueDateDay(payment.dueDate)
    return when (payment.status) {
        PaymentStatus.OVERDUE   -> "Vencido hace ${-n} ${if (-n == 1) "día" else "días"}"
        PaymentStatus.DUE_TODAY -> "Vence hoy"
        PaymentStatus.DUE_SOON  -> "Vence el $day · en $n ${if (n == 1) "día" else "días"}"
        PaymentStatus.UPCOMING  -> "Vence el $day · en $n ${if (n == 1) "día" else "días"}"
    }
}

@Composable
private fun UpcomingPaymentRow(payment: UpcomingPayment, onClick: () -> Unit) {
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            // Status pill badge + category
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Status label (colored)
                Text(
                    text = statusText(payment),
                    fontSize = 11.sp,
                    color = color,
                )
                // Separator dot
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MinTextFaint),
                )
                Text(rule.category, fontSize = 11.sp, color = MinTextMute)
            }
        }

        // Amount
        Text(
            text = "${if (isIncome) "+" else "−"}${formatCOP(rule.amount)}",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isIncome) MinIncome else MinText,
            letterSpacing = (-0.3).sp,
        )
    }
}


