package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

/**
 * «¿De dónde sale esta cuota?» — la pregunta que Movi no sabía hacer.
 *
 * De los nueve créditos del dueño, **tres no se pagan con su sueldo**: las dos hipotecas de
 * Davibank las cubre su pensión voluntaria de Skandia ($11.761.122/mes entre las dos) y el
 * crédito de Cotrafa —que está a su nombre— lo paga su esposa ($1.371.000/mes). Cargarlos sin
 * poder decirlo habría metido **~$13,1 millones mensuales** de gasto inventado en su flujo, y le
 * habría reclamado cuotas vencidas que nadie le debe.
 *
 * Los números de esta suite son los suyos, de `investments/`, y no redondeos inventados: si
 * alguna regla se rompe, el mensaje del test dice cuánta plata real se movió.
 */
class QuienPagaLaCuotaTest {

    private fun hipoteca1254(paga: String?) = CreditTerms(
        accountId = "acc-1254",
        bank = "Davibank",
        principal = 784_000_000,
        rateEa = 10.99,
        termMonths = 180,
        installment = 9_147_408,
        dayOfMonth = 12,
        startDate = "2025-06-12",
        paidBy = paga,
    )

    @Test
    fun por_defecto_la_paga_el_dueno() {
        // Lo normal es que la cuota salga de su cuenta: el DEFAULT no puede significar otra cosa,
        // o los créditos que ya tiene cargados cambiarían de comportamiento al desplegar esto.
        // Se construye SIN pasar el parámetro —la versión anterior pasaba `null` explícito, o sea
        // no ejercía el default y no probaba nada.
        val sinPasarlo = CreditTerms(
            accountId = "acc-x",
            bank = "Davibank",
            principal = 1,
            rateEa = 1.0,
            termMonths = 1,
            installment = 1,
            dayOfMonth = 1,
            startDate = "2026-01-01",
        )
        assertNull(sinPasarlo.paidBy)
        assertFalse(sinPasarlo.payrollDeduction)
    }

    @Test
    fun la_cuota_que_paga_otro_no_es_gasto_ni_ingreso_del_mes() {
        // Por NOMBRE y no por tipo de cuenta: el movimiento vive en la cuenta LOAN, que ya está
        // excluida, pero dejarlo implícito haría que la exclusión dependiera de dónde quedó
        // guardado. Misma disciplina que el descuento de nómina.
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, THIRD_PARTY_PAYMENT_CATEGORY))
        assertFalse(isCashFlow(AccountType.CHECKING, TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY))
        assertFalse(isCashFlow(AccountType.LOAN, TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY))
    }

    @Test
    fun esta_protegida_como_las_otras_cinco() {
        // Si dejara de estarlo, la pantalla de Categorías la ofrecería para renombrar y el mes
        // entero cambiaría de cifra.
        assertTrue(isReservedCategory(THIRD_PARTY_PAYMENT_CATEGORY))
        assertTrue(isReservedCategory("pago de un tercero"), "se compara sin distinguir mayúsculas")
    }

    @Test
    fun la_deuda_sigue_siendo_del_dueno_aunque_la_pague_otro() {
        // La regla central, y la más fácil de romper por «ayudar»: quién paga la cuota NO cambia
        // de quién es el pasivo. Si esto se invirtiera, la deuda del dueño caería $869,3M de un
        // saque —las dos hipotecas más Cotrafa— y su patrimonio se vería mejor por un cambio de
        // rótulo.
        //
        // La primera versión de este test releía `principal` e `installment` del constructor de
        // un `data class`: no tocaba NINGUNA función que calcule deuda, así que la regla que
        // prometía blindar no estaba probada en ningún lado. Lo encontró la revisión.
        //
        // Ahora se prueba contra `signedDelta`, que es de donde sale la deuda de verdad: el
        // desembolso la sube y el pago la baja, EXACTAMENTE igual pague quien pague.
        val desembolso = signedDelta(AccountType.LOAN, TransactionType.EXPENSE, 784_000_000)
        val cuota = signedDelta(AccountType.LOAN, TransactionType.INCOME, 9_147_408)

        assertEquals(784_000_000L, desembolso, "el desembolso sube la deuda")
        assertEquals(-9_147_408L, cuota, "la cuota la baja, ni más ni menos")
        // Y el saldo después de una cuota es el mismo con o sin tercero: la única diferencia
        // entre los dos créditos es el rótulo.
        assertEquals(desembolso + cuota, 774_852_592L)
        assertEquals(hipoteca1254(null).installment, hipoteca1254("Skandia").installment)
        assertEquals(hipoteca1254(null).principal, hipoteca1254("Skandia").principal)
    }

    @Test
    fun un_cliente_viejo_deserializa_sin_romperse() {
        // Un APK anterior no conoce el campo. La dirección de LECTURA es inofensiva —cree que la
        // cuota la paga el dueño, que es mostrar de más y no de menos— pero hay que comprobarla
        // deserializando JSON de verdad: la versión anterior de este test construía el objeto en
        // Kotlin omitiendo el parámetro, o sea probaba el default de Kotlin y no la
        // deserialización que su nombre prometía.
        //
        // La dirección que SÍ duele es la de escritura —un cliente viejo que edita un crédito y
        // borra el `paidBy` guardado— y esa se cierra en el server mirando las claves del JSON
        // recibido, no acá. Ver `PUT /api/credits/{id}`.
        val json = """
            {"accountId":"acc-x","bank":"Davibank","principal":1,"rateEa":1.0,
             "termMonths":1,"installment":1,"dayOfMonth":1,"startDate":"2026-01-01"}
        """.trimIndent()

        val terms = Json.decodeFromString<CreditTerms>(json)

        assertNull(terms.paidBy)
        assertFalse(terms.payrollDeduction)
    }

    @Test
    fun y_uno_nuevo_lo_lleva_de_ida_y_de_vuelta() {
        // El campo tiene que sobrevivir el viaje completo: si `paidBy` no se serializara, el
        // dueño marcaría «la paga Skandia», el server nunca lo recibiría y seguiría recibiendo
        // avisos sin entender por qué.
        val ida = Json.encodeToString(CreditTerms.serializer(), hipoteca1254("Skandia"))
        assertTrue("Skandia" in ida)
        assertEquals("Skandia", Json.decodeFromString<CreditTerms>(ida).paidBy)
    }
}
