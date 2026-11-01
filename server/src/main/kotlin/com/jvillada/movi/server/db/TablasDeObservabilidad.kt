package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/*
 * # Las tablas de la observabilidad del servidor (vuelta 102)
 *
 * **Ninguna tiene `user_id`**, a propósito: guardan cómo responde el servidor, no qué hace nadie. Una
 * ruta es su plantilla, un servicio su nombre y su modelo, un job su nombre; los usuarios aparecen
 * solo como una cuenta («3 distintos este minuto»). Por eso no entran a `TABLAS_DEL_USUARIO`: borrar
 * una cuenta no tiene nada que borrar acá. `BorrarLaCuentaTest` recorre las tablas buscando columnas
 * `user_id`, y estas no tienen.
 *
 * Todas son nuevas: entran solo al `SchemaUtils.create` del arranque (sus claves primarias viajan
 * dentro del `CREATE TABLE`, no hay `CREATE INDEX` sueltos que puedan fallar sobre una base con datos).
 *
 * `escala` separa lo de un minuto (`"m"`, `inicio` = el minuto) de lo resumido por hora (`"h"`,
 * `inicio` = la hora): la misma forma, y una hora se arma sumando sus minutos, cubetas incluidas. Lo de
 * un minuto dura 30 días; lo de una hora, 12 meses (ver `RetencionDeMetricas`).
 */

/** Pedidos por ruta: (escala, inicio, método, plantilla). */
object MetricasDeRutas : Table("metricas_de_rutas") {
    val escala     = varchar("escala", 1)
    val inicio     = long("inicio")
    val metodo     = varchar("metodo", 10)
    /** La plantilla (`/api/events/{id}`), nunca la URL. */
    val ruta       = varchar("ruta", 200)
    val pedidos    = long("pedidos")
    val errores4xx = long("errores_4xx")
    val errores5xx = long("errores_5xx")
    val p50Ms      = long("p50_ms")
    val p95Ms      = long("p95_ms")
    val p99Ms      = long("p99_ms")
    val maximoMs   = long("maximo_ms")
    /** Las cubetas de `Cubetas`, como texto: lo que deja calcular el p95 de una hora o de siete días. */
    val cubetas    = varchar("cubetas", 400)
    override val primaryKey = PrimaryKey(escala, inicio, metodo, ruta)
}

/** Llamadas a un servicio de afuera: (escala, inicio, servicio, operación). La operación de Anthropic es el modelo. */
object MetricasDeServicios : Table("metricas_de_servicios") {
    val escala        = varchar("escala", 1)
    val inicio        = long("inicio")
    val servicio      = varchar("servicio", 20)
    val operacion     = varchar("operacion", 60)
    val llamadas      = long("llamadas")
    val sinCredito    = long("errores_sin_credito")
    val limite        = long("errores_limite")
    val servidor      = long("errores_servidor")
    val otros         = long("errores_otros")
    val fichasEntrada = long("fichas_entrada")
    val fichasSalida  = long("fichas_salida")
    val fichasCache   = long("fichas_cache")
    val p95Ms         = long("p95_ms")
    val maximoMs      = long("maximo_ms")
    val cubetas       = varchar("cubetas", 400)
    override val primaryKey = PrimaryKey(escala, inicio, servicio, operacion)
}

/**
 * Lo demás del minuto: cuántos usuarios autenticados distintos pidieron algo (la cuenta, no quiénes),
 * cuántas consultas pasaron de 500 ms y el pico del pool de conexiones.
 */
object ActividadDelServidor : Table("actividad_del_servidor") {
    val escala           = varchar("escala", 1)
    val inicio           = long("inicio")
    val usuarios         = integer("usuarios_distintos")
    val consultasLentas  = long("consultas_lentas")
    val poolActivasMax   = integer("pool_activas_max")
    val poolEsperandoMax = integer("pool_esperando_max")
    override val primaryKey = PrimaryKey(escala, inicio)
}

/**
 * **La IA por día**: llamadas, fichas y cuántos usuarios distintos la usaron. Sin el usuario: la cuenta.
 * Es lo que dice cuánto cuesta la IA por usuario activo. No hay columna de costo en plata: los precios
 * por ficha no están en el código, y escribirlos de memoria sería inventarlos (ver CLAUDE.md).
 */
object IaPorDia : Table("ia_por_dia") {
    /** `yyyy-MM-dd`, en la zona de la app. */
    val dia           = varchar("dia", 10)
    val llamadas      = long("llamadas")
    val errores       = long("errores")
    val fichasEntrada = long("fichas_entrada")
    val fichasSalida  = long("fichas_salida")
    val fichasCache   = long("fichas_cache")
    val usuarios      = integer("usuarios")
    override val primaryKey = PrimaryKey(dia)
}

/** Cada corrida de un job del scheduler: nombre, inicio, duración, cómo salió y el error recortado. */
object CorridasDeJobs : Table("corridas_de_jobs") {
    val id         = varchar("id", 36)
    val nombre     = varchar("nombre", 60)
    val inicio     = long("inicio")
    val duracionMs = long("duracion_ms")
    val bien       = bool("bien")
    /** El tipo de la excepción y su mensaje, recortado y sin números largos (ver `errorRecortado`). */
    val error      = varchar("error", 300).nullable()
    override val primaryKey = PrimaryKey(id)
}

/** El candado de cada alerta: cuándo se mandó por última vez, para no repetirla antes de una hora. */
object AlertasDelServidor : Table("alertas_del_servidor") {
    val clave     = varchar("clave", 100)
    val enviadaEn = long("enviada_en")
    override val primaryKey = PrimaryKey(clave)
}

/** Las tablas de la vuelta 102, para el `create` del arranque y las pruebas. */
val TABLAS_DE_OBSERVABILIDAD = arrayOf(MetricasDeRutas, MetricasDeServicios, ActividadDelServidor, IaPorDia, CorridasDeJobs, AlertasDelServidor)
