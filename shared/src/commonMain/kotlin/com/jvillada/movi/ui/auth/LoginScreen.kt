package com.jvillada.movi.ui.auth

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.ArranqueDeSesion
import com.jvillada.movi.data.EXPLICACION_HUELLA
import com.jvillada.movi.data.MENSAJE_SIN_REGISTRAR
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.avisoDeLaSesionRechazada
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.decidirArranque
import com.jvillada.movi.data.motivoDeLaHuella
import com.jvillada.movi.data.ofrecerHuellaTrasEntrar
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Campo

@Composable
fun LoginScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var email by remember { mutableStateOf(SessionManager.rememberedEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Si llegó acá porque el server rechazó la sesión (y no porque salió), se le dice por qué: con
    // el token muerto del 26-oct la app callaba y el dueño creía estar adentro.
    var notice by remember {
        mutableStateOf(avisoDeLaSesionRechazada(SessionManager.sesionRechazada, conCapturaDelBanco = isAndroid))
    }

    // «Entrar con huella» — solo Android tiene lector; en iOS y la web esto es `null` y todo lo
    // que sigue queda apagado, sin una sola diferencia con la pantalla de antes.
    val huella = Huella.deEsteAparato()
    // true mientras se le está ofreciendo activar la huella recién entrado.
    var ofreciendo by remember { mutableStateOf(false) }
    var pidiendoHuella by remember { mutableStateOf(false) }

    /**
     * **La puerta.** Con el interruptor prendido, App.kt arranca en esta pantalla aunque la sesión
     * esté viva: el dedo no abre ninguna caja fuerte, solo deja pasar. Por eso el éxito navega al
     * Inicio sin guardar nada — la sesión ya estaba ahí, y siguió ahí todo el tiempo.
     *
     * Lo llaman el arranque y el enlace de reintento de más abajo.
     */
    fun desbloquear() {
        val lector = huella ?: return
        if (pidiendoHuella) return
        pidiendoHuella = true
        error = null
        notice = null
        lector.pedir(PropositoDeHuella.ENTRAR) { resultado ->
            pidiendoHuella = false
            // Ningún resultado borra la sesión: fallar la huella no puede costarle nada, porque
            // la huella nunca custodió nada. Se dice el motivo y quedan las dos salidas — el dedo
            // otra vez, o la contraseña, que sigue debajo.
            notice = motivoDeLaHuella(resultado)
            if (resultado == ResultadoDeHuella.EXITO) onNavigate(Screen.Dashboard)
        }
    }

    // El arranque: se evalúa UNA vez por visita a esta pantalla.
    var arranqueEvaluado by remember { mutableStateOf(false) }
    LaunchedEffect(huella) {
        if (arranqueEvaluado) return@LaunchedEffect
        arranqueEvaluado = true
        val lector = huella ?: return@LaunchedEffect
        when (
            decidirArranque(
                haySesion = SessionManager.isLoggedIn,
                huellaActivada = SessionManager.huellaActivada,
                estado = lector.estado(),
            )
        ) {
            // La puerta no se interpone: esta pantalla es el login de siempre.
            ArranqueDeSesion.SIN_PUERTA -> Unit
            ArranqueDeSesion.PEDIR_HUELLA -> desbloquear()
            // El interruptor sigue prendido y la sesión intacta: lo único que falta es un dedo que
            // este teléfono ya no puede leer. Se dice, y el formulario de abajo lo deja entrar.
            ArranqueDeSesion.PEDIR_CONTRASENA -> notice = MENSAJE_SIN_REGISTRAR
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
                if (vale) ofreciendo = true else onNavigate(Screen.Dashboard)
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
        modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).padding(Espacio.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // El logotipo de la entrada: `portada`, el estilo de una pantalla de una sola cosa (era un
        // tamaño suelto de 32; `titular`, 19, lo apagaba y `cifra` es para montos).
        Text("Movi", style = Movi.textos.portada, color = Movi.colores.texto)
        Text("Finanzas personales", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xxxl))

        if (ofreciendo) {
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
                        // Se pide el dedo una vez antes de prender el interruptor: prenderlo sin
                        // comprobar que el lector de verdad lo acepta sería dejarlo con una puerta
                        // que recién falla la próxima vez que abra la app.
                        lector.pedir(PropositoDeHuella.ACTIVAR) { resultado ->
                            pidiendoHuella = false
                            if (resultado == ResultadoDeHuella.EXITO) {
                                SessionManager.huellaActivada = true
                                onNavigate(Screen.Dashboard)
                            } else {
                                // No se entra a la fuerza ni se sigue de largo callado: la
                                // sesión ya está abierta, así que puede reintentar o seguir.
                                avisoDelOfrecimiento = motivoDeLaHuella(resultado)
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

        Superficie(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Campo(
                rotulo = "Correo",
                valor = email,
                alCambiar = { email = it },
                guia = "tu@correo.com",
                teclado = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                acciones = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            Spacer(Modifier.height(Espacio.m))
            Campo(
                rotulo = "Contraseña",
                valor = password,
                alCambiar = { password = it },
                guia = "••••••",   // login: no se insinúa longitud, la cuenta puede ser vieja
                secreto = true,
                teclado = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                acciones = KeyboardActions(onDone = { submit() }),
                foco = passwordFocus,
            )

            error?.let {
                Spacer(Modifier.height(Espacio.s))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }
            notice?.let {
                Spacer(Modifier.height(Espacio.s))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }

            // El reintento. Cancelar el prompt no deja al dueño encerrado en el formulario hasta
            // el próximo arranque: acá vuelve a pedirlo cuando quiera.
            if (huella != null && SessionManager.huellaActivada && SessionManager.isLoggedIn) {
                Spacer(Modifier.height(Espacio.s))
                Boton(
                    if (pidiendoHuella) "Esperando tu huella…" else "Entrar con huella",
                    onClick = { desbloquear() },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = !pidiendoHuella,
                )
            }

            Spacer(Modifier.height(Espacio.s))
            Boton("¿Olvidaste tu contraseña?", onClick = { requestReset() }, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)

            Spacer(Modifier.height(Espacio.l))
            Boton("Entrar", onClick = { submit() }, cargando = loading, anchoCompleto = true)
        }

        Spacer(Modifier.height(Espacio.m))
        Boton("¿No tienes cuenta? Regístrate", onClick = { onNavigate(Screen.Register) }, variante = VarianteDeBoton.Fantasma)
    }
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
    Superficie(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text(
            "Entra con tu huella la próxima vez",
            style = Movi.textos.titulo,
            color = Movi.colores.texto,
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(EXPLICACION_HUELLA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)

        aviso?.let {
            Spacer(Modifier.height(Espacio.s))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }

        Spacer(Modifier.height(Espacio.l))
        Boton(if (ocupado) "Esperando tu huella…" else "Activar", onClick = onActivar, habilitado = !ocupado, anchoCompleto = true)
        Spacer(Modifier.height(Espacio.xs))
        Boton("Ahora no", onClick = onAhoraNo, variante = VarianteDeBoton.Fantasma, anchoCompleto = true)
    }
}
