package com.jvillada.movi.ui.transactions

import com.jvillada.movi.ui.components.ds.PildoraDeAccion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Espacio as EspacioCompartido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.categorias.colorDeCategoria
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton

/**
 * # El detalle de un movimiento, para leerlo primero (Ola 2b)
 *
 * Hasta acá la hoja de un movimiento era un formulario: el monto en 15,5 sp bajo «MONTO, CUENTA Y
 * CONCEPTO», la pregunta de «¿se repite?» con su párrafo, y la cuadrícula de categorías entera en
 * la misma columna. Ahora abre como en Revolut o Monzo: **quién, cuánto, cuándo y en qué
 * categoría**, y debajo una fila de acciones. Cada sección de edición sigue existiendo —las mismas,
 * con el mismo guardado— y se abre desde su acción.
 *
 * Acá viven las piezas nuevas (el encabezado, el chip y la fila de acciones). El armado está en
 * `ContenidoDelMovimiento` (`CategorySheets.kt`), que es quien sabe qué secciones aplican.
 */

/** Lo que se puede hacer sobre un movimiento desde su detalle. El orden es el de la fila. */
enum class AccionDelMovimiento(val rotulo: String, internal val icono: ImageVector) {
    Categoria("Categoría", Icons.Rounded.Category),
    /** La nota (el concepto), y con ella el monto y la cuenta: la sección «Monto, cuenta y concepto». */
    Nota("Nota", Icons.Rounded.EditNote),
    Fecha("Fecha", Icons.Rounded.Today),
    /** «¿Se repite todos los meses?». */
    Repetir("Repetir", Icons.Rounded.Repeat),
    /**
     * Repartirlo en un espacio: desde ahí cuenta solo tu parte. Con el ícono de Espacios. Solo en un
     * gasto que se puede repartir, sin repartir, y con algún espacio (vuelta de diseño 17: antes era
     * una tarjeta grande debajo de la categoría de cada gasto).
     */
    Repartir("Repartir", Icons.Rounded.Groups),
    /** De quién es la cuenta que nombra el aviso. Solo si el movimiento nombra una. */
    Tercero("Tercero", Icons.Rounded.Person),
    /** Vincular a una deuda y anular. */
    Mas("Más", Icons.Rounded.MoreHoriz),
}

/**
 * Cuántas acciones caben en la fila: a 360 dp, siete celdas de ~47 dp ya cortan «Categoría». Siete
 * solo pasa con «Tercero» y «Repartir» a la vez, y siempre con «Más»: ahí «Repartir» se muda a «Más».
 */
const val MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO: Int = 6

/**
 * Las acciones de la fila, en su orden ([AccionDelMovimiento]). Solo aparece la que tiene algo que
 * abrir. Si no caben ([MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO]), «Repartir» queda adentro de «Más»: ver
 * [repartirVaEnMas].
 */
internal fun accionesDelMovimiento(
    ofreceRepetir: Boolean,
    ofreceRepartir: Boolean,
    conTercero: Boolean,
    conMas: Boolean,
): List<AccionDelMovimiento> {
    val todas = buildList {
        add(AccionDelMovimiento.Categoria)
        add(AccionDelMovimiento.Nota)
        add(AccionDelMovimiento.Fecha)
        if (ofreceRepetir) add(AccionDelMovimiento.Repetir)
        if (ofreceRepartir) add(AccionDelMovimiento.Repartir)
        if (conTercero) add(AccionDelMovimiento.Tercero)
        if (conMas) add(AccionDelMovimiento.Mas)
    }
    return if (todas.size > MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO) todas - AccionDelMovimiento.Repartir else todas
}

/** ¿«Repartir» se ofrece pero no cupo en la fila? Entonces va al principio de «Más». */
internal fun repartirVaEnMas(ofreceRepartir: Boolean, acciones: List<AccionDelMovimiento>): Boolean =
    ofreceRepartir && AccionDelMovimiento.Repartir !in acciones

/** El `testTag` del monto grande del encabezado. */
const val TAG_MONTO_DEL_MOVIMIENTO: String = "monto-del-movimiento"

/** El `testTag` de «de $88.600 · con Caro» bajo tu parte, en el detalle de un gasto repartido. */
const val TAG_TOTAL_DEL_REPARTIDO: String = "total-del-repartido"

/** El `testTag` del chip de la categoría. */
const val TAG_CHIP_DE_CATEGORIA: String = "chip-de-categoria"

/** El `testTag` de la línea «En este comercio». */
const val TAG_EN_ESTE_COMERCIO: String = "en-este-comercio"

/** El `testTag` de cada acción de la fila. */
fun tagDeAccionDelMovimiento(accion: AccionDelMovimiento): String = "accion-del-movimiento:${accion.name}"

/**
 * El monto con su signo, como en la fila de Movimientos: «−$27.500» un gasto, «+$1.200.000» un
 * ingreso, sin signo lo que no movió plata del bolsillo. Una sola regla para la fila y el detalle.
 */
internal fun textoDelMontoConSigno(tx: FinancialEvent, tono: TonoDelMonto): String = when (tono) {
    TonoDelMonto.INGRESO -> "+${formatMoney(tx.amount, tx.currency)}"
    TonoDelMonto.GASTO -> "−${formatMoney(tx.amount, tx.currency)}"
    TonoDelMonto.NEUTRO, TonoDelMonto.ENTRE_CUENTAS -> formatMoney(tx.amount, tx.currency)
}

/**
 * El encabezado: el avatar (la marca del comercio a 48 dp, o el ícono de la categoría), el título,
 * el monto grande y «Hoy, 12:40 · Nu». Tocar el título o el monto abre la nota: es lo que se toca
 * para corregirlos.
 */
@Composable
internal fun EncabezadoDelMovimiento(
    evento: FinancialEvent,
    cuentas: List<Account>,
    onEditar: () -> Unit,
    /** Los espacios del dueño, para decir con quién se repartió un gasto («con Caro»). */
    espacios: List<EspacioCompartido> = emptyList(),
) {
    val hoy = remember { hoyEnAppZone() }
    val cuenta = cuentas.firstOrNull { it.id == evento.accountId }?.name
    // Vuelta de diseño 30: en un gasto repartido la cifra es tu parte, debajo «de $88.600 · con
    // Caro», y la línea de cuándo dice lo que salió de la cuenta («Salieron $88.600 de Nu»): el saldo
    // sigue siendo el de toda la plata que salió.
    val repartido = lineaDelRepartido(evento, espacios.firstOrNull { it.id == evento.espacioId })
    val dondeOLoQueSalio = salieronDeLaCuenta(evento, cuenta) ?: cuenta
    val linea = lineaDeCuandoYDonde(evento, dondeOLoQueSalio, hoy)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radio.m)).clickable(onClick = onEditar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Box(modifier = Modifier.size(TamanoDeAvatar.Encabezado.circulo), contentAlignment = Alignment.Center) {
            AvatarDelComercio(evento, tamano = TamanoDeAvatar.Encabezado)
        }
        Text(
            descripcionParaMostrar(evento),
            style = Movi.textos.tituloMedio,
            color = Movi.colores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    val tono = tonoDelEvento(evento)
    // La cifra protagonista: la héroe del sistema, en un renglón; si no entra —millones en dólares
    // con centavos en un teléfono de 360 dp— se achica hasta la de una tarjeta.
    CifraDelMovimiento(
        monto = montoPrincipalDelMovimiento(evento),
        moneda = evento.currency,
        tono = tono,
        variante = VarianteDeCifra.Heroe,
        modifier = Modifier.testTag(TAG_MONTO_DEL_MOVIMIENTO).clickable(onClick = onEditar),
    )
    if (repartido != null) {
        Text(
            repartido,
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.testTag(TAG_TOTAL_DEL_REPARTIDO),
        )
    }
    Spacer(Modifier.height(Espacio.xxs))
    Text(linea, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
}

/**
 * La categoría como chip: su ícono, su nombre y una flecha. Tocarlo abre (o cierra) el selector.
 * [habilitado] en `false` mientras se guarda otra categoría.
 */
@Composable
internal fun ChipDeCategoria(
    nombre: String,
    abierto: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
) {
    // Vuelta de diseño 20: la `PildoraDeAccion` del sistema, con el ícono de la categoría (en su
    // color) adelante y la flecha de lo que se despliega al final. Era una fila armada a mano sobre
    // el lavado del color de la categoría.
    PildoraDeAccion(
        texto = nombre,
        onClick = onClick,
        modifier = Modifier.testTag(TAG_CHIP_DE_CATEGORIA),
        descripcion = "Cambiar la categoría: $nombre",
        inicio = { IconoDeCategoria(nombre, tamano = TamanoDeIconoDeCategoria.Chico) },
        iconoFinal = if (abierto) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        habilitada = habilitado,
    )
}

/** «4 veces este período · $142.800», tocable: lleva a Movimientos con la búsqueda puesta. */
@Composable
internal fun LineaEnEsteComercio(resumen: EnEsteComercio, onVer: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_EN_ESTE_COMERCIO)
            .heightIn(min = Medida.objetivoTactil)
            .clip(RoundedCornerShape(Radio.m))
            .then(if (onVer != null) Modifier.clickable(onClick = onVer) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            textoDeEnEsteComercio(resumen, formatMoney(resumen.total, resumen.moneda)),
            style = Movi.textos.apoyo.copy(fontFeatureSettings = "tnum"),
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        if (onVer != null) {
            // Vuelta de diseño 18: el botón de texto del sistema (era «Ver ›» en texto suelto).
            Boton("Ver", onClick = onVer, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
        }
    }
}

/** El `testTag` de la pregunta «¿De quién es la cuenta ·0756?» que se ve al abrir. */
const val TAG_PREGUNTA_DEL_TERCERO: String = "pregunta-del-tercero"

/**
 * **«¿De quién es la cuenta ·0756?», a la vista al abrir.** Es una pregunta pendiente, como «Por
 * confirmar»: no se esconde detrás de la acción «Tercero». Una sola línea compacta; tocarla abre la
 * sección «Tercero», donde está la fila de siempre para guardarla.
 */
@Composable
internal fun PreguntaDelTercero(pregunta: String, onAbrir: () -> Unit) {
    // Una pregunta pendiente es algo que Movi propone resolver: la superficie de la propuesta, con
    // una [Fila] del sistema adentro.
    Superficie(
        modifier = Modifier.fillMaxWidth(),
        nivel = NivelDeSuperficie.Propuesta,
        relleno = RellenoDeSuperficie.Lista,
    ) {
        Fila(
            titulo = pregunta,
            // El tag en la fila que se toca.
            modifier = Modifier.testTag(TAG_PREGUNTA_DEL_TERCERO),
            inicio = { IconoEnCirculo(Icons.Rounded.Person, tono = TonoDeIcono.Aviso) },
            final = { Boton("Responder", onClick = onAbrir, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S) },
            onClick = onAbrir,
        )
    }
}

/**
 * La fila de acciones: un ícono en un círculo y un rótulo corto. La abierta se marca (y lo declara
 * con `selected`); tocarla otra vez la cierra.
 */
@Composable
internal fun FilaDeAcciones(
    acciones: List<AccionDelMovimiento>,
    abierta: AccionDelMovimiento?,
    onElegir: (AccionDelMovimiento) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        acciones.forEach { accion ->
            val elegida = accion == abierta
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag(tagDeAccionDelMovimiento(accion))
                    .clip(RoundedCornerShape(Radio.m))
                    .clickable(role = Role.Tab) { onElegir(accion) }
                    .semantics { selected = elegida }
                    .padding(vertical = Espacio.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
            ) {
                // El círculo de una acción: la capa 2 sin elegir, el lavado de la marca elegida. Sin
                // bordes (superficies por capas).
                Box(
                    modifier = Modifier
                        .size(Medida.avatar)
                        .background(if (elegida) Movi.colores.lavado(Movi.colores.marca) else Movi.colores.capa2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = accion.icono,
                        contentDescription = null,
                        tint = if (elegida) Movi.colores.marca else Movi.colores.texto,
                        modifier = Modifier.size(Icono.m),
                    )
                }
                Text(
                    accion.rotulo,
                    style = if (elegida) Movi.textos.apoyoMedio else Movi.textos.apoyo,
                    color = if (elegida) Movi.colores.marca else Movi.colores.textoMedio,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
