package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * # ⌘A no seleccionaba nada, y eso corrompía cifras de plata
 *
 * **El defecto, reportado por el dueño y confirmado con un teclado físico:** en la web, sobre un
 * monto de $18.000.009, seleccionar todo con **⌘A** y escribir `7000` dejaba **$180.000.097.000**.
 * Con **triple-click** el mismo campo daba $7.000 exacto. O sea que reemplazar funciona; lo que
 * fallaba era el atajo de teclado.
 *
 * ## Por qué pasa (medido, no deducido)
 *
 * Una sonda en la página, escuchando en las dos fases, dio esto al presionar ⌘A con el campo
 * enfocado y `18000009` escrito:
 *
 * ```
 * captura:  keydown key=a meta=true  prevented=false
 * burbuja:  prevented=true                     <- Compose la canceló
 * <input>:  value="18000009"  sel=8..8         <- y no seleccionó nada
 * ```
 *
 * **Compose-wasm 1.8.2 intercepta ⌘A —le hace `preventDefault`, matando el «seleccionar todo»
 * nativo del navegador— y después no implementa el suyo.** La app nunca recibe una selección, así
 * que la tecla siguiente le llega como «escribir al final»: por eso concatena en vez de reemplazar.
 * Es un defecto de la plataforma, no de este repo; el triple-click se salva porque ese camino sí
 * produce una selección de verdad.
 *
 * ## Por qué ESTE arreglo y no otro
 *
 * `MoneyField` ya tiene documentado un intento fallido en la dirección vecina: el botón de «borrar
 * todo» se sacó porque **vaciar el campo desde el estado de la app deja el `<input>` oculto con el
 * texto viejo**, y el dígito siguiente se pegaba a lo que había ($71.800.000 sobre un «7»).
 *
 * La diferencia que hace que esto sí pueda funcionar: **acá el texto no se toca**. Solo se mueve la
 * selección dentro del mismo texto, que es justo el dato que Compose se estaba comiendo. No hay
 * buffer que resincronizar porque no hay nada nuevo que escribir.
 *
 * Vive en su propio archivo, y no adentro de `MoneyField`, porque el defecto es de **todo campo de
 * texto de la web**, no del monto: el mismo ⌘A fallaba igual en NOMBRE y en el campo de categoría.
 * Se arregló primero donde el error cuesta plata, y después en los otros quince campos de
 * `commonMain` — los que guardan un `String` a través de [CampoConSeleccion], que es lo que sigue.
 */
fun esAtajoDeSeleccionarTodo(evento: KeyEvent): Boolean =
    evento.type == KeyEventType.KeyDown &&
        evento.key == Key.A &&
        (evento.isMetaPressed || evento.isCtrlPressed)

/**
 * El mismo texto, entero seleccionado.
 *
 * `TextRange(0, length)` y no `TextRange(length, 0)`: con el ancla al final, escribir encima dejaba
 * el cursor al principio del texto nuevo.
 */
fun conTodoSeleccionado(valor: TextFieldValue): TextFieldValue =
    valor.copy(selection = TextRange(0, valor.text.length))

/**
 * Un campo de texto que sigue guardando un `String`, pero que además sabe dónde está la selección
 * —lo único que hace falta para poder implementar ⌘A a mano.
 *
 * ## Por qué hace falta un intermediario
 *
 * `BasicTextField(value: String, …)` no expone la selección: la guarda adentro y solo entrega
 * texto. Sin acceso a la selección no hay forma de responder al atajo, y por eso [MoneyField]
 * —que ya trabajaba con `TextFieldValue`— pudo arreglarse en una línea y el resto de los campos
 * de la app, no.
 *
 * Esta clase es exactamente lo que hace por dentro esa sobrecarga de Compose (guardar selección y
 * composición, cambiarle el texto por el que llega de afuera, avisar solo cuando el texto
 * cambió), con un agregado: [atajoDeSeleccionarTodo]. La pantalla sigue guardando su `String`
 * como siempre; no se le mueve el estado de lugar.
 *
 * ## La propiedad que hay que cuidar
 *
 * **Acá nunca se empuja texto hacia el campo.** [valor] arma el `TextFieldValue` con el texto que
 * ya venía de afuera y le pega la selección; el atajo solo escribe [seleccion]. Esto no es
 * prolijidad: empujar texto desde el estado de la app es el intento que **ya falló** —el botón de
 * «borrar todo» de [MoneyField] dejaba el `<input>` oculto de Compose con el buffer viejo y el
 * dígito siguiente se pegaba a lo que había. Mover la selección no toca ese buffer.
 *
 * Uso:
 * ```
 * val campo = rememberCampoConSeleccion(nombre) { nombre = it }
 * BasicTextField(
 *     value = campo.valor,
 *     onValueChange = campo::alCambiar,
 *     modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
 * )
 * ```
 */
@Stable
class CampoConSeleccion internal constructor(
    textoInicial: String,
    private val avisarTextoNuevo: (String) -> Unit,
) {
    /**
     * El texto **no vive acá**: vive donde ya vivía, en el estado de la pantalla. Esto es la última
     * copia que llegó por composición, y sirve para dos cosas: armar el `TextFieldValue` que el
     * campo pinta y saber hasta dónde llega «todo» cuando hay que seleccionarlo.
     */
    private var texto: String = textoInicial

    private var seleccion by mutableStateOf(TextRange(textoInicial.length))
    private var composicion by mutableStateOf<TextRange?>(null)

    /** Lo que se le pasa al campo: el texto de afuera, con la selección de acá. */
    val valor: TextFieldValue get() = TextFieldValue(texto, seleccion, composicion)

    /**
     * Se queda con la selección y la composición del IME siempre; el texto lo avisa hacia afuera
     * solo si cambió, para que mover el cursor no dispare el `onValueChange` de la pantalla.
     */
    fun alCambiar(entrante: TextFieldValue) {
        seleccion = entrante.selection
        composicion = entrante.composition
        if (entrante.text != texto) avisarTextoNuevo(entrante.text)
    }

    /**
     * Para `Modifier.onPreviewKeyEvent`. Guardado como propiedad y no como método: así el modifier
     * recibe siempre la misma lambda y no se reconstruye la cadena en cada recomposición.
     */
    val atajoDeSeleccionarTodo: (KeyEvent) -> Boolean = { evento ->
        esAtajoDeSeleccionarTodo(evento) && seleccionarTodo()
    }

    /**
     * Selecciona el texto entero y dice si se quedó con la tecla.
     *
     * Con el campo vacío devuelve `false` a propósito: no hay nada que seleccionar, y quedarse con
     * la tecla sería robarle al navegador algo que sí podría usar — mismo criterio que
     * [MoneyField]. Está separada del `KeyEvent` para poder probar esa decisión sin fabricar un
     * evento de teclado, que es una clase distinta en cada plataforma.
     */
    internal fun seleccionarTodo(): Boolean {
        if (texto.isEmpty()) return false
        seleccion = TextRange(0, texto.length)
        return true
    }

    /** Lo llama [rememberCampoConSeleccion] en cada composición. Ver [texto]. */
    internal fun sincronizar(textoDeAfuera: String) {
        texto = textoDeAfuera
    }
}

/**
 * El [CampoConSeleccion] de un campo, atado a la composición. Ver esa clase para el porqué.
 */
@Composable
fun rememberCampoConSeleccion(texto: String, alCambiarTexto: (String) -> Unit): CampoConSeleccion {
    // El aviso se lee por indirección: la pantalla puede pasar una lambda nueva en cada
    // recomposición, y el campo se recuerda una sola vez.
    val ultimoAviso = rememberUpdatedState(alCambiarTexto)
    val campo = remember { CampoConSeleccion(texto) { nuevo -> ultimoAviso.value(nuevo) } }
    campo.sincronizar(texto)
    return campo
}
