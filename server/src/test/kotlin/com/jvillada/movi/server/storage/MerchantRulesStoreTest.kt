package com.jvillada.movi.server.storage

import com.jvillada.movi.server.db.Migrations
import com.jvillada.movi.server.db.ReglasDeComercio
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.shared.model.MerchantRule
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las reglas de comercio en la base, no en un archivo que el despliegue borra
 *
 * Que cada dueño vea solo las suyas, que el mismo patrón reemplace a la regla, y que lo que quedó en
 * los archivos viejos (`movi-data/merchant-rules-<usuario>.json`) pase a la base al arrancar sin
 * pisar lo que la base ya sabe.
 */
class MerchantRulesStoreTest {

    private val a = "user-reglas-a"
    private val b = "user-reglas-b"
    private val store = MerchantRulesStore()
    private lateinit var carpeta: File

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:reglas_de_comercio;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(ReglasDeComercio, Users)
            SchemaUtils.create(Users, ReglasDeComercio)
            listOf(a, b).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@reglas.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
        }
        carpeta = Files.createTempDirectory("movi-data").toFile()
    }

    @AfterTest
    fun tearDown() {
        carpeta.deleteRecursively()
    }

    private fun archivo(uid: String, json: String) = File(carpeta, "merchant-rules-$uid.json").writeText(json)

    @Test
    fun `cada duenio ve solo sus reglas`() = runBlocking {
        store.saveRule(a, MerchantRule("RAPPI", "Comida"))
        store.saveRule(b, MerchantRule("RAPPI", "Domicilios"))
        store.saveRule(b, MerchantRule("UBER", "Transporte"))
        assertEquals(listOf(MerchantRule("RAPPI", "Comida")), store.getRules(a))
        assertEquals(setOf(MerchantRule("RAPPI", "Domicilios"), MerchantRule("UBER", "Transporte")), store.getRules(b).toSet())
        assertEquals(emptyList(), store.getRules("user-que-no-existe"))
    }

    @Test
    fun `el mismo patron reemplaza la regla`() = runBlocking {
        store.saveRule(a, MerchantRule("RAPPI", "Comida"))
        store.saveRule(a, MerchantRule("RAPPI", "Mercado"))
        assertEquals(listOf(MerchantRule("RAPPI", "Mercado")), store.getRules(a))
    }

    @Test
    fun `una regla sin patron o sin categoria no se guarda`() = runBlocking {
        store.saveRule(a, MerchantRule("  ", "Comida"))
        store.saveRule(a, MerchantRule("RAPPI", ""))
        assertEquals(emptyList(), store.getRules(a))
    }

    @Test
    fun `lo que quedo en los archivos pasa a la base al arrancar`() = runBlocking {
        archivo(a, """[{"merchantPattern":"RAPPI","category":"Comida"},{"merchantPattern":"EXITO","category":"Mercado"}]""")
        archivo(b, """[{"merchantPattern":"UBER","category":"Transporte"}]""")
        transaction { with(Migrations) { pasarLasReglasDeComercioALaBase(carpeta) } }
        assertEquals(setOf(MerchantRule("RAPPI", "Comida"), MerchantRule("EXITO", "Mercado")), store.getRules(a).toSet())
        assertEquals(listOf(MerchantRule("UBER", "Transporte")), store.getRules(b))
    }

    @Test
    fun `la migracion no pisa lo de la base y correrla dos veces no duplica`() = runBlocking {
        store.saveRule(a, MerchantRule("RAPPI", "Domicilios"))
        archivo(a, """[{"merchantPattern":"RAPPI","category":"Comida"},{"merchantPattern":"EXITO","category":"Mercado"}]""")
        transaction { with(Migrations) { pasarLasReglasDeComercioALaBase(carpeta) } }
        transaction { with(Migrations) { pasarLasReglasDeComercioALaBase(carpeta) } }
        assertEquals(setOf(MerchantRule("RAPPI", "Domicilios"), MerchantRule("EXITO", "Mercado")), store.getRules(a).toSet())
        assertEquals(2L, transaction { ReglasDeComercio.selectAll().count() })
    }

    @Test
    fun `un archivo ilegible o de alguien que no existe se salta sin tumbar el arranque`() = runBlocking {
        archivo(a, "esto no es json")
        archivo("user-a-transfers", """[{"merchantPattern":"RAPPI","category":"Comida"}]""")
        archivo(b, """[{"merchantPattern":"UBER","category":"Transporte"}]""")
        File(carpeta, "otra-cosa.json").writeText("[]")
        transaction { with(Migrations) { pasarLasReglasDeComercioALaBase(carpeta) } }
        assertEquals(emptyList(), store.getRules(a))
        assertEquals(listOf(MerchantRule("UBER", "Transporte")), store.getRules(b))
        assertEquals(1L, transaction { ReglasDeComercio.selectAll().count() }, "ninguna fila huérfana")
        assertTrue(File(carpeta, "merchant-rules-$b.json").exists(), "el archivo no se borra")
    }

    @Test
    fun `sin la carpeta no pasa nada`() {
        transaction { with(Migrations) { pasarLasReglasDeComercioALaBase(File(carpeta, "no-existe")) } }
        assertEquals(0L, transaction { ReglasDeComercio.selectAll().count() })
    }
}
