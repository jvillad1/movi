package com.jvillada.movi.ui.accounts

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
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
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

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
                modifier = Modifier.padding(top = Espacio.xxs, bottom = Espacio.s),
            )
            Text(
                text = "La plata sigue en ${account.name}, pero deja de contar en «Tu plata» y en " +
                    "«Te quedan». No se mueve nada en tu banco.",
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Normal,
                color = Movi.colores.textoMedio,
            )

            if (confirmandoQuitar && existente != null) {
                Spacer(Modifier.height(Espacio.l))
                Text(
                    text = "¿Quitar «${existente.proposito}»? La plata vuelve a «Tu plata».",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Espacio.l))
                PieDeHoja(
                    texto = if (trabajando) "Quitando…" else "Quitar",
                    onClick = { quitar() },
                    habilitado = !trabajando,
                    variante = VarianteDeBoton.Peligro,
                    error = error,
                    secundaria = "Cancelar",
                    secundariaHabilitada = !trabajando,
                    onSecundaria = {
                        confirmandoQuitar = false
                        error = null
                    },
                )
                Spacer(Modifier.height(Espacio.xl))
                return@Column
            }

            Spacer(Modifier.height(Espacio.m))
            Campo(
                proposito, { proposito = it.take(MAX_PROPOSITO_APARTADO) },
                rotulo = "¿Para qué?",
                guia = "Colegio nov-dic",
                teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                habilitado = !trabajando,
            )

            Spacer(Modifier.height(Espacio.m))
            CampoDeMonto(monto, { monto = it }, rotulo = "Monto")

            Spacer(Modifier.height(Espacio.m))
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
                        .padding(top = Espacio.xs)
                        .clickable(enabled = !trabajando, role = Role.Button) { categoria = "" },
                )
            }

            Spacer(Modifier.height(Espacio.l))
            PieDeHoja(
                texto = if (trabajando) "Guardando…" else "Guardar",
                onClick = { guardar() },
                habilitado = puedeGuardar,
                error = error,
                secundaria = if (existente != null) "Quitar" else "Cancelar",
                secundariaHabilitada = !trabajando,
                onSecundaria = {
                    if (existente != null) {
                        confirmandoQuitar = true
                        error = null
                    } else {
                        onDismiss()
                    }
                },
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
