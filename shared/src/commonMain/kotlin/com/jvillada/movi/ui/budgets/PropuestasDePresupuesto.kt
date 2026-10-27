package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import com.jvillada.movi.ui.components.BotonReintentar
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.formatCOP
import kotlin.math.roundToLong

/** El «Crear estos N» de las propuestas. */
const val TAG_CREAR_PROPUESTAS: String = "crear-propuestas-de-presupuesto"

/** La fila (y su casilla) de la propuesta de [categoria]. */
fun tagDePropuestaDePresupuesto(categoria: String): String = "propuesta-de-presupuesto-$categoria"

private const val TITULO_DEL_VACIO = "Ponle un tope a lo que más gastas"

/**
 * **El vacío que enseña de Presupuestos**: sin ninguno creado, explica qué hace un presupuesto y
 * ofrece la MISMA hoja «Nuevo presupuesto» que ya abre el «Nuevo» del encabezado — ver
 * [EstadoDePresupuestos.abrirNuevo]. No hay lógica de alta nueva acá, solo el botón que la dispara.
 *
 * **Con propuestas** (las categorías de más gasto del período pasado, ver [PropuestaDePresupuesto])
 * la misma tarjeta las trae marcadas, y la acción principal pasa a crearlas; crear a mano queda
 * como secundaria. Sin propuestas —no hubo gasto, o la lectura no contestó— es el vacío de siempre.
 *
 * ### Mientras las propuestas vienen en camino
 *
 * Se ve el vacío simple, y al llegar la tarjeta crece **una sola vez, hacia abajo**: el título
 * queda donde estaba y abajo de la tarjeta no hay nada que empujar (la tarjeta de «Gastado en …» no
 * existe sin presupuestos). Se descartó reservar el alto: no se sabe cuántas propuestas vienen —de
 * cero a cuatro— y un hueco reservado que después queda vacío es otro salto, más feo. Y se descartó
 * esperarlas con el esqueleto: una sugerencia lenta no puede demorar crear a mano.
 */
@Composable
internal fun VacioDePresupuestos(estado: EstadoDePresupuestos) {
    // Las ya creadas no se vuelven a ofrecer: si la recarga de después falló, el vacío sigue acá
    // con las que faltan, y ninguna se manda dos veces.
    val propuestas = estado.propuestasPendientes
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        if (propuestas.isEmpty()) {
            VacioQueEnsena(
                titulo = TITULO_DEL_VACIO,
                detalle = "Elige una categoría y cuánto quieres gastar como máximo en cada período. Movi te avisa " +
                    "cuando te acerques.",
                accion = "Nuevo presupuesto",
                onAccion = { estado.abrirNuevo() },
            )
        } else {
            val marcadas = estado.propuestasMarcadas.size
            VacioQueEnsena(
                titulo = TITULO_DEL_VACIO,
                detalle = textoDeLasPropuestas(propuestas),
                // Las propuestas son `Fila`s: van de borde a borde de la tarjeta, con su propio margen.
                contenidoDeBordeABorde = true,
                contenido = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        propuestas.forEachIndexed { i, p ->
                            FilaDePropuesta(
                                propuesta = p,
                                marcada = p.category !in estado.desmarcadas,
                                habilitada = !estado.creandoPropuestas,
                                onAlternar = { estado.alternarPropuesta(p.category) },
                            )
                            if (i < propuestas.lastIndex) Separador(SangriaDeSeparador.ConInicio)
                        }
                    }
                    // Los botones quedan como estaban: con el relleno de la tarjeta, como el título.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(Movi.espacios.amplio))
                        // Adentro de la tarjeta y junto al botón: con cuatro propuestas la tarjeta es
                        // alta, y un aviso debajo de ella quedaba fuera de la pantalla justo cuando el
                        // dueño acababa de tocar «Crear» — parecía que no había pasado nada.
                        if (estado.propuestasQueFallaron.isNotEmpty()) {
                            AvisoDePropuestasQueFallaron(estado)
                            Spacer(Modifier.height(Movi.espacios.amplio))
                        }
                        Boton(
                            texto = rotuloDeCrearPropuestas(marcadas),
                            onClick = { estado.crearPropuestas() },
                            modifier = Modifier.testTag(TAG_CREAR_PROPUESTAS),
                            icono = Icons.Rounded.Add,
                            anchoCompleto = true,
                            habilitado = marcadas > 0 && !estado.creandoPropuestas,
                        )
                        Spacer(Modifier.height(Espacio.xs))
                        Boton(
                            texto = "Crear uno a mano",
                            onClick = { estado.abrirNuevo() },
                            variante = VarianteDeBoton.Fantasma,
                            icono = Icons.Rounded.Add,
                        )
                    }
                },
            )
        }
    }
}

/**
 * «Movi te propone 3 a partir de lo que gastaste del 25 de julio al 24 de agosto.» Las fechas con
 * [diaLegible], la misma forma que el rango del período en Movimientos.
 */
internal fun textoDeLasPropuestas(propuestas: List<PropuestaDePresupuesto>): String {
    val primera = propuestas.first()
    val desde = diaLegible(primera.desde) ?: primera.desde
    val hasta = diaLegible(primera.hasta) ?: primera.hasta
    return "Movi te propone ${propuestas.size} a partir de lo que gastaste del $desde al $hasta."
}

/**
 * **Una propuesta, como `Fila` del sistema** (vuelta de diseño 14): el ícono de la categoría, su
 * nombre, «Gastaste $X» y, a la derecha, el tope que se propone con [Cifra] y la
 * [Casilla]. Era una fila a mano con el tope metido en el subtítulo («Gastaste $1.850.000 · tope
 * $1.850.000», partido en dos renglones) y un cuadradito lavanda de 20 dp.
 *
 * **Se marca la fila entera**: el toque va de borde a borde y mide 64 de alto (más de los 48 del
 * sistema), con semántica de casilla; la [Casilla] de la derecha solo dice el estado.
 *
 * El subtítulo es «Gastaste $1.850.000» (vuelta de diseño 16): el período ya lo dice el párrafo de
 * arriba. Así entra en **un renglón** a 360 dp al lado de un tope de siete cifras (lo mide
 * `PresupuestosConJerarquiaTest`); con «el período pasado» al final necesitaba tres. Igual puede bajar
 * hasta [RENGLONES_DEL_SUBTITULO_DE_LA_PROPUESTA] con la letra agrandada: es la razón de la propuesta y
 * no se corta.
 */
@Composable
private fun FilaDePropuesta(
    propuesta: PropuestaDePresupuesto,
    marcada: Boolean,
    habilitada: Boolean,
    onAlternar: () -> Unit,
) {
    // El toque va en una caja alrededor de la fila (y no en el `modifier` de la `Fila`) para que el
    // nodo de la casilla y el de la fila no se pisen el tag.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagDePropuestaDePresupuesto(propuesta.category))
            .toggleable(value = marcada, enabled = habilitada, role = Role.Checkbox, onValueChange = { onAlternar() }),
    ) {
        Fila(
            titulo = propuesta.category,
            subtitulo = subtituloDeLaPropuesta(propuesta),
            lineasDelSubtitulo = RENGLONES_DEL_SUBTITULO_DE_LA_PROPUESTA,
            inicio = { IconoDeCategoria(propuesta.category) },
            final = {
                // El tope y su casilla van juntos, como un ícono y su rótulo (`Espacio.xxs`): son un
                // solo control, «este tope, sí o no». Con `Espacio.s` entre los dos, a 360 dp
                // «Gastaste $1.850.000» quedaba 6 dp corto y bajaba a un segundo renglón (medido en
                // `PresupuestosConJerarquiaTest`, vuelta de diseño 16).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                    Cifra(propuesta.amount.roundToLong(), modifier = Modifier.testTag(TAG_TOPE_DE_LA_PROPUESTA))
                    Casilla(marcada = marcada)
                }
            },
        )
    }
}

/** Hasta cuántos renglones baja el subtítulo de una propuesta antes de cortarse. Ver [FilaDePropuesta]. */
private const val RENGLONES_DEL_SUBTITULO_DE_LA_PROPUESTA: Int = 3

/** El tope que se propone, a la derecha de la fila de una propuesta. */
const val TAG_TOPE_DE_LA_PROPUESTA: String = "tope-de-la-propuesta"

/**
 * «Gastaste $1.020.000»: lo que se gastó, que es de donde sale el tope. Sin «el período pasado»
 * (vuelta de diseño 16): el párrafo de arriba de la tarjeta ya dice de qué fechas, y con la cola la
 * frase bajaba a tres renglones a 360 dp al lado de un tope de siete cifras.
 */
internal fun subtituloDeLaPropuesta(propuesta: PropuestaDePresupuesto): String =
    "Gastaste ${formatCOP(propuesta.gastado.roundToLong())}"

/**
 * «Crear este presupuesto» con una sola marcada; «Crear estos N» con varias (y con cero, apagado).
 * «Crear estos 1» no se dice.
 */
internal fun rotuloDeCrearPropuestas(marcadas: Int): String =
    if (marcadas == 1) "Crear este presupuesto" else "Crear estos $marcadas"

/** «No pudimos crear el presupuesto de Comida», o «los presupuestos de Comida, Hija y Mercado». */
internal fun textoDePropuestasQueFallaron(fallaron: List<PropuestaDePresupuesto>): String {
    val nombres = fallaron.map { it.category }
    return if (nombres.size == 1) {
        "No pudimos crear el presupuesto de ${nombres.single()}"
    } else {
        "No pudimos crear los presupuestos de ${nombres.dropLast(1).joinToString(", ")} y ${nombres.last()}"
    }
}

/**
 * Las propuestas que el server no aceptó, con «Reintentar» que manda solo esas, **adentro** de la
 * tarjeta del vacío. Sin tarjeta propia: una tarjeta dentro de otra no se lee como aviso. Cuando
 * alguna sí se creó y la pantalla ya pasó a la lista, el aviso es un [NoSePudoLeer] arriba de ella
 * (ver [presupuestos]).
 */
@Composable
private fun AvisoDePropuestasQueFallaron(estado: EstadoDePresupuestos) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            textoDePropuestasQueFallaron(estado.propuestasQueFallaron),
            style = Movi.textos.cuerpo,
            color = Movi.colores.sale,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Movi.espacios.corto))
        BotonReintentar(onReintentar = { estado.reintentarPropuestas() })
    }
}
