package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Las reglas de la pantalla «Categorías», sin Compose. Lo que se fija acá es sobre todo que el
 * **tipo sea un filtro** y no la identidad de la categoría: es la decisión de diseño de toda la
 * ola, y es la que se rompe sola si alguien vuelve a leer `Category.type` directo.
 */
class CategoriasLogicTest {

    private fun cat(
        name: String,
        scope: CategoryScope = CategoryScope.CUSTOM,
        usedTypes: List<TransactionType> = emptyList(),
        pinnedType: String? = null,
        hidden: Boolean = false,
        reserved: Boolean = false,
        movements: Int = 0,
        total: Long = 0,
        incomeTotal: Long = 0,
        monthMovements: Int = 0,
        monthTotal: Long = 0,
        monthIncomeTotal: Long = 0,
        budgets: Int = 0,
        recurringRules: Int = 0,
        otherCurrencyMovements: Int = 0,
    ) = CategoryUsage(
        name = name, scope = scope, reserved = reserved, usedTypes = usedTypes,
        pinnedType = pinnedType, hidden = hidden, movements = movements, total = total,
        incomeTotal = incomeTotal,
        monthMovements = monthMovements, monthTotal = monthTotal,
        monthIncomeTotal = monthIncomeTotal,
        otherCurrencyMovements = otherCurrencyMovements,
        budgets = budgets, recurringRules = recurringRules,
    )

    // ── El filtro por tipo ────────────────────────────────────────────────────

    @Test
    fun `Todas muestra tambien las escondidas`() {
        // Un filtro llamado «Todas» que esconde cosas sería mentir — y además es donde el dueño
        // va a buscar la que escondió por error.
        val lista = listOf(cat("Ropa", hidden = true), cat("Comida"))
        // (Salen alfabéticas, no en el orden en que llegaron — ver los tests de orden más abajo.)
        assertEquals(listOf("Comida", "Ropa"), filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name })
    }

    // ── El orden de la lista ──────────────────────────────────────────────────
    // «No me gusta que las categorías no estén tipo orden alfabético sino en cualquier orden.»
    // El «cualquier orden» era el del server: lo más usado primero. Ese orden tenía su razón
    // escrita —reconocer lo que sobra por contraste con lo que se usa— y no se pierde: cada
    // renglón sigue diciendo su uso. Lo que se gana es poder encontrar una por su nombre.

    @Test
    fun `la lista sale en orden alfabetico, no por uso`() {
        val lista = listOf(
            cat("Vivienda", movements = 90),
            cat("Comida", movements = 300),
            cat("Ñoquis", movements = 1),
            cat("Educación", movements = 40),
            cat("Ácido fólico", movements = 0),
        )
        assertEquals(
            listOf("Ácido fólico", "Comida", "Educación", "Ñoquis", "Vivienda"),
            filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name },
        )
    }

    @Test
    fun `las reservadas no aparecen en ninguna lista`() {
        // Ola B, tarea 5: no se pueden tocar (ni renombrar, ni unificar, ni esconder), así que se
        // sacan de raíz en vez de mostrarse como renglones muertos — en cualquier filtro.
        val lista = listOf(cat("Vivienda"), cat("Cuenta eliminada", reserved = true), cat("Comida"))
        assertEquals(
            listOf("Comida", "Vivienda"),
            filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name },
        )
        assertEquals(emptyList(), filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name })
    }

    @Test
    fun `una reservada escondida tampoco aparece en Escondidas`() {
        val lista = listOf(cat("Cuenta eliminada", reserved = true, hidden = true), cat("Comida"))
        assertEquals(emptyList(), filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name })
    }

    @Test
    fun `buscando, lo que empieza con lo escrito va antes que lo que apenas lo contiene`() {
        val lista = listOf(cat("Bancolombia"), cat("Comida"))
        assertEquals(
            listOf("Comida", "Bancolombia"),
            filtrarCategorias(lista, CategoryFilter.TODAS, "co").map { it.name },
        )
    }

    @Test
    fun `el orden dentro de cada pastilla de filtro tambien es alfabetico`() {
        val lista = listOf(
            cat("Vivienda", usedTypes = listOf(TransactionType.EXPENSE), hidden = true),
            cat("Comida", usedTypes = listOf(TransactionType.EXPENSE), hidden = true),
            cat("Salud", usedTypes = listOf(TransactionType.EXPENSE)),
            cat("Arriendo", usedTypes = listOf(TransactionType.EXPENSE)),
        )
        assertEquals(
            listOf("Arriendo", "Salud"),
            filtrarCategorias(lista, CategoryFilter.GASTOS).map { it.name },
        )
        assertEquals(
            listOf("Comida", "Vivienda"),
            filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name },
        )
    }

    @Test
    fun `Gastos deja fuera las escondidas`() {
        val lista = listOf(cat("Ropa", usedTypes = listOf(TransactionType.EXPENSE), hidden = true))
        assertTrue(filtrarCategorias(lista, CategoryFilter.GASTOS).isEmpty())
    }

    @Test
    fun `una categoria en Ambos aparece en Gastos Y en Ingresos`() {
        // El corazón del cambio: «Otros» fijada en «Ambos» sirve para las dos cosas.
        val lista = listOf(cat("Otros", scope = CategoryScope.PREDEFINED, pinnedType = CATEGORY_TYPE_BOTH))
        assertEquals(listOf("Otros"), filtrarCategorias(lista, CategoryFilter.GASTOS).map { it.name })
        assertEquals(listOf("Otros"), filtrarCategorias(lista, CategoryFilter.INGRESOS).map { it.name })
    }

    @Test
    fun `el tipo fijado le gana al catalogo tambien en el filtro`() {
        // «Comida» es EXPENSE en el catálogo; fijada en INCOME sale de Gastos y entra a Ingresos.
        val lista = listOf(cat("Comida", scope = CategoryScope.PREDEFINED, pinnedType = "INCOME"))
        assertTrue(filtrarCategorias(lista, CategoryFilter.GASTOS).isEmpty())
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.INGRESOS).size)
    }

    @Test
    fun `una categoria propia sin uso conocido aparece en los dos filtros`() {
        // Ante la duda, mostrar: esconder por falta de datos es peor que sugerir de más.
        val lista = listOf(cat("Colegio"))
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.GASTOS).size)
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.INGRESOS).size)
    }

    @Test
    fun `Escondidas muestra solo las escondidas`() {
        val lista = listOf(cat("Ropa", hidden = true), cat("Comida"))
        assertEquals(listOf("Ropa"), filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name })
    }

    @Test
    fun `la busqueda ignora tildes y mayusculas`() {
        val lista = listOf(cat("Educación"), cat("Comida"))
        assertEquals(listOf("Educación"), filtrarCategorias(lista, CategoryFilter.TODAS, "EDUCACION").map { it.name })
    }

    @Test
    fun `la busqueda tambien ignora la dieresis`() {
        // Ordenaba como «pinguinos» pero no se encontraba escribiéndolo así.
        val lista = listOf(cat("Pingüinos"), cat("Comida"))
        assertEquals(
            listOf("Pingüinos"),
            filtrarCategorias(lista, CategoryFilter.TODAS, "pinguinos").map { it.name },
        )
    }

    // ── Las etiquetas ─────────────────────────────────────────────────────────

    @Test
    fun `la etiqueta de tipo dice lo fijado, no lo del catalogo`() {
        // Ola B, tarea 5: «Gasto e ingreso» y no «Ambos» — la palabra que ahora se dice, y solo
        // en la hoja de detalle, nunca en la fila.
        assertEquals("Gasto e ingreso", etiquetaDeTipo(cat("Otros", scope = CategoryScope.PREDEFINED, pinnedType = CATEGORY_TYPE_BOTH)))
        assertEquals("Gasto", etiquetaDeTipo(cat("Comida", scope = CategoryScope.PREDEFINED)))
        assertEquals("Ingreso", etiquetaDeTipo(cat("Salario", scope = CategoryScope.PREDEFINED)))
        assertEquals("Sin usar", etiquetaDeTipo(cat("Colegio")))
    }

    @Test
    fun `el resumen de uso dice movimientos y plata`() {
        val resumen = resumenDeUso(cat("Comida", movements = 12, total = 450_000))
        assertTrue(resumen.contains("12 movimientos"), resumen)
        assertTrue(resumen.contains("450"), resumen)
    }

    @Test
    fun `los gastos y los ingresos NO se suman en un solo numero`() {
        // Los importes se guardan en positivo y el signo lo lleva el tipo: un solo total daba
        // «$130.000» para 100k de gasto y 30k de ingreso — un número sin significado, justo en
        // una categoría de tipo «Ambos», que es el caso que esta ola habilita.
        val resumen = resumenDeUso(
            cat("Otros", movements = 4, total = 100_000, incomeTotal = 30_000, pinnedType = CATEGORY_TYPE_BOTH),
        )
        assertTrue(resumen.contains("100.000"), resumen)
        assertTrue(resumen.contains("30.000"), resumen)
        assertFalse(resumen.contains("130.000"), resumen)
    }

    @Test
    fun `el resumen del mes tambien separa gasto de ingreso`() {
        val resumen = resumenDelMes(cat("Otros", monthMovements = 2, monthTotal = 10_000, monthIncomeTotal = 5_000))!!
        assertTrue(resumen.contains("10.000"), resumen)
        assertTrue(resumen.contains("5.000"), resumen)
        assertFalse(resumen.contains("15.000"), resumen)
    }

    @Test
    fun `una categoria sin nada detras lo dice`() {
        assertEquals("Sin movimientos", resumenDeUso(cat("Freelance", scope = CategoryScope.PREDEFINED)))
    }

    @Test
    fun `los movimientos en otra moneda se cuentan aparte y no se suman`() {
        // Sumar dólares y pesos en un solo número sería mentir.
        val resumen = resumenDeUso(cat("Tecnología", movements = 2, total = 100_000, otherCurrencyMovements = 3))
        assertTrue(resumen.contains("3 en otra moneda"), resumen)
        assertTrue(resumen.contains("2 movimientos"), resumen)
    }

    @Test
    fun `el resumen del mes es nulo si no la uso este mes`() {
        assertEquals(null, resumenDelMes(cat("Comida", movements = 12, total = 450_000)))
        assertTrue(resumenDelMes(cat("Comida", monthMovements = 3, monthTotal = 80_000))!!.contains("3 movimientos"))
    }

    // ── El aviso antes de unificar ────────────────────────────────────────────

    @Test
    fun `renombrar choca con otra categoria aunque cambien las tildes o las mayusculas`() {
        val transporte = cat("Transporte")
        val existentes = listOf(transporte, cat("Alimentación", scope = CategoryScope.PREDEFINED))
        assertEquals("Alimentación", colisionAlRenombrar(transporte, "alimentacion", existentes)?.name)
        assertEquals("Alimentación", colisionAlRenombrar(transporte, "  ALIMENTACIÓN ", existentes)?.name)
        assertEquals(null, colisionAlRenombrar(transporte, "Mercado", existentes))
        assertEquals(null, colisionAlRenombrar(transporte, "   ", existentes))
    }

    @Test
    fun `corregir la tilde de la propia categoria no es una colision`() {
        val propia = cat("Alimentacion")
        assertEquals(null, colisionAlRenombrar(propia, "Alimentación", listOf(propia, cat("Mercado"))))
    }

    @Test
    fun `el aviso de unificar dice cuantos movimientos cambian de nombre`() {
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1, recurringRules = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED),
        )
        assertTrue(aviso.contains("2 movimientos"), aviso)
        assertTrue(aviso.contains("su presupuesto"), aviso)
        assertTrue(aviso.contains("1 recurrente"), aviso)
        assertTrue(aviso.contains("Transporte"), aviso)
        assertTrue(aviso.contains("No se borra nada"), aviso)
    }

    @Test
    fun `si las dos tienen presupuesto, el aviso lo dice ANTES y avisa que no se deshace`() {
        // La suma de los dos límites es lo único de la operación que no es «el mismo dato con
        // otro nombre»: le cambia un número que el dueño puso a propósito, y es irreversible.
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED, budgets = 1),
        )
        assertTrue(aviso.contains("presupuesto"), aviso)
        assertTrue(aviso.contains("se suman"), aviso)
        assertTrue(aviso.contains("no se puede deshacer"), aviso)
    }

    @Test
    fun `si solo una tiene presupuesto no se habla de sumar nada`() {
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED),
        )
        assertFalse(aviso.contains("se suman"), aviso)
    }

    @Test
    fun `unificar una categoria vacia lo dice en vez de prometer un cambio`() {
        val aviso = avisoDeUnificacion(
            cat("Otros ingresos", scope = CategoryScope.PREDEFINED),
            cat("Otros", scope = CategoryScope.PREDEFINED),
        )
        assertTrue(aviso.contains("Nada cambia de nombre"), aviso)
    }

    @Test
    fun `la etiqueta del selector de tipo cubre las cuatro opciones`() {
        assertEquals("Automático", etiquetaDeTipoFijado(null))
        assertEquals("Gasto", etiquetaDeTipoFijado("EXPENSE"))
        assertEquals("Ingreso", etiquetaDeTipoFijado("INCOME"))
        assertEquals("Ambos", etiquetaDeTipoFijado(CATEGORY_TYPE_BOTH))
    }

    // ── La fila compacta (Ola B, tarea 5) ────────────────────────────────────

    @Test
    fun `el resumen corto solo cuenta movimientos, sin plata ni presupuesto`() {
        assertEquals("12 movimientos", resumenDeUsoCorto(cat("Comida", movements = 12, total = 450_000, budgets = 1)))
        assertEquals("1 movimiento", resumenDeUsoCorto(cat("Comida", movements = 1)))
        assertEquals("Sin movimientos", resumenDeUsoCorto(cat("Comida")))
    }

    @Test
    fun `el resumen corto cuenta tambien los de otra moneda`() {
        assertEquals("5 movimientos", resumenDeUsoCorto(cat("Tecnología", movements = 2, otherCurrencyMovements = 3)))
    }

    @Test
    fun `una categoria con presupuesto pero sin movimientos dice Sin movimientos en el resumen corto`() {
        // El resumen corto es de MOVIMIENTOS: tener un presupuesto sin haber anotado nada todavía
        // sigue siendo, literalmente, no tener movimientos.
        assertEquals("Sin movimientos", resumenDeUsoCorto(cat("Colegio", budgets = 1)))
    }

    @Test
    fun `una categoria escondida lo dice en el resumen corto, no solo con el color`() {
        // Fix round 1: sin las etiquetas de la fila vieja, el color gris no bastaba para decir
        // «está escondida» — acá se dice con palabras, en el mismo renglón.
        assertEquals("Escondida · 3 movimientos", resumenDeUsoCorto(cat("Ropa", movements = 3, hidden = true)))
        assertEquals("Escondida · Sin movimientos", resumenDeUsoCorto(cat("Ropa", hidden = true)))
    }

    @Test
    fun `la cifra del mes es null si no la uso este mes`() {
        assertEquals(null, cifraDelMes(cat("Comida", movements = 12, total = 450_000)))
    }

    @Test
    fun `la cifra del mes separa gasto de ingreso, sin la oracion completa`() {
        val cifra = cifraDelMes(cat("Otros", monthMovements = 2, monthTotal = 10_000, monthIncomeTotal = 5_000))!!
        assertTrue(cifra.contains("10.000"), cifra)
        assertTrue(cifra.contains("5.000"), cifra)
        assertFalse(cifra.contains("Este mes"), cifra)
        assertFalse(cifra.contains("movimiento"), cifra)
    }

    @Test
    fun `los rotulos del catalogo se leen para la vista previa de la hoja de detalle`() {
        assertEquals("Restaurante", rotuloDeIcono("restaurante"))
        assertEquals("Naranja", rotuloDeColor("naranja"))
        // Una clave desconocida (de una versión más nueva) se muestra tal cual, no revienta.
        assertEquals("no-existe", rotuloDeIcono("no-existe"))
    }

    // ── «Ordena tus categorías» (Ola B, tarea 6) ─────────────────────────────

    @Test
    fun `Credito con 1 movimiento se propone unificar en Cuota de credito con 10`() {
        val credito = cat("Crédito", movements = 1)
        val cuotaDeCredito = cat("Cuota de crédito", movements = 10)
        val propuestas = propuestasDeOrden(listOf(credito, cuotaDeCredito))
        val unificar = propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().single()
        assertEquals("Crédito", unificar.origen.name)
        assertEquals("Cuota de crédito", unificar.destino.name)
        assertEquals(
            "«Crédito» tiene 1 movimiento; «Cuota de crédito» tiene 10.",
            explicacionDePropuesta(unificar),
        )
    }

    @Test
    fun `Mercado de 1 movimiento se propone unificar en Mercado extra de 7`() {
        val mercado = cat("Mercado", movements = 1)
        val mercadoExtra = cat("Mercado extra", movements = 7)
        val propuestas = propuestasDeOrden(listOf(mercado, mercadoExtra))
        val unificar = propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().single()
        assertEquals("Mercado", unificar.origen.name)
        assertEquals("Mercado extra", unificar.destino.name)
    }

    @Test
    fun `no propone unificar si las dos tienen 5 movimientos o mas`() {
        // Ambas bien usadas: lo más probable es que sean categorías distintas a propósito, como
        // «Mercado» y «Mercado extra» cuando las dos siguen vivas.
        val mercado = cat("Mercado", movements = 5)
        val mercadoExtra = cat("Mercado extra", movements = 7)
        val propuestas = propuestasDeOrden(listOf(mercado, mercadoExtra))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().isEmpty())
    }

    @Test
    fun `no propone unificar si no comparten tipo efectivo`() {
        val a = cat("Regalo", usedTypes = listOf(TransactionType.EXPENSE), movements = 1)
        val b = cat("Regalo recibido", usedTypes = listOf(TransactionType.INCOME), movements = 2)
        val propuestas = propuestasDeOrden(listOf(a, b))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().isEmpty())
    }

    @Test
    fun `no propone unificar si el nombre no esta contenido como palabras completas`() {
        // «Cine» no es una palabra completa dentro de «Cocina» — no hay espacio de por medio.
        val a = cat("Cine", movements = 1)
        val b = cat("Cocina", movements = 2)
        val propuestas = propuestasDeOrden(listOf(a, b))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().isEmpty())
    }

    // ── Fix round 1 ───────────────────────────────────────────────────────────

    @Test
    fun `hallazgo 1 - filtra los Ahora no antes de aplicar el tope de 12, no despues`() {
        // 14 candidatas de «un solo uso», nombradas para que el orden alfabético sea A..N. Sin el
        // fix, el tope de 12 se aplicaba ANTES de sacar los descartados: al descartar A y B
        // quedarían C..L (10) y nunca aparecerían M y N, aunque hay lugar de sobra para ellas.
        val letras = ('A'..'N').toList()
        val candidatas = letras.map { cat("Solo uso $it", movements = 1) }
        val descartados = setOf(
            claveDePropuesta(PropuestaDeOrden.UnUso(candidatas[0])), // Solo uso A
            claveDePropuesta(PropuestaDeOrden.UnUso(candidatas[1])), // Solo uso B
        )
        val propuestas = propuestasDeOrden(candidatas, descartados)
        assertEquals(12, propuestas.size)
        assertTrue(propuestas.any { it is PropuestaDeOrden.UnUso && it.categoria.name == "Solo uso M" })
        assertTrue(propuestas.any { it is PropuestaDeOrden.UnUso && it.categoria.name == "Solo uso N" })
    }

    @Test
    fun `hallazgo 4 - no propone unificar si alguna de las dos esta escondida`() {
        val visible = cat("Crédito", movements = 1)
        val escondida = cat("Cuota de crédito", movements = 10, hidden = true)
        assertTrue(propuestasDeOrden(listOf(visible, escondida)).filterIsInstance<PropuestaDeOrden.UnificarParecidas>().isEmpty())

        val otraEscondida = cat("Crédito", movements = 1, hidden = true)
        val otraVisible = cat("Cuota de crédito", movements = 10)
        assertTrue(
            propuestasDeOrden(listOf(otraEscondida, otraVisible)).filterIsInstance<PropuestaDeOrden.UnificarParecidas>().isEmpty(),
        )
    }

    @Test
    fun `hallazgo 5 - no propone unificar si el destino se queda en 0 movimientos`() {
        // Las dos sin uso: no hay nada que ordenar unificándolas (y si alguna es del catálogo,
        // ya la ofrece la regla 2 para esconder).
        val a = cat("Mercado", movements = 0)
        val b = cat("Mercado extra", movements = 0)
        assertTrue(propuestasDeOrden(listOf(a, b)).isEmpty())
    }

    @Test
    fun `hallazgo 5 - en un empate se unifica la contenida (mas corta) en la que la contiene`() {
        val credito = cat("Crédito", movements = 3)
        val cuotaDeCredito = cat("Cuota de crédito", movements = 3)
        // Sin importar en qué orden llegan del server: el resultado es siempre el mismo.
        val propuestasEnUnOrden = propuestasDeOrden(listOf(credito, cuotaDeCredito))
        val propuestasEnElOtroOrden = propuestasDeOrden(listOf(cuotaDeCredito, credito))
        for (propuestas in listOf(propuestasEnUnOrden, propuestasEnElOtroOrden)) {
            val unificar = propuestas.filterIsInstance<PropuestaDeOrden.UnificarParecidas>().single()
            assertEquals("Crédito", unificar.origen.name)
            assertEquals("Cuota de crédito", unificar.destino.name)
        }
    }

    @Test
    fun `Arriendo recibido sin uso se propone esconder`() {
        val arriendoRecibido = cat("Arriendo recibido", scope = CategoryScope.PREDEFINED)
        val propuestas = propuestasDeOrden(listOf(arriendoRecibido))
        val esconder = propuestas.filterIsInstance<PropuestaDeOrden.EsconderNuncaUsada>().single()
        assertEquals("Arriendo recibido", esconder.categoria.name)
    }

    @Test
    fun `una del catalogo ya escondida no se vuelve a proponer`() {
        val yaEscondida = cat("Freelance", scope = CategoryScope.PREDEFINED, hidden = true)
        val propuestas = propuestasDeOrden(listOf(yaEscondida))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.EsconderNuncaUsada>().isEmpty())
    }

    @Test
    fun `Tecnologia del catalogo con 1 movimiento no entra en un solo uso ni en esconder`() {
        // Es del catálogo, así que no es «propia» (regla 3); y tiene uso, así que no es «nunca la
        // usaste» (regla 2). No hay nada que ordenar ahí.
        val tecnologia = cat("Tecnología", scope = CategoryScope.PREDEFINED, movements = 1)
        val propuestas = propuestasDeOrden(listOf(tecnologia))
        assertTrue(propuestas.isEmpty())
    }

    @Test
    fun `una propia con un solo movimiento se propone como un solo uso`() {
        val unaVez = cat("Ñoquis", movements = 1)
        val propuestas = propuestasDeOrden(listOf(unaVez))
        val unUso = propuestas.filterIsInstance<PropuestaDeOrden.UnUso>().single()
        assertEquals("Ñoquis", unUso.categoria.name)
    }

    @Test
    fun `una propia con un solo movimiento pero con presupuesto no es un solo uso`() {
        val conPresupuesto = cat("Ñoquis", movements = 1, budgets = 1)
        val propuestas = propuestasDeOrden(listOf(conPresupuesto))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.UnUso>().isEmpty())
    }

    @Test
    fun `las reservadas nunca entran en ninguna propuesta`() {
        val reservada = cat("Cuenta eliminada", reserved = true, scope = CategoryScope.PREDEFINED)
        val comoPareja = cat("Cuenta eliminada extra", movements = 1)
        val propuestas = propuestasDeOrden(listOf(reservada, comoPareja))
        assertTrue(propuestas.none { it is PropuestaDeOrden.EsconderNuncaUsada && it.categoria.reserved })
        assertTrue(
            propuestas.none {
                it is PropuestaDeOrden.UnificarParecidas &&
                    (it.origen.reserved || it.destino.reserved)
            },
        )
    }

    @Test
    fun `una categoria ya propuesta para unificar no se repite como un solo uso`() {
        // «Crédito» con 1 movimiento también cumple el criterio de «un solo uso», pero ya tiene
        // una propuesta concreta (unificar en Cuota de crédito) — no hace falta preguntar dos
        // veces lo mismo con dos botones distintos.
        val credito = cat("Crédito", movements = 1)
        val cuotaDeCredito = cat("Cuota de crédito", movements = 10)
        val propuestas = propuestasDeOrden(listOf(credito, cuotaDeCredito))
        assertTrue(propuestas.filterIsInstance<PropuestaDeOrden.UnUso>().isEmpty())
        assertEquals(1, propuestas.size)
    }

    @Test
    fun `el orden es unificar parecidas, un solo uso y despues esconder nunca usadas`() {
        val credito = cat("Crédito", movements = 1)
        val cuotaDeCredito = cat("Cuota de crédito", movements = 10)
        val soloUnaVez = cat("Ñoquis", movements = 1)
        val nuncaUsada = cat("Freelance", scope = CategoryScope.PREDEFINED)
        val propuestas = propuestasDeOrden(listOf(credito, cuotaDeCredito, soloUnaVez, nuncaUsada))
        assertEquals(
            listOf(
                PropuestaDeOrden.UnificarParecidas::class,
                PropuestaDeOrden.UnUso::class,
                PropuestaDeOrden.EsconderNuncaUsada::class,
            ),
            propuestas.map { it::class },
        )
    }

    @Test
    fun `no pasa de 12 propuestas`() {
        val muchas = (1..20).map { cat("Solo uso $it", movements = 1) }
        val propuestas = propuestasDeOrden(muchas)
        assertEquals(MAX_PROPUESTAS_DE_ORDEN, propuestas.size)
    }

    @Test
    fun `la clave de la propuesta normaliza los nombres`() {
        val unificar = PropuestaDeOrden.UnificarParecidas(cat("Crédito"), cat("Cuota de Crédito"))
        assertEquals("unificar:credito>cuota de credito", claveDePropuesta(unificar))
        assertEquals("unUso:noquis", claveDePropuesta(PropuestaDeOrden.UnUso(cat("Ñoquis"))))
        assertEquals(
            "esconder:freelance",
            claveDePropuesta(PropuestaDeOrden.EsconderNuncaUsada(cat("Freelance"))),
        )
    }

    @Test
    fun `el texto de la tarjeta usa singular con una sola propuesta`() {
        assertEquals("Movi encontró 1 cosa para ordenar", textoDeLaTarjetaDeOrden(1))
        assertEquals("Movi encontró 3 cosas para ordenar", textoDeLaTarjetaDeOrden(3))
    }
}
