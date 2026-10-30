package com.jvillada.movi.ui.components.ds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Sombra
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * # El aviso de abajo: «Anotado · Deshacer» (vuelta 67, generalizado en la vuelta 74)
 *
 * Vuelta 67: anular era la única acción de la app sin vuelta atrás a la vista; al anular, abajo
 * aparecía «Anulado» con «Deshacer».
 *
 * Vuelta 74 («un toque por movimiento»): **es EL aviso de la app** para todo lo que guarda un
 * movimiento —Agregar (gasto, ingreso, traspaso), confirmar un aviso del banco, «Es este»— y para
 * anular. Dice qué quedó («Anotado · Restaurantes $32.000»), ofrece «Deshacer» durante
 * [DURACION_DEL_AVISO_DE_DESHACER] y, si hace falta, **una acción más** ([AvisoDeDeshacer.Accion]):
 * «Repartir con Caro», que al hacerse cambia el aviso por el suyo («Repartido con Caro · tu parte
 * $16.000 · Deshacer»). Una acción con varias opciones (varios grupos) abre una hoja para elegir.
 *
 * **Vive en la cáscara** (App.kt lo provee con [LocalAvisoDeDeshacer] y lo pinta abajo del hueco de
 * la pantalla): así no tapa la barra de pestañas (está encima de ella) ni el «+» (que vive en el rail),
 * y sobrevive a que se cierre la hoja o la pantalla que lo pidió. Entra y sale con el movimiento de
 * siempre ([Movimiento]), que con el sistema pidiendo menos movimiento pasa en el acto.
 */

/** Cuánto dura el aviso a la vista (ms). Vuelta 74: unos 8 s para todo (antes, 10 s el de anular). */
const val DURACION_DEL_AVISO_DE_DESHACER: Long = 8_000L

/** El tag del aviso, el de su «Deshacer» y el de su acción. */
const val TAG_AVISO_DE_DESHACER: String = "aviso-de-deshacer"
const val TAG_DESHACER_DEL_AVISO: String = "aviso-de-deshacer:deshacer"
const val TAG_ACCION_DEL_AVISO: String = "aviso-de-deshacer:accion"
const val TAG_ELEGIR_EN_EL_AVISO: String = "aviso-de-deshacer:elegir"
const val TAG_TEXTO_DEL_AVISO: String = "aviso-de-deshacer:texto"

/** Revisión de la vuelta 82: lo que va después de « · » en el aviso (la categoría), lo que se corta. */
const val TAG_RESTO_DEL_AVISO: String = "aviso-de-deshacer:resto"

/** Revisión de la vuelta 82: el monto del aviso, en su propia pieza (nunca se corta). */
const val TAG_CIFRA_DEL_AVISO: String = "aviso-de-deshacer:cifra"

/**
 * **El aviso en partes** (revisión de la vuelta 82): el [verbo] («Anotado», «Ya estaba anotado», «Tu
 * parte», o todo el texto si no trae cifra: «Deshecho»), la [cifra] («$12.300», «US$45») —la última palabra
 * antes del primer « · », si tiene un dígito— y el [resto] desde « · » («· Restaurantes»).
 */
data class PartesDelAviso(val verbo: String, val cifra: String?, val resto: String?)

/** Ver [PartesDelAviso]. Pura. */
fun partesDelAviso(texto: String): PartesDelAviso {
    val i = texto.indexOf(" · ")
    val cabeza = if (i <= 0) texto else texto.substring(0, i)
    val resto = if (i <= 0) null else texto.substring(i + 1)
    var espacio = cabeza.lastIndexOf(' ')
    val ultima = if (espacio < 0) cabeza else cabeza.substring(espacio + 1)
    if (espacio <= 0 || ultima.none { it.isDigit() }) return PartesDelAviso(cabeza, null, resto)
    // Re-revisión de la vuelta 82: una moneda que no es pesos ni dólares se escribe «EUR 45»
    // (`formatMoney`): el código va con la cifra, entero, y no en el verbo que se puede cortar.
    val anterior = cabeza.lastIndexOf(' ', espacio - 1)
    val palabraAnterior = cabeza.substring(anterior + 1, espacio)
    if (anterior > 0 && palabraAnterior.length == 3 && palabraAnterior.all { it in 'A'..'Z' }) espacio = anterior
    return PartesDelAviso(cabeza.substring(0, espacio), cabeza.substring(espacio + 1), resto)
}

/**
 * **El texto del aviso, acomodado** (revisión de la vuelta 82): la cifra se mide primero y entera (si cabe
 * ella sola en el ancho); el verbo toma lo que le queda antes de ella (se corta, o se va, si no cabe); lo
 * que se corta con «…» es el resto. Un renglón, como siempre.
 */
@Composable
private fun TextoDelAviso(partes: PartesDelAviso, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val estilo = Movi.textos.cuerpo
    androidx.compose.ui.layout.Layout(
        modifier = modifier.semantics(mergeDescendants = true) {},
        content = {
            Text(partes.verbo, style = estilo, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(TAG_TEXTO_DEL_AVISO))
            if (partes.cifra != null) {
                Text(partes.cifra, style = estilo, color = color, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = Espacio.xxs).testTag(TAG_CIFRA_DEL_AVISO))
            }
            if (partes.resto != null) {
                Text(partes.resto, style = estilo, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Espacio.xxs).testTag(TAG_RESTO_DEL_AVISO))
            }
        },
    ) { medibles, restricciones ->
        val ancho = if (restricciones.hasBoundedWidth) restricciones.maxWidth else Int.MAX_VALUE
        val verbo = medibles[0]
        val cifra = if (partes.cifra != null) medibles[1] else null
        val resto = if (partes.resto != null) medibles.last() else null
        val libre = androidx.compose.ui.unit.Constraints()
        fun hasta(max: Int) = androidx.compose.ui.unit.Constraints(maxWidth = max.coerceAtLeast(0))
        val pc = cifra?.let { it.measure(hasta(minOf(it.maxIntrinsicWidth(libre.maxHeight), ancho))) }
        val anchoDeLaCifra = pc?.width ?: 0
        val pv = verbo.measure(hasta(minOf(verbo.maxIntrinsicWidth(libre.maxHeight), ancho - anchoDeLaCifra)))
        val pr = resto?.measure(hasta(ancho - anchoDeLaCifra - pv.width))
        val piezas = listOfNotNull(pv, pc, pr)
        val alto = piezas.maxOf { it.height }
        val total = piezas.sumOf { it.width }
        layout(if (restricciones.hasBoundedWidth) restricciones.maxWidth else total, alto) {
            var x = 0
            piezas.forEach { p ->
                p.place(x, (alto - p.height) / 2)
                x += p.width
            }
        }
    }
}

/**
 * **Un renglón** (vuelta 79; eran dos): el aviso es un snackbar oscuro, y lo que no cabe a 360 dp se corta
 * con «…» —las acciones quedan siempre a la vista—.
 */
const val RENGLONES_DEL_AVISO: Int = 1

/** Lo que dice el aviso al anular. */
const val ANULADO: String = "Anulado"

/**
 * Vuelta 82: lo que dice el aviso cuando «Deshacer» salió bien, durante [DURACION_DEL_DESHECHO]. Antes el
 * aviso se iba sin decir nada y el dueño no sabía si el toque había llegado.
 */
const val DESHECHO: String = "Deshecho"

/** Cuánto se ve «Deshecho» (ms): lo justo para leerlo. */
const val DURACION_DEL_DESHECHO: Long = 1_500L

/** El botón de deshacer. */
const val DESHACER: String = "Deshacer"

/**
 * Una falla que trae su frase para el aviso (por ejemplo, [com.jvillada.movi.ui.espacios.REPARTO_SIN_CONEXION]):
 * el aviso la dice tal cual, sin pasarla por `toUserMessage`.
 */
class FalloDelAviso(val frase: String) : Exception(frase)

/**
 * El aviso con «Deshacer» de la cáscara. [mostrar] lo pone (reemplaza al que hubiera); [alCambiar] se
 * llama cuando deshacer o la acción salieron bien, para que las pantallas relean.
 */
@Stable
class AvisoDeDeshacer(private val alCambiar: () -> Unit = {}) {
    /** Una opción de una [Accion] que pide elegir (un grupo de varios). */
    data class Opcion(val rotulo: String, val detalle: String? = null, val hacer: suspend () -> Unit)

    /**
     * La acción del aviso, además de «Deshacer» (vuelta 74: «Repartir con Caro»). Con [opciones], tocarla
     * abre una hoja para elegir; sin ellas, hace [hacer].
     */
    data class Accion(
        val rotulo: String,
        val hacer: suspend () -> Unit = {},
        val opciones: List<Opcion>? = null,
        val tituloDeLaEleccion: String = rotulo,
        /**
         * Vuelta 79: lo que se lee en el aviso de un renglón («Repartir»); el lector de pantalla dice el
         * [rotulo] entero («Repartir con Caro»). Sin él, el [rotulo].
         */
        val rotuloCorto: String? = null,
    )

    /** Lo que se ve, o `null`. [vez] distingue dos avisos con el mismo texto. Sin [deshacer], sin el botón. */
    data class Aviso(
        val texto: String,
        val vez: Long,
        val deshacer: (suspend () -> Unit)?,
        val accion: Accion? = null,
        /** Cuánto se ve (ms). Vuelta 82: «Deshecho» dura menos ([DURACION_DEL_DESHECHO]). */
        val duracion: Long = DURACION_DEL_AVISO_DE_DESHACER,
    )

    var actual by mutableStateOf<Aviso?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var trabajando by mutableStateOf(false)
        private set
    /** La hoja de elegir de una [Accion] con opciones está abierta: el aviso espera. */
    var eligiendo by mutableStateOf(false)
        private set
    /**
     * Cuántas veces se deshizo algo en esta sesión: quien ofrece algo después de guardar (el «¿se
     * repite?» de App.kt) lo mira para no ofrecerlo sobre un movimiento que ya no está.
     */
    var deshechos by mutableStateOf(0)
        private set
    private var veces = 0L

    fun mostrar(texto: String, accion: Accion? = null, deshacer: (suspend () -> Unit)? = null) {
        // Vuelta 79: lo que dice el aviso lo acaba de hacer el dueño; la cifra del héroe lo cuenta.
        CambioDelDueno.marcar()
        error = null
        trabajando = false
        eligiendo = false
        actual = Aviso(texto, ++veces, deshacer, accion)
    }

    fun cerrar(vez: Long? = null) {
        if (vez == null || actual?.vez == vez) {
            actual = null
            error = null
            eligiendo = false
        }
    }

    internal suspend fun deshacer() {
        val aviso = actual ?: return
        val accion = aviso.deshacer ?: return
        correr(aviso) {
            accion()
            deshechos++
            // Vuelta 82: «Deshecho», un momento, sin botones. `correr` no lo cierra: es otro aviso.
            error = null
            actual = Aviso(DESHECHO, ++veces, deshacer = null, duracion = DURACION_DEL_DESHECHO)
        }
    }

    /** Toca la acción: abre la elección si tiene opciones, o la hace. */
    internal suspend fun tocarLaAccion() {
        val aviso = actual ?: return
        val accion = aviso.accion ?: return
        if (accion.opciones != null) {
            if (!trabajando) eligiendo = true
            return
        }
        correr(aviso, accion.hacer)
    }

    /** Eligió [opcion] en la hoja de la acción. */
    internal suspend fun elegir(opcion: Opcion) {
        val aviso = actual ?: return
        eligiendo = false
        correr(aviso, opcion.hacer)
    }

    internal fun dejarDeElegir() {
        eligiendo = false
    }

    /**
     * Corre [bloque] con el aviso «trabajando». Bien: se cierra —salvo que el bloque haya puesto otro
     * aviso (la acción que se dice a sí misma: «Repartido con Caro»)— y las pantallas releen. Mal: el
     * aviso se queda y dice por qué.
     */
    private suspend fun correr(aviso: Aviso, bloque: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        try {
            bloque()
            // Vuelta 79: deshacer (o repartir) también es un cambio del dueño: la cifra cuenta hacia atrás.
            CambioDelDueno.marcar()
            if (actual?.vez == aviso.vez) cerrar(aviso.vez)
            alCambiar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FalloDelAviso) {
            if (actual?.vez == aviso.vez) error = e.frase
        } catch (e: Throwable) {
            if (actual?.vez == aviso.vez) error = e.toUserMessage()
        } finally {
            trabajando = false
        }
    }
}

/** El aviso de la cáscara; `null` donde no hay cáscara (una prueba que monta solo una pantalla). */
val LocalAvisoDeDeshacer = staticCompositionLocalOf<AvisoDeDeshacer?> { null }

/**
 * El aviso, abajo de la pantalla: el texto, la acción (si hay) y «Deshacer». Se va solo a los
 * [DURACION_DEL_AVISO_DE_DESHACER] —la cuenta se para mientras trabaja o mientras se elige—.
 */
@Composable
fun AnfitrionDelAvisoDeDeshacer(aviso: AvisoDeDeshacer, modifier: Modifier = Modifier) {
    val actual = aviso.actual
    val reducido = LocalMovimientoReducido.current
    // Lo último que se vio, para que el aviso salga animado con su texto (no en blanco). Un simple
    // recuerdo, no un estado: escribirlo mientras se compone no tiene que recomponer nada.
    val ultimo = remember { arrayOfNulls<AvisoDeDeshacer.Aviso>(1) }
    if (actual != null) ultimo[0] = actual
    val coroutine = rememberCoroutineScope()
    if (actual != null) {
        LaunchedEffect(actual.vez, aviso.trabajando, aviso.eligiendo) {
            if (aviso.trabajando || aviso.eligiendo) return@LaunchedEffect
            delay(actual.duracion)
            aviso.cerrar(actual.vez)
        }
    }
    // Vuelta 79: entra **en el flujo** (abajo de la pantalla, arriba de la barra): crece y le quita su alto
    // al hueco de la pantalla, así no tapa lo de abajo —la propuesta de Inicio— ni la barra.
    AnimatedVisibility(
        visible = actual != null,
        modifier = modifier,
        enter = fadeIn(Movimiento.alEntrar(reducido)) + expandVertically(Movimiento.alEntrar(reducido), expandFrom = Alignment.Top),
        exit = fadeOut(Movimiento.alSalir(reducido)) + shrinkVertically(Movimiento.alSalir(reducido), shrinkTowards = Alignment.Top),
    ) {
        val visto = actual ?: ultimo[0] ?: return@AnimatedVisibility
        SnackbarDeMovi(
            texto = aviso.error ?: visto.texto,
            esError = aviso.error != null,
            accion = visto.accion?.let { accion ->
                AccionDelSnackbar(
                    rotulo = accion.rotuloCorto ?: accion.rotulo,
                    descripcion = accion.rotulo,
                    tag = TAG_ACCION_DEL_AVISO,
                    habilitada = !aviso.trabajando,
                    alTocar = { coroutine.launch { aviso.tocarLaAccion() } },
                )
            },
            deshacer = visto.deshacer?.let {
                AccionDelSnackbar(
                    rotulo = DESHACER,
                    descripcion = DESHACER,
                    tag = TAG_DESHACER_DEL_AVISO,
                    habilitada = !aviso.trabajando,
                    cargando = aviso.trabajando,
                    alTocar = { coroutine.launch { aviso.deshacer() } },
                )
            },
        )
    }
    // La elección de una acción con opciones (varios grupos): una hoja con una fila por opción.
    val opciones = actual?.accion?.opciones
    if (aviso.eligiendo && actual != null && opciones != null) {
        MarcoDeHoja(onDismiss = { aviso.dejarDeElegir() }) {
            EncabezadoDeHoja(actual.accion.tituloDeLaEleccion)
            Column(modifier = Modifier.padding(top = Espacio.s, bottom = Medida.margenDePantalla)) {
                Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                    opciones.forEachIndexed { i, opcion ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = opcion.rotulo,
                            subtitulo = opcion.detalle,
                            onClick = { coroutine.launch { aviso.elegir(opcion) } },
                            modifier = Modifier.testTag("$TAG_ELEGIR_EN_EL_AVISO:$i"),
                        )
                    }
                }
            }
        }
    }
}

/** Una acción de texto del snackbar: «Repartir», «Deshacer». */
internal data class AccionDelSnackbar(
    val rotulo: String,
    val descripcion: String,
    val tag: String,
    val habilitada: Boolean,
    val alTocar: () -> Unit,
    val cargando: Boolean = false,
)

/**
 * **El snackbar de Movi** (vuelta 79): un renglón oscuro —fondo en la tinta (`texto`), la letra en
 * `sobreTinta`— con las acciones **como texto, sin bordes**, en `enlaceSobreTinta` («Repartir · Deshacer»).
 * El texto se corta con «…» si no cabe; las acciones nunca. `ContrasteDeLosTokensTest` mide los tres pares.
 * La falla de deshacer se dice en `saleSobreTinta`.
 */
@Composable
internal fun SnackbarDeMovi(
    texto: String,
    esError: Boolean,
    accion: AccionDelSnackbar?,
    deshacer: AccionDelSnackbar?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs)
            .shadow(Sombra.destacada, RoundedCornerShape(Radio.m), clip = false, ambientColor = Movi.colores.sombra, spotColor = Movi.colores.sombra)
            .clip(RoundedCornerShape(Radio.m))
            .background(Movi.colores.texto)
            .heightIn(min = Medida.objetivoTactil)
            .testTag(TAG_AVISO_DE_DESHACER)
            .padding(start = Espacio.m, end = Espacio.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Revisión de la vuelta 82: el aviso en tres partes ([partesDelAviso]) —«Anotado», «$12.300» y
        // «· Restaurantes»— que [TextoDelAviso] acomoda con el monto primero: entero siempre que quepa él
        // solo; después el verbo; lo que se corta es la categoría. Con la letra grande a 360 dp y un monto de
        // ocho cifras, cae el verbo antes que una cifra del monto.
        TextoDelAviso(
            partes = partesDelAviso(texto),
            color = if (esError) Movi.colores.saleSobreTinta else Movi.colores.sobreTinta,
            modifier = Modifier.weight(1f),
        )
        val acciones = listOfNotNull(accion, deshacer)
        acciones.forEachIndexed { i, a ->
            if (i > 0) {
                Text("·", style = Movi.textos.cuerpo, color = Movi.colores.sobreTinta, modifier = Modifier.clearAndSetSemantics {})
            }
            AccionDeTextoDelSnackbar(a)
        }
    }
}

@Composable
private fun AccionDeTextoDelSnackbar(accion: AccionDelSnackbar) {
    val forma = RoundedCornerShape(Radio.s)
    Box(
        modifier = Modifier
            .heightIn(min = Medida.objetivoTactil)
            .anilloDeFoco(forma, adentro = true)
            .alPresionar(onClick = accion.alTocar, habilitado = accion.habilitada, rol = Role.Button, forma = forma)
            .semantics { if (accion.descripcion != accion.rotulo) contentDescription = accion.descripcion }
            .testTag(accion.tag)
            .padding(horizontal = Espacio.s),
        contentAlignment = Alignment.Center,
    ) {
        if (accion.cargando) {
            CircularProgressIndicator(
                modifier = Modifier.size(Icono.s).semantics { stateDescription = ROTULO_CARGANDO },
                color = Movi.colores.enlaceSobreTinta,
                strokeWidth = Medida.trazoDeCarga,
            )
        } else {
            Text(
                accion.rotulo,
                style = Movi.textos.cuerpoFuerte,
                color = Movi.colores.enlaceSobreTinta,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
