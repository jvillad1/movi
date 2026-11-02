package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 111 · lo que llega por los atajos del iPhone, del lado de `:core`. */
class CapturaPorAtajoTest {

    private val dia = 86_400_000L
    /** 2026-10-01 15:00 UTC = 10:00 en Bogotá. */
    private val ahora = 1_790_866_800_000L

    private fun diarios(origen: String, desde: Int, hasta: Int) = (hasta..desde).map { Captura(origen, ahora - it * dia) }

    @Test
    fun `los rotulos del atajo se reconocen`() {
        assertTrue(esUnAtajo("Atajo · Bancolombia"))
        assertTrue(esUnAtajo(ORIGEN_DE_APPLE_PAY))
        assertFalse(esUnAtajo("Notificación · Nu"))
        assertTrue(esElAtajoDeApplePay("Atajo · Apple Pay"))
        assertFalse(esElAtajoDeApplePay("Atajo · Bancolombia"))
        assertFalse(esElAtajoDeApplePay("Notificación · Apple Pay"))
    }

    @Test
    fun `banco mudo cuenta el SMS del iPhone como los SMS de su banco`() {
        assertEquals(CanalDeCaptura.SMS, canalDeCaptura("Atajo · Bancolombia"))
        assertEquals("Bancolombia", nombreDelOrigenDeCaptura("Atajo · Bancolombia"))
        // Los SMS de Bancolombia por Android y por el atajo son un solo origen.
        val mudos = origenesMudos(diarios("85540", 30, 16) + diarios("Atajo · Bancolombia", 15, 5), ahora, diasDeSilencio = 3)
        assertEquals(1, mudos.size)
        assertEquals("Hace 5 días no llegan los SMS de Bancolombia. Revisa la captura", textoDeOrigenMudo(mudos.single()))
    }

    @Test
    fun `banco mudo nombra los pagos con Apple Pay como lo que son`() {
        assertEquals(CanalDeCaptura.NOTIFICACION, canalDeCaptura(ORIGEN_DE_APPLE_PAY))
        val mudo = origenesMudos(diarios(ORIGEN_DE_APPLE_PAY, 30, 4), ahora, diasDeSilencio = 3).single()
        assertEquals("Apple Pay", mudo.nombre)
        assertEquals("Hace 4 días no llegan los pagos con Apple Pay. Revisa la captura", textoDeOrigenMudo(mudo))
    }

    @Test
    fun `la linea del codigo dice cuando se creo y el ultimo aviso`() {
        val creado = 1_793_628_000_000L // 2-nov-2026, 9:00 en Bogotá
        assertNull(lineaDelCodigoDeCaptura(CodigoDeCaptura(vigente = false), creado))
        assertEquals(
            "Código creado el 2 nov · todavía no llega ningún aviso",
            lineaDelCodigoDeCaptura(CodigoDeCaptura(vigente = true, creadoEn = creado), creado + 60_000),
        )
        assertEquals(
            "Código creado el 2 nov · último aviso hace 2 h",
            lineaDelCodigoDeCaptura(CodigoDeCaptura(vigente = true, creadoEn = creado, ultimoUso = creado), creado + 2 * 3_600_000L + 5),
        )
        assertEquals("hace un momento", haceCuantoLlegoElAviso(creado, creado + 10_000))
        assertEquals("hace 5 min", haceCuantoLlegoElAviso(creado, creado + 5 * 60_000))
        assertEquals("hace 1 día", haceCuantoLlegoElAviso(creado, creado + dia + 1))
        assertEquals("hace 3 días", haceCuantoLlegoElAviso(creado, creado + 3 * dia))
    }

    private fun cuenta(nombre: String, tipo: AccountType = AccountType.SAVINGS) = Account(id = nombre, name = nombre, type = tipo, balance = 0)

    @Test
    fun `las palabras de la automatizacion salen de los bancos de sus cuentas`() {
        // Vuelta 114: siempre Bancolombia y DAVIVIENDA (como firman su SMS), y después los bancos de sus cuentas.
        assertEquals(listOf("Bancolombia", "DAVIVIENDA"), palabrasParaLaAutomatizacion(emptyList()))
        assertEquals(
            listOf("Bancolombia", "DAVIVIENDA"),
            palabrasParaLaAutomatizacion(
                listOf(
                    cuenta("Bancolombia Ahorros 8133"),
                    cuenta("AMEX 1004", AccountType.CREDIT_CARD), // de Bancolombia: su SMS
                    cuenta("Nu"), // avisa por la app, no por SMS
                    cuenta("Nequi"), // tampoco
                    cuenta("Davivienda 0136"),
                ),
            ),
        )
        assertEquals(listOf("Bancolombia", "DAVIVIENDA"), palabrasParaLaAutomatizacion(listOf(cuenta("Nu"), cuenta("Efectivo", AccountType.CASH))))
        // Con y sin tilde donde el banco puede escribirla de las dos formas; Colpatria y Davibank firman «Davibank».
        assertEquals(
            listOf("Bancolombia", "DAVIVIENDA", "Banco de Bogotá", "Banco de Bogota", "Itaú", "Itau", "Davibank"),
            palabrasParaLaAutomatizacion(listOf(cuenta("Banco de Bogotá 1234"), cuenta("Itaú"), cuenta("Scotiabank Colpatria"), cuenta("AFC Davibank 9497"))),
        )
    }

    @Test
    fun `el tracking acepta el origen y el evento del atajo`() {
        assertTrue(ClaveDeUso.ORIGEN.admite("ATAJO"))
        assertEquals(TipoDeUso.SISTEMA, EventoDeUso.deNombre("CAPTURA_POR_ATAJO")?.tipo)
        assertNull(
            problemaDelEvento(
                "CAPTURA_POR_ATAJO",
                mapOf("origen" to "ATAJO", "estado" to "NO_ES_DEL_BANCO", "clase" to "AVISO", "aviso_id" to "atajo_0123456789abcdef0123456789abcdef"),
            ),
        )
    }

    @Test
    fun `la linea de lo descartado dice cuantos y por que`() {
        assertNull(textoDeAtajosDescartados(ResumenDeAtajosDescartados()))
        assertEquals(
            "12 mensajes no eran de un banco en los últimos 30 días. 3 avisos llegaron demasiado seguidos y no se guardaron.",
            textoDeAtajosDescartados(
                ResumenDeAtajosDescartados(
                    grupos = listOf(GrupoDeAtajosDescartados("NO_ES_DEL_BANCO", 12), GrupoDeAtajosDescartados("DEMASIADOS", 3)),
                ),
            ),
        )
        assertEquals(
            "1 mensaje no era de un banco en los últimos 30 días. 1 aviso llegó vacío.",
            textoDeAtajosDescartados(
                ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("NO_ES_DEL_BANCO", 1), GrupoDeAtajosDescartados("VACIO", 1))),
            ),
        )
        // Vuelta 114 · lo que nombraba un banco sin la forma de un aviso va aparte, en la misma línea.
        assertEquals(
            "3 mensajes nombraban un banco pero no tenían la forma de un aviso en los últimos 30 días.",
            textoDeAtajosDescartados(ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("SIN_FORMA_DE_AVISO", 3)))),
        )
        assertEquals(
            "1 mensaje nombraba un banco pero no tenía la forma de un aviso en los últimos 30 días.",
            textoDeAtajosDescartados(ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("SIN_FORMA_DE_AVISO", 1)))),
        )
        assertEquals(
            "En los últimos 30 días, 12 mensajes no eran de un banco y 3 nombraban un banco pero no tenían la forma de un aviso.",
            textoDeAtajosDescartados(
                ResumenDeAtajosDescartados(
                    grupos = listOf(GrupoDeAtajosDescartados("NO_ES_DEL_BANCO", 12), GrupoDeAtajosDescartados("SIN_FORMA_DE_AVISO", 3)),
                ),
            ),
        )
        // Un motivo de un server más nuevo no rompe nada: no se dice.
        assertNull(textoDeAtajosDescartados(ResumenDeAtajosDescartados(grupos = listOf(GrupoDeAtajosDescartados("OTRO", 2)))))
    }
}
