package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Cómo responde el servidor (vuelta 102)
 *
 * Lo que contestan `GET /api/admin/servidor/...` (solo un admin) y lo que pinta la pestaña «Servidor»
 * de «Fallos y uso». Nada de esto tiene datos de nadie: rutas como plantilla, servicios por nombre y
 * modelo, usuarios como una cuenta.
 */

/** Una hora de la serie: pedidos, errores y el p95 de esa hora. */
@Serializable
data class PuntoDelServidor(
    val inicio: Long,
    val pedidos: Long,
    val errores4xx: Long = 0,
    val errores5xx: Long = 0,
    val p95Ms: Long? = null,
)

/** Una ruta (su plantilla) con sus cifras del lapso pedido. */
@Serializable
data class RutaMedida(
    val metodo: String,
    val ruta: String,
    val pedidos: Long,
    val errores4xx: Long = 0,
    val errores5xx: Long = 0,
    val p95Ms: Long? = null,
    val maximoMs: Long = 0,
)

/** `GET /api/admin/servidor/resumen?horas=24` (o 168): los pedidos del lapso, hora por hora. */
@Serializable
data class ResumenDelServidor(
    val desde: Long,
    val hasta: Long,
    val horas: Int,
    val pedidos: Long = 0,
    val errores4xx: Long = 0,
    val errores5xx: Long = 0,
    val p95Ms: Long? = null,
    val serie: List<PuntoDelServidor> = emptyList(),
    val masLentas: List<RutaMedida> = emptyList(),
    val conMas5xx: List<RutaMedida> = emptyList(),
    /** El pico de usuarios autenticados distintos en un mismo minuto. */
    val usuariosPicoPorMinuto: Int = 0,
    /** Cuántas consultas a la base pasaron de 500 ms. */
    val consultasLentas: Long = 0,
) {
    /** Qué parte de los pedidos fueron 5xx, de 0 a 1 (0 sin pedidos). */
    val parteDe5xx: Double get() = if (pedidos == 0L) 0.0 else errores5xx.toDouble() / pedidos
}

/** Un servicio de afuera (Anthropic por modelo, Resend por operación, la TRM) en el lapso pedido. */
@Serializable
data class ServicioMedido(
    val servicio: String,
    val operacion: String,
    val llamadas: Long,
    val sinCredito: Long = 0,
    val limite: Long = 0,
    val servidor: Long = 0,
    val otros: Long = 0,
    val p95Ms: Long? = null,
    val maximoMs: Long = 0,
    val fichasEntrada: Long = 0,
    val fichasSalida: Long = 0,
    val fichasCache: Long = 0,
) {
    val errores: Long get() = sinCredito + limite + servidor + otros
}

/** Un día de la IA: llamadas, fichas y cuántos usuarios distintos la usaron (la cuenta, no quiénes). */
@Serializable
data class IaDelDia(
    val dia: String,
    val llamadas: Long,
    val errores: Long = 0,
    val fichasEntrada: Long = 0,
    val fichasSalida: Long = 0,
    val fichasCache: Long = 0,
    val usuarios: Int = 0,
)

/** `GET /api/admin/servidor/servicios?dias=7`. */
@Serializable
data class ServiciosDelServidor(
    val desde: Long,
    val hasta: Long,
    val servicios: List<ServicioMedido> = emptyList(),
    val iaPorDia: List<IaDelDia> = emptyList(),
)

/** Una corrida de un job del scheduler. */
@Serializable
data class CorridaDeJob(
    val nombre: String,
    val inicio: Long,
    val duracionMs: Long,
    val bien: Boolean,
    val error: String? = null,
)

/** `GET /api/admin/servidor/jobs`: la última corrida de cada job y las últimas que fallaron. */
@Serializable
data class JobsDelServidor(
    val ultimas: List<CorridaDeJob> = emptyList(),
    val fallidas: List<CorridaDeJob> = emptyList(),
)

/** Una variable de entorno crítica: **solo si está**, nunca su valor. */
@Serializable
data class VariableDeEntorno(val nombre: String, val presente: Boolean)

/** `GET /health/detalle` y `GET /api/admin/servidor/salud` (solo admin). */
@Serializable
data class SaludDelServidor(
    val baseOk: Boolean,
    val baseMs: Long? = null,
    val version: String? = null,
    val uptimeMs: Long = 0,
    val memoriaUsadaMb: Long = 0,
    val memoriaMaximaMb: Long = 0,
    val poolActivas: Int? = null,
    val poolInactivas: Int? = null,
    val poolTotal: Int? = null,
    val poolEsperando: Int? = null,
    val variables: List<VariableDeEntorno> = emptyList(),
    val ultimasCorridas: List<CorridaDeJob> = emptyList(),
)

/** Las cuatro lecturas juntas, como las pinta la pestaña «Servidor». */
data class PanelDelServidor(
    val resumen: ResumenDelServidor,
    val servicios: ServiciosDelServidor,
    val jobs: JobsDelServidor,
    val salud: SaludDelServidor,
)
