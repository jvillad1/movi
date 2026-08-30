package com.jvillada.movi.ui.profile

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import kotlinx.coroutines.launch
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import com.jvillada.movi.ui.recurrentes.reminderLeadHint

@Composable
fun PerfilScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    // F42 · F46: perfil real, leído del server — antes esta pantalla solo mostraba lo que
    // SessionManager tenía cacheado desde el login (nombre y correo, nada de color). `profile`
    // arranca en `null` y la tarjeta de identidad se pinta igual mientras tanto con lo cacheado,
    // así que no hay parpadeo — solo se actualiza cuando la respuesta llega, y de paso deja
    // SessionManager al día (ver el bloque de abajo) para que AvatarButton en otras pantallas
    // también tenga el color correcto.
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showPeriodo by remember { mutableStateOf(false) }
    var showAviso by remember { mutableStateOf(false) }
    var guardandoAviso by remember { mutableStateOf(false) }
    var errorAviso by remember { mutableStateOf<String?>(null) }
    var guardandoPeriodo by remember { mutableStateOf(false) }
    var errorPeriodo by remember { mutableStateOf<String?>(null) }
    var profileReloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(profileReloadKey) {
        runCatching { Repositories.wallets.getUserProfile() }.onSuccess {
            profile = it
            SessionManager.userName = it.name
            SessionManager.avatarColor = it.avatarColor
        }
    }

    // F47 · F48: "Editor de pantallas" vivía en Más, agregado a la grilla después de que
    // isScreenAdmin() resolvía — eso hacía que la grilla "saltara" al cargar. Se muda acá,
    // al final de Perfil, en su propia sección "Administración" (es una herramienta de
    // administración, no algo de uso diario, así que no tiene sentido mezclada con Créditos
    // y Metas). `isAdmin` arranca en null ("todavía no sé") y la sección de abajo no se
    // pinta ni en null ni en false — solo cuando la respuesta llega y es true. Sin eso habría
    // el mismo salto que tenía en Más, solo que acá abajo.
    var isAdmin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isAdmin = runCatching { Repositories.wallets.isScreenAdmin() }.getOrDefault(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        // F60: encabezado único — Perfil es subpantalla (se abre desde el avatar o desde Más):
        // flecha atrás con Más como reserva. El engranaje que había a la derecha no hacía nada
        // (sin onClick), así que no se conserva como «acción».
        MinScreenHeader(
            title = "Perfil",
            leading = HeaderLeading.Back(fallback = Screen.Mas),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Identity card
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        // F42 · F46: mientras `profile` no llegó del server se usa lo que
                        // SessionManager ya tenía cacheado del login — no hay pantalla vacía
                        // ni parpadeo, y cuando la respuesta llega ambas fuentes coinciden
                        // (el LaunchedEffect de arriba las sincroniza).
                        val displayName = profile?.name ?: SessionManager.userName ?: "Usuario"
                        val avatarColor = avatarColorOrDefault(profile?.avatarColor ?: SessionManager.avatarColor)
                        val initials = displayName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials.ifEmpty { "U" }, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = (-0.5).sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.3).sp)
                            Text(SessionManager.userEmail ?: "", fontSize = 12.5.sp, color = MinTextMute)
                            // F44: "PREMIUM · FAMILIAR" estaba fijo en el código — no existen
                            // planes ni tipos de cuenta, así que la etiqueta mentía. Se saca;
                            // vuelve con significado real si algún día hay planes o familia (F8).
                        }
                        // F42 · F46: antes esta tarjeta era de solo lectura — nada acá abría
                        // nada. Un solo "Editar" alcanza para las dos cosas que se pueden
                        // cambiar (alias y color), en vez de dos afordancias separadas.
                        Text(
                            "Editar",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MinPrimary,
                            modifier = Modifier.clickable { showEditProfile = true },
                        )
                    }
                }
            }

            // Cuenta
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Cuenta")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        // F42: reemplaza la sección "Cuenta" que F45 había sacado entera por
                        // ser puro decorado — esta fila sí tiene algo real detrás.
                        CardRow(
                            left = { Text("Cambiar contraseña", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                            showChevron = true,
                            onClick = { showChangePassword = true },
                        )
                        // El día en que arranca el período. Vive en «Cuenta» y no escondido en
                        // una pantalla aparte porque cambia el significado de TODAS las cifras
                        // del mes: quien lo busca lo busca acá.
                        CardRow(
                            left = {
                                Column {
                                    Text("Inicio del mes", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(
                                        text = profile?.periodCutoffDay?.let { d ->
                                            if (d == 1) "Mes de calendario" else "Cada día $d"
                                        } ?: "Mes de calendario",
                                        fontSize = 12.sp,
                                        color = MinTextMute,
                                    )
                                }
                            },
                            showChevron = true,
                            isLast = true,
                            onClick = { errorPeriodo = null; showPeriodo = true },
                        )
                    }
                }
            }

            // Aviso de vencimientos
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Avisos")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        // Cuántos días antes avisar. Vivía SOLO como variable de entorno del
                        // server: global para todos y fuera del alcance de cualquier usuario.
                        CardRow(
                            left = {
                                Column {
                                    Text("Avisarme antes de un vencimiento", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(
                                        text = reminderLeadHint(profile?.reminderLeadDays ?: DEFAULT_REMINDER_LEAD_DAYS),
                                        fontSize = 12.sp,
                                        color = MinTextMute,
                                    )
                                }
                            },
                            showChevron = true,
                            isLast = true,
                            onClick = { errorAviso = null; showAviso = true },
                        )
                    }
                }
            }

            // Archetype card
            item {
                // F43: acá vivía la tarjeta "Tu arquetipo / Por definir / Completa el
                // cuestionario financiero…" — el cuestionario no existe en ninguna parte de la
                // app, así que era una promesa sin nada detrás. Se saca entera (no solo el
                // texto) en vez de dejar un cuestionario que nunca se puede completar; la
                // sección "Mi perfil financiero" se va con ella. Si algún día se construye el
                // cuestionario, vuelve con contenido real.
                if (PushOptIn.supported) {
                    Spacer(Modifier.height(14.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Notificaciones")
                        var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
                        var refreshTick by remember { mutableStateOf(0) }
                        LaunchedEffect(refreshTick) {
                            // el flujo JS es async: refrescar unas veces tras cada acción
                            repeat(20) {
                                kotlinx.coroutines.delay(600)
                                pushStatus = PushOptIn.status()
                            }
                        }
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Notificaciones push", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText)
                                    Text(
                                        when (pushStatus) {
                                            "enabled" -> "Activadas en este dispositivo"
                                            "denied" -> "Bloqueadas por el navegador"
                                            else -> "Recibe tus pagos próximos"
                                        },
                                        fontSize = 12.sp, color = MinTextMute,
                                    )
                                }
                                Text(
                                    if (pushStatus == "enabled") "Desactivar" else "Activar",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = if (pushStatus == "denied") MinTextFaint else MinText,
                                    modifier = Modifier.clickable(enabled = pushStatus != "denied") {
                                        if (pushStatus == "enabled") PushOptIn.disable() else PushOptIn.enable()
                                        refreshTick++
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Meta principal
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MinSectionHeader(title = "Meta principal", action = "Ver metas", onAction = { onNavigate(Screen.Goals) })
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        // F45: "Sin meta definida" + "Ve a Metas para crear tu primera meta"
                        // prometía un alta que Metas todavía no tiene (F26 — llega en la Ola
                        // 6). El texto ya no promete: solo dice que no hay meta y enlaza a
                        // Metas.
                        CardRow(
                            left = { Text("Aún sin meta", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinTextMute) },
                            sub = "Ver Metas de ahorro",
                            isLast = true,
                            onClick = { onNavigate(Screen.Goals) },
                        )
                    }
                }
            }

            // F45: acá vivía la sección "Cuenta" con las filas "Familia" (sin acción),
            // "Privacidad y datos · SMS y extractos cifrados" (no hay cifrado propio en el
            // servidor — viaja por HTTPS y queda en Postgres tal cual) y "Notificaciones ·
            // Alertas inteligentes activas" (no existen). Las tres eran decorado, y dos
            // afirmaban cosas falsas — se sacan enteras. "Notificaciones push", que sí
            // funciona, se queda arriba, en su propia sección.


            // Logout button
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainer)
                        .clickable { onLogout() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cerrar sesión", fontSize = 14.sp, color = MinExpense, fontWeight = FontWeight.Medium)
                }
            }

            // Va DESPUÉS de «Cerrar sesión» a propósito: `isAdmin` llega async, y si esta sección
            // se pintara antes del botón, al aparecer lo empujaría hacia abajo en cada visita (salto de
            // layout). Al final de la lista solo alarga el scroll — nada se mueve bajo el dedo.
            // F47 · F48: sección de administración — solo para quien administra el Inicio
            // (SDUI). Ver comentario junto a `isAdmin` arriba.
            if (isAdmin == true) {
                item {
                    Spacer(Modifier.height(14.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Administración")
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            CardRow(
                                left = { Text("Editor de pantallas", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MinText) },
                                sub = "Reordena y edita las secciones del Inicio sin desplegar",
                                showChevron = true,
                                isLast = true,
                                onClick = { onNavigate(Screen.ScreenEditor) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileSheet(
            initialName = profile?.name ?: SessionManager.userName ?: "",
            initialColor = profile?.avatarColor ?: SessionManager.avatarColor,
            onDismiss = { showEditProfile = false },
            onSaved = { showEditProfile = false; profileReloadKey++ },
        )
    }
    if (showChangePassword) {
        ChangePasswordSheet(
            onDismiss = { showChangePassword = false },
            onSaved = { showChangePassword = false },
        )
    }
    val alcancePeriodo = rememberCoroutineScope()
    if (showAviso) {
        DiasDeAvisoSheet(
            diasActuales = profile?.reminderLeadDays ?: DEFAULT_REMINDER_LEAD_DAYS,
            saving = guardandoAviso,
            error = errorAviso,
            onDismiss = { showAviso = false; errorAviso = null },
            onSave = { dias ->
                guardandoAviso = true
                errorAviso = null
                alcancePeriodo.launch {
                    runCatching { Repositories.wallets.updateUserProfile(UpdateProfileRequest(reminderLeadDays = dias)) }
                        .onSuccess { guardandoAviso = false; showAviso = false; profileReloadKey++ }
                        .onFailure {
                            guardandoAviso = false
                            errorAviso = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
                        }
                }
            },
        )
    }
    if (showPeriodo) {
        PeriodoSheet(
            cutoffActual = profile?.periodCutoffDay ?: 1,
            saving = guardandoPeriodo,
            error = errorPeriodo,
            onDismiss = { showPeriodo = false; errorPeriodo = null },
            onSave = { dia ->
                guardandoPeriodo = true
                errorPeriodo = null
                alcancePeriodo.launch {
                    runCatching { Repositories.wallets.updateUserProfile(UpdateProfileRequest(periodCutoffDay = dia)) }
                        .onSuccess {
                            guardandoPeriodo = false
                            showPeriodo = false
                            profileReloadKey++
                        }
                        .onFailure {
                            guardandoPeriodo = false
                            // Mandarlo a reintentar es más útil que un código de error: lo único
                            // que puede hacer desde acá es volver a tocar Guardar.
                            errorPeriodo = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
                        }
                }
            },
        )
    }
    }
}
