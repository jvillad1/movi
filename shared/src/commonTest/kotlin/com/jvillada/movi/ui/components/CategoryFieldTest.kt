package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F35: [suggestCategoryMatches] es el filtro puro detrás de `CategoryField` — texto libre con
 * sugerencias, compartido por QuickAdd, Presupuestos (crear) y Recurrentes (crear/editar).
 */
class CategoryFieldTest {

    @Test
    fun `sin texto sugiere las predefinidas del tipo, en orden alfabetico`() {
        // Antes salían en el orden en que alguien las escribió en `PREDEFINED_CATEGORIES`
        // («Salario, Freelance, Arriendo recibido, …»), que es lo que el dueño leyó como
        // «cualquier orden».
        val result = suggestCategoryMatches(query = "", type = TransactionType.INCOME)
        assertEquals(
            listOf("Arriendo recibido", "Freelance", "Inversiones", "Otros ingresos", "Salario"),
            result,
        )
    }

    @Test
    fun `filtra por tipo y BOTH, sin mezclar categorias del otro tipo`() {
        val result = suggestCategoryMatches(query = "", type = TransactionType.EXPENSE)
        assert(result.none { it == "Salario" || it == "Freelance" })
        assert("Comida" in result)
    }

    @Test
    fun `contiene ignora mayusculas`() {
        val result = suggestCategoryMatches(query = "COMI", type = TransactionType.EXPENSE)
        assertEquals(listOf("Comida"), result)
    }

    @Test
    fun `contiene ignora tildes tanto en la consulta como en el nombre`() {
        // "tecnologia" sin tilde debe encontrar "Tecnología" (con tilde).
        val result = suggestCategoryMatches(query = "tecnologia", type = TransactionType.EXPENSE)
        assertEquals(listOf("Tecnología"), result)
    }

    @Test
    fun `contiene ignora tildes escritas por quien pregunta`() {
        // Al revés: si alguien escribe "á" y el nombre no la lleva, también matchea normalizado.
        val result = suggestCategoryMatches(query = "educación", type = TransactionType.EXPENSE)
        assertEquals(listOf("Educación"), result)
    }

    @Test
    fun `una sola lista alfabetica - las propias no van en un bloque aparte`() {
        // Cambio deliberado: antes las propias iban DESPUÉS de todo el catálogo. Con las dos
        // listas ordenadas por separado, el alfabeto arrancaba dos veces y había que recorrer la
        // lista dos veces para encontrar «Mascotas» — la queja que este cambio vino a resolver.
        val result = suggestCategoryMatches(
            query = "",
            type = TransactionType.EXPENSE,
            usedCategories = mapOf("Mascotas" to setOf(TransactionType.EXPENSE)),
        )
        assertEquals(
            listOf(
                "Comida", "Educación", "Entretenimiento", "Mascotas", "Otros",
                "Ropa", "Salud", "Servicios", "Tecnología", "Transporte", "Vivienda",
            ),
            result,
        )
    }

    @Test
    fun `lo que empieza con lo tecleado va antes que lo que apenas lo contiene`() {
        // «co» está adentro de «Bancolombia» y al principio de «Comida». Alfabético puro pondría
        // «Bancolombia» arriba, que es peor que el orden viejo: el resultado que empieza con lo
        // escrito no puede quedar debajo.
        val used = mapOf("Bancolombia" to setOf(TransactionType.EXPENSE))
        val result = suggestCategoryMatches("co", TransactionType.EXPENSE, used)
        assertEquals(listOf("Comida", "Bancolombia"), result)
    }

    @Test
    fun `dentro del grupo que empieza con lo tecleado, tambien alfabetico`() {
        val result = suggestCategoryMatches("sal", TransactionType.EXPENSE)
        assertEquals(listOf("Salud"), result)
        assertEquals(listOf("Salario"), suggestCategoryMatches("sal", TransactionType.INCOME))
        assertEquals(listOf("Salario", "Salud"), suggestCategoryMatches("sal", type = null))
    }

    @Test
    fun `el orden ignora tildes y pone la enie despues de la n`() {
        // La lista con la que se verificó de verdad: «Ñoquis» no puede caer después de «Zapatos»,
        // y «Ñandú» ordena entre «Nueces» y «Ñoquis».
        val used = listOf("Zapatos", "Ñoquis", "Ñandú", "Nueces", "Árbol", "Ángel")
            .associateWith { emptySet<TransactionType>() }
        val result = suggestCategoryMatches("", TransactionType.EXPENSE, used)
        val propias = result.filter { it in used.keys }
        assertEquals(listOf("Ángel", "Árbol", "Nueces", "Ñandú", "Ñoquis", "Zapatos"), propias)
        // Y quedan intercaladas con el catálogo, no en un bloque aparte: «Ángel» va primera de
        // toda la lista y «Zapatos» última.
        assertEquals("Ángel", result.first())
        assertEquals("Zapatos", result.last())
    }

    @Test
    fun `una usada que ya es predefinida no se duplica`() {
        val result = suggestCategoryMatches(
            query = "",
            type = TransactionType.EXPENSE,
            usedCategories = mapOf("comida" to emptySet(), "Salud" to emptySet()), // minúscula a propósito
        )
        assertEquals(1, result.count { it.equals("Comida", ignoreCase = true) })
    }

    @Test
    fun `usadas en blanco o repetidas se ignoran`() {
        val result = suggestCategoryMatches(
            query = "masc",
            type = null,
            usedCategories = mapOf("Mascotas" to emptySet(), "  " to emptySet(), "" to emptySet()),
        )
        assertEquals(listOf("Mascotas"), result)
    }

    @Test
    fun `sin tipo no filtra el catalogo por lado`() {
        val result = suggestCategoryMatches(query = "otro", type = null)
        assert("Otros" in result)
        assert("Otros ingresos" in result)
    }

    @Test
    fun `una consulta sin coincidencias no sugiere nada`() {
        val result = suggestCategoryMatches(query = "xyzxyz", type = TransactionType.EXPENSE)
        assertEquals(emptyList(), result)
    }

    // ── Ola 9 · A3: las categorías propias se ofrecen según cómo se usaron ─────────────

    @Test
    fun `una categoria propia usada solo en gastos no se ofrece al anotar un ingreso`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        assert("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Carro" !in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `una categoria propia usada de los dos lados se ofrece en los dos`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE, TransactionType.INCOME))
        assert("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Carro" in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `ante la duda se muestra - sin tipos conocidos se ofrece en los dos lados`() {
        // Arranque en frío: la conocemos (vino de un presupuesto, de una regla vieja) pero no
        // sabemos de qué lado. Esconderla por falta de datos sería peor que sugerirla de más.
        val used = mapOf("Colegio" to emptySet<TransactionType>())
        assert("Colegio" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Colegio" in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `sin tipo se ofrecen todas las propias, del lado que sean`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        assert("Carro" in suggestCategoryMatches("", type = null, usedCategories = used))
    }

    // ── Ola 9 · A1: la opción de crear ────────────────────────────────────────────────

    @Test
    fun `ofrece crear cuando lo escrito no coincide con ninguna sugerencia`() {
        val matches = suggestCategoryMatches("Carro", TransactionType.EXPENSE)
        assertEquals(emptyList(), matches)
        assertTrue(shouldOfferCreateCategory("Carro", matches))
    }

    @Test
    fun `coincidencia parcial - se ve la sugerencia Y la opcion de crear`() {
        // "Sal" con "Salario" en el catálogo: las dos cosas, sin que una tape a la otra.
        val matches = suggestCategoryMatches("Sal", TransactionType.INCOME)
        assertEquals(listOf("Salario"), matches)
        assertTrue(shouldOfferCreateCategory("Sal", matches))
    }

    @Test
    fun `no ofrece crear lo que ya existe, ni con otras mayusculas o tildes`() {
        val matches = suggestCategoryMatches("educacion", TransactionType.EXPENSE)
        assertEquals(listOf("Educación"), matches)
        assertFalse(shouldOfferCreateCategory("educacion", matches))
    }

    /**
     * Ola 9 · B4: «Carro» existe (como gasto) aunque el filtro por tipo la esconda al anotar un
     * ingreso. Ofrecer «Crear "Carro"» ahí prometía algo nuevo que no crea nada.
     */
    @Test
    fun `no ofrece crear algo que ya existe aunque el tipo lo esconda`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val matches = suggestCategoryMatches("Carro", TransactionType.INCOME, used)

        assertEquals(emptyList(), matches, "el filtro por tipo la esconde")
        assertFalse(shouldOfferCreateCategory("Carro", matches, conocidas = used.keys))
        // Pero una que de verdad no existe sí se ofrece.
        assertTrue(shouldOfferCreateCategory("Moto", matches, conocidas = used.keys))
    }

    @Test
    fun `no ofrece crear con el campo vacio o en blanco`() {
        assertFalse(shouldOfferCreateCategory("", emptyList()))
        assertFalse(shouldOfferCreateCategory("   ", emptyList()))
    }

    /**
     * Ola 9 · A4: el hueco entre las dos guardas anteriores. El filtro por tipo escondía la
     * categoría que existe y la guarda anti-duplicado escondía el «Crear»: el panel quedaba
     * completamente vacío, de pantalla completa y sin explicación.
     */
    @Test
    fun `lo que existe del otro lado se ofrece como usar, no como crear`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val matches = suggestCategoryMatches("Carro", TransactionType.INCOME, used)
        val conocidas = used.keys + PREDEFINED_CATEGORIES.map { it.name }

        assertEquals(emptyList(), matches)
        assertFalse(shouldOfferCreateCategory("Carro", matches, conocidas))
        assertTrue(shouldOfferKnownFromOtherSide("Carro", matches, conocidas))
        assertEquals(
            "Ya la tienes en Gastos",
            ladoConocidoDeCategoria("Carro", TransactionType.INCOME, used),
        )
    }

    @Test
    fun `crear y usar nunca salen las dos a la vez`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val conocidas = used.keys + PREDEFINED_CATEGORIES.map { it.name }
        for (q in listOf("Carro", "Moto", "Comida", "Sal", "")) {
            val matches = suggestCategoryMatches(q, TransactionType.EXPENSE, used)
            assertFalse(
                shouldOfferCreateCategory(q, matches, conocidas) &&
                    shouldOfferKnownFromOtherSide(q, matches, conocidas),
                "«$q» ofrecía crear Y usar",
            )
        }
    }

    @Test
    fun `una categoria que ya se ve entre las sugerencias no se ofrece como usar`() {
        val matches = suggestCategoryMatches("Comida", TransactionType.EXPENSE)
        assertTrue(matches.contains("Comida"))
        assertFalse(shouldOfferKnownFromOtherSide("Comida", matches, matches))
    }

    @Test
    fun `una del catalogo escondida por tipo se explica con el lado que le toca`() {
        // «Salario» es del catálogo y es INCOME: anotando un gasto no aparece entre las sugerencias.
        val matches = suggestCategoryMatches("Salario", TransactionType.EXPENSE)
        assertEquals(emptyList(), matches)
        assertTrue(shouldOfferKnownFromOtherSide("Salario", matches, PREDEFINED_CATEGORIES.map { it.name }))
        assertEquals("Ya la tienes en Ingresos", ladoConocidoDeCategoria("Salario", TransactionType.EXPENSE))
    }

    @Test
    fun `usar elige como esta escrita la que ya existe, no lo tecleado`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        assertEquals("Carro", nombreCanonicoConocido("carro", used))
        assertEquals("Educación", nombreCanonicoConocido("educacion"))
        assertNull(nombreCanonicoConocido("Moto", used))
        assertNull(nombreCanonicoConocido("   "))
    }

    // ── Ola 10: lo que el dueño decidió en «Más → Categorías» ─────────────────
    // Esconder y fijar el tipo solo sirven de algo si se notan ACÁ, en el campo donde escribe la
    // categoría. Estos tests son la única prueba de que la pantalla nueva hace algo.

    @Test
    fun `una categoria del catalogo escondida deja de sugerirse`() {
        val prefs = mapOf("Ropa" to CategoryPref(hidden = true))
        val result = suggestCategoryMatches("", TransactionType.EXPENSE, prefs = prefs)
        assertFalse("Ropa" in result)
        assertTrue("Comida" in result, "las demás siguen igual")
    }

    @Test
    fun `una categoria propia escondida deja de sugerirse`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val prefs = mapOf("Carro" to CategoryPref(hidden = true))
        assertFalse("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used, prefs))
        assertTrue("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
    }

    @Test
    fun `esconder no depende de como este escrito el nombre`() {
        // El caché guarda lo que tecleó el dueño; la preferencia viene del server. Una diferencia
        // de mayúscula o de tilde no puede hacer reaparecer lo que escondió.
        val used = mapOf("educacion" to setOf(TransactionType.EXPENSE))
        val prefs = mapOf("Educación" to CategoryPref(hidden = true))
        val result = suggestCategoryMatches("", TransactionType.EXPENSE, used, prefs)
        assertFalse(result.any { it.equals("educacion", ignoreCase = true) })
    }

    @Test
    fun `Otros fijada en Ambos se ofrece anotando un ingreso`() {
        // El caso del que salió toda la ola: «Otros» está clavada en EXPENSE en el catálogo, y
        // por eso existía «Otros ingresos» duplicándola. Fijarla en «Ambos» la libera.
        val prefs = mapOf("Otros" to CategoryPref(pinnedType = "BOTH"))
        assertTrue("Otros" in suggestCategoryMatches("", TransactionType.INCOME, prefs = prefs))
        assertTrue("Otros" in suggestCategoryMatches("", TransactionType.EXPENSE, prefs = prefs))
        assertFalse("Otros" in suggestCategoryMatches("", TransactionType.INCOME))
    }

    @Test
    fun `fijar el tipo de una propia le gana a lo aprendido del uso`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val prefs = mapOf("Carro" to CategoryPref(pinnedType = "INCOME"))
        assertTrue("Carro" in suggestCategoryMatches("", TransactionType.INCOME, used, prefs))
        assertFalse("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used, prefs))
    }

    // ── Los tres defectos que la revisión encontró en «Agregar» ──────────────
    // Los tres eran de la misma familia: la pantalla donde el dueño anota todos los días seguía
    // leyendo el `type` clavado del catálogo y no sabía nada de `prefs`.

    @Test
    fun `una escondida no puede ser el valor inicial del campo`() {
        // B1: el campo arrancaba diciendo «Comida» aunque él acabara de esconderla — a un toque
        // de «Guardar movimiento» de anotar un gasto en la categoría que retiró.
        val prefs = mapOf("Comida" to CategoryPref(hidden = true))
        val inicial = categoriaPorDefectoPara(TransactionType.EXPENSE, prefs = prefs)
        assertFalse(inicial == "Comida")
        assertEquals("Comida", categoriaPorDefectoPara(TransactionType.EXPENSE))
    }

    @Test
    fun `ordenar alfabeticamente NO cambia con que categoria arranca el campo`() {
        // El orden alfabético es de las LISTAS. Si el valor inicial saliera de «la primera
        // sugerencia», anotar un ingreso arrancaría en «Arriendo recibido» en vez de «Salario»:
        // un cambio que nadie pidió, en la pantalla que se usa todos los días.
        assertEquals("Salario", categoriaPorDefectoPara(TransactionType.INCOME))
        assertEquals("Comida", categoriaPorDefectoPara(TransactionType.EXPENSE))
        assertEquals("Arriendo recibido", suggestCategoryMatches("", TransactionType.INCOME).first())
    }

    @Test
    fun `si escondio todas las del catalogo igual queda un valor inicial`() {
        val prefs = PREDEFINED_CATEGORIES.associate { it.name to CategoryPref(hidden = true) }
        assertTrue(categoriaPorDefectoPara(TransactionType.EXPENSE, prefs = prefs).isNotEmpty())
    }

    @Test
    fun `con una escondida escrita, el panel sigue mostrando todas las demas`() {
        // B2: `matches` quedaba vacío y el panel se reducía a «Usar "Comida"» — para elegir otra
        // había que borrar el campo primero. Esconder una hacía desaparecer todas.
        val prefs = mapOf("Comida" to CategoryPref(hidden = true))
        val conocidas = PREDEFINED_CATEGORIES.map { it.name }
        val matches = categoriasParaElPanel("Comida", TransactionType.EXPENSE, prefs = prefs)
        // La escondida no está…
        assertFalse("Comida" in matches)
        // …pero el resto del catálogo sí, y se la puede seguir usando a mano si insiste.
        assertTrue("Transporte" in matches)
        assertTrue(shouldOfferKnownFromOtherSide("Comida", matches, conocidas))
    }

    @Test
    fun `el panel sigue filtrando cuando lo escrito NO es una categoria conocida`() {
        // La otra mitad de la regla: escribir «Trans» tiene que acotar, no listar todo.
        val matches = categoriasParaElPanel("Trans", TransactionType.EXPENSE)
        assertEquals(listOf("Transporte"), matches)
    }

    @Test
    fun `con el campo vacio el panel lista todas las del tipo`() {
        assertEquals(
            suggestCategoryMatches("", TransactionType.INCOME),
            categoriasParaElPanel("", TransactionType.INCOME),
        )
    }

    @Test
    fun `una categoria fijada en Ambos sirve para los dos tipos`() {
        // B3: al pasar de Gasto a Ingreso, «Otros» fijada en «Ambos» se reemplazaba en silencio
        // por «Salario» — leía el EXPENSE del catálogo e ignoraba lo que él acababa de decidir.
        val prefs = mapOf("Otros" to CategoryPref(pinnedType = "BOTH"))
        assertTrue(categoriaSirveParaTipo("Otros", TransactionType.INCOME, prefs = prefs))
        assertTrue(categoriaSirveParaTipo("Otros", TransactionType.EXPENSE, prefs = prefs))
        // Sin fijar, sigue siendo solo de gastos.
        assertFalse(categoriaSirveParaTipo("Otros", TransactionType.INCOME))
    }

    @Test
    fun `una escondida no sirve para ningun tipo`() {
        val prefs = mapOf("Ropa" to CategoryPref(hidden = true))
        assertFalse(categoriaSirveParaTipo("Ropa", TransactionType.EXPENSE, prefs = prefs))
    }

    @Test
    fun `una categoria propia sin nada declarado sirve para cualquier tipo`() {
        // Ante la duda no se le saca del campo lo que escribió a mano.
        assertTrue(categoriaSirveParaTipo("Colegio", TransactionType.INCOME))
        assertTrue(categoriaSirveParaTipo("Colegio", TransactionType.EXPENSE))
    }

    @Test
    fun `las reservadas nunca se sugieren en el campo`() {
        // «Pago de tarjeta» está en el catálogo Y es reservada: se ofrecía al anotar un gasto y,
        // si el dueño la elegía, isCashFlow sacaba ese gasto real de «Gastos del mes» en silencio.
        val todas = suggestCategoryMatches("", TransactionType.EXPENSE)
        assertFalse(CARD_PAYMENT_CATEGORY in todas)
        assertFalse(categoriaSirveParaTipo(CARD_PAYMENT_CATEGORY, TransactionType.EXPENSE))
        assertFalse(categoriaPorDefectoPara(TransactionType.EXPENSE) == CARD_PAYMENT_CATEGORY)
        // Y tampoco entra por la puerta de las propias, si quedó en algún movimiento viejo.
        val used = mapOf(TRANSFER_CATEGORY to setOf(TransactionType.EXPENSE))
        assertFalse(TRANSFER_CATEGORY in suggestCategoryMatches("", TransactionType.EXPENSE, used))
    }

    @Test
    fun `en frio, el nombre canonico sale de las preferencias`() {
        // `prefs` se persiste y `used` no: tras una recarga sin red, escribir «carro» reconocía la
        // categoría (el panel mira las claves de prefs) pero se guardaba «carro» en minúscula —
        // la categoría partida en dos por una mayúscula, que es como se cruzan presupuesto y gasto.
        val prefs = mapOf("Carro" to CategoryPref(pinnedType = "EXPENSE"))
        assertEquals("Carro", nombreCanonicoConocido("carro", emptyMap(), prefs))
        // Sin las preferencias (el estado viejo) devolvía null y se guardaba lo tecleado.
        assertNull(nombreCanonicoConocido("carro", emptyMap()))
    }

    @Test
    fun `con Ambos fijado desaparece el cartel de que la tienes del otro lado`() {
        // Sin fijar nada, anotando un ingreso «Otros» no se sugiere (el catálogo la tiene en
        // EXPENSE) y el panel explica por qué. Con «Ambos» fijado ya se sugiere, así que no queda
        // nada que explicar: el cartel es la consecuencia de esconderla, no un adorno.
        val conocidas = PREDEFINED_CATEGORIES.map { it.name }
        val sinFijar = suggestCategoryMatches("Otros", TransactionType.INCOME)
        assertFalse("Otros" in sinFijar)
        assertTrue(shouldOfferKnownFromOtherSide("Otros", sinFijar, conocidas))
        assertEquals("Ya la tienes en Gastos", ladoConocidoDeCategoria("Otros", TransactionType.INCOME))

        val prefs = mapOf("Otros" to CategoryPref(pinnedType = "BOTH"))
        val fijada = suggestCategoryMatches("Otros", TransactionType.INCOME, prefs = prefs)
        assertTrue("Otros" in fijada)
        assertFalse(shouldOfferKnownFromOtherSide("Otros", fijada, conocidas))
    }
}
