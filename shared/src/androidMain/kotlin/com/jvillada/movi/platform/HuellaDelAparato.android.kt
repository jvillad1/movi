package com.jvillada.movi.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella

/**
 * # «Entrar con huella» en Android
 *
 * ## Lo que hace, y lo que deliberadamente NO hace
 *
 * Muestra el prompt biométrico del sistema y contesta si el dedo fue aceptado. **No cifra ni
 * guarda nada.** El token de sesión sigue en el almacenamiento privado de la app, igual que
 * siempre, y ninguna pieza de este archivo lo toca.
 *
 * Hubo una versión anterior que sí lo cifraba, con una llave del Keystore creada con
 * `setUserAuthenticationRequired(true)`. Se quitó entera —no quedó a medias— porque su precio era
 * la captura de SMS: `SmsBackfillWorker` y `SmsSyncWorker` corren sin nadie delante del teléfono,
 * y una llave que exige un dedo los dejaba sin token. Mantener el Keystore «por las dudas» habría
 * sido peor que no tenerlo: un mecanismo a medio usar que aparenta una protección que ya no está.
 * El porqué completo, con el costo que el dueño eligió pagar, está en `EntrarConHuella.kt`.
 *
 * Sin `CryptoObject` el prompt es exactamente lo que dice ser: una puerta, no una caja fuerte.
 *
 * ## Por qué hace falta una FragmentActivity
 *
 * `BiometricPrompt` de AndroidX se monta como fragmento. Si la actividad que hospeda a Compose no
 * es una [FragmentActivity] —el caso de `createComposeRule()` en las pruebas de Robolectric—,
 * [huellaDeLaPlataforma] devuelve `null` y la app se comporta como en la web: sin ofrecimiento y
 * sin interruptor. Nunca tira.
 */
@Composable
internal actual fun huellaDeLaPlataforma(): HuellaDelAparato? {
    val contexto = LocalContext.current
    val actividad = remember(contexto) { contexto.buscarFragmentActivity() }
    return remember(actividad) { actividad?.let { HuellaAndroid(it) } }
}

private tailrec fun Context.buscarFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.buscarFragmentActivity()
    else -> null
}

private class HuellaAndroid(private val actividad: FragmentActivity) : HuellaDelAparato {

    override fun estado(): EstadoDeHuella = runCatching {
        when (BiometricManager.from(actividad).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> EstadoDeHuella.LISTA
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> EstadoDeHuella.SIN_REGISTRAR
            else -> EstadoDeHuella.NO_DISPONIBLE
        }
    }.getOrDefault(EstadoDeHuella.NO_DISPONIBLE)

    /**
     * El prompt del sistema. El botón negativo dice «Usar mi contraseña» y no «Cancelar» porque
     * eso es lo que de verdad pasa al tocarlo: queda el formulario de siempre, que nunca deja de
     * estar debajo.
     */
    override fun pedir(proposito: PropositoDeHuella, alTerminar: (ResultadoDeHuella) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                alTerminar(ResultadoDeHuella.EXITO)
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                alTerminar(
                    when (codigo) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> ResultadoDeHuella.CANCELADA
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> ResultadoDeHuella.SIN_REGISTRAR
                        else -> ResultadoDeHuella.FALLA
                    }
                )
            }

            // Una huella que no coincide NO termina nada: el prompt sigue abierto y la persona
            // puede volver a intentar. Avisar acá cerraría el diálogo al primer dedo mal puesto.
            override fun onAuthenticationFailed() = Unit
        }

        runCatching {
            val prompt = BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(
                    when (proposito) {
                        PropositoDeHuella.ENTRAR -> "Entrar en Movi"
                        PropositoDeHuella.ACTIVAR -> "Activar «Entrar con huella»"
                    }
                )
                .setSubtitle(
                    when (proposito) {
                        PropositoDeHuella.ENTRAR -> "Pon tu huella para abrir la app."
                        PropositoDeHuella.ACTIVAR -> "Confirma tu huella: es la que va a abrir Movi de ahora en más."
                    }
                )
                .setNegativeButtonText("Usar mi contraseña")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
        }.onFailure { alTerminar(ResultadoDeHuella.FALLA) }
    }
}
