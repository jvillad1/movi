package com.jvillada.movi.ui.transactions

import com.jvillada.movi.ui.components.ds.IconoDeMovimiento
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import com.jvillada.movi.ui.components.ds.coloresDeLoElegido
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
import androidx.compose.material.icons.rounded.AccountBalance
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Espacio as EspacioCompartido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.CuotaPorSaldar
import com.jvillada.movi.shared.model.cuotaQueSeParece
import com.jvillada.movi.shared.model.cuotasPorSaldar
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
    /**
     * El monto, la cuenta y el concepto: la sección «Monto, cuenta y concepto». Vuelta 67: se llama
     * «Editar» (era «Nota»): abre mucho más que la nota. El nombre del valor no cambió.
     */
    Nota("Editar", Icons.Rounded.EditNote),
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
    /**
     * Vuelta 81 · en la ficha (fuera del teléfono) el encabezado es solo el título y la cifra: cuándo,
     * dónde y las cuotas los dicen las filas de [datosDeLaFicha].
     */
    ficha: Boolean = false,
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
            IconoDeMovimiento(evento, tamano = TamanoDeAvatar.Encabezado)
        }
        // Vuelta de diseño 36: el mismo título que su fila («Éxito Suba», no «Mercado»).
        Text(
            tituloDelMovimiento(evento, cuenta),
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
    } else if (!ficha) {
        // Vuelta 71 (revisión): una compra diferida dice a cuántas cuotas, como su fila en Actividad.
        textoDeLasCuotas(evento.cuotas)?.let { cuotas ->
            Text(cuotas, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio, modifier = Modifier.testTag(TAG_CUOTAS_DEL_MOVIMIENTO))
        }
    }
    if (!ficha) {
        Spacer(Modifier.height(Espacio.xxs))
        Text(linea, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
    }
    // Vuelta 80: lo que decía el encabezado del día en Actividad, ahora en el pago mismo.
    if (esPagoDeTarjetaQueSale(evento)) {
        Text(
            NOTA_DEL_PAGO_DE_TARJETA_EN_EL_DETALLE,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.testTag(TAG_NOTA_DEL_PAGO_DE_TARJETA),
        )
    }
}

/** Lo que dice el detalle de un pago de tarjeta (vuelta 80; antes iba en el encabezado del día de Actividad). */
const val NOTA_DEL_PAGO_DE_TARJETA_EN_EL_DETALLE: String = "No suma en tus gastos: la compra ya contó cuando la hiciste."

/** El `testTag` de [NOTA_DEL_PAGO_DE_TARJETA_EN_EL_DETALLE]. */
const val TAG_NOTA_DEL_PAGO_DE_TARJETA: String = "movimiento:nota-del-pago-de-tarjeta"

/** ¿Es la pata que sale de un pago de tarjeta (la que el encabezado del día explicaba)? Pura. */
internal fun esPagoDeTarjetaQueSale(evento: FinancialEvent): Boolean =
    evento.category == com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY && evento.type == com.jvillada.movi.shared.model.TransactionType.EXPENSE

/** Vuelta 71 (revisión): «a 3 cuotas» debajo de la cifra del detalle de una compra diferida. */
const val TAG_CUOTAS_DEL_MOVIMIENTO: String = "movimiento:cuotas"

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

/** El `testTag` de la sugerencia «¿Es la cuota de X?» del detalle (vuelta 53). */
const val TAG_SUGERENCIA_DE_LA_CUOTA: String = "detalle:sugerencia-de-la-cuota"

/** El `testTag` del lugar que la sugerencia guarda mientras carga (vuelta 56). */
const val TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA: String = "detalle:lugar-de-la-sugerencia-de-la-cuota"

/**
 * Vuelta 56 · **¿El detalle guarda el lugar de «¿Es la cuota de X?» mientras pide los vencimientos?**
 * La sugerencia espera `/upcoming` y `/occurrences`, y al llegar empujaba las acciones hacia abajo. Con
 * lo que la app ya leyó —el Inicio deja en [datos] los mismos vencimientos y ocurrencias, y el período
 * del dueño— se hace **la misma cuenta pura** ([cuotaQueSeParece] sobre [cuotasPorSaldar]): si el gasto
 * se parece a una cuota (monto, categoría, moneda y período), devuelve esa cuota y la hoja reserva su
 * alto con su misma pregunta; si no, `null` y no se reserva nada (un gasto cualquiera no deja un hueco
 * que después se cierra). Sin nada leído, tampoco. Es solo el lugar: lo que se muestra y se toca sale
 * siempre de la lectura fresca.
 */
fun cuotaPrevistaParaElDetalle(
    gasto: FinancialEvent,
    cuentas: List<Account>,
    datos: com.jvillada.movi.ui.dashboard.DashboardData?,
): CuotaPorSaldar? {
    val vencimientos = datos?.upcoming ?: return null
    return cuotaQueSeParece(gasto, cuotasPorSaldar(vencimientos, cuentas, datos.ocurrencias.orEmpty()), datos.ajustesDePeriodo)
}

/**
 * **«¿Es la cuota de Vehículo 4083? · Sí, es la cuota»** (vuelta 53): la fila destacada suave, arriba de
 * las acciones, cuando el gasto se parece a la cuota de un crédito sin saldar (`cuotaQueSeParece`, en
 * `:core`). La misma superficie de propuesta que [PreguntaDelTercero]; el botón hace exactamente lo de
 * «Es la cuota de un crédito» con ese crédito. Nunca se vincula sola.
 */
@Composable
internal fun SugerenciaDeLaCuota(
    pregunta: String,
    detalle: String,
    ocupado: Boolean,
    onSi: () -> Unit,
    /**
     * Vuelta 56 · **solo el lugar**: mientras se piden los vencimientos, la misma tarjeta, invisible, sin
     * semántica y sin toque, guarda su alto para que las acciones no salten cuando llega. Ver
     * [cuotaPrevistaParaElDetalle].
     */
    reserva: Boolean = false,
) {
    // El botón va debajo y a la derecha, no al final del renglón: a 360 dp, con «Sí, es la cuota» al
    // lado, «¿Es la cuota de Libre inversión 9695?» no cabía en dos renglones.
    Superficie(
        modifier = if (reserva) {
            Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics { testTag = TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA }
        } else {
            Modifier.fillMaxWidth().testTag(TAG_SUGERENCIA_DE_LA_CUOTA)
        },
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            IconoEnCirculo(Icons.Rounded.AccountBalance, tono = TonoDeIcono.Aviso)
            Column(modifier = Modifier.weight(1f)) {
                Text(pregunta, style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto)
                Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        Spacer(Modifier.height(Espacio.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Boton(
                com.jvillada.movi.shared.model.SI_ES_LA_CUOTA,
                onClick = { if (!reserva) onSi() },
                variante = VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S,
                cargando = ocupado,
                habilitado = !reserva,
            )
        }
    }
}

/** El `testTag` del rótulo de una acción de [FilaDeAcciones]. */
const val TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO: String = "detalle:rotulo-de-accion"

/** Hasta dónde se achica el rótulo de una acción antes de recortarse: el 85 % de `apoyo`. */
private const val PISO_DEL_ROTULO_DE_ACCION = 0.85f

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
                // El círculo de una acción: la capa 3 sin elegir, la tinta elegida (vuelta 29). Sin
                // bordes (superficies por capas).
                Box(
                    modifier = Modifier
                        .size(Medida.avatar)
                        .background(if (elegida) coloresDeLoElegido(Movi.colores).first else Movi.colores.capa3, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = accion.icono,
                        contentDescription = null,
                        tint = if (elegida) coloresDeLoElegido(Movi.colores).second else Movi.colores.texto,
                        modifier = Modifier.size(Icono.m),
                    )
                }
                // Vuelta 29: con Figtree, «Categoría» se recortaba en el borde de su columna a 360 dp
                // (un sexto del ancho). Un renglón siempre, y si no entra se achica un poco antes de
                // recortarse; lo vigila `LosRotulosDeLasAccionesDelMovimientoEntranTest`.
                val estiloDelRotulo = (if (elegida) Movi.textos.apoyoMedio else Movi.textos.apoyo).copy(
                    color = if (elegida) Movi.colores.texto else Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                )
                BasicText(
                    accion.rotulo,
                    modifier = Modifier.fillMaxWidth().testTag(TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO),
                    style = estiloDelRotulo,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = estiloDelRotulo.fontSize * PISO_DEL_ROTULO_DE_ACCION,
                        maxFontSize = estiloDelRotulo.fontSize,
                    ),
                )
            }
        }
    }
}

// ── Vuelta 81: la ficha de un movimiento, fuera del teléfono ─────────────────────────────────────

/**
 * # La ficha (vuelta 81)
 *
 * A 1.280 el detalle de un movimiento flotaba sobre el crema: la cifra arriba, el chip de la categoría
 * y seis acciones repartidas cada 100 px, con el 70 % de la pantalla vacío. Fuera del teléfono —en el
 * panel de Actividad o como diálogo— el detalle es una **ficha**: una superficie de
 * [com.jvillada.movi.theme.Medida.anchoDeFicha] (520) como mucho, con
 *
 * 1. la cifra y el título arriba ([EncabezadoDelMovimiento] con `ficha = true`, sin la línea de cuándo
 *    y dónde: eso ya lo dicen las filas);
 * 2. los datos como [Fila]s ([datosDeLaFicha]): cuenta, fecha, categoría, nota, origen, cuotas y grupo;
 *    tocar uno abre la sección que lo corrige;
 * 3. las acciones **alineadas a la izquierda, en un renglón** ([AccionesDeLaFicha]): una sola principal
 *    ([principalDeLaFicha], la regla del amarillo) y el resto secundarias; «Más» va en «⋯».
 *
 * Las secciones de edición son las mismas de siempre y se abren debajo. **El teléfono no cambia.**
 */

/** El `testTag` de la superficie de la ficha, en el panel de Actividad. */
const val TAG_FICHA_DEL_MOVIMIENTO: String = "movimiento:ficha"

/** El `testTag` del renglón de acciones de la ficha. */
const val TAG_ACCIONES_DE_LA_FICHA: String = "movimiento:acciones-de-la-ficha"

/** El `testTag` de cada dato de la ficha (una [Fila]). */
const val TAG_DATO_DE_LA_FICHA: String = "movimiento:dato-de-la-ficha"

/** Cuánto del ancho de un dato de la ficha se lleva el valor: el rótulo («Categoría») cabe en el resto. */
const val FRACCION_DEL_VALOR_DE_LA_FICHA: Float = 0.62f

/** Qué dice cada fila de la ficha, en su orden. */
enum class ClaveDelDato(val rotulo: String) {
    Cuenta("Cuenta"),
    Fecha("Fecha"),
    Categoria("Categoría"),
    Nota("Nota"),
    Origen("Origen"),
    Cuotas("Cuotas"),
    Grupo("Grupo"),
}

/** Un dato de la ficha: su [clave], lo que dice y la sección que lo corrige (`null`: solo informa). */
data class DatoDeLaFicha(val clave: ClaveDelDato, val valor: String, val accion: AccionDelMovimiento?)

/** De dónde vino un movimiento, dicho en una palabra o dos. Pura. */
fun origenDelMovimiento(fuente: com.jvillada.movi.shared.model.EventSource): String = when (fuente) {
    com.jvillada.movi.shared.model.EventSource.MANUAL -> "Anotado a mano"
    com.jvillada.movi.shared.model.EventSource.SMS -> "Aviso del banco"
    com.jvillada.movi.shared.model.EventSource.OCR -> "Comprobante"
    com.jvillada.movi.shared.model.EventSource.STATEMENT -> "Extracto"
}

/**
 * **Los datos de la ficha** de [evento], en el orden de [ClaveDelDato]. Solo va lo que hay: sin cuenta
 * conocida no va «Cuenta», la nota solo si dice algo distinto del título, las cuotas solo en una compra
 * diferida, el grupo solo en un gasto repartido. Pura.
 */
fun datosDeLaFicha(
    evento: FinancialEvent,
    nombreDeLaCuenta: String?,
    espacio: EspacioCompartido?,
    hoy: kotlinx.datetime.LocalDate,
): List<DatoDeLaFicha> = buildList {
    nombreDeLaCuenta?.takeIf { it.isNotBlank() }?.let { add(DatoDeLaFicha(ClaveDelDato.Cuenta, it, AccionDelMovimiento.Nota)) }
    add(DatoDeLaFicha(ClaveDelDato.Fecha, lineaDeCuandoYDonde(evento, null, hoy), AccionDelMovimiento.Fecha))
    add(DatoDeLaFicha(ClaveDelDato.Categoria, evento.category, AccionDelMovimiento.Categoria))
    notaDeLaFicha(evento, tituloDelMovimiento(evento, nombreDeLaCuenta))
        ?.let { add(DatoDeLaFicha(ClaveDelDato.Nota, it, AccionDelMovimiento.Nota)) }
    add(DatoDeLaFicha(ClaveDelDato.Origen, origenDelMovimiento(evento.source), null))
    evento.cuotas?.takeIf { it > 1 }?.let { add(DatoDeLaFicha(ClaveDelDato.Cuotas, "$it cuotas", null)) }
    if (evento.espacioId != null) {
        add(DatoDeLaFicha(ClaveDelDato.Grupo, espacio?.nombre?.trim()?.ifEmpty { null } ?: "Repartido", null))
    }
}

/**
 * **La nota de la ficha**: lo que va después de « · » en el concepto («Mercado del sábado · con Caro» →
 * «con Caro», la forma en que Movi guarda la nota del dueño); sin « · », el concepto si dice algo que el
 * título y la categoría no dicen. `null` si no hay nada que agregar. Pura.
 */
fun notaDeLaFicha(evento: FinancialEvent, titulo: String): String? {
    val descripcion = descripcionParaMostrar(evento).trim()
    if (SEPARADOR_DE_LA_NOTA in descripcion) return descripcion.substringAfter(SEPARADOR_DE_LA_NOTA).trim().ifEmpty { null }
    return descripcion.takeIf { it.isNotEmpty() && !it.equals(titulo, ignoreCase = true) && !it.equals(evento.category, ignoreCase = true) }
}

/** Lo que separa el concepto de la nota del dueño en `description`. */
private const val SEPARADOR_DE_LA_NOTA: String = " · "

/**
 * **Las acciones de la ficha**: las de la fila del teléfono sin «Categoría» ni «Fecha» —en la ficha son
 * filas que se tocan— y sin el tope de [MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO]: con dos menos, «Repartir»
 * siempre cabe en el renglón. Pura.
 */
internal fun accionesDeLaFicha(
    ofreceRepetir: Boolean,
    ofreceRepartir: Boolean,
    conTercero: Boolean,
    conMas: Boolean,
): List<AccionDelMovimiento> = buildList {
    add(AccionDelMovimiento.Nota)
    if (ofreceRepetir) add(AccionDelMovimiento.Repetir)
    if (ofreceRepartir) add(AccionDelMovimiento.Repartir)
    if (conTercero) add(AccionDelMovimiento.Tercero)
    if (conMas) add(AccionDelMovimiento.Mas)
}

/**
 * **La acción amarilla de la ficha** (la regla del amarillo, vuelta 79: uno por vista). «Editar»,
 * que es lo que más se hace con un movimiento ya anotado; ninguna si [porConfirmar] —ahí el amarillo es
 * «Confirmar», que va arriba— o si [otroAmarilloALaVista] (la tarjeta de los avisos del banco, en la
 * lista de al lado). Pura.
 */
internal fun principalDeLaFicha(
    acciones: List<AccionDelMovimiento>,
    porConfirmar: Boolean,
    otroAmarilloALaVista: Boolean,
): AccionDelMovimiento? =
    AccionDelMovimiento.Nota.takeIf { it in acciones && !porConfirmar && !otroAmarilloALaVista }

/** Los datos de la ficha, en una superficie de lista dentro de la ficha (sin relleno propio a los lados). */
@Composable
internal fun DatosDeLaFicha(
    datos: List<DatoDeLaFicha>,
    abierta: AccionDelMovimiento?,
    onElegir: (AccionDelMovimiento) -> Unit,
) {
    Superficie(
        modifier = Modifier.fillMaxWidth(),
        nivel = NivelDeSuperficie.Capa2,
        relleno = RellenoDeSuperficie.Lista,
    ) {
        datos.forEachIndexed { i, dato ->
            if (i > 0) com.jvillada.movi.ui.components.ds.Separador(com.jvillada.movi.ui.components.ds.SangriaDeSeparador.Ninguna)
            val accion = dato.accion
            // Dos nodos: el tag de todo dato, y el de la acción cuando la fila es su puerta («Categoría»,
            // «Fecha»), el mismo tag que su botón del teléfono.
            Box(modifier = Modifier.testTag(TAG_DATO_DE_LA_FICHA)) {
            Box(
                modifier = Modifier
                    .then(if (accion != null && dato.clave.esLaPuertaDe(accion)) Modifier.testTag(tagDeAccionDelMovimiento(accion)) else Modifier)
                    .semantics { if (accion != null) selected = accion == abierta },
            ) {
                Fila(
                    titulo = dato.clave.rotulo,
                    tonoDelTitulo = com.jvillada.movi.ui.components.ds.TonoDeFila.Apagado,
                    final = { ValorDelDato(dato) },
                    onClick = accion?.let { { onElegir(it) } },
                    chevron = accion != null,
                )
            }
            }
        }
    }
}

/**
 * ¿Es esta fila la que hace de la acción [accion] (la que lleva su tag, para que se encuentre como la del
 * teléfono)? «Categoría» y «Fecha»: sus filas. «Editar» tiene su botón; la cuenta y la nota también lo
 * abren, sin su tag.
 */
private fun ClaveDelDato.esLaPuertaDe(accion: AccionDelMovimiento): Boolean =
    (this == ClaveDelDato.Categoria && accion == AccionDelMovimiento.Categoria) ||
        (this == ClaveDelDato.Fecha && accion == AccionDelMovimiento.Fecha)

@Composable
private fun ValorDelDato(dato: DatoDeLaFicha) {
    Row(
        modifier = Modifier.fillMaxWidth(FRACCION_DEL_VALOR_DE_LA_FICHA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dato.clave == ClaveDelDato.Categoria) IconoDeCategoria(dato.valor, tamano = TamanoDeIconoDeCategoria.Chico)
        Text(
            dato.valor,
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * **Las acciones de la ficha, alineadas a la izquierda en un renglón** (si no caben, siguen abajo, también
 * a la izquierda): un [Boton] chico por acción, la [principal] en amarillo y el resto secundarias; «Más»
 * es el «⋯». La abierta lo declara con `selected`; tocarla otra vez la cierra.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AccionesDeLaFicha(
    acciones: List<AccionDelMovimiento>,
    principal: AccionDelMovimiento?,
    abierta: AccionDelMovimiento?,
    onElegir: (AccionDelMovimiento) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(TAG_ACCIONES_DE_LA_FICHA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        acciones.forEach { accion ->
            val elegida = accion == abierta
            // El tag en su propio nodo: el botón trae el suyo.
            Box(modifier = Modifier.testTag(tagDeAccionDelMovimiento(accion)).semantics { selected = elegida }) {
                if (accion == AccionDelMovimiento.Mas) {
                    com.jvillada.movi.ui.components.ds.BotonDeIcono(
                        icono = accion.icono,
                        descripcion = "Más acciones",
                        onClick = { onElegir(accion) },
                        variante = com.jvillada.movi.ui.components.ds.VarianteDeBotonDeIcono.Secundario,
                    )
                } else {
                    Boton(
                        accion.rotulo,
                        onClick = { onElegir(accion) },
                        variante = if (accion == principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                        tamano = TamanoDeBoton.S,
                        icono = accion.icono,
                    )
                }
            }
        }
    }
}
