package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.hayQueAvisarDeLaIa
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * # «Qué ve la IA» (vuelta 95)
 *
 * La primera vez que alguien usa algo que va a Claude —el chat libre de Movi AI, leer un papel o un
 * extracto con IA— se abre una hoja corta que dice **qué se manda y qué no**, antes de mandarlo. No es
 * un permiso legal: es que la confianza no se pida a ciegas. Tiene dos botones:
 *
 * - **«Entendido»**: se anota como visto y sigue lo que el usuario iba a hacer.
 * - **«Ver qué sabe Movi»**: se anota como visto y abre «Lo que Movi sabe de ti». Lo que iba a hacer
 *   **no** sigue (el chat se queda con lo escrito); un papel compartido, que espera en su cola, se lee
 *   cuando se vuelve a mirar una pantalla.
 * - Cerrarla con la X, el velo o «Atrás» no manda nada y no la da por vista.
 *
 * **Se guarda por usuario en el servidor** (`users.aviso_ia_visto_en`), así que no vuelve en otro
 * aparato; [AvisoDeLaIa.visto] es solo lo ya sabido en esta sesión. **Las tres preguntas sugeridas no
 * la abren**: no van a la IA (las contesta el servidor con sus números), y quien las llama ni pasa
 * por la [PuertaDeLaIa]. En Ajustes → Movi, «Qué ve la IA» la vuelve a abrir (sin nada pendiente).
 *
 * Sobre el entrenamiento: los términos comerciales de Anthropic (anthropic.com/legal/commercial-terms,
 * «Customer Content») dicen que Anthropic no puede entrenar modelos con el contenido del cliente —lo
 * que se envía y lo que se recibe— de sus servicios, entre ellos la API. La hoja lo cuenta con esas
 * palabras y no promete más. (Antes citaba la página de privacidad de claude.com, que habla de los
 * productos de consumo y dice «por defecto».)
 *
 * **Lo que se manda, completo**: el contexto de cuentas y movimientos, los recuerdos de «Lo que Movi sabe
 * de ti», la foto que se adjunta en el chat y el archivo de un papel o extracto. Lo que se omite pesa más
 * que lo que se afirma: si el chat manda algo nuevo, esta hoja se actualiza en el mismo cambio.
 */
object AvisoDeLaIa {
    /** ¿Ya la vio este usuario? `null` = todavía no se sabe (se lee del perfil la primera vez que hace falta). */
    var visto: Boolean? by mutableStateOf(null)

    /** Para el cierre de sesión y las pruebas: lo visto por uno no lo hereda el siguiente. */
    fun olvidar() {
        visto = null
    }
}

const val TITULO_QUE_VE_LA_IA = "Qué ve la IA"

internal const val SUBTITULO_QUE_VE_LA_IA =
    "Algunas funciones de Movi usan Claude, la IA de Anthropic. Esto es lo que le llega y lo que no."

internal const val SE_MANDA_EL_CHAT =
    "Movi AI, cuando le escribes: un resumen de tus cuentas y movimientos para poder contestarte."
internal const val SE_MANDA_LOS_RECUERDOS =
    "Los recuerdos de «Lo que Movi sabe de ti», para que te entienda mejor."
internal const val SE_MANDA_LA_FOTO =
    "La foto que adjuntes en el chat."
internal const val SE_MANDA_EL_ARCHIVO =
    "Leer un comprobante o un extracto con IA: el archivo que elegiste."
internal const val NO_SE_MANDA =
    "Tu contraseña ni tu sesión. Nunca salen de Movi."
internal const val NO_ENTRENA =
    "Según los términos comerciales de Anthropic, no puede entrenar sus modelos con lo que se envía por su API."
internal const val LAS_SUGERIDAS_NO_VAN =
    "Las tres preguntas sugeridas de Movi AI las contesta Movi con sus números, sin la IA."

internal const val ENTENDIDO = "Entendido"
internal const val VER_QUE_SABE_MOVI = "Ver qué sabe Movi"

/** El tag de la hoja, para las pruebas y las capturas. */
const val TAG_AVISO_DE_LA_IA: String = "aviso-de-la-ia"

/**
 * **La puerta de lo que va a Claude.** [pasar] corre [luego] ya si el usuario vio el aviso; si no, abre la
 * hoja y lo corre cuando toque «Entendido». Se arma con [rememberPuertaDeLaIa], que además dibuja la hoja
 * ([Hoja]). Cada pantalla que manda algo a la IA tiene la suya; lo visto es global ([AvisoDeLaIa]).
 */
class PuertaDeLaIa internal constructor(
    private val alcance: CoroutineScope,
    private val alNavegar: (Screen) -> Unit,
) {
    var abierta: Boolean by mutableStateOf(false)
        private set
    private var pendiente: (() -> Unit)? = null
    private var siNoSigue: (() -> Unit)? = null

    /**
     * Corre [luego] ya si el usuario vio el aviso; si no, abre la hoja. [alNoSeguir] corre si la hoja se
     * cierra sin «Entendido» (la X, el velo, «Atrás» o «Ver qué sabe Movi»): lo que el usuario iba a mandar
     * no se pierde —el chat lo deja en el campo de texto—.
     */
    fun pasar(alNoSeguir: (() -> Unit)? = null, luego: () -> Unit) {
        if (abierta) return
        if (AvisoDeLaIa.visto == true) return luego()
        alcance.launch {
            val visto = AvisoDeLaIa.visto ?: runCatching { Repositories.wallets.getUserProfile() }.getOrNull()
                ?.let { !hayQueAvisarDeLaIa(it.avisoIaVistoEn) }
                ?: false // sin poder leer el perfil, mejor avisar de más que de menos
            if (visto) {
                AvisoDeLaIa.visto = true
                luego()
            } else {
                pendiente = luego
                siNoSigue = alNoSeguir
                abierta = true
            }
        }
    }

    /** «Ajustes → Qué ve la IA»: la hoja sin nada pendiente. */
    fun abrir() {
        pendiente = null
        siNoSigue = null
        abierta = true
    }

    internal fun entendido() {
        marcarVisto()
        abierta = false
        val seguir = pendiente
        pendiente = null
        siNoSigue = null
        seguir?.invoke()
    }

    internal fun verQueSabeMovi() {
        marcarVisto()
        abierta = false
        pendiente = null
        soltar()
        alNavegar(Screen.LoQueMoviSabe)
    }

    internal fun cerrar() {
        abierta = false
        pendiente = null
        soltar()
    }

    private fun soltar() {
        val alNoSeguir = siNoSigue
        siNoSigue = null
        alNoSeguir?.invoke()
    }

    /** Se anota ya, en la sesión; si el servidor no contesta, se vuelve a preguntar la próxima vez. */
    private fun marcarVisto() {
        if (AvisoDeLaIa.visto == true) return
        AvisoDeLaIa.visto = true
        alcance.launch { runCatching { Repositories.wallets.marcarAvisoDeLaIa() } }
    }

    /** La hoja, si está abierta. Va una vez por pantalla que use la puerta. */
    @Composable
    fun Hoja() {
        if (!abierta) return
        HojaQueVeLaIa(onEntendido = ::entendido, onVer = ::verQueSabeMovi, onCerrar = ::cerrar)
    }
}

@Composable
fun rememberPuertaDeLaIa(onNavigate: (Screen) -> Unit): PuertaDeLaIa {
    val alcance = rememberCoroutineScope()
    return remember(alcance) { PuertaDeLaIa(alcance) { onNavigate(it) } }
}

@Composable
internal fun HojaQueVeLaIa(onEntendido: () -> Unit, onVer: () -> Unit, onCerrar: () -> Unit) {
    AtrasCierraEstaHoja(onCerrar)
    MarcoDeHoja(
        onDismiss = onCerrar,
        relleno = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Espacio.xl),
        conCierre = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag(TAG_AVISO_DE_LA_IA),
        ) {
            EncabezadoDeHoja(TITULO_QUE_VE_LA_IA, subtitulo = SUBTITULO_QUE_VE_LA_IA)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Bloque("Lo que se manda", listOf(SE_MANDA_EL_CHAT, SE_MANDA_LOS_RECUERDOS, SE_MANDA_LA_FOTO, SE_MANDA_EL_ARCHIVO))
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Bloque("Lo que no se manda", listOf(NO_SE_MANDA))
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Bloque("Sobre tus datos", listOf(NO_ENTRENA, LAS_SUGERIDAS_NO_VAN))
            Spacer(Modifier.height(Medida.entreSecciones))
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Boton(ENTENDIDO, onClick = onEntendido, tamano = TamanoDeBoton.M, anchoCompleto = true)
                Boton(
                    VER_QUE_SABE_MOVI,
                    onClick = onVer,
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.M,
                    anchoCompleto = true,
                )
            }
        }
    }
}

@Composable
private fun Bloque(titulo: String, lineas: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        Text(titulo, style = Movi.textos.titulo, color = Movi.colores.texto)
        lineas.forEach { Text(it, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio) }
    }
}
