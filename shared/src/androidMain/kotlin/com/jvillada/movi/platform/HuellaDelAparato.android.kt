package com.jvillada.movi.platform

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SesionGuardada
import com.jvillada.movi.data.SessionManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * # «Entrar con huella» en Android
 *
 * ## Qué se guarda y dónde
 *
 * Se guarda **el token de sesión** que el servidor ya había emitido — el mismo que hasta ahora
 * vivía en texto plano en las SharedPreferences—, cifrado con AES-GCM. **La contraseña no se
 * guarda nunca, en ningún lado**: Movi ni siquiera la conserva en memoria después de entrar.
 *
 * La llave del cifrado la genera el **Keystore de Android** y no sale nunca del teléfono (en los
 * equipos modernos vive en hardware dedicado). Los bytes cifrados y su vector de inicialización
 * sí van a las SharedPreferences de siempre, y eso está bien: sin la llave no son nada.
 *
 * ## Por qué la llave pide huella, y no solo la app
 *
 * La llave se crea con `setUserAuthenticationRequired(true)`, así que **el Keystore mismo se
 * niega a usarla** hasta que una huella autentique la operación. La alternativa fácil —cifrar con
 * una llave que la app pueda usar cuando quiera y mostrar el prompt solo como paso de interfaz—
 * deja el secreto al alcance de cualquiera que corra código como la app (con un APK de depuración
 * eso es `adb shell run-as`, que es exactamente el escenario que esta función vino a cerrar). Acá
 * no hay nada que sacarle al teléfono sin un dedo.
 *
 * La contracara, y es real: **cifrar también pide la huella**. Por eso activar la función muestra
 * un prompt una vez. Se prefirió eso a una promesa más floja.
 *
 * ## Cuando las huellas del teléfono cambian
 *
 * `setInvalidatedByBiometricEnrollment(true)`: si alguien agrega o quita una huella, Android
 * **invalida la llave a propósito** y lo cifrado deja de poder abrirse para siempre. No es un
 * error a evitar, es la protección funcionando —una huella nueva sería una persona nueva—, y el
 * único camino correcto es olvidar lo guardado y pedir la contraseña. Eso lo decide
 * [com.jvillada.movi.data.quePasaTrasLaHuella]; acá solo se reporta
 * [ResultadoDeHuella.LLAVE_INVALIDA].
 *
 * ## Por qué hace falta una FragmentActivity
 *
 * `BiometricPrompt` de AndroidX se monta como fragmento. Si la actividad que hospeda a Compose no
 * es una [FragmentActivity] —el caso de `createComposeRule()` en las pruebas de Robolectric—,
 * [huellaDeLaPlataforma] devuelve `null` y la app se comporta como en la web: sin ofrecimiento y
 * sin interruptor. Nunca tira.
 */

/** Una sola llave, versionada en el nombre por si algún día cambia el formato de lo cifrado. */
private const val ALIAS = "movi_sesion_v1"
private const val TRANSFORMACION = "AES/GCM/NoPadding"
private const val BITS_DE_ETIQUETA = 128

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

    override fun haySesionGuardada(): Boolean = SessionManager.sesionBajoLlave != null

    override fun guardar(sesion: SesionGuardada, alTerminar: (ResultadoDeHuella) -> Unit) {
        // Llave nueva en cada activación: así una llave vieja e inservible (huellas cambiadas)
        // nunca bloquea al dueño que quiere volver a activar la función.
        val cifrador = runCatching {
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llaveNueva()) }
        }.getOrElse { return alTerminar(fallaDe(it)) }

        pedirHuella(
            titulo = "Activar «Entrar con huella»",
            subtitulo = "Confirma tu huella para guardar tu sesión cifrada en este teléfono.",
            cifrador = cifrador,
            alFallar = alTerminar,
        ) { listo ->
            runCatching {
                val datos = listo.doFinal(sesion.aTexto().encodeToByteArray())
                SessionManager.activarHuella(iv = listo.iv.aHex(), datos = datos.aHex())
            }.fold(
                onSuccess = { alTerminar(ResultadoDeHuella.EXITO) },
                onFailure = { alTerminar(fallaDe(it)) },
            )
        }
    }

    override fun abrir(alTerminar: (ResultadoDeHuella, SesionGuardada?) -> Unit) {
        val (iv, datos) = SessionManager.sesionBajoLlave
            // No hay nada que abrir. `decidirArranque` no llega hasta acá en ese caso, pero si
            // alguien lo llamara de otro lado, «la llave no sirve» es la respuesta que borra el
            // resto y manda al formulario, que es lo correcto.
            ?: return alTerminar(ResultadoDeHuella.LLAVE_INVALIDA, null)

        val cifrador = runCatching {
            val llave = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey(ALIAS, null) as? SecretKey
                ?: throw KeyPermanentlyInvalidatedException("Sin llave en el Keystore")
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(BITS_DE_ETIQUETA, iv.deHex()))
            }
        }.getOrElse { return alTerminar(fallaDe(it), null) }

        pedirHuella(
            titulo = "Entrar en Movi",
            subtitulo = "Pon tu huella para abrir tu sesión guardada.",
            cifrador = cifrador,
            alFallar = { alTerminar(it, null) },
        ) { listo ->
            runCatching {
                textoASesion(listo.doFinal(datos.deHex()).decodeToString())
            }.fold(
                onSuccess = { alTerminar(ResultadoDeHuella.EXITO, it) },
                onFailure = { alTerminar(fallaDe(it), null) },
            )
        }
    }

    override fun olvidar() {
        SessionManager.desactivarHuella()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        }
    }

    /**
     * El prompt del sistema. El botón negativo dice «Usar mi contraseña» y no «Cancelar» porque
     * eso es lo que de verdad pasa al tocarlo: se vuelve al formulario de siempre, que nunca deja
     * de estar debajo.
     */
    private fun pedirHuella(
        titulo: String,
        subtitulo: String,
        cifrador: Cipher,
        alFallar: (ResultadoDeHuella) -> Unit,
        alLograr: (Cipher) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                val listo = resultado.cryptoObject?.cipher
                if (listo == null) alFallar(ResultadoDeHuella.FALLA) else alLograr(listo)
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                alFallar(
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
                .setTitle(titulo)
                .setSubtitle(subtitulo)
                .setNegativeButtonText("Usar mi contraseña")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cifrador))
        }.onFailure { alFallar(ResultadoDeHuella.FALLA) }
    }
}

/**
 * Traduce lo que tiró el Keystore. Solo [KeyPermanentlyInvalidatedException] borra lo guardado:
 * es la única que significa «esto ya no se puede abrir nunca más».
 */
private fun fallaDe(error: Throwable): ResultadoDeHuella =
    if (error is KeyPermanentlyInvalidatedException) ResultadoDeHuella.LLAVE_INVALIDA
    else ResultadoDeHuella.FALLA

private fun llaveNueva(): SecretKey {
    val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .apply {
            // El «cuánto vale una autenticación»: cero segundos = vale para ESTA operación y
            // nada más. Es la misma idea en las dos APIs; la de arriba llegó en Android 11.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(-1)
            }
        }
        .build()
    generador.init(spec)
    return generador.generateKey()
}

/**
 * **Los cuatro campos, en un texto, sin depender de nada.**
 *
 * `:shared` no aplica el plugin de kotlinx.serialization (solo `:core` lo hace), así que un
 * `@Serializable` acá no generaría nada. Y aunque lo aplicara, esto no lo necesita: cada campo va
 * en hexadecimal, así que el separador **no puede aparecer adentro de un valor** — un nombre con
 * una barra vertical, o un token con cualquier cosa, no puede partir el texto en otro lado.
 */
private fun SesionGuardada.aTexto(): String =
    listOf(token, userId, nombre, correo).joinToString("|") { it.encodeToByteArray().aHex() }

private fun textoASesion(texto: String): SesionGuardada {
    val partes = texto.split("|").map { it.deHex().decodeToString() }
    // Un formato que no reconocemos es una caja fuerte que no sirve: tirar acá termina en
    // `fallaDe` → se olvida lo guardado y se pide la contraseña, que es lo correcto.
    require(partes.size == 4) { "La sesión guardada no tiene el formato esperado" }
    return SesionGuardada(token = partes[0], userId = partes[1], nombre = partes[2], correo = partes[3])
}

// Hexadecimal a mano: `android.util.Base64` no existe fuera del teléfono (las pruebas de JVM lo
// ven como un stub que tira) y `java.util.Base64` recién llega en Android 8, por debajo del
// mínimo de Movi. Esto no tiene ninguna de las dos pegas y se lee igual.
private fun ByteArray.aHex(): String = joinToString("") { b -> "%02x".format(b) }

private fun String.deHex(): ByteArray =
    ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
