package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import com.jvillada.movi.ui.components.SubirAlRetocarLaPestana
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.components.PanelDeTablero
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DeleteForever
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.data.EXPLICACION_HUELLA
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.data.motivoDeLaHuella
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.siElCorteCaeEnFinDeSemanaDe
import com.jvillada.movi.theme.Espacio
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
import com.jvillada.movi.ui.components.inicialesDelUsuario
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
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
import com.jvillada.movi.ui.profile.INICIO_DEL_PERIODO
import com.jvillada.movi.ui.profile.PeriodoSheet
import com.jvillada.movi.ui.primeruso.DireccionDeMovi
import com.jvillada.movi.ui.primeruso.rememberDireccionDeMovi
import com.jvillada.movi.ui.profile.AHORRO_DE_CADA_PERIODO
import com.jvillada.movi.ui.profile.AhorroDelPeriodoSheet
import com.jvillada.movi.ui.profile.guardarElAhorroDelPeriodo
import com.jvillada.movi.ui.profile.textoDelAhorro
import com.jvillada.movi.shared.model.ahorroDelPeriodo
import com.jvillada.movi.ui.profile.guardarElInicioDelMes
import com.jvillada.movi.ui.profile.textoDelInicioDelMes
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco
import com.jvillada.movi.ui.primeruso.rememberPrimerosPasos
import com.jvillada.movi.ui.primeruso.modoDeCapturaDe
import com.jvillada.movi.shared.model.pasosQueFaltan
import kotlinx.coroutines.launch
import com.jvillada.movi.platform.elLectorDePantallaLeeLaApp
import com.jvillada.movi.platform.notaDelLectorDePantalla

/**
 * Lo que se llamaba la pantalla que abría el avatar. Vuelta 38: sus secciones viven en «Tú»
 * ([TITULO_DE_TU]); el nombre queda para la búsqueda («ajustes» encuentra Tú) y lo que lo cite.
 */
const val TITULO_DE_AJUSTES: String = "Ajustes"

/** La tarjeta del perfil, arriba de todo en Ajustes. Para las pruebas. */
const val TAG_TARJETA_DEL_PERFIL: String = "ajustes:tarjeta-del-perfil"

/** Lo que dice la fila que cierra la sesión de este aparato. */
const val CERRAR_SESION: String = "Cerrar sesión"

/**
 * Las secciones de Ajustes, en el orden en que se ven (en «Tú», debajo de «Lo tuyo»). «Entrar» (la huella) solo existe donde hay
 * lector, y «Administración» solo para quien administra el Inicio; las demás están siempre.
 */
val SECCIONES_DE_AJUSTES: List<String> =
    listOf("Tu plata", "Movi", "Cómo se ve", "Avisos", "Tus datos", "Entrar", "Cuenta", "Administración")

/**
 * # «Tú» — la cuarta pestaña (vuelta 38), con todo lo que era Ajustes
 *
 * **Vuelta 38 (la opción 3):** la pestaña **Tú** es el centro de lo personal. Arriba, la tarjeta del
 * perfil (avatar, nombre y correo); después «Lo tuyo» ([LoTuyo]: Tu patrimonio con la cifra neta,
 * Personas con su saldo y Cuadre de saldos); y debajo, **las secciones de Ajustes tal cual**, en
 * `Fila`s del sistema. Es la raíz de su pestaña: sin avatar ni flecha. En pantalla ancha, lo tuyo a la
 * izquierda y los ajustes a la derecha ([tuEnDosColumnas]). `Screen.Mas` (Ajustes) lleva acá.
 *
 * Lo que sigue es la historia de Ajustes, que vale igual para sus secciones:
 *
 * **Vuelta de diseño 3: una sola pantalla.** Hasta acá había dos para lo mismo: el avatar abría
 * «Ajustes», una cuadrícula de ocho íconos y un botón rojo «Borrar mi cuenta», y la configuración de
 * verdad —la contraseña, el inicio del mes, cómo se ve, los avisos, descargar los datos, cerrar la
 * sesión— estaba escondida detrás del primer ícono, «Perfil». Ahora, como en las apps de plata que
 * se usan bien (Revolut, Nubank): arriba la tarjeta del perfil y debajo las secciones, cada una con
 * su [Seccion] y sus [Fila]s, en este orden:
 *
 * - **Tu plata**: Inicio del período, Ahorro de cada período, Categorías, Captura (o Mensajes) del banco
 *   y, donde no se leen SMS, «Recibe tus alertas por correo»;
 * - **Movi**: Movi AI, Lo que Movi sabe de ti, Editar inicio (y Primeros pasos, mientras falte algo);
 * - **Cómo se ve**: «Ocultar los saldos» (de este aparato). El tema se fue en la vuelta 29: la opción 3
 *   es clara y nada más;
 * - **Avisos**: las notificaciones con un solo «Activar avisos», cuántos días antes avisar y el resumen por correo;
 * - **Tus datos**: Documentos, Descargar tus datos, Compartir;
 * - **Entrar** (solo con lector de huella) y **Cuenta**: la contraseña y cerrar la sesión;
 * - y al final, aparte y sin relleno rojo, «Borrar mi cuenta».
 *
 * **Vuelta 31: más corta y con íconos.** Cada fila lleva su [IconoEnCirculo] ([IconoDeAjuste]); el tema
 * pasó de tres filas a una; «Personas y comercios» quedó solo en Espacios (su pestaña); y «Inicio del
 * mes» se llama «Inicio del período», como en el resto de la app.
 *
 * `Screen.Profile` dejó de existir: quien navegaba a Perfil llega acá (el destino SDUI `"profile"`
 * también). **Es una reubicación**: cada interruptor y cada acción usa la MISMA lógica y el mismo
 * store que tenía en Perfil (`SaldosOcultosStore`, `updateUserProfile`, las hojas de
 * `ui/profile/`).
 *
 * Sigue llamándose `MasScreen` porque las pruebas y las pilas ya la nombran así; la pinta
 * `Screen.Tu` (y `Screen.Mas`, que lleva acá). Lo que el dueño ve es «Tú».
 */
@Composable
fun MasScreen(
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit = {},
    /** ¿Este aparato lee los SMS del banco? Donde no (la web, iOS), «Recibe tus alertas por correo». */
    esAndroid: Boolean = isAndroid,
) {
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
    var showAhorro by remember { mutableStateOf(false) }
    var showCorreo by remember { mutableStateOf(false) }
    var guardandoAhorro by remember { mutableStateOf(false) }
    var errorAhorro by remember { mutableStateOf<String?>(null) }
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

    // Ola B, tarea 7: «Primeros pasos» solo mientras falte algún paso. Vuelta 54: la lista única
    // (`primerosPasos`, la misma de la guía y de la fila del Inicio), leída del caché del Inicio: sin
    // datos, no se afirma que falte nada. Sigue aunque el dueño haya ocultado la fila del Inicio.
    val guiaIncompleta = rememberPrimerosPasos(DashboardDataCache.data, modoDeCapturaDe(esAndroid))?.let { pasosQueFaltan(it) > 0 } == true

    // Vuelta 31: la dirección de Movi para reenviar los correos del banco, solo donde no se leen SMS.
    val direccionDeMovi = if (!esAndroid) rememberDireccionDeMovi() else remember { DireccionDeMovi() }

    // Vuelta 38: «Tú» es la raíz de su pestaña. Re-tocarla sube al principio (las dos columnas en la
    // pantalla ancha), y la rueda del mouse sobre los márgenes mueve la lista.
    val estadoDeLoTuyo = rememberLazyListState()
    val estadoDeLosAjustes = rememberLazyListState()
    ScrollDesdeLosMargenes(estadoDeLoTuyo)
    SubirAlRetocarLaPestana(estadoDeLoTuyo, estadoDeLosAjustes)

    // Lo tuyo: quién eres, tu patrimonio, las personas y el cuadre. En dos columnas va a la izquierda.
    val loTuyo: LazyListScope.() -> Unit = {
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

        // Vuelta 38: lo que eran las pestañas Patrimonio y Espacios, como acceso, con su cifra.
        item(key = "lo-tuyo") { LoTuyo(onNavigate) }
    }

    // Lo que era Ajustes, con sus secciones en el mismo orden, en dos partes: «Tu plata» y «Movi»
    // van debajo de lo tuyo, y desde «Cómo se ve» lo demás. En dos columnas, la primera parte va a la
    // izquierda y la segunda a la derecha, para que las dos columnas terminen parejas (revisión de la
    // vuelta 38: con todos los ajustes a la derecha, la izquierda acababa a los 390 px).
    val ajustesDeLaPlata: LazyListScope.() -> Unit = {
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
                        titulo = INICIO_DEL_PERIODO,
                        inicio = { IconoDeAjuste(Icons.Rounded.CalendarMonth) },
                        subtitulo = cutoff?.let { d ->
                            textoDelInicioDelMes(d, siElCorteCaeEnFinDeSemanaDe(profile?.periodWeekendRule))
                        } ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                        final = if (cutoff != null) ({ Chevron() }) else null,
                        onClick = if (cutoff == null) null else ({ errorPeriodo = null; showPeriodo = true }),
                    )
                    Separador()
                    // Lo que «Te quedan» resta antes de decirte cuánto puedes gastar. Mismo
                    // cuidado que «Inicio del período»: sin perfil no dice «No apartas nada».
                    val ahorro = profile?.ahorroDelPeriodo()
                    Fila(
                        titulo = AHORRO_DE_CADA_PERIODO,
                        inicio = { IconoDeAjuste(Icons.Rounded.Savings) },
                        subtitulo = ahorro?.let(::textoDelAhorro) ?: if (falloElPerfil) NO_PUDIMOS_LEERLO else CARGANDO,
                        final = if (ahorro != null) ({ Chevron() }) else null,
                        onClick = if (ahorro == null) null else ({ errorAhorro = null; showAhorro = true }),
                    )
                    Separador()
                    FilaQueLleva("Categorías", Icons.Rounded.Category, Screen.Categorias, onNavigate)
                    // Vuelta 31: «Personas y comercios» vive en Espacios —su pestaña, la de lo que
                    // tienes con los demás, y donde se elige con quién se comparte—, no también acá.
                    Separador()
                    // «Captura del banco» en Android, «Mensajes del banco» donde no hay captura.
                    FilaQueLleva(
                        tituloDeCapturaDelBanco,
                        if (esAndroid) Icons.Rounded.Sms else Icons.Rounded.Inbox,
                        Screen.CapturaDelBanco,
                        onNavigate,
                    )
                    // Donde no se leen SMS, la captura es reenviar los correos del banco: la
                    // dirección y el paso a paso, con la misma pieza de la guía de primer uso.
                    if (!esAndroid) {
                        Separador()
                        FilaDeAlertasPorCorreo(direccionDeMovi, onAbrir = { showCorreo = true })
                    }
                }
            }
        }

        // ── Movi ─────────────────────────────────────────────────────────────────────
        item(key = "movi") {
            Column {
                Seccion(titulo = "Movi")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    FilaQueLleva("Movi AI", Icons.Rounded.AutoAwesome, Screen.AIChat(), onNavigate)
                    Separador()
                    FilaQueLleva(TITULO_LO_QUE_MOVI_SABE, Icons.Rounded.Psychology, Screen.LoQueMoviSabe, onNavigate)
                    Separador()
                    // Ola 3 · «Editar inicio»: es del dueño (se guarda en el server).
                    FilaQueLleva(
                        TITULO_EDITAR_INICIO,
                        Icons.Rounded.DashboardCustomize,
                        Screen.EditarInicio,
                        onNavigate,
                        subtitulo = "Qué secciones ves en Hoy y en qué orden",
                    )
                    if (guiaIncompleta) {
                        Separador()
                        FilaQueLleva("Primeros pasos", Icons.Rounded.Flag, Screen.PrimerosPasos, onNavigate)
                    }
                }
            }
        }
    }
    val ajustesDelAparatoYLaCuenta: LazyListScope.() -> Unit = {
        // ── Cómo se ve: es de ESTE aparato, no del dueño ──
        // Vuelta 29: sin la fila del tema. La opción 3 es clara y nada más, así que queda
        // solo «Ocultar los saldos».
        item(key = "como-se-ve") {
            Column {
                Seccion(titulo = "Cómo se ve")
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    // «Ocultar los saldos»: lo mismo que el ojo de Hoy. También de este aparato.
                    val ocultos = LocalSaldosOcultos.current
                    Fila(
                        titulo = ROTULO_OCULTAR_LOS_SALDOS,
                        subtitulo = "Tus cifras se ven como $PLATA_OCULTA en este aparato",
                        inicio = { IconoDeAjuste(Icons.Rounded.VisibilityOff) },
                        final = { Interruptor(ocultos, onCambio = { SaldosOcultosStore.poner(it) }) },
                        onClick = { SaldosOcultosStore.alternar() },
                    )
                }
                // Vuelta 55: en la web un lector de pantalla no lee la app (es un canvas); se dice.
                NotaDeSeccion(notaDelLectorDePantalla(elLectorDePantallaLeeLaApp))
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
            // Qué canales tiene el server para los recordatorios (correo): una lectura por sesión.
            LaunchedEffect(Unit) { ReminderChannelsCache.cargar() }
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
                    // Las notificaciones push del navegador, donde se pueden pedir, con un solo
                    // «Activar avisos» (vuelta 31), y cuántos días antes avisar.
                    FilasDeAvisos(
                        pushSoportado = PushOptIn.supported,
                        pushStatus = pushStatus,
                        canales = ReminderChannelsCache.canales,
                        diasDeAviso = profile?.reminderLeadDays,
                        falloElPerfil = falloElPerfil,
                        onActivar = {
                            PushOptIn.enable()
                            refreshTick++
                        },
                        onDesactivar = {
                            PushOptIn.disable()
                            refreshTick++
                        },
                        onElegirDias = { errorAviso = null; showAviso = true },
                    )
                    Separador()
                    // «Cerró tu período» por correo: de la cuenta (sale del server), no del
                    // teléfono. Sin el perfil no se afirma si está encendido.
                    Fila(
                        titulo = "Resumen del período por correo",
                        inicio = { IconoDeAjuste(Icons.Rounded.Summarize) },
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
                        // Encendido, la fila ya dice cuándo llega: la nota lo repetía.
                        true -> null
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
                    FilaQueLleva("Documentos", Icons.Rounded.Description, Screen.Documentos, onNavigate)
                    Separador()
                    Fila(
                        titulo = DESCARGAR_TUS_DATOS,
                        inicio = { IconoDeAjuste(Icons.Rounded.Download) },
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
                    Separador()
                    // El enlace de solo lectura para Caro o un asesor.
                    FilaQueLleva("Compartir", Icons.Rounded.Share, Screen.Compartir, onNavigate)
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
                    Fila(
                        titulo = "Cambiar contraseña",
                        inicio = { IconoDeAjuste(Icons.Rounded.Password) },
                        final = { Chevron() },
                        onClick = { showChangePassword = true },
                    )
                    Separador()
                    Fila(
                        titulo = CERRAR_SESION,
                        inicio = { IconoDeAjuste(Icons.AutoMirrored.Rounded.Logout) },
                        onClick = onLogout,
                    )
                    Separador()
                    // Para el teléfono perdido: pide confirmación antes.
                    Fila(
                        titulo = CERRAR_SESION_EN_TODOS,
                        inicio = { IconoDeAjuste(Icons.Rounded.Devices) },
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
                            Icons.Rounded.Tune,
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

    // Lo que queda libre abajo: el aire de una sección más un margen, como en las demás listas.
    val alPie = Medida.botonFlotante + Medida.entreSecciones
    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // En pantalla ancha, lo tuyo a la izquierda y los ajustes a la derecha (las mismas columnas que
        // Patrimonio, ver [tuEnDosColumnas]); en el teléfono y en una ventana mediana, una lista.
        PanelDeTablero(enDosColumnas = ::tuEnDosColumnas) { dosColumnas ->
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(
                title = TITULO_DE_TU,
                // La raíz de su pestaña, sin avatar: el avatar llevaría a esta misma pantalla, y la
                // tarjeta de arriba ya es el avatar con el nombre y el correo.
                leading = HeaderLeading.Ninguno,
            )
            if (dosColumnas) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = estadoDeLoTuyo,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LO_TUYO),
                        contentPadding = PaddingValues(start = Medida.margenDePantalla, end = Medida.margenDePantalla / 2, top = Medida.dentroDeSeccion, bottom = alPie),
                        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                    ) {
                        loTuyo()
                        ajustesDeLaPlata()
                    }
                    LazyColumn(
                        state = estadoDeLosAjustes,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_COLUMNA_DE_LOS_AJUSTES),
                        contentPadding = PaddingValues(start = Medida.margenDePantalla / 2, end = Medida.margenDePantalla, top = Medida.dentroDeSeccion, bottom = alPie),
                        verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                        content = ajustesDelAparatoYLaCuenta,
                    )
                }
            } else {
                LazyColumn(
                    state = estadoDeLoTuyo,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Medida.margenDePantalla,
                        end = Medida.margenDePantalla,
                        top = Medida.dentroDeSeccion,
                        bottom = alPie,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                ) {
                    loTuyo()
                    ajustesDeLaPlata()
                    ajustesDelAparatoYLaCuenta()
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
        if (showCorreo) {
            AtrasCierraEstaHoja { showCorreo = false }
            HojaDeAlertasPorCorreo(direccionDeMovi, onDismiss = { showCorreo = false })
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
        if (showAhorro && perfil != null) {
            AtrasCierraEstaHoja { showAhorro = false; errorAhorro = null }
            AhorroDelPeriodoSheet(
                actual = perfil.ahorroDelPeriodo(),
                saving = guardandoAhorro,
                error = errorAhorro,
                onDismiss = { showAhorro = false; errorAhorro = null },
                onSave = { elegido ->
                    guardandoAhorro = true
                    errorAhorro = null
                    alcance.launch {
                        runCatching { guardarElAhorroDelPeriodo(elegido) }
                            .onSuccess {
                                guardandoAhorro = false
                                showAhorro = false
                                // «Te quedan» cambia: guardar el perfil ya invalida el Inicio
                                // (`InvalidaElInicioAlEscribir`), así que Hoy y Plan lo vuelven a pedir.
                                profileReloadKey++
                            }
                            .onFailure {
                                guardandoAhorro = false
                                errorAhorro = "No pudimos guardar el cambio. Revisa tu conexión e inténtalo de nuevo."
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

/** Una fila que abre otra pantalla: su ícono, el título (y el subtítulo) y la flecha de «esto se abre». */
@Composable
private fun FilaQueLleva(
    titulo: String,
    icono: ImageVector,
    destino: Screen,
    onNavigate: (Screen) -> Unit,
    subtitulo: String? = null,
) {
    Fila(
        titulo = titulo,
        subtitulo = subtitulo,
        inicio = { IconoDeAjuste(icono) },
        final = { Chevron() },
        onClick = { onNavigate(destino) },
    )
}

/**
 * **El ícono de una fila de Ajustes** (vuelta 31): cada fila lleva el suyo, como las listas de Revolut
 * y Nubank, en un [IconoEnCirculo] neutro —ninguna fila de Ajustes es un estado ni una propuesta—.
 * Solo «Borrar mi cuenta» va en peligro.
 */
@Composable
private fun IconoDeAjuste(icono: ImageVector) {
    IconoEnCirculo(icono, tono = TonoDeIcono.Neutro)
}

/** Quién eres: el avatar, el nombre y el correo, con «Editar» (alias y color). */
@Composable
private fun TarjetaDelPerfil(profile: UserProfile?, onEditar: () -> Unit) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_PERFIL)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            // F42 · F46: mientras `profile` no llegó se usa lo que SessionManager tenía del login.
            val displayName = profile?.name ?: SessionManager.userName ?: "Usuario"
            val avatarColor = avatarColorOrDefault(profile?.avatarColor ?: SessionManager.avatarColor)
            Box(
                modifier = Modifier
                    .size(Medida.avatarGrande)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                // Vuelta 41: las mismas iniciales que el avatar del Inicio (`inicialesDelUsuario`).
                Text(inicialesDelUsuario(displayName), style = Movi.textos.tituloMedio, color = Movi.colores.sobreAvatar)
            }
            Column(modifier = Modifier.weight(1f)) {
                // Vuelta 29: el nombre en `saludo` (17/700 a la vista), con el correo debajo, como en la opción 3.
                Text(displayName, style = Movi.textos.saludo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                inicio = { IconoDeAjuste(Icons.Rounded.Fingerprint) },
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
