package com.jvillada.movi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.data.Repositories
import kotlinx.coroutines.launch
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.EXPLICACION_HUELLA
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.motivoDeLaHuella
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
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
    // **Qué pasó con la lectura**, que no es lo mismo que «todavía no llegó». Sin esta variable
    // la pantalla no podía distinguir «se está cargando» de «falló», y las dos filas de abajo
    // pintaban el valor por defecto como si fuera el suyo. Ver [AJUSTES_NO_LEIDOS].
    var falloElPerfil by remember { mutableStateOf(false) }
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
        falloElPerfil = false
        runCatching { Repositories.wallets.getUserProfile() }.onSuccess {
            profile = it
            SessionManager.userName = it.name
            SessionManager.avatarColor = it.avatarColor
        }.onFailure {
            // Antes acá no había nada: la lectura fallaba en silencio, `profile` se quedaba en
            // `null` para siempre —nada reintentaba— y las filas de «Inicio del mes» y del aviso
            // mostraban el valor por defecto como si fuera el ajuste guardado. Con corte 25, un
            // toque en «Guardar» le cambiaba el mes entero a mes de calendario y con él TODAS las
            // cifras de Movimientos, Presupuestos, Créditos e Inicio. Ahora se dice y se reintenta.
            falloElPerfil = true
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
            .background(Movi.colores.fondo)
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
                            Text(displayName, style = Movi.textos.titular, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.3).sp)
                            Text(SessionManager.userEmail ?: "", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            // F44: "PREMIUM · FAMILIAR" estaba fijo en el código — no existen
                            // planes ni tipos de cuenta, así que la etiqueta mentía. Se saca;
                            // vuelve con significado real si algún día hay planes o familia (F8).
                        }
                        // F42 · F46: antes esta tarjeta era de solo lectura — nada acá abría
                        // nada. Un solo "Editar" alcanza para las dos cosas que se pueden
                        // cambiar (alias y color), en vez de dos afordancias separadas.
                        Text(
                            "Editar",
                            style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.marca,
                            modifier = Modifier.clickable { showEditProfile = true },
                        )
                    }
                }
            }

            // Lo que no se pudo leer se dice, con su reintento — mismo componente que Cuentas,
            // Créditos y Metas. Va arriba de las filas que dependen del perfil para que se lea
            // antes de tocarlas, no después.
            if (falloElPerfil) {
                item {
                    Spacer(Modifier.height(14.dp))
                    NoSePudoLeer(
                        texto = AJUSTES_NO_LEIDOS,
                        onReintentar = { profileReloadKey++ },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
                            left = { Text("Cambiar contraseña", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto) },
                            showChevron = true,
                            onClick = { showChangePassword = true },
                        )
                        // El día en que arranca el período. Vive en «Cuenta» y no escondido en
                        // una pantalla aparte porque cambia el significado de TODAS las cifras
                        // del mes: quien lo busca lo busca acá.
                        // Mientras el corte no se sepa, esta fila NO dice «Mes de calendario» ni
                        // se deja tocar: ese texto era el valor por defecto disfrazado del suyo, y
                        // la hoja que abría llegaba con el día 1 preseleccionado y un «Guardar»
                        // que lo escribía de verdad.
                        val cutoff = profile?.periodCutoffDay
                        val abrirPeriodo: (() -> Unit)? =
                            if (cutoff == null) null else ({ errorPeriodo = null; showPeriodo = true })
                        CardRow(
                            left = {
                                Column {
                                    Text("Inicio del mes", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                                    Text(
                                        text = cutoff?.let { d ->
                                            if (d == 1) "Mes de calendario" else "Cada día $d"
                                        } ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                            },
                            showChevron = cutoff != null,
                            isLast = true,
                            onClick = abrirPeriodo,
                        )
                    }
                }
            }

            // Cómo se ve la app. Sección propia y no una fila de «Cuenta» porque no es una
            // propiedad del dueño: es de ESTE aparato. Ver el KDoc de TemaStore — el mismo día
            // puede querer oscuro en el teléfono de noche y claro en el navegador de día.
            item {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
                    MinSectionHeader(title = "Cómo se ve")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        CardRow(
                            left = {
                                Column {
                                    Text(
                                        "Tema oscuro",
                                        style = Movi.textos.titulo,
                                        color = Movi.colores.texto,
                                    )
                                    Text(
                                        text = if (TemaStore.oscuro) {
                                            "Como lo tuviste siempre"
                                        } else {
                                            "Estás en el tema claro"
                                        },
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                            },
                            right = {
                                Switch(
                                    checked = TemaStore.oscuro,
                                    onCheckedChange = { TemaStore.poner(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Movi.colores.sobreMarca,
                                        checkedTrackColor = Movi.colores.marca,
                                        uncheckedThumbColor = Movi.colores.textoApagado,
                                        uncheckedTrackColor = Movi.colores.tarjeta,
                                        uncheckedBorderColor = Movi.colores.borde,
                                    ),
                                )
                            },
                            isLast = true,
                            // Toda la fila alterna, no solo el interruptor: un blanco de 44 dp
                            // de alto es más fácil de acertar que uno de 32 de ancho.
                            onClick = { TemaStore.alternar() },
                        )
                    }
                }
            }

            // «Entrar con huella». Solo aparece donde hay lector: en iOS y la web
            // `Huella.deEsteAparato()` es null y este item no dibuja nada, igual que la sección
            // de push no existe fuera del navegador.
            item {
                val huella = Huella.deEsteAparato()
                val estado = huella?.estado() ?: EstadoDeHuella.NO_DISPONIBLE
                if (huella != null && estado != EstadoDeHuella.NO_DISPONIBLE) {
                    // Se lee una vez y se guarda en estado de Compose: `SessionManager` no es
                    // observable, así que sin esto el interruptor no se movería al tocarlo.
                    var activada by remember { mutableStateOf(SessionManager.huellaActivada) }
                    var avisoHuella by remember { mutableStateOf<String?>(null) }
                    var ocupada by remember { mutableStateOf(false) }

                    Spacer(Modifier.height(14.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MinSectionHeader(title = "Entrar")
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            val alternar = alternar@{
                                if (ocupada) return@alternar
                                if (activada) {
                                    // Apagarla es inmediato y no toca la sesión: lo único que
                                    // cambia es que al abrir la app ya no se pide el dedo.
                                    SessionManager.huellaActivada = false
                                    activada = false
                                    avisoHuella = null
                                } else if (estado == EstadoDeHuella.SIN_REGISTRAR) {
                                    avisoHuella = "Registra una huella en los ajustes de este teléfono y vuelve."
                                } else {
                                    // Se pide el dedo ANTES de prender el interruptor: prenderlo a
                                    // ciegas lo dejaría con una puerta que recién falla la próxima
                                    // vez que abra Movi, cuando ya no está mirando esta pantalla.
                                    ocupada = true
                                    avisoHuella = null
                                    huella.pedir(PropositoDeHuella.ACTIVAR) { resultado ->
                                        ocupada = false
                                        if (resultado == ResultadoDeHuella.EXITO) {
                                            SessionManager.huellaActivada = true
                                            activada = true
                                        } else {
                                            avisoHuella = motivoDeLaHuella(resultado)
                                        }
                                    }
                                }
                            }
                            CardRow(
                                left = {
                                    Column {
                                        Text(
                                            "Entrar con huella",
                                            style = Movi.textos.titulo,
                                            fontWeight = FontWeight.Medium,
                                            color = Movi.colores.texto,
                                        )
                                        Text(
                                            text = avisoHuella ?: when {
                                                ocupada -> "Esperando tu huella…"
                                                activada -> EXPLICACION_HUELLA
                                                estado == EstadoDeHuella.SIN_REGISTRAR ->
                                                    "Este teléfono todavía no tiene huellas registradas."
                                                else -> "Abre Movi con tu huella, sin escribir tu contraseña."
                                            },
                                            style = Movi.textos.apoyo,
                                            color = if (avisoHuella != null) Movi.colores.sale else Movi.colores.textoMedio,
                                        )
                                        // El alcance exacto, dicho donde se toma la decisión y no
                                        // descubierto después. La huella tapa la app; NO cifra la
                                        // sesión, y eso es lo que deja a la captura de SMS
                                        // funcionando sola en segundo plano.
                                        if (activada) {
                                            Text(
                                                "Tapa la app, no la cifra. Tus SMS del banco se siguen subiendo solos aunque no abras Movi.",
                                                style = Movi.textos.apoyo,
                                                color = Movi.colores.textoApagado,
                                            )
                                        }
                                    }
                                },
                                right = {
                                    Switch(
                                        checked = activada,
                                        onCheckedChange = { alternar() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Movi.colores.sobreMarca,
                                            checkedTrackColor = Movi.colores.marca,
                                            uncheckedThumbColor = Movi.colores.textoApagado,
                                            uncheckedTrackColor = Movi.colores.tarjeta,
                                            uncheckedBorderColor = Movi.colores.borde,
                                        ),
                                    )
                                },
                                isLast = true,
                                onClick = { alternar() },
                            )
                        }
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
                        // Mismo trato que «Inicio del mes»: sin el perfil no se afirma cuántos
                        // días antes avisa, porque lo que se afirmaría es el valor por defecto.
                        val diasDeAviso = profile?.reminderLeadDays
                        val abrirAviso: (() -> Unit)? =
                            if (diasDeAviso == null) null else ({ errorAviso = null; showAviso = true })
                        CardRow(
                            left = {
                                Column {
                                    Text("Avisarme antes de un vencimiento", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                                    Text(
                                        text = diasDeAviso?.let { reminderLeadHint(it) }
                                            ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                            },
                            showChevron = diasDeAviso != null,
                            isLast = true,
                            onClick = abrirAviso,
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
                                    Text("Notificaciones push", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                                    Text(
                                        when (pushStatus) {
                                            "enabled" -> "Activadas en este dispositivo"
                                            "denied" -> "Bloqueadas por el navegador"
                                            else -> "Recibe tus pagos próximos"
                                        },
                                        style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
                                    )
                                }
                                Text(
                                    if (pushStatus == "enabled") "Desactivar" else "Activar",
                                    style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium,
                                    color = if (pushStatus == "denied") Movi.colores.textoApagado else Movi.colores.texto,
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
                            left = { Text("Aún sin meta", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio) },
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
                        .background(Movi.colores.tarjeta)
                        .clickable { onLogout() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cerrar sesión", style = Movi.textos.cuerpo, color = Movi.colores.sale, fontWeight = FontWeight.Medium)
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
                                left = { Text("Editor de pantallas", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto) },
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
    // `profile?.let` y no `?: DEFAULT_...`: si el perfil no llegó, la hoja no se abre. Un default
    // en el `initial` de una hoja con botón de guardar es una propuesta de cambio que el dueño no
    // pidió, y que se parece a su ajuste actual.
    val perfil = profile
    if (showAviso && perfil != null) {
        DiasDeAvisoSheet(
            diasActuales = perfil.reminderLeadDays,
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
    if (showPeriodo && perfil != null) {
        PeriodoSheet(
            cutoffActual = perfil.periodCutoffDay,
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

/** Lo que dice la tarjeta de reintento cuando el perfil del dueño no se pudo leer. */
const val AJUSTES_NO_LEIDOS: String = "No pudimos cargar tus ajustes"

/** Subtítulo de una fila cuyo valor guardado todavía viaja. */
const val CARGANDO: String = "Cargando…"

/** Subtítulo de una fila cuyo valor guardado no se pudo leer. Nunca el valor por defecto. */
const val NO_PUDIMOS_LEERLO: String = "No pudimos leerlo"
