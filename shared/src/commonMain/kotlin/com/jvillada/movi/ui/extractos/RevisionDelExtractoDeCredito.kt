package com.jvillada.movi.ui.extractos

import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeCredito
import com.jvillada.movi.shared.model.hayAlgoQueActualizar
import com.jvillada.movi.shared.model.renglonesDelExtractoDeCredito
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

const val TAG_PROPUESTA_DEL_EXTRACTO_DE_CREDITO: String = "propuesta-del-extracto-de-credito"
const val ACTUALIZAR_EL_CREDITO: String = "Actualizar el crédito"

/** Lo que se le explica antes de tocar «Actualizar»: qué cambia y qué no cuenta. */
internal const val QUE_HACE_ACTUALIZAR: String =
    "Movi guarda en las condiciones del crédito el seguro, la tasa y el interés de esta cuota, y " +
        "anota la diferencia del saldo como un ajuste a la fecha del corte. El ajuste no cuenta " +
        "como gasto ni como ingreso. Nada cambia hasta que lo confirmes."

/**
 * **La revisión del extracto de un crédito**: en vez de la lista de movimientos (un extracto de
 * crédito no trae), lo que dice el papel al lado de lo que dice Movi, y «Actualizar el crédito».
 * Sin el crédito en Movi lo dice, y no ofrece nada que actualizar.
 */
@Composable
internal fun RevisionDelExtractoDeCredito(propuesta: PropuestaDelExtractoDeCredito, titulo: String) {
    val goBack = LocalGoBack.current
    val alcance = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    var hecho by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sePuede = hayAlgoQueActualizar(propuesta) && !trabajando && !hecho

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = titulo,
            leading = HeaderLeading.Back(fallback = Screen.Documentos),
            subtitle = "Extracto de un crédito",
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Espacio.m, vertical = Espacio.s),
        ) {
            Superficie(
                modifier = Modifier.fillMaxWidth().testTag(TAG_PROPUESTA_DEL_EXTRACTO_DE_CREDITO),
            ) {
                Text(
                    propuesta.cuentaNombre ?: "Crédito ·${propuesta.resumen.numero?.takeLast(4) ?: "sin número"}",
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Espacio.s))
                Text("El extracto dice", style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xs))
                renglonesDelExtractoDeCredito(propuesta).forEach { renglon ->
                    Text(
                        conCifrasOcultas("· $renglon"),
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = Espacio.xxs),
                    )
                }
                Spacer(Modifier.height(Espacio.s))
                Text(
                    when {
                        propuesta.cuentaId == null ->
                            "No encontramos este crédito en Movi. Revisa que el nombre del crédito lleve los últimos " +
                                "cuatro números de la obligación."
                        hecho -> "Listo: el crédito quedó con lo que dice el extracto."
                        !hayAlgoQueActualizar(propuesta) -> "Todo coincide con Movi: no hay nada que actualizar."
                        else -> QUE_HACE_ACTUALIZAR
                    },
                    style = Movi.textos.apoyo,
                    color = if (hecho) Movi.colores.entra else Movi.colores.textoMedio,
                )
                error?.let {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m).padding(bottom = Espacio.l),
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Boton(if (hecho) "Volver" else "Ahora no", principal = false, habilitado = !trabajando, modifier = Modifier.weight(1f)) {
                goBack(Screen.Credits)
            }
            if (!hecho && propuesta.cuentaId != null) {
                Boton(
                    if (trabajando) "Actualizando…" else ACTUALIZAR_EL_CREDITO,
                    principal = true,
                    habilitado = sePuede,
                    modifier = Modifier.weight(1.4f),
                ) {
                    val cuenta = propuesta.cuentaId ?: return@Boton
                    trabajando = true
                    error = null
                    alcance.launch {
                        intentar { Repositories.wallets.aceptarElExtractoDelCredito(cuenta, propuesta.resumen) }
                            .onSuccess { hecho = true }
                            .onFailure { error = it.toUserMessage() }
                        trabajando = false
                    }
                }
            }
        }
    }
}

/** Ola R4: el [Boton][com.jvillada.movi.ui.components.ds.Boton] del sistema, principal o secundario. */
@Composable
private fun Boton(texto: String, principal: Boolean, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    com.jvillada.movi.ui.components.ds.Boton(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        variante = if (principal) com.jvillada.movi.ui.components.ds.VarianteDeBoton.Principal else com.jvillada.movi.ui.components.ds.VarianteDeBoton.Secundario,
        habilitado = habilitado,
    )
}
