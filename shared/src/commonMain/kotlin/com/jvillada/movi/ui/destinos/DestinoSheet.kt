package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.cuentaPropiaConEseNumero
import com.jvillada.movi.shared.model.mensajeDeNumeroPropio
import com.jvillada.movi.shared.model.rechazoDelDestino
import com.jvillada.movi.shared.model.soloLosDigitos
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmacionEnLinea
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.credits.SectionLabel
import kotlinx.coroutines.launch

/**
 * **Guardar, renombrar o borrar una cuenta de otra persona.** Tres campos y nada más: el nombre, el
 * número, y de quién es.
 *
 * ### La guarda que se muestra ACÁ y no solo en el server
 *
 * Si el número que escribe es el de una cuenta suya, esta hoja lo dice **antes** de mandar nada
 * (ver [cuentaPropiaConEseNumero] y su KDoc, que explica por qué es la guarda más importante de la
 * feature). No es que el server no lo valide —lo valida, y con el mismo mensaje—: es que el dueño
 * tiene que entender el porqué mientras está escribiendo, no después de un rebote.
 *
 * ### Borrar pregunta antes, y dice qué NO se borra
 *
 * Es la regla del proyecto ([ConfirmacionEnLinea]), y acá tiene algo propio que aclarar: borrar un
 * destino **no toca un solo movimiento**. Lo que se olvida es el nombre y la agrupación.
 */
@Composable
fun DestinoSheet(
    cuentas: List<Account>,
    existente: DestinoConocido?,
    onDismiss: () -> Unit,
    onGuardado: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(existente?.nombre ?: "") }
    var numero by remember { mutableStateOf(existente?.numero ?: "") }
    var deQuien by remember { mutableStateOf(existente?.deQuien ?: "") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pidiendoBorrar by remember { mutableStateOf(false) }

    val editando = existente != null
    val digitos = soloLosDigitos(numero)
    val propia = remember(digitos, cuentas) {
        if (digitos.length >= 4) cuentaPropiaConEseNumero(digitos, cuentas) else null
    }
    val loQueFalta = rechazoDelDestino(nombre, digitos, deQuien)
        ?: propia?.let { mensajeDeNumeroPropio(it.name) }
    val sePuedeGuardar = loQueFalta == null && !guardando

    fun guardar() {
        if (!sePuedeGuardar) return
        guardando = true
        error = null
        alcance.launch {
            val destino = DestinoConocido(
                id = existente?.id ?: "",
                nombre = nombre.trim(),
                numero = digitos,
                deQuien = deQuien.trim().ifBlank { null },
            )
            val resultado = if (editando) {
                runCatching { Repositories.wallets.updateDestino(existente!!.id, destino) }
            } else {
                runCatching { Repositories.wallets.createDestino(destino) }
            }
            guardando = false
            resultado.onSuccess { onGuardado() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun borrar() {
        if (existente == null || guardando) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching { Repositories.wallets.deleteDestino(existente.id) }
            guardando = false
            resultado.onSuccess { onGuardado() }.onFailure { error = it.toUserMessage() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !guardando, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !guardando)
            // `weight(1f, fill = false)`: la hoja crece con su contenido y recién ahí desplaza,
            // igual que el resto de las hojas de Movi — sin esto, con el teclado abierto en un
            // teléfono chico el botón de guardar queda fuera de la pantalla y recortado.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editando) "Editar cuenta de otro" else "Nueva cuenta de otro",
                        style = Movi.textos.titulo,
                        color = Movi.colores.texto,
                        modifier = Modifier.weight(1f),
                    )
                    if (editando) {
                        Text(
                            text = if (guardando) "…" else "Eliminar",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.sale,
                            modifier = Modifier.clickable(enabled = !guardando) { pidiendoBorrar = true },
                        )
                    }
                }
                if (pidiendoBorrar && existente != null) {
                    ConfirmacionEnLinea(
                        pregunta = "¿Eliminar «${existente.nombre}»?",
                        detalle = "Se borra el nombre y la agrupación. Tus movimientos no se tocan: " +
                            "siguen ahí, con el nombre que tengan. No se puede deshacer.",
                        textoConfirmar = "Eliminar",
                        ocupado = guardando,
                        onConfirmar = { borrar() },
                        onCancelar = { pidiendoBorrar = false },
                        modifier = Modifier.padding(bottom = 18.dp),
                    )
                }

                Text(
                    QUE_ES_ESTO,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(bottom = 18.dp),
                )

                SectionLabel("NOMBRE")
                Spacer(Modifier.height(8.dp))
                FieldBox("Ej: Caro", nombre, { nombre = it })

                Spacer(Modifier.height(18.dp))

                SectionLabel("NÚMERO DE LA CUENTA")
                Spacer(Modifier.height(8.dp))
                // Se acepta pegado como lo mandó el banco («*31973270756», con guiones o espacios)
                // y se guardan solo los dígitos: dos formas del mismo número no pueden verse como
                // dos destinos distintos. Ver `soloLosDigitos`.
                FieldBox("Ej: *31973270756", numero, { numero = it })
                Spacer(Modifier.height(6.dp))
                Text(
                    "Puedes pegarlo como te lo manda el banco. Movi se queda solo con los dígitos.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                )

                Spacer(Modifier.height(18.dp))

                SectionLabel("DE QUIÉN ES (OPCIONAL)")
                Spacer(Modifier.height(8.dp))
                FieldBox("Ej: esposa, papá", deQuien, { deQuien = it })

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (sePuedeGuardar) Movi.colores.marca.copy(alpha = 0.16f)
                            else Movi.colores.tarjeta,
                        )
                        .clickable(enabled = sePuedeGuardar) { guardar() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            guardando -> if (editando) "Guardando…" else "Guardando…"
                            editando -> "Guardar cambios"
                            else -> "Guardar cuenta"
                        },
                        style = Movi.textos.titulo,
                        color = if (sePuedeGuardar) Movi.colores.marca else Movi.colores.textoApagado,
                    )
                }
                // Mismo patrón que el resto de las hojas: se dice LO PRIMERO que falta, no un botón
                // gris sin explicación.
                if (!sePuedeGuardar && !guardando && loQueFalta != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = loQueFalta,
                        style = Movi.textos.apoyo,
                        color = if (propia != null) Movi.colores.sale else Movi.colores.textoMedio,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
