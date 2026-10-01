package com.jvillada.movi.avisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant

/**
 * **Lo que Hoy ofrece una sola vez: que Movi avise en este teléfono.**
 *
 * El permiso de notificaciones (Android 13+) se pide **en contexto**, nunca al abrir la app en
 * frío: una tarjeta que dice para qué —«Te aviso cuando llegue un movimiento y antes de que venza
 * un pago»— y que se puede descartar. Pedirlo o descartarlo la cierra para siempre; después el
 * permiso vive en Ajustes → «Captura del banco» → «Captura en este teléfono».
 *
 * Solo Android tiene un `actual` de verdad. En la web y en iOS [rememberOfertaDeAvisos] devuelve
 * `null` y Hoy no pinta nada (la web ya tiene su push por VAPID aparte).
 */
interface OfertaDeAvisos {
    /** Muestra el diálogo del sistema y cierra la oferta, conteste lo que conteste. */
    fun pedir()

    /** «Ahora no»: cierra la oferta sin pedir nada. */
    fun descartar()
}

/**
 * La oferta de ESTE aparato, o `null` si no hay nada que ofrecer: no es Android 13+, el permiso ya
 * está concedido, o la tarjeta ya se mostró y se contestó.
 */
@Composable
internal expect fun rememberOfertaDeAvisos(): OfertaDeAvisos?

object AvisosEnHoy {
    /**
     * Solo para pruebas, como `Huella.sustitutoDePrueba`: sin esto Hoy no se podría montar con la
     * tarjeta a la vista en Robolectric. `AppDePrueba` lo devuelve a `null` antes de cada método.
     */
    internal var sustitutoDePrueba: OfertaDeAvisos? = null

    /** Lo que usa Hoy. `null` = no hay tarjeta. */
    @Composable
    fun oferta(): OfertaDeAvisos? = sustitutoDePrueba ?: rememberOfertaDeAvisos()
}

/** El tag de la tarjeta, para encontrarla en una prueba sin depender de su texto. */
const val TAG_TARJETA_DE_AVISOS: String = "tarjeta-de-avisos"

/** El texto de la tarjeta: para qué son los avisos, dicho antes de pedir nada. */
const val PARA_QUE_SON_LOS_AVISOS: String = "Te aviso cuando llegue un movimiento y antes de que venza un pago."

/**
 * La tarjeta de Hoy que ofrece los avisos. Se esconde apenas se toca cualquiera de los dos botones,
 * sin esperar a que el sistema conteste: la respuesta del diálogo no cambia lo que esta tarjeta
 * tenía para decir.
 */
@Composable
fun TarjetaDeAvisos(oferta: OfertaDeAvisos) {
    var visible by androidx.compose.runtime.remember(oferta) { mutableStateOf(true) }
    if (!visible) return
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinCard(
            modifier = Modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_AVISOS),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.amplio),
        ) {
            Text("¿Te aviso en este teléfono?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(Movi.espacios.minimo))
            Text(PARA_QUE_SON_LOS_AVISOS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Movi.espacios.corto))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { visible = false; oferta.descartar() }) {
                    Text("Ahora no", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                }
                TextButton(onClick = { visible = false; oferta.pedir() }) {
                    Text("Activar avisos", style = Movi.textos.cuerpo, color = Movi.colores.marca)
                }
            }
        }
        Spacer(Modifier.height(Movi.espacios.amplio))
    }
}

/**
 * **A qué pantalla abrir porque se tocó un aviso.** Lo escribe la `MainActivity` al recibir el
 * `Intent` del aviso (ver [destinoDeAviso]) y lo consume `App()` apenas hay sesión y la puerta de
 * la huella quedó atrás: abrir «Por revisar» encima del login sería saltarse esa puerta.
 *
 * `mutableStateOf` para que `App()` se entere aunque ya esté compuesta (la app estaba abierta y el
 * aviso llegó por `onNewIntent`). Lo limpia el logout: un destino pendiente es de quien lo tocó.
 */
object DestinoDesdeAfuera {
    var pendiente: Screen? by mutableStateOf(null)
}
