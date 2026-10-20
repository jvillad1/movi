package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.MAX_PROPOSITO_APARTADO
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.valorEnPesosDe
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CategoryField
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.dashboard.apartadoEfectivoDe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Lo que se dice cuando apartar (o quitar) no llegó al server por falta de red. */
const val APARTAR_SIN_CONEXION: String = "Para apartar plata necesitas conexión"

/**
 * **Lo libre de una cuenta**: su saldo menos lo apartado que de verdad pesa ([apartadoEfectivoDe]),
 * nunca por debajo de cero. Es el renglón «Libre» del detalle de la cuenta.
 */
fun libreDeLaCuenta(account: Account, apartados: List<Apartado>): Long =
    (valorEnPesosDe(account) - apartadoEfectivoDe(account, apartados)).coerceAtLeast(0L)

/**
 * El error de guardar o quitar un apartado, dicho para esta hoja. Se decide por el TIPO, no por el
 * texto: un [ApiException] es que el server contestó (su motivo, o el de siempre para su código);
 * cualquier otra cosa es que el pedido no llegó —sin red, el server caído, un tiempo agotado—, y los
 * apartados no se guardan en el aparato (ver el diseño), así que se dice que hace falta conexión.
 */
fun mensajeAlApartar(e: Throwable): String =
    if (e is ApiException) e.toUserMessage() else APARTAR_SIN_CONEXION

/**
 * **Apartar plata** de [account]: crear un apartado, o editar / quitar [existente].
 *
 * El id de uno nuevo se elige **una vez, al abrir la hoja**, y viaja en cada intento: el POST es
 * idempotente por id, así que reintentar tras un corte no deja dos apartados iguales.
 *
 * «Quitar» pregunta adentro de la hoja (sin diálogos del sistema), y un error —sin red o un rechazo
 * del server— se dice abajo sin perder lo escrito.
 */
@Composable
fun ApartadoSheet(
    account: Account,
    existente: Apartado?,
    onDismiss: () -> Unit,
    onGuardado: (Apartado) -> Unit,
    onQuitado: (String) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val id = remember { existente?.id ?: newId("apt") }
    var proposito by remember { mutableStateOf(existente?.proposito.orEmpty()) }
    var monto by remember { mutableStateOf(existente?.monto) }
    var categoria by remember { mutableStateOf(existente?.categoria.orEmpty()) }
    var trabajando by remember { mutableStateOf(false) }
    var confirmandoQuitar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val propositoLimpio = proposito.trim()
    val montoValido = (monto ?: 0L) > 0L
    val puedeGuardar = propositoLimpio.isNotEmpty() && montoValido && !trabajando

    fun guardar() {
        if (!puedeGuardar) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                val guardado = Repositories.wallets.guardarApartado(
                    Apartado(
                        id = id,
                        accountId = account.id,
                        proposito = propositoLimpio,
                        monto = monto ?: 0L,
                        categoria = categoria.trim().ifEmpty { null },
                        // Uno nuevo cuenta gastos desde que se crea (el server pone la hora); uno
                        // editado conserva su arranque.
                        desde = existente?.desde ?: 0L,
                    ),
                )
                onGuardado(guardado)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeAlApartar(e)
                trabajando = false
            }
        }
    }

    fun quitar() {
        val actual = existente ?: return
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.quitarApartado(actual.id)
                onQuitado(actual.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeAlApartar(e)
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            Text(
                text = if (existente == null) "Apartar plata" else "Apartado",
                style = Movi.textos.titulo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Text(
                text = "La plata sigue en ${account.name}, pero deja de contar en «Tu plata» y en " +
                    "«Te quedan». No se mueve nada en tu banco.",
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Normal,
                color = Movi.colores.textoMedio,
            )

            if (confirmandoQuitar && existente != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "¿Quitar «${existente.proposito}»? La plata vuelve a «Tu plata».",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                )
                MensajeDeError(error)
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonDeHoja("Cancelar", habilitado = !trabajando, destacado = false, modifier = Modifier.weight(1f)) {
                        confirmandoQuitar = false
                        error = null
                    }
                    BotonDeHoja(
                        if (trabajando) "Quitando…" else "Quitar",
                        habilitado = !trabajando,
                        destacado = true,
                        colorDestacado = Movi.colores.sale,
                        modifier = Modifier.weight(1.4f),
                    ) { quitar() }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            RotuloDeLaHoja("¿Para qué?")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Movi.colores.tarjeta)
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (proposito.isEmpty()) {
                    Text("Colegio nov-dic", style = Movi.textos.cuerpo, color = Movi.colores.textoApagado, maxLines = 1)
                }
                val campo = rememberCampoConSeleccion(proposito) { proposito = it.take(MAX_PROPOSITO_APARTADO) }
                BasicTextField(
                    value = campo.valor,
                    onValueChange = campo::alCambiar,
                    textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                    cursorBrush = SolidColor(Movi.colores.texto),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    enabled = !trabajando,
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                )
            }

            Spacer(Modifier.height(16.dp))
            MoneyField(
                value = monto,
                onValueChange = { monto = it },
                label = "Monto",
            )

            Spacer(Modifier.height(16.dp))
            CategoryField(
                value = categoria,
                onValueChange = { categoria = it },
                type = TransactionType.EXPENSE,
                usedCategories = UsedCategoriesCache.used,
                prefs = UsedCategoriesCache.prefs,
                usos = UsedCategoriesCache.usosRecientes,
                label = "Se descuenta con los gastos de",
                placeholder = "Ninguna",
            )
            Text(
                text = if (categoria.isBlank()) {
                    "Opcional. Si eliges una, cada gasto de esa categoría desde ${account.name} lo va bajando solo."
                } else {
                    "Cada gasto de $categoria desde ${account.name} lo va bajando solo."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            if (categoria.isNotBlank()) {
                Text(
                    text = "Sin categoría",
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.marca,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(enabled = !trabajando, role = Role.Button) { categoria = "" },
                )
            }

            MensajeDeError(error)
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existente != null) {
                    BotonDeHoja("Quitar", habilitado = !trabajando, destacado = false, modifier = Modifier.weight(1f)) {
                        confirmandoQuitar = true
                        error = null
                    }
                } else {
                    BotonDeHoja("Cancelar", habilitado = !trabajando, destacado = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                }
                BotonDeHoja(
                    if (trabajando) "Guardando…" else "Guardar",
                    habilitado = puedeGuardar,
                    destacado = true,
                    modifier = Modifier.weight(1.4f),
                ) { guardar() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RotuloDeLaHoja(texto: String) {
    Text(texto, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MensajeDeError(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(12.dp))
    Text(text = error, style = Movi.textos.apoyo, color = Movi.colores.sale)
}

@Composable
private fun BotonDeHoja(
    texto: String,
    habilitado: Boolean,
    destacado: Boolean,
    modifier: Modifier = Modifier,
    colorDestacado: androidx.compose.ui.graphics.Color = Movi.colores.marca,
    onClick: () -> Unit,
) {
    val activo = destacado && habilitado
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (activo) colorDestacado.copy(alpha = 0.16f) else Movi.colores.tarjeta)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = Movi.textos.cuerpo,
            fontWeight = FontWeight.Medium,
            color = when {
                activo -> colorDestacado
                habilitado -> Movi.colores.texto
                else -> Movi.colores.textoApagado
            },
        )
    }
}
