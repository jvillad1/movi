package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.SIN_CATEGORIA
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `parseSms` es el único clasificador de la cadena que estaba sin cobertura, y es donde un error
 * cuesta más caro: lo que sale de acá se le propone al dueño ya preseleccionado en la pantalla de
 * confirmación, y si él acepta se crea un evento.
 *
 * El riesgo concreto que se cubre acá es la categoría [CARD_PAYMENT_CATEGORY]: un evento con esa
 * categoría **deja de contar como gasto del mes** (ver `isCashFlow`). O sea que un falso positivo
 * no produce un error visible — produce plata que desaparece del presupuesto en silencio. Por eso
 * la mitad de estos tests son negativos: verifican que un gasto real no se cuele por ahí.
 */
class SmsParseTest {

    // ── Lo que SÍ tiene que detectarse como pago de tarjeta ───────────────────

    /**
     * El SMS real de Bancolombia por el pago automático del extracto. Es el caso que motivó la
     * feature: sin esta categoría, esta plata se contaba dos veces — acá, y otra vez en cada
     * compra que ese pago está cancelando.
     */
    @Test
    fun `el pago automatico del extracto se categoriza como pago de tarjeta`() {
        val parsed = assertNotNull(parseSms("Bancolombia: Pago autom TC *1234 por \$808.940 el 02/06/2026."))
        assertEquals(CARD_PAYMENT_CATEGORY, parsed.category)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(808940.0, parsed.amount)
    }

    /**
     * La variante sin "autom". Se prueba aparte porque cada patrón de la lista es una decisión
     * suya: si alguien saca uno, tiene que romper acá y no en el Dashboard del dueño.
     */
    @Test
    fun `las otras redacciones de pago de tarjeta tambien se detectan`() {
        listOf(
            "Bancolombia: Pago tarjeta de credito por \$500.000.",
            "Pago TC 4567 por \$120.000 realizado con exito.",
            "Abono tarjeta *9876 por \$75.000.",
        ).forEach { text ->
            val parsed = assertNotNull(parseSms(text), "no parseó: $text")
            assertEquals(CARD_PAYMENT_CATEGORY, parsed.category, "no se detectó como pago de tarjeta: $text")
        }
    }

    // ── Lo que NO puede caer ahí: gasto real que dejaría de contar ────────────

    /**
     * `PAGO QR` y `PAGO PSE` son compras reales, y son **frecuentes** en los datos del dueño:
     * en la base local hay decenas de "PAGO QR ..." en la cuenta de ahorros. Que empiecen con la
     * palabra "Pago" es justo lo que hace peligrosa a una heurística por substring.
     */
    @Test
    fun `una compra con QR o PSE no es un pago de tarjeta`() {
        listOf(
            "Bancolombia: Pago QR por \$126.800 en Carnes y legumbres.",
            "Bancolombia: Pago PSE por \$119.400 en Frisby S A.",
            "Compra por \$45.220 en NOTARIA 13.",
        ).forEach { text ->
            val parsed = assertNotNull(parseSms(text), "no parseó: $text")
            assertNotEquals(
                CARD_PAYMENT_CATEGORY, parsed.category,
                "un gasto real quedó como pago de tarjeta y dejaría de contar en el mes: $text",
            )
        }
    }

    /**
     * El borde del patrón `"pago tc "`, que lleva un espacio al final justamente para esto: sin
     * ese espacio, cualquier comercio cuyo nombre empiece con "tc" habría matcheado.
     */
    @Test
    fun `un comercio que empieza con TC no dispara el patron pago tc`() {
        // El texto arranca con "Pago TCHERASSI": si el patrón perdiera el espacio final, esto
        // matchearía ("pago tc" está adentro de "pago tcherassi") y una compra de ropa dejaría
        // de contar como gasto. Con el espacio, no.
        val parsed = assertNotNull(parseSms("Bancolombia: Pago TCHERASSI STORE por \$30.000."))
        assertNotEquals(CARD_PAYMENT_CATEGORY, parsed.category)
    }

    /**
     * Un ingreso no puede ser un pago de tarjeta por más que el texto lo diga: `categoryFor`
     * cortocircuita por tipo antes de mirar los patrones. Sin esa guarda, un abono entrante
     * quedaría fuera de los ingresos del mes.
     */
    @Test
    fun `un ingreso nunca se categoriza como pago de tarjeta`() {
        val parsed = assertNotNull(parseSms("Bancolombia: Recibiste \$200.000 de PAGO TARJETA S.A.S."))
        assertEquals(TransactionType.INCOME, parsed.type)
        assertNotEquals(CARD_PAYMENT_CATEGORY, parsed.category)
    }

    // ── El resto del parseo, que la firma nueva de categoryFor podía romper ───

    @Test
    fun `una compra se clasifica por el comercio, con las categorias que la app ofrece`() {
        assertEquals("Transporte", assertNotNull(parseSms("Compra por \$25.000 en UBER TRIP.")).category)
        // Antes «Mercado» y «Suscripción»: dos categorías que no existen en ningún selector de la
        // app ni en los datos del dueño. Cada SMS confirmado abría una categoría paralela de un
        // solo movimiento y partía el gráfico de «en qué se fue la plata». Ver SIN_CATEGORIA.
        assertEquals("Comida", assertNotNull(parseSms("Compra por \$180.000 en EXITO POBLADO.")).category)
        assertEquals("Entretenimiento", assertNotNull(parseSms("Compra por \$44.900 en NETFLIX.")).category)
    }

    @Test
    fun `lo que no se reconoce queda sin categoria, y eso se llama Otros`() {
        assertEquals(SIN_CATEGORIA, assertNotNull(parseSms("Compra por \$3.200 en ZELO GROUP.")).category)
        assertEquals("Otros", SIN_CATEGORIA, "la app entera dice «Otros»; «Otro» era solo de acá")
    }

    @Test
    fun `la nomina es un ingreso y se reconoce por su propio nombre`() {
        val parsed = assertNotNull(parseSms("Bancolombia: Nómina recibida por \$8.500.000."))
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("Nómina", parsed.merchant)
        assertEquals("Nómina", parsed.category)
    }

    /** Sin monto no hay nada que registrar: parsear a medias sería peor que no parsear. */
    @Test
    fun `un texto sin monto no parsea`() {
        assertNull(parseSms("Bancolombia: tu clave fue actualizada."))
    }

    // ── Las redacciones que no se leían (bandeja real, sep-2026; nombres cambiados) ──────────

    @Test
    fun `una compra con COP pegado al monto se lee`() {
        val p = assertNotNull(parseSms("Bancolombia: Compraste COP249.000,00 en PAYPAL *MICROSOFT con tu T.Cred *1111, el 06/09/2026 a las 08:03."))
        assertEquals(249_000.0, p.amount)
        assertEquals("COP", p.currency)
        assertEquals("PAYPAL *MICROSOFT", p.merchant, "sin «con tu T» pegado")
    }

    @Test
    fun `una compra en dolares se lee con su moneda`() {
        val p = assertNotNull(parseSms("Bancolombia: Compraste USD20,00 en ANTHROPIC* CLAUDE SU, el 09/09/2026 a las 00:53. Esta compra esta asociada a T.Cred *1111."))
        assertEquals(20.0, p.amount)
        assertEquals("USD", p.currency)
        assertEquals("ANTHROPIC* CLAUDE SU", p.merchant)
    }

    @Test
    fun `el pago recibido a la tarjeta sin signo pesos es pago de tarjeta`() {
        val p = assertNotNull(parseSms("Bancolombia: Recibimos pago por 9.809.799 a tu tarjeta de credito **2222 desde Wompi-PSE, el 08/09/2026 07:56:38."))
        assertEquals(9_809_799.0, p.amount)
        assertEquals(CARD_PAYMENT_CATEGORY, p.category)
        assertEquals(TransactionType.INCOME, p.type, "es plata que ENTRA a la tarjeta")
        val otro = assertNotNull(parseSms("Bancolombia: Pagaste \$1,008,902 en la tarjeta de credito *2222 desde la cuenta *3333, el 29/08/2026."))
        assertEquals(CARD_PAYMENT_CATEGORY, otro.category)
    }

    /**
     * **El abono a la tarjeta es plata que entra a la tarjeta**, no un gasto: leído como EXPENSE y
     * confirmado, le sumaba la deuda en vez de bajarla. Pasó con dos abonos de un tercero a la AMEX
     * en septiembre ($9.000.000 y $9.809.799). El «Pagaste … en la tarjeta … desde la cuenta» sigue
     * siendo una salida: ese aviso habla desde la cuenta de ahorros.
     */
    @Test
    fun `recibimos pago a tu tarjeta es un ingreso en la tarjeta, no un gasto`() {
        val abono = assertNotNull(
            parseSms("Bancolombia: Recibimos pago por \$9,000,000.00 a tu tarjeta de credito **2222 desde Wompi-PSE, el 04/09/2026 08:11."),
        )
        assertEquals(9_000_000.0, abono.amount)
        assertEquals(TransactionType.INCOME, abono.type)
        assertEquals(CARD_PAYMENT_CATEGORY, abono.category)
        assertEquals("Pago de tarjeta", abono.merchant)
        assertNull(abono.identificadorDelDestino, "un abono a la tarjeta propia no ofrece guardar a nadie")

        val pagoDesdeLaCuenta = assertNotNull(parseSms("Bancolombia: Pagaste \$974,550 en la tarjeta de credito *2222 desde la cuenta *3333, el 15/08/2026 19:37."))
        assertEquals(TransactionType.EXPENSE, pagoDesdeLaCuenta.type)

        // El de Nu, que el teléfono captura del lado de la cuenta de ahorros, sigue siendo una salida.
        val nu = assertNotNull(parseSms("¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.998,96.", origen = "Notificación · Nu"))
        assertEquals(TransactionType.EXPENSE, nu.type)
    }

    /**
     * El SMS real de Nu, hallado en vivo el 27-sep-2026: sin este caso caía en `Otros`, con «en
     * tu app» —el pie del mensaje— como comercio (`merchantInRegex` capturando después de «En un
     * rato podrás verlo…»). Nu no escribe ninguna de las frases de Bancolombia
     * ([looksLikeCardPayment]); necesita su propia detección, ya calculada en `parseSms` con
     * `pagoDeNu`. Solo aplica con `origen` diciendo Nu — el mismo criterio que ya usa
     * `loDeNuEsUnMovimiento`.
     */
    @Test
    fun `el pago de tarjeta de Nu se categoriza aunque no diga ninguna frase de Bancolombia`() {
        val texto = "¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.998,96. " +
            "En un rato podrás verlo en tu app."
        val p = assertNotNull(parseSms(texto, origen = "Notificación · Nu"))
        assertEquals(CARD_PAYMENT_CATEGORY, p.category)
        assertEquals("Pago de tarjeta", p.merchant)
        assertEquals(1_998.96, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)

        // Sin el origen de Nu, no hay como distinguirlo de un texto cualquiera con "pago" y "en":
        // sigue el camino de siempre, sin la regla nueva.
        assertNotEquals(CARD_PAYMENT_CATEGORY, assertNotNull(parseSms(texto)).category)
    }

    /**
     * **A quién fue, como campo aparte** (29-sep): el número o la llave ya no van solo pegados en el
     * nombre. Con los tres textos reales: la transferencia a una cuenta, el pago por QR a una llave
     * y la plata que llega a Nu «de NOMBRE con tu llave». El cliente decide con esto si ofrecer
     * «¿De quién es esta cuenta?».
     */
    @Test
    fun `el identificador del destino sale aparte para los tres textos reales`() {
        val transferencia = assertNotNull(parseSms("Bancolombia: ANA, transferiste \$10,000.00 a la cuenta *41279033068 desde tu cuenta *3333."))
        assertEquals("41279033068", transferencia.identificadorDelDestino, "la de destino, no la de origen (*3333)")
        assertEquals(false, transferencia.identificadorEsLlave)

        val qr = assertNotNull(parseSms("Bancolombia: ANA PEREZ pagaste \$18,500.00 por codigo QR desde tu cuenta *3333 a la llave 0087 el 09/09/2026 a las 15:08."))
        assertEquals("0087", qr.identificadorDelDestino)
        assertEquals(true, qr.identificadorEsLlave, "una llave numérica sigue siendo llave, no un número de cuenta")

        val nu = assertNotNull(
            parseSms("Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave.", "Notificación · Nu"),
        )
        assertEquals("carolina restrepo salazar", nu.identificadorDelDestino, "«con tu llave» es la del dueño: lo que identifica es el nombre")
        assertEquals(true, nu.identificadorEsLlave)

        // Una compra no es a una persona, y un pago de tarjeta tampoco.
        assertEquals(null, assertNotNull(parseSms("Bancolombia: Compraste \$28.500 en UBER con tu T.Cred *3684.")).identificadorDelDestino)
    }

    @Test
    fun `pagos QR y transferencias dicen a quien`() {
        // **La llave va en el nombre.** Sin ella todos los pagos por QR se llaman igual, y Movi no
        // tiene con qué distinguir el almuerzo de ayer del arriendo de mañana — ni, por lo tanto,
        // con qué acordarse de cómo se categorizó ninguno de los dos.
        assertEquals("Pago QR · llave 0087", assertNotNull(parseSms("Bancolombia: ANA PEREZ pagaste \$18,500.00 por codigo QR desde tu cuenta *3333 a la llave 0087 el 09/09/2026 a las 15:08.")).merchant)
        // Con el nombre del comercio adentro, ese nombre manda: la llave sobra.
        assertEquals("Carnes y legumbres", assertNotNull(parseSms("Bancolombia: Pago QR por \$126.800 en Carnes y legumbres.")).merchant)
        // Igual con una transferencia sin nombre: la cuenta de DESTINO es lo único que identifica.
        assertEquals(
            "Transferencia a la cuenta *41279033068",
            assertNotNull(parseSms("Bancolombia: ANA, transferiste \$10,000.00 a la cuenta *41279033068 desde tu cuenta *3333.")).merchant,
        )
        assertEquals("PEDRO GOMEZ", assertNotNull(parseSms("Bancolombia: ANA, transferiste \$25,910.00 a la llave @pedro desde tu cuenta *3333 a PEDRO GOMEZ el 10/09/26 a las 08:50.")).merchant)
        assertEquals("Salud Total S A", assertNotNull(parseSms("Bancolombia: Pagaste \$138,600.00 a Salud Total S A desde tu producto 3333 el 05/09/2026 17:16:47.")).merchant)
        assertEquals("CARLOS RUIZ", assertNotNull(parseSms("Bancolombia: ANA, recibiste una transferencia de CARLOS RUIZ por \$1,000,000 en tu cuenta *3333.")).merchant, "sin «por \$…» pegado")
    }

    @Test
    fun `los avisos que no mueven plata no se proponen como movimiento`() {
        assertNull(parseSms("Bancolombia confirma ampliacion de plazo por USD 1,202.49 en su TC MASTER *1111. La tasa es de 2.18%."))
        assertNull(parseSms("Bancolombia: Muy bien. Inscribiste la cuenta de un tercero desde APP Bancolombia."))
    }
}
