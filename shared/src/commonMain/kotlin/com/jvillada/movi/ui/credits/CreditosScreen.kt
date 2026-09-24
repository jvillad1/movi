package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jvillada.movi.ui.LocalRefreshTick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.FormaDeCreditos
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.shared.model.resumirDeudas
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * F20 — Créditos es «todo lo que debes»: préstamos (cuota, tasa, plazo) y tarjetas de crédito
 * (cupo, corte, día de pago), con **un solo total de deuda** arriba que suma ambos — la misma
 * suma que muestra el acceso «Créditos» del Inicio (ver [totalDebtCop]).
 */
@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    // `null` = la lectura todavía no contestó bien; `emptyList()` = contestó y no hay ninguno. Son
    // dos cosas distintas y la pantalla las dice distinto: la primera con el esqueleto (o con
    // [NoSePudoLeer] si ya se rindió), la segunda con el vacío de siempre. Antes las dos eran una
    // lista vacía, y mientras cargaba se leía «Deuda total $0 · Sin créditos registrados» a quien
    // debe $2.191 millones en 12 préstamos. Una vez leídas no vuelven a `null`: si un reintento
    // falla se sigue mostrando lo último que se supo, como antes con `creditosLeidos`.
    var credits by remember { mutableStateOf<List<CreditSummary>?>(null) }
    var cards by remember { mutableStateOf<List<CardSummary>?>(null) }
    // El perfil ya contestó (bien o mal) al menos una vez. Hasta entonces el mes de la última cuota
    // se diría con el corte 1 y cambiaría de nombre cuando llegue el corte del dueño — ver
    // [ajustesDelPeriodo]. Si falla se sigue con el corte 1, como siempre: esto solo espera.
    var perfilContestado by remember { mutableStateOf(false) }
    var showTypeChooser by remember { mutableStateOf(false) }
    var showLoanSheet by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<CreditSummary?>(null) }
    var showCardSheet by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<CardSummary?>(null) }
    var adjusting by remember { mutableStateOf<CreditSummary?>(null) }
    // El crédito cuyo abono extraordinario se está simulando. No escribe nada: contesta «¿y si le
    // abono de más?». Ver [SimuladorDeAbonoSheet].
    var simulando by remember { mutableStateOf<CreditSummary?>(null) }
    // El crédito cuyo descuento de nómina se está registrando. No abre hoja: es un solo dato
    // (la cuota, que ya está en los términos) y el server lo hace idempotente por mes, así que
    // pedir confirmación sería ceremonia sobre algo que no se puede duplicar.
    var descontando by remember { mutableStateOf<CreditSummary?>(null) }
    var errorDescuento by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los créditos reales.
    var loading by remember { mutableStateOf(true) }
    // Ola 14 — Créditos también escucha el «se guardó algo» de la hoja de Agregar.
    //
    // Hasta esta rama no hacía falta: nada de lo que se podía guardar desde Agregar movía la
    // deuda de un crédito, así que `reloadKey` (los cambios hechos EN esta pantalla) alcanzaba.
    // Ahora un desembolso o un abono extraordinario sí la mueven, y sin este tick la pantalla
    // se quedaba mostrando la deuda vieja —verificado en el navegador: se guardó el abono de
    // $5.000.000 y Créditos siguió diciendo $70.000.000 hasta salir y volver a entrar—. Mismo
    // mecanismo que ya usaba Movimientos, y por el mismo motivo: la hoja es una modal y esta
    // pantalla nunca sale de la composición.
    val refreshTick = LocalRefreshTick.current
    // El día de corte del dueño y los inicios que movió a mano — lo mismo que cargan Movimientos,
    // el Inicio y Presupuestos. Ver [ajustesDelPeriodo] para por qué esta pantalla los necesita.
    var cutoffDay by remember { mutableStateOf(1) }
    var iniciosPropios by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(reloadKey, refreshTick) {
        loading = true
        val loans = launch { runCatching { Repositories.wallets.getCredits() }.onSuccess { credits = it } }
        val tarjetas = launch { runCatching { Repositories.wallets.getCards() }.onSuccess { cards = it } }
        // Si el perfil no se puede leer, se queda el corte 1 (el mes de calendario) y no se dice:
        // acá el período no cambia ninguna cifra de plata, solo el NOMBRE del mes de la última
        // cuota. Un error a pantalla completa por un mes corrido sería más ruido que el defecto.
        val perfil = launch {
            runCatching { Repositories.wallets.getUserProfile() }.onSuccess {
                cutoffDay = it.periodCutoffDay
                iniciosPropios = it.periodStarts
            }
            perfilContestado = true
        }
        loans.join()
        tarjetas.join()
        perfil.join()
        loading = false
    }
    // Sin las DOS respuestas no se sabe si hay deudas: «Deuda total $0 · Sin créditos» con el botón
    // de crear uno era mentirle a quien sí debe, e invitarlo a duplicar. Ver [NoSePudoLeer].
    val noSeLeyo = !loading && (credits == null || cards == null)
    // Lo que ya se puede pintar: las dos listas y el período. Mientras falte algo y la lectura siga
    // en vuelo, el esqueleto; una recarga con esto ya pintado NO vuelve al esqueleto (las listas no
    // vuelven a `null`), así que guardar un crédito no hace parpadear la pantalla.
    val creditosListos = credits.takeIf { perfilContestado }
    val tarjetasListas = cards.takeIf { perfilContestado }
    val cargando = !noSeLeyo && (creditosListos == null || tarjetasListas == null)
    val isEmpty = creditosListos?.isEmpty() == true && tarjetasListas?.isEmpty() == true
    // El plan de cada préstamo (interés de la cuota, si amortiza, cuándo termina). Se calcula acá
    // una sola vez y baja a las tarjetas: la aritmética vive en `:core` para que el server y los
    // tres clientes vean el mismo número. Ver [PlanDelCredito].
    val planes = remember(credits) { credits.orEmpty().associate { it.account.id to planDelCredito(it) } }
    // ── La forma de la última carga (ver `FormaRecordada`) ──────────────────────
    // Se lee una vez, al montar: es lo que el esqueleto reserva mientras la lectura no contestó.
    val formaRecordada = remember { FormaRecordada.delAparato.creditos(SessionManager.userId) }
    // Los renglones que ocupó cada aviso de la tarjeta de resumen, medidos al dibujarse — el texto
    // trae una cifra y parte distinto según su largo y el ancho de la pantalla. 0 = sin medir.
    var renglonesDelAvisoAmbar by remember { mutableStateOf(0) }
    var renglonesDelAvisoRojo by remember { mutableStateOf(0) }
    val resumen = remember(planes) {
        planes.values.filterNotNull().takeIf { it.isNotEmpty() }?.let { resumirDeudas(it) }
    }
    /**
     * El mes en curso, para poder decir «enero de 2046» en vez de «232 cuotas».
     *
     * **Con el período del dueño, no con el de calendario.** Acá había un `PeriodSettings()` a
     * secas —corte 1— con el argumento de que la fecha de la última cuota no depende del corte que
     * él use para sus gastos. Suena razonable y es falso en la práctica: esa fecha se dice contando
     * meses desde «el mes en curso», y con corte 25, el 26 de septiembre el resto de la app ya está
     * en octubre mientras esto seguía en septiembre. La misma deuda se anunciaba «la última en
     * octubre de 2029» acá y habría que leerla como noviembre — seis días de cada mes, en una
     * pantalla cuyo trabajo es decir cuándo se termina de pagar.
     *
     * Lo que importa no es cuál de los dos calendarios es «el correcto» en abstracto, es que Movi
     * tenga UNO: el dueño lee estas fechas al lado de las de Movimientos y el Inicio.
     */
    val ajustesDelPeriodo = remember(cutoffDay, iniciosPropios) {
        PeriodSettings(cutoffDay = cutoffDay.coerceIn(1, 31), iniciosPropios = iniciosPropios)
    }
    val periodoActual = remember(ajustesDelPeriodo) {
        periodoDe(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)
    }
    // Se escribe cuando la lectura salió bien y la tarjeta ya midió sus avisos: un aviso presente
    // sin medir todavía (0 renglones) esperaría al próximo cuadro, no se guarda como ausente.
    LaunchedEffect(creditosListos, tarjetasListas, resumen, renglonesDelAvisoAmbar, renglonesDelAvisoRojo) {
        if (creditosListos == null || tarjetasListas == null) return@LaunchedEffect
        val hayAmbar = (resumen?.creditosQueNoSeTerminan ?: 0) > 0
        val hayRojo = (resumen?.creditosQueCrecen ?: 0) > 0
        if (hayAmbar && renglonesDelAvisoAmbar == 0) return@LaunchedEffect
        if (hayRojo && renglonesDelAvisoRojo == 0) return@LaunchedEffect
        FormaRecordada.delAparato.guardarCreditos(
            SessionManager.userId,
            FormaDeCreditos(
                gruposDelResumen = resumen?.let { gruposDelResumen(it) }.orEmpty(),
                renglonesDelAvisoAmbar = if (hayAmbar) renglonesDelAvisoAmbar else 0,
                renglonesDelAvisoRojo = if (hayRojo) renglonesDelAvisoRojo else 0,
                prestamos = creditosListos.size,
            ),
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)
        ) {
            // F60: encabezado único — avatar en ancho (Créditos está en el rail), flecha a Más
            // en el teléfono (se llega por Más; F22: reserva si no hay historial). Con deudas ya
            // creadas, el alta compacta a la derecha (F18).
            //
            // Ola B: el alta compacta está **desde el primer cuadro**, también mientras carga. Antes
            // aparecía cuando llegaban los créditos y el título se corría. Solo se va en los dos
            // casos que tienen su propia forma: la lectura que no se pudo hacer (no se invita a
            // duplicar) y el vacío de verdad (el botón ancho de abajo).
            MinScreenHeader(
                title = "Créditos",
                leading = leadingFor(Screen.Credits, onProfile = { onNavigate(Screen.Profile) }, fallback = Screen.Mas),
                action = if (!isEmpty && !noSeLeyo) {
                    { NewItemButton(label = "Nuevo crédito", onClick = { showTypeChooser = true }) }
                } else null,
            )
            if (noSeLeyo) {
                Spacer(Modifier.height(14.dp))
                NoSePudoLeer(
                    "No pudimos cargar tus créditos",
                    onReintentar = { reloadKey++ },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else if (isEmpty) {
                NewItemButton(
                    label = "Nuevo crédito",
                    onClick = { showTypeChooser = true },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 14.dp),
                    full = true,
                )
            } else {
                Spacer(Modifier.height(14.dp))
            }

            if (cargando) {
                CreditosEsqueleto(forma = formaRecordada, modifier = Modifier.weight(1f))
            } else if (creditosListos != null && tarjetasListas != null) LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(22.dp),
                    ) {
                        Text("Deuda total", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        // F20: préstamos + tarjetas — la MISMA función que usa el Inicio.
                        // La protagonista de esta pantalla, igual que «Tu plata» en el Inicio: misma
                        // letra, mismo tamaño, y un renglón siempre. Ver [CifraProtagonista].
                        CifraProtagonista(formatCOP(totalDebtCop(creditosListos, tarjetasListas)), color = Movi.colores.texto)
                        // Lo que esa deuda CUESTA, que es lo que la pantalla no decía. La deuda
                        // total de arriba cuenta todos los créditos —quién paga la cuota no cambia
                        // de quién es el pasivo—; el costo mensual de acá sí separa. Ver
                        // [saleDeTuBolsillo].
                        LoQueCuestaLaDeuda(
                            planes.values.filterNotNull(),
                            periodoActual,
                            quienesPaganLoQueNoSaleDeTuBolsillo(creditosListos.mapNotNull { it.terms }),
                            onRenglonesDelAvisoAmbar = { renglonesDelAvisoAmbar = it },
                            onRenglonesDelAvisoRojo = { renglonesDelAvisoRojo = it },
                        )
                    }
                }

                if (isEmpty) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Mis créditos")
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            ) {
                                Text(
                                    "Sin créditos registrados",
                                    style = Movi.textos.cuerpo, color = Movi.colores.textoMedio,
                                )
                            }
                        }
                    }
                }

                if (creditosListos.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Préstamos", count = creditosListos.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                creditosListos.forEach { c ->
                                    LoanCard(
                                        credit = c,
                                        plan = planes[c.account.id],
                                        periodoActual = periodoActual,
                                        onOpen = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                                        onEdit = { editingLoan = c; showLoanSheet = true },
                                        onAdjust = { adjusting = c },
                                        onSimulate = { simulando = c },
                                        onPayrollDeduction = { descontando = c },
                                    )
                                }
                            }
                        }
                    }
                }

                if (tarjetasListas.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Tarjetas", count = tarjetasListas.size)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                tarjetasListas.forEach { c ->
                                    CreditCardCard(
                                        card = c,
                                        onOpen = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                                        onEdit = { editingCard = c; showCardSheet = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showTypeChooser) {
            DebtTypeChooserSheet(
                onDismiss = { showTypeChooser = false },
                onLoan = { showTypeChooser = false; editingLoan = null; showLoanSheet = true },
                onCard = { showTypeChooser = false; editingCard = null; showCardSheet = true },
            )
        }
        if (showLoanSheet) {
            CreditTermsSheet(
                editing = editingLoan,
                // «+ Nuevo crédito» se puede tocar desde el primer cuadro, antes de que contesten
                // los créditos. La hoja lee [candidates] en cada composición, así que la línea
                // «Ya tienes una deuda cargada como cuenta, ¿es esta?» aparece sola en cuanto
                // llegan — lo prueba `CreditosNoAfirmanMientrasCarganTest`.
                candidates = credits.orEmpty().filter { it.terms == null }.map { it.account },
                onDismiss = { showLoanSheet = false },
                onSaved = { showLoanSheet = false; reloadKey++ },
            )
        }
        if (showCardSheet) {
            CardTermsSheet(
                editing = editingCard,
                onDismiss = { showCardSheet = false },
                onSaved = { showCardSheet = false; reloadKey++ },
            )
        }
        simulando?.let { credit ->
            SimuladorDeAbonoSheet(
                credit = credit,
                periodoActual = periodoActual,
                onDismiss = { simulando = null },
            )
        }
        adjusting?.let { credit ->
            CreditBalanceSheet(
                credit = credit,
                onDismiss = { adjusting = null },
                onSaved = { adjusting = null; reloadKey++ },
            )
        }
        // El descuento de nómina se registra sin hoja: no hay nada que preguntar —el monto es la
        // cuota— y el server lo hace idempotente por mes, así que un doble toque no baja la deuda
        // dos veces.
        descontando?.let { credit ->
            LaunchedEffect(credit.account.id) {
                runCatching { Repositories.wallets.registerPayrollDeduction(credit.account.id) }
                    .onSuccess { descontando = null; reloadKey++ }
                    .onFailure { descontando = null; errorDescuento = it.toUserMessage() }
            }
        }
    }
}

/**
 * **Lo que la deuda de arriba cuesta**: intereses de este mes, los que faltan por pagar, y cuándo
 * cae la última cuota de todas.
 *
 * Las tres cifras salen del mismo desglose que la app ya calculaba al registrar cada pago
 * ([desglosarCuota]) y tiraba después de mover el saldo. Ver [PlanDelCredito].
 *
 * ### Cada cifra dice de qué habla, en el rótulo
 *
 * Hay **dos cortes cruzados** —de quién sale la plata, y si la deuda se termina— y por lo tanto
 * cifras con alcances distintos pegadas una debajo de la otra. La primera versión de esta tarjeta
 * las ponía en filas contiguas con una sola pista, en letra chica y solo cuando era mayor que cero:
 * «Intereses este mes» eran los propios, «Te falta en intereses» los propios **y** solo los que se
 * terminan (el 36 % de lo que de verdad falta), y «Tu última cuota» **todas**. Tres alcances, un
 * pie de página. Una cifra sin alcance le hace creer al dueño que le sobra plata que no tiene, así
 * que ahora cada una viaja con el suyo en el rótulo. Ver [ALCANCE_INTERES_PROPIO].
 */
@Composable
private fun LoQueCuestaLaDeuda(
    planes: List<PlanDelCredito>,
    periodoActual: PeriodoFinanciero,
    quienesPagan: List<String> = emptyList(),
    onRenglonesDelAvisoAmbar: (Int) -> Unit = {},
    onRenglonesDelAvisoRojo: (Int) -> Unit = {},
) {
    if (planes.isEmpty()) return
    val resumen = resumirDeudas(planes)
    Spacer(Modifier.height(16.dp))
    Hairline()
    Spacer(Modifier.height(14.dp))

    // **Las dos cifras se muestran, no se colapsan en una, y las dos son filas.** Quedarse solo con
    // el total le cobraría al bolsillo millones que no salen de su cuenta; quedarse solo con lo suyo
    // escondería que existen; y dejar la ajena como nota al pie hacía que un dueño con solo créditos
    // de Skandia leyera «Intereses este mes — $0» en la fila titular. Ver [saleDeTuBolsillo].
    TituloDelGrupo(TITULO_INTERES_DEL_MES)
    FilaDelResumen(ALCANCE_INTERES_PROPIO, formatCOP(resumen.interesMensualPropio))
    if (resumen.interesMensualAjeno > 0L) {
        Spacer(Modifier.height(4.dp))
        FilaDelResumen(alcanceDelInteresAjeno(quienesPagan), formatCOP(resumen.interesMensualAjeno))
    }

    if (resumen.interesPorPagarPropio > 0L || resumen.interesPorPagarAjeno > 0L) {
        Spacer(Modifier.height(12.dp))
        TituloDelGrupo(TITULO_INTERES_POR_PAGAR)
        if (resumen.interesPorPagarPropio > 0L) {
            FilaDelResumen(ALCANCE_FALTA_PROPIO, formatCOP(resumen.interesPorPagarPropio))
        }
        if (resumen.interesPorPagarAjeno > 0L) {
            Spacer(Modifier.height(4.dp))
            FilaDelResumen(alcanceDeLaFaltaAjena(quienesPagan), formatCOP(resumen.interesPorPagarAjeno))
        }
    }

    // La fecha final solo aparece cuando hay algo que decir: o una fecha, o el «Sin fecha» que el
    // aviso de abajo explica. Una cartera entera sin tasas registradas no tiene ninguna de las dos.
    if (resumen.mesesHastaLaUltimaCuota != null || resumen.creditosQueNoSeTerminan > 0) {
        Spacer(Modifier.height(12.dp))
        TituloDelGrupo(TITULO_ULTIMA_CUOTA)
        FilaDelResumen(ALCANCE_ULTIMA_CUOTA, textoDeLaUltimaCuota(resumen, periodoActual))
    }

    // **Los dos avisos, y el primero es el que faltaba.** `creditosQueNoSeTerminan` se calculaba,
    // se probaba y no se dibujaba: la única advertencia visible contaba 1 (la amortización
    // negativa) y los $100.000.000 del Crédito Mamá no aparecían en ningún lado del resumen.
    if (resumen.creditosQueNoSeTerminan > 0) {
        Spacer(Modifier.height(12.dp))
        AvisoDeLaPantalla(
            texto = textoDeLoQueNoSeTermina(resumen.creditosQueNoSeTerminan, resumen.deudaQueNoSeTermina),
            color = Movi.colores.aviso,
            fondo = Movi.colores.tarjeta,
            onRenglones = onRenglonesDelAvisoAmbar,
        )
    }
    // La alerta, arriba de todo y contada en créditos: si hay uno solo en el que la deuda crece
    // sola, el dueño tiene que salir de esta pantalla sabiéndolo.
    if (resumen.creditosQueCrecen > 0) {
        Spacer(Modifier.height(8.dp))
        AvisoDeLaPantalla(
            texto = textoDeLaAmortizacionNegativa(resumen.creditosQueCrecen),
            color = Movi.colores.sale,
            fondo = Movi.colores.sale.copy(alpha = 0.14f),
            onRenglones = onRenglonesDelAvisoRojo,
        )
    }

    Spacer(Modifier.height(12.dp))
    Text(SUPUESTO_DE_LA_PROYECCION, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, lineHeight = 15.sp)
}

/** El rótulo de un grupo de filas del resumen: qué se está midiendo, antes de con qué alcance. */
@Composable
private fun TituloDelGrupo(titulo: String) {
    Text(titulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
}

/**
 * Una fila del resumen. [alcance] no es una decoración: es **de qué habla** la cifra de la derecha,
 * y va en el rótulo justamente para que no se pueda leer el número sin él.
 */
@Composable
private fun FilaDelResumen(alcance: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(alcance, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(valor, style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
    }
}

/**
 * **Esto tiene que ser imposible de no ver.** Antes, un crédito cuya deuda crece sola se veía igual
 * que uno recién creado: una barra en 0 % y la palabra «pagado» al lado.
 */
@Composable
private fun AvisoDeLaPantalla(texto: String, color: Color, fondo: Color, onRenglones: (Int) -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .padding(horizontal = RELLENO_DEL_AVISO_H, vertical = RELLENO_DEL_AVISO_V),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = color,
            lineHeight = INTERLINEADO_DEL_AVISO,
            fontWeight = FontWeight.Medium,
            // Cuántos renglones ocupó, para que la próxima carga reserve ese alto (ver
            // `FormaRecordada`).
            onTextLayout = { onRenglones(it.lineCount) },
        )
    }
}

/** Los rellenos y el interlineado de [AvisoDeLaPantalla], compartidos con [AvisoEsqueleto]. */
private val RELLENO_DEL_AVISO_H = 12.dp
private val RELLENO_DEL_AVISO_V = 10.dp
private val INTERLINEADO_DEL_AVISO = 16.sp

/**
 * Tarjeta de un préstamo: cuota, tasa, plazo y progreso — lo que Créditos mostraba desde siempre.
 * Ola 7 (F61): como las deudas ya no se listan en Cuentas, tocar la tarjeta abre el historial
 * de la cuenta ([onOpen] → AccountDetail); editar términos pasa a ser el lápiz de la derecha.
 *
 * Desde esta rama también dice **cuánto de la cuota es interés y cuándo se termina la deuda**
 * ([plan]), que es lo que la pantalla no contestaba.
 */
@Composable
private fun LoanCard(
    credit: CreditSummary,
    plan: PlanDelCredito?,
    periodoActual: PeriodoFinanciero,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onSimulate: () -> Unit,
    onPayrollDeduction: () -> Unit,
) {
    // Ola 14: no siempre es un porcentaje. Un crédito recién creado en $0 —el paso 1 de registrar
    // un desembolso— decía «100% pagado» con la barra llena. Ver [progresoDeCredito].
    val progreso = progresoDeCredito(credit)
    val pct = progreso.fraccion
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(credit.account.name, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
            Text(credit.terms?.let { if (it.sinIntereses) "Sin intereses" else "${it.rateEa}% EA" } ?: "", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            EditTermsIcon(onEdit)
        }
        Text(credit.terms?.bank ?: "Sin términos registrados", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatCOP(credit.account.balance), style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.3).sp)
            // Sin monoespaciada cuando no es una cifra (ver [ProgresoDeCredito.esAviso]).
            Text(
                progreso.etiqueta,
                style = Movi.textos.apoyo,
                fontFamily = if (progreso.esAviso) FontFamily.Default else FontFamily.Monospace,
                color = Movi.colores.textoMedio,
            )
        }
        // La barra solo se dibuja cuando hay progreso que dibujar. Una barra vacía sobre una deuda
        // que creció por encima del capital original dice lo contrario de lo que pasó — la etiqueta
        // de al lado ya dice cuánto se pasó, en pesos. Ver [ProgresoDeCredito.mostrarBarra].
        if (progreso.mostrarBarra) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .testTag(TAG_BARRA_DE_PROGRESO)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Movi.colores.hilo)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Movi.colores.texto.copy(alpha = 0.9f))
                )
            }
        }
        credit.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Quién paga va PEGADO a la cuota, no en una fila aparte: es lo que decide si
                // ese número sale del bolsillo del dueño, y leerlo suelto —«$9.147.408» a secas—
                // es exactamente el malentendido que esta feature vino a evitar. Sin esto, la
                // pantalla de Créditos sumaría $13,1 millones al mes de cuotas que él no paga.
                Text(
                    text = when {
                        t.payrollDeduction -> "Cuota · día ${t.dayOfMonth} · de tu nómina"
                        !t.paidBy.isNullOrBlank() -> "Cuota · día ${t.dayOfMonth} · la paga ${t.paidBy}"
                        else -> "Cuota · día ${t.dayOfMonth}"
                    },
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Text(formatCOP(t.installment), style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            }
            // Plazo y fecha de desembolso: son los dos datos que uno compara contra el extracto,
            // y estaban solo dentro de la hoja de edición.
            //
            // Dice **«Plazo pactado»** y no solo «105 meses» porque tres líneas más abajo aparece
            // la proyección, y las dos difieren: al ·9695 le quedan 42 meses de contrato y 46 de
            // proyección. Dos números de cuotas sin rótulo en la misma tarjeta se leen como un
            // error de la app; con el rótulo son dos hechos distintos —lo que se firmó y lo que
            // pasa a este ritmo— y la diferencia es justamente la información.
            Spacer(Modifier.height(6.dp))
            // El plazo se lleva el ancho que sobre y el banco se corta con «…». Con los dos sin peso,
            // un banco de nombre largo se quedaba sin lugar y se partía una letra por renglón:
            // visto en la web a 390 dp con «Banco de ejemplo».
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
            ) {
                Text(
                    "Plazo pactado ${t.termMonths} meses · desde ${t.startDate}",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    t.bank,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // **Qué parte de esa cuota es alquiler de la plata, y cuándo se termina esta deuda.**
            // Las dos salen del mismo desglose que la app ya calculaba al registrar cada pago y
            // tiraba después de mover el saldo. Ver [PlanDelCredito].
            plan?.let { p ->
                Spacer(Modifier.height(10.dp))
                Text(textoDelInteres(p), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, lineHeight = 16.sp)
                comoVaEstaDeuda(p, periodoActual)?.let { comoVa ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        comoVa.texto,
                        style = Movi.textos.apoyo,
                        // La alerta va con color y en negrita; el resto no. El Crédito Mamá —cuya
                        // cuota también es interés puro, pero por acuerdo— cae del lado sin color.
                        color = if (comoVa.esAlerta) Movi.colores.sale else Movi.colores.textoApagado,
                        fontWeight = if (comoVa.esAlerta) FontWeight.Medium else FontWeight.Normal,
                        lineHeight = 16.sp,
                    )
                }
                // **«¿Y si abonas de más?»**, pegado a la proyección que modifica y no en la fila
                // de acciones de abajo. Por dos motivos: ahí abajo son operaciones que ESCRIBEN
                // (registrar un descuento, ajustar el saldo) y esto no escribe nada; y con un
                // crédito que paga un tercero eran tres botones en una fila que no entra en 411dp.
                //
                // Solo donde hay algo que simular: sin tasa, sin cuota o sin deuda no hay
                // proyección que acortar, y la hoja solo podría contestar «no se sabe». Ver
                // [ComoVaLaDeuda.seProyecta].
                if (p.comoVa.seProyecta) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ACCION_SIMULAR_ABONO,
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.marca,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickableSimple(onSimulate)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            // La NOTA, que es donde vive lo que falta confirmar con el banco («plazo estimado»,
            // «la deuda subió», «preguntar el capital original»). Estaba escondida detrás del
            // lápiz: había que entrar a cada crédito para recordar qué tenía pendiente, que es
            // justo lo contrario de para qué sirve una nota.
            t.notes?.takeIf { it.isNotBlank() }?.let { nota ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = nota,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    lineHeight = 16.sp,
                )
            }
        }
        // La deuda es estado (se mueve a diario por intereses), no
        // contrato: por eso cuadrarla con el banco vive fuera de la
        // hoja de términos.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // Una libranza no se «paga»: ya se descontó del sueldo. Lo único que falta es que la
            // deuda lo refleje, y eso es un toque — no anotar un gasto que no existió.
            // Y una cuota que paga OTRO tampoco se «paga» desde acá: la giró Skandia, o la pagó
            // Caro. Lo único que falta es que la deuda lo refleje. Mismo endpoint que la
            // libranza (ver POST /credits/{id}/payroll-deduction), distinto rótulo — el rótulo
            // es lo que le dice al dueño qué está confirmando.
            val quienPaga = credit.terms?.paidBy?.takeIf { it.isNotBlank() }
            if (quienPaga != null && credit.terms?.payrollDeduction != true) {
                Text(
                    "Registrar pago de $quienPaga",
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.marca,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableSimple(onPayrollDeduction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            if (credit.terms?.payrollDeduction == true) {
                Text(
                    "Registrar descuento",
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.marca,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableSimple(onPayrollDeduction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "Ajustar saldo",
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.textoMedio,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickableSimple(onAdjust)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Tarjeta de una tarjeta de crédito (F20): deuda actual, cupo disponible si hay, corte y día de
 * pago. La deuda se muestra en la moneda de la tarjeta ([formatMoney]) — una Mastercard en USD
 * debe dólares, y su componente COP sería $0, un número que miente.
 */
@Composable
private fun CreditCardCard(card: CardSummary, onOpen: () -> Unit, onEdit: () -> Unit) {
    val currency = card.account.currency
    val debt = card.account.balancesByCurrency[currency] ?: card.account.balance
    // Ola 7 (F61): tocar la tarjeta abre su historial (AccountDetail); el lápiz edita corte y pago.
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(card.account.name, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
            if (currency != "COP") {
                Text(currency, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            EditTermsIcon(onEdit)
        }
        Text(card.terms?.bank ?: "Sin corte ni pago — edítalos con el lápiz", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatMoney(debt, currency), style = Movi.textos.monto, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.3).sp)
            card.available?.let {
                Text("Disponible ${formatMoney(it, currency)}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        card.terms?.let { t ->
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(t.cutoffDay?.let { "Corte · día $it" } ?: "Sin día de corte", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Text("Pago · día ${t.paymentDay}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
}

/**
 * Selector previo al alta (F20): un préstamo y una tarjeta no se crean igual — el préstamo
 * tiene capital, tasa y plazo; la tarjeta, cupo, corte y día de pago. Preguntar primero evita
 * una hoja única llena de campos que no aplican.
 */
@Composable
private fun DebtTypeChooserSheet(
    onDismiss: () -> Unit,
    onLoan: () -> Unit,
    onCard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
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
            SheetHandleWithClose(onClose = onDismiss)
            // El contenido de la hoja se desplaza.
            //
            // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
            // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
            // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
            // propia hoja. Sin manera de llegar a él.
            //
            // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
            // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
            // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                Text("¿Qué deuda quieres registrar?", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                Spacer(Modifier.height(14.dp))
                DebtTypeOption(
                    title = "Préstamo",
                    subtitle = "Cuota fija, tasa y plazo — libranza, libre inversión, vehículo",
                    onClick = onLoan,
                )
                Spacer(Modifier.height(8.dp))
                DebtTypeOption(
                    title = "Tarjeta de crédito",
                    subtitle = "Cupo, día de corte y día de pago",
                    onClick = onCard,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DebtTypeOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/** Lápiz de «editar términos» a la derecha de la fila — acción secundaria explícita (Ola 7). */
@Composable
private fun EditTermsIcon(onEdit: () -> Unit) {
    Icon(
        Icons.Rounded.Edit,
        contentDescription = "Editar términos",
        tint = Movi.colores.textoMedio,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickableSimple(onEdit)
            .padding(6.dp)
            .size(16.dp),
    )
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)

/** La tarjeta de «Deuda total», cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_RESUMEN_DE_DEUDA: String = "tarjeta-del-resumen-de-deuda"

/** La cifra esqueleto de la tarjeta de «Deuda total» — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA: String = "esqueleto-del-resumen-de-deuda"

/** Cada tarjeta de préstamo que todavía no llegó. */
const val TAG_ESQUELETO_TARJETA_DE_PRESTAMO: String = "esqueleto-tarjeta-de-prestamo"

/** Cada aviso de la tarjeta de «Deuda total» que todavía no llegó (solo si la última carga lo tenía). */
const val TAG_ESQUELETO_DE_AVISO: String = "esqueleto-de-aviso-de-credito"

/** Tope de tarjetas de préstamo esqueleto: más no caben en ninguna pantalla, y todas se componen. */
private const val MAX_PRESTAMOS_ESQUELETO = 12

/**
 * **Créditos mientras carga: la forma de lo que viene, sin una sola cifra.**
 *
 * Ola B. Antes esta pantalla, en frío, decía «Deuda total $0» y «Sin créditos registrados» durante
 * el segundo que tardaba la lectura — y después aparecían $2.191 millones en 12 préstamos. No era
 * solo un salto: era una afirmación falsa sobre la plata del dueño, en la cifra más grande de la
 * pantalla.
 *
 * Cada pieza copia los rellenos y los estilos de texto de la real ([LoQueCuestaLaDeuda],
 * [LoanCard]) para que la tarjeta de arriba mida lo mismo cargando que cargada (±8 dp, lo mide
 * `CreditosNoAfirmanMientrasCarganTest`).
 *
 * **Con [forma] (la última carga que salió bien en este aparato, ver `FormaRecordada`) reserva
 * exactamente lo que había**: las filas de cada grupo del resumen, los avisos ámbar y rojo con los
 * renglones que ocuparon, y tantas tarjetas de préstamo como había. Sin ella —la primera vez— la
 * forma de siempre: los tres grupos de una fila, sin avisos, y tres préstamos; una cartera más
 * chica encoge al llegar, una con avisos crece (una sola vez: la próxima ya los recuerda).
 *
 * Es una `LazyColumn` sin desplazamiento y no una `Column`: así recorta lo que no entra en vez de
 * dibujarlo encima de la barra de abajo.
 */
@Composable
private fun CreditosEsqueleto(forma: FormaDeCreditos?, modifier: Modifier = Modifier) {
    val grupos = forma?.gruposDelResumen ?: listOf(1, 1, 1)
    val prestamos = (forma?.prestamos ?: 3).coerceAtMost(MAX_PRESTAMOS_ESQUELETO)
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 80.dp), userScrollEnabled = false) {
        item {
            MinCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(22.dp),
            ) {
                LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
                Spacer(Modifier.height(10.dp))
                LineaEsqueleto(
                    fraccionDelAncho = 0.7f,
                    estilo = Movi.textos.cifra,
                    modifier = Modifier.testTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA),
                )
                // Sin grupos, [LoQueCuestaLaDeuda] no dibuja nada: ni el hilo, ni los avisos, ni el
                // supuesto.
                if (grupos.isNotEmpty()) {
                    // Los mismos espacios que [LoQueCuestaLaDeuda]: 16 · hilo · 14 y 12 entre grupos.
                    Spacer(Modifier.height(16.dp))
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                    grupos.forEachIndexed { i, filas ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        // [TituloDelGrupo] y [FilaDelResumen]: el rótulo, 6 dp, y cada fila con 10 dp
                        // de sangría y 4 dp entre una y otra.
                        LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.apoyo)
                        Spacer(Modifier.height(6.dp))
                        repeat(filas) { f ->
                            if (f > 0) Spacer(Modifier.height(4.dp))
                            RenglonConCifraEsqueleto(
                                modifier = Modifier.padding(start = 10.dp),
                                fraccionDelRotulo = 0.55f,
                            )
                        }
                    }
                    // Los avisos, con los espacios de [LoQueCuestaLaDeuda]: 12 antes del ámbar, 8
                    // antes del rojo.
                    val ambar = forma?.renglonesDelAvisoAmbar ?: 0
                    if (ambar > 0) {
                        Spacer(Modifier.height(12.dp))
                        AvisoEsqueleto(ambar)
                    }
                    val rojo = forma?.renglonesDelAvisoRojo ?: 0
                    if (rojo > 0) {
                        Spacer(Modifier.height(8.dp))
                        AvisoEsqueleto(rojo)
                    }
                    Spacer(Modifier.height(12.dp))
                    // [SUPUESTO_DE_LA_PROYECCION]: dos renglones de apoyo a 15 sp de interlineado en
                    // un teléfono.
                    val supuesto = Movi.textos.apoyo.copy(lineHeight = 15.sp)
                    LineaEsqueleto(fraccionDelAncho = 0.95f, estilo = supuesto)
                    LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = supuesto)
                }
            }
        }
        if (prestamos > 0) {
            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    RotuloDeSeccionEsqueleto()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(prestamos) { TarjetaDePrestamoEsqueleto() }
                    }
                }
            }
        }
    }
}

/**
 * Un aviso de la tarjeta de resumen que todavía no llegó: un solo bloque del alto de
 * [AvisoDeLaPantalla] con [renglones] renglones — sus rellenos más un interlineado por renglón.
 */
@Composable
private fun AvisoEsqueleto(renglones: Int) {
    val interlineado = with(LocalDensity.current) { INTERLINEADO_DEL_AVISO.toDp() }
    BloqueEsqueleto(
        alto = RELLENO_DEL_AVISO_V * 2 + interlineado * renglones,
        modifier = Modifier.testTag(TAG_ESQUELETO_DE_AVISO),
    )
}

/**
 * Un préstamo que todavía no llegó, con la forma de [LoanCard]: nombre y tasa, el banco, el saldo
 * con su avance, la barra, y debajo del hilo la cuota, el plazo y la línea del interés.
 */
@Composable
private fun TarjetaDePrestamoEsqueleto() {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.titulo) }
            BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 52.dp)
            // El lugar del lápiz de [EditTermsIcon]: 8 dp antes, 6 dp alrededor y 16 dp de ícono.
            Box(Modifier.padding(start = 8.dp).padding(6.dp)) { BloqueEsqueleto(alto = 16.dp, ancho = 16.dp) }
        }
        LineaEsqueleto(
            fraccionDelAncho = 0.3f,
            estilo = Movi.textos.apoyo,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.monto), ancho = 112.dp)
            Spacer(Modifier.weight(1f))
            BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 64.dp)
        }
        Spacer(Modifier.height(8.dp))
        BloqueEsqueleto(alto = 2.dp)
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        RenglonConCifraEsqueleto(fraccionDelRotulo = 0.4f)
        Spacer(Modifier.height(6.dp))
        LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.apoyo)
        Spacer(Modifier.height(10.dp))
        LineaEsqueleto(fraccionDelAncho = 0.85f, estilo = Movi.textos.apoyo)
    }
}
