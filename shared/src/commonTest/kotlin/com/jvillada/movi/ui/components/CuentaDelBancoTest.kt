package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # A qué cuenta va lo que llegó del banco
 *
 * Las dos pantallas que confirman lo que la app leyó sola —el SMS y el extracto— resolvían esto
 * cada una por su lado, con la misma cadena copiada, y las dos terminaban en
 * `accounts.firstOrNull()`. Estas pruebas fijan **la decisión**: qué se resuelve, qué se rotula
 * como adivinado, y —lo que no existía— **qué no se resuelve**.
 *
 * Las cuentas son las del dueño, con sus nombres y sus cifras: la que abrió el caso es «Vehículo
 * 4083 · $177.200.000», que es lo que DEBE por un carro y era la primera del abecedario.
 */
class CuentaDelBancoTest {

    private val amex = Account("c2", "AMEX 9208", AccountType.CREDIT_CARD, 19_818_701)
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val efectivo = Account("a0", "Efectivo", AccountType.CASH, 200_000)
    private val hipotecario = Account("l2", "Hipotecario 7712", AccountType.LOAN, 257_000_000)
    private val nu = Account("c1", "Nu", AccountType.CREDIT_CARD, 1_240_000)
    private val skandia = Account(
        "i1", "Pensión voluntaria Skandia", AccountType.INVESTMENT, 106_000_000,
        condicionadaA = "Vivienda",
    )
    private val carro = Account("l1", "Vehículo 4083", AccountType.LOAN, 177_200_000)

    /** Ordenadas por nombre, como las devuelven `GET /api/accounts` y el `selectAll` de SQLDelight. */
    private val todas = listOf(amex, ahorros, efectivo, hipotecario, nu, skandia, carro)

    // ── El defecto, y su ausencia ───────────────────────────────────────────────

    /**
     * **El caso que abrió esto.** Un banco que él no tiene anotado con ese nombre, y una lista
     * donde la primera del abecedario es una tarjeta o un crédito. Con `firstOrNull()` el
     * movimiento se anotaba contra la AMEX (o, con otro orden, contra el «Vehículo 4083»); ahora
     * cae en la primera cuenta de banco, que es una suposición **y viene rotulada como tal**.
     */
    @Test
    fun sin_coincidencia_de_nombre_cae_en_una_cuenta_de_banco_y_lo_dice() {
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Falabella")

        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
        assertEquals("La puso Movi", avisoDeLaCuentaDelBanco(r.origen))
    }

    /**
     * **Lo que antes no podía pasar: no resolver nada.** Quien solo tiene créditos abría la
     * pantalla con el crédito ya puesto como origen del gasto. Ahora no hay cuenta, el botón
     * sigue apagado —eso ya estaba— y la pantalla pide que la elija.
     */
    @Test
    fun sin_ninguna_cuenta_que_sirva_no_se_inventa_un_destino() {
        val r = resolverCuentaDelBanco(
            listOf(carro, hipotecario), UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Bancolombia",
        )

        assertNull(r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.NINGUNA, r.origen)
        assertEquals("Elígela tú", avisoDeLaCuentaDelBanco(r.origen))
    }

    @Test
    fun sin_cuentas_tampoco() {
        assertNull(resolverCuentaDelBanco(emptyList(), UsoDeCuenta.ORIGEN_DE_GASTO, "Nu").cuenta)
    }

    // ── El nombre del banco ─────────────────────────────────────────────────────

    @Test
    fun la_cuenta_que_se_llama_como_el_banco_gana() {
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Nu")

        assertEquals(nu, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
        // El nombre de la cuenta ya explica por qué está ahí: no hay nada que confesar.
        assertNull(avisoDeLaCuentaDelBanco(r.origen))
    }

    /**
     * **El mismo accidente por otra puerta.** Quien tiene un solo producto de Davivienda y es el
     * crédito hipotecario veía ese crédito elegido *por coincidencia de nombre*, sin pasar por
     * ningún respaldo. Por eso el criterio pesa también sobre este paso.
     */
    @Test
    fun el_nombre_no_alcanza_para_elegir_un_credito() {
        val hipotecarioDavivienda = Account("l3", "Davivienda Hipotecario", AccountType.LOAN, 250_000_000)
        val cuentas = listOf(ahorros, hipotecarioDavivienda)

        val r = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Davivienda")

        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    @Test
    fun un_banco_en_blanco_no_coincide_con_todo() {
        // `"lo que sea".contains("")` es cierto: sin esta guarda, un SMS sin banco elegiría la
        // primera cuenta de la lista disfrazada de coincidencia — la AMEX, acá.
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "")

        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    // ── El uso manda ────────────────────────────────────────────────────────────

    @Test
    fun un_ingreso_del_banco_no_entra_a_la_tarjeta_que_se_llama_igual() {
        // Plata que «entra» a una tarjeta es un pago del extracto, y eso es la pestaña «Cuota».
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.DESTINO_DE_INGRESO, banco = "Nu")

        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, r.origen)
    }

    @Test
    fun un_gasto_del_banco_si_sale_de_la_tarjeta_que_se_llama_igual() {
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "AMEX")

        assertEquals(amex, r.cuenta)
    }

    @Test
    fun el_extracto_de_la_pension_voluntaria_es_de_la_pension_voluntaria() {
        // Con el uso del gasto, Skandia queda excluida (está condicionada) y el extracto habría
        // caído en la cuenta de ahorros. `CUENTA_DEL_EXTRACTO` es el uso que existe para esto.
        val r = resolverCuentaDelBanco(todas, UsoDeCuenta.CUENTA_DEL_EXTRACTO, banco = "Skandia")

        assertEquals(skandia, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
    }

    // ── Lo que él eligió con el dedo ────────────────────────────────────────────

    @Test
    fun lo_que_eligio_a_mano_le_gana_a_la_coincidencia_de_nombre() {
        val r = resolverCuentaDelBanco(
            todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Nu", elegidaAMano = ahorros.id,
        )

        assertEquals(ahorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.A_MANO, r.origen)
        assertNull(avisoDeLaCuentaDelBanco(r.origen))
    }

    /**
     * **Una elección suya no se revoca.** Si la sacó del «Ver todas» —donde vive todo lo que el
     * criterio no propone—, esta función no tiene por qué devolvérsela cambiada: la app no
     * propone esas cuentas, pero tampoco las prohíbe.
     */
    @Test
    fun lo_que_eligio_a_mano_vale_aunque_el_criterio_no_lo_proponga() {
        val r = resolverCuentaDelBanco(
            todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Bancolombia", elegidaAMano = carro.id,
        )

        assertEquals(carro, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.A_MANO, r.origen)
    }

    @Test
    fun una_cuenta_elegida_que_ya_no_existe_no_bloquea_la_pantalla() {
        // Se borró la cuenta, o la lista es de otro usuario del mismo teléfono: se vuelve a
        // resolver en vez de quedar con un id colgado y la pantalla sin cuenta.
        val r = resolverCuentaDelBanco(
            todas, UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Nu", elegidaAMano = "acc_borrada",
        )

        assertEquals(nu, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, r.origen)
    }

    @Test
    fun el_efectivo_no_es_el_respaldo_de_algo_que_llego_del_banco() {
        // Es la única razón por la que el paso 3 pregunta por el tipo y no toma la primera
        // candidata: con solo efectivo y una tarjeta, «la primera que sirve» sería el efectivo.
        val r = resolverCuentaDelBanco(
            listOf(efectivo, nu), UsoDeCuenta.ORIGEN_DE_GASTO, banco = "Falabella",
        )

        assertNull(r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.NINGUNA, r.origen)
    }
}
