package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.data.EXPLICACION_HUELLA
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PreferenciaDeTema
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.data.motivoDeLaHuella
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.ai.TITULO_LO_QUE_MOVI_SABE
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LocalSaldosOcultos
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.ROTULO_OCULTAR_LOS_SALDOS
import com.jvillada.movi.ui.components.avatarColorOrDefault
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeFila
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.TITULO_EDITAR_INICIO
import com.jvillada.movi.ui.profile.AJUSTES_NO_LEIDOS
import com.jvillada.movi.ui.profile.AvisosEnElIphone
import com.jvillada.movi.ui.profile.BORRAR_MI_CUENTA
import com.jvillada.movi.ui.profile.CARGANDO
import com.jvillada.movi.ui.profile.CERRAR_SESION_EN_TODOS
import com.jvillada.movi.ui.profile.ChangePasswordSheet
import com.jvillada.movi.ui.profile.DESCARGAR_TUS_DATOS
import com.jvillada.movi.ui.profile.DiasDeAvisoSheet
import com.jvillada.movi.ui.profile.EditProfileSheet
import com.jvillada.movi.ui.profile.Interruptor
import com.jvillada.movi.ui.profile.NO_PUDIMOS_LEERLO
import com.jvillada.movi.ui.profile.NotaDeSeccion
import com.jvillada.movi.ui.profile.PeriodoSheet
import com.jvillada.movi.ui.profile.guardarElInicioDelMes
import com.jvillada.movi.ui.profile.textoDelInicioDelMes
import com.jvillada.movi.ui.recurrentes.reminderLeadHint
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco
import kotlinx.coroutines.launch

/** El título de la pantalla que abre el avatar. Ver [MasScreen]. */
const val TITULO_DE_AJUSTES: String = "Ajustes"

/** La tarjeta del perfil, arriba de todo en Ajustes. Para las pruebas. */
const val TAG_TARJETA_DEL_PERFIL: String = "ajustes:tarjeta-del-perfil"

/** Lo que dice la fila que cierra la sesión de este aparato. */
const val CERRAR_SESION: String = "Cerrar sesión"

/**
 * Las secciones de Ajustes, en el orden en que se ven. «Entrar» (la huella) solo existe donde hay
 * lector, y «Administración» solo para quien administra el Inicio; las demás están siempre.
 */
val SECCIONES_DE_AJUSTES: List<String> =
    listOf("Tu plata", "Movi", "Cómo se ve", "Avisos", "Tus datos", "Entrar", "Cuenta", "Administración")

/**
 * # Ajustes — la pantalla que abre el avatar
 *
 * **Vuelta de diseño 3: una sola pantalla.** Hasta acá había dos para lo mismo: el avatar abría
 * «Ajustes», una cuadrícula de ocho íconos y un botón rojo «Borrar mi cuenta», y la configuración de
 * verdad —la contraseña, el inicio del mes, cómo se ve, los avisos, descargar los datos, cerrar la
 * sesión— estaba escondida detrás del primer ícono, «Perfil». Ahora, como en las apps de plata que
 * se usan bien (Revolut, Nubank): arriba la tarjeta del perfil y debajo las secciones, cada una con
 * su [Seccion] y sus [Fila]s, en este orden:
 *
 * - **Tu plata**: Inicio del mes, Categorías, Personas y comercios, Captura (o Mensajes) del banco;
 * - **Movi**: Movi AI, Lo que Movi sabe de ti, Editar inicio (y Primeros pasos, mientras falte algo);
 * - **Cómo se ve**: el tema y «Ocultar los saldos» (de este aparato);
 * - **Avisos**: las notificaciones, cuántos días antes avisar y el resumen por correo;
 * - **Tus datos**: Documentos, Descargar tus datos, Compartir;
 * - **Entrar** (solo con lector de huella) y **Cuenta**: la contraseña y cerrar la sesión;
 * - y al final, aparte y sin relleno rojo, «Borrar mi cuenta».
 *
 * `Screen.Profile` dejó de existir: quien navegaba a Perfil llega acá (el destino SDUI `"profile"`
 * también). **Es una reubicación**: cada interruptor y cada acción usa la MISMA lógica y el mismo
 * store que tenía en Perfil (`TemaStore`, `SaldosOcultosStore`, `updateUserProfile`, las hojas de
 * `ui/profile/`).
 *
 * Sigue llamándose `MasScreen` / `Screen.Mas` porque el destino SDUI `"mas"` y las pilas ya lo
 * nombran así; lo que el dueño ve es «Ajustes». No es una pestaña, así que lleva flecha y no avatar:
 * se vuelve a donde se estaba, o al Hoy si no hay historial.
 */
@Composable
fun MasScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit = {}) {
    // F42 · F46: el perfil real, leído del server. Mientras no llega, la tarjeta se pinta con lo que
    // SessionManager tenía del login (sin parpadeo), y al llegar deja SessionManager al día para que
    // el avatar de las otras pantallas tenga el color correcto.
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    // Qué pasó con la lectura, que no es lo mismo que «todavía no llegó». Sin esto las filas que
    // dependen del perfil pintaban el valor por defecto como si fuera el suyo (ver AJUSTES_NO_LEIDOS).
    var falloElPerfil by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showPeriodo by remember { mutableStateOf(false) }
    var showAviso by remember { mutableStateOf(false) }
    var guardandoAviso by remember { mutableStateOf(false) }
    var errorAviso by remember { mutableStateOf<String?>(null) }
    var guardandoPeriodo by remember { mutableStateOf(false) }
    var guardandoCierre by remember { mutableStateOf(false) }
    var errorCierre by remember { mutableStateOf<String?>(null) }
    var errorPeriodo by remember { mutableStateOf<String?>(null) }
    var profileReloadKey by remember { mutableStateOf(0) }
    // «Cerrar sesión en todos los aparatos»: pide confirmación y, si sale bien, cierra también la de
    // este (el token de acá tampoco sirve después). Ver `POST /api/users/me/cerrar-sesiones`.
    var confirmarCierreTotal by remember { mutableStateOf(false) }
    var cerrandoTodas by remember { mutableStateOf(false) }
    var errorCierreTotal by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    // «Descargar tus datos»: un enlace de dos minutos que se abre como navegación (en la web baja el
    // ZIP; en el teléfono, el navegador del sistema a Descargas): bajar un archivo no lleva la sesión.
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
            // Se dice y se reintenta: con el corte 25 del dueño, un «Guardar» sobre el default le
            // cambiaba el mes entero a mes de calendario.
            falloElPerfil = true
        }
    }

    // F47 · F48: «Editor de pantallas», solo para quien administra el Inicio. `null` = todavía no
    // se sabe, y la sección no se pinta ni en null ni en false: va al final para no empujar nada.
    var isAdmin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isAdmin = runCatching { Repositories.wallets.isScreenAdmin() }.getOrDefault(false)
    }

    // Ola B, tarea 7: «Primeros pasos» solo mientras la guía del Inicio tenga algo por tildar — la
    // MISMA condición que decide si el Inicio la pinta (`DashboardData.guiaIncompleta`), leída del
    // caché del Inicio: sin datos, no se afirma que falte nada.
    val guiaIncompleta = DashboardDataCache.data?.guiaIncompleta == true

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
            MinScreenHeader(
                title = TITULO_DE_AJUSTES,
                leading = HeaderLeading.Back(fallback = Screen.Dashboard),
            )
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
                item(key = "perfil") {
                    TarjetaDelPerfil(profile = profile, onEditar = { showEditProfile = true })
                }

                // Lo que no se pudo leer se dice, con su reintento, arriba de las filas que dependen
                // del perfil para que se lea antes de tocarlas.
                if (falloElPerfil) {
                    item(key = "fallo") {
                        NoSePudoLeer(texto = AJUSTES_NO_LEIDOS, onReintentar = { profileReloadKey++ })
                    }
                }

                // ── Tu plata ─────────────────────────────────────────────────────────────────
                item(key = "tu-plata") {
                    Column {
                        Seccion(titulo = "Tu plata")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            // El día en que arranca el período: cambia el significado de TODAS las
                            // cifras. Mientras no se sepa, la fila no dice «Mes de calendario» ni se
                            // deja tocar: ese texto era el valor por defecto disfrazado del suyo.
                            val cutoff = profile?.periodCutoffDay
                            Fila(
                                titulo = "Inicio del mes",
                                subtitulo = cutoff?.let { d ->
                                    textoDelInicioDelMes(d, siElCorteCaeEnFinDeSemanaDe(profile?.periodWeekendRule))
                                } ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                                final = if (cutoff != null) ({ Chevron() }) else null,
                                onClick = if (cutoff == null) null else ({ errorPeriodo = null; showPeriodo = true }),
                            )
                            Separador(SangriaDeSeparador.Margen)
                            FilaQueLleva("Categorías", Screen.Categorias, onNavigate)
                            Separador(SangriaDeSeparador.Margen)
                            FilaQueLleva(PERSONAS_Y_COMERCIOS, Screen.Destinos(), onNavigate)
                            Separador(SangriaDeSeparador.Margen)
                            // «Captura del banco» en Android, «Mensajes del banco» donde no hay captura.
                            FilaQueLleva(tituloDeCapturaDelBanco, Screen.CapturaDelBanco, onNavigate)
                        }
                    }
                }

                // ── Movi ─────────────────────────────────────────────────────────────────────
                item(key = "movi") {
                    Column {
                        Seccion(titulo = "Movi")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            FilaQueLleva("Movi AI", Screen.AIChat(), onNavigate)
                            Separador(SangriaDeSeparador.Margen)
                            FilaQueLleva(TITULO_LO_QUE_MOVI_SABE, Screen.LoQueMoviSabe, onNavigate)
                            Separador(SangriaDeSeparador.Margen)
                            // Ola 3 · «Editar inicio»: es del dueño (se guarda en el server).
                            FilaQueLleva(
                                TITULO_EDITAR_INICIO,
                                Screen.EditarInicio,
                                onNavigate,
                                subtitulo = "Qué secciones ves en Hoy y en qué orden",
                            )
                            if (guiaIncompleta) {
                                Separador(SangriaDeSeparador.Margen)
                                FilaQueLleva("Primeros pasos", Screen.PrimerosPasos, onNavigate)
                            }
                        }
                    }
                }

                // ── Cómo se ve: es de ESTE aparato, no del dueño (ver el KDoc de TemaStore) ──
                item(key = "como-se-ve") {
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
                        }
                    }
                }

                // ── Avisos: lo que era Perfil → Avisos y Perfil → Notificaciones ───────────
                item(key = "avisos") {
                    // El flujo JS del permiso es async: se relee el estado unas veces tras cada toque.
                    var pushStatus by remember { mutableStateOf(PushOptIn.status()) }
                    var refreshTick by remember { mutableStateOf(0) }
                    if (PushOptIn.supported) {
                        LaunchedEffect(refreshTick) {
                            repeat(20) {
                                kotlinx.coroutines.delay(600)
                                pushStatus = PushOptIn.status()
                            }
                        }
                    }
                    val cierrePorCorreo = profile?.cierrePorCorreo
                    val alternarCierre: () -> Unit = alternarCierre@{
                        val actual = cierrePorCorreo ?: return@alternarCierre
                        if (guardandoCierre) return@alternarCierre
                        guardandoCierre = true
                        errorCierre = null
                        alcance.launch {
                            runCatching { Repositories.wallets.updateUserProfile(UpdateProfileRequest(cierrePorCorreo = !actual)) }
                                .onSuccess { profile = it; guardandoCierre = false }
                                .onFailure {
                                    guardandoCierre = false
                                    errorCierre = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
                                }
                        }
                    }
                    Column {
                        Seccion(titulo = "Avisos")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            // Las notificaciones push del navegador, donde se pueden pedir.
                            if (PushOptIn.supported) {
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
                                Separador(SangriaDeSeparador.Margen)
                            }
                            // Cuántos días antes avisar. Sin el perfil no se afirma: sería el default.
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
                            errorCierre ?: when (profile?.cierrePorCorreo) {
                                true -> "El día siguiente al corte te llega cómo te fue en el período."
                                false -> "El resumen sigue en Hoy y en «Tus períodos»."
                                null -> null
                            },
                            esError = errorCierre != null,
                        )
                        // En un iPhone, Safari solo avisa con Movi en la pantalla de inicio.
                        if (!PushOptIn.supported && PushOptIn.necesitaInstalar) {
                            Box(modifier = Modifier.padding(top = Medida.dentroDeSeccion)) { AvisosEnElIphone() }
                        }
                    }
                }

                // ── Tus datos ────────────────────────────────────────────────────────────────
                item(key = "tus-datos") {
                    Column {
                        Seccion(titulo = "Tus datos")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            // Ola 18: los papeles. Absorbió a «Extractos».
                            FilaQueLleva("Documentos", Screen.Documentos, onNavigate)
                            Separador(SangriaDeSeparador.Margen)
                            Fila(
                                titulo = DESCARGAR_TUS_DATOS,
                                subtitulo = if (bajandoDatos) "Preparando el archivo…" else "Un ZIP en CSV y JSON",
                                final = { Chevron() },
                                onClick = {
                                    if (!bajandoDatos) {
                                        bajandoDatos = true
                                        errorDatos = null
                                        alcance.launch {
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
                            Separador(SangriaDeSeparador.Margen)
                            // El enlace de solo lectura para Caro o un asesor.
                            FilaQueLleva("Compartir", Screen.Compartir, onNavigate)
                        }
                        NotaDeSeccion(
                            errorDatos ?: "La descarga trae tus cuentas, movimientos, créditos y mensajes del banco.",
                            esError = errorDatos != null,
                        )
                    }
                }

                // ── Entrar: la huella, solo donde hay lector (en iOS y la web no dibuja nada) ──
                item(key = "entrar") { SeccionEntrarConHuella() }

                // ── Cuenta ───────────────────────────────────────────────────────────────────
                item(key = "cuenta") {
                    Column {
                        Seccion(titulo = "Cuenta")
                        Superficie(relleno = RellenoDeSuperficie.Lista) {
                            Fila(titulo = "Cambiar contraseña", final = { Chevron() }, onClick = { showChangePassword = true })
                            Separador(SangriaDeSeparador.Margen)
                            Fila(titulo = CERRAR_SESION, onClick = onLogout)
                            Separador(SangriaDeSeparador.Margen)
                            // Para el teléfono perdido: pide confirmación antes.
                            Fila(
                                titulo = CERRAR_SESION_EN_TODOS,
                                subtitulo = if (cerrandoTodas) "Cerrando…" else null,
                                onClick = if (cerrandoTodas) null else ({ errorCierreTotal = null; confirmarCierreTotal = true }),
                            )
                        }
                        NotaDeSeccion(errorCierreTotal, esError = true)
                    }
                }

                // `isAdmin` llega async: va después de todo lo de uso diario para no empujarlo.
                if (isAdmin == true) {
                    item(key = "administracion") {
                        Column {
                            Seccion(titulo = "Administración")
                            Superficie(relleno = RellenoDeSuperficie.Lista) {
                                FilaQueLleva(
                                    "Editor de pantallas",
                                    Screen.ScreenEditor,
                                    onNavigate,
                                    subtitulo = "Reordena y edita las secciones de Hoy",
                                )
                            }
                        }
                    }
                }

                // Al final y aparte: se usa una vez, si es que se usa, y no puede quedar a un toque
                // distraído de nada. Sin relleno rojo: una fila más, con el ícono y el título en el
                // color de alerta (vuelta de diseño 5: `Fila` ya deja elegir el tono del título).
                item(key = "borrar") {
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        Fila(
                            titulo = BORRAR_MI_CUENTA,
                            tonoDelTitulo = TonoDeFila.Alerta,
                            inicio = { IconoEnCirculo(Icons.Rounded.DeleteForever, tono = TonoDeIcono.Peligro) },
                            final = { Chevron() },
                            onClick = { onNavigate(Screen.BorrarLaCuenta) },
                        )
                    }
                }
            }
        }

        // Las hojas de esta pantalla se anotan en la pila de hojas: el «atrás» del teléfono cierra la
        // hoja abierta, no saca Ajustes de la navegación (ver `PilaDeHojas`). La lambda es la MISMA
        // que el `onDismiss` de al lado — atrás y la X cierran igual.
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
                    alcance.launch {
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
        // `profile?.let` y no un default: si el perfil no llegó, la hoja no se abre. Un default en
        // una hoja con «Guardar» es una propuesta de cambio que el dueño no pidió.
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
                    alcance.launch {
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
                    alcance.launch {
                        runCatching { guardarElInicioDelMes(dia, regla) }
                            .onSuccess {
                                guardandoPeriodo = false
                                showPeriodo = false
                                profileReloadKey++
                            }
                            .onFailure {
                                guardandoPeriodo = false
                                errorPeriodo = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
                            }
                    }
                },
            )
        }
    }
}

/** Una fila que abre otra pantalla: el título (y el subtítulo) con la flecha de «esto se abre». */
@Composable
private fun FilaQueLleva(titulo: String, destino: Screen, onNavigate: (Screen) -> Unit, subtitulo: String? = null) {
    Fila(titulo = titulo, subtitulo = subtitulo, final = { Chevron() }, onClick = { onNavigate(destino) })
}

/** Quién eres: el avatar, el nombre y el correo, con «Editar» (alias y color). */
@Composable
private fun TarjetaDelPerfil(profile: UserProfile?, onEditar: () -> Unit) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_PERFIL)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            // F42 · F46: mientras `profile` no llegó se usa lo que SessionManager tenía del login.
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
            Boton("Editar", onClick = onEditar, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
        }
    }
}

/**
 * «Entrar con huella». Solo donde hay lector: en iOS y la web no dibuja nada. La misma lógica que
 * tenía en Perfil: se pide el dedo ANTES de prender el interruptor, y apagarlo no toca la sesión.
 */
@Composable
private fun SeccionEntrarConHuella() {
    val huella = Huella.deEsteAparato()
    val estado = huella?.estado() ?: EstadoDeHuella.NO_DISPONIBLE
    if (huella == null || estado == EstadoDeHuella.NO_DISPONIBLE) return
    // `SessionManager` no es observable: sin el estado el interruptor no se movería.
    var activada by remember { mutableStateOf(SessionManager.huellaActivada) }
    var avisoHuella by remember { mutableStateOf<String?>(null) }
    var ocupada by remember { mutableStateOf(false) }
    val alternar = alternar@{
        if (ocupada) return@alternar
        if (activada) {
            SessionManager.huellaActivada = false
            activada = false
            avisoHuella = null
        } else if (estado == EstadoDeHuella.SIN_REGISTRAR) {
            avisoHuella = "Registra una huella en los ajustes de este teléfono y vuelve."
        } else {
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
        // Lo que la fila no alcanza a decir: el aviso, o el alcance exacto (tapa la app, NO cifra la
        // sesión: por eso la captura de SMS sigue sola).
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
