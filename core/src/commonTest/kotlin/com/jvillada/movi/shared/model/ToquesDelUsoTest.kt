package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 104 · los nombres `toque:<id>`, las ráfagas sin efecto y el CSV de una sesión. */
class ToquesDelUsoTest {

    @Test
    fun `un toque tiene forma fija y cabe en la columna`() {
        assertTrue(esNombreDeToque("toque:guardar"))
        assertTrue(esNombreDeToque("toque:por-revisar:ignorar-los-antiguos"))
        assertTrue(esNombreDeToque("toque:dia-del-mes:15"))
        assertTrue("toque:x".length <= 40)
        assertTrue((PREFIJO_DE_TOQUE + "a".repeat(MAX_ID_DE_TOQUE)).length <= 40, "el nombre entero entra en uso_de_la_app.nombre")
        assertFalse(esNombreDeToque("toque:" + "a".repeat(MAX_ID_DE_TOQUE + 1)), "más largo que la columna")
        assertEquals(TipoDeUso.ACCION, tipoDeUsoDe("toque:guardar"))
        assertEquals(TipoDeUso.SISTEMA, tipoDeUsoDe("WORKER"))
        assertNull(tipoDeUsoDe("toque:"))
    }

    @Test
    fun `ningun texto del usuario ni un monto tiene la forma de un toque`() {
        listOf(
            "toque:Guardar", // mayúsculas: un nombre propio
            "toque:pagarle a caro", // espacios: una frase
            "toque:27500", // un monto
            "toque:pago-27500000", // un monto pegado a un rótulo
            "toque:a1234", // cuatro cifras seguidas
            "toque:\$27.500",
            "toque:juan@correo.com",
            "toque:rappi.las-doce",
            "toque:-guardar",
            "toque:guardar-",
        ).forEach { assertFalse(esNombreDeToque(it), it) }
        assertNotNull(problemaDelEvento("toque:Pagarle a Caro", emptyMap()))
        assertNull(problemaDelEvento("toque:guardar", mapOf("componente" to "Boton", "boton" to "PRINCIPAL")))
        assertNotNull(problemaDelEvento("toque:guardar", mapOf("monto" to "27500")), "una clave fuera de la lista")
        // Las claves nuevas siguen sin dejar pasar nada del usuario.
        assertFalse(ClaveDeUso.CAMPO.admite("Mercado del mes"))
        assertFalse(ClaveDeUso.HOJA.admite("Nu"))
        assertFalse(ClaveDeUso.PERIODO.admite("octubre"))
        assertFalse(ClaveDeUso.COMO.admite("tocó afuera"))
        assertTrue(ClaveDeUso.COMO.admite("VELO"))
        assertTrue(ClaveDeUso.WORKER.admite("SmsSyncWorker"))
        assertFalse(ClaveDeUso.WORKER.admite("SmsSyncWorker2"))
    }

    @Test
    fun `una rafaga son tres toques al mismo id en menos de dos segundos`() {
        val t = { id: String, ms: Long, quien: String -> ToqueParaContar(quien, "ses_1", "toque:$id", ms) }
        val toques = listOf(
            // Cuatro seguidos a «guardar» en 1,2 s: una sola ráfaga.
            t("guardar", 0, "a"), t("guardar", 300, "a"), t("guardar", 700, "a"), t("guardar", 1_200, "a"),
            // Tres a «confirmar» pero en 2,5 s: no.
            t("confirmar", 10_000, "a"), t("confirmar", 11_000, "a"), t("confirmar", 12_500, "a"),
            // Tres a dos ids distintos: no.
            t("x", 20_000, "a"), t("y", 20_100, "a"), t("x", 20_200, "a"),
            // Otra persona, otra ráfaga a «guardar».
            t("guardar", 0, "b"), t("guardar", 100, "b"), t("guardar", 200, "b"),
            // Seis en 1 s más tarde: una más de «a».
            t("guardar", 60_000, "a"), t("guardar", 60_100, "a"), t("guardar", 60_200, "a"),
            t("guardar", 60_300, "a"), t("guardar", 60_400, "a"), t("guardar", 60_500, "a"),
        )
        assertEquals(listOf(Conteo("guardar", 3, 2)), rafagasSinEfecto(toques))
        assertEquals(emptyList(), rafagasSinEfecto(emptyList()))
    }

    @Test
    fun `una sesion en CSV es una fila por evento, citada`() {
        val csv = csvDelRecorrido(
            listOf(
                EventoDelRecorrido(1, 1_000, "SISTEMA", "SESION_EMPEZADA", "", mapOf("origen" to "FRIO"), plataforma = "android"),
                EventoDelRecorrido(2, 2_000, "ACCION", "toque:guardar", "Dashboard>QuickAdd", mapOf("componente" to "Boton", "boton" to "PRINCIPAL"), plataforma = "android"),
                EventoDelRecorrido(0, 2_500, "SISTEMA", "AVISO_CAPTURADO", "", mapOf("origen" to "SMS"), plataforma = "server", sinSesion = true),
            ),
        )
        val filas = csv.trimEnd().lines()
        assertEquals("secuencia,hora_ms,tipo,nombre,ruta,datos,plataforma", filas[0])
        assertEquals("1,1000,SISTEMA,SESION_EMPEZADA,,origen=FRIO,android", filas[1])
        assertEquals("2,2000,ACCION,toque:guardar,Dashboard>QuickAdd,boton=PRINCIPAL;componente=Boton,android", filas[2])
        assertEquals("0,2500,SISTEMA,AVISO_CAPTURADO,,origen=SMS,server", filas[3])
        assertEquals(4, filas.size)
    }
}
