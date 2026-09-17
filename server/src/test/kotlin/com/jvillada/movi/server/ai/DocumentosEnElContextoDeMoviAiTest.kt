package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.routes.buildUserContext
import com.jvillada.movi.shared.model.TipoDeDocumento
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El camino completo: de la tabla `documents` al contexto que recibe Movi AI.
 *
 * El pedido del dueño era que el asistente pudiera *«comparar informacion o cargar informacion
 * veridica basandose en los documentos cargados»*. Tenía 30 documentos guardados y
 * [buildUserContext] **no los miraba**: llevaba cuentas, saldos y movimientos del mes y nada
 * más, así que a «¿qué seguro paga el 2334?» no había con qué contestar aunque la respuesta
 * estuviera escrita en una nota.
 *
 * La otra mitad de estas pruebas es la que fija que los BYTES no se acerquen al contexto.
 */
class DocumentosEnElContextoDeMoviAiTest {

    private val duenoId = "user-dueno-docs-ai"
    private val otroId = "user-otro-docs-ai"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:docs_en_contexto_ai;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Documents, VoidEvents, Events, Budgets, Accounts, Users)
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Budgets, Documents,
                // Desde que el contexto del asistente incluye el período (recurrentes, créditos,
                // suscripciones, metas y lo que espera confirmación), `buildUserContext` las lee.
                RecurringRules, RecurringOccurrences, Credits, Subscriptions, Goals, SmsMessages,
            )
            listOf(duenoId to "dueno@movi.test", otroId to "otro@movi.test").forEach { (uid, mail) ->
                Users.insert {
                    it[id] = uid
                    it[email] = mail
                    it[name] = "Alguien"
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    private fun cuenta(uid: String, accId: String, nombre: String) = transaction {
        Accounts.insert {
            it[id] = accId
            it[userId] = uid
            it[name] = nombre
            it[type] = "LOAN"
            it[balance] = 0L
        }
    }

    private fun documento(
        uid: String,
        nombre: String,
        notas: String?,
        accountId: String? = null,
        contenido: ByteArray = ByteArray(0),
        subidoEn: Long = 1_000L,
    ) = transaction {
        Documents.insert {
            it[id] = "doc_${nombre}_$uid"
            it[userId] = uid
            it[name] = nombre
            it[kind] = TipoDeDocumento.EXTRACTO.name
            it[mimeType] = "application/pdf"
            it[sizeBytes] = contenido.size.toLong()
            it[uploadedAt] = subidoEn
            it[Documents.accountId] = accountId
            it[period] = "2026-09"
            it[notes] = notas
            it[content] = contenido
        }
    }

    private fun contexto(uid: String = duenoId) = runBlocking { buildUserContext(uid) }

    /**
     * **Los documentos se consultan, ya no viajan en cada mensaje.** Eran 33 papeles con sus notas
     * —casi seis mil caracteres— en el contexto de CADA pregunta, para responder una cada tantas.
     * El texto que devuelve la herramienta es el mismo de antes; lo que cambió es cuándo se paga.
     *
     * Las garantías que fija esta clase no cambiaron de contenido, solo de puerta: agrupados por
     * cuenta, con la nota, sin los bytes y sin los de otro usuario.
     */
    private fun documentosSegunElAsistente(uid: String = duenoId) = runBlocking {
        ejecutarHerramienta(uid, LlamadaDeHerramienta("tu_1", BUSCAR_DOCUMENTOS, emptyMap()))
    }

    @Test
    fun `el asistente ve el documento, con su nota, bajo el nombre de la cuenta`() {
        cuenta(duenoId, "acc-2334", "Crediágil 2334")
        documento(
            duenoId,
            "Extracto_2334_08_2026.pdf",
            "Extracto del Crediagil. Tasa 29,64 EA, seguro 1.960, saldo 507.553.",
            accountId = "acc-2334",
        )

        val ctx = documentosSegunElAsistente()

        assertTrue(ctx.contains("== Documentos guardados"), ctx)
        assertTrue(ctx.contains("[Crediágil 2334]"), "agrupado por cuenta, como los busca el dueño:\n$ctx")
        assertTrue(ctx.contains("Extracto_2334_08_2026.pdf"), ctx)
        // El dato por el que existe todo esto: de acá sale «el 2334 paga 1.960 de seguro».
        assertTrue(ctx.contains("seguro 1.960, saldo 507.553."), ctx)
    }

    /**
     * El contexto ya no lleva los documentos, pero **sí tiene que decir que existen**: si no, el
     * asistente no tiene por qué sospechar que hay una herramienta que vale la pena usar.
     */
    @Test
    fun `el contexto dice cuantos documentos hay y con que herramienta se leen`() {
        documento(duenoId, "Extracto_2334_08_2026.pdf", "una nota")
        documento(duenoId, "Poliza_HDI.pdf", "otra nota")

        val ctx = contexto()

        assertTrue(ctx.contains("2 documentos guardados"), ctx)
        assertTrue(ctx.contains(BUSCAR_DOCUMENTOS), ctx)
        // Y las notas NO están: por eso el bloque se fue.
        assertFalse(ctx.contains("una nota"), "las notas cuestan fichas en cada mensaje:\n$ctx")
    }

    /**
     * **Pedirlos sin filtro cuesta caro, así que trae menos y lo dice.** Medido: la lista entera de
     * los 33 papeles del dueño son casi ocho mil caracteres — tanto como costaba el contexto viejo
     * completo, en una sola consulta. Con el techo corto entra una parte, el bloque anuncia cuántos
     * quedaron fuera, y el modelo puede volver a pedir con un filtro.
     */
    @Test
    fun `sin filtro los documentos vienen recortados, y el recorte se anuncia`() {
        repeat(40) { i ->
            documento(duenoId, "Extracto_$i.pdf", "nota larga de relleno ".repeat(6), subidoEn = i.toLong())
        }

        val sinFiltro = documentosSegunElAsistente()
        assertTrue(sinFiltro.length < PRESUPUESTO_DE_DOCUMENTOS, "quedó en ${sinFiltro.length} chars")
        assertTrue(
            sinFiltro.contains("hay 40 documentos guardados"),
            "tiene que decir cuántos quedaron fuera:\n${sinFiltro.take(300)}",
        )

        // Con filtro no hace falta recortar: son pocos.
        val conFiltro = runBlocking {
            ejecutarHerramienta(duenoId, LlamadaDeHerramienta("tu_1", BUSCAR_DOCUMENTOS, mapOf("texto" to "Extracto_7.pdf")))
        }
        assertTrue(conFiltro.contains("Extracto_7.pdf"), conFiltro)
        assertFalse(conFiltro.contains("Extracto_8.pdf"), conFiltro)
    }

    @Test
    fun `sin documentos el contexto no menciona documentos`() {
        cuenta(duenoId, "acc-2334", "Crediágil 2334")

        val ctx = contexto()

        assertFalse(ctx.contains("Documentos guardados"), ctx)
        // Y el resto del contexto sigue entero: el bloque nuevo no se comió nada.
        assertTrue(ctx.contains("== Cuentas =="), ctx)
        assertTrue(ctx.contains("Crediágil 2334"), ctx)
    }

    @Test
    fun `el contexto trae el documento propio y NO el del otro usuario`() {
        // Los dos documentos, no solo el ajeno: esta es la única prueba que cubre el
        // aislamiento entre usuarios, y con un documento solo las dos negaciones también
        // pasarían si el bloque entero desapareciera del contexto — o sea, pasarían por el
        // motivo equivocado. Afirmando lo propio y lo ajeno en el MISMO contexto, la única
        // forma de que pase es que el filtro por usuario esté haciendo su trabajo.
        documento(duenoId, "Extracto_propio.pdf", "esta plata sí es del dueño")
        documento(otroId, "Extracto_ajeno.pdf", "plata que no es del dueño")

        val ctx = documentosSegunElAsistente()

        assertTrue(ctx.contains("Extracto_propio.pdf"), ctx)
        assertTrue(ctx.contains("esta plata sí es del dueño"), ctx)
        assertFalse(ctx.contains("Extracto_ajeno.pdf"), ctx)
        assertFalse(ctx.contains("plata que no es del dueño"), ctx)
    }

    @Test
    fun `los bytes del archivo no llegan al contexto`() {
        val marcador = "ESTO-SON-LOS-BYTES-DEL-PDF"
        documento(duenoId, "Extracto_con_bytes.pdf", "una nota corta", contenido = marcador.toByteArray())

        val ctx = documentosSegunElAsistente()

        assertTrue(ctx.contains("Extracto_con_bytes.pdf"), "el documento sí está:\n$ctx")
        assertFalse(ctx.contains(marcador), "el contenido del archivo NO viaja al modelo:\n$ctx")
    }

    @Test
    fun `la consulta ni siquiera PIDE la columna del contenido`() {
        // Esta es la prueba que importa, y no la del marcador de arriba: aunque el bloque nunca
        // imprima los bytes, un `selectAll()` los traería igual: 10 MB por fila, en cada mensaje
        // del chat, a un `toString()` de distancia de un log. Cambiar `consultaDeDocumentos` por
        // `Documents.selectAll()` deja pasar la prueba del marcador y rompe esta.
        val columnas = transaction { consultaDeDocumentos(duenoId).set.fields }

        assertFalse(columnas.contains(Documents.content), "columnas pedidas: $columnas")
        // Y no es que no pida nada: los metadatos que el bloque necesita sí están.
        assertTrue(columnas.contains(Documents.notes), "columnas pedidas: $columnas")
        assertTrue(columnas.contains(Documents.name), "columnas pedidas: $columnas")
        assertTrue(columnas.contains(Documents.accountId), "columnas pedidas: $columnas")
    }

    @Test
    fun `un documento sin cuenta se lista igual, aparte`() {
        // No es un error que le falte la cuenta: hay dos así a propósito (un crédito saldado y
        // una cuenta con $61). Antes de esta prueba, un `accountId` nulo era el caso que un
        // agrupamiento descuidado tira a la basura.
        cuenta(duenoId, "acc-2334", "Crediágil 2334")
        documento(duenoId, "Extracto_2334_08_2026.pdf", "del crédito", accountId = "acc-2334", subidoEn = 2_000)
        documento(duenoId, "Poliza_HDI.pdf", "prima 835.200 al ano = 69.600 AL MES.", subidoEn = 1_000)

        val ctx = documentosSegunElAsistente()

        assertTrue(ctx.contains("[$SIN_CUENTA]"), ctx)
        assertTrue(ctx.contains("Poliza_HDI.pdf"), ctx)
        assertTrue(ctx.contains("prima 835.200 al ano = 69.600 AL MES."), ctx)
    }
}
