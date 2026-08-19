package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.openingEventFor
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private data class TypeOption(val type: AccountType, val label: String, val description: String, val icon: ImageVector)

// F56: dos tipos, no seis. Efectivo/Ahorros/Corriente se tratan idéntico en todos los cálculos
// (verificado contra Balance.kt) — "Dinero" los cubre a todos, y el nombre que escribe el
// dueño ("Bancolombia Ahorros", "Nequi") dice el resto. Tarjeta y Préstamo salen del selector
// (F51/F52): son deuda, no plata tuya, y se crean desde Créditos con sus términos.
private val TYPE_OPTIONS = listOf(
    TypeOption(AccountType.SAVINGS, "Dinero", "La plata disponible: ahorros, corriente, efectivo", Icons.Filled.AccountBalanceWallet),
    TypeOption(AccountType.INVESTMENT, "Inversión", "Plata guardada: CDT, fondos", Icons.AutoMirrored.Filled.TrendingUp),
)

@Composable
fun CreateAccountSheet(
    onDismiss: () -> Unit,
    onAccountCreated: () -> Unit,
    // F50: Inversiones abre esta hoja con el tipo ya elegido — el dueño no tiene que
    // volver a seleccionar "Inversión" a mano después de tocar el "+" de esa pantalla.
    initialType: AccountType = AccountType.SAVINGS,
) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(initialType) }
    var initialBalance by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = name.isNotBlank() && !saving
    // F24: el mismo patrón para todas las hojas de crear — si el botón está gris, debajo dice
    // la PRIMERA cosa que falta, no solo se apaga en silencio. Acá lo único obligatorio es el
    // nombre (el saldo puede quedar en $0).
    val missingFieldMessage = when {
        name.isBlank() -> "Falta el nombre"
        else -> null
    }

    // La cuenta que ya quedó creada en el server si el paso 1 (crear) tuvo éxito y el paso 2
    // (postear la apertura) falló — en la web son dos POST independientes. Sin esto, tocar
    // «Crear cuenta» de nuevo creaba una SEGUNDA cuenta (id nuevo) y la primera quedaba
    // huérfana en $0 para siempre, sin forma de borrarla desde la app. Con esto, el reintento
    // reusa la cuenta y solo repite el paso que falló. En Android/iOS postEvent nunca lanza,
    // así que este estado no se llega a usar — pero no cuesta nada tenerlo.
    var createdAccount by remember { mutableStateOf<Account?>(null) }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val account = createdAccount ?: Account(
                // Generado acá, no en blanco — mismo motivo que en QuickAddScreen: en Android/iOS
                // esto pasa por LocalRepository, que ahora necesita un id propio desde el
                // primer instante (ver newId() y LocalRepository.createAccount).
                id = newId("acc"),
                name = name.trim(),
                type = selectedType,
                balance = initialBalance ?: 0L,
                // F51: la moneda ya no es exclusiva de tarjeta (que salió del selector) — una
                // cuenta de Inversión en USD existe de verdad (un CDT en dólares); Dinero se
                // queda fijo en COP porque es efectivo/ahorros/corriente de acá.
                // COP fijo por ahora: el display de saldos solo entiende COP (`balance` es el
                // componente COP) — un CDT en USD se mostraría como $0 en la web y como pesos
                // en Android. El selector de moneda vuelve cuando exista display multimoneda.
                currency = "COP",
            )
            val result = runCatching {
                // La cuenta se crea SIEMPRE en $0 — el saldo/deuda inicial que el dueño tipeó
                // arriba no viaja en este POST. Es este único call site el que decide "esta
                // cuenta arranca con plata", posteando el evento de apertura aparte, explícito y
                // una sola vez (ver el KDoc de openingEventFor en :core para el porqué: el server
                // dejó de fabricarlo — hacerlo ahí duplicaba el saldo de una cuenta creada
                // offline cuando el ingreso/gasto real anotado antes del sync se sumaba encima de
                // la apertura que el server fabricaba a partir del balance sincronizado).
                val created = createdAccount
                    ?: Repositories.wallets.createAccount(account.copy(balance = 0L)).also { createdAccount = it }
                if (account.balance != 0L) {
                    val opening = openingEventFor(
                        account.copy(id = created.id),
                        now = Clock.System.now().toEpochMilliseconds(),
                    )
                    if (opening != null) Repositories.wallets.postEvent(opening)
                }
                created
            }
            saving = false
            result.onSuccess { onAccountCreated() }
                .onFailure {
                    error = if (createdAccount != null)
                        "La cuenta quedó creada, pero no se pudo registrar el saldo inicial. Toca «Crear cuenta» para reintentar."
                    else it.toUserMessage()
                }
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

            // --- NOMBRE ---
            SectionLabel("NOMBRE")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinSurfaceContainerLow)
                    .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    cursorBrush = SolidColor(MinText),
                    textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("Ej: Bancolombia Ahorros", fontSize = 14.sp, color = MinTextMute)
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(18.dp))

            // --- TIPO ---
            SectionLabel("TIPO")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TYPE_OPTIONS.forEach { option ->
                    TypeCard(
                        option = option,
                        selected = selectedType == option.type,
                        onClick = {
                            selectedType = option.type
                            // El selector de moneda es solo de Inversión; cualquier otro tipo
                            // (Dinero) queda fijo en COP.
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // F52: nota, no botón — Créditos vive en Más, no acá.
            Text(
                text = "¿Tarjetas o préstamos? Se cargan en Créditos",
                fontSize = 12.sp,
                color = MinTextMute,
            )

            Spacer(Modifier.height(18.dp))

            // --- SALDO INICIAL --- (ya no hay tipo de deuda en este selector, ver F51/F52)
            SectionLabel("SALDO INICIAL")
            Spacer(Modifier.height(8.dp))
            MoneyField(
                value = initialBalance,
                onValueChange = { initialBalance = it },
            )


            // Inline error display
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error!!,
                    fontSize = 12.sp,
                    color = MinExpense,
                )
            }

            Spacer(Modifier.height(20.dp))

            // --- CTA ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canSave) MinPrimaryContainer else MinSurfaceContainerLow)
                    .clickable(enabled = canSave) { save() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saving) "Creando…" else "Crear cuenta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canSave) MinOnPrimaryContainer else MinTextFaint,
                )
            }
            if (!canSave && !saving && missingFieldMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = missingFieldMessage,
                    fontSize = 12.sp,
                    color = MinTextMute,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MinTextMute,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Selectable rounded chip that fills its share of the enclosing [Row]. */
@Composable
private fun RowScope.Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(
                if (!selected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(10.dp)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MinOnPrimaryContainer else MinTextDim,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) MinOnPrimaryContainer else MinTextDim,
                )
            }
        } else {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MinOnPrimaryContainer else MinTextDim,
            )
        }
    }
}

/**
 * F56: cada uno de los dos tipos ahora trae una línea explicando qué va ahí — antes eran 6
 * chips de una sola palabra ("Crédito", "Préstamo"…) que el dueño no podía distinguir de las
 * secciones del mismo nombre (F50/F51). Full width, no comparten fila.
 */
@Composable
private fun TypeCard(option: TypeOption, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MinPrimaryContainer else MinSurfaceContainerLow)
            .then(
                if (!selected) Modifier.border(1.dp, MinBorder, RoundedCornerShape(12.dp)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = if (selected) MinOnPrimaryContainer else MinTextDim,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = option.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) MinOnPrimaryContainer else MinText,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = option.description,
            fontSize = 12.sp,
            color = if (selected) MinOnPrimaryContainer else MinTextMute,
        )
    }
}
