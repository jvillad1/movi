package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Anular media cuota tiene DOS consecuencias, y la hoja tiene que decir las dos
 *
 * Desde que la deuda baja solo por lo que abona a capital ([DesgloseDeCuota]), las dos patas de un
 * pago de cuota **dejaron de valer lo mismo**. La anulación cascadea a las dos por `transferId` y
 * revierte cada una por su propio monto, así que los saldos quedan bien; lo que quedó mintiendo es
 * lo que la pantalla *dice*: anular desde la cuenta de ahorros mostraba «$4.215.223» y nada más,
 * mientras desaparecían $4.215.223 de la cuenta **y** $1.733.905 de la deuda.
 *
 * Es el mismo defecto que la ola arregló en el renglón de Movimientos (`transferRowSubtitle`), por
 * la otra puerta: una sola cifra afirmando ser el efecto entero de la operación.
 *
 * Vive en `:core` y no en la hoja porque decide sobre plata y ya hay dos pantallas que cuentan la
 * misma historia. La hoja solo pone el nombre de la cuenta y el monto formateado.
 */
class AnulacionDeUnParTest {

    /** La cuota real del vehículo: $4.215.223 salen de la cuenta, $1.733.905 abonan a capital. */
    private val dinero = FinancialEvent(
        id = "ev_dinero",
        accountId = "acc_ahorros",
        type = TransactionType.EXPENSE,
        amount = 4_215_223L,
        category = CUOTA_CATEGORY,
        description = "Cuota de Vehículo 4083",
        timestamp = 1_788_000_000_000L,
        transferId = "tr_cuota",
    )

    private val deuda = FinancialEvent(
        id = "ev_deuda",
        accountId = "acc_carro",
        type = TransactionType.INCOME,
        amount = 1_733_905L,
        category = CUOTA_CATEGORY,
        description = "Abono a capital desde Bancolombia Ahorros",
        timestamp = 1_788_000_000_000L,
        transferId = "tr_cuota",
        noAmortiza = 2_481_318L,
    )

    // ── Las dos cifras ────────────────────────────────────────────────────────

    @Test
    fun anular_desde_la_cuenta_de_ahorros_nombra_TAMBIEN_lo_que_le_pasa_a_la_deuda() {
        // El caso que abrió esto: el dueño toca la pata que ve en su cuenta de ahorros.
        val efectos = loQuePasaAlAnular(pata = dinero, hermana = deuda)

        assertEquals(2, efectos.size, "las dos mitades, no una")
        assertEquals("acc_ahorros", efectos[0].accountId, "la plata primero: es la que él tocó")
        assertEquals(4_215_223L, efectos[0].monto)
        assertEquals(EfectoDeAnular.LA_CUENTA_RECUPERA, efectos[0].efecto)
        assertEquals("acc_carro", efectos[1].accountId)
        assertEquals(1_733_905L, efectos[1].monto, "la deuda vuelve a subir por el CAPITAL, no por la cuota")
        assertEquals(EfectoDeAnular.LA_DEUDA_VUELVE_A_SUBIR, efectos[1].efecto)
    }

    @Test
    fun anular_desde_el_credito_dice_exactamente_lo_mismo() {
        // Se llega por las dos puertas —Movimientos y el detalle de cada una de las dos cuentas— y
        // el hecho es uno solo. Si el orden o las cifras cambiaran según por dónde se entró, la
        // hoja estaría contando dos historias distintas del mismo par.
        assertEquals(loQuePasaAlAnular(dinero, deuda), loQuePasaAlAnular(deuda, dinero))
    }

    // ── Cuándo NO se dice nada ────────────────────────────────────────────────

    @Test
    fun un_par_simetrico_no_agrega_ninguna_aclaracion() {
        // Un traspaso, un pago de tarjeta, o una cuota anterior a este cambio: las dos mitades son
        // la misma plata y la cifra de arriba ya la dice entera. Un aviso de más sobre algo que no
        // cambia enseña a ignorarlos.
        val hermanaSimetrica = deuda.copy(amount = dinero.amount, noAmortiza = null)

        assertEquals(emptyList(), loQuePasaAlAnular(dinero, hermanaSimetrica))
    }

    @Test
    fun sin_la_hermana_no_se_inventa_la_segunda_cifra() {
        // La hermana se lee del repositorio y esa lectura puede fallar o no haber terminado. Callar
        // es lo único honesto: la alternativa sería mostrar un número deducido de la nada.
        assertEquals(emptyList(), loQuePasaAlAnular(dinero, hermana = null))
    }

    @Test
    fun un_evento_suelto_no_tiene_nada_que_aclarar() {
        val suelto = dinero.copy(transferId = null)

        assertEquals(emptyList(), loQuePasaAlAnular(suelto, hermana = null))
    }

    @Test
    fun dos_eventos_de_traspasos_distintos_no_se_emparejan() {
        // El enlace es explícito justamente para que esto no sea una adivinanza. Si el repositorio
        // devolviera la pata de otro par, decir «tu deuda sube $X» sería inventar un hecho.
        assertEquals(emptyList(), loQuePasaAlAnular(dinero, deuda.copy(transferId = "tr_otro")))
    }

    // ── Cómo se lee ───────────────────────────────────────────────────────────

    @Test
    fun cada_efecto_se_lee_con_el_nombre_de_su_cuenta_y_su_cifra() {
        val (plata, credito) = loQuePasaAlAnular(dinero, deuda)

        assertEquals(
            "Bancolombia Ahorros recupera $4.215.223",
            textoDeLoQuePasa(plata, "Bancolombia Ahorros", "$4.215.223"),
        )
        assertEquals(
            "La deuda de Vehículo 4083 vuelve a subir $1.733.905",
            textoDeLoQuePasa(credito, "Vehículo 4083", "$1.733.905"),
        )
    }

    @Test
    fun sin_la_lista_de_cuentas_se_dicen_los_roles_y_no_un_nombre_inventado() {
        // Mismo criterio que el subtítulo del renglón de Movimientos: si las cuentas todavía no
        // llegaron, se dice el rol. La hoja tiene que seguir sirviendo sin ellas.
        val (plata, credito) = loQuePasaAlAnular(dinero, deuda)

        assertEquals("Tu cuenta recupera $4.215.223", textoDeLoQuePasa(plata, null, "$4.215.223"))
        assertEquals("La deuda vuelve a subir $1.733.905", textoDeLoQuePasa(credito, null, "$1.733.905"))
    }

    @Test
    fun la_explicacion_dice_por_que_son_dos_cifras_distintas() {
        // Sin esto, dos números distintos sobre un solo pago se leen como un error de la app.
        assertTrue(ANULAR_DESHACE_LAS_DOS_MITADES.contains("capital"), ANULAR_DESHACE_LAS_DOS_MITADES)
    }
}
