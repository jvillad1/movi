package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Toda escritura del repositorio vacía lo recordado
 *
 * [InvalidaElInicioAlEscribir] envuelve las escrituras una por una, a mano. Cuando se sumaron las
 * cuentas ajenas (`createDestino`, `updateDestino`, `deleteDestino`) nadie las envolvió, y el
 * compilador no dijo nada: la delegación `by delegado` las dejaba pasar derecho. Esta prueba no
 * confía en la lista: recorre la INTERFAZ, llama a cada función que no es una lectura y afirma que
 * [CacheDeLecturas.borrarTodo] corrió. Una escritura nueva sin envolver se pone roja acá.
 *
 * «Lectura» es lo que empieza con `get`, más [LECTURAS_SIN_GET]. Si mañana aparece una lectura con
 * otro nombre, esta prueba la marcará como escritura sin envolver: se agrega a [LECTURAS_SIN_GET]
 * a conciencia, no por omisión.
 *
 * Cómo se llama a una función `suspend` sin saber sus argumentos: por reflexión, con una
 * `Continuation` a mano, y un repositorio de abajo ([Proxy]) que contesta `null` a todo sin
 * suspender. Los argumentos se fabrican vacíos (ver [argumentoVacio]); lo único que importa es
 * que la llamada atraviese el envoltorio.
 */
class CadaEscrituraVaciaLoRecordadoTest {

    @Test
    fun `cada escritura del repositorio borra lo recordado`() {
        val envuelto = envueltoSobreNada()

        val escrituras = WalletRepository::class.java.methods
            .filter { !Modifier.isStatic(it.modifiers) && !esLectura(it) }
        assertTrue(escrituras.size > 40, "la interfaz tiene muchas más escrituras que ${escrituras.size}")

        val sinEnvolver = escrituras.filter { metodo ->
            val antes = CacheDeLecturas.generacion
            val args = metodo.parameterTypes.map { argumentoVacio(it) }.toTypedArray()
            metodo.invoke(envuelto, *args)
            CacheDeLecturas.generacion == antes
        }.map { it.name }

        assertEquals(emptyList(), sinEnvolver, "escrituras que no vacían lo recordado")
    }

    /**
     * Y al revés: una lectura envuelta por error no engaña a nadie, pero vacía lo recordado cada vez
     * que se hace — `parseSms` lo hacía, y abrir un mensaje del banco y volver dejaba Por revisar y
     * Movimientos en esqueleto.
     */
    @Test
    fun `ninguna lectura borra lo recordado`() {
        val envuelto = envueltoSobreNada()
        val lecturas = WalletRepository::class.java.methods
            .filter { !Modifier.isStatic(it.modifiers) && esLectura(it) }

        val queBorran = lecturas.filter { metodo ->
            val antes = CacheDeLecturas.generacion
            val args = metodo.parameterTypes.map { argumentoVacio(it) }.toTypedArray()
            metodo.invoke(envuelto, *args)
            CacheDeLecturas.generacion != antes
        }.map { it.name }

        assertEquals(emptyList(), queBorran, "lecturas que vacían lo recordado")
    }

    /** El envoltorio de la app sobre un repositorio que contesta vacío a todo, sin suspender. */
    private fun envueltoSobreNada(): WalletRepository {
        val abajo = Proxy.newProxyInstance(
            WalletRepository::class.java.classLoader,
            arrayOf(WalletRepository::class.java),
        ) { _, metodo, _ -> respuestaVacia(metodo) } as WalletRepository
        return InvalidaElInicioAlEscribir(abajo)
    }

    /**
     * `null` para todo lo que es un objeto; un cero para lo que el envoltorio va a desempacar a un
     * primitivo (`requestPasswordReset` devuelve `Int`). El tipo sale de la `Continuation<in T>`,
     * que es el único lugar del bytecode donde una función `suspend` todavía dice qué devuelve.
     */
    private fun respuestaVacia(metodo: Method): Any? {
        val continuacion = metodo.genericParameterTypes.lastOrNull() as? ParameterizedType ?: return null
        val tipo = (continuacion.actualTypeArguments.single() as? WildcardType)?.lowerBounds?.singleOrNull()
            ?: continuacion.actualTypeArguments.single()
        return when (tipo) {
            java.lang.Integer::class.java -> 0
            java.lang.Long::class.java -> 0L
            java.lang.Boolean::class.java -> false
            java.lang.Double::class.java -> 0.0
            else -> null
        }
    }

    private fun esLectura(m: Method) = m.name.startsWith("get") || m.name in LECTURAS_SIN_GET

    private fun argumentoVacio(tipo: Class<*>): Any? = when {
        tipo == Continuation::class.java -> CONTINUACION
        tipo == java.lang.Long.TYPE -> 0L
        tipo == Integer.TYPE -> 0
        tipo == java.lang.Boolean.TYPE -> false
        tipo == java.lang.Double.TYPE -> 0.0
        tipo == String::class.java -> ""
        tipo == ByteArray::class.java -> ByteArray(0)
        tipo == List::class.java -> emptyList<Any>()
        tipo == Map::class.java -> emptyMap<Any, Any>()
        tipo.isEnum -> tipo.enumConstants.first()
        // Una instancia sin pasar por el constructor: el envoltorio solo la reenvía.
        else -> ASIGNAR_SIN_CONSTRUCTOR.invoke(UNSAFE, tipo)
    }

    private companion object {
        // `parseSms` es `GET /api/sms/{id}/parse`: interpreta el mensaje, no escribe nada.
        val LECTURAS_SIN_GET = setOf("isScreenAdmin", "parseSms")

        val CONTINUACION = object : Continuation<Any?> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) = Unit
        }

        // Por nombre y no por tipo: las pruebas de Android compilan contra `android.jar`, que no
        // publica `sun.misc.Unsafe`, aunque la JVM que las corre sí lo tiene.
        private val CLASE_UNSAFE: Class<*> = Class.forName("sun.misc.Unsafe")
        val UNSAFE: Any = CLASE_UNSAFE.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val ASIGNAR_SIN_CONSTRUCTOR: Method = CLASE_UNSAFE.getMethod("allocateInstance", Class::class.java)
    }
}
