package com.jvillada.movi.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.motivoDeLaHuella
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.platform.HuellaDelAparato
import com.jvillada.movi.theme.Movi
import kotlinx.datetime.Clock

/**
 * **Avisa cuando la app vuelve del segundo plano, con cuándo se fue.** [alVolver] recibe
 * `(fueraDesde, ahora)` en epoch ms. El primer arranque no cuenta: no hubo un «se fue» antes.
 *
 * Con el ciclo de vida de la ventana (`ON_STOP` → `ON_START`): apagar la pantalla, ir al inicio del
 * teléfono o cambiar de app. Un diálogo del sistema encima —el de la propia huella, un permiso— solo
 * pausa, no detiene, así que no dispara nada.
 */
@Composable
internal fun AlVolverDelSegundoPlano(alVolver: (fueraDesde: Long, ahora: Long) -> Unit) {
    val ciclo = LocalLifecycleOwner.current
    val alVolverVigente by rememberUpdatedState(alVolver)
    DisposableEffect(ciclo) {
        var fueraDesde: Long? = null
        val observador = LifecycleEventObserver { _, evento ->
            val ahora = Clock.System.now().toEpochMilliseconds()
            when (evento) {
                Lifecycle.Event.ON_STOP -> fueraDesde = ahora
                Lifecycle.Event.ON_START -> {
                    val desde = fueraDesde
                    fueraDesde = null
                    if (desde != null) alVolverVigente(desde, ahora)
                }
                else -> Unit
            }
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
}

const val TAG_PUERTA_AL_VOLVER: String = "puerta-al-volver"

/**
 * **La puerta al volver**: tapa la app entera hasta que pase el dedo. Encima de todo —pantalla,
 * hojas abiertas, barra— y sin sacar a nadie de donde estaba: con el dedo, la puerta se abre y el
 * ciclo sigue exactamente donde lo dejó (a mitad de un monto en Agregar, si era el caso).
 *
 * Es la misma puerta del arranque (`LoginScreen`), dicha de otra forma: no guarda ni borra nada, y
 * fallar la huella no cuesta nada. «Entrar con mi contraseña» lleva al login de siempre, con la
 * sesión intacta.
 */
@Composable
internal fun PuertaAlVolver(
    huella: HuellaDelAparato,
    onAbierta: () -> Unit,
    onConContrasena: () -> Unit,
) {
    var pidiendo by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf<String?>(null) }

    fun pedir() {
        if (pidiendo) return
        pidiendo = true
        motivo = null
        huella.pedir(PropositoDeHuella.ENTRAR) { resultado ->
            pidiendo = false
            motivo = motivoDeLaHuella(resultado)
            if (resultado == ResultadoDeHuella.EXITO) onAbierta()
        }
    }

    // El dedo se pide solo, apenas aparece la puerta: lo mismo que al abrir la app.
    LaunchedEffect(Unit) { pedir() }
    // «Atrás» no abre la puerta: se queda acá, sin hacer nada.
    BackHandlerEffect(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Movi.colores.fondo)
            .testTag(TAG_PUERTA_AL_VOLVER)
            // Se come los toques: nada de lo que está debajo se puede tocar a través de la puerta.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Como en el login: el logotipo, con tamaño suelto a propósito.
            Text("Movi", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Movi.colores.texto)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pon tu huella para seguir donde estabas.",
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                textAlign = TextAlign.Center,
            )
            motivo?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (pidiendo) Movi.colores.tarjeta else Movi.colores.marca)
                    .clickable(role = Role.Button) { pedir() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (pidiendo) "Esperando tu huella…" else "Usar mi huella",
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (pidiendo) Movi.colores.textoMedio else Movi.colores.sobreMarca,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Entrar con mi contraseña",
                style = Movi.textos.cuerpo,
                color = Movi.colores.marca,
                modifier = Modifier.clickable(role = Role.Button, onClick = onConContrasena),
            )
        }
    }
}
