package com.jvillada.movi.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*

/**
 * F50: Inversiones y una cuenta tipo Inversión (CDT, fondo, cajita) eran "dos formas de la
 * misma idea que crecieron por separado" — una cuenta con saldo real y alta; la otra, un
 * modelo de "posiciones" sin forma de crearlas (F21) que el server siempre devolvía vacío
 * (ver `GET /api/holdings`, ahora sin consumidor). Se unifican: esta pantalla ya no lee
 * holdings, lee las cuentas de tipo [AccountType.INVESTMENT] — mismas que Cuentas — y el "+"
 * abre la misma hoja de alta que Cuentas, con el tipo ya elegido.
 */
@Composable
fun InversionesScreen(onNavigate: (Screen) -> Unit) {
    val goBack = LocalGoBack.current
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
    }

    val investmentAccounts = accounts.filter { it.type == AccountType.INVESTMENT }
    val total = investmentAccounts.sumOf { it.balance }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MinBg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // F22: Inversiones vive en Más — destino de reserva si no hay historial
                // (antes caía siempre en Inicio, aunque entraras desde Más).
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = MinText, modifier = Modifier.size(22.dp).clickableSimple { goBack(Screen.Mas) })
                Text("Inversiones", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
                // F21/F50: el "+" volvió — ahora sí crea algo real, una cuenta de Inversión (misma
                // hoja que Cuentas, con el tipo ya elegido).
                Text(
                    text = "+ Nueva",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinText,
                    modifier = Modifier.clickableSimple { showCreateSheet = true },
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                if (investmentAccounts.isEmpty()) {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Text("Aún no tienes cuentas de inversión", fontSize = 14.sp, color = MinTextMute)
                        }
                    }
                } else {
                    item {
                        MinCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(22.dp),
                        ) {
                            Text("Patrimonio invertido", fontSize = 12.sp, color = MinTextMute, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = formatCOP(total),
                                fontSize = 38.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                                color = MinText,
                                letterSpacing = (-1.4).sp,
                                lineHeight = 38.sp,
                            )
                            // F50: el gráfico por período que había acá dibujaba una curva fija
                            // inventada, no datos reales (mismo defecto que ya se sacó del Balance
                            // del Inicio en la Ola 4) — se va sin reemplazo hasta que haya una
                            // serie real que mostrar.
                        }
                    }

                    item {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            MinSectionHeader(title = "Mis cuentas de inversión", count = investmentAccounts.size)
                            MinCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = MinCardVariant.Elevated,
                                padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                investmentAccounts.forEachIndexed { i, account ->
                                    CardRow(
                                        left = { Text(account.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                        right = {
                                            Text(
                                                text = formatCOP(account.balance),
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                color = MinText,
                                                letterSpacing = (-0.3).sp,
                                            )
                                        },
                                        isLast = i == investmentAccounts.size - 1,
                                        showChevron = true,
                                        onClick = { onNavigate(Screen.AccountDetail(account.id)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
        // La hoja como overlay del Box: cubre el encabezado también — antes vivía dentro
        // del Column y el scrim dejaba el «+ Nueva» y la flecha tocables por encima.
        if (showCreateSheet) {
        CreateAccountSheet(
        onDismiss = { showCreateSheet = false },
        onAccountCreated = {
        showCreateSheet = false
        refreshKey++
        },
        initialType = AccountType.INVESTMENT,
        )
        }
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick)
)
