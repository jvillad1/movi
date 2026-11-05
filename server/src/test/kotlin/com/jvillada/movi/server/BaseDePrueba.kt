package com.jvillada.movi.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * # La base H2 de una clase de prueba, fijada como la de por defecto (revisión de lanzamiento, nov-2026)
 *
 * Cada clase de prueba del server conecta su propia base H2 en memoria. Antes lo hacía con
 * `Database.connect(...)` a secas y dependía de que la suya fuera **la última conectada**, que es la que
 * Exposed usa por defecto en `transaction {}` (y por lo tanto en `dbQuery`, el validador del token, todo).
 * Si otra clase del mismo fork conectaba la suya después —una corrutina que terminó tarde—, las consultas
 * caían en esa otra base: «Table "users" not found (this database is empty)». Cambiar de versión POI y
 * logback movió el orden de las clases y lo destapó en `CerrarSesionesTest` y
 * `PeriodoQuincenalEnElPerfilTest`.
 *
 * Esto conecta y además **fija** la base con `TransactionManager.defaultDatabase`: una conexión posterior
 * de otra clase ya no la reemplaza, y la siguiente clase fija la suya al arrancar. `LasPruebasFijanSuBaseTest`
 * no deja volver a `Database.connect` directo.
 */
fun conectarBaseDePrueba(url: String, driver: String = "org.h2.Driver"): Database =
    Database.connect(url = url, driver = driver).also {
        TransactionManager.defaultDatabase = it
        // El hilo que conecta (el de JUnit) puede tener guardado el gestor de la base de otra clase: Exposed
        // lo cachea en un ThreadLocal la primera vez que el hilo pregunta. Sin soltarlo, el `transaction {}`
        // del `@BeforeTest` creaba las tablas en esa otra base y la de la prueba quedaba vacía.
        TransactionManager.resetCurrent(null)
    }
