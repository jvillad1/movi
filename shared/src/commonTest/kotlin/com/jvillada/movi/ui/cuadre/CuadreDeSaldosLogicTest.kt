package com.jvillada.movi.ui.cuadre

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Las reglas del cuadre de saldos
 *
 * Las tres cosas que esta pantalla no puede equivocar: **cuánto** se va a anotar, **qué** cuentas
 * entran, y **cuándo** vale la pena avisar. Las dos primeras mueven plata; la tercera decide si el
 * dueño ve un aviso o no, que es lo único que separa a esta feature de la que ya existía.
 */
class CuadreDeSaldosLogicTest {

    private val UN_DIA = 24L * 60 * 60 * 1000
    private val AHORA = 1_789_923_600_000L // 20-sep-2026, mediodía en Bogotá

    private fun cuenta(
        id: String = "acc-nu",
        nombre: String = "Nu",
        tipo: AccountType = AccountType.SAVINGS,
        saldo: Long = 352_082L,
        moneda: String = "COP",
        ultimoAjuste: Long? = null,
        primerEvento: Long? = null,
    ) = Account(
        id = id,
        name = nombre,
        type = tipo,
        balance = saldo,
        currency = moneda,
        balancesByCurrency = mapOf(moneda to saldo),
        lastAdjustmentAt = ultimoAjuste,
        firstEventAt = primerEvento,
    )

    // ── La diferencia ────────────────────────────────────────────────────────

    /** El caso real del 21-sep: los rendimientos de Nu que nadie anotó — el saldo SUBE. */
    @Test
    fun `cuando el banco dice mas, la diferencia es positiva`() {
        val diferencia = diferenciaDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = 1_097_938L)
        assertEquals(745_856L, diferencia)
        assertTrue(hayAjusteQueAnotar(diferencia))
        assertEquals("+\$745.856", montoDelAjuste(diferencia!!, "COP"))
    }

    /** Una cuota de manejo que solo salió en el extracto: el saldo BAJA. */
    @Test
    fun `cuando el banco dice menos, la diferencia es negativa`() {
        val diferencia = diferenciaDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = 270_730L)
        assertEquals(-81_352L, diferencia)
        assertTrue(hayAjusteQueAnotar(diferencia))
        assertEquals("−\$81.352", montoDelAjuste(diferencia!!, "COP"))
    }

    /** Coincide: no se escribe nada. Un movimiento de $0 sería ruido y no movería ningún saldo. */
    @Test
    fun `cuando coincide no hay nada que anotar`() {
        val diferencia = diferenciaDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = 352_082L)
        assertEquals(0L, diferencia)
        assertFalse(hayAjusteQueAnotar(diferencia))
        assertTrue("no hay nada que anotar" in textoDelCuadre(352_082L, 352_082L, "COP")!!)
    }

    /**
     * **El campo vacío NO es cero.** Es la peor falla posible de esta pantalla: entrar, cuadrar una
     * cuenta y que las otras cuatro queden en cero porque nadie las tocó.
     */
    @Test
    fun `el campo vacio significa que no la cuadro, no que el banco diga cero`() {
        assertNull(diferenciaDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = null))
        assertFalse(hayAjusteQueAnotar(null))
        // Y cero escrito a mano SÍ es cero: una cuenta que quedó vacía se puede cuadrar.
        assertEquals(-352_082L, diferenciaDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = 0L))
    }

    /** El texto que se lee ANTES de confirmar dice las dos cifras y el movimiento que va a quedar. */
    @Test
    fun `el texto previo dice lo que cree Movi, lo que dice el banco y el ajuste`() {
        val texto = textoDelCuadre(saldoEnMovi = 352_082L, saldoDelBanco = 270_730L, moneda = "COP")
        assertEquals("Movi dice \$352.082, tú dices \$270.730: se va a anotar un ajuste de −\$81.352.", texto)
    }

    /** En dólares el símbolo cambia: «US$» — en Colombia «$20» se lee veinte pesos. */
    @Test
    fun `una cuenta en dolares se dice en dolares`() {
        val texto = textoDelCuadre(saldoEnMovi = 100L, saldoDelBanco = 250L, moneda = "USD")!!
        assertTrue("US\$100" in texto && "US\$250" in texto && "+US\$150" in texto, texto)
    }

    /**
     * Sin cifra escrita no se dice nada (vuelta de diseño 37): la ayuda «Escribe el saldo que te
     * muestra el banco hoy.» se repetía debajo de cada cuenta, y el rótulo del campo ya lo dice.
     */
    @Test
    fun `sin cifra escrita no hay texto que repetir`() {
        assertNull(textoDelCuadre(352_082L, null, "COP"))
        assertNull(textoDelCuadre(2_000_000L, null, "COP", esDeuda = true))
    }

    /** Una tarjeta se dice como su hoja «Ajustar saldo»: subir lo que se debe es un cargo. */
    @Test
    fun `en una tarjeta el ajuste se dice cargo o abono`() {
        assertEquals(
            "Movi dice \$2.000.000, tú dices \$2.100.000: se va a anotar un cargo de \$100.000.",
            textoDelCuadre(2_000_000L, 2_100_000L, "COP", esDeuda = true),
        )
        assertEquals(
            "Movi dice \$2.000.000, tú dices \$1.950.000: se va a anotar un abono de \$50.000.",
            textoDelCuadre(2_000_000L, 1_950_000L, "COP", esDeuda = true),
        )
        assertEquals("Coincide con Movi: no hay nada que anotar.", textoDelCuadre(2_000_000L, 2_000_000L, "COP", esDeuda = true))
    }

    // ── Qué cuentas entran ───────────────────────────────────────────────────

    /**
     * Dinero, Inversión, desde la vuelta de diseño 37 tarjetas y, desde la vuelta 59, préstamos: «Cuadre
     * de saldos no incluye los créditos, que es justo donde estaba el mayor descuadre».
     */
    @Test
    fun `se cuadran dinero, inversion, tarjetas y prestamos`() {
        val todas = listOf(
            cuenta(id = "a", tipo = AccountType.SAVINGS),
            cuenta(id = "b", tipo = AccountType.CASH),
            cuenta(id = "c", tipo = AccountType.CHECKING),
            cuenta(id = "d", tipo = AccountType.INVESTMENT),
            cuenta(id = "e", tipo = AccountType.CREDIT_CARD),
            cuenta(id = "f", tipo = AccountType.LOAN),
        )
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), cuentasParaCuadrar(todas).map { it.id })
        // `sePuedeCuadrar` (`:core`) no cambió: la usan el server y los avisos de «sin cuadrar», que
        // siguen sin tarjetas.
        assertEquals(listOf("a", "b", "c", "d"), todas.filter { sePuedeCuadrar(it) }.map { it.id })
        assertFalse(estaSinCuadrar(cuenta(id = "e", tipo = AccountType.CREDIT_CARD, primerEvento = AHORA - 400 * UN_DIA), AHORA))
    }

    /** Cada cuenta bajo su sección, en el orden Dinero · Inversión · Tarjetas · Préstamos, sin secciones vacías. */
    @Test
    fun `las cuentas van agrupadas por seccion`() {
        val todas = listOf(
            cuenta(id = "tarjeta", tipo = AccountType.CREDIT_CARD),
            cuenta(id = "fondo", tipo = AccountType.INVESTMENT),
            cuenta(id = "nu", tipo = AccountType.SAVINGS),
            cuenta(id = "efectivo", tipo = AccountType.CASH),
            cuenta(id = "prestamo", tipo = AccountType.LOAN),
        )
        val secciones = seccionesDelCuadre(cuentasParaCuadrar(todas))
        assertEquals(
            listOf(
                SeccionDelCuadre.DINERO to listOf("nu", "efectivo"),
                SeccionDelCuadre.INVERSION to listOf("fondo"),
                SeccionDelCuadre.TARJETAS to listOf("tarjeta"),
                SeccionDelCuadre.PRESTAMOS to listOf("prestamo"),
            ),
            secciones.map { (s, cuentas) -> s to cuentas.map { it.id } },
        )
        assertEquals(listOf("Dinero", "Inversión", "Tarjetas", "Préstamos"), secciones.map { it.first.titulo })
        // Sin inversiones, no hay sección vacía.
        assertEquals(
            listOf(SeccionDelCuadre.DINERO),
            seccionesDelCuadre(listOf(cuenta(id = "nu"))).map { it.first },
        )
        // La cuenta pedida arriba («Cuadrar efectivo») queda primera de la suya.
        assertEquals(
            listOf("efectivo", "nu"),
            seccionesDelCuadre(cuentasConLaPrimera(cuentasParaCuadrar(todas), "efectivo")).first().second.map { it.id },
        )
    }

    /** El rótulo del campo basta, y en una tarjeta pide la deuda. */
    @Test
    fun `el rotulo del campo de una tarjeta pide la deuda`() {
        assertEquals("Cuánto debes según el banco", rotuloDelCampoDelCuadre(cuenta(tipo = AccountType.CREDIT_CARD)))
        assertEquals("Lo que dice el banco", rotuloDelCampoDelCuadre(cuenta(tipo = AccountType.SAVINGS)))
        assertEquals("Lo que dice el banco", rotuloDelCampoDelCuadre(cuenta(tipo = AccountType.INVESTMENT)))
    }

    // ── Vuelta 59: los préstamos y el efectivo ───────────────────────────────

    /** Un préstamo pide la deuda, y su ajuste va por la ruta de los créditos (la hoja de su ficha). */
    @Test
    fun `un prestamo se cuadra como deuda y por el ajuste del credito`() {
        val prestamo = cuenta(id = "vehiculo", tipo = AccountType.LOAN, saldo = -980_000L)
        assertEquals(SeccionDelCuadre.PRESTAMOS, seccionDelCuadre(prestamo))
        assertEquals(CUANTO_DEBES_SEGUN_EL_BANCO, rotuloDelCampoDelCuadre(prestamo))
        assertTrue(seCuadraComoDeuda(prestamo))
        assertTrue(seCuadraComoPrestamo(prestamo))
        assertFalse(seCuadraComoPrestamo(cuenta(tipo = AccountType.CREDIT_CARD)))
        // El ajuste de un crédito no cruza monedas: un préstamo en dólares no se ofrece.
        assertNull(seccionDelCuadre(cuenta(id = "usd", tipo = AccountType.LOAN, moneda = "USD", saldo = 5_000L)))
    }

    /** Un solo signo: «Movi dice −$980.000» no; Movi no tiene su saldo, y el cargo lo explica. */
    @Test
    fun `el cuadre de un prestamo sin saldo no dice la cifra con el signo al reves`() {
        val texto = textoDelCuadre(saldoEnMovi = -980_000L, saldoDelBanco = 34_500_000L, moneda = "COP", esDeuda = true)!!
        assertEquals("Movi no tiene su saldo, tú dices \$34.500.000: se va a anotar un cargo de \$35.480.000.", texto)
        assertFalse("−" in texto)
    }

    /** «Cuadrar» desde Créditos: la sección del préstamo pedido va primera, no al final fuera de pantalla. */
    @Test
    fun `la seccion de la cuenta pedida va primera`() {
        val todas = listOf(
            cuenta(id = "nu"),
            cuenta(id = "tarjeta", tipo = AccountType.CREDIT_CARD),
            cuenta(id = "vehiculo", tipo = AccountType.LOAN, saldo = -980_000L),
        )
        val secciones = seccionesDelCuadre(cuentasConLaPrimera(todas, "vehiculo"), primeraId = "vehiculo")
        assertEquals(
            listOf(SeccionDelCuadre.PRESTAMOS, SeccionDelCuadre.DINERO, SeccionDelCuadre.TARJETAS),
            secciones.map { it.first },
        )
        // Sin cuenta pedida, el orden de siempre.
        assertEquals(SeccionDelCuadre.DINERO, seccionesDelCuadre(todas).first().first)
    }

    /** El ajuste de un préstamo se confirma con lo que va a quedar, sin signos. */
    @Test
    fun `la confirmacion del prestamo dice en cuanto queda la deuda y el ajuste`() {
        assertEquals(
            "Movi deja la deuda en \$35.830.084 (ajuste de \$36.810.084)",
            textoDelAjusteDelPrestamo(saldoEnMovi = -980_000L, saldoDelBanco = 35_830_084L),
        )
        assertEquals("¿Ajustar la deuda de Vehículo 4083?", preguntaDelAjusteDePrestamos(listOf("Vehículo 4083")))
        assertEquals("¿Ajustar la deuda de 2 préstamos?", preguntaDelAjusteDePrestamos(listOf("A", "B")))
    }

    /** El efectivo no tiene banco que lo diga. */
    @Test
    fun `el campo del efectivo dice lo que tienes`() {
        assertEquals(LO_QUE_TIENES, rotuloDelCampoDelCuadre(cuenta(tipo = AccountType.CASH)))
        assertEquals("Lo que tienes", LO_QUE_TIENES)
    }

    // ── Desde cuándo nadie la mira ───────────────────────────────────────────

    /**
     * El umbral son 45 días: un período del dueño (mensual, corte 25) más medio de gracia. A los
     * 44 días todavía no se dice nada — quien cuadra una vez por período nunca ve el aviso.
     */
    @Test
    fun `a los 44 dias todavia no se avisa, a los 45 si`() {
        val recien = cuenta(ultimoAjuste = AHORA - 44 * UN_DIA)
        val vieja = cuenta(ultimoAjuste = AHORA - 45 * UN_DIA)
        assertFalse(estaSinCuadrar(recien, AHORA))
        assertTrue(estaSinCuadrar(vieja, AHORA))
    }

    /** Recién cuadrada: **nunca** se le reclama una cuenta que acaba de cuadrar. */
    @Test
    fun `la cuenta cuadrada hoy no aparece en el aviso`() {
        val cuentas = listOf(cuenta(ultimoAjuste = AHORA), cuenta(id = "vieja", nombre = "Fiducuenta", ultimoAjuste = AHORA - 90 * UN_DIA))
        assertEquals(listOf("vieja"), cuentasSinCuadrar(cuentas, AHORA).map { it.id })
    }

    /**
     * Sin ningún ajuste, la cuenta se mide desde su movimiento más viejo — su edad dentro de Movi.
     * Sin esta mitad, una cuenta creada hoy aparecería atrasada el mismo día en que nació.
     */
    @Test
    fun `una cuenta nueva sin ajustes no esta atrasada, una vieja si`() {
        val nueva = cuenta(primerEvento = AHORA - 3 * UN_DIA)
        val vieja = cuenta(primerEvento = AHORA - 200 * UN_DIA)
        assertFalse(estaSinCuadrar(nueva, AHORA))
        assertTrue(estaSinCuadrar(vieja, AHORA))
        assertEquals(3L, diasSinCuadrar(nueva, AHORA))
    }

    /** Sin movimientos no hay contra qué medir: no se avisa nada. */
    @Test
    fun `una cuenta sin movimientos no dispara el aviso`() {
        val vacia = cuenta()
        assertNull(diasSinCuadrar(vacia, AHORA))
        assertFalse(estaSinCuadrar(vacia, AHORA))
    }

    /** Una deuda nunca entra al aviso: no se cuadra desde acá. */
    @Test
    fun `una tarjeta vieja no entra en el aviso`() {
        val tarjeta = cuenta(tipo = AccountType.CREDIT_CARD, primerEvento = AHORA - 300 * UN_DIA)
        assertFalse(estaSinCuadrar(tarjeta, AHORA))
    }

    /** El aviso: una línea, con el nombre cuando es una sola, y sin cifras inventadas. */
    @Test
    fun `el aviso nombra la cuenta cuando es una sola y cuenta cuando son varias`() {
        assertNull(textoDelAvisoDeCuadre(emptyList()))
        // Vuelta 31: con una sola, su nombre y la MISMA frase que dice su detalle.
        assertEquals(
            "Nu · Nunca la has cuadrado",
            textoDelAvisoDeCuadre(listOf(cuenta(primerEvento = AHORA - 60 * UN_DIA))),
        )
        // 60 días antes del 20-sep: el 22 de julio. La versión con nombre, corta y con la fecha.
        assertEquals(
            "Nu · última el 22 jul",
            textoDelAvisoDeCuadre(listOf(cuenta(ultimoAjuste = AHORA - 60 * UN_DIA)), AHORA),
        )
        assertEquals(
            "2 cuentas nunca se han cuadrado",
            textoDelAvisoDeCuadre(listOf(cuenta(), cuenta(id = "b", nombre = "Fiducuenta"))),
        )
        assertEquals(
            "2 cuentas llevan más de un mes sin cuadrar",
            textoDelAvisoDeCuadre(listOf(cuenta(ultimoAjuste = AHORA - 60 * UN_DIA), cuenta(id = "b", nombre = "Fiducuenta"))),
        )
    }

    // ── Cómo se lee la fecha del último cuadre ───────────────────────────────

    @Test
    fun `cada fila dice cuando se cuadro, o que nunca se cuadro`() {
        val hoy = LocalDate(2026, 9, 20)
        assertEquals("Nunca la has cuadrado", textoDelUltimoCuadre(cuenta(primerEvento = AHORA), hoy, AHORA))
        assertEquals("Sin movimientos todavía", textoDelUltimoCuadre(cuenta(), hoy, AHORA))
        // 1-sep-2026 12:00 en Bogotá (la zona de la app) — se escribe como lo escribe Movimientos.
        val primeroDeSeptiembre = 1_788_282_000_000L
        assertEquals("Cuadrada el 1 sep", textoDelUltimoCuadre(cuenta(ultimoAjuste = primeroDeSeptiembre), hoy, AHORA))
        assertEquals("Cuadrada hoy", textoDelUltimoCuadre(cuenta(ultimoAjuste = AHORA), hoy, AHORA))
        assertEquals("Cuadrada ayer", textoDelUltimoCuadre(cuenta(ultimoAjuste = AHORA - UN_DIA), hoy, AHORA))
        // 1-ago-2026: 50 días antes, pasó el plazo. Sin perder la fecha.
        val primeroDeAgosto = 1_785_603_600_000L
        assertEquals(
            "Hace más de un mes que no la cuadras · última el 1 ago",
            textoDelUltimoCuadre(cuenta(ultimoAjuste = primeroDeAgosto), hoy, AHORA),
        )
        // 45 días justos ya es «hace más de un mes»; 44, todavía con su fecha.
        assertEquals("Cuadrada el 7 ago", textoDelUltimoCuadre(cuenta(ultimoAjuste = AHORA - 45 * UN_DIA + UN_DIA), hoy, AHORA))
        assertTrue(textoDelUltimoCuadre(cuenta(ultimoAjuste = AHORA - 45 * UN_DIA), hoy, AHORA).startsWith("Hace más de un mes"))
        // De otro año, con el año.
        assertEquals(
            "Hace más de un mes que no la cuadras · última el 21 sep 2025",
            textoDelUltimoCuadre(cuenta(ultimoAjuste = AHORA - 364 * UN_DIA), hoy, AHORA),
        )
    }

    /** Vuelta 31: la frase de una cuenta es una sola, la de su detalle y la del aviso de Patrimonio. */
    @Test
    fun `el aviso y el detalle dicen la misma frase de la misma cuenta`() {
        val hoy = LocalDate(2026, 9, 20)
        listOf(
            cuenta(nombre = "Bancolombia Ahorros", primerEvento = AHORA - 60 * UN_DIA),
            cuenta(nombre = "Bancolombia Ahorros", ultimoAjuste = AHORA - 60 * UN_DIA),
        ).forEach { c ->
            assertEquals(listOf(c), cuentasSinCuadrar(listOf(c), AHORA))
            val delDetalle = textoDelUltimoCuadre(c, hoy, AHORA)
            val conNombre = textoDelAvisoDeCuadre(listOf(c), AHORA)!!
            // La versión con nombre dice lo mismo, más corta: su tramo después del nombre está en la del detalle.
            assertTrue(conNombre.startsWith("Bancolombia Ahorros · "), conNombre)
            assertTrue(conNombre.removePrefix("Bancolombia Ahorros · ") in delDetalle, "«$conNombre» contra «$delDetalle»")
        }
    }

    private fun propuesta(banco: Long, movi: Long) = com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso(
        avisoId = "sms_1",
        cuentaId = "acc-ahorros",
        cuentaNombre = "Bancolombia Ahorros 8133",
        origen = "85540",
        saldoDelBanco = banco,
        saldoEnMovi = movi,
        momento = AHORA,
    )

    @Test
    fun `la propuesta del aviso dice las dos cifras y el ajuste con su signo`() {
        assertEquals(
            "El banco dice \$138.407 y Movi \$138.361 → ajustar +\$46",
            textoDeLaPropuestaPorAviso(propuesta(138_407L, 138_361L)),
        )
        assertTrue(textoDeLaPropuestaPorAviso(propuesta(100_000L, 100_500L)).endsWith("ajustar −\$500"))
    }

    @Test
    fun `la fila del Inicio nombra la cuenta cuando es una sola`() {
        assertNull(textoDeLasPropuestasPorAviso(emptyList()))
        assertEquals(
            "El saldo de Bancolombia Ahorros 8133 no coincide con el del banco",
            textoDeLasPropuestasPorAviso(listOf(propuesta(1L, 2L))),
        )
        assertEquals(
            "2 cuentas no coinciden con el saldo del banco",
            textoDeLasPropuestasPorAviso(listOf(propuesta(1L, 2L), propuesta(3L, 4L))),
        )
    }

    @Test
    fun `de cuando es el saldo que se compara`() {
        val hoy = LocalDate(2026, 9, 20)
        assertEquals("Según el aviso de hoy", textoDelMomentoDelAviso(AHORA, hoy))
        assertEquals("Según el aviso de ayer", textoDelMomentoDelAviso(AHORA - UN_DIA, hoy))
    }
}
