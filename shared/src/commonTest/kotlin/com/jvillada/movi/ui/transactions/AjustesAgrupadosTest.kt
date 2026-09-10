package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Los ajustes de saldo de un día, en un renglón que se abre.**
 *
 * Corregir el saldo de una cuenta no es plata que se movió: es una corrección a lo que Movi creía.
 * Pero se anota como movimiento y se listaba como movimiento, así que una tarde de conciliar
 * contra el portal del banco dejaba diez renglones «Ajuste al saldo del banco — quedó en $…»
 * compitiendo de igual a igual con «Carnes y Legumbres Santa Elena». El dueño lo dijo así:
 * *«Tantos movimientos de ajuste de saldo se ven horribles»*.
 *
 * Lo que se prueba acá es que agrupar **no esconde ni reordena nada**: el grupo queda donde estaba
 * el primer ajuste, los movimientos de verdad no se tocan, y adentro del grupo están todos.
 */
class AjustesAgrupadosTest {

    private var n = 0

    private fun ajuste(cuenta: String = "acc_banco", monto: Long = 9_006L) = FinancialEvent(
        id = "aj_${n++}",
        accountId = cuenta,
        type = TransactionType.INCOME,
        amount = monto,
        category = ADJUSTMENT_CATEGORY,
        description = "Ajuste al saldo del banco — quedó en $498.547",
        timestamp = 1_757_000_000_000L,
    )

    private fun gasto(descripcion: String = "Carnes y Legumbres Santa Elena") = FinancialEvent(
        id = "ev_${n++}",
        accountId = "acc_banco",
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = descripcion,
        timestamp = 1_757_000_000_000L,
    )

    private fun filas(vararg eventos: FinancialEvent) = eventos.map { MovementRow.Single(it) }

    @Test
    fun `varios ajustes se juntan en un solo renglon`() {
        val rows = filas(gasto(), ajuste(), ajuste("acc_libranza"), ajuste("acc_master"), gasto("Pago QR"))

        val agrupadas = agruparAjustesDeSaldo(rows, query = "")

        assertEquals(3, agrupadas.size, "dos gastos y un grupo")
        val grupo = agrupadas.filterIsInstance<MovementRow.Ajustes>().single()
        assertEquals(3, grupo.events.size, "adentro están los tres, ninguno se pierde")
    }

    @Test
    fun `el grupo queda donde estaba el primer ajuste`() {
        // Nada se mueve de lugar: si el ajuste venía después del primer gasto, el grupo también.
        val rows = filas(gasto(), ajuste(), ajuste("acc_libranza"), gasto("Pago QR"))

        val agrupadas = agruparAjustesDeSaldo(rows, query = "")

        assertTrue(agrupadas[0] is MovementRow.Single)
        assertTrue(agrupadas[1] is MovementRow.Ajustes)
        assertTrue(agrupadas[2] is MovementRow.Single)
    }

    @Test
    fun `un ajuste solo no se agrupa`() {
        // Un grupo de uno ocupa el mismo renglón y encima pide un toque más para leer lo que ya
        // se veía. El problema del dueño empieza cuando son varios.
        val rows = filas(gasto(), ajuste())

        val agrupadas = agruparAjustesDeSaldo(rows, query = "")

        assertEquals(rows, agrupadas)
    }

    @Test
    fun `con una busqueda escrita no se agrupa nada`() {
        // Buscar es pedirlos explícitamente. Esconder adentro de un grupo justo lo que acabás de
        // buscar es peor que mostrar de más — el mismo escape que tiene la apertura de una cuenta.
        val rows = filas(ajuste(), ajuste("acc_libranza"), ajuste("acc_master"))

        assertEquals(rows, agruparAjustesDeSaldo(rows, query = "ajuste"))
    }

    @Test
    fun `un dia sin ajustes queda exactamente igual`() {
        val rows = filas(gasto(), gasto("Pago QR"), gasto("Colegio Hija"))

        assertEquals(rows, agruparAjustesDeSaldo(rows, query = ""))
    }

    @Test
    fun `el titulo cuenta cuentas, no correcciones`() {
        // Dos ajustes sobre la MISMA cuenta —se equivocó y volvió a corregir— son una cuenta
        // revisada, no dos. Es lo que el dueño hizo, y es lo que el renglón tiene que decir.
        assertEquals(
            "Ajustaste el saldo de 2 cuentas",
            tituloDeLosAjustes(listOf(ajuste("acc_banco"), ajuste("acc_banco"), ajuste("acc_nu"))),
        )
        assertEquals("Ajustaste el saldo de una cuenta", tituloDeLosAjustes(listOf(ajuste())))
    }

    /**
     * **Agrupar no puede bajarle la cuenta al día.** El encabezado de un día plegado dice cuántos
     * movimientos tiene, y contaba renglones: con el grupo pasaba a decir «2 movimientos» sobre un
     * día con cuatro. Un número que cambia solo porque cambió cómo se dibuja la lista es
     * exactamente lo que no puede pasar.
     */
    @Test
    fun `el dia plegado sigue diciendo cuantos movimientos tiene de verdad`() {
        val rows = agruparAjustesDeSaldo(
            filas(gasto(), ajuste(), ajuste("acc_libranza"), ajuste("acc_master")),
            query = "",
        )

        assertEquals(2, rows.size, "en pantalla son dos renglones")
        assertEquals(4, cuantosMovimientosDice(rows), "pero el día tiene cuatro movimientos")
    }

    @Test
    fun `el grupo es gris, porque ninguno movio plata del bolsillo`() {
        val grupo = MovementRow.Ajustes(listOf(ajuste(), ajuste("acc_nu")))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelRenglon(grupo))
    }
}
