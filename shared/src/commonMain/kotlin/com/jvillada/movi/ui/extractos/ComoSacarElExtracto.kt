package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.GuiaDelExtracto
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.guiaDelExtractoParaAlguno
import com.jvillada.movi.shared.model.NOTA_DE_LA_GUIA_SIN_VERIFICAR
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.formatoDelExtracto
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.rangoIdealDelExtracto
import com.jvillada.movi.shared.model.tituloDeLaGuiaDelExtracto
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/** La hoja de la guía. */
const val TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO: String = "hoja-de-como-sacar-el-extracto"

/** La fila que la abre, en el detalle de una cuenta y en la revisión de un extracto. */
const val TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO: String = "fila-de-como-sacar-el-extracto"

/** El renglón del rango ideal, dentro de la hoja. */
const val TAG_RANGO_IDEAL_DEL_EXTRACTO: String = "rango-ideal-del-extracto"

/**
 * **La guía de una cuenta de Movi** (vuelta 107), para la fila «Sube el extracto de este mes» de su
 * detalle: solo una cuenta de ahorros, una corriente o una tarjeta de crédito (lo que el banco lista en
 * «Movimientos»; un crédito tiene otro papel y un bien ninguno), con el banco de la tarjeta
 * ([bancoDeLaTarjeta], `CardTerms.bank`) antes que el nombre de la cuenta. Sin guía, `null`. Pura.
 */
fun guiaDelExtractoDeLaCuenta(cuenta: Account, bancoDeLaTarjeta: String? = null): GuiaDelExtracto? {
    if (cuenta.esBien) return null
    if (cuenta.type != AccountType.SAVINGS && cuenta.type != AccountType.CHECKING && cuenta.type != AccountType.CREDIT_CARD) return null
    return guiaDelExtractoParaAlguno(bancoDeLaTarjeta, cuenta.name)
}

/**
 * **Desde cuándo bajar los movimientos** (vuelta 107): el inicio del período del dueño (su «corte», el
 * 23 de octubre si su período arrancó ese día) o el corte de la tarjeta, el más viejo de los dos, para
 * que el extracto cubra lo que Movi no pudo ver desde ahí. Sin ninguno de los dos, `null`. Pura.
 */
fun desdeDelExtracto(ajustes: PeriodSettings?, corteDeLaTarjeta: LocalDate?, ahoraMillis: Long): LocalDate? {
    val delPeriodo = ajustes?.let { inicioDelPeriodo(periodoActual(ahoraMillis, it), it) }
    return listOfNotNull(delPeriodo, corteDeLaTarjeta).minOrNull()
}

/**
 * **«Cómo sacar tu extracto de Bancolombia»**: los pasos, el formato, hasta dónde deja bajar y el rango
 * ideal ([rangoIdealDelExtracto], solo si el banco deja elegir fechas). La guía es dato de `:core`
 * ([GuiaDelExtracto]); esta hoja solo la pinta. Si los pasos no se pudieron comprobar en el banco (Nu),
 * lo dice. No escribe nada.
 *
 * El rango sale del perfil (el inicio del período) y de [corteDeLaTarjeta]; mientras el perfil no llega
 * el renglón dice el rango sin la fecha, con el mismo alto: nada salta.
 */
@Composable
fun HojaDeComoSacarElExtracto(
    guia: GuiaDelExtracto,
    esTarjeta: Boolean,
    onDismiss: () -> Unit,
    corteDeLaTarjeta: LocalDate? = null,
) {
    var ajustes by remember { mutableStateOf<PeriodSettings?>(null) }
    if (guia.conRango) {
        LaunchedEffect(Unit) {
            ajustes = try {
                Repositories.wallets.getUserProfile().ajustesDelPeriodo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Sin perfil, el rango del período por defecto (corte el 1): mejor una fecha aproximada
                // que ninguna. Throwable: en la web un fetch caído llega como `kotlin.Error`.
                PeriodSettings()
            }
        }
    }
    val hoy = hoyEnAppZone()
    val desde = desdeDelExtracto(ajustes, corteDeLaTarjeta, Clock.System.now().toEpochMilliseconds())
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            EncabezadoDeHoja(tituloDeLaGuiaDelExtracto(guia))
            guia.pasosPara(esTarjeta).forEachIndexed { i, paso ->
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Text("${i + 1}.", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    Text(paso, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                }
            }
            Spacer(Modifier.height(Espacio.xxs))
            Text(formatoDelExtracto(guia), style = Movi.textos.cuerpo, color = Movi.colores.texto)
            if (guia.conRango) {
                Text(
                    if (desde != null) rangoIdealDelExtracto(desde, hoy) else "Rango ideal: desde tu último corte hasta hoy.",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                    modifier = Modifier.testTag(TAG_RANGO_IDEAL_DEL_EXTRACTO),
                )
            }
            guia.limite?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) }
            if (!guia.verificada) Text(NOTA_DE_LA_GUIA_SIN_VERIFICAR, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/**
 * La fila que abre la guía: «Sube el extracto de este mes · Cómo sacarlo de Bancolombia» en el detalle
 * de una cuenta, o el título que quiera quien la usa ([titulo]).
 */
@Composable
fun FilaDeComoSacarElExtracto(
    guia: GuiaDelExtracto,
    titulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Fila(
        titulo = titulo,
        subtitulo = "Cómo sacarlo de ${guia.banco}",
        inicio = { IconoEnCirculo(Icons.Rounded.Download, tono = TonoDeIcono.Neutro) },
        onClick = onClick,
        modifier = modifier.testTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO),
    )
}
