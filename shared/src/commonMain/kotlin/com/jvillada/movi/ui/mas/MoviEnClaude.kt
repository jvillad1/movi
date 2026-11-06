package com.jvillada.movi.ui.mas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.shared.model.ConectorDeClaude
import com.jvillada.movi.shared.model.ConexionDeClaude
import com.jvillada.movi.shared.model.comandoDeClaudeCode
import com.jvillada.movi.shared.model.lineaDeLaConexion
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.launch

/*
 * # Vuelta 135 · «Movi en Claude» (Tú → Ajustes → Movi)
 *
 * El dueño conecta Claude (web, escritorio, móvil y Claude Code) a sus cifras de Movi con un conector
 * personalizado: pega la dirección del servidor MCP de Movi, entra con su cuenta y lo permite. Esta pantalla da la
 * dirección con «Copiar», el paso a paso corto, el comando de Claude Code y la lista de conexiones activas con
 * «Desconectar», que corta el acceso de inmediato (ver `ConectorDeLaAppRoutes.kt`).
 *
 * **Ningún amarillo**: aquí no hay una acción principal (copiar y desconectar son secundarias). Desconectar pregunta
 * antes en una hoja.
 */

const val TITULO_MOVI_EN_CLAUDE: String = "Movi en Claude"
const val SUBTITULO_MOVI_EN_CLAUDE: String = "Conecta Claude para que lea tus cifras"

/** La línea de qué ve Claude: la misma promesa que la pantalla de consentimiento del server. */
const val QUE_VE_CLAUDE: String = "Claude puede leer tus cuentas, movimientos y pagos. No puede anotar, borrar ni mover plata."

const val INTRO_MOVI_EN_CLAUDE: String =
    "Con el conector de Movi, Claude (en la web, el escritorio, el teléfono y Claude Code) lee tus cifras para " +
        "ayudarte a pensarlas."

const val PASOS_EN_CLAUDE: String =
    "En Claude: Ajustes → Conectores → Agregar conector personalizado → pega esta dirección → Conectar. " +
        "Después entras con tu correo y tu contraseña de Movi y lo permites."

const val PASO_EN_CLAUDE_CODE: String = "En Claude Code, corre este comando y después escribe /mcp para entrar:"

const val SIN_CONEXIONES: String = "Todavía no has conectado Claude."
const val NO_SE_PUDO_LEER_EL_CONECTOR: String = "No pudimos leer tus conexiones con Claude."
const val NO_SE_PUDO_DESCONECTAR: String = "No pudimos desconectar. Revisa tu conexión e inténtalo de nuevo."
const val DESCONECTAR: String = "Desconectar"
const val COPIAR: String = "Copiar"
const val COPIADO: String = "Copiado"
const val COPIAR_EL_COMANDO: String = "Copiar el comando"

const val TAG_DIRECCION_DEL_CONECTOR: String = "movi-en-claude:direccion"
const val TAG_COMANDO_DE_CLAUDE_CODE: String = "movi-en-claude:comando"
const val TAG_CONEXION: String = "movi-en-claude:conexion"

fun preguntaDeDesconectar(cliente: String): String = "¿Desconectar $cliente?"

fun detalleDeDesconectar(cliente: String): String =
    "$cliente dejará de leer tus datos de Movi enseguida. Para volver, conéctalo otra vez desde Claude."

private sealed interface EstadoDelConector {
    data object Leyendo : EstadoDelConector
    data object NoSePudo : EstadoDelConector
    data class Listo(val conector: ConectorDeClaude) : EstadoDelConector
}

@Composable
fun MoviEnClaudeScreen(
    onNavigate: (Screen) -> Unit,
    ahora: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    val alcance = rememberCoroutineScope()
    val hoja = hojaDeCompartir()
    var estado by remember { mutableStateOf<EstadoDelConector>(EstadoDelConector.Leyendo) }
    var lectura by remember { mutableStateOf(0) }
    var copiado by remember { mutableStateOf<String?>(null) }
    var preguntando by remember { mutableStateOf<ConexionDeClaude?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lectura) {
        estado = intentar { Repositories.wallets.getConectorDeClaude() }
            .getOrNull()?.let { EstadoDelConector.Listo(it) } ?: EstadoDelConector.NoSePudo
    }

    fun desconectar(c: ConexionDeClaude) {
        ocupado = true
        error = null
        alcance.launch {
            intentar { Repositories.wallets.desconectarDeClaude(c.id) }
                .onSuccess {
                    (estado as? EstadoDelConector.Listo)?.let { e ->
                        estado = EstadoDelConector.Listo(e.conector.copy(conexiones = e.conector.conexiones.filterNot { it.id == c.id }))
                    }
                }
                .onFailure {
                    error = NO_SE_PUDO_DESCONECTAR
                    lectura++
                }
            ocupado = false
            preguntando = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(title = TITULO_MOVI_EN_CLAUDE, leading = HeaderLeading.Back(fallback = Screen.Tu))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion),
            verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "intro") {
                Column {
                    Text(INTRO_MOVI_EN_CLAUDE, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Spacer(Modifier.height(Espacio.xs))
                    Text(QUE_VE_CLAUDE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
            when (val e = estado) {
                EstadoDelConector.Leyendo -> item(key = "leyendo") {
                    Column { repeat(2) { EsqueletoDeFila(conInicio = false, conFinal = false) } }
                }
                EstadoDelConector.NoSePudo -> item(key = "no-se-pudo") {
                    NoSePudoLeer(NO_SE_PUDO_LEER_EL_CONECTOR, onReintentar = { lectura++ })
                }
                is EstadoDelConector.Listo -> {
                    val url = e.conector.url
                    item(key = "en-claude") {
                        Column {
                            Seccion("En Claude")
                            Superficie(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    SelectionContainer {
                                        Text(
                                            url,
                                            style = Movi.textos.cuerpoFuerte,
                                            color = Movi.colores.texto,
                                            modifier = Modifier.testTag(TAG_DIRECCION_DEL_CONECTOR),
                                        )
                                    }
                                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                                    Boton(
                                        if (copiado == "url") COPIADO else COPIAR,
                                        onClick = { hoja.copiar(url); copiado = "url" },
                                        variante = VarianteDeBoton.Secundario,
                                        tamano = TamanoDeBoton.S,
                                        icono = Icons.Rounded.ContentCopy,
                                    )
                                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                                    Text(PASOS_EN_CLAUDE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                }
                            }
                        }
                    }
                    item(key = "en-claude-code") {
                        val comando = comandoDeClaudeCode(url)
                        Column {
                            Seccion("En Claude Code")
                            Superficie(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text(PASO_EN_CLAUDE_CODE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                    Spacer(Modifier.height(Espacio.xs))
                                    SelectionContainer {
                                        Text(
                                            comando,
                                            style = Movi.textos.cuerpo,
                                            color = Movi.colores.texto,
                                            modifier = Modifier.testTag(TAG_COMANDO_DE_CLAUDE_CODE),
                                        )
                                    }
                                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                                    Boton(
                                        if (copiado == "comando") COPIADO else COPIAR_EL_COMANDO,
                                        onClick = { hoja.copiar(comando); copiado = "comando" },
                                        variante = VarianteDeBoton.Secundario,
                                        tamano = TamanoDeBoton.S,
                                        icono = Icons.Rounded.ContentCopy,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "conexiones-titulo") {
                        Column {
                            Seccion("Conexiones")
                            error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                            if (e.conector.conexiones.isEmpty()) {
                                Text(SIN_CONEXIONES, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            }
                        }
                    }
                    if (e.conector.conexiones.isNotEmpty()) {
                        item(key = "conexiones") {
                            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                                e.conector.conexiones.forEachIndexed { i, c ->
                                    if (i > 0) Separador()
                                    Column(modifier = Modifier.testTag(TAG_CONEXION)) {
                                        Fila(
                                            titulo = c.cliente,
                                            subtitulo = lineaDeLaConexion(c, ahora()),
                                            lineasDelSubtitulo = 3,
                                            inicio = { IconoDeAjuste(Icons.Rounded.Link) },
                                        )
                                        Boton(
                                            DESCONECTAR,
                                            onClick = { preguntando = c },
                                            variante = VarianteDeBoton.Fantasma,
                                            tamano = TamanoDeBoton.S,
                                            modifier = Modifier.padding(start = Medida.margenDePantalla, bottom = Espacio.xs),
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

    // Después de la lista: la hoja va encima de todo.
    preguntando?.let { c ->
        ConfirmarEnHoja(
            pregunta = preguntaDeDesconectar(c.cliente),
            detalle = detalleDeDesconectar(c.cliente),
            textoConfirmar = DESCONECTAR,
            ocupado = ocupado,
            onConfirmar = { desconectar(c) },
            onCancelar = { preguntando = null },
        )
    }
}
