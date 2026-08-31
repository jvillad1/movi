package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **El criterio de qué cuentas se ofrecen, sobre las cuentas reales del dueño.**
 *
 * No son cuentas inventadas: son las suyas, con sus nombres y sus cifras. La que abrió el caso es
 * «Vehículo 4083 · $177.200.000», que aparecía en el selector de «¿de dónde sale este gasto?»
 * cuando esa cifra es lo que DEBE.
 *
 * Lo que estas pruebas fijan es la decisión, no la implementación: de la tarjeta sí sale un gasto,
 * del crédito ya desembolsado no, y **nada de esto borra cuentas** — lo excluido sigue accesible.
 */
class CuentasQueMuevenPlataTest {

    private val efectivo = Account("a0", "Efectivo", AccountType.CASH, 200_000)
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("c1", "Nu", AccountType.CREDIT_CARD, 1_240_000)
    private val amex = Account("c2", "AMEX 9208", AccountType.CREDIT_CARD, 19_818_701)
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)
    private val hipotecario = Account("l2", "Hipotecario 7712", AccountType.LOAN, 257_000_000)
    private val skandia = Account("i1", "Pensión voluntaria Skandia", AccountType.INVESTMENT, 42_000_000)

    private val todas = listOf(efectivo, ahorros, nu, amex, carro, hipotecario, skandia)

    private fun nombres(cuentas: List<Account>) = cuentas.map { it.name }

    // ── Gasto: el pedido, literal ────────────────────────────────────────────────

    @Test
    fun de_un_credito_ya_desembolsado_no_sale_un_gasto() {
        assertFalse(sirvePara(carro, UsoDeCuenta.ORIGEN_DE_GASTO))
        assertFalse(sirvePara(hipotecario, UsoDeCuenta.ORIGEN_DE_GASTO))
    }

    @Test
    fun de_una_tarjeta_de_credito_SI_sale_un_gasto() {
        // Comprar con la Nu o con la AMEX es un gasto real que además sube la deuda. Este es el
        // caso que hace que la regla no pueda ser «el grupo DEUDA no aparece».
        assertTrue(sirvePara(nu, UsoDeCuenta.ORIGEN_DE_GASTO))
        assertTrue(sirvePara(amex, UsoDeCuenta.ORIGEN_DE_GASTO))
    }

    @Test
    fun de_la_inversion_no_sale_un_gasto() {
        // De la pensión voluntaria no se paga un almuerzo: sacar plata de ahí es un traspaso.
        assertFalse(sirvePara(skandia, UsoDeCuenta.ORIGEN_DE_GASTO))
    }

    @Test
    fun el_selector_de_un_gasto_ofrece_efectivo_banco_y_tarjetas() {
        val partido = cuentasPara(todas, UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(listOf("Efectivo", "Bancolombia Ahorros", "Nu", "AMEX 9208"), nombres(partido.principales))
        assertEquals(
            listOf("Vehículo 4083", "Hipotecario 7712", "Pensión voluntaria Skandia"),
            nombres(partido.otras),
        )
    }

    // ── Ingreso: la inversión sí, la tarjeta no ──────────────────────────────────

    @Test
    fun un_ingreso_puede_entrar_a_la_inversion_pero_no_a_una_tarjeta() {
        // Un rendimiento de Skandia es un ingreso legítimo. Plata que «entra» a una tarjeta es un
        // pago del extracto, y eso es la pestaña «Cuota».
        assertTrue(sirvePara(skandia, UsoDeCuenta.DESTINO_DE_INGRESO))
        assertFalse(sirvePara(nu, UsoDeCuenta.DESTINO_DE_INGRESO))
        assertFalse(sirvePara(carro, UsoDeCuenta.DESTINO_DE_INGRESO))
    }

    @Test
    fun gasto_e_ingreso_no_ofrecen_lo_mismo() {
        // La prueba de que la regla depende del uso y no es una sola lista disfrazada de dos.
        val gasto = nombres(cuentasPara(todas, UsoDeCuenta.ORIGEN_DE_GASTO).principales)
        val ingreso = nombres(cuentasPara(todas, UsoDeCuenta.DESTINO_DE_INGRESO).principales)
        assertTrue("Nu" in gasto && "Nu" !in ingreso)
        assertTrue("Pensión voluntaria Skandia" in ingreso && "Pensión voluntaria Skandia" !in gasto)
    }

    // ── Traspaso y cuota: lo que ya existía, ahora dicho una sola vez ────────────

    @Test
    fun un_traspaso_admite_creditos_e_inversion_pero_no_tarjetas() {
        val partido = cuentasPara(todas, UsoDeCuenta.PUNTA_DE_TRASPASO)
        assertTrue("Vehículo 4083" in nombres(partido.principales))
        assertTrue("Pensión voluntaria Skandia" in nombres(partido.principales))
        assertEquals(listOf("Nu", "AMEX 9208"), nombres(partido.otras))
    }

    @Test
    fun una_cuota_sale_de_plata_propia_y_paga_una_deuda() {
        assertEquals(
            listOf("Efectivo", "Bancolombia Ahorros", "Pensión voluntaria Skandia"),
            nombres(cuentasPara(todas, UsoDeCuenta.DINERO_PROPIO).principales),
        )
        assertEquals(
            listOf("Nu", "AMEX 9208", "Vehículo 4083", "Hipotecario 7712"),
            nombres(cuentasPara(todas, UsoDeCuenta.DEUDA_QUE_SE_PAGA).principales),
        )
    }

    // ── No es un filtro duro ────────────────────────────────────────────────────

    @Test
    fun nada_se_pierde_al_partir_la_lista() {
        // La afirmación que sostiene el «Ver todas»: partir no borra. Se vale para los cinco usos,
        // porque el día que alguien agregue el sexto esto tiene que seguir siendo cierto.
        UsoDeCuenta.entries.forEach { uso ->
            assertEquals(nombres(todas).toSet(), nombres(cuentasPara(todas, uso).todas).toSet(), "uso $uso")
        }
    }

    @Test
    fun la_cuenta_ya_elegida_se_queda_a_la_vista_aunque_no_sirva() {
        // El caso de editar: la regla vieja apuntaba al hipotecario. Al abrir el selector tiene que
        // seguir viéndose —marcada— y no evaporarse.
        val partido = cuentasPara(todas, UsoDeCuenta.ORIGEN_DE_GASTO, conservar = hipotecario.id)
        assertTrue("Hipotecario 7712" in nombres(partido.principales))
        assertFalse("Hipotecario 7712" in nombres(partido.otras))
        // Y no arrastra a sus compañeras: el vehículo sigue detrás del «Ver todas».
        assertTrue("Vehículo 4083" in nombres(partido.otras))
    }

    @Test
    fun conservar_una_cuenta_que_ya_servia_no_la_mueve_de_lugar() {
        // Sin esto, `conservar` podría reordenar la lista principal y mover la fila bajo el dedo.
        val partido = cuentasPara(todas, UsoDeCuenta.ORIGEN_DE_GASTO, conservar = amex.id)
        assertEquals(listOf("Efectivo", "Bancolombia Ahorros", "Nu", "AMEX 9208"), nombres(partido.principales))
    }

    @Test
    fun sin_cuentas_no_hay_ni_principales_ni_otras() {
        val partido = cuentasPara(emptyList(), UsoDeCuenta.ORIGEN_DE_GASTO)
        assertTrue(partido.vacio)
        assertFalse(partido.hayOtras)
    }

    @Test
    fun con_solo_cuentas_que_sirven_no_hay_ver_todas() {
        // Quien no tiene créditos ni inversión no ve el «Ver todas» en ninguna parte: la pantalla
        // se ve exactamente como antes de esta rama.
        val partido = cuentasPara(listOf(efectivo, ahorros, nu), UsoDeCuenta.ORIGEN_DE_GASTO)
        assertFalse(partido.hayOtras)
        assertEquals(3, partido.principales.size)
    }
}
