package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.destinoConEseIdentificador
import com.jvillada.movi.shared.model.identificadoresDelDestino
import com.jvillada.movi.shared.model.tipoProbable
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.destinoParaGuardar
import com.jvillada.movi.shared.model.enTituloCaso
import com.jvillada.movi.shared.model.laLlaveEsUnNombre
import com.jvillada.movi.shared.model.nombreSugeridoParaElDestino
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.ofreceGuardarElDestino
import com.jvillada.movi.shared.model.rechazoDelDestino
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import kotlinx.coroutines.launch

/** El tag de la fila «¿De quién es la cuenta ·1234?», se muestre donde se muestre. */
const val TAG_GUARDAR_EL_DESTINO: String = "guardar-el-destino"

/** El botón que abre el formulario corto de la fila. */
const val GUARDAR_COMO: String = "Guardar como…"

/**
 * **El enlace que despliega la nota** (`deQuien`: esposa, papá). Se usa en la fila y en la hoja de
 * alta: el nombre casi siempre ya dice de quién es, así que la relación es un extra que se pide
 * solo si él quiere.
 */
const val AGREGAR_UNA_NOTA: String = "Agregar una nota (ej. esposa)"

/** Lo que dice el campo de la nota, ya desplegado. */
internal const val NOTA_DEL_DESTINO: String = "Nota (opcional). Ej: esposa, papá"

/** El enlace que suma el dato a un tercero que ya está guardado (4-oct-2026). */
const val ES_DE_UN_TERCERO_QUE_YA_TENGO: String = "Es de un tercero que ya tengo"

/** El enlace que dice que la cuenta es del dueño: la fila no vuelve a preguntar. */
const val ES_MIA: String = "Es mía"

/** El tag de la lista de terceros que se abre con [ES_DE_UN_TERCERO_QUE_YA_TENGO]. */
const val TAG_TERCEROS_QUE_YA_TENGO: String = "terceros-que-ya-tengo"

/**
 * **La pregunta de la fila**, según lo que el banco haya dicho: «¿De quién es la cuenta ·0756?»,
 * «¿De quién es la llave 0087?», o —cuando lo que hay es el nombre de quien mandó la plata, que ya
 * contesta de quién es— «¿Guardar a Carolina Restrepo Salazar?».
 */
internal fun preguntaDelDestino(identificador: IdentificadorDelDestino): String =
    if (identificador.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(identificador.valor)) {
        "¿Guardar a ${identificador.comoSeDice} en tus cuentas de otros?"
    } else {
        "¿De quién es ${identificador.comoSeDice}?"
    }

/**
 * **El nombre con que la fila llega prellenada**: el que mandó el banco en Título Caso; si el banco
 * no mandó un nombre de verdad pero el identificador ES un nombre (Nu, «Te llegó dinero de …»), ese;
 * y si no, vacío — un número no es el nombre de nadie y lo escribe él.
 */
internal fun nombreParaLaFila(identificador: IdentificadorDelDestino, nombreDelBanco: String?): String =
    nombreSugeridoParaElDestino(nombreDelBanco).ifEmpty {
        if (identificador.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(identificador.valor)) {
            enTituloCaso(identificador.valor)
        } else {
            ""
        }
    }

/**
 * **Los destinos guardados, para decidir si la fila hace falta** — leídos solo cuando [hacenFalta]
 * (hay un identificador en pantalla). `null` mientras no contestó o si la lectura falló: sin saber
 * qué hay guardado **no se ofrece nada**, porque ofrecer guardar una cuenta que ya está guardada es
 * peor que no ofrecerlo (el server lo rechazaría con un 409 que el dueño no entendería).
 */
@Composable
internal fun rememberDestinosParaGuardar(hacenFalta: Boolean): DestinosParaGuardar {
    var destinos by remember { mutableStateOf<List<DestinoConocido>?>(null) }
    var descartados by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(hacenFalta) {
        if (hacenFalta && destinos == null) {
            runCatching { Repositories.wallets.getDestinos() }.onSuccess { destinos = it }
            // Lo que el dueño ya dijo que es suyo o que ignore, y lo que Movi sabe que es suyo (4-oct).
            // Si falla, se pregunta igual: es mejor preguntar de más que callar algo nuevo.
            runCatching { Repositories.wallets.getDestinosDescartados() }.onSuccess { descartados = it.claves.toSet() }
        }
    }
    return DestinosParaGuardar(destinos, descartados) { guardado ->
        destinos = destinos.orEmpty().filterNot { it.id == guardado.id } + guardado
    }
}

/** Lo que devuelve [rememberDestinosParaGuardar]: la lista y cómo sumarle el recién guardado. */
internal class DestinosParaGuardar(
    val guardados: List<DestinoConocido>?,
    val descartados: Set<String> = emptySet(),
    val alGuardar: (DestinoConocido) -> Unit,
) {
    /** El tercero guardado que ya se reconoce por [identificador], si hay uno solo. */
    fun deQuienEs(identificador: IdentificadorDelDestino?): DestinoConocido? =
        identificador?.let { id -> guardados?.let { destinoConEseIdentificador(id, it) } }

    /**
     * ¿Se ofrece la fila para [identificador]? Ver `ofreceGuardarElDestino` en `:core`.
     *
     * **Sin las cuentas del dueño tampoco se ofrece**: la lista vacía es «todavía no llegaron», y
     * sin ella no se puede descartar que el número sea el de una cuenta SUYA — un traspaso entre
     * sus cuentas nombra la de destino igual que una transferencia a otra persona.
     */
    fun ofrece(identificador: IdentificadorDelDestino?, cuentas: List<Account>): Boolean =
        guardados != null && cuentas.isNotEmpty() && ofreceGuardarElDestino(identificador, guardados, cuentas, descartados)
}

/**
 * # «¿De quién es la cuenta ·1234? Guardar como…»
 *
 * El pedido del dueño (29-sep): *«gestionar cuentas conocidas no propias es un feature que necesito
 * y debe ser de fácil acceso»*. Hasta acá, guardar una cuenta de otro obligaba a salir de donde
 * estaba —Ajustes o Patrimonio → Cuentas de otros → Nueva cuenta— y copiar el número a mano. Esta
 * fila la ofrece **donde el número aparece**: en «Reconciliar movimiento», en las tarjetas de «Por
 * revisar» y en el detalle de un movimiento.
 *
 * Quien la muestra ya decidió que hace falta ([DestinosParaGuardar.ofrece]); con un destino
 * conocido la fila no existe.
 *
 * Cerrada es una pregunta y un botón. Abierta, **un solo campo**: el nombre, prellenado con el que
 * trajo el banco en Título Caso. El número o la llave no se escriben: son los del mensaje.
 *
 * Hasta el 3-oct-2026 había un segundo campo con el mismo peso, «De quién es (opcional)», y el dueño
 * lo vio redundante: la fila ya pregunta «¿De quién es la cuenta ·0756?», y en casi todos los casos
 * el nombre ES de quién es. La relación (`deQuien`: esposa, papá) sigue existiendo —los destinos
 * guardados la tienen y «Cuentas de otros» la muestra—, pero ahora se pide detrás del enlace
 * [AGREGAR_UNA_NOTA].
 *
 * Si el nombre que escribe ya es el de una cuenta guardada («Caro» tiene el número y esto es su
 * llave), **se le agrega a esa** en vez de crear una segunda — lo decide `destinoParaGuardar` en
 * `:core`, y la fila lo dice antes de guardar.
 *
 * ## Desde el 4-oct-2026
 *
 * - **«Es de un tercero que ya tengo»** abre la lista de los guardados: elegir uno le suma este
 *   identificador (`agregarIdentificador`), sin escribir nada. Es el caso de Caro, que el banco a
 *   veces nombra por la cuenta y Nu por su nombre completo.
 * - **Persona o comercio**, prellenado con [tipoProbable] (un pago por QR es un comercio) y
 *   cambiable con un toque.
 * - **«Es mía»**: la cuenta es del dueño; la fila no vuelve a preguntar (`descartarSugerido`).
 *
 * [textoDelAviso] es el texto del banco, si quien muestra la fila lo tiene: con él se reconoce un
 * pago por QR.
 */
@Composable
internal fun FilaGuardarElDestino(
    identificador: IdentificadorDelDestino,
    nombreSugerido: String,
    destinos: List<DestinoConocido>,
    onGuardado: (DestinoConocido) -> Unit,
    modifier: Modifier = Modifier,
    textoDelAviso: String? = null,
) {
    val alcance = rememberCoroutineScope()
    var abierta by remember(identificador) { mutableStateOf(false) }
    var nombre by remember(identificador, nombreSugerido) { mutableStateOf(nombreSugerido) }
    var deQuien by remember(identificador) { mutableStateOf("") }
    var conNota by remember(identificador) { mutableStateOf(false) }
    var tipo by remember(identificador) { mutableStateOf(tipoProbable(identificador, textoDelAviso)) }
    var eligiendoTercero by remember(identificador) { mutableStateOf(false) }
    var esMia by remember(identificador) { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember(identificador) { mutableStateOf<String?>(null) }

    val aGuardar = destinoParaGuardar(identificador, nombre, deQuien, destinos, tipo)
    val loQueFalta = rechazoDelDestino(aGuardar.nombre, aGuardar.numero, aGuardar.deQuien, aGuardar.llave)
    val sePuedeGuardar = loQueFalta == null && !guardando
    // «Caro» ya existe y esto se le suma: se dice, para que no parezca que se crea otra.
    val seSumaA = aGuardar.takeIf { it.id.isNotEmpty() }

    fun sumarA(tercero: DestinoConocido) {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                Repositories.wallets.agregarIdentificador(tercero.id, AgregarIdentificador(identificador))
            }
            guardando = false
            resultado.onSuccess { onGuardado(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    fun marcarComoMia() {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                Repositories.wallets.descartarSugerido(DescartarSugerido(identificador, MotivoDeDescarte.ES_MIA))
            }
            guardando = false
            resultado.onSuccess { esMia = true }.onFailure { error = it.toUserMessage() }
        }
    }

    if (esMia) {
        Text(
            "Listo: ${identificador.comoSeDice} es tuya. Movi no vuelve a preguntar.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = modifier.fillMaxWidth().testTag(TAG_GUARDAR_EL_DESTINO),
        )
        return
    }

    fun guardar() {
        if (!sePuedeGuardar) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                if (aGuardar.id.isNotEmpty()) Repositories.wallets.updateDestino(aGuardar.id, aGuardar)
                else Repositories.wallets.createDestino(aGuardar)
            }
            guardando = false
            resultado.onSuccess { onGuardado(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_GUARDAR_EL_DESTINO)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                preguntaDelDestino(identificador),
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (abierta) "Cerrar" else GUARDAR_COMO,
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.marca,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Movi.colores.marca.copy(alpha = 0.16f))
                    .clickable(enabled = !guardando, role = Role.Button) { abierta = !abierta }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (!abierta) return@Column

        Spacer(Modifier.height(12.dp))
        FieldBox("Nombre. Ej: Caro", nombre, { nombre = it })
        Spacer(Modifier.height(8.dp))
        // Solo para uno nuevo: si se suma a uno guardado, ese ya dijo lo que es.
        if (seSumaA == null) {
            SelectorSegmentado(
                labels = TipoDeTercero.entries.map { it.comoSeDice() },
                selected = tipo.ordinal,
                onSelect = { tipo = TipoDeTercero.entries[it] },
                enabled = !guardando,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (conNota) {
            FieldBox(NOTA_DEL_DESTINO, deQuien, { deQuien = it })
        } else {
            Text(
                AGREGAR_UNA_NOTA,
                style = Movi.textos.apoyo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.marca,
                modifier = Modifier
                    .clickable(enabled = !guardando, role = Role.Button) { conNota = true }
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                seSumaA != null -> "Se agrega a «${seSumaA.nombre}», que ya tienes guardada."
                else -> "No es una cuenta tuya: no entra en tu plata. Movi le pone este nombre a lo que le envíes."
            },
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (sePuedeGuardar) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.fondo)
                .clickable(enabled = sePuedeGuardar, role = Role.Button) { guardar() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (guardando) "Guardando…" else "Guardar cuenta",
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = if (sePuedeGuardar) Movi.colores.marca else Movi.colores.textoApagado,
            )
        }
        // Lo primero que falta, como en la hoja de alta — no un botón gris sin explicación.
        if (loQueFalta != null && !guardando) {
            Spacer(Modifier.height(6.dp))
            Text(loQueFalta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (destinos.isNotEmpty()) {
                Enlace(ES_DE_UN_TERCERO_QUE_YA_TENGO, { eligiendoTercero = !eligiendoTercero }, enabled = !guardando)
            }
            Enlace(ES_MIA, { marcarComoMia() }, enabled = !guardando)
        }
        if (eligiendoTercero) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth().testTag(TAG_TERCEROS_QUE_YA_TENGO),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                destinos.sortedBy { normalizarParaBuscar(it.nombre) }.forEach { tercero ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !guardando, role = Role.Button) { sumarA(tercero) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tercero.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
                        Text(
                            identificadoresDelDestino(tercero).take(2).joinToString(" · "),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                        )
                    }
                }
            }
        }
    }
}

/** El tag de la fila «Es de Caro · Ver su ficha» del detalle de un movimiento. */
const val TAG_FILA_DEL_TERCERO: String = "fila-del-tercero"

/** El enlace que abre la ficha del tercero desde un movimiento. */
const val VER_SU_FICHA: String = "Ver su ficha"

/**
 * **«Es de Caro, en tus cuentas de otros · Ver su ficha»** (4-oct-2026): en el detalle de un
 * movimiento que ya es de un tercero guardado. Tocarla abre su ficha ([DetalleDelDestinoSheet]) con
 * todo lo que le enviaste y te envió — el «Ver todo lo de Caro» que Movimientos no tiene como
 * filtro. [recienGuardado] = se acaba de guardar desde esta misma hoja.
 */
@Composable
internal fun FilaDelTercero(
    tercero: DestinoConocido,
    recienGuardado: Boolean,
    otros: List<DestinoConocido>,
    modifier: Modifier = Modifier,
) {
    var fichaAbierta by remember(tercero.id) { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_FILA_DEL_TERCERO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (recienGuardado) "«${tercero.nombre}» quedó en tus cuentas de otros. Este movimiento ya cuenta ahí."
            else "Es de «${tercero.nombre}», en tus cuentas de otros.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        Pastilla(VER_SU_FICHA, { fichaAbierta = true }, principal = true)
    }
    if (fichaAbierta) {
        DetalleDelDestinoSheet(
            destino = tercero,
            ajustes = null,
            onDismiss = { fichaAbierta = false },
            onEditar = null,
            otros = otros,
        )
    }
}
