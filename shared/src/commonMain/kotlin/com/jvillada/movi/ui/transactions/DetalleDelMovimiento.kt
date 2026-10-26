package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.Account
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
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.categorias.colorDeCategoria
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.hoyEnAppZone

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
    /** De quién es la cuenta que nombra el aviso. Solo si el movimiento nombra una. */
    Tercero("Tercero", Icons.Rounded.Person),
    /** Vincular a una deuda y anular. */
    Mas("Más", Icons.Rounded.MoreHoriz),
}

/** El `testTag` del monto grande del encabezado. */
const val TAG_MONTO_DEL_MOVIMIENTO: String = "monto-del-movimiento"

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
) {
    val hoy = remember { hoyEnAppZone() }
    val cuenta = cuentas.firstOrNull { it.id == evento.accountId }?.name
    val linea = remember(evento.timestamp, cuenta, hoy) { lineaDeCuandoYDonde(evento, cuenta, hoy) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radio.m)).clickable(onClick = onEditar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Box(modifier = Modifier.size(TamanoDeAvatar.Encabezado.circulo), contentAlignment = Alignment.Center) {
            AvatarDelComercio(evento, tamano = TamanoDeAvatar.Encabezado)
        }
        Text(
            evento.description,
            style = Movi.textos.titulo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    val tono = tonoDelEvento(evento)
    val monto = textoDelMontoConSigno(evento, tono)
    // La cifra protagonista, con la letra de la héroe del sistema (`VarianteDeCifra.Heroe`): un
    // renglón, y si no entra —millones en dólares con centavos en un teléfono de 360 dp— se achica
    // hasta la de una tarjeta, como la [com.jvillada.movi.ui.components.ds.Cifra] héroe. No es esa
    // misma porque lleva el azul de «entre cuentas» de un traspaso, que el sistema no tiene.
    val estilo = estiloDeLaCifra(VarianteDeCifra.Heroe, Movi.textos)
    BasicText(
        monto,
        style = estilo.copy(color = colorDelTono(tono, Movi.colores)),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = Movi.textos.cifraDeTarjeta.fontSize, maxFontSize = estilo.fontSize),
        modifier = Modifier.testTag(TAG_MONTO_DEL_MOVIMIENTO).clickable(onClick = onEditar),
    )
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
    val color = colorDeCategoria(nombre)
    Row(
        modifier = Modifier
            .testTag(TAG_CHIP_DE_CATEGORIA)
            .heightIn(min = Medida.altoChico)
            .clip(RoundedCornerShape(Radio.pill))
            .background(Movi.colores.lavado(color))
            .clickable(enabled = habilitado, role = Role.Button, onClickLabel = "Cambiar la categoría", onClick = onClick)
            .padding(start = Espacio.xxs, end = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        IconoDeCategoria(nombre, tamano = TamanoDeIconoDeCategoria.Chico)
        Text(nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            imageVector = if (abierto) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Movi.colores.textoMedio,
            modifier = Modifier.size(Icono.s),
        )
    }
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
            Text("Ver", style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.marca)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(Icono.s))
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
        modifier = Modifier.fillMaxWidth().testTag(TAG_PREGUNTA_DEL_TERCERO),
        nivel = NivelDeSuperficie.Propuesta,
        relleno = RellenoDeSuperficie.Lista,
    ) {
        Fila(
            titulo = pregunta,
            inicio = { IconoEnCirculo(Icons.Rounded.Person, tono = TonoDeIcono.Aviso) },
            final = { Text("Responder", style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.marca) },
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
                    style = Movi.textos.apoyo,
                    fontWeight = if (elegida) FontWeight.Medium else FontWeight.Normal,
                    color = if (elegida) Movi.colores.marca else Movi.colores.textoMedio,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
