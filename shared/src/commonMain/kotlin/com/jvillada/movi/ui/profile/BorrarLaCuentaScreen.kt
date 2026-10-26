package com.jvillada.movi.ui.profile

import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Espacio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.BorrarLaCuentaRequest
import com.jvillada.movi.shared.model.PALABRA_PARA_BORRAR_LA_CUENTA
import com.jvillada.movi.shared.model.confirmaElBorrado
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.auth.AuthField
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import kotlinx.coroutines.launch

/** El título de la pantalla y el rótulo de la fila de Ajustes que la abre: el mismo, como toda ficha. */
const val BORRAR_MI_CUENTA: String = "Borrar mi cuenta"

/** El botón que borra. Dice «para siempre» porque no hay papelera ni forma de deshacerlo. */
const val BORRAR_PARA_SIEMPRE: String = "Borrar mi cuenta para siempre"

internal const val TAG_CONTRASENA_PARA_BORRAR = "borrar-cuenta:contrasena"
internal const val TAG_CONFIRMACION_PARA_BORRAR = "borrar-cuenta:confirmacion"
internal const val TAG_BOTON_BORRAR = "borrar-cuenta:boton"

/**
 * Lo que se borra, en el orden en que la gente lo piensa: primero la plata, después lo que no es
 * plata pero es suyo. Lo dice entero —incluidos los documentos y las personas y comercios— porque
 * es lo que alguien no espera que se vaya con «la cuenta».
 */
internal val LO_QUE_SE_BORRA: List<String> = listOf(
    "Tus cuentas, movimientos, créditos, tarjetas, recurrentes y presupuestos",
    "Los documentos que guardaste en Movi (extractos, comprobantes, escrituras) y lo que Movi leyó de ellos",
    "Tus personas y comercios",
    "Los mensajes, notificaciones y correos del banco que llegaron a Movi",
    "Tus conversaciones con Movi AI y lo que recuerda de ti",
    "Los enlaces que compartiste y las sesiones abiertas en todos tus aparatos",
)

/**
 * **¿Se puede tocar el botón?** Con la contraseña escrita, la palabra [PALABRA_PARA_BORRAR_LA_CUENTA]
 * en la casilla y nada en vuelo. Pura para poder fijarla sin pantalla; la palabra la decide
 * [confirmaElBorrado], la misma regla que vuelve a exigir el server.
 */
internal fun sePuedeBorrarLaCuenta(contrasena: String, confirmacion: String, borrando: Boolean): Boolean =
    contrasena.isNotEmpty() && confirmaElBorrado(confirmacion) && !borrando

/**
 * El mensaje de un intento que el server rechazó. La contraseña mala se dice tal cual: es el caso de
 * todos los días (se equivocó al escribirla) y no hay oráculo que cuidar, porque quien pide ya está
 * adentro de la cuenta.
 */
internal fun mensajeDelRechazo(error: Throwable): String = when ((error as? ApiException)?.status) {
    403 -> "La contraseña no coincide. No se borró nada."
    429 -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."
    400 -> "Escribe $PALABRA_PARA_BORRAR_LA_CUENTA para confirmar."
    else -> "No pudimos borrar tu cuenta. Revisa tu conexión e inténtalo de nuevo. No se borró nada."
}

/**
 * # Borrar mi cuenta
 *
 * Se llega desde Ajustes (el avatar), abajo de todo. Tres pasos, en el orden en que tienen que pasar:
 *
 * 1. **«Descargar tus datos»** primero, con el mismo ZIP de Perfil: después ya no se puede.
 * 2. **Qué se borra**, entero ([LO_QUE_SE_BORRA]).
 * 3. **La contraseña y la palabra BORRAR.** El botón no se habilita sin las dos
 *    ([sePuedeBorrarLaCuenta]); con un token robado no alcanza, y un toque distraído tampoco.
 *
 * Con el borrado hecho, el aparato se limpia: todo lo local de la cuenta —también lo que no había
 * subido, que ya no tiene a dónde subir ([Repositories.olvidarTodoLoDe])— y después lo mismo que un
 * cierre de sesión ([SessionManager.clear]: la instantánea del Inicio, el widget, los cachés). App.kt
 * ve la sesión cerrada y vuelve a la entrada.
 */
@Composable
fun BorrarLaCuentaScreen(onNavigate: (Screen) -> Unit) {
    var contrasena by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var borrando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var bajandoDatos by remember { mutableStateOf(false) }
    var errorDatos by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val sePuede = sePuedeBorrarLaCuenta(contrasena, confirmacion, borrando)

    fun borrar() {
        if (!sePuedeBorrarLaCuenta(contrasena, confirmacion, borrando)) return
        borrando = true
        error = null
        val uid = SessionManager.userId
        alcance.launch {
            runCatching { Repositories.wallets.borrarLaCuenta(BorrarLaCuentaRequest(contrasena, confirmacion.trim())) }
                .onSuccess {
                    uid?.let { Repositories.olvidarTodoLoDe(it) }
                    SessionManager.clear()
                    onNavigate(Screen.Login)
                }
                .onFailure {
                    borrando = false
                    error = mensajeDelRechazo(it)
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(title = BORRAR_MI_CUENTA, leading = HeaderLeading.Back(fallback = Screen.Mas))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Movi.espacios.amplio),
        ) {
            Text(
                "Borrar tu cuenta borra todo lo que Movi guarda de ti, en todos tus aparatos. No se puede deshacer.",
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.padding(top = Movi.espacios.corto, bottom = Movi.espacios.amplio),
            )

            // 1 — Antes, la descarga: después ya no hay de dónde sacarla.
            Seccion(titulo = "Antes, descarga tus datos")
            Superficie(relleno = RellenoDeSuperficie.Lista) {
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
            }
            errorDatos?.let {
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(top = Espacio.xs))
            }

            // 2 — Qué se borra, entero.
            Spacer(Modifier.height(Medida.entreSecciones))
            Seccion(titulo = "Qué se borra")
            Superficie(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                    LO_QUE_SE_BORRA.forEach { linea ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                            Text("•", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                            Text(linea, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                        }
                    }
                    Text(
                        "Todo, y de una vez: no queda una copia guardada ni una papelera.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }

            // 3 — La contraseña y la palabra.
            Spacer(Modifier.height(Medida.entreSecciones))
            Seccion(titulo = "Confirma que eres tú")
            Text("Tu contraseña", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(bottom = Espacio.xs))
            AuthField(
                value = contrasena,
                onChange = { contrasena = it; error = null },
                placeholder = "La que usas para entrar",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.testTag(TAG_CONTRASENA_PARA_BORRAR),
            )
            Spacer(Modifier.height(Movi.espacios.medio))
            Text(
                "Escribe $PALABRA_PARA_BORRAR_LA_CUENTA para confirmar",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.padding(bottom = Espacio.xs),
            )
            AuthField(
                value = confirmacion,
                onChange = { confirmacion = it; error = null },
                placeholder = PALABRA_PARA_BORRAR_LA_CUENTA,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                modifier = Modifier.testTag(TAG_CONFIRMACION_PARA_BORRAR),
            )

            error?.let {
                Spacer(Modifier.height(Movi.espacios.corto))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }

            Spacer(Modifier.height(Movi.espacios.margen))
            Boton(
                if (borrando) "Borrando…" else BORRAR_PARA_SIEMPRE,
                onClick = ::borrar,
                variante = VarianteDeBoton.Peligro,
                habilitado = sePuede,
                anchoCompleto = true,
                modifier = Modifier.testTag(TAG_BOTON_BORRAR),
            )
            Spacer(Modifier.height(Movi.espacios.seccion))
        }
    }
}
