package com.jvillada.movi.ui.components

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
 * texto de la web**, no del monto: el mismo ⌘A falla igual en NOMBRE y en el campo de categoría.
 * Se arregla primero donde el error cuesta plata; el resto puede adoptarlo llamando a lo mismo.
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
