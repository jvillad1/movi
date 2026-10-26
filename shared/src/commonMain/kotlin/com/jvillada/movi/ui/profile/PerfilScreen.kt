package com.jvillada.movi.ui.profile

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.PreferenciaDeTema
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.data.SaldosOcultosStore
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
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.TITULO_EDITAR_INICIO
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
    // «Resumen del período por correo»: guardándose y el error de la última vez.
    var guardandoCierre by remember { mutableStateOf(false) }
    var errorCierre by remember { mutableStateOf<String?>(null) }
    var errorPeriodo by remember { mutableStateOf<String?>(null) }
    var profileReloadKey by remember { mutableStateOf(0) }
    // «Cerrar sesión en todos los aparatos»: pide confirmación y, si sale bien, cierra también la
    // sesión de este (el token de acá tampoco sirve después). Ver `POST /api/users/me/cerrar-sesiones`.
    var confirmarCierreTotal by remember { mutableStateOf(false) }
    var cerrandoTodas by remember { mutableStateOf(false) }
    var errorCierreTotal by remember { mutableStateOf<String?>(null) }
    val alcanceCierre = rememberCoroutineScope()
    // «Descargar tus datos»: pide un enlace de dos minutos y lo abre. Mismo camino que abrir un
    // documento —en la web el navegador baja el ZIP; en el teléfono lo baja el navegador del
    // sistema a Descargas—, porque bajar un archivo es una navegación y ahí no viaja la sesión.
    val uriHandler = LocalUriHandler.current
    var bajandoDatos by remember { mutableStateOf(false) }
    var errorDatos by remember { mutableStateOf<String?>(null) }
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
        // Ola R4: cada sección es su [Seccion] y una [Superficie] con [Fila]s del sistema; lo que
        // una fila no alcanza a decir en un renglón va debajo, como nota de la sección. Entre
        // secciones, `Medida.entreSecciones` (el `spacedBy` de la lista).
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Medida.margenDePantalla,
                end = Medida.margenDePantalla,
                top = Medida.dentroDeSeccion,
                bottom = Medida.botonFlotante + Medida.entreSecciones,
            ),
            verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
        ) {
            // Quién eres: el avatar, el nombre y el correo, con «Editar» (alias y color).
            item {
                Superficie(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                        // F42 · F46: mientras `profile` no llegó del server se usa lo que
                        // SessionManager ya tenía cacheado del login — no hay pantalla vacía.
                        val displayName = profile?.name ?: SessionManager.userName ?: "Usuario"
                        val avatarColor = avatarColorOrDefault(profile?.avatarColor ?: SessionManager.avatarColor)
                        val initials = displayName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier = Modifier
                                .size(Medida.avatarGrande)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials.ifEmpty { "U" }, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, style = Movi.textos.titular, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SessionManager.userEmail ?: "", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Boton("Editar", onClick = { showEditProfile = true }, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
                    }
                }
            }

            // Lo que no se pudo leer se dice, con su reintento, arriba de las filas que dependen
            // del perfil para que se lea antes de tocarlas.
            if (falloElPerfil) {
                item {
                    NoSePudoLeer(texto = AJUSTES_NO_LEIDOS, onReintentar = { profileReloadKey++ })
                }
            }

            // Cuenta
            item {
                Column {
                    Seccion(titulo = "Cuenta")
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        Fila(titulo = "Cambiar contraseña", final = { Chevron() }, onClick = { showChangePassword = true })
                        Separador(SangriaDeSeparador.Margen)
                        // El día en que arranca el período: cambia el significado de TODAS las cifras
                        // del mes. Mientras el corte no se sepa, la fila NO dice «Mes de calendario»
                        // ni se deja tocar: ese texto era el valor por defecto disfrazado del suyo.
                        val cutoff = profile?.periodCutoffDay
                        Fila(
                            titulo = "Inicio del mes",
                            subtitulo = cutoff?.let { d ->
                                textoDelInicioDelMes(d, siElCorteCaeEnFinDeSemanaDe(profile?.periodWeekendRule))
                            } ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                            final = if (cutoff != null) ({ Chevron() }) else null,
                            onClick = if (cutoff == null) null else ({ errorPeriodo = null; showPeriodo = true }),
                        )
                    }
                }
            }

            // Cómo se ve la app: es de ESTE aparato, no del dueño (ver el KDoc de TemaStore).
            item {
                Column {
                    Seccion(titulo = "Cómo se ve")
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        // Ola 1: tres opciones; la primera sigue al teléfono ([PreferenciaDeTema]).
                        PreferenciaDeTema.entries.forEachIndexed { i, opcion ->
                            if (i > 0) Separador(SangriaDeSeparador.Margen)
                            val elegida = TemaStore.preferencia == opcion
                            Fila(
                                titulo = opcion.titulo,
                                subtitulo = if (opcion == PreferenciaDeTema.SISTEMA) "Claro u oscuro, según tu teléfono" else null,
                                final = {
                                    Box(modifier = Modifier.size(Icono.l), contentAlignment = Alignment.Center) {
                                        if (elegida) {
                                            Icon(Icons.Rounded.Check, contentDescription = "Elegido", tint = Movi.colores.marca, modifier = Modifier.size(Icono.m))
                                        }
                                    }
                                },
                                onClick = { TemaStore.poner(opcion) },
                                modifier = Modifier.semantics { selected = elegida },
                            )
                        }
                        Separador(SangriaDeSeparador.Ninguna)
                        // «Ocultar los saldos»: lo mismo que el ojo de Hoy. También de este aparato.
                        val ocultos = LocalSaldosOcultos.current
                        Fila(
                            titulo = ROTULO_OCULTAR_LOS_SALDOS,
                            subtitulo = "Tus cifras se ven como $PLATA_OCULTA en este aparato",
                            final = { Interruptor(ocultos, onCambio = { SaldosOcultosStore.poner(it) }) },
                            onClick = { SaldosOcultosStore.alternar() },
                        )
                        Separador(SangriaDeSeparador.Margen)
                        // Ola 3 · «Editar inicio»: esto es del dueño (se guarda en el server).
                        Fila(
                            titulo = TITULO_EDITAR_INICIO,
                            subtitulo = "Qué secciones ves en Hoy y en qué orden",
                            final = { Chevron() },
                            onClick = { onNavigate(Screen.EditarInicio) },
                        )
                    }
                }
            }

            // «Entrar con huella». Solo donde hay lector: en iOS y la web no dibuja nada.
            item {
                val huella = Huella.deEsteAparato()
                val estado = huella?.estado() ?: EstadoDeHuella.NO_DISPONIBLE
                if (huella != null && estado != EstadoDeHuella.NO_DISPONIBLE) {
                    // `SessionManager` no es observable: sin el estado el interruptor no se movería.
                    var activada by remember { mutableStateOf(SessionManager.huellaActivada) }
                    var avisoHuella by remember { mutableStateOf<String?>(null) }
                    var ocupada by remember { mutableStateOf(false) }
                    val alternar = alternar@{
                        if (ocupada) return@alternar
                        if (activada) {
                            // Apagarla es inmediato y no toca la sesión.
                            SessionManager.huellaActivada = false
                            activada = false
                            avisoHuella = null
                        } else if (estado == EstadoDeHuella.SIN_REGISTRAR) {
                            avisoHuella = "Registra una huella en los ajustes de este teléfono y vuelve."
                        } else {
                            // Se pide el dedo ANTES de prender el interruptor: prenderlo a ciegas lo
                            // dejaría con una puerta que recién falla la próxima vez que abra Movi.
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
                    Column {
                        Seccion(titulo = "Entrar")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            Fila(
                                titulo = "Entrar con huella",
                                subtitulo = when {
                                    ocupada -> "Esperando tu huella…"
                                    activada -> "Activada"
                                    estado == EstadoDeHuella.SIN_REGISTRAR -> "Sin huellas registradas"
                                    else -> "Sin escribir tu contraseña"
                                },
                                final = { Interruptor(activada, onCambio = { alternar() }) },
                                onClick = { alternar() },
                            )
                        }
                        // Lo que la fila no alcanza a decir: el aviso, o el alcance exacto (tapa la
                        // app, NO cifra la sesión: por eso la captura de SMS sigue sola).
                        NotaDeSeccion(
                            avisoHuella ?: when {
                                activada -> "$EXPLICACION_HUELLA Tapa la app, no la cifra: tus SMS del banco se siguen subiendo solos aunque no abras Movi."
                                estado == EstadoDeHuella.SIN_REGISTRAR -> "Este teléfono todavía no tiene huellas registradas."
                                else -> "Abre Movi con tu huella, sin escribir tu contraseña."
                            },
                            esError = avisoHuella != null,
                        )
                    }
                }
            }

            // Avisos
            item {
                Column {
                    Seccion(titulo = "Avisos")
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        // Cuántos días antes avisar. Sin el perfil no se afirma: lo que se afirmaría
                        // es el valor por defecto.
                        val diasDeAviso = profile?.reminderLeadDays
                        Fila(
                            titulo = "Avisarme antes de un vencimiento",
                            subtitulo = diasDeAviso?.let { reminderLeadHint(it) }
                                ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                            final = if (diasDeAviso != null) ({ Chevron() }) else null,
                            onClick = if (diasDeAviso == null) null else ({ errorAviso = null; showAviso = true }),
                        )
                        Separador(SangriaDeSeparador.Margen)
                        // «Cerró tu período» por correo: de la cuenta (sale del server), no del
                        // teléfono. Sin el perfil no se afirma si está encendido.
                        val cierrePorCorreo = profile?.cierrePorCorreo
                        val alternarCierre: () -> Unit = alternarCierre@{
                            val actual = cierrePorCorreo ?: return@alternarCierre
                            if (guardandoCierre) return@alternarCierre
                            guardandoCierre = true
                            errorCierre = null
                            alcanceCierre.launch {
                                runCatching { Repositories.wallets.updateUserProfile(UpdateProfileRequest(cierrePorCorreo = !actual)) }
                                    .onSuccess { profile = it; guardandoCierre = false }
                                    .onFailure {
                                        guardandoCierre = false
                                        errorCierre = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
                                    }
                            }
                        }
                        Fila(
                            titulo = "Resumen del período por correo",
                            subtitulo = when (cierrePorCorreo) {
                                null -> if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO
                                true -> "El día siguiente al corte"
                                false -> "Apagado"
                            },
                            final = {
                                Interruptor(
                                    cierrePorCorreo == true,
                                    onCambio = { alternarCierre() },
                                    habilitado = cierrePorCorreo != null && !guardandoCierre,
                                )
                            },
                            onClick = alternarCierre,
                        )
                    }
                    NotaDeSeccion(
                        errorCierre ?: when (cierrePorCorreoDe(profile)) {
                            true -> "El día siguiente al corte te llega cómo te fue en el período."
                            false -> "El resumen sigue en Hoy y en «Tus períodos»."
                            null -> null
                        },
                        esError = errorCierre != null,
                    )
                }
            }

            // Notificaciones push (en el navegador) o cómo activarlas en un iPhone.
            if (!PushOptIn.supported && PushOptIn.necesitaInstalar) {
                item {
                    Column {
                        Seccion(titulo = "Notificaciones")
                        AvisosEnElIphone()
                    }
                }
            }
            if (PushOptIn.supported) {
                item {
                    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
                    var refreshTick by remember { mutableStateOf(0) }
                    LaunchedEffect(refreshTick) {
                        // el flujo JS es async: refrescar unas veces tras cada acción
                        repeat(20) {
                            kotlinx.coroutines.delay(600)
                            pushStatus = PushOptIn.status()
                        }
                    }
                    Column {
                        Seccion(titulo = "Notificaciones")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            Fila(
                                titulo = "Notificaciones push",
                                subtitulo = when (pushStatus) {
                                    "enabled" -> "Activadas en este dispositivo"
                                    "denied" -> "Bloqueadas por el navegador"
                                    else -> "Recibe tus pagos próximos"
                                },
                                final = {
                                    Boton(
                                        if (pushStatus == "enabled") "Desactivar" else "Activar",
                                        onClick = {
                                            if (pushStatus == "enabled") PushOptIn.disable() else PushOptIn.enable()
                                            refreshTick++
                                        },
                                        variante = VarianteDeBoton.Secundario,
                                        tamano = TamanoDeBoton.S,
                                        habilitado = pushStatus != "denied",
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Tus datos: lo que hoy solo se podía sacar por psql. La cuenta es de quien la usa.
            item {
                Column {
                    Seccion(titulo = "Tus datos")
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        Fila(
                            titulo = DESCARGAR_TUS_DATOS,
                            subtitulo = if (bajandoDatos) "Preparando el archivo…" else "Un ZIP en CSV y JSON",
                            final = { Chevron() },
                            onClick = {
                                if (!bajandoDatos) {
                                    bajandoDatos = true
                                    errorDatos = null
                                    alcanceCierre.launch {
                                        runCatching { Repositories.wallets.getExportLink() }
                                            .onSuccess { enlace ->
                                                runCatching { uriHandler.openUri(enlace.url) }
                                                    .onFailure { errorDatos = "No pudimos abrir la descarga en este aparato." }
                                            }
                                            .onFailure {
                                                errorDatos = "No pudimos preparar la descarga. Revisa tu conexión e inténtalo de nuevo."
                                            }
                                        bajandoDatos = false
                                    }
                                }
                            },
                        )
                    }
                    NotaDeSeccion(
                        errorDatos ?: "Tus cuentas, movimientos, créditos y mensajes del banco.",
                        esError = errorDatos != null,
                    )
                }
            }

            // Salir: cerrar la sesión de acá y, más discreto, todas (para el teléfono perdido).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Boton("Cerrar sesión", onClick = onLogout, variante = VarianteDeBoton.Peligro, anchoCompleto = true)
                    Boton(
                        CERRAR_SESION_EN_TODOS,
                        onClick = { errorCierreTotal = null; confirmarCierreTotal = true },
                        variante = VarianteDeBoton.Fantasma,
                        habilitado = !cerrandoTodas,
                        anchoCompleto = true,
                    )
                    errorCierreTotal?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                }
            }

            // DESPUÉS de «Cerrar sesión» a propósito: `isAdmin` llega async, y si esta sección se
            // pintara antes, al aparecer empujaría el botón en cada visita.
            if (isAdmin == true) {
                item {
                    Column {
                        Seccion(titulo = "Administración")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            Fila(
                                titulo = "Editor de pantallas",
                                subtitulo = "Reordena y edita las secciones de Hoy",
                                final = { Chevron() },
                                onClick = { onNavigate(Screen.ScreenEditor) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Las cuatro hojas de esta pantalla se anotan en la pila de hojas: el «atrás» del teléfono
    // tiene que cerrar la hoja abierta, no sacar Perfil de la navegación. Es el caso que motivó
    // el mecanismo (ver `PilaDeHojas`): el dueño escribía las dos contraseñas, apretaba atrás
    // para cerrar la hoja y aterrizaba en «Más» con todo lo tipeado perdido. La lambda es la
    // MISMA que el `onDismiss` de al lado — atrás y la X cierran igual.
    if (showEditProfile) {
        AtrasCierraEstaHoja { showEditProfile = false }
        EditProfileSheet(
            initialName = profile?.name ?: SessionManager.userName ?: "",
            initialColor = profile?.avatarColor ?: SessionManager.avatarColor,
            onDismiss = { showEditProfile = false },
            onSaved = { showEditProfile = false; profileReloadKey++ },
        )
    }
    if (showChangePassword) {
        AtrasCierraEstaHoja { showChangePassword = false }
        ChangePasswordSheet(
            onDismiss = { showChangePassword = false },
            onSaved = { showChangePassword = false },
        )
    }
    if (confirmarCierreTotal) {
        AtrasCierraEstaHoja { if (!cerrandoTodas) confirmarCierreTotal = false }
        ConfirmarEnHoja(
            pregunta = "¿Cerrar sesión en todos los aparatos?",
            detalle = "Se cierra en este teléfono o computador y en cualquier otro donde hayas entrado " +
                "con tu cuenta. Para volver a usar Movi en cada uno vas a tener que entrar con tu " +
                "contraseña. Tus datos no se tocan.",
            textoConfirmar = "Cerrar en todos",
            ocupado = cerrandoTodas,
            onConfirmar = {
                cerrandoTodas = true
                alcanceCierre.launch {
                    runCatching { Repositories.wallets.cerrarSesionesEnTodosLosAparatos() }
                        .onSuccess {
                            cerrandoTodas = false
                            confirmarCierreTotal = false
                            onLogout()
                        }
                        .onFailure {
                            cerrandoTodas = false
                            confirmarCierreTotal = false
                            errorCierreTotal = "No pudimos cerrar las sesiones. Revisa tu conexión e inténtalo de nuevo."
                        }
                }
            },
            onCancelar = { confirmarCierreTotal = false },
        )
    }
    val alcancePeriodo = rememberCoroutineScope()
    // `profile?.let` y no `?: DEFAULT_...`: si el perfil no llegó, la hoja no se abre. Un default
    // en el `initial` de una hoja con botón de guardar es una propuesta de cambio que el dueño no
    // pidió, y que se parece a su ajuste actual.
    val perfil = profile
    if (showAviso && perfil != null) {
        AtrasCierraEstaHoja { showAviso = false; errorAviso = null }
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
        AtrasCierraEstaHoja { showPeriodo = false; errorPeriodo = null }
        PeriodoSheet(
            cutoffActual = perfil.periodCutoffDay,
            reglaActual = siElCorteCaeEnFinDeSemanaDe(perfil.periodWeekendRule),
            saving = guardandoPeriodo,
            error = errorPeriodo,
            onDismiss = { showPeriodo = false; errorPeriodo = null },
            onSave = { dia, regla ->
                guardandoPeriodo = true
                errorPeriodo = null
                alcancePeriodo.launch {
                    runCatching { guardarElInicioDelMes(dia, regla) }
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

/**
 * **Guardar «Inicio del mes»**: el día de corte y qué hace si cae en fin de semana o festivo. Una
 * sola escritura para Perfil y para el paso «Tu período» de la guía de primer uso.
 */
internal suspend fun guardarElInicioDelMes(dia: Int, regla: SiElCorteCaeEnFinDeSemana) =
    Repositories.wallets.updateUserProfile(
        UpdateProfileRequest(periodCutoffDay = dia, periodWeekendRule = regla.name),
    )

/**
 * Lo que dice la fila «Inicio del mes»: «Cada día 25 · o el día hábil anterior».
 * Con corte 1 y sin regla sigue siendo «Mes de calendario».
 */
fun textoDelInicioDelMes(dia: Int, regla: SiElCorteCaeEnFinDeSemana): String {
    val base = if (dia == 1 && regla == SiElCorteCaeEnFinDeSemana.ESE_DIA) "Mes de calendario" else "Cada día $dia"
    return when (regla) {
        SiElCorteCaeEnFinDeSemana.ESE_DIA -> base
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> "$base · o el día hábil anterior"
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> "$base · o el día hábil siguiente"
    }
}

private fun cierrePorCorreoDe(perfil: com.jvillada.movi.shared.model.UserProfile?): Boolean? = perfil?.cierrePorCorreo

/**
 * **La nota de una sección**: lo que una [Fila] no alcanza a decir en su renglón (el alcance de la
 * huella, para qué es el correo del cierre), debajo de la superficie y alineado con su texto. En
 * `sale` si es un error.
 */
@Composable
private fun NotaDeSeccion(texto: String?, esError: Boolean = false) {
    if (texto == null) return
    Text(
        texto,
        style = Movi.textos.apoyo,
        color = if (esError) Movi.colores.sale else Movi.colores.textoMedio,
        modifier = Modifier.padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla, top = Espacio.xs),
    )
}

/** El interruptor de Perfil, en los colores de la marca (los dos temas). */
@Composable
private fun Interruptor(encendido: Boolean, onCambio: (Boolean) -> Unit, habilitado: Boolean = true) {
    Switch(
        checked = encendido,
        enabled = habilitado,
        onCheckedChange = onCambio,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Movi.colores.sobreMarca,
            checkedTrackColor = Movi.colores.marca,
            uncheckedThumbColor = Movi.colores.textoApagado,
            uncheckedTrackColor = Movi.colores.tarjeta,
            uncheckedBorderColor = Movi.colores.borde,
        ),
    )
}

/** La fila de Perfil que baja el ZIP con todos los datos de la cuenta. */
const val DESCARGAR_TUS_DATOS: String = "Descargar tus datos"

/** El botón de Perfil que cierra todas las sesiones de la cuenta. */
const val CERRAR_SESION_EN_TODOS: String = "Cerrar sesión en todos los aparatos"

/** Lo que dice la tarjeta de reintento cuando el perfil del dueño no se pudo leer. */
const val AJUSTES_NO_LEIDOS: String = "No pudimos cargar tus ajustes"

/** Subtítulo de una fila cuyo valor guardado todavía viaja. */
const val CARGANDO: String = "Cargando…"

/** Subtítulo de una fila cuyo valor guardado no se pudo leer. Nunca el valor por defecto. */
const val NO_PUDIMOS_LEERLO: String = "No pudimos leerlo"

/**
 * Lo que Safari en un iPhone necesita para avisar: Movi en la pantalla de inicio. Es el único
 * camino que Apple deja (iOS 16.4+), y desde una pestaña la web no puede instalarse sola ni abrir
 * esa hoja: lo único útil es decir los dos toques. Ver [PushOptIn.necesitaInstalar].
 */
@Composable
internal fun AvisosEnElIphone() {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            Text("Avisos en tu iPhone", style = Movi.textos.titulo, color = Movi.colores.texto)
            Text(
                "Safari solo deja avisar a Movi si está en tu pantalla de inicio. Toca Compartir, " +
                    "luego «Agregar a Inicio», y abre Movi desde ese ícono: ahí aparece «Activar».",
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
            )
        }
    }
}
