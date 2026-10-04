package com.jvillada.movi.server.db

import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.normalizarLlave
import com.jvillada.movi.shared.model.soloLosDigitos
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

/**
 * La columna `known_destinations.identificadores` (4-oct-2026), de ida y de vuelta. Vive en `db`
 * porque la usan la migración de arranque y las rutas por igual.
 */
private val jsonDeIdentificadores = Json { ignoreUnknownKeys = true }

private val serializadorDeLaLista = ListSerializer(IdentificadorDelDestino.serializer())

/** El número y la llave de siempre, como lista: lo que la migración pasa a la columna nueva. */
fun identificadoresDeSiempre(numero: String, llave: String?): List<IdentificadorDelDestino> = listOfNotNull(
    soloLosDigitos(numero).takeIf { it.isNotEmpty() }?.let { IdentificadorDelDestino(TipoDeIdentificador.NUMERO, it) },
    llave?.let(::normalizarLlave)?.takeIf { it.isNotEmpty() }?.let { IdentificadorDelDestino(TipoDeIdentificador.LLAVE, it) },
)

fun identificadoresComoJson(lista: List<IdentificadorDelDestino>): String =
    jsonDeIdentificadores.encodeToString(serializadorDeLaLista, lista)

/**
 * La lista guardada, o la del número y la llave si la columna está en NULL (una fila que la
 * migración todavía no pasó) o ilegible. Nunca falla: un destino sin lista sigue reconociéndose
 * por lo de siempre.
 */
fun identificadoresDeLaFila(json: String?, numero: String, llave: String?): List<IdentificadorDelDestino> =
    json?.let { runCatching { jsonDeIdentificadores.decodeFromString(serializadorDeLaLista, it) }.getOrNull() }
        ?: identificadoresDeSiempre(numero, llave)

/** Una fila de `known_destinations` como [DestinoConocido], con todos sus identificadores. */
fun ResultRow.aDestino(): DestinoConocido {
    val numero = this[KnownDestinations.numero]
    val llave = this[KnownDestinations.llave]
    val lista = identificadoresDeLaFila(this[KnownDestinations.identificadores], numero, llave)
    return DestinoConocido(
        id = this[KnownDestinations.id],
        nombre = this[KnownDestinations.nombre],
        numero = numero,
        deQuien = this[KnownDestinations.deQuien],
        llave = llave,
        tipo = this[KnownDestinations.tipo]?.let { t -> TipoDeTercero.entries.firstOrNull { it.name == t } },
    ).conIdentificadores(lista + identificadoresDeSiempre(numero, llave)).copy(
        // `conIdentificadores` pone número y llave en el primero de cada clase; las columnas son
        // la verdad de lo que lee el APK instalado, y se respetan tal cual.
        numero = numero,
        llave = llave,
    )
}
