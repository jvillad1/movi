package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Arreglar uno arregla los parecidos** — la mitad que decide qué se ofrece y cómo se dice.
 *
 * La pregunta se hace una sola vez por cambio de categoría, así que tiene que valer la pena: no se
 * ofrece cambiar lo que ya está cambiado, y el texto tiene que nombrar el lugar para que el dueño
 * sepa sobre qué está decidiendo.
 */
class OfertaDeLoteTest {

    private fun mov(id: String, nombre: String, categoria: String) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = 25_000,
        category = categoria, description = nombre, timestamp = 1_000,
    )

    @Test
    fun `no se ofrece cambiar lo que ya esta en esa categoria`() {
        val parecidos = listOf(
            mov("e1", "Mora Soccer", "Otros"),
            mov("e2", "Mora Soccer", "Fútbol"),
            mov("e3", "Mora Soccer", "Comida"),
        )

        val aCambiar = parecidosQueCambiarian(parecidos, "Fútbol")

        assertEquals(listOf("e1", "e3"), aCambiar.map { it.id })
    }

    @Test
    fun `sin parecidos en otra categoria no hay nada que preguntar`() {
        assertTrue(parecidosQueCambiarian(listOf(mov("e1", "Mora Soccer", "Fútbol")), "Fútbol").isEmpty())
        assertTrue(parecidosQueCambiarian(emptyList(), "Fútbol").isEmpty())
    }

    @Test
    fun `el titulo nombra el lugar y cuenta bien`() {
        val cuatro = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "Mora Soccer", "Fútbol"),
            otros = (1..4).map { mov("e$it", "Mora Soccer", "Otros") },
        )
        assertEquals("Hay 4 movimientos más de «Mora Soccer»", tituloDeLaOferta(cuatro))
    }

    /** «1 movimientos» es de las cosas que hacen que una app se sienta escrita por una máquina. */
    @Test
    fun `con uno solo el titulo va en singular`() {
        val uno = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "Mora Soccer", "Fútbol"),
            otros = listOf(mov("e1", "Mora Soccer", "Otros")),
        )
        assertEquals("Hay 1 movimiento más de «Mora Soccer»", tituloDeLaOferta(uno))
    }

    @Test
    fun `sin nombre el titulo sigue siendo una frase`() {
        val sinNombre = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "   ", "Fútbol"),
            otros = listOf(mov("e1", "Mora Soccer", "Otros")),
        )
        assertEquals("Hay 1 movimiento más de «este lugar»", tituloDeLaOferta(sinNombre))
    }

    // ---- Vuelta 139 · elegir cuáles ----

    private fun ingreso(id: String) = mov(id, "Transferencia recibida", "Otros ingresos").copy(type = TransactionType.INCOME)

    private fun mixta(tipoDelCambiado: TransactionType = TransactionType.EXPENSE) = OfertaDeLote(
        categoria = "Tirze",
        movimiento = mov("e0", "Tirze", "Tirze").copy(type = tipoDelCambiado),
        otros = listOf(ingreso("i1"), mov("g1", "T", "Otros"), ingreso("i2"), mov("g2", "T", "Otros")),
    )

    @Test
    fun `la seleccion inicial marca solo los del mismo tipo`() {
        assertEquals(setOf("g1", "g2"), marcadosDeEntrada(mixta()))
        assertEquals(setOf("i1", "i2"), marcadosDeEntrada(mixta(TransactionType.INCOME)))
    }

    @Test
    fun `los grupos van con el tipo del cambiado primero y sin vacios`() {
        assertEquals(listOf("Salieron · 2", "Entraron · 2"), gruposDelLote(mixta()).map(::tituloDelGrupoDelLote))
        assertEquals(listOf("Entraron · 2", "Salieron · 2"), gruposDelLote(mixta(TransactionType.INCOME)).map(::tituloDelGrupoDelLote))
        val soloGastos = OfertaDeLote("T", mov("e0", "T", "T"), listOf(mov("g1", "T", "Otros")))
        assertEquals(1, gruposDelLote(soloGastos).size)
    }

    @Test
    fun `el texto del boton dice cuantos`() {
        assertEquals("Marca los que quieras cambiar", textoDelBotonDelLote("Tirze", 0))
        assertEquals("Poner 1 en «Tirze»", textoDelBotonDelLote("Tirze", 1))
        assertEquals("Poner 7 en «Tirze»", textoDelBotonDelLote("Tirze", 7))
    }

    @Test
    fun `solo se mandan los ids marcados`() {
        assertEquals(listOf("i1", "g2"), idsDelLoteAMandar(mixta(), setOf("g2", "i1", "fuera")))
        assertTrue(idsDelLoteAMandar(mixta(), emptySet()).isEmpty())
    }

    @Test
    fun `el reparto pasa un lote y deja el resto con otra categoria de destino`() {
        val oferta = OfertaDeLote("Hipoteca", mov("e0", "Cooperativa", "Hipoteca"), listOf(mov("a", "C", "Otros"), mov("b", "C", "Otros"), mov("c", "C", "Otros")))
        var r = repartoInicial(oferta).conMarcados(setOf("a", "b"))
        assertEquals(listOf("a", "b"), r.idsAMandar())
        assertEquals("Hay 3 movimientos más de «Cooperativa»", tituloDelReparto(r))

        r = r.tras(r.idsAMandar(), r.idsAMandar())
        assertEquals(listOf("c"), r.quedan.map { it.id })
        assertEquals(listOf("Hipoteca" to 2), r.pasados)
        assertEquals("Pasaste 2 a «Hipoteca»", lineaDeLoPasado(r.pasados.first()))
        assertEquals("Quedan 1 de «Cooperativa»", tituloDelReparto(r))
        assertTrue(!r.terminado)

        r = r.conDestino("Ocio")
        assertEquals("Ocio", r.destino)
        r = r.tras(r.idsAMandar(), r.idsAMandar())
        assertTrue(r.terminado)
        assertEquals(listOf("Hipoteca" to 2, "Ocio" to 1), r.pasados)
    }

    private fun tres() = OfertaDeLote("H", mov("e0", "C", "H"), listOf(mov("a", "C", "Otros"), mov("b", "C", "Otros"), mov("c", "C", "Otros")))

    @Test
    fun `solo cuenta como pasado lo que el server cambio`() {
        val r = repartoInicial(tres()).tras(listOf("a", "b", "c"), listOf("a"))
        assertEquals(listOf("H" to 1), r.pasados)
        assertEquals(2, r.omitidos)
        assertTrue(r.terminado)
        assertEquals("2 no se pudieron cambiar", lineaDeLosOmitidos(2))
        assertEquals("1 no se pudo cambiar", lineaDeLosOmitidos(1))
    }

    @Test
    fun `despues de un lote no se vuelve a marcar lo desmarcado`() {
        val r = repartoInicial(tres()).conMarcados(setOf("a")).tras(listOf("a"), listOf("a"))
        assertEquals(setOf<String>(), r.marcados)
        assertTrue(r.idsAMandar().isEmpty())
    }

    @Test
    fun `cambiar el destino rehace la seleccion por el tipo del destino`() {
        val o = OfertaDeLote("H", mov("e0", "C", "H"), listOf(mov("g", "C", "Otros"), ingreso("i")))
        val r = repartoInicial(o).conDestino("Salario", TransactionType.INCOME)
        assertEquals(setOf("i"), r.marcados)
        assertEquals(TransactionType.INCOME, r.tipoDelDestino)
    }

    @Test
    fun `el conteo no incluye lo que ya esta en el destino ni lo del otro tipo`() {
        val o = OfertaDeLote("H", mov("e0", "C", "H"), listOf(mov("g", "C", "Otros"), mov("ya", "C", "H"), ingreso("i")))
        val r = repartoInicial(o).conMarcados(setOf("g", "ya", "i"))
        assertEquals(listOf("g"), r.idsAMandar())
        assertTrue(avisoDeTiposMezclados(r)!!.startsWith("Marcaste ingresos y gastos: «H» es de gastos"))
        assertEquals(null, avisoDeTiposMezclados(r.conMarcados(setOf("g"))))
    }

    @Test
    fun `el selector sigue al tipo de lo marcado`() {
        val o = OfertaDeLote("H", mov("e0", "C", "H"), listOf(mov("g", "C", "Otros"), ingreso("i")))
        val r = repartoInicial(o)
        assertEquals(TransactionType.EXPENSE, tipoParaElSelector(r))
        assertEquals(TransactionType.INCOME, tipoParaElSelector(r.conMarcados(setOf("i"))))
        assertEquals(TransactionType.EXPENSE, tipoParaElSelector(r.conMarcados(setOf("i", "g"))))
    }

    @Test
    fun `los textos nuevos`() {
        assertEquals("Marcar todos los que salieron", descripcionDelEnlaceDelGrupo(GrupoDelLote(false, emptyList()), false))
        assertEquals("Desmarcar los que entraron", descripcionDelEnlaceDelGrupo(GrupoDelLote(true, emptyList()), true))
        assertTrue(textoDeCategoriaQueNoSeElige("Cuota de crédito").contains("Cuota de crédito"))
    }
}
