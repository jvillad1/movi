package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.ANULAR_DESHACE_LAS_DOS_MITADES
import com.jvillada.movi.shared.model.BUSCANDO_LA_OTRA_MITAD
import com.jvillada.movi.shared.model.NO_SE_PUDO_LEER_LA_OTRA_MITAD
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.loQuePasaAlAnular
import com.jvillada.movi.shared.model.textoDeLoQuePasa
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Confirmar la anulación de un movimiento — la única acción de la app que no tiene deshacer.
 *
 * ## Por qué la hoja va a buscar la otra mitad
 *
 * Anular una pata cascadea a la otra por `transferId`, y desde que la deuda baja solo por lo que
 * abona a capital ([com.jvillada.movi.shared.model.DesgloseDeCuota]) **las dos mitades dejaron de
 * valer lo mismo**. Los saldos quedaban bien —cada pata se revierte por su propio monto— pero la
 * hoja mostraba el monto de la pata que el dueño tocó y nada más: anular la cuota del vehículo
 * desde la cuenta de ahorros decía «$4.215.223» mientras desaparecían $4.215.223 de la cuenta **y**
 * $1.733.905 de la deuda, sin que la pantalla nombrara nunca el segundo número.
 *
 * Qué se dice lo decide [loQuePasaAlAnular], en `:core`, por el mismo motivo que el resto de las
 * reglas de plata de esta ola: ya son dos las pantallas que cuentan esta historia (el renglón de
 * Movimientos y esta hoja) y una regla sobre plata duplicada en dos pantallas ya sobrevivió tres
 * rondas de arreglos en este proyecto. Acá solo se le pone el nombre de la cuenta y el monto
 * formateado.
 *
 * La hermana se busca en el repositorio y **no se pide como parámetro**: de las dos pantallas que
 * abren esta hoja, el detalle de la cuenta solo tiene los movimientos de *su* cuenta, y la hermana
 * de una cuota vive justo en la otra. Nunca se inventa una cifra: mientras la lectura no vuelva, o
 * si falla, [loQuePasaAlAnular] devuelve vacío.
 *
 * ## Mientras esa lectura tarda, la hoja lo dice
 *
 * Callar durante la espera era defendible mientras se creyera que duraba un parpadeo. Mirado a ojo
 * en la web, tarda del orden de segundos — y en esa ventana la hoja se ve **entera** mostrando una
 * sola cifra, que es el defecto de arriba otra vez, ahora por tiempo en vez de por contenido. Por
 * eso hay [LaOtraMitad] con cuatro estados y no un `FinancialEvent?`: sin distinguir «todavía no
 * volvió» de «falló», sin red la hoja se quedaba diciendo «buscando» para siempre.
 *
 * El botón de anular **sigue habilitado** en los dos casos. No saber cuánto no cambia qué va a
 * pasar: el server cascadea a las dos patas por `transferId` dentro de la misma transacción.
 */
@Composable
fun VoidEventSheet(
    event: FinancialEvent,
    /**
     * Las cuentas del dueño, solo para poder nombrarlas. Vacía = «todavía no llegaron»: se dicen
     * los roles («Tu cuenta», «La deuda») en vez de inventar un nombre, mismo criterio que el
     * subtítulo del renglón de Movimientos.
     */
    cuentas: List<Account>,
    onDismiss: () -> Unit,
    onVoided: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var voiding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // La otra mitad del par. Un movimiento suelto —la enorme mayoría— ni siquiera pregunta:
    // `transferId` en null arranca en [LaOtraMitad.NoLaTiene] y corta antes de tocar el repositorio.
    var otraMitad by remember(event.id) {
        mutableStateOf<LaOtraMitad>(
            if (event.transferId == null) LaOtraMitad.NoLaTiene else LaOtraMitad.Buscando,
        )
    }
    LaunchedEffect(event.id) {
        val transferId = event.transferId ?: return@LaunchedEffect
        otraMitad = try {
            val patas = Repositories.wallets.getEvents()
            LaOtraMitad.Llego(patas.firstOrNull { it.transferId == transferId && it.id != event.id })
        } catch (e: CancellationException) {
            // La hoja se cerró en el medio. Se propaga en vez de tragarse —un `runCatching` acá
            // convertiría un cierre normal en «no se pudo leer», que es un error inventado.
            throw e
        } catch (e: Exception) {
            LaOtraMitad.NoSePudo
        }
    }

    fun doVoid() {
        if (voiding) return
        voiding = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.voidEvent(event.id, reason.trim().ifBlank { null })
                onVoided()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUserMessage()
                voiding = false
            }
        }
    }

    val isIncome = event.type == TransactionType.INCOME
    val signedAmount = "${if (isIncome) "+" else "−"}${formatMoney(event.amount, event.currency)}"
    val nombres = remember(cuentas) { cuentas.associate { it.id to it.name } }
    val loQuePasa = (otraMitad as? LaOtraMitad.Llego)?.let { loQuePasaAlAnular(event, it.hermana) }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !voiding, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            // F37: manija + X para cerrar, mismo componente en las 8 hojas de la app.
            SheetHandleWithClose(onClose = onDismiss, enabled = !voiding)
            // El contenido de la hoja se desplaza.
            //
            // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
            // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
            // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
            // propia hoja. Sin manera de llegar a él.
            //
            // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
            // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
            // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {

                // Event summary card
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.description,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinText,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${event.category} · ${event.source.name}",
                                fontSize = 11.sp,
                                color = MinTextMute,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        MonoText(
                            text = signedAmount,
                            fontSize = 14f,
                            color = if (isIncome) MinIncome else MinText,
                        )
                    }
                }

                // **Las dos cifras, cuando son dos.**
                //
                // La de arriba es la de la pata que el dueño tocó, y hasta acá era todo lo que la
                // hoja decía. En un par simétrico eso alcanza y esta sección no aparece: un aviso
                // de más sobre algo que no cambia enseña a ignorarlos.
                if (loQuePasa.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "AL ANULAR",
                        fontSize = 11.sp,
                        color = MinTextMute,
                        letterSpacing = 0.4.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    loQuePasa.forEach { efecto ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Ícono y no un «✓» de texto: en wasm la fuente del canvas no trae ese
                            // glifo y sale como ▯ — el mismo problema que ya obligó a reemplazar el
                            // «›» y la flecha del subtítulo de un traspaso.
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MinTextMute,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = textoDeLoQuePasa(
                                    efecto,
                                    nombres[efecto.accountId],
                                    formatMoney(efecto.monto, efecto.currency),
                                ),
                                fontSize = 13.5.sp,
                                color = MinText,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // Sin esta frase, dos números distintos sobre un solo pago se leen como un
                    // error de la app.
                    Text(
                        text = ANULAR_DESHACE_LAS_DOS_MITADES,
                        fontSize = 12.5.sp,
                        color = MinTextMute,
                        lineHeight = 17.sp,
                    )
                }

                // **Y mientras la otra mitad no esté, se dice.** La lectura tarda del orden de
                // segundos (medido a ojo en la web), y callar durante esa ventana dejaba la hoja
                // viéndose entera con una sola cifra — el mismo defecto de siempre, ahora por
                // tiempo en vez de por contenido. El botón sigue habilitado en los dos casos: el
                // server cascadea a las dos patas pase lo que pase acá.
                val avisoDeLaEspera = when (otraMitad) {
                    LaOtraMitad.Buscando -> BUSCANDO_LA_OTRA_MITAD
                    LaOtraMitad.NoSePudo -> NO_SE_PUDO_LEER_LA_OTRA_MITAD
                    else -> null
                }
                if (avisoDeLaEspera != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = avisoDeLaEspera,
                        fontSize = 12.5.sp,
                        color = MinTextMute,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Reason label
                Text(
                    text = "MOTIVO (OPCIONAL)",
                    fontSize = 11.sp,
                    color = MinTextMute,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))

                // Reason input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinSurfaceContainerLow)
                        .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    BasicTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        cursorBrush = SolidColor(MinText),
                        textStyle = TextStyle(color = MinText, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (reason.isEmpty()) {
                                Text("Ej: Movimiento duplicado", fontSize = 14.sp, color = MinTextMute)
                            }
                            inner()
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))

                // CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!voiding) MinExpenseContainer else MinSurfaceContainerLow)
                        .clickable(enabled = !voiding) { doVoid() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (voiding) "Anulando…" else "Anular movimiento",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!voiding) MinExpense else MinTextFaint,
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
            // El error de anular, **fuera del área que se desplaza**. Estaba pintado adentro, entre
            // el motivo y el botón: con la sección de «al anular» y el teclado abierto en un
            // teléfono chico, aparecía donde el dueño no está mirando — o sea que, desde su lado,
            // la anulación fallaba en silencio. Ver [BarraDeError].
            BarraDeError(error)
        }
    }
}

/**
 * **En qué punto está la búsqueda de la otra mitad del par.**
 *
 * Cuatro estados y no un `FinancialEvent?`, porque con un nullable dos situaciones muy distintas se
 * escribían igual: «todavía no volvió la lectura» y «la lectura falló» eran las dos `null`. La hoja
 * no podía distinguirlas, así que sin red se quedaba diciendo «buscando» para siempre.
 *
 * [Llego] con `hermana` nula es un quinto caso real y distinto de los otros: la lectura volvió y
 * este `transferId` no tiene otra pata en la base. No hay nada que aclarar y no se aclara nada.
 */
private sealed interface LaOtraMitad {
    /** Un movimiento suelto: no hay par, no se pregunta nada. La enorme mayoría. */
    data object NoLaTiene : LaOtraMitad

    /** La lectura está en camino. */
    data object Buscando : LaOtraMitad

    /** Volvió. `hermana` nula = este par no tiene otra pata guardada. */
    data class Llego(val hermana: FinancialEvent?) : LaOtraMitad

    /** La lectura falló — sin red, o el server dijo que no. */
    data object NoSePudo : LaOtraMitad
}
