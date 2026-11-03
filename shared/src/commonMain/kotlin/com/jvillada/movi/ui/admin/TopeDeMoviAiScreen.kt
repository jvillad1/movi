package com.jvillada.movi.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.jvillada.movi.shared.model.TOPE_DE_PREGUNTAS_POR_DEFECTO
import com.jvillada.movi.shared.model.TopeDeUnUsuario
import com.jvillada.movi.shared.model.TopesDeLaIa
import com.jvillada.movi.shared.model.conPuntosDeMiles
import com.jvillada.movi.shared.model.problemaDelTope
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.rememberListaConElTeclado

/**
 * # «Preguntas de Movi AI», solo admin (vuelta 106)
 *
 * Ajustes → Administración. El admin elige cuántas preguntas libres por mes tiene cada usuario —un
 * valor para todos y excepciones por usuario— sin tocar el código. Solo cuentan las que van al
 * modelo (las tres sugeridas no) y el admin no tiene tope. Lee y guarda con
 * `…/api/admin/ia/topes` ([MoviAiParaAdmin]); un no admin recibe 403 y la fila de Ajustes ni se pinta.
 */
@Composable
fun TopeDeMoviAiScreen(onNavigate: (Screen) -> Unit) {
    val alcance = rememberCoroutineScope()
    var intento by remember { mutableIntStateOf(0) }
    var topes by remember { mutableStateOf<TopesDeLaIa?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var global by remember { mutableStateOf("") }
    // A quién se le está poniendo una excepción, y con qué número.
    var elegido by remember { mutableStateOf<TopeDeUnUsuario?>(null) }
    var deElegido by remember { mutableStateOf("") }

    fun llego(nuevos: TopesDeLaIa) {
        topes = nuevos
        global = nuevos.global.toString()
    }

    LaunchedEffect(intento) {
        fallo = false
        runCatching { MoviAiParaAdmin.actual.topes() }.onSuccess(::llego).onFailure { fallo = true }
    }

    fun guardar(accion: suspend () -> TopesDeLaIa, alTerminar: () -> Unit = {}) {
        if (guardando) return
        guardando = true
        aviso = null
        alcance.launch {
            runCatching { accion() }
                .onSuccess { llego(it); alTerminar() }
                .onFailure { aviso = NO_SE_PUDO_GUARDAR_EL_TOPE }
            guardando = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).testTag(TAG_TOPE_DE_MOVI_AI)) {
        MinScreenHeader(title = TITULO_DEL_TOPE_DE_MOVI_AI, leading = HeaderLeading.Back(fallback = Screen.Tu))
        LazyColumn(
            state = rememberListaConElTeclado(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion),
            verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
        ) {
            val t = topes
            when {
                t == null && fallo -> item(key = "fallo") {
                    NoSePudoLeer("No pude leer el tope de Movi AI.", onReintentar = { intento++ })
                }
                t == null -> item(key = "cargando") { Column { repeat(3) { EsqueletoDeFila(conInicio = false) } } }
                else -> {
                    item(key = "global") {
                        Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                            Seccion(titulo = "Para todos")
                            Superficie {
                                Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                                    val numero = global.toIntOrNull()
                                    val error = errorDelTopeEscrito(global)
                                    Campo(
                                        valor = global,
                                        alCambiar = { global = it.filter(Char::isDigit).take(6) },
                                        rotulo = "Preguntas libres por mes",
                                        ayuda = ayudaDelGlobal(t),
                                        error = error,
                                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                    Boton(
                                        texto = "Guardar",
                                        onClick = { numero?.let { n -> guardar({ MoviAiParaAdmin.actual.guardarGlobal(n) }) } },
                                        habilitado = numero != null && error == null && numero != t.global,
                                        cargando = guardando && elegido == null,
                                        anchoCompleto = true,
                                    )
                                }
                            }
                            Text(QUE_CUENTA_PARA_EL_TOPE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            aviso?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                        }
                    }
                    item(key = "excepciones") {
                        Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                            Seccion(titulo = "Excepciones", conteo = t.excepciones.size.takeIf { it > 0 })
                            if (t.excepciones.isEmpty()) {
                                Text("Nadie tiene un número propio: a todos les toca el de arriba.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            } else {
                                Superficie(relleno = RellenoDeSuperficie.Lista) {
                                    t.excepciones.forEach { u ->
                                        Fila(
                                            titulo = u.correo.ifBlank { u.usuarioId },
                                            subtitulo = lineaDeLaExcepcion(u),
                                            final = {
                                                Boton(
                                                    texto = "Quitar",
                                                    onClick = { guardar({ MoviAiParaAdmin.actual.quitarExcepcion(u.usuarioId) }) },
                                                    variante = VarianteDeBoton.Fantasma,
                                                    habilitado = !guardando,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "agregar") {
                        Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                            Seccion(titulo = "Darle un número propio a alguien")
                            val sinExcepcion = t.usuarios.filter { it.tope == null }
                            val e = elegido
                            if (e != null) {
                                Superficie {
                                    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                                        val numero = deElegido.toIntOrNull()
                                        val error = errorDelTopeEscrito(deElegido)
                                        Campo(
                                            valor = deElegido,
                                            alCambiar = { deElegido = it.filter(Char::isDigit).take(6) },
                                            rotulo = "Preguntas por mes para ${e.correo.ifBlank { e.usuarioId }}",
                                            ayuda = lineaDeUnUsuarioDelTope(e),
                                            error = error,
                                            teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        )
                                        Boton(
                                            texto = "Guardar excepción",
                                            onClick = {
                                                numero?.let { n ->
                                                    guardar({ MoviAiParaAdmin.actual.guardarExcepcion(e.usuarioId, n) }) { elegido = null; deElegido = "" }
                                                }
                                            },
                                            variante = VarianteDeBoton.Secundario,
                                            habilitado = numero != null && error == null,
                                            cargando = guardando,
                                            anchoCompleto = true,
                                        )
                                        Boton(texto = "Cancelar", onClick = { elegido = null; deElegido = "" }, variante = VarianteDeBoton.Fantasma, anchoCompleto = true)
                                    }
                                }
                            } else if (sinExcepcion.isEmpty()) {
                                Text("Todos tienen ya su número.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            } else {
                                Superficie(relleno = RellenoDeSuperficie.Lista) {
                                    sinExcepcion.forEach { u ->
                                        Fila(
                                            titulo = u.correo.ifBlank { u.usuarioId },
                                            subtitulo = lineaDeUnUsuarioDelTope(u),
                                            onClick = { elegido = u; deElegido = t.global.toString() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Los textos, puros ────────────────────────────────────────────────────────

internal const val TITULO_DEL_TOPE_DE_MOVI_AI = "Preguntas de Movi AI"
internal const val TAG_TOPE_DE_MOVI_AI = "tope-de-movi-ai"
internal const val NO_SE_PUDO_GUARDAR_EL_TOPE = "No se pudo guardar. Revisa tu conexión e intenta otra vez."
internal const val SOLO_NUMEROS = "Escribe solo el número de preguntas."

internal const val QUE_CUENTA_PARA_EL_TOPE =
    "Solo cuentan las preguntas que van al modelo: las tres sugeridas no gastan ninguna. Se renuevan el 1.º de cada mes. Quien administra no tiene tope."

/** `null` si [escrito] está vacío o es un tope válido; si no, qué está mal. */
internal fun errorDelTopeEscrito(escrito: String): String? {
    if (escrito.isBlank()) return null
    val numero = escrito.toIntOrNull() ?: return SOLO_NUMEROS
    return problemaDelTope(numero)
}

internal fun ayudaDelGlobal(t: TopesDeLaIa): String =
    if (t.globalElegido) "Lo eligió quien administra." else "Es el de Movi: ${conPuntosDeMiles(TOPE_DE_PREGUNTAS_POR_DEFECTO.toLong())}."

/** «Usó 12 este mes · 300 al mes». */
internal fun lineaDeLaExcepcion(u: TopeDeUnUsuario): String =
    "Usó ${conPuntosDeMiles(u.usadasEsteMes.toLong())} este mes · " +
        (if (u.esAdmin) "administra: sin tope" else "${conPuntosDeMiles((u.tope ?: 0).toLong())} al mes")

/** «Usó 12 este mes», o «Administra: sin tope». */
internal fun lineaDeUnUsuarioDelTope(u: TopeDeUnUsuario): String =
    "Usó ${conPuntosDeMiles(u.usadasEsteMes.toLong())} este mes" + if (u.esAdmin) " · administra: sin tope" else ""
