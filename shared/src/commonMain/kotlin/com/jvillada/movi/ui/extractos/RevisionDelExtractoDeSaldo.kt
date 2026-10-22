package com.jvillada.movi.ui.extractos

import com.jvillada.movi.ui.components.conCifrasOcultas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.CuentaParaElSaldo
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeSaldo
import com.jvillada.movi.shared.model.conPuntosDeMiles
import com.jvillada.movi.shared.model.corteLegible
import com.jvillada.movi.shared.model.fraseDelExtractoDeSaldo
import com.jvillada.movi.shared.model.hayQueActualizarElSaldo
import com.jvillada.movi.shared.model.nombreDelExtractoDeSaldo
import com.jvillada.movi.shared.model.renglonesDeLosContratos
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

const val TAG_PROPUESTA_DEL_EXTRACTO_DE_SALDO: String = "propuesta-del-extracto-de-saldo"
const val ACTUALIZAR_EL_SALDO: String = "Actualizar el saldo"

/** Lo que se le explica antes de tocar «Actualizar el saldo»: qué se escribe y qué no cuenta. */
internal fun queHaceActualizarElSaldo(propuesta: PropuestaDelExtractoDeSaldo): String =
    "Movi anota la diferencia como un ajuste de saldo al ${corteLegible(propuesta.resumen)}. " +
        (if (propuesta.resumen.de == ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO) "En el fondo, esa diferencia es lo que rindió, menos las comisiones. " else "") +
        "El ajuste no cuenta como gasto ni como ingreso. Nada cambia hasta que lo confirmes."

/**
 * **La revisión del extracto que solo dice cuánto hay** (Skandia, la AFC de Davibank): en vez de la
 * lista de movimientos, «El extracto dice $X al 31 de agosto; Movi tiene $Y» y «Actualizar el
 * saldo». Si el extracto no nombra una cuenta sin duda, el dueño la elige de la lista; cada opción
 * ya trae lo que Movi tiene al corte.
 *
 * Los movimientos de la AFC no se muestran: el lector no los saca (ver `ExtractoAfcDavibank.kt`).
 */
@Composable
internal fun RevisionDelExtractoDeSaldo(propuesta: PropuestaDelExtractoDeSaldo, titulo: String) {
    val goBack = LocalGoBack.current
    val alcance = rememberCoroutineScope()
    var elegidaId by remember { mutableStateOf(propuesta.cuentaId) }
    var trabajando by remember { mutableStateOf(false) }
    var hecho by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val resumen = propuesta.resumen
    val elegida: CuentaParaElSaldo? = elegidaId?.let { id -> propuesta.opciones.firstOrNull { it.id == id } }
    val sePuede = hayQueActualizarElSaldo(resumen, elegida) && !trabajando && !hecho

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = titulo,
            leading = HeaderLeading.Back(fallback = Screen.Documentos),
            subtitle = "Extracto de saldo",
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MinCard(
                modifier = Modifier.fillMaxWidth().testTag(TAG_PROPUESTA_DEL_EXTRACTO_DE_SALDO),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(18.dp),
            ) {
                Text(
                    nombreDelExtractoDeSaldo(resumen),
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(10.dp))
                Text("EL EXTRACTO DICE", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(6.dp))
                Text(
                    conCifrasOcultas(fraseDelExtractoDeSaldo(resumen, elegida)),
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                    lineHeight = 20.sp,
                )
                renglonesDeLosContratos(resumen).forEach { renglon ->
                    Text(
                        conCifrasOcultas("· $renglon"),
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))
                when {
                    propuesta.opciones.isEmpty() -> Unit
                    propuesta.cuentaId != null && elegida != null -> {
                        Text("EN TU CUENTA", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                        Spacer(Modifier.height(4.dp))
                        Text(elegida.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    }
                    else -> {
                        Text("¿EN QUÉ CUENTA?", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                        Spacer(Modifier.height(4.dp))
                        propuesta.opciones.forEach { opcion ->
                            OpcionDeCuenta(
                                opcion = opcion,
                                elegida = opcion.id == elegidaId,
                                habilitada = !trabajando && !hecho,
                            ) { elegidaId = opcion.id; error = null }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        propuesta.opciones.isEmpty() ->
                            "No tienes una cuenta de dinero o de inversión en pesos para este saldo. Créala y vuelve a " +
                                "subir el extracto."
                        hecho -> "Listo: la cuenta quedó con el saldo del extracto."
                        elegida == null -> "Elige la cuenta de este extracto."
                        elegida.cuadradaDespues ->
                            "Esta cuenta ya se cuadró después del corte del extracto. Para actualizarla, sube un extracto más nuevo."
                        !hayQueActualizarElSaldo(resumen, elegida) -> "Movi ya tiene ese saldo: no hay nada que actualizar."
                        else -> queHaceActualizarElSaldo(propuesta)
                    },
                    style = Movi.textos.apoyo,
                    color = if (hecho) Movi.colores.entra else Movi.colores.textoMedio,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotonDelSaldo(if (hecho) "Volver" else "Ahora no", principal = false, habilitado = !trabajando, modifier = Modifier.weight(1f)) {
                goBack(Screen.Documentos)
            }
            if (!hecho && propuesta.opciones.isNotEmpty()) {
                BotonDelSaldo(
                    if (trabajando) "Actualizando…" else ACTUALIZAR_EL_SALDO,
                    principal = true,
                    habilitado = sePuede,
                    modifier = Modifier.weight(1.4f),
                ) {
                    val cuenta = elegida ?: return@BotonDelSaldo
                    if (!sePuede) return@BotonDelSaldo
                    trabajando = true
                    error = null
                    alcance.launch {
                        intentar { Repositories.wallets.actualizarElSaldoConElExtracto(cuenta.id, resumen) }
                            .onSuccess { hecho = true }
                            .onFailure { error = it.toUserMessage() }
                        trabajando = false
                    }
                }
            }
        }
    }
}

/** Una cuenta para elegir: su nombre y lo que Movi tiene al corte. */
@Composable
private fun OpcionDeCuenta(opcion: CuentaParaElSaldo, elegida: Boolean, habilitada: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = habilitada, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (elegida) Movi.colores.texto else Movi.colores.borde, CircleShape)
                .padding(4.dp)
                .then(if (elegida) Modifier.clip(CircleShape).background(Movi.colores.texto) else Modifier),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(opcion.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 1)
            Text(
                conCifrasOcultas("Movi tiene $" + conPuntosDeMiles(opcion.saldoEnMovi)),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
    }
}

@Composable
private fun BotonDelSaldo(texto: String, principal: Boolean, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (principal) Modifier.background(if (habilitado) Movi.colores.texto else Movi.colores.textoApagado)
                else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(14.dp)),
            )
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            color = if (principal) Movi.colores.fondo else Movi.colores.texto,
            style = Movi.textos.cuerpo,
            maxLines = 1,
        )
    }
}
