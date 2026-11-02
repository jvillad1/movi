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
 * «Activadores de comunicación» y «Crear una automatización personal»).
 *
 * Vuelta 114 · **las de iOS 27 se verificaron a mano en el iPhone de Caro (iOS 27.0.1, 2-nov-2026)**: ya no
 * hay pestaña «Automatización»; dentro del atajo, «Buscar», la pastilla «Automatización», «Comunicación» →
 * «Mensaje», la tarjeta «Al recibir un mensaje en el que Remitente es Remitente», «Remitente» → «Mensaje»
 * («Mensaje contiene Texto»), la flecha junto a «que» con «Automatización» y «Confirmar antes de ejecutar», y
 * «Agregar filtro». La guía elige la variante por la versión de iOS (`LaVersionDeIos.kt`). La automatización
 * «Transacción» de Apple Pay en iOS 27 **no** se verificó: la guía lo dice. El atajo se instala con el nombre
 * del archivo (`NOMBRE_DEL_ATAJO_DE_MENSAJES`, ver `CapturaPorAtajo.kt`).
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

/**
 * La línea de privacidad. Vuelta 114: decía «Solo se guardan los mensajes de bancos», y desde la 113 el atajo
 * no sabe quién manda (`seGuardaElSmsDelAtajo`): se guarda lo que **empieza** como un aviso del banco y trae un
 * monto, aunque lo escriba una persona. Ahora dice eso, y que nada se anota solo.
 */
const val PRIVACIDAD_DE_LOS_ATAJOS: String =
    "Solo se guardan los mensajes que empiezan como un aviso del banco y traen un monto; los demás se " +
        "descartan sin guardarlos. Nada se anota sin que lo confirmes."

const val INTRO_DESDE_TU_IPHONE: String =
    "Con la app Atajos, tu iPhone le avisa a Movi cada SMS del banco y cada pago con Apple Pay. " +
        "Lo que llegue te espera en «Por revisar» para que lo confirmes. Es opcional."

const val QUE_ES_EL_CODIGO: String =
    "Es una clave solo para tus atajos: no abre tu cuenta, solo deja mandarle avisos a Movi."

const val GUARDALO_AHORA: String =
    "Cópialo ahora: Movi no lo vuelve a mostrar. Lo pegas una vez al agregar cada atajo."

const val PREGUNTA_CREAR_OTRO: String = "¿Crear otro código?"
const val DETALLE_CREAR_OTRO: String =
    "El código de ahora deja de servir. Después abre el atajo y cambia el código de la acción «Texto» por el " +
        "nuevo (en el de Apple Pay, el de «Encabezados»)."

/** Vuelta 114 · lo que dice el paso 2 al agregar el atajo: dónde pegar el código. */
const val AL_AGREGAR_EL_ATAJO: String =
    "Al agregarlo, Atajos muestra «Configurar este atajo» y te pide el código: pégalo y toca «Agregar atajo»."

/**
 * Vuelta 114 · el camino que sí funcionó en el iPhone de Caro cuando «Agregar atajo» no respondió.
 * Vuelta 115 (verificado en ese iPhone con iOS 27 y el atajo de producción): con el código pegado,
 * «Omitir configuración» agrega el atajo y **conserva** el código (la acción «Texto» queda con él), así
 * que no hay que pegarlo otra vez; revisar la acción «Texto» queda como segunda opción.
 */
const val PLAN_B_DEL_CODIGO: String =
    "Pega tu código. Si «Agregar atajo» no responde, toca «Omitir configuración»: el atajo se agrega con el " +
        "código que pegaste. Si después no avisa, abre el atajo y revisa que el código esté en la acción «Texto»."

/** Vuelta 114 · el código quedó a la vista en fotos: al cambiarlo, hay que ponerlo en el atajo. */
const val CAMBIAR_EL_CODIGO_DEL_ATAJO: String =
    "Si tocas «Crear otro», abre el atajo y cambia el código de la acción «Texto»."
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

/** Vuelta 114 · los rótulos de cada variante, cuando se ve más de una o detrás de «¿Tu iPhone muestra otra cosa?». */
const val ROTULO_IOS_27_O_MAS: String = "En iOS 27 o más nuevo:"
const val ROTULO_IOS_17_A_26: String = "En iOS 17 a 26:"

/** Vuelta 114 · antes de iOS 17 no hay «Ejecutar de inmediato» para «Mensaje». */
const val ANTES_DE_IOS_17: String =
    "Antes de iOS 17, el iPhone te pide tocar una notificación cada vez que llega un SMS. Si es tu caso, mejor " +
        "reenvíale a Movi los correos del banco."

/** Vuelta 114 · el enlace que abre la otra variante cuando Movi sabe la versión de iOS. */
const val TU_IPHONE_MUESTRA_OTRA_COSA: String = "¿Tu iPhone muestra otra cosa?"

/** Los tags de cada variante (para las pruebas). */
const val TAG_PASOS_IOS_27: String = "pasos-de-la-automatizacion-ios-27"
const val TAG_PASOS_IOS_17_A_26: String = "pasos-de-la-automatizacion-ios-17-a-26"
const val TAG_ANTES_DE_IOS_17: String = "automatizacion-antes-de-ios-17"

/**
 * **Los pasos de la automatización de mensajes**, con las palabras de iOS de cada [variante] (vuelta 114). Una
 * palabra por banco ([palabras], `palabrasParaLaAutomatizacion`): la primera va en la primera tarjeta o
 * automatización y las demás en una más cada una. [VarianteDeLaAutomatizacion.ANTES_DE_IOS_17] es una sola línea.
 */
fun pasosDeLaAutomatizacionDeMensajes(palabras: List<String>, variante: VarianteDeLaAutomatizacion): List<String> {
    val ejemplo = palabras.firstOrNull() ?: "Bancolombia"
    val otras = palabras.drop(1).joinToString(", ") { "«$it»" }
    return when (variante) {
        VarianteDeLaAutomatizacion.IOS_27_O_MAS -> listOfNotNull(
            "En Atajos abre el atajo «$NOMBRE_DEL_ATAJO_DE_MENSAJES».",
            "Abajo toca «Buscar» y después la pastilla «Automatización».",
            "En «Comunicación» elige «Mensaje». Al atajo se le agrega la tarjeta «Al recibir un mensaje en el que " +
                "Remitente es Remitente».",
            "Toca «Remitente» y elige «Mensaje», para que quede «Mensaje contiene Texto». Escribe «$ejemplo».",
            "Toca la flecha junto a «que»: deja prendido «Automatización» y apagado «Confirmar antes de ejecutar».",
            otras.takeIf { it.isNotEmpty() }?.let {
                "Para cada banco más agrega otra tarjeta «Mensaje» (quedan unidas por «o»), no «+ Agregar filtro»: " +
                    "un filtro se une con «y» y la automatización no correría nunca. $it."
            },
            PROBAR_CON_UN_SMS_REAL,
        )
        VarianteDeLaAutomatizacion.IOS_17_A_26 -> listOfNotNull(
            "En Atajos toca la pestaña «Automatización» y después «+» (o «Nueva automatización»).",
            "Elige «Mensaje», toca «El mensaje contiene» y escribe «$ejemplo».",
            "Elige «Ejecutar de inmediato» y toca «Siguiente».",
            "Elige el atajo «$NOMBRE_DEL_ATAJO_DE_MENSAJES».",
            otras.takeIf { it.isNotEmpty() }?.let { "Haz una automatización más con cada uno: $it." },
            PROBAR_CON_UN_SMS_REAL,
        )
        VarianteDeLaAutomatizacion.ANTES_DE_IOS_17 -> listOf(ANTES_DE_IOS_17)
    }
}

/** El último paso: la palabra hay que comprobarla con un SMS de verdad, porque cada banco firma distinto. */
const val PROBAR_CON_UN_SMS_REAL: String =
    "Cuando te llegue un SMS real del banco, mira que aparezca en «Por revisar». Si no llega, revisa que el " +
        "SMS diga esa palabra tal cual."

/** Los pasos de la automatización de Apple Pay (iOS 17 a 26: la pestaña «Automatización»). */
val PASOS_DE_LA_AUTOMATIZACION_DE_APPLE_PAY: List<String> = listOf(
    "En la pestaña «Automatización» toca «+» y elige «Transacción» (Wallet).",
    "Marca las tarjetas que usas con Apple Pay, elige «Ejecutar de inmediato» y toca «Siguiente».",
    "Elige el atajo «$NOMBRE_DEL_ATAJO_DE_APPLE_PAY».",
)

/**
 * Vuelta 114 · **Apple Pay en iOS 27, sin verificar**: no se adelanta un camino que nadie probó; solo se dice
 * que falta verificarlo y dónde mirar, como con la de mensajes.
 */
const val APPLE_PAY_EN_IOS_27: String =
    "En iOS 27 todavía no lo verificamos: si no ves la pestaña «Automatización», búscala dentro del atajo, " +
        "como la de mensajes."

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
 * dueño (`palabrasParaLaAutomatizacion`); [ahora] se inyecta para las pruebas. [versionDeIos] elige los pasos de
 * la automatización (vuelta 114; `null` si no se sabe con seguridad: se ven todas las variantes). [reintento]
 * vuelve a leer el código (el «Reintentar» de la pantalla) y [alCambiarElCodigo] avisa cada estado del código.
 */
@Composable
fun DesdeTuIphone(
    palabras: List<String>,
    modifier: Modifier = Modifier,
    ahora: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
    versionDeIos: Int? = com.jvillada.movi.platform.versionDeIos(),
    reintento: Int = 0,
    alCambiarElCodigo: (CodigoDeCaptura?) -> Unit = {},
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
    var otraVersionAbierta by remember { mutableStateOf(false) }
    var lectura by remember { mutableStateOf(0) }

    /** Lo que mandaron los atajos y no se guardó en 30 días; `null` mientras no se sabe. */
    var descartados by remember { mutableStateOf<ResumenDeAtajosDescartados?>(null) }

    LaunchedEffect(lectura, reintento) {
        estado = intentar { Repositories.wallets.getCodigoDeCaptura() }
            .getOrNull()?.let { EstadoDelCodigo.Listo(it) } ?: EstadoDelCodigo.NoSePudo
        descartados = intentar { Repositories.wallets.getAtajosDescartados() }.getOrNull()
    }

    // Vuelta 114 · quien la pinta (Mensajes del banco) se entera de cada estado leído, creado o desactivado: así
    // la línea de arriba no queda diciendo un código viejo, y el código se lee una sola vez por pantalla.
    // Mientras lee no se avisa nada (no se borra lo que ya se sabía); si no se pudo leer, `null`.
    LaunchedEffect(estado) {
        when (val e = estado) {
            is EstadoDelCodigo.Listo -> alCambiarElCodigo(e.codigo)
            EstadoDelCodigo.NoSePudo -> alCambiarElCodigo(null)
            EstadoDelCodigo.Leyendo -> Unit
        }
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
                Text(AL_AGREGAR_EL_ATAJO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xxs))
                Text(PLAN_B_DEL_CODIGO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xxs))
                Text(CAMBIAR_EL_CODIGO_DEL_ATAJO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
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
                val variantes = variantesDeLaGuia(versionDeIos)
                val conRotulo = variantes.aLaVista.size > 1
                variantes.aLaVista.forEach { variante ->
                    Spacer(Modifier.height(Espacio.xs))
                    VarianteDeLaAutomatizacionDeMensajes(palabras, variante, conRotulo)
                }
                if (variantes.detras.isNotEmpty()) {
                    Spacer(Modifier.height(Espacio.xs))
                    Boton(
                        TU_IPHONE_MUESTRA_OTRA_COSA,
                        onClick = { otraVersionAbierta = !otraVersionAbierta },
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                    )
                    if (otraVersionAbierta) {
                        variantes.detras.forEach { variante ->
                            Spacer(Modifier.height(Espacio.xs))
                            VarianteDeLaAutomatizacionDeMensajes(palabras, variante, conRotulo = true)
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.s))
                Text("Para los pagos con Apple Pay:", style = Movi.textos.apoyo, color = Movi.colores.texto)
                Spacer(Modifier.height(Espacio.xs))
                Text(ROTULO_IOS_17_A_26, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                RecetaDelAtajo(PASOS_DE_LA_AUTOMATIZACION_DE_APPLE_PAY)
                Spacer(Modifier.height(Espacio.xs))
                Text(APPLE_PAY_EN_IOS_27, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
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

/**
 * Vuelta 114 · una variante de los pasos de la automatización de mensajes: su rótulo (si se ve más de una) y sus
 * pasos; la de antes de iOS 17 es una sola línea.
 */
@Composable
private fun VarianteDeLaAutomatizacionDeMensajes(palabras: List<String>, variante: VarianteDeLaAutomatizacion, conRotulo: Boolean) {
    when (variante) {
        VarianteDeLaAutomatizacion.ANTES_DE_IOS_17 -> Text(
            ANTES_DE_IOS_17,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.testTag(TAG_ANTES_DE_IOS_17),
        )
        else -> {
            val esLaDe27 = variante == VarianteDeLaAutomatizacion.IOS_27_O_MAS
            Column(modifier = Modifier.testTag(if (esLaDe27) TAG_PASOS_IOS_27 else TAG_PASOS_IOS_17_A_26)) {
                if (conRotulo) {
                    Text(
                        if (esLaDe27) ROTULO_IOS_27_O_MAS else ROTULO_IOS_17_A_26,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.texto,
                    )
                }
                RecetaDelAtajo(pasosDeLaAutomatizacionDeMensajes(palabras, variante))
            }
        }
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
