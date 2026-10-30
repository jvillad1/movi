package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CuentasDeCadaUso
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveDeCategoriaParaCuenta
import com.jvillada.movi.shared.model.claveDeNombreParaCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # La cuenta que propone Agregar (vuelta 43)
 *
 * El día del dueño: *«Las cuentas por defecto son siempre "Más usada" (Nu, tarjeta de crédito). El
 * arriendo, el mercado y el efectivo arrancaron todos en Nu.»* Cada caso del orden de
 * [resolverCuentaDelMovimiento], y lo que dice el renglón bajo la cuenta ([avisoDeCuenta]).
 */
class LaCuentaQueProponeAgregarTest {

    private fun cuenta(id: String, name: String, type: AccountType) = Account(id = id, name = name, type = type, balance = 0L)

    private val bancolombia = cuenta("acc_b", "Bancolombia", AccountType.SAVINGS)
    private val nu = cuenta("acc_nu", "Nu", AccountType.CREDIT_CARD)
    private val efectivo = cuenta("acc_e", "Efectivo", AccountType.CASH)
    private val cuentas = listOf(bancolombia, nu, efectivo)

    /** El mercado sale de Bancolombia; el Rappi, de la tarjeta. */
    private val usos = CuentasDeCadaUso(
        porCategoria = mapOf(claveDeCategoriaParaCuenta(TransactionType.EXPENSE, "Mercado")!! to bancolombia.id),
        porNombre = mapOf(claveDeNombreParaCuenta(TransactionType.EXPENSE, "Rappi")!! to nu.id),
    )

    private fun proponer(
        categoria: String = "Comida",
        nombre: String = "",
        modoPago: Boolean = false,
        deLaRegla: String? = null,
        contexto: String? = null,
        ultima: String? = null,
        masUsada: String? = nu.id,
    ) = resolverCuentaDelMovimiento(
        cuentas = cuentas, tipo = TransactionType.EXPENSE, categoria = categoria, nombre = nombre, usos = usos,
        contexto = contexto, modoPago = modoPago, deLaRegla = deLaRegla, ultima = ultima, masUsada = masUsada,
    )

    private fun rotulo(elegida: CuentaElegida) = avisoDeCuenta(elegida.origen, cuentas.size, elegida.para)

    // ── 1. Modo pago: la cuenta de la regla ──────────────────────────────────

    @Test
    fun `en modo pago gana la cuenta de la regla`() {
        val elegida = proponer(categoria = "Mercado", modoPago = true, deLaRegla = efectivo.id)
        assertEquals(efectivo.id, elegida.id)
        assertEquals(OrigenCuenta.REGLA, elegida.origen)
        assertEquals("La de la regla", rotulo(elegida))
    }

    @Test
    fun `en modo pago sin cuenta en la regla propone la misma cuenta que un gasto`() {
        // Vuelta 72: ya no pide la cuenta cada mes; propone con la lógica de Gasto y dice por qué.
        val elegida = proponer(categoria = "Mercado", nombre = "Arriendo", modoPago = true)
        assertEquals(bancolombia.id, elegida.id, "la que usa para Mercado")
        assertEquals(OrigenCuenta.POR_CATEGORIA, elegida.origen)
        assertEquals("La que usas para Mercado", rotulo(elegida))
        // Sin nada que diga qué se anota, la más usada, igual que en un gasto.
        val sinUso = proponer(categoria = "Carro", nombre = "Carro", modoPago = true)
        assertEquals(nu.id, sinUso.id)
        assertEquals(OrigenCuenta.MAS_USADA, sinUso.origen)
    }

    @Test
    fun `en modo pago con una sola cuenta no hay nada que preguntar`() {
        val sola = resolverCuentaDelMovimiento(
            cuentas = listOf(bancolombia), tipo = TransactionType.EXPENSE, categoria = "Otros", nombre = "Arriendo", modoPago = true,
        )
        assertEquals(bancolombia.id, sola.id)
    }

    // ── 2. Lo que se anota: efectivo, el nombre, la categoría ────────────────

    @Test
    fun `un gasto en Efectivo va a la cuenta de efectivo`() {
        val porCategoria = proponer(categoria = "Efectivo")
        assertEquals(efectivo.id, porCategoria.id)
        assertEquals(OrigenCuenta.EFECTIVO, porCategoria.origen)
        assertEquals("Es en efectivo", rotulo(porCategoria))
        assertEquals(efectivo.id, proponer(categoria = "Comida", nombre = "Almuerzo en efectivo").id, "o el título")
    }

    @Test
    fun `la ultima cuenta con ese nombre`() {
        val elegida = proponer(categoria = "Comida", nombre = "Rappi")
        assertEquals(nu.id, elegida.id)
        assertEquals(OrigenCuenta.POR_NOMBRE, elegida.origen)
        assertEquals("La que usas para Rappi", rotulo(elegida))
    }

    @Test
    fun `la ultima cuenta con esa categoria`() {
        val elegida = proponer(categoria = "Mercado", ultima = efectivo.id)
        assertEquals(bancolombia.id, elegida.id, "le gana a la última usada y a la más usada")
        assertEquals(OrigenCuenta.POR_CATEGORIA, elegida.origen)
        assertEquals("La que usas para Mercado", rotulo(elegida))
    }

    @Test
    fun `una cuenta recordada que ya no esta no se propone`() {
        val sinBancolombia = resolverCuentaDelMovimiento(
            cuentas = listOf(nu, efectivo), tipo = TransactionType.EXPENSE, categoria = "Mercado", nombre = "", usos = usos, masUsada = nu.id,
        )
        assertEquals(OrigenCuenta.MAS_USADA, sinBancolombia.origen)
    }

    // ── 3. La más usada, solo si no hay nada de lo anterior ──────────────────

    @Test
    fun `sin nada de lo anterior, la mas usada`() {
        val elegida = proponer(categoria = "Comida")
        assertEquals(nu.id, elegida.id)
        assertEquals(OrigenCuenta.MAS_USADA, elegida.origen)
        assertEquals("Más usada", rotulo(elegida))
    }

    @Test
    fun `Otros no dice nada de la costumbre`() {
        val conOtros = usos.copy(porCategoria = usos.porCategoria + ("EXPENSE:otros" to efectivo.id))
        val elegida = resolverCuentaDelMovimiento(
            cuentas = cuentas, tipo = TransactionType.EXPENSE, categoria = "Otros", nombre = "", usos = conOtros, masUsada = nu.id,
        )
        assertEquals(OrigenCuenta.MAS_USADA, elegida.origen)
        assertNull(claveDeCategoriaParaCuenta(TransactionType.EXPENSE, "Otros"))
    }

    @Test
    fun `el detalle de una cuenta sigue mandando fuera del modo pago`() {
        val elegida = proponer(categoria = "Mercado", contexto = efectivo.id)
        assertEquals(efectivo.id, elegida.id)
        assertEquals(OrigenCuenta.CONTEXTO, elegida.origen)
        assertNull(rotulo(elegida))
    }

    @Test
    fun `un ingreso no hereda la cuenta de un gasto`() {
        val elegida = resolverCuentaDelMovimiento(
            cuentas = cuentas, tipo = TransactionType.INCOME, categoria = "Mercado", nombre = "", usos = usos, masUsada = efectivo.id,
        )
        assertEquals(OrigenCuenta.MAS_USADA, elegida.origen)
    }
}
