package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick

/**
 * F13: filtro puro detrás de la búsqueda de Movimientos, separado del `@Composable` para poder
 * testearlo en `:shared:commonTest` sin arrancar Compose. Compara sin acentos ni mayúsculas
 * (mismo criterio que `CategoryField`, ver [normalizeForMatch]) contra descripción, comercio
 * (si lo hay) y categoría. Una consulta en blanco matchea todo — así el filtro es un no-op
 * mientras el campo de búsqueda está vacío.
 */
fun matchesQuery(event: FinancialEvent, query: String): Boolean {
    val q = normalizeForMatch(query.trim())
    if (q.isEmpty()) return true
    return normalizeForMatch(event.description).contains(q) ||
        event.merchant?.let { normalizeForMatch(it).contains(q) } == true ||
        normalizeForMatch(event.category).contains(q)
}

/**
 * Un renglón de Movimientos. No es siempre un evento: un **traspaso** son dos eventos (ver
 * [collapseTransfers]) que tienen que leerse como un solo hecho.
 */
sealed class MovementRow {
    /** Clave estable para `key` de la lista — el id del evento, o el del traspaso si son dos. */
    abstract val key: String

    data class Single(val event: FinancialEvent) : MovementRow() {
        override val key: String get() = event.id
    }

    /**
     * Las dos patas de un traspaso, juntas. [out] es siempre el EXPENSE (de dónde salió la
     * plata) e [into] el INCOME (a dónde entró) — no el orden en que vinieron en la lista.
     */
    data class Transfer(val out: FinancialEvent, val into: FinancialEvent) : MovementRow() {
        override val key: String get() = out.transferId ?: out.id
        val amount: Long get() = out.amount
    }
}

/** ¿Este movimiento es una pata de traspaso? */
fun isTransferLeg(event: FinancialEvent): Boolean =
    event.transferId != null || event.category == TRANSFER_CATEGORY

/**
 * ¿Este renglón es la **apertura de una cuenta** y no plata que entró o salió?
 *
 * El saldo inicial se guarda como un movimiento (INCOME por lo que había, EXPENSE por lo que se
 * debía) para que el saldo de la cuenta cuadre solo, pero no es un ingreso ni un gasto: es la
 * foto de lo que ya existía el día que la cuenta entró a la app. `isCashFlow` ya lo sabe y lo
 * deja fuera de todos los totales — esto es solo el lado de la presentación, para que la lista
 * lo diga con las mismas palabras que las cuentas.
 */
fun isOpeningBalance(event: FinancialEvent): Boolean = event.category == OPENING_CATEGORY

/**
 * ¿El renglón lleva signo y color de ingreso/gasto?
 *
 * `false` para lo que no movió plata del bolsillo — hoy, la apertura de una cuenta. Es
 * **exactamente el mismo criterio** que ya usa el renglón de un traspaso, con el mismo motivo
 * escrito ahí abajo: ponerle «+» y pintarlo de verde a algo que después se excluye de todos los
 * totales de ingresos es contradecirse dentro de una misma pantalla (Ola 8 · V6).
 */
fun rowShowsSign(event: FinancialEvent): Boolean = !isOpeningBalance(event)

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * El encabezado de un día: «HOY», «AYER», «23 DE AGOSTO» — nunca «2026-08-23».
 *
 * Ola 8 · V13: los encabezados mostraban la fecha ISO cruda que manda el server en
 * `EventDay.date`. Es la clave con la que se agrupa, no algo que alguien quiera leer.
 *
 * [hoy] entra por parámetro (y no se lee acá adentro) para que esto sea una función pura y
 * testeable sin relojes. Quien la llama pasa `todayIsoInAppZone()`, que ya resuelve la zona de
 * Bogotá con el plan B de `AppTimeZone`: en wasm no existe la base de zonas IANA y
 * `TimeZone.of("America/Bogota")` lanza, así que cae a UTC-5 fijo (exacto: Colombia no tiene
 * horario de verano). Acá abajo solo se compara y se resta un día con `LocalDate`, que es
 * aritmética de calendario pura — no toca la tabla de zonas y por eso no la puede romper.
 *
 * Un ISO que no se pueda parsear se devuelve tal cual: es preferible un encabezado feo a una
 * lista que no se pinta.
 */
fun formatDayHeading(iso: String, hoy: String): String {
    if (iso == hoy) return "Hoy"
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val hoyFecha = runCatching { LocalDate.parse(hoy) }.getOrNull()
    if (hoyFecha != null) {
        if (fecha == hoyFecha.minus(DatePeriod(days = 1))) return "Ayer"
        // El año solo se dice cuando NO es el corriente: repetir «de 2026» en cada
        // encabezado de 2026 es ruido.
        if (fecha.year != hoyFecha.year) return "${fecha.dayOfMonth} de ${MESES[fecha.monthNumber - 1]} de ${fecha.year}"
    }
    return "${fecha.dayOfMonth} de ${MESES[fecha.monthNumber - 1]}"
}

// Índices de los chips de arriba de Movimientos, con nombre para que el filtro de abajo (y sus
// tests) no dependan de recordar qué significaba el 1 y qué el 2.
const val CHIP_TODO = 0
const val CHIP_GASTOS = 1
const val CHIP_INGRESOS = 2
const val CHIP_POR_CONFIRMAR = 3

/**
 * ¿Este movimiento entra en el chip [chip]?
 *
 * **Las patas de un traspaso no aparecen ni en «Gastos» ni en «Ingresos».** No es un capricho de
 * pureza contable: son DOS chips y cada uno dejaba pasar UNA sola pata, así que
 * [collapseTransfers] se quedaba sin la hermana, caía a `Single` y el traspaso volvía a leerse
 * como «−$500.000 · Traspaso» — exactamente la lectura que esta feature vino a eliminar, una
 * pestaña más allá. Y es la misma regla que ya aplican el mes y los presupuestos
 * (`isCashFlow`): un traspaso no es un gasto ni un ingreso. En «Todo» sí aparece, como un solo
 * renglón, que es donde tiene sentido verlo.
 */
fun matchesChip(event: FinancialEvent, chip: Int): Boolean = when (chip) {
    CHIP_GASTOS -> event.type == TransactionType.EXPENSE &&
        event.reconciliationStatus != ReconciliationStatus.UNCONFIRMED &&
        !isTransferLeg(event) && !isOpeningBalance(event)
    // Ola 8 · V6: **la apertura de una cuenta tampoco es un ingreso**, por la misma razón que
    // no lo es una pata de traspaso. El chip «Ingresos» listaba dos «Saldo inicial» en verde y
    // con «+», y arriba el total decía «+$4.500.000» sin contarlos: filas pintadas como
    // ingresos, bajo un filtro llamado Ingresos, excluidas a propósito de todos los totales de
    // ingresos. La contradicción vivía entera en una sola pantalla. `isCashFlow` ya sabía la
    // respuesta desde siempre (OPENING_CATEGORY nunca es flujo); lo único que faltaba era que
    // el filtro dijera lo mismo. En «Todo» sí aparece, que es donde tiene sentido verlo.
    CHIP_INGRESOS -> event.type == TransactionType.INCOME &&
        !isTransferLeg(event) && !isOpeningBalance(event)
    CHIP_POR_CONFIRMAR -> event.reconciliationStatus == ReconciliationStatus.UNCONFIRMED
    else -> true
}

/**
 * Junta las dos patas de cada traspaso en **un solo renglón**.
 *
 * Sin esto, mover $5.000.000 de Ahorros al CDT aparecía en Movimientos como un egreso de
 * $5.000.000 y un ingreso de $5.000.000 sin ninguna relación visible: dos renglones que se leen
 * como plata que se gastó y plata que llegó, cuando es la misma plata que cambió de cuenta. Es la
 * forma más simple de arreglarlo sin inventar una pantalla nueva: la lista sigue siendo una lista
 * de hechos, y un traspaso es un hecho.
 *
 * Se junta **por `transferId`**, no por "un egreso y un ingreso del mismo monto el mismo día":
 * el enlace es explícito justamente para que esto no sea una adivinanza que un día empareje dos
 * movimientos que no tenían nada que ver.
 *
 * El renglón queda en el lugar de la primera pata que aparecía en la lista, así el orden
 * cronológico no se altera. Y si de un traspaso solo se ve una pata —porque un chip o la búsqueda
 * filtró a la otra— se muestra suelta, con su descripción ("Traspaso a CDT"), en vez de
 * desaparecer: la lista tiene que seguir mostrando lo que el filtro pidió.
 */
fun collapseTransfers(items: List<FinancialEvent>): List<MovementRow> {
    val porTraspaso = items.filter { it.transferId != null }.groupBy { it.transferId!! }
    val yaEmitidos = mutableSetOf<String>()
    return items.mapNotNull { event ->
        val transferId = event.transferId
        if (transferId == null) return@mapNotNull MovementRow.Single(event)
        val patas = porTraspaso[transferId].orEmpty()
        val salida = patas.firstOrNull { it.type == TransactionType.EXPENSE }
        val entrada = patas.firstOrNull { it.type == TransactionType.INCOME }
        if (salida == null || entrada == null) return@mapNotNull MovementRow.Single(event)
        if (!yaEmitidos.add(transferId)) null
        else MovementRow.Transfer(out = salida, into = entrada)
    }
}

/**
 * "De Ahorros a CDT": de qué cuenta a qué cuenta se movió la plata.
 *
 * Con palabras y no con una flecha: en wasm «→» sale como ▯ (la fuente del canvas no trae ese
 * glifo — el mismo problema que ya obligó a reemplazar el «›» por un ícono Material, ver
 * `ChevronRight`), y verificado en la web local antes de este cambio.
 *
 * Los nombres salen del mapa de cuentas y no de la descripción de las patas: si la lista de
 * cuentas todavía no llegó, se dicen los roles ("De Origen a Destino") en vez de inventar un
 * nombre que después resulte ser otro. Desde la Ola 8 (V7) el MISMO mapa alimenta el subtítulo
 * de un evento suelto — ver [MovementSingleRow]—, que antes no decía de qué cuenta era.
 */
fun transferRowSubtitle(row: MovementRow.Transfer, accountNames: Map<String, String>): String {
    val origen = accountNames[row.out.accountId] ?: "Origen"
    val destino = accountNames[row.into.accountId] ?: "Destino"
    return "De $origen a $destino"
}

/**
 * Minúsculas y sin tildes/eñe — mismo criterio que usa `CategoryField` (F35) para sus
 * sugerencias, pero definido acá aparte: esta pantalla no puede tocar `CategoryField.kt` en esta
 * tarea (Ola 4 la reserva para otro trabajo en paralelo), así que se duplica el normalizador en
 * vez de extraerlo a un helper compartido.
 */
private fun normalizeForMatch(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ñ' -> 'n'
                else -> c
            },
        )
    }
}

@Composable
fun TransactionsScreen(onNavigate: (Screen) -> Unit) {
    var activeFilter by remember { mutableStateOf(0) }
    // F13: la lupa era un dibujo sin acción — ahora despliega un campo que filtra en memoria
    // mientras se escribe (no hay ida al servidor: la lista ya está en pantalla).
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    // F12: "Pendientes" no decía qué es — son los movimientos que entraron solos (SMS, OCR,
    // extracto) y esperan que confirmes monto y categoría. "Por confirmar" sí lo dice.
    val filters = listOf("Todo", "Gastos", "Ingresos", "Por confirmar")

    var allDays by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // F10: el estado vacío necesita saber si hay cuentas para elegir entre "+ Registrar el
    // primero" y "Crear una cuenta primero" — sin esto no hay forma de saber si el problema es
    // "no hay movimientos" o "no hay dónde anotarlos".
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    // Solo se ofrece «Crear una cuenta primero» cuando la lista llegó y vino vacía; mientras
    // carga (o si falló) se ofrece registrar, que es la acción segura.
    var accountsLoaded by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshKey, refreshTick) {
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it; accountsLoaded = true }
    }
    // Los nombres de las cuentas, para que el renglón de un traspaso diga de dónde a dónde fue la
    // plata (ver [transferRowSubtitle]). Un evento suelto no los necesita: la cuenta no se muestra.
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    // V13: «hoy» en la zona de la app (Bogotá), para que los encabezados digan «HOY»/«AYER».
    // Se calcula una vez por composición y no dentro del bucle de días.
    val hoyIso = remember { todayIsoInAppZone() }

    // Candidatos a pago de tarjeta sin marcar (Task 2 del plan): entrada opcional, así que un
    // fallo al traerlos no debe tapar Movimientos con un snackbar.
    var candidates by remember { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    // Los que el dueño ya resolvió en esta pantalla — confirmados con "Marcar" o descartados con
    // "No es", mismo tratamiento para los dos. Se descuentan de `candidates` porque el refetch
    // puede fallar y dejar la lista vieja: sin esto, un pago recién resuelto volvía a aparecer
    // con sus botones activos y el contador seguía diciendo el número de antes — o sea, la app le
    // decía que su acción no se había guardado, cuando sí se guardó. Para "No es" en particular,
    // sin este descuento el falso positivo revivía en cuanto el refetch fallaba.
    var resolvedIds by remember { mutableStateOf(emptySet<String>()) }
    val pendingCandidates = candidates.filterNot { it.id in resolvedIds }
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    var showCandidatesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getEventsByDay() }
            .onSuccess {
                allDays = it
                // F35: de paso, alimenta el caché de "categorías ya usadas" que lee
                // CategoryField — esta pantalla ya carga los movimientos. Ola 9 · A3: con el
                // tipo de cada uno, para poder ofrecerlas del lado correcto.
                UsedCategoriesCache.recordAll(it.flatMap { d -> d.items }.map { ev -> ev.category to ev.type })
            }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(refreshKey, refreshTick) {
        runCatching { Repositories.wallets.getCardPaymentCandidates() }
            .onSuccess { candidates = it }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    fun signedAmount(tx: FinancialEvent): Long =
        if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount

    val visibleDays = remember(activeFilter, allDays, searchQuery) {
        allDays.mapNotNull { day ->
            val filtered = day.items
                .filter { matchesChip(it, activeFilter) }
                .filter { matchesQuery(it, searchQuery) }
            if (filtered.isEmpty()) null
            // El total recalculado sigue el mismo criterio que el del server (ver EventRoutes
            // /by-day): countsAsCashFlow deja fuera los movimientos de cuentas de deuda. Sin
            // esto, el encabezado del día decía $0 en "Todo" y +$60.000.000 en "Ingresos" —
            // el mismo número engañoso que esta rama vino a matar, una pestaña más allá.
            else day.copy(items = filtered, total = filtered.filter { it.countsAsCashFlow }.sumOf { signedAmount(it) })
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // F60: encabezado único — Movimientos es raíz: avatar + rótulo del menú + la lupa.
        MinScreenHeader(
            title = "Movimientos",
            leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
            action = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MinTextDim,
                    modifier = Modifier.size(22.dp).clickable {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        // F13: campo de búsqueda, debajo del encabezado — solo aparece al tocar la lupa.
        if (searchActive) {
            LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MinTextFaint, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Descripción, comercio o categoría", fontSize = 14.sp, color = MinTextFaint)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                        cursorBrush = SolidColor(MinText),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar búsqueda",
                    tint = MinTextMute,
                    modifier = Modifier.size(16.dp).clickable {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, f ->
                val isActive = i == activeFilter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(1.dp, MinBorderStrong, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { activeFilter = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isActive) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MinPrimary, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = f,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) MinText else MinTextDim,
                    )
                }
            }
        }

        // Entrada discreta a los candidatos de pago de tarjeta: solo aparece si hay algo que
        // proponer, y abre una lista donde cada uno se confirma por separado (ver
        // CardPaymentCandidatesSheet) — nunca se marcan todos de una.
        if (pendingCandidates.isNotEmpty()) {
            MinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                onClick = { showCandidatesSheet = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (pendingCandidates.size == 1) "1 pago de tarjeta sin marcar"
                               else "${pendingCandidates.size} pagos de tarjeta sin marcar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                    )
                    ChevronRight()
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            if (!loading && visibleDays.isEmpty()) {
                item {
                    if (searchQuery.isNotBlank()) {
                        // F13: nada que ver acá con "no hay dónde anotar" — la búsqueda no dio
                        // resultados, así que el texto buscado es la pista que hace falta.
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Nada coincide con \"${searchQuery.trim()}\"",
                                fontSize = 14.sp,
                                color = MinTextMute,
                            )
                        }
                    } else {
                        // F10: el estado vacío ofrece la acción — registrar si ya hay dónde
                        // anotar, crear una cuenta primero si no.
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Sin movimientos aún", fontSize = 14.sp, color = MinTextMute)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MinPrimaryContainer)
                                    .clickable {
                                        if (accounts.isNotEmpty() || !accountsLoaded) onNavigate(Screen.QuickAdd())
                                        else showCreateSheet = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (accounts.isNotEmpty() || !accountsLoaded) "+ Registrar el primero" else "Crear una cuenta primero",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MinOnPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            visibleDays.forEach { day ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                // V13: «23 DE AGOSTO» / «HOY», no la clave ISO del server.
                                text = formatDayHeading(day.date, hoyIso).uppercase(),
                                fontSize = 11.sp,
                                color = MinTextMute,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // V6: el total del día suma el FLUJO (lo que entró y salió del
                                // bolsillo), no todos los renglones de abajo — la apertura de
                                // una cuenta queda afuera. Decirlo con todas las letras cuesta
                                // dos palabras y evita que la cifra parezca una suma mal hecha.
                                Text(
                                    text = "Flujo del día",
                                    fontSize = 11.sp,
                                    color = MinTextFaint,
                                )
                                Text(
                                    text = "${if (day.total > 0) "+" else ""}${formatCOP(day.total)}",
                                    fontSize = 11.sp,
                                    color = MinTextMute,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            val rows = collapseTransfers(day.items)
                            rows.forEachIndexed { i, row ->
                                Column {
                                    when (row) {
                                        is MovementRow.Transfer -> TransferRow(
                                            row = row,
                                            accountNames = accountNames,
                                            // Al tocarlo se abre la hoja de categoría sobre la
                                            // pata de origen — que se niega a recategorizar y
                                            // explica por qué. Es la única acción que un traspaso
                                            // ofrece hoy desde acá, y es mejor que un renglón
                                            // muerto que no responde al toque.
                                            onClick = { selectedEvent = row.out },
                                        )
                                        is MovementRow.Single -> MovementSingleRow(
                                            tx = row.event,
                                            accountNames = accountNames,
                                            onClick = { selectedEvent = row.event },
                                        )
                                    }
                                    if (i < rows.size - 1) Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
    )

    selectedEvent?.let { event ->
        ChangeCategorySheet(
            event = event,
            onDismiss = { selectedEvent = null },
            onEventChanged = { selectedEvent = null; refreshKey++ },
        )
    }

    if (showCandidatesSheet) {
        CardPaymentCandidatesSheet(
            candidates = pendingCandidates,
            onDismiss = { showCandidatesSheet = false },
            onConfirmed = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
            onDismissedCandidate = { id -> resolvedIds = resolvedIds + id; refreshKey++ },
        )
    }

    if (showCreateSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateSheet = false },
            onAccountCreated = { showCreateSheet = false; refreshKey++ },
        )
    }
    }
}

/**
 * Un traspaso, leído como un solo hecho: "Traspaso · Ahorros → CDT" y el monto **sin signo**.
 *
 * Sin `+` ni `−` a propósito: la plata no entró ni salió del bolsillo, solo cambió de cuenta.
 * Ponerle un signo obligaría a elegir el punto de vista de una de las dos cuentas, que es
 * exactamente la confusión que este renglón viene a sacar. El signo de cada pata sí aparece, con
 * su cuenta al lado, en el detalle de cada cuenta.
 */
@Composable
private fun TransferRow(
    row: MovementRow.Transfer,
    accountNames: Map<String, String>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Traspaso",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.1).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(transferRowSubtitle(row, accountNames), fontSize = 12.sp, color = MinTextMute)
        }
        Text(
            text = formatCOP(row.amount),
            fontSize = 14.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MinTextMute,
            letterSpacing = (-0.3).sp,
        )
    }
}

/**
 * Un renglón suelto de la lista.
 *
 * Ola 8 · V7: el subtítulo dice ahora **de qué cuenta es el movimiento**. Con dos cuentas
 * abiertas había dos «Saldo inicial» idénticos y nada los distinguía — el renglón de un
 * traspaso sí decía «De Bancolombia Ahorros a Nequi» y los demás no decían nada. En su lugar
 * sale `EventSource` («MANUAL», «SMS»…), que además de no aportar nada acá es el nombre crudo
 * de un enum en una app que habla español; el punto naranja al lado de la descripción ya avisa
 * lo único que importaba de ahí: que el movimiento entró solo y falta confirmarlo.
 */
@Composable
private fun MovementSingleRow(
    tx: FinancialEvent,
    accountNames: Map<String, String>,
    onClick: () -> Unit,
) {
    val isIncome = tx.type == TransactionType.INCOME
    // V6: la apertura de una cuenta no lleva signo ni color — misma decisión, y mismo motivo,
    // que el renglón de un traspaso (ver [TransferRow]): la plata no entró ni salió, ya estaba.
    val conSigno = rowShowsSign(tx)
    val subtitulo = accountNames[tx.accountId]
        ?.let { "${tx.category} · $it" }
        ?: tx.category
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tx.description,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    letterSpacing = (-0.1).sp,
                )
                if (tx.reconciliationStatus == ReconciliationStatus.UNCONFIRMED) {
                    StatusDot(MinWarn)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitulo,
                fontSize = 12.sp,
                color = MinTextMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (conSigno) "${if (isIncome) "+" else "−"}${formatCOP(tx.amount)}"
                   else formatCOP(tx.amount),
            fontSize = 14.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = when {
                !conSigno -> MinTextMute
                isIncome -> MinIncome
                else -> MinText
            },
            letterSpacing = (-0.3).sp,
        )
    }
}
