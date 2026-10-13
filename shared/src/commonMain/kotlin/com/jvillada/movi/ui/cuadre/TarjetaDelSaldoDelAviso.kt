package com.jvillada.movi.ui.cuadre

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
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

    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DEL_SALDO_DEL_AVISO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Text(
            propuesta.cuentaNombre,
            style = Movi.textos.titulo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.texto,
        )
        Text(
            textoDelMomentoDelAviso(propuesta.momento, hoy),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            textoDeLaPropuestaPorAviso(propuesta),
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Movi no ajusta nada hasta que lo confirmes. El ajuste no cuenta como gasto ni como ingreso.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pastilla(if (trabajando) "Ajustando…" else AJUSTAR_POR_EL_AVISO, habilitada = !trabajando, principal = true) {
                hacer { Repositories.wallets.ajustarPorElAviso(propuesta.avisoId) }
            }
            Pastilla(NO_AJUSTAR_POR_EL_AVISO, habilitada = !trabajando) {
                hacer { Repositories.wallets.descartarCuadrePorAviso(propuesta.avisoId) }
            }
        }
    }
}

@Composable
private fun Pastilla(texto: String, habilitada: Boolean, principal: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (principal) Modifier.background(if (habilitada) Movi.colores.texto else Movi.colores.textoApagado)
                else Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)),
            )
            .clickable(enabled = habilitada, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            texto,
            style = Movi.textos.apoyo,
            color = if (principal) Movi.colores.fondo else Movi.colores.texto,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
