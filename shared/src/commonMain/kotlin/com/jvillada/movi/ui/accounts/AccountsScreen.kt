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
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.dashboard.heroBalance

@Composable
fun AccountsScreen(onNavigate: (Screen) -> Unit) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
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

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
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
                    color = MinPrimaryContainer,
                    trackColor = MinSurfaceContainerHigh,
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
                                    text = "No pudimos cargar tus cuentas",
                                    fontSize = 15.sp,
                                    color = MinTextDim,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MinPrimaryContainer)
                                        .clickable { refreshKey++ }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Reintentar",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MinOnPrimaryContainer,
                                    )
                                }
                            }
                        }
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
                                    fontSize = 15.sp,
                                    color = MinTextDim,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MinPrimaryContainer)
                                        .clickable { showCreateSheet = true }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Crear primera cuenta",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MinOnPrimaryContainer,
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
                                fontSize = 11.sp,
                                color = MinTextMute,
                                letterSpacing = 0.4.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            MonoText(
                                text = formatCOP(balance.patrimonio), // formatCOP ya trae el signo (F36) — no duplicarlo acá
                                fontSize = 28f,
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
                                color = MinText,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // «Tu plata» y no «Activos»: es el mismo número y el mismo rótulo
                                // que la cifra grande del Inicio, y compartir la palabra es lo
                                // que hace obvio que son la misma cosa vista dos veces.
                                Text("Tu plata", fontSize = 12.sp, color = MinTextMute)
                                MonoText(formatCOP(balance.tuPlata), 12f, color = MinIncome)
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
                                        fontSize = 12.sp,
                                        color = MinTextMute,
                                    )
                                    MonoText(formatCOP(balance.condicionado), 12f, color = MinTextDim)
                                }
                            }
                            if (balance.deudas > 0) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Deudas", fontSize = 12.sp, color = MinTextMute)
                                    MonoText("−${formatCOP(balance.deudas)}", 12f, color = MinExpense)
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
                    val inversion = accounts.filter { it.type.group == AccountGroup.INVERSION }

                    item { AccountsGroup(title = "Dinero", accounts = dinero, onNavigate = onNavigate) }
                    item {
                        Spacer(Modifier.height(20.dp))
                        AccountsGroup(title = "Inversión", accounts = inversion, onNavigate = onNavigate)
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
            )
        }
    }
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
        // del grupo a la derecha en mono — no es una acción, así que no va en MinPrimary.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MinTextMute, letterSpacing = 0.5.sp)
                Text(" · ${accounts.size}", fontSize = 11.sp, color = MinTextFaint)
            }
            MonoText(formatCOP(accounts.sumOf { it.balance }), 12f, color = MinTextDim)
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            if (accounts.isEmpty()) {
                Text(
                    text = "Sin cuentas de ${title.lowercase()} aún",
                    fontSize = 13.sp,
                    color = MinTextMute,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
            accounts.forEachIndexed { index, account ->
                val icon = accountTypeIcon(account.type)
                // F56: el subtítulo ya no repite el tipo crudo ("Ahorros", "Corriente"…) — son
                // el mismo grupo (Dinero) en todos los cálculos, así que muestran su grupo; el
                // nombre que puso el dueño es lo que de verdad distingue una cuenta de otra.
                val typeLabel = account.type.groupLabel
                CardRow(
                    left = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(imageVector = icon, contentDescription = typeLabel, tint = MinTextDim, modifier = Modifier.size(20.dp))
                            Text(
                                text = account.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinText,
                            )
                        }
                    },
                    sub = typeLabel,
                    right = {
                        MonoText(
                            text = formatCOP(account.balance),
                            fontSize = 14.5f,
                            color = MinIncome,
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
