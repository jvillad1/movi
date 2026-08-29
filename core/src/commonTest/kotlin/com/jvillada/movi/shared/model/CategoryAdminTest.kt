package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Las reglas de «Más → Categorías» que **no se pueden romper sin romper la plata del dueño**.
 *
 * La más importante es la primera: `isCashFlow` reconoce las cuatro categorías reservadas **por
 * su nombre exacto**, así que si alguien renombra una constante (o deja que la pantalla nueva las
 * toque), las cifras de ingresos y egresos de TODOS los meses cambian de golpe y en silencio.
 * Estos tests fijan esa relación en las dos direcciones.
 */
class CategoryAdminTest {

    // ── Las reservadas ────────────────────────────────────────────────────────

    @Test
    fun `las cuatro reservadas son exactamente las que isCashFlow reconoce por nombre`() {
        assertEquals(
            setOf("Traspaso", "Saldo inicial", "Pago de tarjeta", "Cuenta eliminada"),
            RESERVED_CATEGORIES,
        )
        assertEquals(TRANSFER_CATEGORY, "Traspaso")
        assertEquals(OPENING_CATEGORY, "Saldo inicial")
        assertEquals(CARD_PAYMENT_CATEGORY, "Pago de tarjeta")
        assertEquals(ORPHANED_LEG_CATEGORY, "Cuenta eliminada")
    }

    @Test
    fun `las cuatro que isCashFlow excluye por nombre estan todas en la lista de reservadas`() {
        // Si alguna dejara de estar, la pantalla la dejaría renombrar y el mes entero cambiaría.
        //
        // Ola 15: son CUATRO, no tres. [ORPHANED_LEG_CATEGORY] se sumó a la familia — la pata de
        // un traspaso que perdió a su hermana no es plata ganada ni gastada, y mientras contaba,
        // borrar un crédito desembolsado de $257.000.000 subía «Ingresos del mes» de $12,4M a
        // $269,4M. Que el bucle las recorra a las cuatro es lo que hace que sacar una del
        // `isCashFlow` rompa este test en vez de romperle el mes al dueño.
        for (categoria in RESERVED_CATEGORIES) {
            assertFalse(
                isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, categoria),
                "«$categoria» queda fuera del flujo de caja: tiene que ser reservada",
            )
            assertFalse(
                isCashFlow(AccountType.CHECKING, TransactionType.INCOME, categoria),
                "«$categoria» tampoco es un ingreso, y ese es el lado que costó plata",
            )
        }
        // Y la vuelta: `isReservedCategory` compara sobre RESERVED_CATEGORIES, así que
        // preguntárselo por cada elemento del mismo conjunto no probaría nada. Lo que sí prueba
        // algo es que la lista de arriba —la de nombres literales— caiga entera adentro.
        for (nombre in listOf("Traspaso", "Saldo inicial", "Pago de tarjeta", "Cuenta eliminada")) {
            assertTrue(isReservedCategory(nombre), "«$nombre» tiene que estar protegida")
        }
    }

    @Test
    fun `reservada tambien en minusculas y con espacios de mas`() {
        // La guarda es para decidir si el dueño puede ESCRIBIR ese nombre, no para clasificar una
        // fila: dejar pasar «traspaso» fabricaría un nombre a un carácter del reservado.
        assertTrue(isReservedCategory("  traspaso "))
        assertTrue(isReservedCategory("PAGO DE TARJETA"))
    }

    @Test
    fun `una categoria normal no es reservada`() {
        assertFalse(isReservedCategory("Transporte"))
        assertFalse(isReservedCategory("Traspasos"))   // plural: es otra cosa
        assertFalse(isReservedCategory(""))
    }

    // ── El tipo efectivo ──────────────────────────────────────────────────────

    @Test
    fun `sin nada fijado manda el catalogo`() {
        assertEquals(setOf(TransactionType.EXPENSE), effectiveCategoryTypes("Comida", null))
        assertEquals(setOf(TransactionType.INCOME), effectiveCategoryTypes("Salario", null))
    }

    @Test
    fun `lo fijado por el dueno le gana al catalogo`() {
        // El caso que motivó todo: «Otros» está clavada en EXPENSE y él quiere usarla también
        // para ingresos, en vez de tener «Otros» y «Otros ingresos» partidas en dos.
        assertEquals(
            setOf(TransactionType.EXPENSE, TransactionType.INCOME),
            effectiveCategoryTypes("Otros", CATEGORY_TYPE_BOTH),
        )
        assertEquals(setOf(TransactionType.INCOME), effectiveCategoryTypes("Comida", "INCOME"))
    }

    @Test
    fun `una categoria propia sin nada fijado usa los tipos con los que se la vio`() {
        assertEquals(
            setOf(TransactionType.EXPENSE),
            effectiveCategoryTypes("Carro", null, setOf(TransactionType.EXPENSE)),
        )
    }

    @Test
    fun `una categoria propia sin uso conocido no tiene tipo — y eso significa mostrarla`() {
        // Vacío es "no se sabe", no "de ninguno". Quien filtra tiene que mostrarla igual.
        assertEquals(emptySet(), effectiveCategoryTypes("Colegio", null, emptySet()))
    }

    @Test
    fun `un tipo fijado con un valor raro no rompe — se ignora`() {
        assertEquals(emptySet(), effectiveCategoryTypes("Carro", "LO_QUE_SEA"))
    }

    @Test
    fun `los valores validos de tipo son los tres del selector`() {
        assertEquals(setOf("EXPENSE", "INCOME", "BOTH"), CATEGORY_TYPE_VALUES)
    }

    // ── El catálogo, tal como está hoy ────────────────────────────────────────

    @Test
    fun `Otros y Otros ingresos siguen existiendo por separado en el catalogo`() {
        // Este test documenta el punto de partida a propósito: la duplicación NO se resuelve
        // cambiando la constante (eso reescribiría el catálogo de todos los usuarios), sino
        // dejando que el dueño las unifique desde la pantalla si decide que son la misma.
        assertTrue(PREDEFINED_CATEGORIES.any { it.name == "Otros" && it.type == "EXPENSE" })
        assertTrue(PREDEFINED_CATEGORIES.any { it.name == "Otros ingresos" && it.type == "INCOME" })
    }

    @Test
    fun `ninguna categoria del catalogo se llama igual que otra`() {
        val nombres = PREDEFINED_CATEGORIES.map { it.name.lowercase() }
        assertEquals(nombres.size, nombres.distinct().size)
    }
}
