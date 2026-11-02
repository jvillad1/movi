package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/*
 * # Las tablas del tope de Movi AI (vuelta 106)
 *
 * Las preguntas del mes **no** tienen tabla propia: se cuentan en `ai_turns` (un turno con modelo
 * distinto de `sin-modelo` es una pregunta libre). Lo que sí se guarda es lo que elige el admin desde
 * la app: el número global y las excepciones por usuario. Dos tablas NUEVAS, que entran solo por el
 * `SchemaUtils.create` del arranque (su clave primaria viaja dentro del CREATE TABLE).
 */

/**
 * Ajustes de la IA que no son de nadie, por clave (hoy solo `tope_global`). Sin `user_id`: no entra a
 * `TABLAS_DEL_USUARIO`, borrar una cuenta no tiene nada que borrar acá.
 */
object AjustesDeLaIa : Table("ajustes_de_la_ia") {
    val clave = varchar("clave", 40)
    val valor = varchar("valor", 200)
    val actualizadoEn = long("actualizado_en")
    override val primaryKey = PrimaryKey(clave)
}

/**
 * **La excepción de un usuario**: su propio número de preguntas por mes, en vez del global. Lleva
 * `user_id`, así que está en `TABLAS_DEL_USUARIO` y se va con la cuenta.
 */
object TopesDeLaIaPorUsuario : Table("topes_de_la_ia") {
    val userId = varchar("user_id", 50)
    val tope = integer("tope")
    val actualizadoEn = long("actualizado_en")
    override val primaryKey = PrimaryKey(userId)
}
