package com.jvillada.movi.ui.destinos

import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.theme.Espacio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.shared.model.loDeCadaPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador

/**
 * # La ficha de una persona o comercio: **para leer**
 *
 * Lo que el dueño viene a preguntar —*«¿cuánto le mandé a Caro este mes?»*— va primero: **este
 * período**, lo que le enviaste y lo que te envió, lado a lado y nunca sumados. Debajo, período por
 * período en las dos direcciones, el total, y los movimientos — cada uno se abre con un toque
 * ([onAbrirMovimiento]).
 *
 * ### Leer acá, cambiar en «Editar» (4-oct-2026)
 *
 * Hasta esta fecha la ficha tenía, encima de las cifras, el selector Persona/Comercio a todo lo
 * ancho, «Cómo lo reconoce Movi» con Mover y Quitar por renglón y el formulario de agregar un dato.
 * Mezclar campos editables con una lista de plata hace que no se entienda qué se está mirando (es la
 * regla con que nació esta pantalla, ver [DestinosScreen]). Todo eso se mudó a «Editar»
 * ([DestinoSheet]); acá queda una línea que dice qué es y cómo lo reconoce.
 *
 * ### Por período del DUEÑO, no por mes de calendario
 *
 * Su corte es 25, así que una transferencia del 26 de agosto pertenece a «septiembre» — igual que su
 * salario y que lo que dicen Movimientos y el Inicio. Ver [loDeCadaPeriodo].
 */
@Composable
fun DetalleDelDestinoSheet(
    destino: DestinoConocido,
    /** El período del dueño; `null` = esta hoja lo lee del perfil. */
    ajustes: PeriodSettings?,
    onDismiss: () -> Unit,
    /** «Editar» abre la hoja de edición; `null` = quien la abre no la tiene y no se ofrece. */
    onEditar: (() -> Unit)?,
    /** Tocar un movimiento lo abre; `null` = no se ofrece. */
    onAbrirMovimiento: ((FinancialEvent) -> Unit)? = null,
    /** Algo cambió (nombres renombrados): quien la abrió relee. */
    onCambio: () -> Unit = {},
) {
    var movimientos by remember(destino.id) { mutableStateOf<List<FinancialEvent>?>(null) }
    var recibidos by remember(destino.id) { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    var actual by remember(destino.id) { mutableStateOf(destino) }
    var error by remember(destino.id) { mutableStateOf<String?>(null) }
    var periodo by remember { mutableStateOf(ajustes) }
    var renombrables by remember(destino.id) { mutableStateOf<MovimientosParaRenombrar?>(null) }
    var recargar by remember(destino.id) { mutableStateOf(0) }

    LaunchedEffect(destino.id, recargar) {
        runCatching { Repositories.wallets.getMovimientosDelDestino(destino.id) }
            .onSuccess {
                movimientos = it.movimientos
                recibidos = it.recibidos
                actual = it.destino
            }
            .onFailure { error = it.toUserMessage() }
        // Secundario: sin esto la ficha se lee igual, solo no ofrece renombrar.
        runCatching { Repositories.wallets.getRenombrablesDelDestino(destino.id) }
            .onSuccess { renombrables = it }
    }
    LaunchedEffect(Unit) {
        if (periodo == null) {
            runCatching { Repositories.wallets.getUserProfile() }
                .onSuccess { periodo = it.ajustesDelPeriodo() }
            if (periodo == null) periodo = PeriodSettings()
        }
    }

    val hoy = remember { hoyEnAppZone() }
    val porPeriodo = remember(movimientos, recibidos, periodo) {
        val p = periodo
        val m = movimientos
        if (p == null || m == null) emptyList() else loDeCadaPeriodo(m, recibidos, p)
    }
    // **Este período**: de los movimientos en cuanto llegan; mientras tanto, lo que ya trajo la lista.
    val enCurso = remember(periodo) { periodo?.let { periodoActual(kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), it) } }
    val delPeriodo = porPeriodo.firstOrNull { it.periodo == enCurso }
    val enviadoEstePeriodo = if (movimientos != null) delPeriodo?.enviado.orEmpty() else destino.totalesDelPeriodo
    val recibidoEstePeriodo = if (movimientos != null) delPeriodo?.recibido.orEmpty() else destino.recibidosDelPeriodo
    // **Lo que le enviaste este período, por motivo** (la categoría de cada envío; ver
    // `MotivoDelEnvio.kt`): «Mercado $2.000.000 · Cuota de crédito $1.300.000 · Tía $500.000».
    val porMotivo = remember(movimientos, periodo, enCurso) {
        val p = periodo
        val m = movimientos
        if (p == null || m == null || enCurso == null) emptyList()
        else com.jvillada.movi.shared.model.enviadoPorMotivo(m.filter { com.jvillada.movi.shared.model.periodoDe(it.timestamp, p) == enCurso })
    }
    // Lo enviado y lo recibido en una sola lista, del más reciente al más viejo.
    val todos = remember(movimientos, recibidos) {
        movimientos?.let { (it + recibidos).distinctBy { e -> e.id }.sortedByDescending { e -> e.timestamp } }
    }

    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_FICHA_DEL_TERCERO),
        ) {
            // Vuelta de diseño 18: el nombre y, debajo, qué es y cómo lo reconoce Movi, con el
            // encabezado de toda hoja; «Editar» es un botón de texto del sistema.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                EncabezadoDeHoja(
                    actual.nombre,
                    subtitulo = queEsYComoLoReconoce(actual),
                    modifier = Modifier.weight(1f),
                )
                if (onEditar != null) {
                    Spacer(Modifier.width(Espacio.xs))
                    Boton(
                        "Editar",
                        onClick = onEditar,
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                        modifier = Modifier.testTag(TAG_EDITAR_EL_TERCERO),
                    )
                }
            }

            Spacer(Modifier.height(Espacio.m))
            Text("Este período", style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Espacio.xs))
            Row(modifier = Modifier.fillMaxWidth().testTag(TAG_ESTE_PERIODO_DEL_TERCERO)) {
                ColumnaDeCifra("Le enviaste", enviadoEstePeriodo, Modifier.weight(1f))
                ColumnaDeCifra("Te envió", recibidoEstePeriodo, Modifier.weight(1f), entra = true)
            }
            if (porMotivo.isNotEmpty()) {
                Spacer(Modifier.height(Espacio.s))
                Text("Lo que le enviaste, por motivo", style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs), modifier = Modifier.testTag(TAG_POR_MOTIVO_DEL_TERCERO)) {
                    porMotivo.forEach { envio ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                envio.motivo + if (envio.cuantos > 1) " (${envio.cuantos})" else "",
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.texto,
                                modifier = Modifier.weight(1f),
                            )
                            Cifra(envio.monto, moneda = envio.moneda)
                        }
                    }
                }
            }

            renombrables?.takeIf { it.movimientos.isNotEmpty() }?.let { r ->
                Spacer(Modifier.height(Espacio.m))
                PonerleElNombre(
                    destino = actual,
                    renombrables = r,
                    onRenombrados = { renombrables = null; recargar++; onCambio() },
                )
            }

            // Período por período: solo si hay más de uno que mostrar — con uno solo, ya lo dijo arriba.
            if (porPeriodo.size > 1 || (porPeriodo.size == 1 && porPeriodo.single().periodo != enCurso)) {
                Spacer(Modifier.height(Espacio.m))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Por período", style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio, modifier = Modifier.weight(1.4f))
                    Text("Enviaste", style = Movi.textos.apoyoMedio, color = Movi.colores.textoApagado, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("Te envió", style = Movi.textos.apoyoMedio, color = Movi.colores.textoApagado, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(Espacio.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs), modifier = Modifier.testTag(TAG_POR_PERIODO_DEL_TERCERO)) {
                    porPeriodo.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                // Sin «de 2026» si es el año en curso: a 390 dp partía el renglón en dos.
                                nombreDe(p.periodo)
                                    .let { n -> if (p.periodo.year == enCurso?.year) n.removeSuffix(" de ${p.periodo.year}") else n }
                                    .replaceFirstChar { it.uppercase() },
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.weight(1.4f),
                            )
                            CifraDeLaTabla(p.enviado, Modifier.weight(1f))
                            CifraDeLaTabla(p.recibido, Modifier.weight(1f), entra = true)
                        }
                    }
                }
            }

            totalesEnUnaLinea(actual)?.let { linea ->
                Spacer(Modifier.height(Espacio.s))
                Text(linea, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }

            Spacer(Modifier.height(Espacio.m))
            Separador(SangriaDeSeparador.Ninguna)
            Spacer(Modifier.height(Espacio.s))

            Text("Movimientos", style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Espacio.xs))
            when {
                error != null -> Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
                todos == null -> Text("Cargando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                todos.isEmpty() -> Text(NADA_TODAVIA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                else -> Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                    todos.forEach { ev ->
                        val entro = ev.type == TransactionType.INCOME
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (onAbrirMovimiento != null) Modifier.clickable { onAbrirMovimiento(ev) } else Modifier)
                                .padding(vertical = Espacio.xs)
                                .testTag(TAG_MOVIMIENTO_DEL_TERCERO + ":" + ev.id),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.description, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (entro) "Te envió · " else "") + etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoy),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                )
                            }
                            Spacer(Modifier.width(Espacio.xs))
                            Cifra(
                                ev.amount,
                                moneda = ev.currency,
                                tono = if (entro) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Espacio.s))
            Text(COMO_SE_CUENTAN, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Spacer(Modifier.height(Espacio.l))
        }
    }
}

/** El tag de «Lo que le enviaste, por motivo» en la ficha. */
const val TAG_POR_MOTIVO_DEL_TERCERO: String = "ficha-del-tercero:por-motivo"

/** El tag de la ficha de un tercero, para las pruebas. */
const val TAG_FICHA_DEL_TERCERO: String = "ficha-del-tercero"

/** «Editar» en la ficha. */
const val TAG_EDITAR_EL_TERCERO: String = "editar-el-tercero"

/** El bloque «Este período» de la ficha. */
const val TAG_ESTE_PERIODO_DEL_TERCERO: String = "este-periodo-del-tercero"

/** La tabla «Por período» de la ficha. */
const val TAG_POR_PERIODO_DEL_TERCERO: String = "por-periodo-del-tercero"

/** Un movimiento de la ficha: `TAG:<id>`. */
const val TAG_MOVIMIENTO_DEL_TERCERO: String = "movimiento-del-tercero"

/**
 * **Qué es y cómo lo reconoce Movi**, en una línea: «Persona · cuenta ·0756 · llave @caro · Esposa».
 * El tipo primero (así se sabe en qué sección está), los identificadores como los escribe el banco
 * ([identificadorEnUnaLinea]: era «Persona · ·0756», con dos puntos seguidos) y la nota al final.
 */
internal fun queEsYComoLoReconoce(destino: DestinoConocido): String =
    enUnaLinea(
        listOf(destino.tipoOPersona().comoSeDice()) +
            destino.todosLosIdentificadores().map(::identificadorEnUnaLinea) +
            destino.deQuien,
    )

/** «En total: le enviaste $3.500.000 · te envió $900.000», o `null` sin nada. */
internal fun totalesEnUnaLinea(destino: DestinoConocido): String? {
    fun cifras(m: Map<String, Long>) = m.filterValues { it != 0L }.entries.sortedBy { it.key }
        .joinToString(" · ") { (moneda, total) -> formatMoney(total, moneda) }
    val ida = cifras(destino.totales)
    val vuelta = cifras(destino.recibidos)
    if (ida.isEmpty() && vuelta.isEmpty()) return null
    return "En total: " + listOfNotNull(
        ida.takeIf { it.isNotEmpty() }?.let { "le enviaste $it" },
        vuelta.takeIf { it.isNotEmpty() }?.let { "te envió $it" },
    ).joinToString(" · ")
}

/** Una de las dos cifras de «Este período»: el rótulo y el monto, o «Nada» sin nada. */
@Composable
private fun ColumnaDeCifra(rotulo: String, totales: Map<String, Long>, modifier: Modifier, entra: Boolean = false) {
    Column(modifier = modifier) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        Spacer(Modifier.height(Espacio.xxs))
        val conPlata = totales.filterValues { it != 0L }
        if (conPlata.isEmpty()) {
            Text("Nada", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        } else {
            conPlata.entries.sortedBy { it.key }.forEach { (moneda, total) ->
                Cifra(total, moneda = moneda, tono = if (entra) TonoDeCifra.Ingreso else TonoDeCifra.Neutro)
            }
        }
    }
}

@Composable
private fun CifraDeLaTabla(totales: Map<String, Long>, modifier: Modifier, entra: Boolean = false) {
    val conPlata = totales.filterValues { it != 0L }
    Text(
        if (conPlata.isEmpty()) "—" else conPlata.entries.sortedBy { it.key }.joinToString("\n") { formatMoney(it.value, it.key) },
        style = Movi.textos.cuerpo,
        color = when {
            conPlata.isEmpty() -> Movi.colores.textoApagado
            entra -> Movi.colores.entra
            else -> Movi.colores.texto
        },
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

/**
 * **«Ponerle el nombre a 3 movimientos anteriores»** (4-oct-2026): los anotados con un nombre de
 * Movi o del banco («Transferencia a la cuenta *41279033068»). Primero la cantidad y cuáles; después
 * una confirmación que dice el nombre nuevo, y el server vuelve a validar cada uno con la regla de
 * `NombreDelTercero.kt`: lo que escribió el dueño no se toca.
 *
 * **Desde el 20-oct-2026 es un respaldo**: crear, editar, sumar un identificador o unir ya renombran
 * en el server, así que casi siempre no aparece. Queda para lo que se anotó DESPUÉS de la última de
 * esas escrituras (a mano, por un extracto, por un teléfono que subió tarde): sin él, eso se quedaría
 * con el nombre del banco hasta la próxima edición del tercero.
 */
@Composable
private fun PonerleElNombre(
    destino: DestinoConocido,
    renombrables: MovimientosParaRenombrar,
    onRenombrados: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val n = renombrables.movimientos.size
    var confirmando by remember(destino.id) { mutableStateOf(false) }
    var ocupado by remember(destino.id) { mutableStateOf(false) }
    var resultado by remember(destino.id) { mutableStateOf<String?>(null) }

    Superficie(
        modifier = Modifier.fillMaxWidth().testTag(TAG_PONERLE_EL_NOMBRE),
    ) {
        resultado?.let {
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            return@Superficie
        }
        Text(textoDePonerleElNombre(n), style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Text(
            renombrables.movimientos.take(3).joinToString(" · ") { it.description } + if (n > 3) " · …" else "",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            maxLines = 2,
        )
        Spacer(Modifier.height(Espacio.xs))
        if (!confirmando) {
            Pastilla("Ponerle el nombre", { confirmando = true }, principal = true)
        } else {
            Text(
                "Van a decir «${renombrables.nombreNuevoDelGasto}»" +
                    (if (renombrables.movimientos.any { it.type == TransactionType.INCOME }) " (o «${renombrables.nombreNuevoDelIngreso}» lo que te llegó)" else "") +
                    ". Los nombres que escribiste tú no se tocan.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Espacio.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Pastilla(
                    if (ocupado) "Cambiando…" else "Sí, cambiar $n",
                    {
                        ocupado = true
                        alcance.launch {
                            runCatching {
                                Repositories.wallets.renombrarMovimientosDelDestino(destino.id, renombrables.movimientos.map { it.id })
                            }.onSuccess { r ->
                                resultado = if (r.renombrados == 1) "Listo: 1 movimiento ahora dice «${renombrables.nombreNuevoDelGasto}»."
                                else "Listo: ${r.renombrados} movimientos ahora dicen «${renombrables.nombreNuevoDelGasto}»."
                                onRenombrados()
                            }.onFailure { resultado = it.toUserMessage() }
                            ocupado = false
                        }
                    },
                    principal = true,
                    enabled = !ocupado,
                )
                Pastilla("Ahora no", { confirmando = false }, enabled = !ocupado)
            }
        }
    }
}

/** El tag de «Ponerle el nombre a N movimientos anteriores». */
const val TAG_PONERLE_EL_NOMBRE: String = "ponerle-el-nombre"

/** «Ponerle el nombre a 3 movimientos anteriores». */
internal fun textoDePonerleElNombre(n: Int): String =
    if (n == 1) "Ponerle el nombre a 1 movimiento anterior" else "Ponerle el nombre a $n movimientos anteriores"

/** Lo que se dice cuando está guardado pero todavía no se le reconoció ningún movimiento. */
internal const val NADA_TODAVIA: String =
    "Todavía no hay ninguno. Aparecen aquí en cuanto el banco te avise de una transferencia a uno de " +
        "sus números o llaves."

/**
 * **De dónde sale cada renglón**, en una línea, para que la lista se pueda auditar. Ver
 * `vaHaciaElDestino` en `:core`.
 */
internal const val COMO_SE_CUENTAN: String =
    "Cuentan los movimientos en que el banco nombró uno de sus números o llaves, aunque les hayas " +
        "cambiado el nombre, y los que anotaste a su nombre."
