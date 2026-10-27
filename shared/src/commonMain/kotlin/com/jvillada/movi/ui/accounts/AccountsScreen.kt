package com.jvillada.movi.ui.accounts

import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.FormaDeCuentas
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.ClaseDeBien
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.deudaDelBien
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ActualizandoEnLaCabecera
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoActualizar
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.RenglonConCifraEsqueleto
import com.jvillada.movi.ui.components.RotuloDeSeccionEsqueleto
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.components.SubirAlRetocarLaPestana
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.ui.components.signedMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.components.valorEnPesos
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.credits.totalDebtCop
import com.jvillada.movi.ui.cuadre.cuentasSinCuadrar
import com.jvillada.movi.ui.cuadre.textoDelAvisoDeCuadre
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.transactions.CHIP_ENTRE_CUENTAS
import kotlinx.datetime.Clock

/**
 * # Patrimonio — «¿cuánto tengo y cuánto debo?»
 *
 * Ola R4: la pantalla pasa al sistema y queda dueña de UNA pregunta. De arriba abajo:
 *
 * 1. **El patrimonio neto**, con su curva y la resta escrita (tu plata, lo apartado, lo condicionado,
 *    los bienes y las deudas), en una [Superficie]. La cifra es la [Cifra] héroe de la pantalla.
 * 2. **Las cuentas por grupo** —Dinero, Inversión, Bienes y Deudas—, cada una con su [Seccion] (en
 *    frase, nunca en mayúsculas) y sus [Fila]s con la marca del banco. Los subtotales que llevaban los
 *    rótulos («DINERO · 5 · $20,9M») se fueron: repetían los renglones de la tarjeta de arriba con
 *    otra suma al lado. Un grupo sin cuentas no se muestra.
 * 3. **Herramientas**: «Cuadre de saldos» y «Movimientos entre cuentas», dos filas.
 *
 * «Personas y comercios» salió de acá: lo que se les manda a otros no es tuyo (vive en Espacios y en
 * Ajustes).
 */
@Composable
fun AccountsScreen(onNavigate: (Screen) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    // **Lo último que se vio, al primer cuadro** (ver [rememberLectura]): las cuentas, las deudas y
    // las cuentas de otros arrancan con lo que dejó la visita anterior y se releen igual. Se releen
    // con `refreshKey` (el «Reintentar» de esta pantalla, o que algo se guardó desde ella) y con
    // `LocalRefreshTick` (se guardó algo desde la hoja de Agregar, que es una modal: esta pantalla
    // nunca sale de la composición).
    val cuentasLeidas = rememberLectura(ClaveDeLectura.Cuentas, reintento = refreshKey) { Repositories.wallets.getAccounts() }
    // `null` = la lectura todavía no contestó bien (ni en esta visita ni en una reciente);
    // `emptyList()` = contestó y no hay ninguna. Una vez leída no vuelve a `null`: una recarga que
    // falla sigue mostrando lo último que se supo, y lo dice (ver `noSePudoActualizar`).
    val accounts = cuentasLeidas.valor
    // `true` desde el primer cuadro: la lectura arranca en ese mismo cuadro, y con `false` el
    // primer cuadro caía en «no se pudo leer» antes de que la lectura empezara.
    val loading = cuentasLeidas.actualizando
    var showCreateSheet by remember { mutableStateOf(false) }
    // La hoja de un bien abierta: `existente = null` es uno nuevo (desde «Nueva cuenta» → «Bien»).
    var bienAbierto by remember { mutableStateOf<BienAbierto?>(null) }
    // El vacío que enseña es una afirmación sobre la plata del dueño, así que solo se hace cuando
    // una lectura DE VERDAD contestó y contestó vacío (`accounts` no nulo y vacío). Antes bastaba
    // una lectura fallida: el snackbar de error se autodescartaba y abajo quedaba el estado vacío
    // invitando a «crear tu primera cuenta» a alguien que ya tiene tres. Mismo criterio que
    // `accountsLoaded` en la hoja de Agregar.

    val snackbarHostState = remember { SnackbarHostState() }
    // La forma de la última carga que salió bien (ver `FormaRecordada`): el esqueleto la copia.
    // Se lee una vez, al montar.
    val formaRecordada = remember { FormaRecordada.delAparato.cuentas(SessionManager.userId) }

    // Cada lista de cuentas que llega (leída o recordada) deja su forma para el próximo esqueleto.
    LaunchedEffect(accounts) {
        val leidas = accounts ?: return@LaunchedEffect
        FormaRecordada.delAparato.guardarCuentas(SessionManager.userId, formaDeCuentas(leidas))
    }
    // El snackbar de siempre, solo sin nada a la vista: con cuentas pintadas lo dice
    // [NoSePudoActualizar] arriba de la lista, que no se va solo como el snackbar.
    LaunchedEffect(cuentasLeidas.error) {
        val e = cuentasLeidas.error ?: return@LaunchedEffect
        if (accounts == null) error = e.toUserMessage()
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    // ── «Deudas» (Ola C, tarea 4): el mismo total y el mismo conteo que el encabezado de
    // Créditos, leídos acá para no navegar hasta Créditos solo para saber si hay algo que ver.
    // Lectura propia —`null` = no contestó, no vacía por default— e independiente de `accounts`:
    // esta tarjeta tiene que poder aparecer aunque Cuentas siga cargando o se haya rendido.
    val creditosLeidos = rememberLectura(ClaveDeLectura.Creditos, reintento = refreshKey) { Repositories.wallets.getCredits() }
    val tarjetasLeidas = rememberLectura(ClaveDeLectura.Tarjetas, reintento = refreshKey) { Repositories.wallets.getCards() }
    // Un «Sin deudas registradas» recordado no se afirma si la lectura de esta visita falló: se
    // trata como no leído (el «No pudimos cargar» de la sección). Con deudas a la vista se quedan.
    val creditos = creditosLeidos.valor.takeUnless { creditosLeidos.fallo && it.isNullOrEmpty() }
    val tarjetasDeCredito = tarjetasLeidas.valor.takeUnless { tarjetasLeidas.fallo && it.isNullOrEmpty() }
    val cargandoDeudas = creditosLeidos.actualizando || tarjetasLeidas.actualizando

    // Ola R4: «Personas y comercios» salió de Patrimonio (quedan dos puertas: Espacios y Ajustes).
    // Patrimonio contesta «¿qué tengo yo y qué debo?»; lo que se les mandó a otros no es tuyo.

    // ── Lo apartado: sale de «Tu plata» y tiene su renglón. Secundario, fuera de `lecturas`: si
    // falla, la tarjeta se pinta como si no hubiera ninguno, sin avisar ni romper la pantalla.
    val apartadosLeidos = rememberLectura(ClaveDeLectura.Apartados, reintento = refreshKey) { Repositories.wallets.getApartados() }
    val apartados = apartadosLeidos.valor.orEmpty()

    // ── Ola 4 · Ver la plata: el patrimonio en el tiempo, debajo de la cifra. Secundaria, como los
    // apartados: si falla no se dice nada y la tarjeta queda como siempre, sin curva.
    val historiaLeida = rememberLectura(ClaveDeLectura.HistoriaDelPatrimonio, reintento = refreshKey) {
        Repositories.wallets.getHistoriaDelPatrimonio()
    }

    val lecturas = listOf(cuentasLeidas, creditosLeidos, tarjetasLeidas)
    // Se está pintando algo que esta visita todavía no confirmó (lo recordado, o lo de antes de un
    // «Reintentar»): lo dice «Actualizando…» en la cabecera, como el Inicio.
    val actualizandoConAlgoALaVista = lecturas.any { it.actualizando && it.valor != null }
    // Alguna lectura falló con lo de antes a la vista: se dice una vez, arriba, hasta que contesten.
    val noSePudoActualizar = lecturas.any { it.falloConAlgoALaVista }

    // La lista de las cuentas (la única, en una columna; la de la derecha, en dos): la rueda del
    // mouse sobre los márgenes la mueve (ver [ScrollDesdeLosMargenes]).
    val estadoDeLaLista = rememberLazyListState()
    val estadoDelResumen = rememberLazyListState()
    ScrollDesdeLosMargenes(estadoDeLaLista)
    // Re-tocar «Patrimonio» estando acá vuelve arriba, las dos columnas (ver [SubirAlRetocarLaPestana]).
    SubirAlRetocarLaPestana(estadoDeLaLista, estadoDelResumen)

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // Ola W3: en pantalla ancha, el resumen a la izquierda y las cuentas a la derecha. El ancho
        // lo mide el panel, ya sin el rail — ver [patrimonioEnDosColumnas] para la cuenta completa.
        PanelDeTablero(enDosColumnas = { patrimonioEnDosColumnas(it) && hayCuentasParaLaDerecha(accounts, loading) }) { dosColumnas ->
        Column(modifier = Modifier.fillMaxSize()) {
            // La raíz de la pestaña Patrimonio: avatar y el MISMO rótulo que la pestaña. «Cuadrar» no
            // va acá: es la fila «Cuadre de saldos» de Herramientas.
            MinScreenHeader(
                title = "Patrimonio",
                leading = HeaderLeading.Avatar(onNavigate),
                // Mientras dice «Actualizando…» no lleva «Nueva cuenta»: las dos juntas no entran a
                // 390 dp sin cortar el título en «Patr…». El botón vuelve apenas la lectura contesta.
                action = if (actualizandoConAlgoALaVista) {
                    { ActualizandoEnLaCabecera() }
                } else null,
                accion = if (actualizandoConAlgoALaVista) null else {
                    AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, onClick = { showCreateSheet = true })
                },
            )

            if (noSePudoActualizar) {
                NoSePudoActualizar(
                    onReintentar = { refreshKey++ },
                    modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
                )
            }

            // Lo que pinta cada lista: todo, en una columna; el resumen o las cuentas, en dos. El
            // orden dentro de cada parte es el de una columna, así que el teléfono no cambia. Cada
            // `item` es una sección: entre ellas va `Medida.entreSecciones` (el `spacedBy` de la lista).
            fun LazyListScope.itemsDePatrimonio(parte: ParteDePatrimonio) {
                val conResumen = parte != ParteDePatrimonio.Cuentas
                val conCuentas = parte != ParteDePatrimonio.Resumen
                val cuentas = accounts
                if (cuentas == null && loading) {
                    // La forma REAL de la pantalla, no filas sueltas (ver [cuentasEsqueleto]).
                    cuentasEsqueleto(formaRecordada, conTarjeta = conResumen, conGrupos = conCuentas)
                } else if (cuentas == null) {
                    // No se pudo leer y no hay nada que mostrar: se dice eso, y nada más. «Crear
                    // primera cuenta» sin saber si ya existe es como se fabrican los duplicados.
                    if (conResumen) item {
                        NoSePudoLeer("No pudimos cargar tus cuentas", onReintentar = { refreshKey++ })
                    }
                } else if (cuentas.isEmpty() && cuentasLeidas.falloConAlgoALaVista) {
                    // Lo recordado era «ninguna cuenta» y la lectura de esta visita falló: ese vacío
                    // es de otra visita y no se afirma. Queda el aviso de arriba con «Reintentar».
                } else if (cuentas.isEmpty()) {
                    // Sin una sola cuenta, ni un bien ni una deuda no hay patrimonio que mostrar: el
                    // vacío que enseña, con la misma hoja de «Nueva cuenta». Herramientas y Deudas,
                    // debajo, siguen apareciendo: cada una enseña o navega por su cuenta.
                    if (conResumen) item {
                        Vacio(
                            titulo = "Aquí vive lo que tienes y lo que debes",
                            detalle = "Tus cuentas de banco, tus inversiones, tu casa o tu carro, y tus créditos. " +
                                "Empieza por la cuenta donde te llega el sueldo.",
                            accion = "Crear mi primera cuenta",
                            onAccion = { showCreateSheet = true },
                        )
                    }
                } else {
                    if (conResumen) item {
                        TarjetaDelPatrimonio(
                            // **La MISMA función que el resto de la app** (`patrimonioDe` por debajo),
                            // nunca una suma propia: dos pantallas calculando la misma regla distinto
                            // es el error que este proyecto ya cometió dos veces.
                            balance = heroBalance(cuentas, apartados),
                            historia = historiaLeida.valor,
                            historiaEnCamino = historiaLeida.actualizando,
                        )
                    }
                    if (conCuentas) {
                        // F61: Dinero e Inversión según `AccountGroup`; los bienes aparte. Un grupo
                        // vacío no se muestra (ola R4: una sección sin contenido no se pinta).
                        val grupos = listOf(
                            "Dinero" to cuentasDeDinero(cuentas),
                            "Inversión" to cuentasDeInversion(cuentas),
                        )
                        grupos.filter { it.second.isNotEmpty() }.forEach { (titulo, delGrupo) ->
                            item(key = "grupo-$titulo") { GrupoDeCuentas(titulo, delGrupo, onNavigate) }
                        }
                        val bienes = bienesDe(cuentas)
                        if (bienes.isNotEmpty()) item(key = "bienes") {
                            SeccionDeBienes(
                                bienes = bienes,
                                cuentas = cuentas,
                                onAbrir = { bienAbierto = BienAbierto(existente = it) },
                            )
                        }
                    }
                }

                // ── «Deudas» (Ola C, tarea 4): lectura propia, así que aparece (o su esqueleto, o su
                // error) sin importar en qué estado esté `cuentas` arriba. Vuelta de diseño 9: en dos
                // columnas va con las cuentas, debajo de los bienes (el orden de una columna): lo que
                // tienes y lo que debes, juntos; el resumen queda con la cifra y las herramientas.
                if (conCuentas) item(key = "deudas") {
                    SeccionDeDeudas(
                        creditos = creditos,
                        tarjetas = tarjetasDeCredito,
                        cargando = cargandoDeudas,
                        onReintentar = { refreshKey++ },
                        onClick = { onNavigate(Screen.Credits) },
                    )
                }

                // ── Herramientas: el cuadre de saldos y la plata que se movió entre cuentas. Solo
                // necesitan que `cuentas` haya contestado, vacía o no.
                if (conResumen && cuentas != null) item(key = "herramientas") {
                    val ahora = remember(cuentas) { Clock.System.now().toEpochMilliseconds() }
                    Herramientas(
                        atrasadas = textoDelAvisoDeCuadre(cuentasSinCuadrar(cuentas, ahora)),
                        onCuadre = { onNavigate(Screen.CuadreDeSaldos) },
                        onEntreCuentas = { onNavigate(Screen.Transactions(CHIP_ENTRE_CUENTAS)) },
                    )
                }
            }

            // Lo que queda libre abajo: el botón flotante de Agregar más el aire de una sección.
            val alPie = Medida.botonFlotante + Medida.entreSecciones
            if (dosColumnas) {
                // Cada columna con su propio scroll: el resumen es corto y queda a la vista mientras
                // se recorren las cuentas. Los rellenos suman los 48 de [RELLENO_DE_DOS_COLUMNAS]:
                // 16 afuera de cada una y 8 + 8 entre las dos.
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = estadoDelResumen,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO),
                        contentPadding = PaddingValues(start = Medida.margenDePantalla, end = Medida.margenDePantalla / 2, top = Medida.dentroDeSeccion, bottom = alPie),
                        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                    ) { itemsDePatrimonio(ParteDePatrimonio.Resumen) }
                    LazyColumn(
                        state = estadoDeLaLista,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LAS_CUENTAS),
                        contentPadding = PaddingValues(start = Medida.margenDePantalla / 2, end = Medida.margenDePantalla, top = Medida.dentroDeSeccion, bottom = alPie),
                        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                    ) { itemsDePatrimonio(ParteDePatrimonio.Cuentas) }
                }
            } else {
                LazyColumn(
                    state = estadoDeLaLista,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Medida.margenDePantalla,
                        end = Medida.margenDePantalla,
                        top = Medida.dentroDeSeccion,
                        bottom = alPie,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                ) { itemsDePatrimonio(ParteDePatrimonio.Todo) }
            }
        }
        } // PanelDeTablero

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Medida.margenDePantalla),
        )

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = {
                    showCreateSheet = false
                    refreshKey++
                },
                onElegirBien = { nombre ->
                    showCreateSheet = false
                    bienAbierto = BienAbierto(existente = null, nombre = nombre)
                },
            )
        }

        bienAbierto?.let { abierto ->
            BienSheet(
                existente = abierto.existente,
                nombreInicial = abierto.nombre,
                cuentas = accounts.orEmpty(),
                onDismiss = { bienAbierto = null },
                onGuardado = {
                    bienAbierto = null
                    refreshKey++
                },
            )
        }
    }
}

/**
 * **Lo que dice el renglón de una cuenta en la lista**: su saldo escrito en LA MONEDA DE LA
 * CUENTA, y si esa cifra está en contra del dueño.
 *
 * Iba `formatCOP(account.balance)` a secas, y `balance` es solo el componente en PESOS de la
 * cuenta (lo deriva `enrichWith` en el server: `balances["COP"] ?: 0`). Una inversión con
 * US$10.000 y ni un peso salía como «$0» debajo de un subtotal de sección que decía
 * ≈$40.000.000 — el renglón contradecía al encabezado que tenía dos dedos más arriba, y el
 * número que mostraba no era chico: era falso. Lo mismo le pasaba a una cuenta en pesos con
 * cualquier movimiento en dólares.
 *
 * Es el MISMO arreglo que ya tenían el desglose del hero del Inicio ([cuentasDelHero]) y el
 * selector de «¿de dónde sale la plata?» (`saldoDeLaCuenta` en la hoja de Agregar), y usa la
 * misma función que ellos —[saldoEnSuMoneda]— en vez de una tercera copia del criterio. Para
 * una cuenta en pesos el texto es carácter por carácter el de antes (`signedMoney(x, "COP")` y
 * `formatCOP(x)` producen lo mismo).
 *
 * El subtotal del grupo sigue en pesos con [valorEnPesos] y eso no es un descuido: un subtotal
 * de varias cuentas solo se puede decir en UNA moneda, así que ahí la TRM es inevitable. Las
 * dos convenciones conviven a propósito y están explicadas en [saldoEnSuMoneda].
 *
 * Esta lista no muestra deudas —viven en Créditos—, así que acá no hace falta invertirle el
 * signo a nada (para eso está `saldoDeDeuda`): negativo es negativo, plata que no está.
 *
 * @property texto el saldo ya escrito, con su signo y su símbolo de moneda.
 * @property enContra la cifra está en contra del dueño, así que no se pinta de verde.
 */
data class SaldoDeLaFila(val texto: String, val enContra: Boolean)

/** Ver [SaldoDeLaFila]. */
fun saldoDeLaFila(account: Account): SaldoDeLaFila {
    val (monto, moneda) = saldoEnSuMoneda(account)
    return SaldoDeLaFila(texto = signedMoney(monto, moneda), enContra = monto < 0)
}

/**
 * **¿Esta cuenta es plata con destino?** La AFC (vivienda), el ahorro de Nu, la pensión voluntaria:
 * son suyas y cuentan en el patrimonio, pero no son «Tu plata». En Cuentas se veían igual que una
 * cuenta de ahorros cualquiera —mismo verde, mismo «Dinero»—, y el encabezado «DINERO · 5 ·
 * $20.556.753» contradecía al «Tu plata $558.350» de arriba sin decir por qué. Misma regla que
 * `patrimonioDe`: condición no vacía, y un bien nunca (tiene su propia sección).
 */
fun esDeUsoCondicionado(account: Account): Boolean =
    !account.esBien && !account.condicionadaA.isNullOrBlank()

/**
 * El tipo de una cuenta como lo dice el banco: «Ahorros», «Corriente», «Efectivo». `null` para una
 * inversión: en su sección («Inversión · 2») decir «Inversión» otra vez no agrega nada.
 */
fun tipoLegibleDeLaCuenta(type: AccountType): String? = when (type) {
    AccountType.SAVINGS -> "Ahorros"
    AccountType.CHECKING -> "Corriente"
    AccountType.CASH -> "Efectivo"
    AccountType.CREDIT_CARD -> "Tarjeta"
    AccountType.LOAN -> "Préstamo"
    AccountType.INVESTMENT -> null
}

/** Sin tildes ni mayúsculas, para ver si el nombre ya dice el tipo («Bancolombia Ahorros»). */
private fun palabrasSinTildes(texto: String): List<String> =
    texto.lowercase()
        .map { c -> when (c) { 'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; else -> c } }
        .joinToString("")
        .split(Regex("[^a-z0-9ñ]+"))
        .filter { it.isNotEmpty() }

/**
 * **El renglón de abajo de cada cuenta**: algo que el título no dice.
 *
 * - Si es plata con destino, para qué es, con las palabras que puso el dueño («Uso condicionado ·
 *   vivienda»). Mismo rótulo que el tramo de la tarjeta de patrimonio del Inicio.
 * - Si no, su tipo («Ahorros», «Corriente», «Efectivo»), **salvo que el nombre ya lo diga**
 *   («Bancolombia Ahorros») o que sea una inversión: entonces nada (`null`) y la fila va en un
 *   renglón.
 *
 * Vuelta de diseño 2: antes decía el grupo («Dinero») dentro de la sección «Dinero · 2», y cada fila
 * repetía el título de la sección. Los últimos cuatro de la cuenta no se agregan: Movi no guarda el
 * número aparte del nombre, y cuando el dueño lo tiene es porque lo escribió en el nombre
 * («Master Black 3684»), que ya se lee arriba.
 */
fun subtituloDeLaCuenta(account: Account): String? {
    if (esDeUsoCondicionado(account)) return "Uso condicionado · ${account.condicionadaA!!.trim()}"
    val tipo = tipoLegibleDeLaCuenta(account.type) ?: return null
    return tipo.takeIf { palabrasSinTildes(tipo).first() !in palabrasSinTildes(account.name) }
}

/** Qué bien tiene abierta la hoja: uno que existe, o uno nuevo con el nombre ya escrito. */
private data class BienAbierto(val existente: Account?, val nombre: String = "")

/** El rótulo de la tarjeta del patrimonio: en frase, como todo rótulo (ola R4). */
const val ROTULO_DEL_PATRIMONIO_NETO: String = "Patrimonio neto"

/**
 * **La tarjeta del patrimonio neto**: el rótulo, la [Cifra] héroe, la curva de los últimos meses y
 * la resta escrita debajo —tu plata + lo apartado + lo condicionado + los bienes − las deudas = la
 * cifra de arriba—. Sin esos renglones el lector no tenía forma de cerrar la resta.
 *
 * **Neutra en los dos signos** (Ola 9): un patrimonio negativo por una hipoteca es estructura de
 * largo plazo, no una alarma. Los renglones van en [TonoDeCifra.Apagado]: deber no es una alerta.
 */
@Composable
private fun TarjetaDelPatrimonio(
    balance: com.jvillada.movi.ui.dashboard.HeroBalance,
    historia: com.jvillada.movi.shared.model.HistoriaDelPatrimonio?,
    historiaEnCamino: Boolean,
) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_PATRIMONIO)) {
        Text(ROTULO_DEL_PATRIMONIO_NETO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xs))
        Cifra(balance.patrimonio, VarianteDeCifra.Heroe)
        // La curva; mientras su primera lectura viaja, su lugar (para que la tarjeta no crezca de
        // golpe al llegar).
        if (historia != null) {
            CurvaDelPatrimonio(historia)
        } else if (historiaEnCamino) {
            ReservaDeLaCurva()
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            // «Tu plata» y no «Activos»: es el mismo número y el mismo rótulo que el Inicio.
            RenglonDelPatrimonio("Tu plata", balance.tuPlata, tono = TonoDeCifra.Neutro)
            if (balance.apartado > 0L) {
                RenglonDelPatrimonio(balance.apartadoPara?.let { "Apartado · $it" } ?: "Apartado", balance.apartado)
            }
            if (balance.condicionado > 0L) {
                RenglonDelPatrimonio(balance.condicionadoA?.let { "Solo para $it" } ?: "De uso condicionado", balance.condicionado)
            }
            // Los bienes: sin este renglón la cifra de arriba subía con la casa y nada decía por qué.
            if (balance.bienes > 0L) RenglonDelPatrimonio("Bienes", balance.bienes)
            if (balance.deudas > 0) RenglonDelPatrimonio("Deudas", -balance.deudas)
        }
    }
}

/** Un renglón de la resta del patrimonio: el rótulo a la izquierda y la cifra chica a la derecha. */
@Composable
private fun RenglonDelPatrimonio(rotulo: String, monto: Long, tono: TonoDeCifra = TonoDeCifra.Apagado) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.weight(1f))
        Cifra(monto, VarianteDeCifra.Chica, tono = tono)
    }
}

/**
 * **Un grupo de cuentas** (Dinero o Inversión): su [Seccion] con el conteo y una [Fila] por cuenta
 * con la marca del banco ([AvatarDeLaCuenta]) y el saldo en la moneda de la cuenta.
 *
 * El saldo va en el color del texto: un saldo es un hecho, no plata que entró. En [TonoDeCifra.Alerta]
 * solo si está en contra (una cuenta en descubierto), y apagado si es plata con destino (la AFC, la
 * pensión voluntaria: suya, pero no para cualquier cosa — ver [esDeUsoCondicionado]).
 */
@Composable
private fun GrupoDeCuentas(titulo: String, cuentas: List<Account>, onNavigate: (Screen) -> Unit) {
    Column(Modifier.testTag(TAG_GRUPO_DE_CUENTAS)) {
        Seccion(titulo = titulo, conteo = cuentas.size)
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            cuentas.forEachIndexed { i, cuenta ->
                if (i > 0) Separador()
                val (monto, moneda) = saldoEnSuMoneda(cuenta)
                Fila(
                    titulo = cuenta.name,
                    subtitulo = subtituloDeLaCuenta(cuenta),
                    inicio = { AvatarDeLaCuenta(cuenta) },
                    final = {
                        Cifra(
                            monto,
                            moneda = moneda,
                            tono = when {
                                monto < 0 -> TonoDeCifra.Alerta
                                esDeUsoCondicionado(cuenta) -> TonoDeCifra.Apagado
                                else -> TonoDeCifra.Neutro
                            },
                        )
                    },
                    onClick = { onNavigate(Screen.AccountDetail(cuenta.id, cuenta.type.group)) },
                    // Con o sin subtítulo (vuelta de diseño 2: «Bancolombia Ahorros» ya no lleva uno),
                    // todas las filas del grupo miden lo mismo: la lista no queda despareja y el
                    // esqueleto de la carga, que reserva filas dobles, no salta.
                    modifier = Modifier.heightIn(min = Medida.altoDeFilaDoble).testTag(TAG_FILA_DE_CUENTA),
                )
            }
        }
    }
}

/**
 * **Los bienes**: cada uno con su valor y —si hay una deuda que lo financia— cuánto de él es tuyo
 * de verdad, que va primero en el subtítulo porque es la pregunta que uno se hace al mirar una
 * hipoteca. Tocar uno abre la hoja para actualizar el avalúo: un bien no tiene movimientos.
 */
@Composable
private fun SeccionDeBienes(bienes: List<Account>, cuentas: List<Account>, onAbrir: (Account) -> Unit) {
    val hoy = remember { hoyEnAppZone() }
    Column {
        Seccion(titulo = "Bienes", conteo = bienes.size)
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            bienes.forEachIndexed { i, bien ->
                if (i > 0) Separador()
                val deuda = deudaDelBien(bien, cuentas)
                Fila(
                    titulo = bien.name,
                    subtitulo = listOfNotNull(deuda?.let { lineaDeLoQueEsTuyo(it) }, subtituloDelBien(bien, hoy))
                        .joinToString(" · "),
                    inicio = { IconoEnCirculo(iconoDelBien(bien), tono = TonoDeIcono.Neutro) },
                    final = { Cifra(valorEnPesos(bien)) },
                    onClick = { onAbrir(bien) },
                )
            }
        }
    }
}

private fun iconoDelBien(cuenta: Account): ImageVector = when (claseDeBien(cuenta.bien?.clase)) {
    ClaseDeBien.INMUEBLE -> Icons.Filled.Home
    ClaseDeBien.VEHICULO -> Icons.Filled.DirectionsCar
    ClaseDeBien.OTRO -> Icons.Filled.Inventory2
}

/** La tarjeta del patrimonio neto, cargando o cargada: el mismo tag en las dos para medir que no salte. */
const val TAG_TARJETA_DEL_PATRIMONIO: String = "tarjeta-del-patrimonio"

/** La cifra esqueleto del patrimonio neto — está solo mientras carga. */
const val TAG_ESQUELETO_DEL_PATRIMONIO: String = "esqueleto-del-patrimonio"

/** Las filas esqueleto del grupo de cuentas cuando no hay forma recordada (la primera vez). */
private const val FILAS_DEL_GRUPO_ESQUELETO = 4

/** Tope de filas esqueleto por grupo: más no caben en ninguna pantalla, y todas se componen. */
private const val MAX_FILAS_DEL_GRUPO_ESQUELETO = 12

/** Cada grupo de cuentas («Dinero», «Inversión»), cargando o cargado: el mismo tag en los dos. */
const val TAG_GRUPO_DE_CUENTAS: String = "grupo-de-cuentas"

/** Cada fila de cuenta real, para medirla contra [EsqueletoDeFila]. */
const val TAG_FILA_DE_CUENTA: String = "fila-de-cuenta"

/** Las cuentas del grupo «Dinero». */
internal fun cuentasDeDinero(cuentas: List<Account>): List<Account> =
    cuentas.filter { it.type.group == AccountGroup.DINERO }

/**
 * Las cuentas del grupo «Inversión». Un bien viaja como INVESTMENT (ver `Bien` en :core) pero no es
 * una inversión: tiene su propia sección, abajo, con su valor y la fecha del avalúo.
 */
internal fun cuentasDeInversion(cuentas: List<Account>): List<Account> =
    cuentas.filter { it.type.group == AccountGroup.INVERSION && !it.esBien }

/**
 * **La forma de Cuentas que se recuerda** (ver `FormaRecordada`): cuántos renglones de desglose
 * tiene la tarjeta del patrimonio —con las mismas condiciones que la tarjeta real: tu plata
 * siempre, lo condicionado, los bienes y las deudas si hay— y cuántas cuentas tiene cada grupo.
 * Solo números: ni un saldo ni un nombre.
 */
internal fun formaDeCuentas(cuentas: List<Account>): FormaDeCuentas {
    val balance = heroBalance(cuentas)
    val renglones = 1 +
        (if (balance.condicionado > 0L) 1 else 0) +
        (if (balance.bienes > 0L) 1 else 0) +
        (if (balance.deudas > 0) 1 else 0)
    return FormaDeCuentas(
        renglonesDelPatrimonio = renglones,
        filasPorGrupo = listOf(cuentasDeDinero(cuentas).size, cuentasDeInversion(cuentas).size),
    )
}

/**
 * **Patrimonio mientras carga, con la forma de Patrimonio**: la tarjeta del patrimonio neto (el
 * rótulo, la cifra con el alto de la héroe y sus renglones) y los grupos, cada uno con su título y
 * sus [EsqueletoDeFila] —que miden lo mismo que la [Fila] real—.
 *
 * **Con [forma]** (la última carga que salió bien en este aparato, ver `FormaRecordada`) reserva
 * los renglones del patrimonio y los grupos con la cantidad de filas que tenían (un grupo que tenía
 * cero no se dibuja, como el real). **Sin ella** —la primera vez— cuatro renglones y un grupo de
 * [FILAS_DEL_GRUPO_ESQUELETO] filas.
 */
private fun LazyListScope.cuentasEsqueleto(forma: FormaDeCuentas?, conTarjeta: Boolean = true, conGrupos: Boolean = true) {
    val renglones = forma?.renglonesDelPatrimonio ?: 4
    val grupos = forma?.filasPorGrupo ?: listOf(FILAS_DEL_GRUPO_ESQUELETO)
    if (conTarjeta) item {
        Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_PATRIMONIO)) {
            LineaEsqueleto(fraccionDelAncho = 0.35f, estilo = Movi.textos.apoyo)
            Spacer(Modifier.height(Espacio.xs))
            LineaEsqueleto(
                fraccionDelAncho = 0.65f,
                estilo = Movi.textos.cifra,
                modifier = Modifier.testTag(TAG_ESQUELETO_DEL_PATRIMONIO),
            )
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                repeat(renglones) {
                    RenglonConCifraEsqueleto(fraccionDelRotulo = 0.3f, estiloDeLaCifra = Movi.textos.apoyo)
                }
            }
        }
    }
    if (conGrupos) grupos.filter { it > 0 }.forEach { filasDelGrupo ->
        val filas = filasDelGrupo.coerceAtMost(MAX_FILAS_DEL_GRUPO_ESQUELETO)
        item {
            Column(Modifier.testTag(TAG_GRUPO_DE_CUENTAS)) {
                RotuloDeSeccionEsqueleto()
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    repeat(filas) { i ->
                        if (i > 0) Separador()
                        Box(Modifier.testTag(TAG_FILA_DE_LISTA_ESQUELETO)) { EsqueletoDeFila() }
                    }
                }
            }
        }
    }
}

// ── «Deudas» y herramientas ──────────────────────────────────────────────────────────

/** La fila de «Deudas», cargando o cargada: el mismo tag en las dos. */
const val TAG_TARJETA_DE_DEUDAS: String = "tarjeta-de-deudas"

/** La fila de «Cuadre de saldos», para tocarla desde una prueba sin depender de su texto. */
const val TAG_TARJETA_DE_CUADRE: String = "tarjeta-de-cuadre"

/** El título de la fila que lleva a Créditos. */
const val CREDITOS_Y_TARJETAS: String = "Créditos y tarjetas"

/**
 * **«Deudas»**: la puerta a Créditos, con el mismo total y el mismo conteo que su encabezado —
 * [totalDebtCop] es la MISMA función que usa `CreditosScreen`, no una cuenta nueva—. El detalle de
 * cada crédito vive allá; acá va una fila. Lee lo suyo aparte de `accounts`: puede contestar aunque
 * las cuentas sigan cargando o se hayan rendido.
 */
@Composable
private fun SeccionDeDeudas(
    creditos: List<CreditSummary>?,
    tarjetas: List<CardSummary>?,
    cargando: Boolean,
    onReintentar: () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Seccion(titulo = "Deudas")
        if (creditos == null || tarjetas == null) {
            if (cargando) {
                Superficie(modifier = Modifier.testTag(TAG_TARJETA_DE_DEUDAS), relleno = RellenoDeSuperficie.Lista) {
                    EsqueletoDeFila()
                }
            } else {
                NoSePudoLeer("No pudimos cargar tus deudas", onReintentar = onReintentar)
            }
        } else {
            // Sin un crédito ni una tarjeta no hay cifra: «Sin deudas registradas» ya lo dice, y un
            // «$0» al lado lo presentaría como un hecho en vez de la ausencia de datos que es.
            val hayDeuda = creditos.isNotEmpty() || tarjetas.isNotEmpty()
            Superficie(modifier = Modifier.testTag(TAG_TARJETA_DE_DEUDAS), relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = CREDITOS_Y_TARJETAS,
                    subtitulo = resumenDeDeudas(creditos, tarjetas),
                    inicio = { IconoEnCirculo(Icons.Rounded.CreditCard, tono = TonoDeIcono.Neutro) },
                    // Con monto, el monto es el final (como cada cuenta de arriba: la fila entera se
                    // toca); sin deuda, la flecha. Con monto y flecha a la vez, a 360 dp el título se
                    // quedaba con 68 dp y «Créditos y tarjetas» no entraba ni en dos renglones.
                    // Neutro (Ola 2a): las deudas no son una alerta.
                    final = { if (hayDeuda) Cifra(totalDebtCop(creditos, tarjetas)) else Chevron() },
                    onClick = onClick,
                )
            }
        }
    }
}

/** El conteo de la fila de «Deudas»: «3 créditos · 2 tarjetas», solo los grupos que hay. */
internal fun resumenDeDeudas(creditos: List<CreditSummary>, tarjetas: List<CardSummary>): String {
    val partes = buildList {
        if (creditos.isNotEmpty()) add(if (creditos.size == 1) "1 crédito" else "${creditos.size} créditos")
        if (tarjetas.isNotEmpty()) add(if (tarjetas.size == 1) "1 tarjeta" else "${tarjetas.size} tarjetas")
    }
    return if (partes.isEmpty()) "Sin deudas registradas" else partes.joinToString(" · ")
}

/**
 * **Herramientas**: «Cuadre de saldos» (compara con el banco; si hay cuentas que llevan más de un
 * período sin cuadrarse lo dice el subtítulo, el mismo dato que el aviso del Inicio) y «Movimientos
 * entre cuentas» (traspasos, cuotas y pagos de tarjeta: hechos entre dos cuentas tuyas, que pinta
 * Movimientos con las dos patas juntas — acá es un enlace, no una copia).
 */
@Composable
private fun Herramientas(atrasadas: String?, onCuadre: () -> Unit, onEntreCuentas: () -> Unit) {
    Column {
        Seccion(titulo = "Herramientas")
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = "Cuadre de saldos",
                subtitulo = atrasadas ?: "Compara con lo que dice tu banco y anota la diferencia",
                inicio = {
                    IconoEnCirculo(
                        Icons.Rounded.FactCheck,
                        tono = if (atrasadas == null) TonoDeIcono.Marca else TonoDeIcono.Aviso,
                    )
                },
                final = { Chevron() },
                onClick = onCuadre,
                // «Hace más de un mes que no cuadras Bancolombia Ahorros» es la razón de la fila: a
                // 360 dp se cortaba en «…no cuadras …». Va en dos renglones (vuelta de diseño 2).
                lineasDelSubtitulo = 2,
                modifier = Modifier.testTag(TAG_TARJETA_DE_CUADRE),
            )
            Separador()
            Fila(
                titulo = "Movimientos entre cuentas",
                subtitulo = "Traspasos, cuotas de crédito y pagos de tarjeta",
                inicio = { IconoEnCirculo(Icons.Rounded.SwapHoriz, tono = TonoDeIcono.Info) },
                final = { Chevron() },
                onClick = onEntreCuentas,
                lineasDelSubtitulo = 2,
            )
        }
    }
}
