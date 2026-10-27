package com.jvillada.movi.ui.auth

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.primeruso.GuiaDePrimerUso
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Campo

@Composable
fun RegisterScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (loading) return
        if (name.isBlank() || email.isBlank()) {
            error = "Nombre y correo requeridos"
            return
        }
        // El mínimo sale de PasswordPolicy (:core), el mismo objeto que usa el servidor para
        // validar. Antes había un `6` escrito a mano acá y otro en AuthRoutes.kt: mover uno no
        // movía el otro. Esto es cortesía de UI; la validación que manda es la del servidor.
        PasswordPolicy.problemWith(password)?.let {
            error = PasswordPolicy.messageFor(it)
            return
        }
        loading = true; error = null
        focusManager.clearFocus()
        coroutine.launch {
            runCatching {
                Repositories.wallets.register(RegisterRequest(email.trim(), name.trim(), password))
            }.onSuccess { resp ->
                SessionManager.save(resp.token, resp.userId, resp.name, resp.email)
                loading = false
                // Una cuenta recién creada no tiene nada: la guía de primer uso la acompaña paso a
                // paso. Ya se sabe que va, así que el Inicio no tiene que volver a preguntarlo.
                GuiaDePrimerUso.abrir()
                onNavigate(Screen.PrimerUso)
            }.onFailure {
                // Ver AuthErrors.kt. El 409 se detectaba buscando "409" DENTRO del texto del
                // error, que además de frágil solo acertaba de casualidad: sin `expectSuccess`,
                // lo que llegaba acá era el fallo de deserializar un cuerpo de texto, y ese
                // mensaje no tenía por qué mencionar el código. Ahora el código es un dato.
                error = mensajeDeRegistro(it)
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).padding(Espacio.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Crear cuenta", style = Movi.textos.titular, color = Movi.colores.texto)
        Text("Ingresa tus datos para empezar", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xxl))

        Superficie(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Campo(
                rotulo = "Nombre",
                valor = name,
                alCambiar = { name = it },
                guia = "Tu nombre",
                teclado = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                acciones = KeyboardActions(onNext = { emailFocus.requestFocus() }),
            )
            Spacer(Modifier.height(Espacio.s))
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
                foco = emailFocus,
            )
            Spacer(Modifier.height(Espacio.s))
            Campo(
                rotulo = "Contraseña",
                valor = password,
                alCambiar = { password = it },
                guia = "•".repeat(PasswordPolicy.MIN_LENGTH),
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

            Spacer(Modifier.height(Espacio.l))
            Boton("Crear cuenta", onClick = { submit() }, cargando = loading, anchoCompleto = true)
        }

        Spacer(Modifier.height(Espacio.m))
        Boton("¿Ya tienes cuenta? Entra", onClick = { onNavigate(Screen.Login) }, variante = VarianteDeBoton.Fantasma)
    }
}
