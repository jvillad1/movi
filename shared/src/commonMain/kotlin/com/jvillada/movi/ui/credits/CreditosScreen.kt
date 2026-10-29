package com.jvillada.movi.ui.credits

import com.jvillada.movi.ui.components.ds.CifraConRotulo
import com.jvillada.movi.ui.components.ds.DisposicionDeCifraConRotulo
import com.jvillada.movi.ui.components.ds.RotuloConValor
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.FormaDeCreditos
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.shared.model.resumirDeudas
import com.jvillada.movi.shared.model.deudasSinSaldoDe
import com.jvillada.movi.shared.model.interesDelMes
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.shared.model.ultimaCuotaDeTodas
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.FilaDeNavegacion
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import androidx.compose.ui.graphics.vector.ImageVector
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.recurrentes.BajoLaFila
import com.jvillada.movi.ui.recurrentes.MontoDeFila
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.datetime.Clock
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * F20 — Créditos es «todo lo que debes»: préstamos (cuota, tasa, plazo) y tarjetas de crédito
 * (cupo, corte, día de pago), con **un solo total de deuda** arriba que suma ambos — la misma
 * suma que muestra el acceso «Créditos» del Inicio (ver [totalDebtCop]).
 */
@Composable
fun CreditosScreen(onNavigate: (Screen) -> Unit) {
    var reloadKey by remember { mutableStateOf(0) }
    // **Lo último que se vio, al primer cuadro** (ver [rememberLectura]): los préstamos, las tarjetas
    // y el perfil arrancan con lo que dejó la visita anterior y se releen igual, con `reloadKey`
    // (los cambios hechos EN esta pantalla) y con `LocalRefreshTick` (se guardó algo desde la hoja
    // de Agregar: un desembolso o un abono extraordinario mueven la deuda, y la hoja es una modal,
    // así que esta pantalla nunca sale de la composición — verificado en el navegador: sin el tick,
    // Créditos seguía diciendo $70.000.000 después de un abono de $5.000.000).
    val creditosLeidos = rememberLectura(ClaveDeLectura.Creditos, reintento = reloadKey) { Repositories.wallets.getCredits() }
    val tarjetasLeidas = rememberLectura(ClaveDeLectura.Tarjetas, reintento = reloadKey) { Repositories.wallets.getCards() }
    // Si el perfil no se puede leer, se queda el corte 1 (el mes de calendario) y no se dice: acá el
    // período no cambia ninguna cifra de plata, solo el NOMBRE del mes de la última cuota. Un error
    // a pantalla completa por un mes corrido sería más ruido que el defecto.
    val perfilLeido = rememberLectura(ClaveDeLectura.Perfil, reintento = reloadKey) { Repositories.wallets.getUserProfile() }
    // `null` = la lectura todavía no contestó bien (ni en esta visita ni en una reciente);
    // `emptyList()` = contestó y no hay ninguno. Son dos cosas distintas y la pantalla las dice
    // distinto: la primera con el esqueleto (o con [NoSePudoLeer] si ya se rindió), la segunda con
    // el vacío de siempre. Antes las dos eran una lista vacía, y mientras cargaba se leía «Deuda
    // total $0 · Sin créditos registrados» a quien debe $2.191 millones en 12 préstamos. Una vez
    // leídas no vuelven a `null`: si un reintento falla se sigue mostrando lo último que se supo, y
    // se dice (ver `noSePudoActualizar`).
    val credits = creditosLeidos.valor
    val cards = tarjetasLeidas.valor
    // El perfil ya se sabe (leído, recordado, o su lectura terminó aunque haya fallado). Hasta
    // entonces el mes de la última cuota se diría con el corte 1 y cambiaría de nombre cuando llegue
    // el corte del dueño — ver [ajustesDelPeriodo]. Si falla se sigue con el corte 1: esto solo espera.
    val perfilContestado = perfilLeido.valor != null || perfilLeido.terminada
    var showTypeChooser by remember { mutableStateOf(false) }
    // Desde la vuelta 31 estas hojas solo crean: completar o editar una deuda se hace en su detalle.
    var showLoanSheet by remember { mutableStateOf(false) }
    var showCardSheet by remember { mutableStateOf(false) }
    // Vuelta de diseño 4: «Ajustar saldo», «¿Y si abonas de más?» y «Registrar pago/descuento» ya
    // no son botones de la lista: viven en el detalle de la deuda (ver [SeccionDeLaDeuda]).
    // Ola 2 #6: mismo guard que ya usaba Recurrentes — sin esto el botón ancho de "vacío"
    // parpadeaba un instante antes de que llegaran los créditos reales.
    val loading = creditosLeidos.actualizando || tarjetasLeidas.actualizando || perfilLeido.actualizando
    // El día de corte del dueño y los inicios que movió a mano — lo mismo que cargan Movimientos,
    // el Inicio y Presupuestos. Ver [ajustesDelPeriodo] para por qué esta pantalla los necesita.
    val cutoffDay = perfilLeido.valor?.periodCutoffDay ?: 1
    val iniciosPropios = perfilLeido.valor?.periodStarts ?: emptyMap()
    // Alguna lectura falló con lo de antes a la vista: lo que se ve es lo último que vimos, y se
    // dice arriba hasta que contesten.
    val noSePudoActualizar = creditosLeidos.falloConAlgoALaVista || tarjetasLeidas.falloConAlgoALaVista ||
        perfilLeido.falloConAlgoALaVista
    // Sin las DOS respuestas no se sabe si hay deudas: «Deuda total $0 · Sin créditos» con el botón
    // de crear uno era mentirle a quien sí debe, e invitarlo a duplicar. Ver [NoSePudoLeer].
    val noSeLeyo = !loading && (credits == null || cards == null)
    // Lo que ya se puede pintar: las dos listas y el período. Mientras falte algo y la lectura siga
    // en vuelo, el esqueleto; una recarga con esto ya pintado NO vuelve al esqueleto (las listas no
    // vuelven a `null`), así que guardar un crédito no hace parpadear la pantalla.
    val creditosListos = credits.takeIf { perfilContestado }
    val tarjetasListas = cards.takeIf { perfilContestado }
    val cargando = !noSeLeyo && (creditosListos == null || tarjetasListas == null)
    // Las dos listas a la vista y vacías. NO es lo mismo que [isEmpty]: esto dice qué hay pintado,
    // aquello si se puede AFIRMAR que no hay deudas. Todo lo que presupone deudas —la tarjeta de
    // «Deuda total», el «Nuevo crédito» compacto— mira esto: con las listas vacías y una lectura
    // caída, «Deuda total $0» sería una cifra salida de una lectura que falló.
    val sinDeudas = creditosListos?.isEmpty() == true && tarjetasListas?.isEmpty() == true
    // Un vacío solo se afirma con préstamos y tarjetas que contestaron en ESTA visita: uno recordado
    // con la lectura de ahora caída sería el de otra visita (queda el aviso, con su «Reintentar»).
    // Una falla del perfil no cuenta: el perfil solo nombra el mes de la última cuota.
    val isEmpty = sinDeudas && !creditosLeidos.falloConAlgoALaVista && !tarjetasLeidas.falloConAlgoALaVista
    // Hay algo pintado que esta visita todavía no confirmó: «Actualizando…» en la cabecera.
    val actualizandoConAlgoALaVista = loading && creditosListos != null && tarjetasListas != null
    // El alta compacta. Mientras dice «Actualizando…» la cabecera no la lleva: las dos juntas no
    // entran a 390 dp sin cortar el título en «Cré…», y el alta vuelve en cuanto la lectura contesta.
    val nuevoEnLaCabecera = !sinDeudas && !noSeLeyo && !actualizandoConAlgoALaVista
    // El plan de cada préstamo (interés de la cuota, si amortiza, cuándo termina). Se calcula acá
    // una sola vez y baja a las tarjetas: la aritmética vive en `:core` para que el server y los
    // tres clientes vean el mismo número. Ver [PlanDelCredito].
    val planes = remember(credits) { credits.orEmpty().associate { it.account.id to planDelCredito(it, com.jvillada.movi.ui.fecha.hoyEnAppZone()) } }
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
    val reglaDelFinDeSemana = perfilLeido.valor?.periodWeekendRule
    val ajustesDelPeriodo = remember(cutoffDay, iniciosPropios, reglaDelFinDeSemana) {
        PeriodSettings(
            cutoffDay = cutoffDay.coerceIn(1, 31),
            iniciosPropios = iniciosPropios,
            siCaeEnFinDeSemana = siElCorteCaeEnFinDeSemanaDe(reglaDelFinDeSemana),
        )
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
    // Una columna: la lista de siempre. Dos: una sola página que scrollea entera (el resumen arriba
    // y las dos columnas debajo se leen juntos). La rueda del mouse sobre los márgenes mueve la que
    // está a la vista (ver [ScrollDesdeLosMargenes]).
    val estadoDeLaLista = rememberLazyListState()
    val scrollDeDosColumnas = rememberScrollState()
    val conDeudasParaRepartir = hayDeudasParaDosColumnas(
        cargando = cargando,
        sinDeudas = sinDeudas,
        noSeLeyo = noSeLeyo,
        cuantasDeudas = creditosListos.orEmpty().size + tarjetasListas.orEmpty().size,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // Ola W3: en pantalla ancha, Préstamos y Tarjetas lado a lado debajo del resumen. El ancho lo
        // mide el panel, ya sin el rail — ver [creditosEnDosColumnas] para la cuenta completa.
        PanelDeTablero(enDosColumnas = { creditosEnDosColumnas(it) && conDeudasParaRepartir }) { dosColumnas ->
        ScrollDesdeLosMargenes(if (dosColumnas) scrollDeDosColumnas else estadoDeLaLista)
        Column(
            modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)
        ) {
            // F60: encabezado único. Ola C: Créditos es el detalle de las deudas de Patrimonio,
            // así que lleva flecha y vuelve ahí (F22: reserva si no hay historial). Con deudas ya
            // creadas, el alta compacta a la derecha (F18).
            //
            // Ola B: el alta compacta está **desde el primer cuadro**, también mientras carga. Antes
            // aparecía cuando llegaban los créditos y el título se corría. Solo se va en los dos
            // casos que tienen su propia forma: la lectura que no se pudo hacer (no se invita a
            // duplicar) y el vacío de verdad (el botón ancho de abajo).
            MinScreenHeader(
                title = "Créditos",
                leading = leadingFor(Screen.Credits, onNavigate, fallback = Screen.Accounts),
                action = if (actualizandoConAlgoALaVista) {
                    { ActualizandoEnLaCabecera() }
                } else null,
                accion = if (nuevoEnLaCabecera) {
                    AccionDeEncabezado("Nuevo crédito", Icons.Rounded.Add, onClick = { showTypeChooser = true })
                } else null,
            )
            // Un solo aviso: sin nada que mostrar, el de «No pudimos cargar» de abajo ya lo dice todo.
            if (noSePudoActualizar && !noSeLeyo) {
                Spacer(Modifier.height(Espacio.s))
                NoSePudoActualizar(
                    onReintentar = { reloadKey++ },
                    modifier = Modifier.padding(horizontal = Espacio.m),
                )
            }
            if (noSeLeyo) {
                Spacer(Modifier.height(Espacio.s))
                NoSePudoLeer(
                    "No pudimos cargar tus créditos",
                    onReintentar = { reloadKey++ },
                    modifier = Modifier.padding(horizontal = Espacio.m),
                )
            } else if (isEmpty) {
                Boton(
                    texto = "Nuevo crédito",
                    onClick = { showTypeChooser = true },
                    modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(vertical = Espacio.s),
                    icono = Icons.Rounded.Add,
                    anchoCompleto = true,
                )
            } else {
                Spacer(Modifier.height(Espacio.s))
            }

            // Las piezas de la pantalla, una sola vez: la lista de una columna y la página de dos
            // columnas las ponen en lugares distintos, con el mismo contenido.
            val resumenDeDeuda: @Composable (List<CreditSummary>, List<CardSummary>) -> Unit = { creditosALaVista, tarjetasALaVista ->
                Superficie(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla).testTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA),
                ) {
                    Text("Deuda total", style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
                    Spacer(Modifier.height(Espacio.xs))
                    // F20: préstamos + tarjetas — la MISMA función que usa el Inicio.
                    // La protagonista de esta pantalla, igual que «Tu plata» en el Inicio: misma
                    // letra, mismo tamaño, y un renglón siempre. Ver [CifraProtagonista].
                    Cifra(totalDebtCop(creditosALaVista, tarjetasALaVista), VarianteDeCifra.Heroe)
                    // Vuelta 59: las deudas en negativo no restan arriba; se nombran acá, con «Cuadrar».
                    DeudasSinSaldo(creditosALaVista, tarjetasALaVista, onNavigate)
                    // Lo que esa deuda CUESTA, que es lo que la pantalla no decía. La deuda
                    // total de arriba cuenta todos los créditos —quién paga la cuota no cambia
                    // de quién es el pasivo—; el costo mensual de acá sí separa. Ver
                    // [saleDeTuBolsillo].
                    LoQueCuestaLaDeuda(
                        planes.values.filterNotNull(),
                        // Vuelta 59: el interés del mes con las tarjetas, y la última cuota contada
                        // desde la primera sin pagar de cada crédito.
                        tarjetasALaVista,
                        creditosALaVista.mapNotNull { c ->
                            val plan = planes[c.account.id] ?: return@mapNotNull null
                            mesDeLaPrimeraCuota(c, hoyEnAppZone())?.let { plan to it }
                        },
                        quienesPaganLoQueNoSaleDeTuBolsillo(creditosALaVista.mapNotNull { it.terms }),
                        onRenglonesDelAvisoAmbar = { renglonesDelAvisoAmbar = it },
                        onRenglonesDelAvisoRojo = { renglonesDelAvisoRojo = it },
                    )
                }
            }
            // Vuelta de diseño 4: cada deuda es una fila tocable. Abre su detalle —donde viven
            // «Editar», «Ajustar saldo» y lo demás—. Vuelta 31: también sin términos; el detalle dice
            // arriba que faltan datos y trae «Completar datos».
            val hoy = hoyEnAppZone()
            val filaDePrestamo: @Composable (CreditSummary) -> Unit = { c ->
                FilaDeDeuda(
                    nombre = c.account.name,
                    icono = Icons.Rounded.AccountBalance,
                    deuda = prestamoEnLaLista(c, planes[c.account.id], periodoActual, hoy),
                    saldo = { Cifra(c.account.balance) },
                    onAbrir = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                )
            }
            val filaDeTarjeta: @Composable (CardSummary) -> Unit = { c ->
                val moneda = c.account.currency
                FilaDeDeuda(
                    nombre = c.account.name,
                    icono = Icons.Rounded.CreditCard,
                    deuda = tarjetaEnLaLista(c, hoy),
                    // La deuda en SU moneda: una Mastercard en USD debe dólares, y su componente COP
                    // sería $0, un número que miente.
                    saldo = { MontoDeFila(formatMoney(c.account.balancesByCurrency[moneda] ?: c.account.balance, moneda)) },
                    onAbrir = { onNavigate(Screen.AccountDetail(c.account.id, c.account.type.group)) },
                )
            }

            if (cargando) {
                CreditosEsqueleto(forma = formaRecordada, dosColumnas = dosColumnas, modifier = Modifier.weight(1f))
            } else if (creditosListos != null && tarjetasListas != null && dosColumnas) {
                // ── Dos columnas (Ola W3) ─────────────────────────────────────────────────
                // Solo con deudas (ver [hayDeudasParaDosColumnas]): el resumen a lo ancho, y debajo
                // Préstamos a la izquierda y Tarjetas a la derecha. Con uno solo de los dos grupos,
                // sus tarjetas se reparten en las dos columnas bajo un solo título.
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollDeDosColumnas)
                        .padding(bottom = Medida.botonFlotante + Espacio.xl),
                ) {
                    resumenDeDeuda(creditosListos, tarjetasListas)
                    FilaComoSalirDeTusDeudas(onClick = { onNavigate(Screen.SalidaDeDeudas) })
                    Spacer(Modifier.height(Medida.entreSecciones))
                    val ambos = creditosListos.isNotEmpty() && tarjetasListas.isNotEmpty()
                    if (!ambos) {
                        Box(modifier = Modifier.padding(horizontal = Espacio.m)) {
                            if (creditosListos.isNotEmpty()) {
                                Seccion(titulo = "Préstamos", conteo = creditosListos.size)
                            } else {
                                Seccion(titulo = "Tarjetas", conteo = tarjetasListas.size)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m),
                        horizontalArrangement = Arrangement.spacedBy(Medida.margenDePantalla),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val izquierda = Modifier.weight(1f).testTag(TAG_COLUMNA_IZQUIERDA_DE_CREDITOS)
                        val derecha = Modifier.weight(1f).testTag(TAG_COLUMNA_DERECHA_DE_CREDITOS)
                        if (ambos) {
                            Column(modifier = izquierda) {
                                Seccion(titulo = "Préstamos", conteo = creditosListos.size)
                                GrupoDeDeudas(creditosListos, filaDePrestamo)
                            }
                            Column(modifier = derecha) {
                                Seccion(titulo = "Tarjetas", conteo = tarjetasListas.size)
                                GrupoDeDeudas(tarjetasListas, filaDeTarjeta)
                            }
                        } else if (creditosListos.isNotEmpty()) {
                            val (pares, impares) = repartirEnDos(creditosListos)
                            Column(modifier = izquierda) { GrupoDeDeudas(pares, filaDePrestamo) }
                            Column(modifier = derecha) { if (impares.isNotEmpty()) GrupoDeDeudas(impares, filaDePrestamo) }
                        } else {
                            val (pares, impares) = repartirEnDos(tarjetasListas)
                            Column(modifier = izquierda) { GrupoDeDeudas(pares, filaDeTarjeta) }
                            Column(modifier = derecha) { if (impares.isNotEmpty()) GrupoDeDeudas(impares, filaDeTarjeta) }
                        }
                    }
                }
            } else if (creditosListos != null && tarjetasListas != null) LazyColumn(state = estadoDeLaLista, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = Medida.botonFlotante + Espacio.xl)) {
                // Sin un solo crédito ni tarjeta no hay deuda que resumir — «Deuda total $0»
                // arriba del vacío que enseña presentaba un cero como si fuera un hecho. Con
                // cualquier crédito o tarjeta, esta tarjeta sigue apareciendo igual que siempre.
                if (!sinDeudas) {
                    item { resumenDeDeuda(creditosListos, tarjetasListas) }
                    item { FilaComoSalirDeTusDeudas(onClick = { onNavigate(Screen.SalidaDeDeudas) }) }
                }

                if (isEmpty) {
                    item {
                        Spacer(Modifier.height(Medida.entreSecciones))
                        // Reemplaza al «Sin créditos registrados» de siempre — el
                        // botón hace lo mismo que «Nuevo crédito» del encabezado (ambos ponen
                        // `showTypeChooser = true`, la misma hoja de elegir préstamo o tarjeta).
                        VacioQueEnsena(
                            titulo = "Aquí van tus créditos y tarjetas",
                            detalle = "Carga un préstamo o una tarjeta y Movi te dice cuánto pagas de intereses, " +
                                "cuándo terminas y si la cuota alcanza.",
                            accion = "Agregar un crédito",
                            onAccion = { showTypeChooser = true },
                            modifier = Modifier.padding(horizontal = Espacio.m),
                        )
                    }
                }

                if (creditosListos.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Medida.entreSecciones))
                        Column(modifier = Modifier.padding(horizontal = Espacio.m)) {
                            Seccion(titulo = "Préstamos", conteo = creditosListos.size)
                            GrupoDeDeudas(creditosListos, filaDePrestamo)
                        }
                    }
                }

                if (tarjetasListas.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Medida.entreSecciones))
                        Column(modifier = Modifier.padding(horizontal = Espacio.m)) {
                            Seccion(titulo = "Tarjetas", conteo = tarjetasListas.size)
                            GrupoDeDeudas(tarjetasListas, filaDeTarjeta)
                        }
                    }
                }
            }
        }
        } // PanelDeTablero
        if (showTypeChooser) {
            DebtTypeChooserSheet(
                onDismiss = { showTypeChooser = false },
                onLoan = { showTypeChooser = false; showLoanSheet = true },
                onCard = { showTypeChooser = false; showCardSheet = true },
            )
        }
        if (showLoanSheet) {
            CreditTermsSheet(
                editing = null,
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
                editing = null,
                onDismiss = { showCardSheet = false },
                onSaved = { showCardSheet = false; reloadKey++ },
            )
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
    tarjetas: List<CardSummary>,
    /** Cada plan con el mes de su primera cuota sin pagar: de ahí sale «Tu última cuota». */
    planesConSuPrimeraCuota: List<Pair<PlanDelCredito, PeriodoFinanciero>>,
    quienesPagan: List<String> = emptyList(),
    onRenglonesDelAvisoAmbar: (Int) -> Unit = {},
    onRenglonesDelAvisoRojo: (Int) -> Unit = {},
) {
    // Sin un solo crédito con plan no se pinta (como siempre: la forma recordada del esqueleto cuenta
    // con eso); con alguno, el interés del mes suma también las tarjetas.
    if (planes.isEmpty()) return
    val interes = interesDelMes(planes, tarjetas)
    val resumen = resumirDeudas(planes)
    Spacer(Modifier.height(Espacio.m))
    Separador(SangriaDeSeparador.Ninguna)
    Spacer(Modifier.height(Espacio.s))

    // **Las dos cifras se muestran, no se colapsan en una, y las dos son filas.** Quedarse solo con
    // el total le cobraría al bolsillo millones que no salen de su cuenta; quedarse solo con lo suyo
    // escondería que existen; y dejar la ajena como nota al pie hacía que un dueño con solo créditos
    // de Skandia leyera «Intereses este mes — $0» en la fila titular. Ver [saleDeTuBolsillo].
    // Vuelta 59: la cifra de `interesDelMes` (:core), la misma de «Cómo salir de tus deudas» y de
    // Movi AI: tus créditos y tus tarjetas. Con tarjetas que cobran, el rótulo lo dice.
    TituloDelGrupo(TITULO_INTERES_DEL_MES)
    FilaDelResumen(alcanceDelInteresPropio(interes), interes.propio)
    if (interes.ajeno > 0L) {
        Spacer(Modifier.height(Espacio.xxs))
        FilaDelResumen(alcanceDelInteresAjeno(quienesPagan), interes.ajeno)
    }

    if (resumen.interesPorPagarPropio > 0L || resumen.interesPorPagarAjeno > 0L) {
        Spacer(Modifier.height(Espacio.s))
        TituloDelGrupo(TITULO_INTERES_POR_PAGAR)
        if (resumen.interesPorPagarPropio > 0L) {
            FilaDelResumen(ALCANCE_FALTA_PROPIO, resumen.interesPorPagarPropio)
        }
        if (resumen.interesPorPagarAjeno > 0L) {
            Spacer(Modifier.height(Espacio.xxs))
            FilaDelResumen(alcanceDeLaFaltaAjena(quienesPagan), resumen.interesPorPagarAjeno)
        }
    }

    // La fecha final solo aparece cuando hay algo que decir: o una fecha, o el «Sin fecha» que el
    // aviso de abajo explica. Una cartera entera sin tasas registradas no tiene ninguna de las dos.
    if (resumen.mesesHastaLaUltimaCuota != null || resumen.creditosQueNoSeTerminan > 0) {
        Spacer(Modifier.height(Espacio.s))
        TituloDelGrupo(TITULO_ULTIMA_CUOTA)
        FechaDelResumen(ALCANCE_ULTIMA_CUOTA, textoDeLaUltimaCuota(ultimaCuotaDeTodas(planesConSuPrimeraCuota)))
    }

    // **Los dos avisos, y el primero es el que faltaba.** `creditosQueNoSeTerminan` se calculaba,
    // se probaba y no se dibujaba: la única advertencia visible contaba 1 (la amortización
    // negativa) y los $100.000.000 del Crédito Mamá no aparecían en ningún lado del resumen.
    if (resumen.creditosQueNoSeTerminan > 0) {
        Spacer(Modifier.height(Espacio.s))
        AvisoDeLaPantalla(
            texto = textoDeLoQueNoSeTermina(resumen.creditosQueNoSeTerminan, resumen.deudaQueNoSeTermina),
            color = Movi.colores.aviso,
            fondo = Movi.colores.lavado(Movi.colores.aviso),
            onRenglones = onRenglonesDelAvisoAmbar,
        )
    }
    // La alerta, arriba de todo y contada en créditos: si hay uno solo en el que la deuda crece
    // sola, el dueño tiene que salir de esta pantalla sabiéndolo.
    if (resumen.creditosQueCrecen > 0) {
        Spacer(Modifier.height(Espacio.xs))
        AvisoDeLaPantalla(
            texto = textoDeLaAmortizacionNegativa(resumen.creditosQueCrecen),
            color = Movi.colores.sale,
            fondo = Movi.colores.lavado(Movi.colores.sale),
            onRenglones = onRenglonesDelAvisoRojo,
        )
    }

    Spacer(Modifier.height(Espacio.s))
    Text(SUPUESTO_DE_LA_PROYECCION, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
}

/** El tag de la línea de las deudas sin saldo, debajo de «Deuda total». */
const val TAG_DEUDAS_SIN_SALDO: String = "creditos:deudas-sin-saldo"

/**
 * **Las deudas sin saldo, debajo de «Deuda total»** (vuelta 59): «1 crédito sin saldo · Cuadrar». No
 * restan arriba ([totalDebtCop]) y por eso se dicen acá, con la salida a la mano: «Cuadrar» abre Cuadre
 * de saldos —que desde esta vuelta lista los préstamos— con la deuda arriba si es una sola. Nada si no hay.
 */
@Composable
private fun DeudasSinSaldo(creditos: List<CreditSummary>, tarjetas: List<CardSummary>, onNavigate: (Screen) -> Unit) {
    val sinSaldo = deudasSinSaldoDe(creditos.map { it.account } + tarjetas.map { it.account })
    val texto = textoDeLasDeudasSinSaldo(
        creditos = sinSaldo.count { it.type == com.jvillada.movi.shared.model.AccountType.LOAN },
        tarjetas = sinSaldo.count { it.type != com.jvillada.movi.shared.model.AccountType.LOAN },
    ) ?: return
    Spacer(Modifier.height(Espacio.xs))
    // Topada al ancho de los renglones del resumen (vuelta 61): así «Cuadrar» y la cifra del interés
    // del mes quedan alineadas al mismo borde derecho, también a 1.280.
    Row(
        modifier = Modifier.widthIn(max = ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA).fillMaxWidth().testTag(TAG_DEUDAS_SIN_SALDO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Text(texto, style = Movi.textos.apoyoMedio, color = Movi.colores.aviso, modifier = Modifier.weight(1f))
        Boton(
            texto = CUADRAR_LAS_DEUDAS_SIN_SALDO,
            onClick = {
                onNavigate(sinSaldo.singleOrNull()?.let { Screen.CuadrarLaCuenta(it.id) } ?: Screen.CuadreDeSaldos)
            },
            variante = VarianteDeBoton.Fantasma,
            tamano = TamanoDeBoton.S,
        )
    }
}

/** El rótulo de un grupo de filas del resumen: qué se está midiendo, antes de con qué alcance. */
@Composable
private fun TituloDelGrupo(titulo: String) {
    Text(titulo, style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio, modifier = Modifier.testTag(TAG_TITULO_DEL_GRUPO_DEL_RESUMEN))
    Spacer(Modifier.height(Espacio.xs))
}

/** El rótulo de un grupo del resumen («Intereses este mes»), para medir que sus filas no van sangradas. */
const val TAG_TITULO_DEL_GRUPO_DEL_RESUMEN: String = "titulo-del-grupo-del-resumen-de-deuda"

/**
 * Una fila del resumen. [alcance] no es una decoración: es **de qué habla** la cifra de la derecha,
 * y va en el rótulo justamente para que no se pueda leer el número sin él. Vuelta de diseño 19: la
 * `CifraConRotulo` del sistema, en renglón (la cifra con su letra, su signo y «Ocultar los saldos»).
 *
 * Vuelta 61: **sin sangría** —iba 8 dp corrida a la derecha de su grupo, el único renglón de la app
 * así— y **topada** a [ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA], para que a 1.280 el rótulo y la cifra no
 * queden a lados opuestos de la tarjeta.
 */
@Composable
private fun FilaDelResumen(alcance: String, monto: Long) {
    CifraConRotulo(
        alcance,
        monto,
        Modifier.widthIn(max = ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA),
        disposicion = DisposicionDeCifraConRotulo.EnRenglon,
    )
}

/** [FilaDelResumen] con una fecha («Marzo 2031») en lugar de una cifra: el mismo renglón. */
@Composable
private fun FechaDelResumen(alcance: String, fecha: String) {
    RotuloConValor(
        alcance,
        Modifier.widthIn(max = ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA),
        disposicion = DisposicionDeCifraConRotulo.EnRenglon,
    ) { MontoDeFila(fecha) }
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
            .clip(RoundedCornerShape(Radio.m))
            .background(fondo)
            .padding(horizontal = RELLENO_DEL_AVISO_H, vertical = RELLENO_DEL_AVISO_V),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyoMedio,
            color = color,
            lineHeight = INTERLINEADO_DEL_AVISO,
            // Cuántos renglones ocupó, para que la próxima carga reserve ese alto (ver
            // `FormaRecordada`).
            onTextLayout = { onRenglones(it.lineCount) },
        )
    }
}

/** Los rellenos y el interlineado de [AvisoDeLaPantalla], compartidos con [AvisoEsqueleto]. */
private val RELLENO_DEL_AVISO_H = Espacio.s
private val RELLENO_DEL_AVISO_V = Espacio.xs
private val INTERLINEADO_DEL_AVISO = 16.sp

/** El tag de la fila de una deuda en la lista de Créditos (con lo que cuelga de ella). */
const val TAG_FILA_DE_DEUDA: String = "fila-de-deuda"

/**
 * **Las deudas de un grupo, en una lista**: una [Superficie] con relleno de lista y una fila por
 * deuda, separadas por un hilo — la misma forma que los grupos de cuentas de Patrimonio.
 */
@Composable
private fun <T> GrupoDeDeudas(deudas: List<T>, fila: @Composable (T) -> Unit) {
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        deudas.forEachIndexed { i, deuda ->
            if (i > 0) Separador()
            fila(deuda)
        }
    }
}

/**
 * **Una deuda en la lista de Créditos** (vuelta de diseño 4): una [Fila] tocable —ícono, nombre, el
 * próximo pago y el saldo— y, debajo, solo lo que hace falta: la barra cuando es verdadera, o la
 * nota que la barra no puede decir (la deuda que crece, el desembolso que falta). Sin botones: tocarla
 * abre su detalle, donde están «Editar» y «Ajustar saldo». **Siempre** el detalle (vuelta 31): sin
 * términos abría directo el formulario, y una deuda sin datos era la única que no se podía consultar
 * —saldo, movimientos—; ahora el detalle dice arriba que faltan y ofrece «Completar datos».
 *
 * Antes era una tarjeta con «Ajustar saldo» y «Editar» adentro, un «0% pagado» y una barra, y una
 * deuda sin términos decía «Sin términos registrados · $0 · 0% pagado».
 */
@Composable
private fun FilaDeDeuda(
    nombre: String,
    icono: ImageVector,
    deuda: DeudaEnLaLista,
    saldo: @Composable () -> Unit,
    onAbrir: () -> Unit,
) {
    Column(modifier = Modifier.testTag(TAG_FILA_DE_DEUDA)) {
        Fila(
            titulo = nombre,
            subtitulo = deuda.subtitulo,
            // El subtítulo es la razón de la fila —el próximo pago, o que faltan datos— y no se
            // corta: hasta dos renglones (a 360 dp, «Cuota $1.204.064 · 15 de noviembre» al lado de
            // un saldo de ocho cifras no entra en uno). «Faltan datos» va en el tono de aviso.
            tonoDelSubtitulo = if (deuda.faltanDatos) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
            lineasDelSubtitulo = 2,
            inicio = { IconoEnCirculo(icono, tono = TonoDeIcono.Neutro) },
            // Vuelta 59: una deuda en negativo no pinta su cifra con el signo al revés.
            final = {
                when {
                    deuda.pagada -> Pildora(DEUDA_PAGADA, TonoDePildora.Exito)
                    deuda.sinSaldo -> Pildora(SIN_SALDO_EN_MOVI, TonoDePildora.Aviso)
                    else -> saldo()
                }
            },
            onClick = onAbrir,
            onClickLabel = "Ver la deuda",
        )
        val nota = deuda.nota
        val fraccion = deuda.fraccionPagada
        if (nota != null || fraccion != null) {
            BajoLaFila {
                if (nota != null) {
                    Text(
                        nota.texto,
                        style = if (nota.esAlerta) Movi.textos.apoyoMedio else Movi.textos.apoyo,
                        color = if (nota.esAlerta) Movi.colores.sale else Movi.colores.aviso,
                    )
                }
                if (fraccion != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) {
                        Box(Modifier.weight(1f).testTag(TAG_BARRA_DE_PROGRESO)) {
                            BarraDeProgreso(fraccion = fraccion, tono = TonoDeBarra.Neutro)
                        }
                        Text(deuda.avance.orEmpty(), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Selector previo al alta (F20): un préstamo y una tarjeta no se crean igual — el préstamo
 * tiene capital, tasa y plazo; la tarjeta, cupo, corte y día de pago. Preguntar primero evita
 * una hoja única llena de campos que no aplican.
 */
/** El título de la hoja que pregunta qué deuda se va a registrar. */
const val TITULO_DEL_ELECTOR_DE_DEUDA: String = "¿Qué deuda quieres registrar?"

@Composable
internal fun DebtTypeChooserSheet(
    onDismiss: () -> Unit,
    onLoan: () -> Unit,
    onCard: () -> Unit,
) {
    MarcoDeHoja(onDismiss = onDismiss) {
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
            // Vuelta 61: la pregunta ES el título de la hoja (iba en `tituloMedio`, chica).
            EncabezadoDeHoja(TITULO_DEL_ELECTOR_DE_DEUDA)
            Spacer(Modifier.height(Medida.entreSecciones))
            DebtTypeOption(
                title = "Préstamo",
                subtitle = "Cuota fija, tasa y plazo — libranza, libre inversión, vehículo",
                onClick = onLoan,
            )
            Spacer(Modifier.height(Espacio.xs))
            DebtTypeOption(
                title = "Tarjeta de crédito",
                subtitle = "Cupo, día de corte y día de pago",
                onClick = onCard,
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

@Composable
private fun DebtTypeOption(title: String, subtitle: String, onClick: () -> Unit) {
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(titulo = title, subtitulo = subtitle, final = { Chevron() }, onClick = onClick)
    }
}

/** La tarjeta de «Deuda total», cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_RESUMEN_DE_DEUDA: String = "tarjeta-del-resumen-de-deuda"

/** La cifra esqueleto de la tarjeta de «Deuda total» — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA: String = "esqueleto-del-resumen-de-deuda"

/** Cada fila de préstamo que todavía no llegó. */
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
 * [FilaDeDeuda]) para que la tarjeta de arriba mida lo mismo cargando que cargada (±8 dp, lo mide
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
private fun CreditosEsqueleto(forma: FormaDeCreditos?, dosColumnas: Boolean = false, modifier: Modifier = Modifier) {
    val grupos = forma?.gruposDelResumen ?: listOf(1, 1, 1)
    val prestamos = (forma?.prestamos ?: 3).coerceAtMost(MAX_PRESTAMOS_ESQUELETO)
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = Medida.botonFlotante + Espacio.xl), userScrollEnabled = false) {
        item {
            Superficie(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Medida.margenDePantalla)
                    .testTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA),
            ) {
                LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
                Spacer(Modifier.height(Espacio.xs))
                LineaEsqueleto(
                    fraccionDelAncho = 0.7f,
                    estilo = Movi.textos.cifra,
                    modifier = Modifier.testTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA),
                )
                // Sin grupos, [LoQueCuestaLaDeuda] no dibuja nada: ni el hilo, ni los avisos, ni el
                // supuesto.
                if (grupos.isNotEmpty()) {
                    // Los mismos espacios que [LoQueCuestaLaDeuda]: 16 · hilo · 14 y 12 entre grupos.
                    Spacer(Modifier.height(Espacio.m))
                    Separador(SangriaDeSeparador.Ninguna)
                    Spacer(Modifier.height(Espacio.s))
                    grupos.forEachIndexed { i, filas ->
                        if (i > 0) Spacer(Modifier.height(Espacio.s))
                        // [TituloDelGrupo] y [FilaDelResumen]: el rótulo, 6 dp, y cada fila con 10 dp
                        // de sangría y 4 dp entre una y otra.
                        LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.apoyo)
                        Spacer(Modifier.height(Espacio.xs))
                        repeat(filas) { f ->
                            if (f > 0) Spacer(Modifier.height(Espacio.xxs))
                            RenglonConCifraEsqueleto(
                                modifier = Modifier.padding(start = Espacio.xs),
                                fraccionDelRotulo = 0.55f,
                            )
                        }
                    }
                    // Los avisos, con los espacios de [LoQueCuestaLaDeuda]: 12 antes del ámbar, 8
                    // antes del rojo.
                    val ambar = forma?.renglonesDelAvisoAmbar ?: 0
                    if (ambar > 0) {
                        Spacer(Modifier.height(Espacio.s))
                        AvisoEsqueleto(ambar)
                    }
                    val rojo = forma?.renglonesDelAvisoRojo ?: 0
                    if (rojo > 0) {
                        Spacer(Modifier.height(Espacio.xs))
                        AvisoEsqueleto(rojo)
                    }
                    Spacer(Modifier.height(Espacio.s))
                    // [SUPUESTO_DE_LA_PROYECCION]: dos renglones de apoyo a 15 sp de interlineado en
                    // un teléfono.
                    LineaEsqueleto(fraccionDelAncho = 0.95f, estilo = Movi.textos.apoyo)
                    LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
                }
            }
        }
        if (prestamos > 0) {
            item {
                Spacer(Modifier.height(Medida.entreSecciones))
                // En dos columnas (Ola W3) los préstamos caen a la izquierda, donde van a quedar: la
                // derecha es de las tarjetas, que la forma recordada no cuenta.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m),
                    horizontalArrangement = Arrangement.spacedBy(Medida.margenDePantalla),
                ) {
                    Column(modifier = if (dosColumnas) Modifier.weight(1f).testTag(TAG_COLUMNA_IZQUIERDA_DE_CREDITOS) else Modifier.weight(1f)) {
                        RotuloDeSeccionEsqueleto()
                        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                            repeat(prestamos) { i ->
                                if (i > 0) Separador()
                                EsqueletoDeFila(modifier = Modifier.testTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
                            }
                        }
                    }
                    if (dosColumnas) Spacer(Modifier.weight(1f))
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

/** El tag de la fila «Cómo salir de tus deudas», para encontrarla sin depender de su texto. */
const val TAG_FILA_COMO_SALIR_DE_TUS_DEUDAS: String = "fila-como-salir-de-tus-deudas"

/**
 * Ola 4: **la puerta al plan de salida** ([Screen.SalidaDeDeudas]), debajo de «Deuda total». Un
 * enlace fijo, sin cifra propia: lo que calcula vive en su pantalla.
 */
@Composable
private fun FilaComoSalirDeTusDeudas(onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        // Vuelta 42: la fila de navegación del sistema (el círculo neutro, no el amarillo).
        FilaDeNavegacion(
            titulo = TITULO_SALIDA_DE_DEUDAS,
            icono = Icons.AutoMirrored.Rounded.TrendingDown,
            onClick = onClick,
            subtitulo = "En qué orden abonar y cuánto interés te ahorras",
            modifier = Modifier.testTag(TAG_FILA_COMO_SALIR_DE_TUS_DEUDAS),
        )
    }
}
