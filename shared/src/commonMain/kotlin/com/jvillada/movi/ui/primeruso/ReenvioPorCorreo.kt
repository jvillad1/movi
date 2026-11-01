package com.jvillada.movi.ui.primeruso

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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.FiltroDeGmail
import com.jvillada.movi.shared.model.NOMBRE_DEL_ARCHIVO_DEL_FILTRO
import kotlinx.coroutines.launch
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.recurrentes.NumeroEnCirculo
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco

/*
 * # Recibir las alertas del banco por correo (vuelta 31)
 *
 * La pieza que la guía de primer uso ya mostraba en la web y en iOS —tu dirección de Movi para
 * copiar y cómo crear el reenvío en Gmail—, sacada de `PrimerUsoScreen` para que Ajustes la use
 * igual («Recibe tus alertas por correo»). Una sola pieza: la guía y Ajustes no pueden explicar el
 * reenvío de dos maneras.
 */

/**
 * **Los tres pasos del reenvío** (vuelta 93), cortos y en el orden en que se hacen. El detalle de cada uno
 * (dónde se toca en Gmail, el código, la búsqueda) va debajo del título, dentro de [ReenvioPorCorreo].
 */
val PASOS_DEL_REENVIO_EN_GMAIL: List<String>
    get() = listOf(
        "Agrega a Movi como dirección de reenvío en Gmail.",
        "Movi te muestra el código de confirmación.",
        "Crea el filtro.",
    )

/** Cómo se llega a «Agregar una dirección de reenvío» en Gmail. */
const val DONDE_AGREGAR_EL_REENVIO: String =
    "En Gmail abre Configuración → «Ver todos los ajustes» → «Reenvío y correo POP/IMAP» → " +
        "«Agregar una dirección de reenvío», y pega la dirección de arriba."

/** Cómo se pega la búsqueda en Gmail. */
const val DONDE_PEGAR_LA_BUSQUEDA: String =
    "En Gmail abre Configuración → «Filtros y direcciones bloqueadas» → «Crear un filtro», pega la búsqueda " +
        "en «Incluye las palabras», y elige «Reenviarlo a» tu dirección de Movi."

/** Cómo se importa el archivo en Gmail. */
const val DONDE_IMPORTAR_EL_FILTRO: String =
    "O impórtalo: en «Filtros y direcciones bloqueadas» toca «Importar filtros», elige el archivo y " +
        "«Crear filtros»."

/** La línea que avisa que algunos bancos no mandan nada hasta que se les pide. */
const val ALGUNOS_BANCOS_PIDEN_ACTIVARLO: String =
    "Algunos bancos solo mandan alertas por correo si las activas en su app."

const val TAG_CODIGO_DE_GMAIL: String = "codigo-de-gmail"
const val TAG_BUSCAR_EL_CODIGO: String = "buscar-el-codigo-de-gmail"
const val TAG_COPIAR_LA_BUSQUEDA: String = "copiar-la-busqueda-de-gmail"
const val TAG_DESCARGAR_EL_FILTRO: String = "descargar-el-filtro-de-gmail"

/** El tag del texto con la dirección de Movi, para las pruebas. */
const val TAG_DIRECCION_DE_MOVI: String = "direccion-de-movi"

/**
 * **La dirección de Movi del dueño** (`GET /api/correo-entrante/direccion`). [leida] es que la lectura
 * terminó; [direccion] es `null` mientras no llegó, si falló o si el server no tiene el reenvío
 * configurado.
 */
@Stable
class DireccionDeMovi internal constructor() {
    var leida: Boolean by mutableStateOf(false)
        internal set
    var direccion: String? by mutableStateOf(null)
        internal set

    /**
     * **El filtro de Gmail** (`GET /api/correo-entrante/filtro`, vuelta 93): la búsqueda, el XML y el código
     * de confirmación. `null` mientras no llegó o si el server no lo tiene (uno viejo): los tres pasos
     * siguen, sin lo que depende de él.
     */
    var filtro: FiltroDeGmail? by mutableStateOf(null)
        internal set
}

/** Lee la dirección una vez por composición. Sin red o sin configurar, queda en `null`. */
@Composable
fun rememberDireccionDeMovi(): DireccionDeMovi {
    val estado = remember { DireccionDeMovi() }
    LaunchedEffect(Unit) {
        val leida = runCatching { Repositories.wallets.getDireccionDeCorreo() }.getOrNull()
        estado.direccion = leida?.takeIf { it.configurado }?.direccion
        estado.filtro = runCatching { Repositories.wallets.getFiltroDeCorreo() }.getOrNull()
        estado.leida = true
    }
    return estado
}

/**
 * **Tu dirección de Movi y cómo crear el reenvío**: la dirección con «Copiar» y los pasos de Gmail,
 * numerados. Mientras lee, un esqueleto; sin dirección, que todavía no está disponible.
 */
@Composable
fun ReenvioPorCorreo(
    direccion: DireccionDeMovi,
    onCopiada: () -> Unit = {},
    sinDireccion: String = "El reenvío por correo todavía no está disponible.",
) {
    val hoja = hojaDeCompartir()
    var copiada by remember { mutableStateOf(false) }
    Seccion("Tu dirección de Movi")
    val laDireccion = direccion.direccion
    when {
        !direccion.leida -> Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) { EsqueletoDeFila() }
        laDireccion == null -> Text(
            sinDireccion,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        else -> {
            Superficie(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        laDireccion,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        modifier = Modifier.testTag(TAG_DIRECCION_DE_MOVI),
                    )
                }
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    if (copiada) "Copiada" else "Copiar",
                    onClick = {
                        hoja.copiar(laDireccion)
                        copiada = true
                        onCopiada()
                    },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.ContentCopy,
                )
            }
            Spacer(Modifier.height(Medida.entreSecciones))
            Seccion("Cómo armar el reenvío en Gmail")
            PasosDelReenvio(direccion)
        }
    }
}

/**
 * **Los tres pasos** (vuelta 93), cada uno con su título y, debajo, lo que hace falta para darlo: dónde se
 * toca en Gmail, el código que Movi recibió y los botones de la búsqueda. Mismas piezas que el resto de la
 * guía (`Superficie` de lista, `NumeroEnCirculo`, `Boton` secundario): ninguno es el botón amarillo, que en
 * esta vista es de «Siguiente».
 */
@Composable
private fun PasosDelReenvio(direccion: DireccionDeMovi) {
    val hoja = hojaDeCompartir()
    val alcance = rememberCoroutineScope()
    // Qué se copió último ("codigo" o "b0", "b1"…): el botón lo dice, para que copiar no sea mudo.
    var copiado by remember { mutableStateOf<String?>(null) }
    val filtro = direccion.filtro
    val titulos = PASOS_DEL_REENVIO_EN_GMAIL
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        PasoDelReenvio(1, titulos[0]) {
            Text(DONDE_AGREGAR_EL_REENVIO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        Separador()
        PasoDelReenvio(2, titulos[1]) {
            val codigo = filtro?.codigoDeConfirmacion
            if (codigo != null) {
                Text(
                    "Tu código es $codigo. Escríbelo en Gmail.",
                    style = Movi.textos.cuerpoFuerte,
                    color = Movi.colores.texto,
                    modifier = Modifier.testTag(TAG_CODIGO_DE_GMAIL),
                )
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    if (copiado == "codigo") "Copiado" else "Copiar el código",
                    onClick = { hoja.copiar(codigo); copiado = "codigo" },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.ContentCopy,
                )
            } else {
                Text(
                    "Gmail manda un código a Movi. Cuando llegue, lo ves aquí; también queda en Ajustes → " +
                        "«$tituloDeCapturaDelBanco».",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    "Buscar el código",
                    onClick = {
                        alcance.launch {
                            runCatching { Repositories.wallets.getFiltroDeCorreo() }.getOrNull()
                                ?.let { direccion.filtro = it }
                        }
                    },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.Refresh,
                    modifier = Modifier.testTag(TAG_BUSCAR_EL_CODIGO),
                )
            }
        }
        Separador()
        PasoDelReenvio(3, titulos[2]) {
            val busquedas = filtro?.busquedas.orEmpty()
            Text(DONDE_PEGAR_LA_BUSQUEDA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            if (busquedas.isNotEmpty()) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                busquedas.forEachIndexed { i, busqueda ->
                    if (i > 0) Spacer(Modifier.height(Espacio.xs))
                    val queDice = if (busquedas.size == 1) "la búsqueda" else "la búsqueda ${i + 1} de ${busquedas.size}"
                    Boton(
                        if (copiado == "b$i") "Copiada" else "Copiar $queDice",
                        onClick = { hoja.copiar(busqueda); copiado = "b$i" },
                        variante = VarianteDeBoton.Secundario,
                        tamano = TamanoDeBoton.S,
                        icono = Icons.Rounded.ContentCopy,
                        modifier = Modifier.testTag(TAG_COPIAR_LA_BUSQUEDA + "-$i"),
                    )
                }
                if (busquedas.size > 1) {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(
                        "Son ${busquedas.size} filtros: crea uno con cada búsqueda.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }
            val xml = filtro?.xml
            if (xml != null && hoja.puedeDescargar) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    "Descargar el filtro",
                    onClick = { hoja.descargar(NOMBRE_DEL_ARCHIVO_DEL_FILTRO, xml, "text/xml") },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.Download,
                    modifier = Modifier.testTag(TAG_DESCARGAR_EL_FILTRO),
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(DONDE_IMPORTAR_EL_FILTRO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            if (filtro != null && filtro.remitentes.isNotEmpty()) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                val cuantos = filtro.remitentes.map { it.nombre }.distinct().size
                Text(
                    "El filtro cubre $cuantos bancos y billeteras de Colombia.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
        }
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Text(ALGUNOS_BANCOS_PIDEN_ACTIVARLO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
}

/** Un paso: el número, el título y lo que haga falta debajo. */
@Composable
private fun PasoDelReenvio(numero: Int, titulo: String, detalle: @Composable () -> Unit) {
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
