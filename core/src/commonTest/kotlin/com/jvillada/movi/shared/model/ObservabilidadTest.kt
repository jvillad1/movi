package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 100 · la limpieza de un fallo y la lista cerrada del uso. Ver `Observabilidad.kt`. */
class ObservabilidadTest {

    private val stackJvm = """
        java.lang.NumberFormatException: For input string: "27500000"
        	at java.lang.Long.parseLong(Long.java:589)
        	at com.jvillada.movi.sms.SmsSyncWorker${'$'}doWork${'$'}2.invokeSuspend(SmsSyncWorker.kt:43)
        	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
        Caused by: java.lang.IllegalStateException: saldo 1.843.014 de juan@correo.com
        	at com.jvillada.movi.data.Algo.otra(Algo.kt:12)
    """.trimIndent()

    @Test
    fun `el mensaje pierde los numeros largos, los correos y lo citado`() {
        val limpio = limpiarTextoDelFallo("Saldo 27.500.000 de juan@correo.com: \"Rappi Colombia\" y 1234567890 y 1234")
        assertFalse("27" in limpio && "500" in limpio, limpio)
        assertFalse("juan" in limpio, limpio)
        assertFalse("Rappi" in limpio, limpio)
        assertFalse("1234567890" in limpio, limpio)
        assertTrue("1234" in limpio, "un número de cuatro cifras no es un dato: $limpio")
        assertTrue(limpiarTextoDelFallo("x".repeat(5_000)).length <= MAX_MENSAJE_DEL_FALLO)
        assertFalse('\n' in limpiarTextoDelFallo("uno\ndos"))
    }

    @Test
    fun `el stack se recorta y se limpia linea por linea`() {
        val largo = (1..200).joinToString("\n") { "\tat com.jvillada.movi.X.f$it(X.kt:$it)" }
        assertEquals(MAX_LINEAS_DEL_STACK, stackRecortado(largo).lines().size)
        val limpio = stackRecortado(stackJvm)
        assertFalse("27500000" in limpio, limpio)
        assertFalse("1.843.014" in limpio, limpio)
        assertFalse("juan@" in limpio, limpio)
        assertTrue("SmsSyncWorker.kt:43" in limpio, "los números de línea sobreviven: $limpio")
    }

    @Test
    fun `el componente es la clase propia de arriba`() {
        assertEquals("SmsSyncWorker", componenteDelStack(stackJvm))
        assertEquals("App", componenteDelStack("Error\n at 3 ComposeApp 0x0 kfun:com.jvillada.movi.App#App(){} + 12"))
        assertEquals("", componenteDelStack("java.lang.Error\n\tat android.os.Looper.loop(Looper.java:1)"))
    }

    @Test
    fun `la huella no depende del numero de linea`() {
        val otraVersion = stackJvm.replace("SmsSyncWorker.kt:43", "SmsSyncWorker.kt:57")
        assertEquals(huellaDelFallo("NumberFormatException", stackJvm), huellaDelFallo("NumberFormatException", otraVersion))
        assertTrue(huellaDelFallo("NumberFormatException", stackJvm).startsWith("NumberFormatException · com.jvillada.movi.sms.SmsSyncWorker"))
    }

    @Test
    fun `falloDe arma el reporte ya limpio`() {
        val f = falloDe(
            IllegalStateException("cuenta 0123456789 sin saldo"), id = "f1", plataforma = "android", version = "1.90",
            hilo = "main", modelo = "samsung SM-A155M", sistema = "Android 14", ahora = 1L,
        )
        assertEquals("IllegalStateException", f.tipo)
        assertFalse("0123456789" in f.mensaje)
        assertFalse("0123456789" in f.stack)
        assertTrue(f.stack.lines().size <= MAX_LINEAS_DEL_STACK)
    }

    @Test
    fun `el uso rechaza lo que no esta en la lista`() {
        assertNull(problemaDelEvento("PANTALLA", mapOf("pantalla" to "PorRevisar"), ruta = "Dashboard>PorRevisar"))
        assertNull(problemaDelEvento("ESCRITURA_FALLIDA", mapOf("codigo" to "422", "metodo" to "POST", "recurso" to "events")))
        // Ids de entidades, estados, códigos de error, conteos y duraciones sí.
        assertNull(
            problemaDelEvento(
                "LECTURA_DE_PAPEL",
                mapOf(
                    "documento_id" to "doc_3f2a9c1d-0b7e-4c55-9a51-1c2d3e4f5a6b", "resultado" to "OK", "clase" to "EXTRACTO",
                    "ms" to "8450", "cuantos" to "23", "error" to "IA_SIN_CREDITO", "estado" to "PENDIENTE",
                ),
            ),
        )
        assertNull(problemaDelEvento("POR_REVISAR", mapOf("aviso_id" to "sms_rt_9c1d2e3f4a5b6c7d", "resultado" to "CONFIRMADO")))
        // Un nombre fuera de la lista, una clave fuera de la lista.
        assertNotNull(problemaDelEvento("COMPRA", emptyMap()))
        assertNotNull(problemaDelEvento("PANTALLA", mapOf("monto" to "27500")))
        assertNotNull(problemaDelEvento("PANTALLA", mapOf("descripcion" to "Almuerzo")))
        // Valores con la forma equivocada: un monto, un texto, un comercio, un número de cuenta.
        assertNotNull(problemaDelEvento("SEGUNDO_PLANO", mapOf("segundos" to "27.500.000")))
        assertNotNull(problemaDelEvento("PANTALLA", mapOf("pantalla" to "Rappi 27500")))
        assertNotNull(problemaDelEvento("MOVI_AI", mapOf("origen" to "cuanto gaste en rappi")))
        assertNotNull(problemaDelEvento("ESCRITURA_FALLIDA", mapOf("recurso" to "events/ev_123")))
        assertNotNull(problemaDelEvento("POR_REVISAR", mapOf("cuenta_id" to "0123456789")))
        assertNotNull(problemaDelEvento("POR_REVISAR", mapOf("cuenta_id" to "rappi_colombia")))
        assertNotNull(problemaDelEvento("POR_REVISAR", mapOf("estado" to "Pagado a Caro")))
    }

    @Test
    fun `la ruta es una pila de pantallas con su parametro`() {
        assertTrue(rutaBienFormada(""))
        assertTrue(rutaBienFormada("Dashboard>PorRevisar>SMSReconcile(sms_rt_9c1d2e3f4a5b6c7d)"))
        assertTrue(rutaBienFormada("PrimerUso(paso 4)"))
        assertTrue(rutaBienFormada("Periodos>DetalleDePeriodo(2026-09)"))
        assertFalse(rutaBienFormada("Dashboard>Cuenta 1234"))
        assertFalse(rutaBienFormada("Transactions(Rappi 27.500)"))
        assertFalse(rutaBienFormada("Dashboard>" + "Plan>".repeat(80)))
    }

    @Test
    fun `ninguna clave admite una frase ni un monto`() {
        for (c in ClaveDeUso.entries) {
            assertFalse(c.admite("Compra en Rappi por \$27.500"), "${c.clave} admite una frase")
            assertFalse(c.admite("\$27.500"), "${c.clave} admite un monto con signo")
            assertTrue(c.forma != FormaDeDato.LISTA || c.valores.isNotEmpty(), "${c.clave} es una lista vacía")
        }
    }
}
