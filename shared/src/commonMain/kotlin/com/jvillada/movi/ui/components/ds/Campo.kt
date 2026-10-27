package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.conTodoSeleccionado
import com.jvillada.movi.ui.components.esAtajoDeSeleccionarTodo
import com.jvillada.movi.ui.components.moneyFieldFromDigits
import com.jvillada.movi.ui.components.nextMoneyField
import com.jvillada.movi.ui.components.parseMoneyDigits
import com.jvillada.movi.ui.components.rememberCampoConSeleccion

/** Tag del texto editable de un [Campo] (se puede cambiar con `tag`). */
const val TAG_CAMPO: String = "ds-campo"

/** Tag del texto editable de un [CampoDeMonto] (se puede cambiar con `tag`). */
const val TAG_CAMPO_DE_MONTO: String = "ds-campo-de-monto"

/** La caja de un campo (capa 2, borde, alto mínimo de 48): para medirla en una prueba. */
const val TAG_CAJA_DEL_CAMPO: String = "ds-campo-caja"

/** El rótulo de un campo, arriba de la caja. */
const val TAG_ROTULO_DEL_CAMPO: String = "ds-campo-rotulo"

/** El texto guía de un campo vacío. */
const val TAG_GUIA_DEL_CAMPO: String = "ds-campo-guia"

/** El símbolo de la moneda de un [CampoDeMonto]. */
const val TAG_MONEDA_DEL_CAMPO: String = "ds-campo-moneda"

/** La ayuda de una línea debajo de la caja. */
const val TAG_AYUDA_DEL_CAMPO: String = "ds-campo-ayuda"

/** «¿Qué es esto?»: abre y cierra la ayuda larga. */
const val TAG_QUE_ES_ESTO: String = "ds-campo-que-es-esto"

/** La ayuda larga, abierta. */
const val TAG_MAS_AYUDA_DEL_CAMPO: String = "ds-campo-mas-ayuda"

/** El error de un campo, en el color de alerta. */
const val TAG_ERROR_DEL_CAMPO: String = "ds-campo-error"

/** El título de un [GrupoDeCampos]. */
const val TAG_GRUPO_DE_CAMPOS: String = "ds-grupo-de-campos"

/** La razón por la que el botón de un [PieDeHoja] está apagado («Falta el banco»). */
const val TAG_RAZON_DEL_PIE: String = "ds-pie-razon"

/** El error del guardado, encima del botón de un [PieDeHoja]. */
const val TAG_ERROR_DEL_PIE: String = "ds-pie-error"

/** Lo que dice el enlace que abre la ayuda larga. */
const val TEXTO_QUE_ES_ESTO: String = "¿Qué es esto?"

/** Lo que dice el mismo enlace con la ayuda abierta. */
const val TEXTO_OCULTAR_AYUDA: String = "Ocultar"

/**
 * **El campo de texto de toda la app.** Vuelta de diseño 6.
 *
 * Antes cada hoja armaba el suyo: el rótulo en mayúsculas con un `SectionLabel` («TÉRMINOS»), el
 * nombre del campo metido en el texto guía (que se borra al escribir), un párrafo de tres o cuatro
 * renglones de ayuda debajo y la caja con el radio y el relleno que le tocara. Ahora la anatomía es
 * una sola, de arriba a abajo:
 *
 * 1. **El rótulo**, arriba y siempre, en frase (nunca en mayúsculas: si llega así, se pasa a frase),
 *    en `apoyo` medio y `textoMedio`. Es el nombre del campo: no se borra al escribir.
 * 2. **La caja**: capa 2, [Radio.s], relleno [Espacio.s], alto mínimo [Medida.objetivoTactil]. El
 *    borde se pinta en la marca con foco y en `sale` con error.
 * 3. **El texto guía**, en `cuerpo` y `textoApagado`: un ejemplo («Ej: Bancolombia Ahorros»), no el
 *    nombre del campo.
 * 4. **La ayuda**, de UNA línea, en `apoyo`. Lo que no cabe en una línea va en [masAyuda], que se
 *    abre con «¿Qué es esto?»: se mueve, no se borra.
 * 5. **El error**, en `sale`, en el lugar de la ayuda.
 *
 * El texto lo guarda la pantalla, como siempre; el campo lleva el ⌘A de la web
 * ([rememberCampoConSeleccion]).
 *
 * ```
 * Campo(nombre, { nombre = it }, rotulo = "Nombre", guia = "Ej: Bancolombia Ahorros")
 * Campo(dia, { dia = it.filter(Char::isDigit) }, rotulo = "Día de pago", guia = "5",
 *     teclado = KeyboardOptions(keyboardType = KeyboardType.Number))
 * ```
 */
@Composable
fun Campo(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String? = null,
    guia: String? = null,
    ayuda: String? = null,
    masAyuda: String? = null,
    error: String? = null,
    habilitado: Boolean = true,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    acciones: KeyboardActions = KeyboardActions.Default,
    /** Una contraseña: se pinta con puntos y lleva el ojo para mostrarla. */
    secreto: Boolean = false,
    /** Lo que va pegado después del valor, fuera del texto («%»): nunca se escribe. */
    sufijo: String? = null,
    /** Más de 1 para un texto largo (una nota, un recuerdo): la caja crece hasta ahí. */
    renglones: Int = 1,
    foco: FocusRequester? = null,
    tag: String = TAG_CAMPO,
) {
    val interaccion = remember { MutableInteractionSource() }
    val enfocado by interaccion.collectIsFocusedAsState()
    var mostrarSecreto by remember { mutableStateOf(false) }
    MarcoDeCampo(
        modifier = modifier,
        rotulo = rotulo,
        ayuda = ayuda,
        masAyuda = masAyuda,
        error = error,
        enfocado = enfocado,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty() && guia != null) {
                    Text(
                        text = guia,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoApagado,
                        maxLines = renglones,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(TAG_GUIA_DEL_CAMPO),
                    )
                }
                // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
                val campo = rememberCampoConSeleccion(valor, alCambiar)
                BasicTextField(
                    value = campo.valor,
                    onValueChange = campo::alCambiar,
                    textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                    cursorBrush = SolidColor(Movi.colores.marca),
                    keyboardOptions = if (secreto && teclado == KeyboardOptions.Default) {
                        KeyboardOptions(keyboardType = KeyboardType.Password)
                    } else {
                        teclado
                    },
                    keyboardActions = acciones,
                    singleLine = renglones <= 1,
                    maxLines = renglones.coerceAtLeast(1),
                    enabled = habilitado,
                    visualTransformation = if (secreto && !mostrarSecreto) PasswordVisualTransformation() else VisualTransformation.None,
                    interactionSource = interaccion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
                        .testTag(tag)
                        .semantics {
                            if (rotulo != null) contentDescription = rotuloEnFrase(rotulo)
                            if (error != null) error(error)
                        }
                        .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                )
            }
            if (sufijo != null && valor.isNotEmpty()) {
                Spacer(Modifier.width(Espacio.xxs))
                Text(sufijo, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            }
            if (secreto) {
                Spacer(Modifier.width(Espacio.xs))
                Icon(
                    imageVector = if (mostrarSecreto) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (mostrarSecreto) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier
                        .size(Icono.m)
                        .clickable(role = Role.Button) { mostrarSecreto = !mostrarSecreto },
                )
            }
        }
    }
}

/**
 * **El campo de un monto.** Vuelta de diseño 6.
 *
 * La misma anatomía que [Campo], con tres diferencias: el valor escrito va en la letra de las cifras
 * (`monto`, Martian Mono), el símbolo de la moneda ([moneda], «$» o «US$») se pinta aparte —no es
 * parte del texto, el cursor nunca se para antes de él— y el texto guía va en la letra de cuerpo,
 * sin la moneda adentro («Capital original», no «$ Capital original (COP)»). Sin guía, un «0» en
 * la letra de las cifras.
 *
 * La lógica —agrupar en miles mientras se escribe, dejar el cursor después del mismo dígito,
 * parsear a [Long]— es la de siempre: [nextMoneyField], [moneyFieldFromDigits] y
 * [parseMoneyDigits], en `MoneyField.kt`. `MoneyField` delega acá.
 *
 * ```
 * CampoDeMonto(capital, { capital = it }, rotulo = "Capital original")
 * CampoDeMonto(cupo, { cupo = it }, rotulo = "Cupo total", moneda = "US$", ayuda = "Opcional")
 * ```
 */
@Composable
fun CampoDeMonto(
    valor: Long?,
    alCambiar: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String? = null,
    guia: String? = null,
    ayuda: String? = null,
    masAyuda: String? = null,
    error: String? = null,
    habilitado: Boolean = true,
    moneda: String = "$",
    maxDigitos: Int = 12,
    tag: String = TAG_CAMPO_DE_MONTO,
) {
    val digitos = valor?.toString() ?: ""
    // (Vivía en `MoneyField`, que ahora delega acá.)
    // ── Ola 9 · C — CÓMO SE EDITA UN MONTO (y por qué antes se guardaba otra cifra) ──
    //
    // Este campo armaba un `TextFieldValue(display, selection = TextRange(display.length))`
    // NUEVO en cada composición: le tiraba a la basura la selección que mandaba el sistema y
    // reanclaba el cursor al final, siempre. En el navegador eso no "perdía teclas": las
    // **concatenaba**. Escribir 50000, seleccionar todo con ⌘A y escribir 7000 dejaba
    // $500.007.000 — quinientos millones donde el dueño quiso siete mil, guardables sin que
    // nada avisara.
    //
    // El arreglo NO es una limitación de la plataforma —fue un diagnóstico equivocado en la
    // primera versión de esta ola—: `CategoryField`, en la misma hoja y a diez líneas de
    // acá, ya lo hacía bien. Se copia su patrón, que son dos reglas:
    //
    //  1. El estado del texto vive ACÁ y solo se resincroniza cuando el cambio viene de
    //     AFUERA (prellenado, reset, otra pantalla) — nunca por recomponer.
    //  2. Lo que manda el sistema se respeta TAL CUAL, selección incluida. Solo se reescribe
    //     cuando de verdad hay que reformatear, y ahí se dice explícitamente dónde queda el
    //     cursor (ver [nextMoneyField], que es toda la lógica y vive afuera para poder
    //     testearla).
    //
    // **Qué NO arregla esto, dicho como es.** En Compose-wasm el `<input>` oculto que recibe
    // el teclado y lo que el canvas pinta siguen pudiendo discrepar: tras tipear 1800000,
    // Compose pinta "1.800.000" y `document.activeElement.value` dice "1800000"; tras un
    // triple-click el canvas muestra todo resaltado y el `<input>` reporta la selección
    // colapsada. Lo que se logró —y alcanza para que el campo sea usable— es que Compose
    // **resincronice el `<input>` en la siguiente tecla real**, así que la divergencia se
    // cura sola en vez de acumularse. Es una propiedad más débil que "ya no discrepan".
    //
    // **⌘A: RESUELTO, y confirmado en producción con un teclado real (2026-09-03).** Este
    // bloque decía que el atajo «no llega a Compose» y que alguien tenía que teclearlo a mano.
    // Se tecleó, y después se midió con una sonda escuchando en las dos fases: Compose-wasm
    // **sí** recibe la tecla, le hace `preventDefault` —matando el «seleccionar todo» nativo
    // del navegador— y no implementa el suyo. Por eso la selección nunca ocurría (el `<input>`
    // quedaba en `sel=8..8`) y la tecla siguiente llegaba como un tipeo al final. No era «el
    // arnés de automatización»: era la plataforma. Ahora lo implementa esta app — ver
    // [esAtajoDeSeleccionarTodo] y el `onPreviewKeyEvent` de abajo. Verificado sobre el
    // artefacto desplegado: $18.000.009 + ⌘A + «7000» = $7.000.
    //
    // **La «pérdida de teclas»: medida, y no era eso.** «Bancolombia» + triple-click +
    // «Nequi» daba «BancolombiaNequi», y se anotó acá como teclas que se pierden escribiendo
    // en ráfaga. **No se pierde ninguna tecla**: están todas. Lo que no ocurría era el
    // reemplazo, porque el triple-click no llegaba a contar como tal — Compose cierra la
    // ventana del multi-clic a los 300 ms y el doble clic del sistema son 500. Con los clics
    // más separados que eso, son tres clics sueltos: mueven el cursor, no seleccionan, y lo
    // que se escribe se inserta ahí. Arreglado en la web subiendo la ventana; el barrido que
    // lo midió está en `ViewConfigurationConDobleClicDelSistema`.
    //
    // Vale como advertencia sobre este archivo: el nombre del defecto mandó a buscar en el
    // lugar equivocado durante dos olas. Y la primera sonda que se le puso encima —un `Text`
    // que mostraba la selección al lado del campo— **rompía el conteo de clics que venía a
    // medir**, con lo cual inventó un segundo defecto que no existía.
    //
    // **El atajo ya no es solo de este campo.** Se arregló primero donde el error cuesta
    // plata, y después en los otros quince campos de texto de `commonMain` (NOMBRE, la
    // categoría, la búsqueda, el login…). Los que guardan un `String` no podían engancharse
    // acá —`BasicTextField(value: String, …)` no expone la selección— y pasan por
    // `CampoConSeleccion`, en `SeleccionarTodo.kt`. Este campo se queda con el
    // `onPreviewKeyEvent` directo porque ya trabajaba con `TextFieldValue`.
    var campo by remember { mutableStateOf(moneyFieldFromDigits(digitos)) }
    LaunchedEffect(digitos) {
        if (campo.text.filter { it.isDigit() } != digitos) campo = moneyFieldFromDigits(digitos)
    }
    val interaccion = remember { MutableInteractionSource() }
    val enfocado by interaccion.collectIsFocusedAsState()
    MarcoDeCampo(
        modifier = modifier,
        rotulo = rotulo,
        ayuda = ayuda,
        masAyuda = masAyuda,
        error = error,
        enfocado = enfocado,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = moneda,
                style = Movi.textos.monto,
                color = if (digitos.isEmpty()) Movi.colores.textoApagado else Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_MONEDA_DEL_CAMPO),
            )
            Spacer(Modifier.width(Espacio.xs))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (digitos.isEmpty()) {
                    // Una sola línea siempre: el campo también va a media fila (la cuota de un
                    // crédito) y una guía partida en dos estiraba la caja y desalineaba al de al lado.
                    Text(
                        text = guia ?: "0",
                        style = if (guia != null) Movi.textos.cuerpo else Movi.textos.monto,
                        color = Movi.colores.textoApagado,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(TAG_GUIA_DEL_CAMPO),
                    )
                }
                BasicTextField(
                    value = campo,
                    onValueChange = { entrante ->
                        val siguiente = nextMoneyField(entrante, maxDigitos)
                        campo = siguiente
                        // Se avisa lo MISMO que quedó pintado, no lo que entró.
                        alCambiar(parseMoneyDigits(siguiente.text))
                    },
                    textStyle = Movi.textos.monto.copy(color = Movi.colores.texto),
                    cursorBrush = SolidColor(Movi.colores.marca),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = habilitado,
                    interactionSource = interaccion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(tag)
                        .semantics {
                            if (rotulo != null) contentDescription = rotuloEnFrase(rotulo)
                            if (error != null) error(error)
                        }
                        // ⌘A: con el campo vacío no se consume la tecla. Ver [esAtajoDeSeleccionarTodo].
                        .onPreviewKeyEvent { evento ->
                            if (esAtajoDeSeleccionarTodo(evento) && campo.text.isNotEmpty()) {
                                campo = conTodoSeleccionado(campo)
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            // NO hay un botón de «borrar todo», y es a propósito: se probó y **no funciona
            // en el navegador**. Vaciar el campo desde el estado de la app deja el buffer
            // del `<input>` oculto con lo de antes, así que el siguiente dígito se pega a lo
            // que había: borrar sobre «7» y escribir 1800000 daba $71.800.000. Un botón que
            // a veces suma un dígito fantasma a una cifra de plata es peor que no tenerlo,
            // y con la selección arreglada ya no hace falta: seleccionar y reescribir —el
            // gesto de cualquier campo de texto— reemplaza el monto entero.
        }
    }
}

/**
 * **El título de un bloque de un formulario**, en frase: «El crédito», «Términos», «La plata que te
 * entró». Reemplaza al `SectionLabel` en mayúsculas («TÉRMINOS»). Es un [Seccion] —el mismo estilo
 * y el mismo espacio de abajo que el título de cualquier sección de la app— con una descripción
 * opcional de una línea, y los campos debajo separados por [Medida.dentroDeSeccion].
 *
 * Entre un grupo y el siguiente va [Medida.entreSecciones], y eso lo pone la hoja.
 *
 * ```
 * GrupoDeCampos("Términos") {
 *     Campo(banco, { banco = it }, rotulo = "Banco", guia = "Ej: Bancolombia")
 *     CampoDeMonto(capital, { capital = it }, rotulo = "Capital original")
 * }
 * ```
 */
@Composable
fun GrupoDeCampos(
    titulo: String,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Seccion(titulo = rotuloEnFrase(titulo), modifier = Modifier.testTag(TAG_GRUPO_DE_CAMPOS))
        if (descripcion != null) {
            Text(
                text = descripcion,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion), content = contenido)
    }
}

/**
 * **El pie de una hoja: el botón principal y, si está apagado, por qué.** Vuelta de diseño 6.
 *
 * Una sola forma para todas las hojas: el error del guardado (si lo hay) en `sale`, encima del
 * botón —fuera del scroll, donde se ve—; el [Boton] `M` a todo el ancho; y si [habilitado] es
 * `false` y hay [razon], la razón en una línea centrada **justo debajo** («Falta el banco»). Un
 * botón que se apaga en silencio se lee como roto.
 *
 * Con [secundaria] («Cancelar», «Quitar»), los dos botones van lado a lado y del mismo ancho: la
 * secundaria a la izquierda en `Secundario`, la principal a la derecha. La razón va igual, debajo.
 *
 * ```
 * PieDeHoja("Guardar crédito", ::guardar, habilitado = puedeGuardar, razon = loQueFalta,
 *     cargando = guardando, error = error)
 * PieDeHoja("Anular movimiento", ::anular, variante = VarianteDeBoton.Peligro)
 * PieDeHoja("Guardar", ::guardar, secundaria = "Cancelar", onSecundaria = onDismiss)
 * ```
 */
@Composable
fun PieDeHoja(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    razon: String? = null,
    cargando: Boolean = false,
    error: String? = null,
    variante: VarianteDeBoton = VarianteDeBoton.Principal,
    secundaria: String? = null,
    onSecundaria: () -> Unit = {},
    secundariaHabilitada: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (error != null) {
            Text(
                text = error,
                style = Movi.textos.apoyo,
                color = Movi.colores.sale,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Espacio.xs).testTag(TAG_ERROR_DEL_PIE),
            )
        }
        if (secundaria == null) {
            Boton(
                texto = texto,
                onClick = onClick,
                variante = variante,
                cargando = cargando,
                habilitado = habilitado,
                anchoCompleto = true,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Boton(
                    texto = secundaria,
                    onClick = onSecundaria,
                    variante = VarianteDeBoton.Secundario,
                    habilitado = secundariaHabilitada,
                    modifier = Modifier.weight(1f),
                    anchoCompleto = true,
                )
                Boton(
                    texto = texto,
                    onClick = onClick,
                    variante = variante,
                    cargando = cargando,
                    habilitado = habilitado,
                    modifier = Modifier.weight(1f),
                    anchoCompleto = true,
                )
            }
        }
        if (!habilitado && !cargando && razon != null) {
            Text(
                text = razon,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs).testTag(TAG_RAZON_DEL_PIE),
            )
        }
    }
}

/**
 * **El rótulo de un campo**, solo: el que [Campo] y [CampoDeMonto] pintan arriba de su caja, para lo
 * que pide un dato y no es una caja de texto (las píldoras de la moneda, un selector de cuenta). En
 * frase, en `apoyo` medio y `textoMedio`, un renglón, con [Espacio.xs] hasta lo que rotula.
 */
@Composable
fun RotuloDeCampo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = rotuloEnFrase(texto),
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = Movi.colores.textoMedio,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(bottom = Espacio.xs)
            .testTag(TAG_ROTULO_DEL_CAMPO),
    )
}

/** Un rótulo que llega en mayúsculas («TÉRMINOS») se pinta en frase («Términos»). */
internal fun rotuloEnFrase(texto: String): String {
    val letras = texto.filter { it.isLetter() }
    if (letras.length < 2 || letras.any { it.isLowerCase() }) return texto
    return texto.lowercase().replaceFirstChar { it.uppercase() }.replace("movi", "Movi")
}

/**
 * **La ayuda de un campo**: la de [Campo] y [CampoDeMonto], sola, para lo que pide un dato y no es
 * una caja de texto (un selector de cuenta, una lista de opciones). Una línea en `apoyo` —o el
 * [error] en `sale`, en su lugar— y, si hay [masAyuda], «¿Qué es esto?» a la derecha, que la abre
 * debajo. Lo largo se mueve acá, no se borra.
 */
@Composable
fun AyudaDeCampo(
    ayuda: String?,
    masAyuda: String? = null,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    var ayudaAbierta by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        val renglon = error ?: ayuda
        if (renglon != null || masAyuda != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Espacio.xxs),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalAlignment = Alignment.Top,
            ) {
                if (renglon != null) {
                    Text(
                        text = renglon,
                        style = Movi.textos.apoyo,
                        color = if (error != null) Movi.colores.sale else Movi.colores.textoMedio,
                        modifier = Modifier
                            .weight(1f, fill = masAyuda == null)
                            .testTag(if (error != null) TAG_ERROR_DEL_CAMPO else TAG_AYUDA_DEL_CAMPO),
                    )
                }
                if (masAyuda != null) {
                    Text(
                        text = if (ayudaAbierta) TEXTO_OCULTAR_AYUDA else TEXTO_QUE_ES_ESTO,
                        style = Movi.textos.apoyo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.marca,
                        maxLines = 1,
                        modifier = Modifier
                            .testTag(TAG_QUE_ES_ESTO)
                            .clickable(role = Role.Button) { ayudaAbierta = !ayudaAbierta },
                    )
                }
            }
        }
        if (masAyuda != null && ayudaAbierta) {
            Text(
                text = masAyuda,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Espacio.xxs)
                    .testTag(TAG_MAS_AYUDA_DEL_CAMPO),
            )
        }
    }
}

/** Lo que comparten [Campo] y [CampoDeMonto]: rótulo, caja, ayuda, «¿Qué es esto?» y error. */
@Composable
private fun MarcoDeCampo(
    modifier: Modifier,
    rotulo: String?,
    ayuda: String?,
    masAyuda: String?,
    error: String?,
    enfocado: Boolean,
    caja: @Composable () -> Unit,
) {
    val forma = RoundedCornerShape(Radio.s)
    val colorDelBorde: Color = when {
        error != null -> Movi.colores.sale
        enfocado -> Movi.colores.marca
        else -> Movi.colores.borde
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (rotulo != null) RotuloDeCampo(rotulo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Medida.objetivoTactil)
                .clip(forma)
                .background(Movi.colores.capa2)
                .border(Medida.trazo, colorDelBorde, forma)
                .testTag(TAG_CAJA_DEL_CAMPO)
                .padding(horizontal = Espacio.s, vertical = Espacio.s),
            contentAlignment = Alignment.CenterStart,
        ) {
            caja()
        }
        AyudaDeCampo(ayuda = ayuda, masAyuda = masAyuda, error = error)
    }
}
