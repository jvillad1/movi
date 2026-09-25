package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.diaLegible
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.CasillaDeSeleccion
import com.jvillada.movi.ui.components.NewItemButton
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
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
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
                contenido = {
                    Column(verticalArrangement = Arrangement.spacedBy(Movi.espacios.minimo)) {
                        propuestas.forEach { p ->
                            FilaDePropuesta(
                                propuesta = p,
                                marcada = p.category !in estado.desmarcadas,
                                habilitada = !estado.creandoPropuestas,
                                onAlternar = { estado.alternarPropuesta(p.category) },
                            )
                        }
                    }
                    Spacer(Modifier.height(Movi.espacios.amplio))
                    // Adentro de la tarjeta y junto al botón: con cuatro propuestas la tarjeta es
                    // alta, y un aviso debajo de ella quedaba fuera de la pantalla justo cuando el
                    // dueño acababa de tocar «Crear» — parecía que no había pasado nada.
                    if (estado.propuestasQueFallaron.isNotEmpty()) {
                        AvisoDePropuestasQueFallaron(estado)
                        Spacer(Modifier.height(Movi.espacios.amplio))
                    }
                    NewItemButton(
                        label = rotuloDeCrearPropuestas(marcadas),
                        onClick = { estado.crearPropuestas() },
                        modifier = Modifier.testTag(TAG_CREAR_PROPUESTAS),
                        full = true,
                        enabled = marcadas > 0 && !estado.creandoPropuestas,
                    )
                    Spacer(Modifier.height(Movi.espacios.corto))
                    NewItemButton(label = "Crear uno a mano", onClick = { estado.abrirNuevo() })
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
 * Una propuesta: el ícono de la categoría, su nombre, cuánto se gastó y el tope que se propone, y la
 * casilla. Se toca la fila entera —con semántica de casilla— y no solo el cuadrito de 20 dp.
 */
@Composable
private fun FilaDePropuesta(
    propuesta: PropuestaDePresupuesto,
    marcada: Boolean,
    habilitada: Boolean,
    onAlternar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .testTag(tagDePropuestaDePresupuesto(propuesta.category))
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.normal))
            .toggleable(value = marcada, enabled = habilitada, role = Role.Checkbox, onValueChange = { onAlternar() })
            .padding(vertical = Movi.espacios.corto),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        IconoDeCategoria(propuesta.category)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                propuesta.category,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Gastaste ${formatCOP(propuesta.gastado.roundToLong())} · tope ${formatCOP(propuesta.amount.roundToLong())}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
        CasillaDeSeleccion(marcada = marcada)
    }
}

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
