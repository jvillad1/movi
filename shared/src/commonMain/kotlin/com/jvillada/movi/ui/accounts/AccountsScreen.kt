package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.groupLabel
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.transactions.CHIP_ENTRE_CUENTAS
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.cuadre.cuentasSinCuadrar
import com.jvillada.movi.ui.cuadre.textoDelAvisoDeCuadre
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.shared.model.ClaseDeBien
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.deudaDelBien
import com.jvillada.movi.shared.model.esBien
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import kotlinx.datetime.Clock

@Composable
fun AccountsScreen(onNavigate: (Screen) -> Unit) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    // La hoja de un bien abierta: `existente = null` es uno nuevo (desde «Nueva cuenta» → «Bien»).
    var bienAbierto by remember { mutableStateOf<BienAbierto?>(null) }
    // «Sin cuentas aún» es una afirmación sobre la plata del dueño, así que solo se hace cuando
    // una lectura DE VERDAD contestó y contestó vacío. Antes bastaba una lectura fallida: el
    // snackbar de error se autodescartaba y abajo quedaba el estado vacío invitando a «crear tu
    // primera cuenta» a alguien que ya tiene tres. Mismo criterio que `accountsLoaded` en la
    // hoja de Agregar.
    var cuentasLeidas by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando los datos de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it; cuentasLeidas = true }
            .onFailure { e -> error = e.toUserMessage() }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // F60: encabezado único — Cuentas es raíz (está en la barra y en el rail), así que
            // lleva avatar y el MISMO rótulo que el menú («Cuentas», ya no «Mis cuentas»).
            MinScreenHeader(
                title = "Cuentas",
                leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
                action = { NewItemButton(label = "Nueva cuenta", onClick = { showCreateSheet = true }) },
            )

            // Linear progress indicator below header while loading
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Movi.colores.marca.copy(alpha = 0.16f),
                    trackColor = Movi.colores.tarjeta,
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 80.dp,
                ),
            ) {
                if (accounts.isEmpty() && !loading && !cuentasLeidas) {
                    // No se pudo leer y no hay nada que mostrar: se dice eso, y nada más. El
                    // botón acá sería «Reintentar», no «Crear primera cuenta» — proponer crear
                    // una cuenta sin saber si ya existe es como se fabrican los duplicados.
                    item {
                        NoSePudoLeer("No pudimos cargar tus cuentas", onReintentar = { refreshKey++ })
                    }
                } else if (accounts.isEmpty() && !loading) {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(32.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "Sin cuentas aún",
                                    style = Movi.textos.titulo,
                                    color = Movi.colores.textoMedio,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Movi.colores.marca.copy(alpha = 0.16f))
                                        .clickable { showCreateSheet = true }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Crear primera cuenta",
                                        style = Movi.textos.cuerpo,
                                        fontWeight = FontWeight.Medium,
                                        color = Movi.colores.marca,
                                    )
                                }
                            }
                        }
                    }
                } else if (accounts.isNotEmpty()) {
                    // Total assets card
                    item {
                        // **La MISMA función que el hero del Inicio**, no `assetsDebtsNet` por su
                        // cuenta. Con «Activos» sumando todo, esta tarjeta decía $137.625.167 al
                        // lado de un Inicio que ya decía «Tu plata $31.625.167» — dos pantallas
                        // calculando la misma regla distinto, que es el error que este proyecto
                        // ya cometió dos veces (Créditos vs. Inicio en la Ola 4, los presupuestos
                        // en la Ola 16). El desglose de abajo ahora escribe la cuenta completa:
                        // tu plata + lo condicionado − las deudas = el patrimonio de arriba.
                        val balance = heroBalance(accounts)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(20.dp),
                        ) {
                            Text(
                                text = "PATRIMONIO NETO",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.textoMedio,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            // La cifra protagonista de esta pantalla: `Movi.textos.cifra`, que se
                            // achica sola si un patrimonio largo no entra en un renglón.
                            CifraProtagonista(
                                text = formatCOP(balance.patrimonio), // formatCOP ya trae el signo (F36) — no duplicarlo acá
                                // Ola 9: **neutro en los dos signos**, como el patrimonio del Inicio.
                                // Antes era verde/rojo según el signo, y la línea nueva del Inicio
                                // («Patrimonio neto», en gris) NAVEGA acá: el dueño veía −$1.492,7M en
                                // gris, tocaba, y el MISMO número aparecía en rojo a 28 sp un toque
                                // después. Ese salto se lee como que algo empeoró en el camino, cuando
                                // es la misma cifra. El rojo queda para lo que sí es alarma del día
                                // (el flujo del mes, una cuenta en descubierto); un patrimonio negativo
                                // por hipotecas es estructura de largo plazo, no una pérdida — y el
                                // desglose de acá abajo (Activos en verde, Deudas en rojo) es el que
                                // carga la lectura de signo, con más información que un solo color.
                                color = Movi.colores.texto,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // «Tu plata» y no «Activos»: es el mismo número y el mismo rótulo
                                // que la cifra grande del Inicio, y compartir la palabra es lo
                                // que hace obvio que son la misma cosa vista dos veces.
                                Text("Tu plata", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                Cifra(formatCOP(balance.tuPlata), Movi.textos.apoyo, color = Movi.colores.entra)
                            }
                            // El renglón que faltaba: sin él, tu plata − deudas no daba el
                            // patrimonio de arriba y el lector no tenía forma de cerrar la resta.
                            if (balance.condicionado > 0L) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = balance.condicionadoA?.let { "Solo para $it" } ?: "De uso condicionado",
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                    Cifra(formatCOP(balance.condicionado), Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                }
                            }
                            // **Los bienes**: la casa y el carro. Sin este renglón la resta de la
                            // tarjeta no cerraba en cuanto el dueño cargaba uno — la cifra de arriba
                            // subía $1.411,9M y ninguna línea de abajo decía por qué.
                            if (balance.bienes > 0L) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Bienes", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                    Cifra(formatCOP(balance.bienes), Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                }
                            }
                            if (balance.deudas > 0) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Deudas", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                    Cifra("−${formatCOP(balance.deudas)}", Movi.textos.apoyo, color = Movi.colores.sale)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // F61: Inversiones dejó de ser sección — Cuentas muestra DOS grupos con
                    // subtotal (Dinero e Inversión, según AccountGroup). Las deudas viven en
                    // Créditos, así que acá no se listan (aunque sigan sumando en el
                    // patrimonio neto de arriba).
                    val dinero = accounts.filter { it.type.group == AccountGroup.DINERO }
                    // Un bien viaja como INVESTMENT (ver `Bien` en :core) pero no es una inversión:
                    // tiene su propia sección, abajo, con su valor y la fecha del avalúo.
                    val inversion = accounts.filter { it.type.group == AccountGroup.INVERSION && !it.esBien }
                    val bienes = bienesDe(accounts)

                    item { AccountsGroup(title = "Dinero", accounts = dinero, onNavigate = onNavigate) }
                    item {
                        Spacer(Modifier.height(20.dp))
                        AccountsGroup(title = "Inversión", accounts = inversion, onNavigate = onNavigate)
                    }
                    // **Bienes**: lo que el dueño tiene y no es plata. Solo aparece cuando hay alguno
                    // —a diferencia de Dinero e Inversión, que dicen «Sin cuentas… aún»—: la puerta
                    // para crear uno es «Nueva cuenta» → «Bien», y una sección vacía más en la
                    // pantalla que más se mira sería ruido para quien no tiene casa ni carro.
                    if (bienes.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(20.dp))
                            SeccionDeBienes(
                                bienes = bienes,
                                cuentas = accounts,
                                onAbrir = { bienAbierto = BienAbierto(existente = it) },
                            )
                        }
                    }
                    // **La puerta al cuadre de saldos**, justo debajo de las cuentas cuyo saldo
                    // se acaba de leer: si alguno de esos números está corrido, esta es la fila
                    // que lo arregla. Cuando hay cuentas que llevan más de un período sin
                    // cuadrarse, la segunda línea lo dice — es el mismo dato que el aviso del
                    // Inicio (ver `cuentasSinCuadrar`), no una regla aparte.
                    item {
                        Spacer(Modifier.height(20.dp))
                        val ahora = remember(accounts) { Clock.System.now().toEpochMilliseconds() }
                        val atrasadas = cuentasSinCuadrar(accounts, ahora)
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            onClick = { onNavigate(Screen.CuadreDeSaldos) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cuadre de saldos",
                                        style = Movi.textos.cuerpo,
                                        fontWeight = FontWeight.Medium,
                                        color = Movi.colores.texto,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = textoDelAvisoDeCuadre(atrasadas)
                                            ?: "Compara con lo que dice tu banco y anota la diferencia",
                                        style = Movi.textos.apoyo,
                                        color = if (atrasadas.isEmpty()) Movi.colores.textoMedio else Movi.colores.aviso,
                                    )
                                }
                                ChevronRight()
                            }
                        }
                    }

                    // **La plata que se movió entre estas cuentas**, que hasta acá era un chip en
                    // Movimientos. El dueño: «Entre cuentas creo que no hace falta acá, debería ir
                    // en cuentas tal vez no?» — y sí: un traspaso, una cuota o un pago de tarjeta
                    // son hechos ENTRE DOS CUENTAS SUYAS, no una forma de mirar sus gastos.
                    //
                    // Es un enlace y no una lista: la pantalla que sabe pintar esos renglones
                    // —con las dos patas juntas en un solo hecho, ver `collapseTransfers`— ya
                    // existe y es Movimientos. Duplicarla acá sería mantener dos.
                    //
                    // Va al final, debajo de las cuentas: primero lo que uno tiene, después lo que
                    // se movió entre eso.
                    item {
                        Spacer(Modifier.height(20.dp))
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            onClick = { onNavigate(Screen.Transactions(CHIP_ENTRE_CUENTAS)) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = "Movimientos entre cuentas",
                                        style = Movi.textos.cuerpo,
                                        fontWeight = FontWeight.Medium,
                                        color = Movi.colores.texto,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Traspasos, cuotas de crédito y pagos de tarjeta",
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                                ChevronRight()
                            }
                        }
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
                .padding(bottom = 16.dp),
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
                cuentas = accounts,
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

/** Qué bien tiene abierta la hoja: uno que existe, o uno nuevo con el nombre ya escrito. */
private data class BienAbierto(val existente: Account?, val nombre: String = "")

/**
 * **La sección «Bienes»**: cada bien con su valor, de cuándo es ese valor y —si hay una deuda que
 * lo financia— cuánto de él es tuyo de verdad. Tocar un renglón abre la hoja para actualizar el
 * avalúo; un bien no tiene detalle de movimientos porque no tiene movimientos.
 *
 * El subtotal del encabezado es el mismo número que el renglón «Bienes» de la tarjeta de
 * patrimonio de arriba, por construcción: los dos suman [valorEnPesos], que para un bien es su
 * valor.
 */
@Composable
private fun SeccionDeBienes(bienes: List<Account>, cuentas: List<Account>, onAbrir: (Account) -> Unit) {
    val hoy = remember { hoyEnAppZone() }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text("BIENES", style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio, letterSpacing = 0.5.sp)
                Text(" · ${bienes.size}", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }
            Cifra(formatCOP(bienes.sumOf { valorEnPesos(it) }), Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            bienes.forEachIndexed { index, bien ->
                val deuda = deudaDelBien(bien, cuentas)
                CardRow(
                    left = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = iconoDelBien(bien),
                                contentDescription = null,
                                tint = Movi.colores.textoMedio,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = bien.name,
                                style = Movi.textos.titulo,
                                fontWeight = FontWeight.Medium,
                                color = Movi.colores.texto,
                            )
                        }
                    },
                    sub = listOfNotNull(
                        subtituloDelBien(bien, hoy),
                        deuda?.let { lineaDeLoQueEsTuyo(it) },
                    ).joinToString("\n"),
                    right = {
                        Text(
                            text = formatCOP(valorEnPesos(bien)),
                            style = Movi.textos.monto,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                        )
                    },
                    isLast = index == bienes.size - 1,
                    showChevron = true,
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

/**
 * F61: un grupo de cuentas (Dinero o Inversión) con su subtotal en el encabezado de sección y
 * la lista debajo. Vacío, dice que no hay cuentas de ese grupo en lugar de desaparecer — así
 * el dueño ve que el grupo existe y dónde va a caer lo que cree.
 */
@Composable
private fun AccountsGroup(
    title: String,
    accounts: List<Account>,
    onNavigate: (Screen) -> Unit,
) {
    Column {
        // Mismo lenguaje que MinSectionHeader (rótulo en mayúsculas + conteo), con el subtotal
        // del grupo a la derecha en mono — no es una acción, así que no va en Movi.colores.marca.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(title.uppercase(), style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio, letterSpacing = 0.5.sp)
                Text(" · ${accounts.size}", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }
            Cifra(formatCOP(accounts.sumOf { valorEnPesos(it) }), Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            if (accounts.isEmpty()) {
                Text(
                    text = "Sin cuentas de ${title.lowercase()} aún",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
            accounts.forEachIndexed { index, account ->
                val icon = accountTypeIcon(account.type)
                // F56: el subtítulo ya no repite el tipo crudo ("Ahorros", "Corriente"…) — son
                // el mismo grupo (Dinero) en todos los cálculos, así que muestran su grupo; el
                // nombre que puso el dueño es lo que de verdad distingue una cuenta de otra.
                val typeLabel = account.type.groupLabel
                val saldo = saldoDeLaFila(account)
                CardRow(
                    left = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(imageVector = icon, contentDescription = typeLabel, tint = Movi.colores.textoMedio, modifier = Modifier.size(20.dp))
                            Text(
                                text = account.name,
                                style = Movi.textos.titulo,
                                fontWeight = FontWeight.Medium,
                                color = Movi.colores.texto,
                            )
                        }
                    },
                    sub = typeLabel,
                    right = {
                        Text(
                            text = saldo.texto,
                            style = Movi.textos.monto,
                            fontWeight = FontWeight.Medium,
                            // Verde es «tengo»: un saldo en contra —una cuenta en descubierto—
                            // pintado de verde dice lo contrario de lo que pasó. Mismo criterio
                            // que el hero del detalle de la cuenta.
                            color = if (saldo.enContra) Movi.colores.sale else Movi.colores.entra,
                        )
                    },
                    isLast = index == accounts.size - 1,
                    showChevron = true,
                    onClick = { onNavigate(Screen.AccountDetail(account.id, account.type.group)) },
                )
            }
        }
    }
}

// El ícono se queda por tipo específico (glifo, no texto — no repite "Ahorros"/"Corriente" en
// palabras); el texto que ve el dueño es [AccountType.groupLabel] (ver arriba).
private fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH        -> Icons.Filled.Payments
    AccountType.SAVINGS     -> Icons.Filled.AccountBalance
    AccountType.CHECKING    -> Icons.Filled.AccountBalanceWallet
    AccountType.INVESTMENT  -> Icons.AutoMirrored.Filled.TrendingUp
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
    AccountType.LOAN        -> Icons.Filled.RequestQuote
}
