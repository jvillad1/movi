package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.comoSeLeeEnLaFicha
import com.jvillada.movi.shared.model.laLlaveEsUnNombre
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.quitarIdentificador
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.credits.FieldBox
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.loQueSeLeMandoPorPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * **Lo que le mandaste a esta persona**: el total, el total período por período, y los movimientos.
 *
 * Es la mitad del pedido del dueño —*«poder ver los movimientos hacia esa cuenta»*— y vive en la
 * hoja del propio destino por lo que explica el KDoc de [DestinosScreen]: es la única de las tres
 * puertas posibles que no agrega una pantalla ni un chip por persona registrada.
 *
 * ### Por período del DUEÑO, no por mes de calendario
 *
 * Su corte es 25, así que una transferencia del 26 de agosto pertenece a «septiembre» — igual que su
 * salario, y igual que lo que dicen Movimientos, Presupuestos y el Inicio. Ver
 * [loQueSeLeMandoPorPeriodo].
 *
 * ### Y dice de dónde salió cada renglón
 *
 * Abajo, en letra chica, se explica **cómo** Movi decidió que un movimiento fue para allá: porque el
 * banco nombró el número, o porque el concepto dice el nombre. Sin eso, una lista de plata que
 * aparece sola no se puede verificar — y este repo ya pagó por cifras que nadie podía auditar.
 */
@Composable
fun DetalleDelDestinoSheet(
    destino: DestinoConocido,
    /** El período del dueño; `null` = esta hoja lo lee del perfil (la abren lugares que no lo tienen). */
    ajustes: PeriodSettings?,
    onDismiss: () -> Unit,
    /** «Editar» abre la hoja de alta/edición; `null` = quien la abre no la tiene y no se ofrece. */
    onEditar: (() -> Unit)?,
    /** Los otros terceros guardados: a dónde se puede mover un identificador. */
    otros: List<DestinoConocido> = emptyList(),
    /** Algo cambió (un identificador, el tipo, nombres renombrados): quien la abrió relee. */
    onCambio: () -> Unit = {},
) {
    val alcance = rememberCoroutineScope()
    var actual by remember(destino.id) { mutableStateOf(destino) }
    var movimientos by remember(destino.id) { mutableStateOf<List<FinancialEvent>?>(null) }
    var recibidos by remember(destino.id) { mutableStateOf<List<FinancialEvent>>(emptyList()) }
    var totales by remember(destino.id) { mutableStateOf(destino.totales) }
    var totalesRecibidos by remember(destino.id) { mutableStateOf(destino.recibidos) }
    var error by remember(destino.id) { mutableStateOf<String?>(null) }
    var periodo by remember { mutableStateOf(ajustes) }
    var renombrables by remember(destino.id) { mutableStateOf<MovimientosParaRenombrar?>(null) }
    var recargar by remember(destino.id) { mutableStateOf(0) }

    LaunchedEffect(destino.id, recargar) {
        runCatching { Repositories.wallets.getMovimientosDelDestino(destino.id) }
            .onSuccess {
                movimientos = it.movimientos
                recibidos = it.recibidos
                totales = it.destino.totales
                totalesRecibidos = it.destino.recibidos
            }
            .onFailure { error = it.toUserMessage() }
        // Secundario: sin esto la ficha se lee igual, solo no ofrece renombrar.
        runCatching { Repositories.wallets.getRenombrablesDelDestino(destino.id) }
            .onSuccess { renombrables = it }
    }
    LaunchedEffect(Unit) {
        if (periodo == null) {
            runCatching { Repositories.wallets.getUserProfile() }
                .onSuccess { periodo = PeriodSettings(it.periodCutoffDay, it.periodStarts) }
            if (periodo == null) periodo = PeriodSettings()
        }
    }

    val hoy = remember { hoyEnAppZone() }
    val porPeriodo = remember(movimientos, periodo) {
        val p = periodo
        if (p == null) emptyList() else movimientos?.let { loQueSeLeMandoPorPeriodo(it, p) }.orEmpty()
    }
    // Lo enviado y lo recibido en una sola lista, del más reciente al más viejo.
    val todos = remember(movimientos, recibidos) {
        movimientos?.let { (it + recibidos).distinctBy { e -> e.id }.sortedByDescending { e -> e.timestamp } }
    }

    fun cambiarTipo(tipo: TipoDeTercero) {
        if (tipo == actual.tipoOPersona()) return
        alcance.launch {
            runCatching { Repositories.wallets.updateDestino(actual.id, actual.copy(tipo = tipo)) }
                .onSuccess { actual = actual.copy(tipo = it.tipo ?: tipo); onCambio() }
                .onFailure { error = it.toUserMessage() }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_FICHA_DEL_TERCERO),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    actual.nombre,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
                if (onEditar != null) {
                    Text(
                        "Editar",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.marca,
                        modifier = Modifier.clickable(onClick = onEditar),
                    )
                }
            }
            Text(
                subtituloDelDestino(actual),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            Text(
                "No es una cuenta tuya: no entra en tu plata ni en tu patrimonio.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))
            SelectorSegmentado(
                labels = TipoDeTercero.entries.map { it.comoSeDice() },
                selected = actual.tipoOPersona().ordinal,
                onSelect = { cambiarTipo(TipoDeTercero.entries[it]) },
            )

            Spacer(Modifier.height(18.dp))
            IdentificadoresDeLaFicha(
                destino = actual,
                otros = otros,
                onCambio = { nuevo -> actual = nuevo; recargar++; onCambio() },
                onError = { error = it },
            )

            renombrables?.takeIf { it.movimientos.isNotEmpty() }?.let { r ->
                Spacer(Modifier.height(18.dp))
                PonerleElNombre(
                    destino = actual,
                    renombrables = r,
                    onRenombrados = { renombrables = null; recargar++; onCambio() },
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LE HAS ENVIADO", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                    Spacer(Modifier.height(6.dp))
                    if (totales.isEmpty()) {
                        Text("Nada todavía", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    } else {
                        TotalesEnColumna(totales, alineadoAlFinal = false)
                    }
                }
                // 4-oct-2026: lo que te envió, al lado y aparte. Nunca suma con lo enviado.
                if (totalesRecibidos.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TE HA ENVIADO", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                        Spacer(Modifier.height(6.dp))
                        TotalesEnColumna(totalesRecibidos, alineadoAlFinal = false)
                    }
                }
            }

            if (porPeriodo.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("LE ENVIASTE POR PERÍODO", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    porPeriodo.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                nombreDe(p.periodo),
                                style = Movi.textos.cuerpo,
                                color = Movi.colores.textoMedio,
                                modifier = Modifier.weight(1f),
                            )
                            TotalesEnColumna(p.totales, alineadoAlFinal = true)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))

            Text("MOVIMIENTOS", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(8.dp))
            when {
                error != null -> Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
                todos == null -> Text(
                    "Cargando…",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                )
                todos.isEmpty() -> Text(
                    NADA_TODAVIA,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todos.forEach { ev ->
                        val entro = ev.type == TransactionType.INCOME
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.description, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                                Text(
                                    (if (entro) "Te envió · " else "") + etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoy),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                )
                            }
                            Text(
                                (if (entro) "+" else "") + formatMoney(ev.amount, ev.currency),
                                style = Movi.textos.monto,
                                fontWeight = FontWeight.Medium,
                                color = if (entro) Movi.colores.entra else Movi.colores.texto,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(COMO_SE_CUENTAN, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** El tag de la ficha de un tercero, para las pruebas. */
const val TAG_FICHA_DEL_TERCERO: String = "ficha-del-tercero"

/**
 * **«Cómo lo reconoce Movi»**: cada identificador con **Quitar** y **Mover a otra persona**, y
 * **Agregar un número o una llave**. Quitar el último no se ofrece: un tercero sin identificadores
 * no reconoce nada (el server también lo rechaza).
 */
@Composable
private fun IdentificadoresDeLaFicha(
    destino: DestinoConocido,
    otros: List<DestinoConocido>,
    onCambio: (DestinoConocido) -> Unit,
    onError: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val ids = destino.todosLosIdentificadores()
    var moviendo by remember(destino.id) { mutableStateOf<IdentificadorDelDestino?>(null) }
    var agregando by remember(destino.id) { mutableStateOf(false) }
    var tipoNuevo by remember(destino.id) { mutableStateOf(TipoDeIdentificador.NUMERO) }
    var valorNuevo by remember(destino.id) { mutableStateOf("") }
    var ocupado by remember(destino.id) { mutableStateOf(false) }

    fun hacer(bloque: suspend () -> DestinoConocido?) {
        if (ocupado) return
        ocupado = true
        alcance.launch {
            runCatching { bloque() }
                .onSuccess { it?.let(onCambio) }
                .onFailure { onError(it.toUserMessage()) }
            ocupado = false
        }
    }

    Text("CÓMO LO RECONOCE MOVI", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ids.forEach { id ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(comoSeLeeEnLaFicha(id), style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Text(queEsElIdentificador(id), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
                if (otros.isNotEmpty()) {
                    Enlace("Mover", { moviendo = if (moviendo == id) null else id }, enabled = !ocupado)
                    Spacer(Modifier.width(14.dp))
                }
                if (ids.size > 1) {
                    Enlace(
                        "Quitar",
                        { hacer { Repositories.wallets.quitarIdentificador(destino.id, id) } },
                        enabled = !ocupado,
                    )
                }
            }
            if (moviendo == id) {
                Text(
                    "¿De quién es ${id.comoSeDice}?",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                otros.sortedBy { normalizarParaBuscar(it.nombre) }.forEach { otro ->
                    Enlace(
                        "Mover a ${otro.nombre}",
                        {
                            hacer {
                                Repositories.wallets.agregarIdentificador(otro.id, AgregarIdentificador(id, mover = true))
                                moviendo = null
                                quitarIdentificador(destino, id)
                            }
                        },
                        enabled = !ocupado,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    if (!agregando) {
        Enlace(AGREGAR_UN_IDENTIFICADOR, { agregando = true }, enabled = !ocupado)
    } else {
        SelectorSegmentado(
            labels = listOf("Número de cuenta", "Llave o nombre"),
            selected = tipoNuevo.ordinal,
            onSelect = { tipoNuevo = TipoDeIdentificador.entries[it] },
        )
        Spacer(Modifier.height(8.dp))
        FieldBox(
            if (tipoNuevo == TipoDeIdentificador.NUMERO) "Ej: *55500001111" else "Ej: @caro o como la nombra el banco",
            valorNuevo,
            { valorNuevo = it },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastilla(
                "Agregar",
                {
                    val nuevo = IdentificadorDelDestino(tipoNuevo, valorNuevo.trim())
                    hacer {
                        Repositories.wallets.agregarIdentificador(destino.id, AgregarIdentificador(nuevo)).also {
                            agregando = false
                            valorNuevo = ""
                        }
                    }
                },
                principal = true,
                enabled = !ocupado && valorNuevo.isNotBlank(),
            )
            Pastilla("Cancelar", { agregando = false; valorNuevo = "" }, enabled = !ocupado)
        }
    }
}

/** El enlace que abre el formulario corto de un identificador más. */
const val AGREGAR_UN_IDENTIFICADOR: String = "Agregar un número o una llave"

/** «Número de cuenta», «Llave», «Como lo nombra el banco». */
internal fun queEsElIdentificador(id: IdentificadorDelDestino): String = when {
    id.tipo == TipoDeIdentificador.NUMERO -> "Número de cuenta"
    laLlaveEsUnNombre(id.valor) -> "Como lo nombra el banco"
    else -> "Llave"
}

/**
 * **«Ponerle el nombre a 3 movimientos anteriores»** (4-oct-2026): los anotados con un nombre que
 * nadie lee («Transferencia a la cuenta *41279033068»). Primero la cantidad y cuáles; después una
 * confirmación que dice el nombre nuevo. **Nunca se renombra solo**, y el server vuelve a validar que
 * cada uno siga siendo ilegible: lo que escribió el dueño no se toca.
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

    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_PONERLE_EL_NOMBRE),
        variant = MinCardVariant.Default,
        padding = PaddingValues(14.dp),
    ) {
        resultado?.let {
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            return@MinCard
        }
        Text(textoDePonerleElNombre(n), style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Text(
            renombrables.movimientos.take(3).joinToString(" · ") { it.description } + if (n > 3) " · …" else "",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Lo que se dice cuando el destino está guardado pero todavía no se le reconoció ningún envío. */
internal const val NADA_TODAVIA: String =
    "Todavía no hay ninguno. Aparecen aquí en cuanto el banco te avise de una transferencia a esa " +
        "cuenta o a su llave, o si el concepto de un movimiento dice el nombre que le pusiste."

/**
 * **De dónde sale cada renglón**, dicho en la pantalla. Ver `vaHaciaElDestino` en `:core` para las
 * dos señales; esto es la misma regla en palabras del dueño, para que la lista se pueda auditar.
 */
internal const val COMO_SE_CUENTAN: String =
    "Se cuentan los movimientos en los que el banco nombró alguno de sus números, llaves o su nombre " +
        "—aunque después les hayas cambiado el nombre— y los que digan el nombre que le pusiste. Lo que " +
        "te envió va aparte de lo que le enviaste: nunca se suman."
