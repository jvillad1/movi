package com.jvillada.movi.ui.accounts

import com.jvillada.movi.data.SaldosOcultosStore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.esDeTuPlata
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.accountDayTotal
import com.jvillada.movi.shared.model.masRecientePrimero
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import androidx.compose.material.icons.rounded.Add
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.homeScreenFor
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.credits.HojasDeLaDeuda
import com.jvillada.movi.ui.credits.SeccionDeLaDeuda
import com.jvillada.movi.ui.credits.rememberDeudaDelDetalle
import com.jvillada.movi.ui.credits.textosDeLosPagosQueNoFueronLaCuota
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TonoDelMonto
import com.jvillada.movi.ui.cuadre.textoDelUltimoCuadre
import com.jvillada.movi.shared.model.sePuedeCuadrar
import kotlinx.coroutines.CancellationException
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.transactions.formatDayHeading

@Composable
fun AccountDetailScreen(onNavigate: (Screen) -> Unit, accountId: String, group: AccountGroup) {
    val goBack = LocalGoBack.current
    var account by remember { mutableStateOf<Account?>(null) }
    var days by remember { mutableStateOf<List<EventDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }  // true de entrada: antes de la primera lectura no se afirma ni vacío ni error
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    // Ver [NoSePudoLeer]: «Sin movimientos aún» solo si la lectura contestó.
    var leida by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<FinancialEvent?>(null) }
    /**
     * Las cuentas del dueño, **para poder mover un movimiento de cuenta desde esta pantalla**.
     *
     * Se piden **al tocar una fila** y no al entrar, por lo mismo que «Esto se repite» pide las
     * suyas al tocar el botón: entrar acá ya cuesta dos lecturas (la cuenta y sus movimientos), y
     * la enorme mayoría de las veces el dueño entra a mirar, no a corregir. Y se piden una sola
     * vez por visita.
     *
     * Si la lectura falla, la lista queda vacía y la hoja lo dice: el selector de cuenta no se
     * abre, y el monto y el concepto se siguen pudiendo corregir. Una lista de cuentas que no
     * llegó no puede impedir arreglar una cifra.
     */
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    /**
     * **Las condiciones de este crédito** (la cuota pactada y su día de pago), o `null` si esta
     * cuenta no es un préstamo, si no tiene condiciones cargadas, o si la lectura falló.
     *
     * Es el único dato que esta pantalla no tenía para poder explicar un pago que no fue la cuota
     * (ver [textosDeLosPagosQueNoFueronLaCuota]): la fila de la deuda ya trae su capital y su
     * `noAmortiza`. Viaja con el día de pago, que decide a qué cuota va cada pago. Se lee **solo
     * en una cuenta LOAN** y junto con los movimientos, así que una cuenta de ahorros no paga
     * ningún viaje de más. Si no llega, la línea no se dibuja: es una
     * explicación de más, no un dato sin el cual la pantalla mienta.
     */
    // Vuelta de diseño 4: la deuda de esta cuenta (préstamo o tarjeta) con sus términos y sus
    // acciones, que dejaron de vivir en la lista de Créditos. Las mismas lecturas que Créditos.
    val deuda = rememberDeudaDelDetalle(accountId, account?.type, reintento = refreshKey)
    val condiciones: CreditTerms? = deuda?.prestamo?.terms
    // Con «Ocultar los saldos» en la llave: son textos con plata adentro, y guardados con la cifra
    // a la vista no se taparían al tocar el ojo.
    val lineasDeLaCuota = remember(days, condiciones, SaldosOcultosStore.ocultos) {
        val terms = condiciones
        if (terms == null) {
            emptyMap()
        } else {
            textosDeLosPagosQueNoFueronLaCuota(
                movimientos = days.flatMap { it.items },
                cuotaPactada = terms.installment,
                diaDePago = terms.dayOfMonth,
                hoy = Clock.System.now().toLocalDateTime(AppTimeZone.zone).date,
            )
        }
    }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showCondicion by remember { mutableStateOf(false) }
    /**
     * Los apartados de ESTA cuenta, con lo que le queda a cada uno (el server lo calcula). Solo se
     * leen en una cuenta de tu plata en pesos; si la lectura falla, ninguno: la sección sigue
     * ofreciendo «Apartar plata» y el resto de la pantalla no se entera.
     */
    var apartados by remember { mutableStateOf<List<Apartado>>(emptyList()) }
    var apartadosKey by remember { mutableStateOf(0) }
    /** La hoja de apartar abierta, con el apartado que se edita (o ninguno, si es nuevo). */
    var hojaDeApartado by remember { mutableStateOf<HojaDeApartado?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        try {
            val acc = Repositories.wallets.getAccount(accountId)
            val events = Repositories.wallets.getEvents(accountId)
            val grouped = events
                .groupBy { epochToDate(it.timestamp) }
                .map { (date, items) ->
                    EventDay(
                        date  = date,
                        // accountDayTotal, no countsAsCashFlow (Hallazgo bloqueante 1 de la
                        // revisión de esta rama). Movimientos/`by-day` filtran por
                        // countsAsCashFlow porque agregan VARIAS cuentas para responder "¿cuánta
                        // plata entró o salió del bolsillo?" — acá ya se sabe de qué cuenta se
                        // trata (es esta pantalla), así que esa pregunta no aplica. Lo que no
                        // miente es "¿cuánto se movió el saldo de esta cuenta hoy?", con SU
                        // convención: en LOAN/CREDIT_CARD un INCOME (abono) resta. Con
                        // countsAsCashFlow el total habría dado $0 todos los días en una cuenta
                        // LOAN (la regla es "LOAN nunca es flujo de caja") — incluso el día de un
                        // ajuste de $60.000.000 — y en CREDIT_CARD el abono habría desaparecido
                        // del total aunque sí mueve la deuda. Ver el KDoc de accountDayTotal.
                        total = accountDayTotal(acc.type, items),
                        // masRecientePrimero, no `sortedByDescending { it.timestamp }` (que es
                        // lo que había): sin desempate, dos movimientos del mismo milisegundo
                        // —las dos patas de un traspaso, un lote de extracto, o cualquier día
                        // pasado, donde la fecha elegida a mano se sella al mediodía— quedaban en
                        // el orden en que hubieran llegado. Hoy llegan bien porque `getEvents` ya
                        // ordena y `sortedByDescending` es estable, así que esto no cambia nada en
                        // pantalla: cambia que deje de depender de eso. Es el quinto lugar que
                        // ordena la misma lista — ver MAS_RECIENTE_PRIMERO, que los enumera.
                        items = items.masRecientePrimero(),
                    )
                }
                .sortedByDescending { it.date }
            account = acc
            days = grouped
            leida = true
            // La cuota pactada ([condiciones]) llega con la deuda ([rememberDeudaDelDetalle]): si
            // esa lectura falla, la pantalla se ve como siempre, sin la línea que la explica.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, no Exception: en la web un fetch caído llega como `kotlin.Error`, y lo
            // que se escapa de este efecto congela la app entera. Ver ExcepcionesDeRedScanTest.
            error = e.toUserMessage()
        }
        loading = false
    }

    // Los apartados, aparte de la lectura de arriba: guardar uno no relee los movimientos.
    val sePuedeApartar = account?.let { it.esDeTuPlata() && it.currency == "COP" } == true
    LaunchedEffect(sePuedeApartar, apartadosKey, refreshKey, refreshTick) {
        if (!sePuedeApartar) return@LaunchedEffect
        apartados = runCatching { Repositories.wallets.getApartados() }
            .getOrDefault(emptyList())
            .filter { it.accountId == accountId }
    }

    // La lista de cuentas, recién cuando el dueño toca una fila (ver [cuentas]). Un fallo acá no
    // se cuenta como error de la pantalla: no rompe nada de lo que está mirando.
    LaunchedEffect(selectedEvent != null) {
        if (selectedEvent != null && cuentas.isEmpty()) {
            cuentas = runCatching { Repositories.wallets.getAccounts() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    // **Filas, no `movementCount`** — y desde la Ola 16 conviene decir por qué, porque el resto de
    // la app se movió en la dirección contraria y esto va a parecer un olvido.
    //
    // `movementCount` (`:core`) no cuenta las aperturas, y ahora tampoco las lista Movimientos.
    // Acá sí se listan, a propósito y por la misma razón por la que el total del día de esta
    // pantalla usa `accountDayTotal` y no `countsAsCashFlow` (ver el bloque de arriba): las otras
    // pantallas agregan VARIAS cuentas para contestar «¿qué hice con mi plata?», donde una
    // apertura no es un hecho; esta contesta «¿de dónde sale el saldo de ESTA cuenta?», y ahí la
    // apertura es el primer renglón de la respuesta — es el ancla de la que sale todo lo demás.
    //
    // Por eso el número tiene que ser el de filas: es el rótulo de la lista que está justo abajo,
    // y ponerle `movementCount` diría «13» sobre 14 renglones visibles — exactamente la
    // contradicción entre un contador y una lista que la Ola 16 vino a sacar de Movimientos,
    // reintroducida acá. El mismo número alimenta el aviso de borrado (`DeleteAccountSheet`), que
    // enumera lo que se va con la cuenta: ahí también son filas, porque la apertura se borra.
    val totalEvents = days.sumOf { it.items.size }
    // Cuántos de esos movimientos tienen su otra mitad en OTRA cuenta. Sale de lo que ya está en
    // pantalla —no hay endpoint nuevo, y funciona igual sin red— porque `transferId` viaja en cada
    // evento. Una pata ya huérfana (de un borrado anterior) lo tiene en null y no cuenta: su otra
    // punta ya no existe, así que borrar esta cuenta no le hace nada a nadie más.
    val transferLegs = days.sumOf { day -> day.items.count { it.transferId != null } }
    // Ola 14: y cuánta plata suman. El aviso de borrado decía cuántos movimientos eran pero no
    // cuánto valían, y desde que una punta de un traspaso puede ser un crédito ese renglón puede
    // valer el crédito entero — ver [transferWarningLabel]. Solo la moneda de la cuenta: mezclar
    // monedas en un total daría una cifra que no es de nadie.
    val transferLegsAmount = days.sumOf { day ->
        day.items.filter { it.transferId != null && it.currency == (account?.currency ?: "COP") }
            .sumOf { it.amount }
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            val accountTypePair = account?.let { accountTypeIcon(it.type) }
            // F60 · F22: encabezado único; sin historial, el detalle vuelve a donde vive la
            // cuenta: Créditos si es deuda (Ola 7: tarjetas y préstamos ya no se listan en
            // Cuentas), Cuentas en cualquier otro caso. El grupo viene en la pantalla, no de la
            // cuenta cargada: así la reserva es la correcta desde el primer frame (y es el mismo
            // dato con el que `navTabFor` resalta la pestaña).
            MinScreenHeader(
                title = account?.name ?: "",
                leading = HeaderLeading.Back(fallback = homeScreenFor(group)),
                action = if (accountTypePair != null) {
                    {
                        Icon(
                            imageVector = accountTypePair.first,
                            contentDescription = accountTypePair.second,
                            tint = Movi.colores.textoMedio,
                            modifier = Modifier.size(Icono.l),
                        )
                    }
                } else null,
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Movi.colores.lavado(Movi.colores.marca),
                    trackColor = Movi.colores.tarjeta,
                )
            } else {
                Spacer(Modifier.height(Medida.altoDeBarraDeProgreso))
            }

            // **Abrir arriba.** Mientras la cuenta no llega, el primer ítem de la lista es
            // «section-header»; cuando llega, el encabezado del saldo se inserta ANTES, y la
            // LazyColumn ancla la posición por clave: se queda mirando «section-header» y el saldo
            // queda escondido arriba (en la web, con la red de por medio, abría así siempre). La
            // primera vez que la cuenta llega, la lista vuelve al principio.
            val lista = rememberLazyListState()
            val cuentaLlego = account != null
            LaunchedEffect(cuentaLlego) {
                if (cuentaLlego) lista.scrollToItem(0)
            }

            LazyColumn(
                state = lista,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Medida.margenDePantalla,
                    end = Medida.margenDePantalla,
                    top = Medida.dentroDeSeccion,
                    bottom = Medida.botonFlotante + Medida.entreSecciones,
                ),
            ) {
                // **El saldo arriba** (Ola 2a). Antes el detalle abría casi directo en la lista y el
                // dueño tenía que leer una tarjeta genérica para saber de qué cuenta se trataba: ahora
                // el encabezado dice la cuenta (su marca), cuánto hay, desde cuándo nadie la cuadra y
                // cómo cuadrarla. El saldo es el que ya trae `Account` —derivado por el server, sin
                // anulados—: acá no se suma nada a mano.
                account?.let { acc ->
                    item(key = "balance-card") {
                        EncabezadoDeLaCuenta(
                            acc = acc,
                            onCuadrar = { onNavigate(Screen.CuadreDeSaldos) },
                        )
                        Spacer(Modifier.height(Medida.entreSecciones))
                    }

                    // **La deuda** (vuelta de diseño 4): sus condiciones y sus acciones —«Editar las
                    // condiciones», «Ajustar saldo», «¿Y si abonas de más?», registrar el pago de un
                    // tercero—, que antes eran botones dentro de la tarjeta de Créditos.
                    if (deuda != null && (deuda.prestamo != null || deuda.tarjeta != null)) {
                        item(key = "deuda") {
                            SeccionDeLaDeuda(deuda)
                            Spacer(Modifier.height(Medida.entreSecciones))
                        }
                    }

                    // **El camino de escritura de `Account.condicionadaA`.** Sin esta fila el
                    // campo nacía muerto: solo se podía guardar al crear la cuenta, y la que lo
                    // motivó —la pensión voluntaria del dueño— ya existía en producción. Ver
                    // [CondicionDeCuentaSheet].
                    //
                    // No aparece en una deuda: una tarjeta o un préstamo no son plata que se
                    // pueda usar para algo, así que la pregunta no significa nada ahí.
                    if (!isDebtAccount(acc.type)) {
                        item(key = "condicion") {
                            Superficie(relleno = RellenoDeSuperficie.Lista) {
                                Fila(
                                    titulo = "¿Solo sirve para algo?",
                                    subtitulo = acc.condicionadaA
                                        ?.let { "Solo para $it · no suma en «Tu plata»" }
                                        ?: "Puedes usar esta plata para lo que quieras",
                                    final = { Chevron() },
                                    onClick = { showCondicion = true },
                                )
                            }
                            Spacer(Modifier.height(Medida.entreSecciones))
                        }
                    }
                }

                // **Lo apartado** de esta cuenta: cada propósito con lo que le queda, y lo libre.
                // Solo en una cuenta de tu plata en pesos (el server no aparta en otra).
                account?.takeIf { sePuedeApartar }?.let { acc ->
                    item(key = "apartado") {
                        SeccionDeApartados(
                            account = acc,
                            apartados = apartados,
                            onEditar = { hojaDeApartado = HojaDeApartado(it) },
                            onApartar = { hojaDeApartado = HojaDeApartado(null) },
                        )
                        Spacer(Modifier.height(Medida.entreSecciones))
                    }
                }

                // Section header
                item(key = "section-header") {
                    Seccion(titulo = "Movimientos", conteo = totalEvents.takeIf { it > 0 })
                }

                // Empty state
                if (!loading && days.isEmpty() && !leida) {
                    item(key = "no-se-pudo-leer") {
                        NoSePudoLeer(
                            "No pudimos cargar los movimientos de esta cuenta",
                            onReintentar = { refreshKey++ },
                            modifier = Modifier.padding(top = Espacio.xxxl),
                        )
                    }
                } else if (!loading && days.isEmpty()) {
                    item(key = "empty-state") {
                        // F10: la acción registra directo en esta cuenta — no en la primera de
                        // la lista, que es lo que pasaba antes de que QuickAdd aceptara un
                        // accountId preseleccionado.
                        Vacio(
                            titulo = "Sin movimientos aún",
                            detalle = "Lo que anotes en esta cuenta o llegue del banco aparece aquí.",
                            accion = "Registrar el primero",
                            onAccion = { onNavigate(Screen.QuickAdd(presetAccountId = accountId)) },
                        )
                    }
                }

                // Day groups
                days.forEach { day ->
                    item(key = day.date) {
                        Column(modifier = Modifier.padding(top = Espacio.m)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Espacio.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    // «Hoy», «Ayer», «23 de agosto» — no el «2026-08-23» de la clave.
                                    // Ola R4: en frase, no en mayúsculas.
                                    text = formatDayHeading(day.date, hoyEnAppZone().toString()),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (day.items.any { it.currency == "COP" }) {
                                    Text(
                                        // Se pasa el valor absoluto: el signo ya lo pone el if de acá
                                        // (siempre "+" o "−", incluso en 0) — pasarle el total con signo
                                        // a formatCOP (F36) duplicaría el "−" cuando el día cierra en rojo.
                                        text = "${if (day.total >= 0) "+" else "−"}${formatCOP(kotlin.math.abs(day.total))}",
                                        // El tamaño del rótulo del día que tiene al lado, con dígitos
                                        // tabulares como todo monto.
                                        style = Movi.textos.apoyo.copy(fontFeatureSettings = "tnum"),
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                            }
                            Superficie(relleno = RellenoDeSuperficie.Lista) {
                                day.items.forEachIndexed { i, event ->
                                    Column {
                                        // **Las mismas filas que Movimientos** (Ola 2a): con la marca
                                        // del comercio, la categoría de subtítulo y sin el `source`
                                        // crudo («MANUAL», «SMS») que se leía acá. Dos cosas propias
                                        // de esta pantalla, pasadas como parámetro:
                                        //
                                        // - **El signo se pinta SIEMPRE.** En Movimientos `tonoDelEvento`
                                        //   se lo quita a la pata huérfana y a la apertura porque ahí el
                                        //   total del día es flujo de caja. Esta pantalla suma con
                                        //   `accountDayTotal`, que cuenta TODO lo que mueve el saldo —
                                        //   la pata huérfana incluida—: sin signo, la fila contradiría
                                        //   el total de ESTA pantalla.
                                        // - **Qué le pasó a la deuda si este pago no fue la cuota**, en
                                        //   una línea debajo (ver [textosDeLosPagosQueNoFueronLaCuota]:
                                        //   se juzga la cuota entera, en su último pago).
                                        MovementSingleRow(
                                            tx = event,
                                            accountNames = emptyMap(),
                                            nota = lineasDeLaCuota[event.id],
                                            tonoForzado = if (event.type == TransactionType.INCOME) TonoDelMonto.INGRESO else TonoDelMonto.GASTO,
                                            onClick = { selectedEvent = event },
                                        )
                                        if (i < day.items.size - 1) Separador()
                                    }
                                }
                            }
                        }
                    }
                }

                // F55: al final de la pantalla, en rojo — mismo lugar donde el dueño esperaba
                // encontrarlo cuando pidió "ayúdame a eliminarla" y no había nada acá. Solo
                // aparece una vez que la cuenta cargó (necesita accountName real para la hoja).
                account?.let { acc ->
                    item(key = "delete-account") {
                        Boton(
                            texto = "Eliminar cuenta",
                            onClick = { showDeleteAccount = true },
                            variante = VarianteDeBoton.Peligro,
                            anchoCompleto = true,
                            modifier = Modifier.padding(top = Medida.entreSecciones),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // La barra inferior ya no vive dentro de esta pantalla (la pinta App.kt debajo),
                // así que el snackbar solo necesita separarse del borde.
                .padding(bottom = Medida.margenDePantalla),
        )

        selectedEvent?.let { event ->
            // **La misma hoja que Movimientos**, y no `VoidEventSheet` a secas como hasta acá.
            //
            // Esta es la pantalla donde el dueño NOTA que un saldo está mal —es la que le muestra
            // el saldo y los renglones que lo forman— y lo único que se le ofrecía sobre la fila
            // era **anular**: justo el rodeo destructivo que la edición vino a reemplazar. Anular
            // sigue estando, al final de la hoja y separado; arriba están el monto, la cuenta, el
            // concepto, la fecha y la categoría. Ver [HojaDelMovimiento].
            //
            // `onVerCuenta = null` (el default) a propósito: la rama del saldo inicial ofrece
            // «Ver la cuenta» y acá ya estamos en ella.
            HojaDelMovimiento(
                event = event,
                cuentas = cuentas,
                onDismiss = { selectedEvent = null },
                onCambiado = {
                    selectedEvent = null
                    refreshKey++
                },
            )
        }

        deuda?.let {
            HojasDeLaDeuda(
                deuda = it,
                onCambiada = { refreshKey++ },
                onError = { mensaje -> error = mensaje },
            )
        }

        if (showCondicion) {
            account?.let { acc ->
                CondicionDeCuentaSheet(
                    account = acc,
                    onDismiss = { showCondicion = false },
                    onGuardada = { actualizada ->
                        // Se pinta lo que contestó el server, sin recargar: la cuenta ya viene
                        // completa en la respuesta. El Inicio se entera por su cuenta — el
                        // repositorio invalida su caché en cada escritura (ver
                        // `InvalidaElInicioAlEscribir`).
                        account = actualizada
                        showCondicion = false
                    },
                )
            }
        }

        hojaDeApartado?.let { hoja ->
            // `key`: cada apertura es una hoja nueva, con su propio id para uno nuevo.
            account?.let { acc ->
                key(hoja) {
                    ApartadoSheet(
                        account = acc,
                        existente = hoja.existente,
                        onDismiss = { hojaDeApartado = null },
                        // Se relee la lista: lo que le queda a cada uno lo calcula el server.
                        onGuardado = { hojaDeApartado = null; apartadosKey++ },
                        onQuitado = { hojaDeApartado = null; apartadosKey++ },
                    )
                }
            }
        }

        if (showDeleteAccount) {
            account?.let { acc ->
                DeleteAccountSheet(
                    accountId = acc.id,
                    accountName = acc.name,
                    eventCount = totalEvents,
                    transferCount = transferLegs,
                    transferAmount = transferLegsAmount,
                    // El saldo de ESTA cuenta, que es lo que de verdad se va con ella. En una
                    // deuda se usa el estimado en COP si lo hay (una tarjeta en USD), que es la
                    // misma cifra con la que `assetsDebtsNet` arma el patrimonio: el aviso tiene
                    // que decir el número que el dueño va a ver cambiar, no otro.
                    accountBalance = if (isDebtAccount(acc.type)) {
                        acc.estimatedTotalCop ?: acc.balance
                    } else {
                        acc.balance
                    },
                    accountIsDebt = isDebtAccount(acc.type),
                    // Si se usó el estimado, la cifra ya está en COP aunque la cuenta sea en USD.
                    accountBalanceCurrency = if (isDebtAccount(acc.type) && acc.estimatedTotalCop != null) {
                        "COP"
                    } else {
                        acc.currency
                    },
                    transferCurrency = account?.currency ?: "COP",
                    onDismiss = { showDeleteAccount = false },
                    onDeleted = {
                        // F22: mismo destino de reserva que la flecha de volver de acá arriba —
                        // AccountsScreen recarga sola al re-entrar (LaunchedEffect(refreshKey)
                        // con estado fresco, ver App.kt: cada pantalla se descompone del todo al
                        // salir de su rama del `when`), así que no hace falta pedirle un refresh.
                        showDeleteAccount = false
                        goBack(Screen.Accounts)
                    },
                )
            }
        }
    }
}

// La clave de agrupación, en la zona de la app (no la del aparato): la misma que usa Movimientos.
private fun epochToDate(millis: Long): String = fechaDeEpoch(millis).toString()

private fun accountTypeIcon(type: AccountType): Pair<ImageVector, String> = when (type) {
    AccountType.CASH        -> Icons.Filled.Payments to "Efectivo"
    AccountType.SAVINGS     -> Icons.Filled.AccountBalance to "Ahorros"
    AccountType.CHECKING    -> Icons.Filled.AccountBalanceWallet to "Corriente"
    AccountType.INVESTMENT  -> Icons.AutoMirrored.Filled.TrendingUp to "Inversión"
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to "Crédito"
    AccountType.LOAN        -> Icons.Filled.RequestQuote to "Préstamo"
}

/** Qué hoja de apartar está abierta: [existente] `null` = uno nuevo. */
private class HojaDeApartado(val existente: Apartado?)

/**
 * La sección «Apartado» del detalle de una cuenta: cada apartado con lo que le queda (tocarlo lo
 * edita), lo libre de la cuenta y «Apartar plata». Sin apartados, solo la invitación.
 */
@Composable
private fun SeccionDeApartados(
    account: Account,
    apartados: List<Apartado>,
    onEditar: (Apartado) -> Unit,
    onApartar: () -> Unit,
) {
    if (apartados.isNotEmpty()) Seccion(titulo = "Apartado")
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        apartados.forEach { apartado ->
            Fila(
                titulo = apartado.proposito,
                subtitulo = apartado.categoria?.let { "Se descuenta con los gastos de $it" },
                final = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                        Cifra(apartado.restante)
                        Chevron()
                    }
                },
                onClick = { onEditar(apartado) },
            )
            Separador()
        }
        if (apartados.isNotEmpty()) {
            Fila(titulo = "Libre", final = { Cifra(libreDeLaCuenta(account, apartados), tono = TonoDeCifra.Apagado) })
            Separador()
        }
        Fila(
            titulo = "Apartar plata",
            subtitulo = if (apartados.isEmpty()) "Separa plata de esta cuenta para algo puntual" else null,
            inicio = { IconoEnCirculo(Icons.Rounded.Add) },
            final = { Chevron() },
            onClick = onApartar,
        )
    }
}

/** El `testTag` del encabezado del detalle de una cuenta. */
const val TAG_ENCABEZADO_DE_LA_CUENTA: String = "encabezado-de-la-cuenta"

/**
 * El encabezado del detalle de una cuenta: su avatar (la marca del banco, o el ícono del tipo), el
 * saldo en la cifra protagonista, el tipo y desde cuándo no se cuadra, y «Cuadrar con el banco», que
 * abre Cuadre de saldos —lo que ya existe— y no una forma nueva de tocar un saldo.
 *
 * **Colores.** Un saldo en contra de una cuenta de dinero va en rojo: es de lo poco que de verdad pide
 * atención. Una deuda va en el color del texto: deber no es una alerta (Ola 2a), y un saldo a favor
 * de una tarjeta sí va en verde, porque es plata tuya.
 */
@Composable
private fun EncabezadoDeLaCuenta(acc: Account, onCuadrar: () -> Unit) {
    val isCard = isDebtAccount(acc.type)
    val typeLabel = accountTypeIcon(acc.type).second
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_ENCABEZADO_DE_LA_CUENTA)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            AvatarDeLaCuenta(acc, tamano = TamanoDeAvatar.Encabezado)
            Column(modifier = Modifier.weight(1f)) {
                // Ola R4: en frase, no en mayúsculas.
                Text(
                    text = if (isCard) "Deuda actual" else "Saldo actual",
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.textoMedio,
                )
                if (typeLabel.isNotEmpty()) {
                    Text(
                        text = if (isCard) typeLabel else "${acc.currency} · $typeLabel",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        if (isCard) {
            val (debt, isEstimate) = cardDebt(acc)
            // El signo invertido —negativo es saldo a FAVOR— vive en [saldoDeDeuda] desde la Ola 15.
            // Queda en `CifraProtagonista` (el texto ya armado) por el «≈» de una deuda estimada.
            val saldo = saldoDeDeuda(debt)
            CifraProtagonista(
                text = "${if (isEstimate) "≈" else ""}${if (saldo.aFavor) "+" else ""}${saldo.magnitud}",
                color = if (saldo.aFavor) Movi.colores.entra else Movi.colores.texto,
            )
        } else {
            Cifra(acc.balance, VarianteDeCifra.Heroe, tono = if (acc.balance < 0) TonoDeCifra.Alerta else TonoDeCifra.Neutro)
        }
        if (hasForeignBalance(acc)) {
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CurrencyBreakdown(acc)
        }
        if (sePuedeCuadrar(acc)) {
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                Text(
                    text = textoDelUltimoCuadre(acc, hoyEnAppZone()),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.weight(1f),
                )
                Boton(
                    texto = "Cuadrar con el banco",
                    onClick = onCuadrar,
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                )
            }
        }
    }
}
