package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Personas y comercios», perfecto** (4-oct-2026): el tipo deducido, el campo único «número o
 * llave», unir dos fichas y lo enviado/recibido por período. Textos y números sintéticos — ninguno
 * es del dueño.
 */
class PersonasYComerciosTest {

    private fun numero(v: String) = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, v)
    private fun llave(v: String) = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, v)

    private val cancha = DestinoConocido(id = "dst_cancha", nombre = "Cancha", numero = "", llave = "3001112222")
    private val contratista = DestinoConocido(id = "dst_obra", nombre = "Contratista", numero = "00800000404")
    private val parqueadero = DestinoConocido(id = "dst_parq", nombre = "Parqueadero", numero = "", llave = "0088001122")

    private val qrALaCancha =
        "Bancolombia: JUAN PRUEBA pagaste \$13,500.00 por codigo QR desde tu cuenta *9999 a la llave 3001112222 el 20/09/2026."
    private val transferenciaALaObra =
        "Bancolombia: Transferiste \$2,000,000 desde tu cuenta *9999 a la cuenta *00800000404 el 03/10/2026 a las 17:55."

    // ── Persona o comercio ───────────────────────────────────────────────────────

    @Test
    fun `lo que se paga por QR es un comercio aunque la llave sea un celular`() {
        assertEquals(TipoDeTercero.COMERCIO, tipoInferido(cancha, listOf(qrALaCancha, qrALaCancha)))
    }

    @Test
    fun `una cuenta a la que se transfiere es una persona`() {
        assertEquals(TipoDeTercero.PERSONA, tipoInferido(contratista, listOf(transferenciaALaObra)))
    }

    @Test
    fun `solo cuentan los avisos que lo nombran`() {
        // Un QR a OTRA llave no dice nada de la obra.
        assertEquals(TipoDeTercero.PERSONA, tipoInferido(contratista, listOf(qrALaCancha, transferenciaALaObra)))
        assertEquals(TipoDeTercero.COMERCIO, tipoInferido(cancha, listOf(qrALaCancha, transferenciaALaObra)))
    }

    @Test
    fun `sin avisos, una llave con forma de QR es un comercio y lo demas una persona`() {
        assertEquals(TipoDeTercero.COMERCIO, tipoInferido(parqueadero, emptyList()))
        assertEquals(TipoDeTercero.PERSONA, tipoInferido(cancha, emptyList()))
        assertEquals(TipoDeTercero.PERSONA, tipoInferido(contratista, emptyList()))
    }

    @Test
    fun `con tipo elegido no se deduce nada, y sin tipo se marca como deducido`() {
        val elegido = conTipoInferido(cancha.copy(tipo = TipoDeTercero.PERSONA), listOf(qrALaCancha))
        assertEquals(TipoDeTercero.PERSONA, elegido.tipo)
        assertFalse(elegido.tipoInferido)
        val deducido = conTipoInferido(cancha, listOf(qrALaCancha))
        assertEquals(TipoDeTercero.COMERCIO, deducido.tipo)
        assertTrue(deducido.tipoInferido)
    }

    @Test
    fun `al guardar, un tipo deducido que nadie toco viaja vacio`() {
        val deducido = conTipoInferido(cancha, listOf(qrALaCancha))
        assertNull(tipoParaGuardar(deducido, TipoDeTercero.COMERCIO))
        assertEquals(TipoDeTercero.PERSONA, tipoParaGuardar(deducido, TipoDeTercero.PERSONA))
        val elegido = cancha.copy(tipo = TipoDeTercero.COMERCIO)
        assertEquals(TipoDeTercero.COMERCIO, tipoParaGuardar(elegido, TipoDeTercero.COMERCIO))
    }

    // ── Número o llave, en un campo ──────────────────────────────────────────────

    @Test
    fun `lo que tiene arroba o letras es una llave`() {
        assertEquals(llave("@dani.prueba"), identificadorEscrito(" @Dani.Prueba "))
        assertEquals(llave("ana@correo.com"), identificadorEscrito("ana@correo.com"))
        assertEquals(llave("ana prueba salazar"), identificadorEscrito("ANA PRUEBA SALAZAR"))
    }

    @Test
    fun `un numero como lo escribe el banco es una cuenta`() {
        assertEquals(numero("55500000756"), identificadorEscrito("*55500000756"))
        assertEquals(numero("55500000756"), identificadorEscrito("555-0000-0756"))
        assertEquals(numero("00800000404"), identificadorEscrito("00800000404"))
        assertEquals(numero("0756"), identificadorEscrito("0756"))
    }

    @Test
    fun `un celular y una llave de QR son llaves`() {
        assertEquals(llave("3001112222"), identificadorEscrito("300 111 2222"))
        assertEquals(llave("0088001122"), identificadorEscrito("0088001122"))
        // Con asterisco es como el banco escribe una cuenta, aunque tenga diez dígitos.
        assertEquals(numero("3001112222"), identificadorEscrito("*3001112222"))
    }

    @Test
    fun `nada escrito no es nada`() {
        assertNull(identificadorEscrito(""))
        assertNull(identificadorEscrito("  "))
        assertNull(identificadorEscrito("*-"))
    }

    @Test
    fun `la otra lectura da vuelta solo lo que puede darse vuelta`() {
        assertEquals(llave("3001112222"), otraLecturaDe(numero("3001112222")))
        assertEquals(numero("3001112222"), otraLecturaDe(llave("3001112222")))
        assertNull(otraLecturaDe(llave("@dani")))
        assertNull(otraLecturaDe(llave("ana prueba")))
    }

    @Test
    fun `la hoja dice que entendio`() {
        assertEquals("Número de cuenta ·0756", comoLoEntiendeMovi(numero("55500000756")))
        assertEquals("Llave 3001112222", comoLoEntiendeMovi(llave("3001112222")))
    }

    // ── Unir ─────────────────────────────────────────────────────────────────────

    @Test
    fun `unir suma los datos, conserva el nombre de la que queda y hereda lo que le falta`() {
        val ana = DestinoConocido(id = "dst_ana", nombre = "Ana", numero = "55500000756")
        val repetida = DestinoConocido(id = "dst_rep", nombre = "Ana Prueba", numero = "", llave = "ana prueba salazar", deQuien = "esposa")
        val unida = unirTerceros(seVa = repetida, queda = ana)
        assertEquals("dst_ana", unida.id)
        assertEquals("Ana", unida.nombre)
        assertEquals("esposa", unida.deQuien)
        assertEquals(listOf(numero("55500000756"), llave("ana prueba salazar")), unida.todosLosIdentificadores())
        assertEquals("55500000756", unida.numero, "el APK instalado sigue leyendo el primero")
    }

    @Test
    fun `unir no hereda un tipo deducido como si fuera elegido`() {
        val queda = cancha.copy(tipo = TipoDeTercero.COMERCIO, tipoInferido = true)
        val seVa = contratista.copy(tipo = TipoDeTercero.PERSONA, tipoInferido = true)
        assertNull(unirTerceros(seVa, queda).tipo)
        assertEquals(TipoDeTercero.PERSONA, unirTerceros(seVa.copy(tipoInferido = false), queda).tipo)
    }

    // ── Por período ──────────────────────────────────────────────────────────────

    private fun ev(id: String, tipo: TransactionType, monto: Long, cuando: Long) = FinancialEvent(
        id = id, accountId = "a", type = tipo, amount = monto, category = "Otros", description = id, timestamp = cuando,
    )

    @Test
    fun `lo enviado y lo recibido van por periodo del duenno, sin sumarse`() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        // 3-oct-2026 y 20-sep-2026 (mediodía en Bogotá): períodos «octubre» y «septiembre» con corte 25.
        val tresOct = 1_791_050_400_000L
        val veinteSep = 1_789_927_200_000L
        val filas = loDeCadaPeriodo(
            enviados = listOf(ev("e1", TransactionType.EXPENSE, 2_000_000, tresOct), ev("e2", TransactionType.EXPENSE, 500_000, veinteSep)),
            recibidos = listOf(ev("r1", TransactionType.INCOME, 400_000, tresOct)),
            settings = corte25,
        )
        assertEquals(2, filas.size)
        assertEquals(PeriodoFinanciero(2026, 10), filas[0].periodo)
        assertEquals(mapOf("COP" to 2_000_000L), filas[0].enviado)
        assertEquals(mapOf("COP" to 400_000L), filas[0].recibido)
        assertEquals(PeriodoFinanciero(2026, 9), filas[1].periodo)
        assertEquals(emptyMap(), filas[1].recibido)
    }

    // ── La pregunta de Movi AI ───────────────────────────────────────────────────

    @Test
    fun `una pregunta nombra al tercero por su nombre o por un dato, nunca a dos`() {
        val ana = DestinoConocido(id = "dst_ana", nombre = "Ana", numero = "55500000756")
        val anabel = DestinoConocido(id = "dst_anabel", nombre = "Anabel", numero = "", llave = "@anabel")
        val todos = listOf(ana, anabel, cancha)
        assertEquals(ana, terceroQueNombra("ana", todos), "el nombre exacto gana")
        assertEquals(anabel, terceroQueNombra("Anab", todos))
        assertEquals(cancha, terceroQueNombra("cancha", todos))
        assertEquals(ana, terceroQueNombra("0756", todos))
        assertNull(terceroQueNombra("An", listOf(ana.copy(nombre = "Andrea"), anabel)), "dos candidatos: ninguno")
        assertNull(terceroQueNombra("Pedro", todos))
    }
}
