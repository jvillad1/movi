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
    LaunchedEffect(hacenFalta) {
        if (hacenFalta && destinos == null) {
            runCatching { Repositories.wallets.getDestinos() }.onSuccess { destinos = it }
        }
    }
    return DestinosParaGuardar(destinos) { guardado ->
        destinos = destinos.orEmpty().filterNot { it.id == guardado.id } + guardado
    }
}

/** Lo que devuelve [rememberDestinosParaGuardar]: la lista y cómo sumarle el recién guardado. */
internal class DestinosParaGuardar(
    val guardados: List<DestinoConocido>?,
    val alGuardar: (DestinoConocido) -> Unit,
) {
    /**
     * ¿Se ofrece la fila para [identificador]? Ver `ofreceGuardarElDestino` en `:core`.
     *
     * **Sin las cuentas del dueño tampoco se ofrece**: la lista vacía es «todavía no llegaron», y
     * sin ella no se puede descartar que el número sea el de una cuenta SUYA — un traspaso entre
     * sus cuentas nombra la de destino igual que una transferencia a otra persona.
     */
    fun ofrece(identificador: IdentificadorDelDestino?, cuentas: List<Account>): Boolean =
        guardados != null && cuentas.isNotEmpty() && ofreceGuardarElDestino(identificador, guardados, cuentas)
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
 * Cerrada es una pregunta y un botón. Abierta, dos campos y nada más: el nombre —prellenado con el
 * que trajo el banco, en Título Caso— y «de quién es», opcional. El número o la llave no se
 * escriben: son los del mensaje.
 *
 * Si el nombre que escribe ya es el de una cuenta guardada a la que le falta este dato («Caro»
 * tiene el número y esto es su llave), **se le agrega a esa** en vez de crear una segunda — lo
 * decide `destinoParaGuardar` en `:core`, y la fila lo dice antes de guardar.
 */
@Composable
internal fun FilaGuardarElDestino(
    identificador: IdentificadorDelDestino,
    nombreSugerido: String,
    destinos: List<DestinoConocido>,
    onGuardado: (DestinoConocido) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alcance = rememberCoroutineScope()
    var abierta by remember(identificador) { mutableStateOf(false) }
    var nombre by remember(identificador, nombreSugerido) { mutableStateOf(nombreSugerido) }
    var deQuien by remember(identificador) { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember(identificador) { mutableStateOf<String?>(null) }

    val aGuardar = destinoParaGuardar(identificador, nombre, deQuien, destinos)
    val loQueFalta = rechazoDelDestino(aGuardar.nombre, aGuardar.numero, aGuardar.deQuien, aGuardar.llave)
    val sePuedeGuardar = loQueFalta == null && !guardando
    // «Caro» ya existe y esto se le suma: se dice, para que no parezca que se crea otra.
    val seSumaA = aGuardar.takeIf { it.id.isNotEmpty() }
    // Ya hay una con ese nombre y con este mismo tipo de dato: se crearía una segunda «Caro».
    val yaHayOtraConEseNombre = seSumaA == null && nombre.isNotBlank() &&
        destinos.any { normalizarParaBuscar(it.nombre.trim()) == normalizarParaBuscar(nombre.trim()) }

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
        FieldBox("De quién es (opcional). Ej: esposa", deQuien, { deQuien = it })
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                seSumaA != null -> "Se agrega a «${seSumaA.nombre}», que ya tienes guardada."
                yaHayOtraConEseNombre -> "Ya tienes una cuenta guardada con ese nombre. Esta queda aparte."
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
    }
}
