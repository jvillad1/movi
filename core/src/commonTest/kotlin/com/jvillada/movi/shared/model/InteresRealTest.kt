package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La cuota acepta el interés real del extracto**, y lo que el server hace con él.
 *
 * El caso que abrió la rama, con las cifras del dueño: el Libre inversión ·9695 (saldo
 * $40.710.555 al 11,27 % E.A.) estima $363.905 de interés y el banco cobró **$473.227**. Son
 * $109.322 en una sola cuota, siempre hacia el mismo lado —la deuda baja de más—, y él ya lo
 * había corregido dos veces a mano en la base porque desde la app no había dónde escribirlo.
 *
 * Las cifras no son de laboratorio: cuota $1.204.064, seguro $124.800, interés real $473.227 →
 * capital **$606.037**, que es exactamente lo que él cargó a mano el 5 de septiembre.
 */
class InteresRealTest {

    private val ahorros = Account("acc-ahorros", "Bancolombia", AccountType.SAVINGS, 20_000_000L)
    private val nueveSeisNueveCinco = Account("acc-9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555L)

    private fun peticion(interesReal: Long?, monto: Long = 1_204_064L) = CreatePagoDeCuotaRequest(
        fromAccountId = ahorros.id,
        debtAccountId = nueveSeisNueveCinco.id,
        amount = monto,
        timestamp = 1_788_000_000_000L,
        transferId = "tr-9695",
        fromEventId = "ev-dinero",
        toEventId = "ev-deuda",
        interesReal = interesReal,
    )

    // ── Los números del dueño ──────────────────────────────────────────────────

    @Test
    fun la_estimacion_del_9695_es_la_que_se_queda_corta() {
        // Lo que Movi estimaba, para que el tamaño del error quede escrito y no en una anécdota.
        val estimado = desglosarCuota(1_204_064L, AccountType.LOAN, 40_710_555L, 11.27, 124_800L)

        assertEquals(363_905L, estimado.interes)
        assertEquals(473_227L - 363_905L, 109_322L, "la diferencia contra el extracto")
    }

    @Test
    fun con_el_interes_real_el_capital_es_el_que_el_dueno_cargo_a_mano() {
        val d = desglosarCuotaRegistrada(
            cuota = 1_204_064L,
            tipoDeLaDeuda = AccountType.LOAN,
            saldoDeLaDeuda = 40_710_555L,
            rateEa = 11.27,
            seguroMensual = 124_800L,
            interesReal = 473_227L,
        )

        assertEquals(473_227L, d.interes, "el interés es el del extracto, no la estimación")
        assertEquals(124_800L, d.seguro, "el seguro sigue saliendo de las condiciones")
        assertEquals(606_037L, d.capital)
        assertEquals(MotivoDelDesglose.INTERES_REAL, d.motivo)
        assertEquals(d.cuota, d.interes + d.seguro + d.capital)
    }

    @Test
    fun sin_interes_real_se_estima_exactamente_como_antes() {
        val conNull = desglosarCuotaRegistrada(1_204_064L, AccountType.LOAN, 40_710_555L, 11.27, 124_800L, interesReal = null)
        val directo = desglosarCuota(1_204_064L, AccountType.LOAN, 40_710_555L, 11.27, 124_800L)

        assertEquals(directo, conNull, "null es «estímalo», y estimar es lo mismo de siempre")
    }

    @Test
    fun la_pata_de_la_deuda_guarda_el_interes_real_mas_el_seguro() {
        // Lo que después lee la corrección del monto (`montoDeLaHermanaAlCorregir`): con el
        // interés real guardado, corregir la cuota vuelve a dar el capital correcto.
        val d = desglosarCuotaConInteresReal(1_204_064L, AccountType.LOAN, 473_227L, 124_800L)
        val (dinero, deuda) = pagoDeCuotaLegs(peticion(473_227L), ahorros, nueveSeisNueveCinco, d)

        assertEquals(1_204_064L, dinero.amount, "la plata que salió es la cuota entera")
        assertNull(dinero.noAmortiza)
        assertEquals(606_037L, deuda.amount, "la deuda baja el capital")
        assertEquals(473_227L + 124_800L, deuda.noAmortiza)
        assertTrue(deuda.description.startsWith("Abono a capital"), deuda.description)
    }

    // ── Las guardas, que son las mismas en la hoja y en el server ─────────────

    @Test
    fun null_siempre_pasa() {
        assertNull(validarInteresReal(null, 1_204_064L, AccountType.LOAN, 124_800L))
        assertNull(validarInteresReal(null, 100L, AccountType.CREDIT_CARD, null))
    }

    @Test
    fun un_interes_negativo_se_rechaza() {
        assertEquals(INTERES_REAL_NEGATIVO, validarInteresReal(-1L, 1_204_064L, AccountType.LOAN, 124_800L))
    }

    @Test
    fun cero_es_un_interes_valido() {
        // «El banco no cobró interés este mes» es una afirmación legítima, y distinta de «no sé».
        assertNull(validarInteresReal(0L, 1_204_064L, AccountType.LOAN, 124_800L))
        val d = desglosarCuotaConInteresReal(1_204_064L, AccountType.LOAN, 0L, 124_800L)
        assertEquals(1_204_064L - 124_800L, d.capital)
    }

    @Test
    fun una_tarjeta_no_lleva_interes_adentro_del_pago() {
        assertEquals(INTERES_REAL_EN_TARJETA, validarInteresReal(10_000L, 1_008_902L, AccountType.CREDIT_CARD, null))
    }

    @Test
    fun un_interes_que_deja_el_capital_negativo_se_rechaza_con_las_cifras() {
        // 473.227 + 124.800 = 598.027 > 500.000: la deuda SUBIRÍA con un pago. Eso no se clampa,
        // se rechaza, y el mensaje dice las tres cifras para que se vea cuál está mal.
        val motivo = assertNotNull(validarInteresReal(473_227L, 500_000L, AccountType.LOAN, 124_800L))

        assertTrue("473.227" in motivo, motivo)
        assertTrue("124.800" in motivo, motivo)
        assertTrue("500.000" in motivo, motivo)
        assertTrue("subiría" in motivo, motivo)
    }

    @Test
    fun interes_mas_seguro_igual_a_la_cuota_se_acepta_y_deja_el_capital_en_cero() {
        // El borde: nada abona a capital, pero la deuda tampoco sube. Es un pago que existe.
        assertNull(validarInteresReal(473_227L, 598_027L, AccountType.LOAN, 124_800L))
        assertEquals(0L, desglosarCuotaConInteresReal(598_027L, AccountType.LOAN, 473_227L, 124_800L).capital)
    }

    @Test
    fun sin_seguro_declarado_el_mensaje_no_lo_nombra() {
        val motivo = assertNotNull(validarInteresReal(600_000L, 500_000L, AccountType.LOAN, null))
        assertTrue("seguro" !in motivo, motivo)
    }

    @Test
    fun desglosar_con_un_interes_invalido_explota_en_vez_de_escribir() {
        // La validación va antes. Si alguien la saltea, que no compile en silencio un capital
        // negativo: que reviente donde se ve.
        assertFailsWith<IllegalArgumentException> {
            desglosarCuotaConInteresReal(500_000L, AccountType.LOAN, 473_227L, 124_800L)
        }
        assertFailsWith<IllegalArgumentException> {
            desglosarCuotaConInteresReal(1_000_000L, AccountType.CREDIT_CARD, 10_000L, null)
        }
    }

    // ── Un crédito sin tasa también lo acepta ──────────────────────────────────

    @Test
    fun un_credito_sin_tasa_acepta_el_interes_del_extracto() {
        // Sin tasa la estimación no puede separar nada y baja la deuda por todo. Con el extracto
        // en la mano sí se puede, y eso es mejor que las condiciones incompletas.
        val d = desglosarCuotaRegistrada(1_204_064L, AccountType.LOAN, 40_710_555L, rateEa = null, seguroMensual = null, interesReal = 473_227L)

        assertEquals(MotivoDelDesglose.INTERES_REAL, d.motivo)
        assertEquals(1_204_064L - 473_227L, d.capital)
    }

    // ── El contrato con los clientes viejos ────────────────────────────────────

    @Test
    fun una_peticion_sin_el_campo_se_lee_como_null() {
        // El APK 1.17 manda exactamente esto. Tiene que seguir funcionando igual que antes.
        val json = """{"fromAccountId":"a","debtAccountId":"b","amount":1204064,"timestamp":1788000000000,
            "transferId":"t","fromEventId":"e1","toEventId":"e2"}"""
        val leida = Json.decodeFromString<CreatePagoDeCuotaRequest>(json)

        assertNull(leida.interesReal)
    }

    @Test
    fun un_null_no_viaja_en_el_JSON() {
        // Sin `@EncodeDefault`: la hoja que no tocó el campo manda lo mismo que un cliente que no
        // sabe que existe. Un `"interesReal":null` explícito sería otra forma más de decir lo
        // mismo, y una forma más es un caso más que un server viejo podría no entender.
        val json = Json.encodeToString(CreatePagoDeCuotaRequest.serializer(), peticion(interesReal = null))

        assertTrue("interesReal" !in json, json)
    }

    @Test
    fun un_valor_si_viaja() {
        val json = Json.encodeToString(CreatePagoDeCuotaRequest.serializer(), peticion(interesReal = 473_227L))

        assertTrue("\"interesReal\":473227" in json, json)
    }

    @Test
    fun el_cuerpo_opcional_de_la_cuota_ajena_tambien_lee_vacio_como_null() {
        assertNull(Json.decodeFromString<RegistrarCuotaAjenaRequest>("{}").interesReal)
        assertEquals(3_646_011L, Json.decodeFromString<RegistrarCuotaAjenaRequest>("""{"interesReal":3646011}""").interesReal)
    }

    @Test
    fun los_puntos_de_miles_se_ponen_bien() {
        assertEquals("1.204.064", conPuntosDeMiles(1_204_064L))
        assertEquals("0", conPuntosDeMiles(0L))
        assertEquals("999", conPuntosDeMiles(999L))
        assertEquals("1.000", conPuntosDeMiles(1_000L))
        assertEquals("-72.705", conPuntosDeMiles(-72_705L))
    }
}
