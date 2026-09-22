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
        assertTrue("no hay nada que anotar" in textoDelCuadre(352_082L, 352_082L, "COP"))
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
        val texto = textoDelCuadre(saldoEnMovi = 100L, saldoDelBanco = 250L, moneda = "USD")
        assertTrue("US\$100" in texto && "US\$250" in texto && "+US\$150" in texto, texto)
    }

    /** Sin cifra escrita, el texto pide la cifra en vez de afirmar nada sobre la plata. */
    @Test
    fun `sin cifra escrita el texto solo pide el saldo del banco`() {
        assertEquals("Escribe el saldo que te muestra el banco hoy.", textoDelCuadre(352_082L, null, "COP"))
    }

    // ── Qué cuentas entran ───────────────────────────────────────────────────

    /**
     * Dinero e Inversión sí; tarjetas y préstamos no. La razón completa está en [sePuedeCuadrar]:
     * el banco no muestra UN número para una tarjeta, y un préstamo se cuadra en Créditos, donde
     * al lado se ven la cuota y los intereses que explican la diferencia.
     */
    @Test
    fun `solo se cuadran las cuentas de dinero e inversion`() {
        val todas = listOf(
            cuenta(id = "a", tipo = AccountType.SAVINGS),
            cuenta(id = "b", tipo = AccountType.CASH),
            cuenta(id = "c", tipo = AccountType.CHECKING),
            cuenta(id = "d", tipo = AccountType.INVESTMENT),
            cuenta(id = "e", tipo = AccountType.CREDIT_CARD),
            cuenta(id = "f", tipo = AccountType.LOAN),
        )
        assertEquals(listOf("a", "b", "c", "d"), cuentasParaCuadrar(todas).map { it.id })
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
        assertEquals(
            "Hace más de un mes que no cuadras Nu",
            textoDelAvisoDeCuadre(listOf(cuenta())),
        )
        assertEquals(
            "2 cuentas llevan más de un mes sin cuadrar",
            textoDelAvisoDeCuadre(listOf(cuenta(), cuenta(id = "b", nombre = "Fiducuenta"))),
        )
    }

    // ── Cómo se lee la fecha del último cuadre ───────────────────────────────

    @Test
    fun `cada fila dice cuando se cuadro, o que nunca se cuadro`() {
        val hoy = LocalDate(2026, 9, 20)
        assertEquals("Nunca la has cuadrado", textoDelUltimoCuadre(cuenta(primerEvento = AHORA), hoy))
        assertEquals("Sin movimientos todavía", textoDelUltimoCuadre(cuenta(), hoy))
        // 1-ago-2026 12:00 en Bogotá (la zona de la app) — se escribe como lo escribe Movimientos.
        val primeroDeAgosto = 1_785_603_600_000L
        assertEquals("Cuadrada el 1 de agosto", textoDelUltimoCuadre(cuenta(ultimoAjuste = primeroDeAgosto), hoy))
    }
}
