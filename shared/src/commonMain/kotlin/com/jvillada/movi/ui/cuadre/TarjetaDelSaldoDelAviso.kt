package com.jvillada.movi.ui.cuadre

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

const val TAG_TARJETA_DEL_SALDO_DEL_AVISO: String = "tarjeta-del-saldo-del-aviso"
const val AJUSTAR_POR_EL_AVISO: String = "Ajustar"
const val NO_AJUSTAR_POR_EL_AVISO: String = "No ajustar"

/**
 * **Lo que dijo el aviso del banco, contra lo que dice Movi**, con dos salidas: «Ajustar» (escribe
 * el ajuste de siempre, fechado en el instante del aviso) y «No ajustar» (no toca nada y no vuelve
 * a proponerlo). Nada se escribe sin que el dueño toque «Ajustar». [onResuelta] avisa que la
 * tarjeta se puede quitar ya, sin esperar la relectura.
 */
@Composable
internal fun TarjetaDelSaldoDelAviso(
    propuesta: PropuestaDeCuadrePorAviso,
    hoy: LocalDate,
    onResuelta: () -> Unit,
) {
    var trabajando by remember(propuesta.avisoId) { mutableStateOf(false) }
    var error by remember(propuesta.avisoId) { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    fun hacer(accion: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        alcance.launch {
            intentar { accion() }
                .onSuccess { onResuelta() }
                .onFailure { error = it.toUserMessage() }
            trabajando = false
        }
    }

    // Ola R4: es una propuesta de Movi, así que va como [TarjetaDePropuesta] —el tinte de la marca,
    // el ícono, la acción principal y la secundaria—, como toda propuesta de la app.
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_SALDO_DEL_AVISO)) {
        TarjetaDePropuesta(
            icono = Icons.Rounded.AccountBalance,
            titulo = propuesta.cuentaNombre,
            cuerpo = "${textoDelMomentoDelAviso(propuesta.momento, hoy)}. ${textoDeLaPropuestaPorAviso(propuesta)}. " +
                "Movi no ajusta nada hasta que lo confirmes. El ajuste no cuenta como gasto ni como ingreso.",
            accion = if (trabajando) "Ajustando…" else AJUSTAR_POR_EL_AVISO,
            onAccion = { hacer { Repositories.wallets.ajustarPorElAviso(propuesta.avisoId) } },
            accionSecundaria = NO_AJUSTAR_POR_EL_AVISO,
            onAccionSecundaria = { hacer { Repositories.wallets.descartarCuadrePorAviso(propuesta.avisoId) } },
        )
        error?.let {
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(top = Espacio.xs))
        }
    }
}
