package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.ArranqueDeSesion
import com.jvillada.movi.data.EXPLICACION_HUELLA
import com.jvillada.movi.data.MENSAJE_SIN_REGISTRAR
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SesionGuardada
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.decidirArranque
import com.jvillada.movi.data.ofrecerHuellaTrasEntrar
import com.jvillada.movi.data.quePasaTrasLaHuella
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var email by remember { mutableStateOf(SessionManager.rememberedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    // «Entrar con huella» — solo Android tiene lector; en iOS y la web esto es `null` y todo lo
    // que sigue queda apagado, sin una sola diferencia con la pantalla de antes.
    val huella = Huella.deEsteAparato()
    // No `null` mientras se le está ofreciendo activar la huella recién entrado. Guarda la sesión
    // que acaba de abrir porque es justo lo que hay que cifrar si dice que sí.
    var ofrecimiento by remember { mutableStateOf<SesionGuardada?>(null) }
    var pidiendoHuella by remember { mutableStateOf(false) }

    /** Pide la huella y suelta la sesión guardada. Lo llaman el arranque y el enlace de reintento. */
    fun desbloquear() {
        val lector = huella ?: return
        if (pidiendoHuella) return
        pidiendoHuella = true
        error = null
        notice = null
        lector.abrir { resultado, sesion ->
            pidiendoHuella = false
            val que = quePasaTrasLaHuella(resultado, sesion)
            // Lo que ya no se puede abrir se borra ACÁ, y no en el próximo arranque: si no,
            // la app volvería a ofrecer una huella que no abre nada, para siempre.
            if (que.olvidarLoGuardado) lector.olvidar()
            notice = que.mensaje
            que.sesion?.let { abierta ->
                SessionManager.save(abierta.token, abierta.userId, abierta.nombre, abierta.correo)
                onNavigate(Screen.Dashboard)
            }
        }
    }

    // El arranque: se evalúa UNA vez por visita a esta pantalla. Volver acá tras un logout no
    // debe disparar el prompt —`clear()` ya olvidó lo guardado— y `decidirArranque` lo confirma.
    var arranqueEvaluado by remember { mutableStateOf(false) }
    LaunchedEffect(huella) {
        if (arranqueEvaluado) return@LaunchedEffect
        arranqueEvaluado = true
        val lector = huella ?: return@LaunchedEffect
        when (
            decidirArranque(
                sesionViva = SessionManager.isLoggedIn,
                huellaActivada = SessionManager.huellaActivada,
                haySesionGuardada = lector.haySesionGuardada(),
                estado = lector.estado(),
            )
        ) {
            // App.kt ya no habría mostrado esta pantalla con la sesión abierta; la rama existe
            // para que el `when` sea exhaustivo y no haya un `else` que tape un caso nuevo.
            ArranqueDeSesion.ENTRAR_DIRECTO -> Unit
            ArranqueDeSesion.PEDIR_HUELLA -> desbloquear()
            ArranqueDeSesion.PEDIR_CONTRASENA ->
                // Quedó algo guardado que este teléfono ya no puede abrir (le borraron las
                // huellas). Se olvida y se dice por qué, en vez de dejar un interruptor prendido
                // que no hace nada.
                if (SessionManager.huellaActivada) {
                    huella.olvidar()
                    notice = MENSAJE_SIN_REGISTRAR
                }
        }
    }

    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (loading) return
        loading = true
        error = null
        notice = null
        focusManager.clearFocus()
        coroutine.launch {
            runCatching {
                Repositories.wallets.login(LoginRequest(email.trim(), password))
            }.onSuccess { resp ->
                SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                loading = false
                // Android y nada más: en el resto `huella` es null y se entra como siempre.
                val lector = huella
                val vale = lector != null && ofrecerHuellaTrasEntrar(
                    estado = lector.estado(),
                    yaActivada = SessionManager.huellaActivada,
                    yaLoRechazo = SessionManager.huellaRechazada,
                )
                if (vale) {
                    ofrecimiento = SesionGuardada(resp.token, resp.userId, resp.name, resp.email)
                } else {
                    onNavigate(Screen.Dashboard)
                }
            }.onFailure {
                // Ver AuthErrors.kt: acá se decidía a ciegas que la culpa era de la contraseña,
                // pasara lo que pasara. Ahora el 401 —y solo el 401— dice eso.
                error = mensajeDeLogin(it)
                loading = false
            }
        }
    }

    /**
     * Pide el enlace de recuperación. El paso 2 (elegir la contraseña nueva) NO vive acá: el
     * enlace del correo abre la PWA en el navegador, que tiene el formulario. Duplicar ese
     * formulario en Compose obligaría a pegar el token a mano — peor experiencia y una segunda
     * copia de la misma pantalla. Acá está lo que sí hace falta desde el teléfono: pedirlo.
     */
    fun requestReset() {
        if (loading) return
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            error = "Escribe tu correo y vuelve a tocar «¿Olvidaste tu contraseña?»"
            return
        }
        loading = true
        error = null
        notice = null
        focusManager.clearFocus()
        coroutine.launch {
            runCatching {
                Repositories.wallets.requestPasswordReset(PasswordResetRequest(trimmed))
            }.onSuccess { status ->
                when (status) {
                    // 202 es idéntico exista o no el correo — la app no sabe (ni debe saber) cuál fue.
                    202  -> notice = "Si el correo está registrado, te enviamos un enlace. Ábrelo desde el correo para elegir una contraseña nueva."
                    // El servidor no tiene cómo mandar correo. Se dice, en vez de prometer un
                    // mensaje que nunca va a llegar.
                    503  -> error = "El envío de correo no está configurado en el servidor, así que no se puede recuperar la contraseña por ahí."
                    429  -> error = "Demasiados pedidos. Espera unos minutos."
                    else -> error = "No se pudo pedir el enlace ($status)"
                }
                loading = false
            }.onFailure {
                // Mismo problema que el login, al revés: esto afirmaba «no se pudo conectar»
                // aunque el servidor sí hubiera contestado y fallado por otra cosa.
                error = mensajeDeRecuperacion(it)
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Movi", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Movi.colores.texto)
        Text("Finanzas personales", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(40.dp))

        val paraOfrecer = ofrecimiento
        if (paraOfrecer != null) {
            // Ya entró: lo único que falta es si quiere que la próxima vez sea con la huella.
            // El formulario no se dibuja debajo — no hay nada más que escribir.
            var avisoDelOfrecimiento by remember { mutableStateOf<String?>(null) }
            OfrecimientoDeHuella(
                ocupado = pidiendoHuella,
                aviso = avisoDelOfrecimiento,
                onActivar = {
                    val lector = huella
                    if (lector != null && !pidiendoHuella) {
                        pidiendoHuella = true
                        avisoDelOfrecimiento = null
                        lector.guardar(paraOfrecer) { resultado ->
                            pidiendoHuella = false
                            if (resultado == ResultadoDeHuella.EXITO) {
                                onNavigate(Screen.Dashboard)
                            } else {
                                // No se entra a la fuerza ni se sigue de largo callado: la
                                // sesión ya está abierta, así que puede activar o seguir.
                                avisoDelOfrecimiento =
                                    "No se pudo activar en este teléfono. Puedes intentarlo otra vez o seguir sin huella."
                            }
                        }
                    }
                },
                onAhoraNo = {
                    SessionManager.huellaRechazada = true
                    onNavigate(Screen.Dashboard)
                },
            )
            return@Column
        }

        MinCard(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(20.dp)) {
            Text("Correo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = email,
                onChange = { email = it },
                placeholder = "tu@correo.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            Spacer(Modifier.height(16.dp))
            Text("Contraseña", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(bottom = 6.dp))
            AuthField(
                value = password,
                onChange = { password = it },
                placeholder = "••••••",   // login: no se insinúa longitud, la cuenta puede ser vieja
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(passwordFocus),
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }

            // El reintento. Cancelar el prompt no deja al dueño encerrado en el formulario hasta
            // el próximo arranque: acá vuelve a pedirlo cuando quiera.
            if (huella != null && SessionManager.huellaActivada && huella.haySesionGuardada()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (pidiendoHuella) "Esperando tu huella…" else "Entrar con huella",
                    style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium,
                    color = if (pidiendoHuella) Movi.colores.textoMedio else Movi.colores.marca,
                    modifier = Modifier.noRippleClickable { desbloquear() },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "¿Olvidaste tu contraseña?",
                style = Movi.textos.cuerpo, color = Movi.colores.marca,
                modifier = Modifier.noRippleClickable { requestReset() },
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (loading) Movi.colores.tarjeta else Movi.colores.marca)
                    .noRippleClickable { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Movi.colores.marca,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Entrar",
                        style = Movi.textos.titulo, fontWeight = FontWeight.SemiBold,
                        color = Movi.colores.fondo,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "¿No tienes cuenta? Regístrate",
            style = Movi.textos.cuerpo, color = Movi.colores.marca,
            modifier = Modifier.noRippleClickable { onNavigate(Screen.Register) }
        )
    }
}

@Composable
internal fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val showAsPassword = isPassword && !passwordVisible
    // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
    val campo = rememberCampoConSeleccion(value, onChange)
    BasicTextField(
        value = campo.valor,
        onValueChange = campo::alCambiar,
        textStyle = Movi.textos.titulo.copy(color = Movi.colores.texto),
        cursorBrush = SolidColor(Movi.colores.marca),
        singleLine = true,
        visualTransformation = if (showAsPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Movi.colores.tarjeta).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Movi.colores.textoApagado, style = Movi.textos.titulo)
                    inner()
                }
                if (isPassword) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        // `Rounded`, como el resto de la app: 33 íconos distintos en 63 usos. Estos dos
                        // eran los ÚNICOS `Outlined` del repo, y por eso se leían como de otro juego
                        // al lado de los checks y las equis: `Rounded` tiene extremos redondeados y
                        // trazos más suaves.
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = Movi.colores.textoMedio,
                        modifier = Modifier.size(20.dp)
                            .noRippleClickable { passwordVisible = !passwordVisible },
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
    )
}

internal fun Modifier.noRippleClickable(onClick: () -> Unit) = this.then(
    clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick,
    )
)

/**
 * **El ofrecimiento, justo después de entrar con la contraseña.**
 *
 * Dice en una línea qué se guarda, dónde, y —sobre todo— qué NO se guarda. La contraseña es lo
 * primero que alguien asume que una función así conserva, y es exactamente lo que Movi no hace.
 *
 * «Ahora no» no es un rechazo definitivo de nada: no vuelve a aparecer solo, pero el interruptor
 * de Perfil está siempre.
 */
@Composable
private fun OfrecimientoDeHuella(
    ocupado: Boolean,
    aviso: String?,
    onActivar: () -> Unit,
    onAhoraNo: () -> Unit,
) {
    MinCard(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(20.dp),
    ) {
        Text(
            "Entra con tu huella la próxima vez",
            style = Movi.textos.titulo, fontWeight = FontWeight.SemiBold,
            color = Movi.colores.texto,
        )
        Spacer(Modifier.height(8.dp))
        Text(EXPLICACION_HUELLA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)

        aviso?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (ocupado) Movi.colores.tarjeta else Movi.colores.marca)
                .noRippleClickable(onActivar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ocupado) "Esperando tu huella…" else "Activar",
                style = Movi.textos.titulo, fontWeight = FontWeight.SemiBold,
                color = if (ocupado) Movi.colores.textoMedio else Movi.colores.fondo,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Ahora no",
                style = Movi.textos.cuerpo, color = Movi.colores.textoMedio,
                modifier = Modifier.noRippleClickable(onAhoraNo),
            )
        }
    }
}
