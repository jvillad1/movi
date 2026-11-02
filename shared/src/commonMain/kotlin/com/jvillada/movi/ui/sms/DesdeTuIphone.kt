package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.intentar
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_APPLE_PAY
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.RUTA_DE_LA_CAPTURA_POR_ATAJO
import com.jvillada.movi.shared.model.ResumenDeAtajosDescartados
import com.jvillada.movi.shared.model.textoDeAtajosDescartados
import com.jvillada.movi.shared.model.lineaDelCodigoDeCaptura
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.recurrentes.NumeroEnCirculo
import kotlinx.coroutines.launch

/*
 * # Vuelta 111 · «Desde tu iPhone»: la guía de los atajos
 *
 * Un iPhone no deja que ninguna app lea los SMS ni las notificaciones del banco. La app Atajos sí puede
 * mandarle a Movi cada SMS del banco (automatización «Mensaje») y cada pago con Apple Pay («Transacción»).
 * Esta sección arma eso en tres pasos: el código de captura, los atajos y las automatizaciones.
 *
 * Va en «Mensajes del banco» (web e iOS; en Android no: ahí Movi lee el teléfono) y en el paso de la
 * captura de la guía de primer uso cuando el aparato es un iPhone.
 *
 * **Las palabras de iOS** («Automatización», «Mensaje», «El mensaje contiene», «Ejecutar de inmediato»,
 * «Transacción») salen de la guía de Atajos de Apple en español (support.apple.com/es-lamr/guide/shortcuts,
 * «Activadores de comunicación» y «Crear una automatización personal»); «Ejecutar de inmediato» y los
 * nombres de las propiedades de la transacción no se pudieron confirmar en una fuente: BORRADOR, revisarlos
 * con el iPhone (ver docs/lanzamiento/atajos-de-iphone.md).
 */

const val ERROR_AL_CREAR_EL_CODIGO: String = "No pudimos crear el código. Revisa tu conexión e inténtalo de nuevo."
const val ERROR_AL_DESACTIVAR_EL_CODIGO: String =
    "No pudimos desactivar el código. Revisa tu conexión e inténtalo de nuevo."

/** El tag de la línea de error de crear o desactivar. */
const val TAG_ERROR_DEL_CODIGO: String = "error-del-codigo-de-captura"

/** El título de la sección. */
const val TITULO_DESDE_TU_IPHONE: String = "Desde tu iPhone"

/** El tag de la sección entera (para saber si se pinta). */
const val TAG_DESDE_TU_IPHONE: String = "desde-tu-iphone"

/** El tag del código en claro, que se ve una sola vez. */
const val TAG_CODIGO_DE_CAPTURA: String = "codigo-de-captura-en-claro"

/** El tag de la línea de lo que llegó y no se guardó («12 mensajes no eran de un banco…»). */
const val TAG_ATAJOS_DESCARTADOS: String = "atajos-descartados"

/** El tag de la línea «Código creado el 2 nov · último aviso hace 2 h». */
const val TAG_LINEA_DEL_CODIGO: String = "linea-del-codigo-de-captura"

const val CREAR_TU_CODIGO: String = "Crear tu código"
const val CREAR_OTRO_CODIGO: String = "Crear otro"
const val DESACTIVAR_EL_CODIGO: String = "Desactivar"
const val AGREGAR_EL_ATAJO_DE_MENSAJES: String = "Agregar el atajo de mensajes"
const val AGREGAR_EL_ATAJO_DE_APPLE_PAY: String = "Agregar el atajo de Apple Pay"
const val COPIAR_EL_TEXTO_DE_PRUEBA: String = "Copiar el texto de prueba"

/** La línea de privacidad, tal cual la pide el dueño. */
const val PRIVACIDAD_DE_LOS_ATAJOS: String =
    "Solo se guardan los mensajes de bancos: los demás que lleguen se descartan sin guardarlos."

const val INTRO_DESDE_TU_IPHONE: String =
    "Con la app Atajos, tu iPhone le avisa a Movi cada SMS del banco y cada pago con Apple Pay. " +
        "Lo que llegue te espera en «Por revisar» para que lo confirmes. Es opcional."

const val QUE_ES_EL_CODIGO: String =
    "Es una clave solo para tus atajos: no abre tu cuenta, solo deja mandarle avisos a Movi."

const val GUARDALO_AHORA: String =
    "Cópialo ahora: Movi no lo vuelve a mostrar. Lo pegas una vez al agregar cada atajo."

const val PREGUNTA_CREAR_OTRO: String = "¿Crear otro código?"
const val DETALLE_CREAR_OTRO: String =
    "El código de ahora deja de servir: tendrás que agregar los atajos otra vez y pegar el nuevo."
const val PREGUNTA_DESACTIVAR: String = "¿Desactivar el código?"
const val DETALLE_DESACTIVAR: String =
    "Tus atajos dejan de avisarle a Movi hasta que crees otro código y los vuelvas a agregar."

/**
 * El texto de prueba: un SMS de Bancolombia con la forma de los del repo (`ElCorreoDePseSeLeeTest`,
 * `ElMismoPagoEnLaBandejaTest`), con un comercio que dice que es una prueba. Entra a «Por revisar» como
 * cualquier aviso: después se ignora.
 */
const val TEXTO_DE_PRUEBA_DEL_ATAJO: String =
    "Bancolombia: Compraste \$1.000,00 en PRUEBA DE MOVI con tu T.Deb *0000."

/** ¿Se pinta «Desde tu iPhone» en «Mensajes del banco»? En todo lo que no es Android. */
fun seVeLaSeccionDesdeTuIphone(esAndroid: Boolean): Boolean = !esAndroid

/**
 * **¿La guía de primer uso ofrece los atajos?** Solo en un iPhone (la app o la web abierta en él): en una
 * computadora o un iPad no hay SMS que reenviar. No es un modo de captura nuevo a propósito
 * (`CapturaDelPrimerUso` sigue en «subir un extracto», que también sirve en el iPhone): los atajos van
 * encima, en el mismo paso.
 */
fun laGuiaOfreceLosAtajos(esAndroid: Boolean, esIphone: Boolean): Boolean = !esAndroid && esIphone

/** Los pasos de la automatización de mensajes, con las palabras de iOS. [palabras]: una por banco. */
fun pasosDeLaAutomatizacionDeMensajes(palabras: List<String>): List<String> {
    val ejemplo = palabras.firstOrNull() ?: "Bancolombia"
    val otras = palabras.drop(1)
    return listOf(
        "En Atajos toca «Automatización» y después «+» (o «Nueva automatización»).",
        "Elige «Mensaje», toca «El mensaje contiene» y escribe «$ejemplo»." +
            if (otras.isEmpty()) "" else " Haz una automatización más con cada uno: ${otras.joinToString(", ") { "«$it»" }}.",
        "Elige «Ejecutar de inmediato» y toca «Siguiente».",
        "Elige el atajo «$NOMBRE_DEL_ATAJO_DE_MENSAJES».",
        PROBAR_CON_UN_SMS_REAL,
    )
}

/** El último paso: la palabra hay que comprobarla con un SMS de verdad, porque cada banco firma distinto. */
const val PROBAR_CON_UN_SMS_REAL: String =
    "Cuando te llegue un SMS real del banco, mira que aparezca en «Por revisar». Si no llega, revisa que el " +
        "SMS diga esa palabra tal cual."

/** Los pasos de la automatización de Apple Pay. */
val PASOS_DE_LA_AUTOMATIZACION_DE_APPLE_PAY: List<String> = listOf(
    "En «Automatización» toca «+» y elige «Transacción» (Wallet).",
    "Marca las tarjetas que usas con Apple Pay, elige «Ejecutar de inmediato» y toca «Siguiente».",
    "Elige el atajo «$NOMBRE_DEL_ATAJO_DE_APPLE_PAY».",
)

/**
 * **Cómo armar un atajo a mano**, cuando Movi no tiene el atajo firmado para instalarlo con un toque. Son
 * los mismos campos del atajo firmado ([url] con la ruta de [RUTA_DE_LA_CAPTURA_POR_ATAJO]).
 */
fun recetaDelAtajo(url: String, deApplePay: Boolean): List<String> = buildList {
    add("En Atajos toca «+» para un atajo nuevo y llámalo «${if (deApplePay) NOMBRE_DEL_ATAJO_DE_APPLE_PAY else NOMBRE_DEL_ATAJO_DE_MENSAJES}».")
    add("Agrega la acción «Obtener contenido de URL» y pega: $url")
    add("Toca la flecha de la acción: «Método» POST; en «Encabezados» agrega «Authorization» con el valor «Bearer » seguido de tu código.")
    if (deApplePay) {
        add(
            "En «Cuerpo de la solicitud» elige JSON y agrega cuatro campos de texto: «tipo» con «transaccion», y " +
                "«comercio», «monto» y «tarjeta» con esos datos de la «Entrada del atajo» (toca la variable y elige cada uno).",
        )
    } else {
        add("En «Cuerpo de la solicitud» elige JSON y agrega dos campos de texto: «tipo» con «mensaje» y «texto» con «Entrada del atajo».")
    }
}

/** Lo que se lee del server para pintar la sección. */
private sealed interface EstadoDelCodigo {
    data object Leyendo : EstadoDelCodigo
    data object NoSePudo : EstadoDelCodigo
    data class Listo(val codigo: CodigoDeCaptura) : EstadoDelCodigo
}

/**
 * **«Desde tu iPhone»**: el código, los atajos y las automatizaciones. [palabras] son las de los bancos del
 * dueño (`palabrasParaLaAutomatizacion`); [ahora] se inyecta para las pruebas.
 */
@Composable
fun DesdeTuIphone(
    palabras: List<String>,
    modifier: Modifier = Modifier,
    ahora: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    val alcance = rememberCoroutineScope()
    val hoja = hojaDeCompartir()
    val uri = LocalUriHandler.current
    var estado by remember { mutableStateOf<EstadoDelCodigo>(EstadoDelCodigo.Leyendo) }
    /** El código recién creado, en claro: vive solo en esta composición. Al salir de la pantalla, se olvida. */
    var enClaro by remember { mutableStateOf<String?>(null) }
    var copiado by remember { mutableStateOf<String?>(null) }
    var preguntando by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var recetaAbierta by remember { mutableStateOf<String?>(null) }
    var lectura by remember { mutableStateOf(0) }

    /** Lo que mandaron los atajos y no se guardó en 30 días; `null` mientras no se sabe. */
    var descartados by remember { mutableStateOf<ResumenDeAtajosDescartados?>(null) }

    LaunchedEffect(lectura) {
        estado = intentar { Repositories.wallets.getCodigoDeCaptura() }
            .getOrNull()?.let { EstadoDelCodigo.Listo(it) } ?: EstadoDelCodigo.NoSePudo
        descartados = intentar { Repositories.wallets.getAtajosDescartados() }.getOrNull()
    }

    /**
     * Revisión de la vuelta 111: si crear o desactivar falla, se dice (como las demás escrituras de Ajustes:
     * la línea en rojo con «Revisa tu conexión…») y se vuelve a leer el estado: una respuesta perdida
     * después de revocar no deja la pantalla diciendo un código que ya no sirve.
     */
    var errorDeEscritura by remember { mutableStateOf<String?>(null) }

    fun crear() {
        ocupado = true
        errorDeEscritura = null
        alcance.launch {
            intentar { Repositories.wallets.crearCodigoDeCaptura() }
                .onFailure {
                    errorDeEscritura = ERROR_AL_CREAR_EL_CODIGO
                    lectura++
                }
                .onSuccess { nuevo ->
                    enClaro = nuevo.codigo
                    copiado = null
                    val antes = (estado as? EstadoDelCodigo.Listo)?.codigo ?: CodigoDeCaptura()
                    estado = EstadoDelCodigo.Listo(antes.copy(vigente = true, creadoEn = nuevo.creadoEn, ultimoUso = null))
                }
            ocupado = false
            preguntando = null
        }
    }

    fun desactivar() {
        ocupado = true
        errorDeEscritura = null
        alcance.launch {
            intentar { Repositories.wallets.revocarCodigoDeCaptura() }
                .onFailure {
                    errorDeEscritura = ERROR_AL_DESACTIVAR_EL_CODIGO
                    lectura++
                }
                .onSuccess {
                    enClaro = null
                    val antes = (estado as? EstadoDelCodigo.Listo)?.codigo ?: CodigoDeCaptura()
                    estado = EstadoDelCodigo.Listo(antes.copy(vigente = false, creadoEn = null, ultimoUso = null))
                }
            ocupado = false
            preguntando = null
        }
    }

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_DESDE_TU_IPHONE)) {
        Seccion(TITULO_DESDE_TU_IPHONE)
        Text(INTRO_DESDE_TU_IPHONE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            PasoDelAtajo(1, "Crea tu código") {
                errorDeEscritura?.let { error ->
                    Text(error, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.testTag(TAG_ERROR_DEL_CODIGO))
                    Spacer(Modifier.height(Espacio.xs))
                }
                when (val e = estado) {
                    EstadoDelCodigo.Leyendo -> EsqueletoDeFila()
                    EstadoDelCodigo.NoSePudo -> {
                        Text("No pudimos leer tu código.", style = Movi.textos.apoyo, color = Movi.colores.aviso)
                        Spacer(Modifier.height(Espacio.xs))
                        Boton("Reintentar", onClick = { lectura++ }, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S)
                    }
                    is EstadoDelCodigo.Listo -> {
                        val codigo = enClaro
                        when {
                            codigo != null -> {
                                SelectionContainer {
                                    Text(
                                        codigo,
                                        style = Movi.textos.cuerpoFuerte,
                                        color = Movi.colores.texto,
                                        modifier = Modifier.testTag(TAG_CODIGO_DE_CAPTURA),
                                    )
                                }
                                Spacer(Modifier.height(Espacio.xs))
                                Text(GUARDALO_AHORA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                Spacer(Modifier.height(Medida.dentroDeSeccion))
                                Boton(
                                    if (copiado == "codigo") "Copiado" else "Copiar",
                                    onClick = { hoja.copiar(codigo); copiado = "codigo" },
                                    variante = VarianteDeBoton.Secundario,
                                    tamano = TamanoDeBoton.S,
                                    icono = Icons.Rounded.ContentCopy,
                                )
                            }
                            e.codigo.vigente -> {
                                Text(
                                    lineaDelCodigoDeCaptura(e.codigo, ahora()).orEmpty(),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.testTag(TAG_LINEA_DEL_CODIGO),
                                )
                                Spacer(Modifier.height(Espacio.xs))
                                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Boton(
                                        CREAR_OTRO_CODIGO,
                                        onClick = { preguntando = CREAR_OTRO_CODIGO },
                                        variante = VarianteDeBoton.Secundario,
                                        tamano = TamanoDeBoton.S,
                                    )
                                    Boton(
                                        DESACTIVAR_EL_CODIGO,
                                        onClick = { preguntando = DESACTIVAR_EL_CODIGO },
                                        variante = VarianteDeBoton.Fantasma,
                                        tamano = TamanoDeBoton.S,
                                    )
                                }
                            }
                            else -> {
                                Text(QUE_ES_EL_CODIGO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                                Spacer(Modifier.height(Medida.dentroDeSeccion))
                                Boton(
                                    CREAR_TU_CODIGO,
                                    onClick = { crear() },
                                    variante = VarianteDeBoton.Secundario,
                                    tamano = TamanoDeBoton.S,
                                    icono = Icons.Rounded.Key,
                                    cargando = ocupado,
                                )
                            }
                        }
                    }
                }
            }
            Separador()
            PasoDelAtajo(2, "Agrega los atajos") {
                val codigo = (estado as? EstadoDelCodigo.Listo)?.codigo
                val url = apiBaseUrl.trimEnd('/') + RUTA_DE_LA_CAPTURA_POR_ATAJO
                Text(
                    "Al agregarlos, Atajos te pide el código: pégalo ahí.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                listOf(
                    Triple(AGREGAR_EL_ATAJO_DE_MENSAJES, codigo?.atajoDeMensajes == true, RUTA_DEL_ATAJO_DE_MENSAJES),
                    Triple(AGREGAR_EL_ATAJO_DE_APPLE_PAY, codigo?.atajoDeApplePay == true, RUTA_DEL_ATAJO_DE_APPLE_PAY),
                ).forEach { (rotulo, firmado, ruta) ->
                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                    Boton(
                        rotulo,
                        onClick = {
                            if (firmado) runCatching { uri.openUri(apiBaseUrl.trimEnd('/') + ruta) }
                            else recetaAbierta = if (recetaAbierta == rotulo) null else rotulo
                        },
                        variante = VarianteDeBoton.Secundario,
                        tamano = TamanoDeBoton.S,
                        icono = Icons.Rounded.Download,
                    )
                    if (recetaAbierta == rotulo) {
                        Spacer(Modifier.height(Espacio.xs))
                        RecetaDelAtajo(recetaDelAtajo(url, deApplePay = rotulo == AGREGAR_EL_ATAJO_DE_APPLE_PAY))
                        Spacer(Modifier.height(Espacio.xs))
                        Boton(
                            if (copiado == rotulo) "Copiada" else "Copiar la dirección",
                            onClick = { hoja.copiar(url); copiado = rotulo },
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                            icono = Icons.Rounded.ContentCopy,
                        )
                    }
                }
            }
            Separador()
            PasoDelAtajo(3, "Crea las automatizaciones") {
                Text("Para los SMS del banco:", style = Movi.textos.apoyo, color = Movi.colores.texto)
                RecetaDelAtajo(pasosDeLaAutomatizacionDeMensajes(palabras))
                Spacer(Modifier.height(Espacio.s))
                Text("Para los pagos con Apple Pay:", style = Movi.textos.apoyo, color = Movi.colores.texto)
                RecetaDelAtajo(PASOS_DE_LA_AUTOMATIZACION_DE_APPLE_PAY)
            }
            Separador()
            PasoDelAtajo(4, "Pruébalo") {
                Text(
                    "En Atajos toca «$NOMBRE_DEL_ATAJO_DE_MENSAJES», pega este texto y en unos segundos aparece en " +
                        "«Por revisar» (después ignóralo):",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Spacer(Modifier.height(Espacio.xs))
                SelectionContainer { Text(TEXTO_DE_PRUEBA_DEL_ATAJO, style = Movi.textos.apoyo, color = Movi.colores.texto) }
                Spacer(Modifier.height(Espacio.xs))
                Boton(
                    if (copiado == "prueba") "Copiado" else COPIAR_EL_TEXTO_DE_PRUEBA,
                    onClick = { hoja.copiar(TEXTO_DE_PRUEBA_DEL_ATAJO); copiado = "prueba" },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.ContentCopy,
                )
            }
        }
        // Lo que llegó y no se guardó, como la línea de los correos descartados: nada se pierde en silencio.
        descartados?.let(::textoDeAtajosDescartados)?.let { linea ->
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Text(linea, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.testTag(TAG_ATAJOS_DESCARTADOS))
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Text(PRIVACIDAD_DE_LOS_ATAJOS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }

    when (preguntando) {
        CREAR_OTRO_CODIGO -> ConfirmarEnHoja(
            pregunta = PREGUNTA_CREAR_OTRO,
            detalle = DETALLE_CREAR_OTRO,
            textoConfirmar = CREAR_OTRO_CODIGO,
            ocupado = ocupado,
            onConfirmar = { crear() },
            onCancelar = { preguntando = null },
        )
        DESACTIVAR_EL_CODIGO -> ConfirmarEnHoja(
            pregunta = PREGUNTA_DESACTIVAR,
            detalle = DETALLE_DESACTIVAR,
            textoConfirmar = DESACTIVAR_EL_CODIGO,
            ocupado = ocupado,
            onConfirmar = { desactivar() },
            onCancelar = { preguntando = null },
        )
    }
}

/** Una lista corta de pasos, uno por renglón, con su viñeta. */
@Composable
private fun RecetaDelAtajo(pasos: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        pasos.forEachIndexed { i, paso ->
            Text("${i + 1}. $paso", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

/** Un paso: el número, el título y lo que haga falta debajo (como los del reenvío por correo). */
@Composable
private fun PasoDelAtajo(numero: Int, titulo: String, detalle: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.s),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.Top,
    ) {
        NumeroEnCirculo(numero)
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(Espacio.xs))
            detalle()
        }
    }
}
