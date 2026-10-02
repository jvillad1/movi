package com.jvillada.movi.server.exportar

import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * # «Descarga tus datos»
 *
 * Todo lo que Movi guarda de una persona, en un ZIP: un CSV por tabla (para abrir en una hoja de
 * cálculo) y un `movi.json` con lo mismo (para un programa). Hasta ahora esto se hacía por `psql`,
 * o sea que solo lo podía hacer quien tuviera la base — y la cuenta es de quien la usa.
 *
 * ## Qué entra y qué no
 *
 * Una tabla entra si es **de la persona** y dice algo de **su plata o de cómo la organiza**. Cada
 * una se filtra por su columna de usuario — esa es la garantía de aislamiento, y la prueba
 * (`ExportRoutesTest`) la mide con dos usuarios. Queda afuera, a propósito:
 *
 * - el **binario** de los documentos (solo van sus metadatos: un ZIP con todas las escrituras y
 *   extractos sería enorme, y cada uno se baja desde Documentos);
 * - el hash de la contraseña y la versión de sesiones del perfil (no son datos, son la cerradura);
 * - lo que es de un aparato o de la cerradura: suscripciones push, enlaces de recuperación, enlaces
 *   compartidos (solo hashes);
 * - la memoria interna de Movi: las conversaciones con Movi AI, los descartes de candidatos.
 *
 * Las columnas salen de la definición de Exposed, no de una lista a mano: una columna nueva en una
 * tabla exportada entra sola, y lo que no tiene que salir se nombra en [TablaExportada.sinColumnas].
 */
internal data class TablaExportada(
    /** El nombre del archivo, en español: `movimientos.csv`. */
    val nombre: String,
    val tabla: Table,
    val usuario: Column<String>,
    val sinColumnas: Set<Column<*>> = emptySet(),
)

internal val TABLAS_EXPORTADAS: List<TablaExportada> = listOf(
    TablaExportada("perfil", Users, Users.id, setOf(Users.passwordHash, Users.tokenVersion)),
    TablaExportada("cuentas", Accounts, Accounts.userId),
    TablaExportada("movimientos", Events, Events.userId),
    TablaExportada("anulados", VoidEvents, VoidEvents.userId),
    TablaExportada("reglas_recurrentes", RecurringRules, RecurringRules.userId),
    TablaExportada("ocurrencias", RecurringOccurrences, RecurringOccurrences.userId),
    TablaExportada("ocurrencias_rechazadas", OccurrenceRejections, OccurrenceRejections.userId),
    TablaExportada("creditos", Credits, Credits.userId),
    TablaExportada("tarjetas", Cards, Cards.userId),
    TablaExportada("suscripciones", Subscriptions, Subscriptions.userId),
    TablaExportada("presupuestos", Budgets, Budgets.userId),
    TablaExportada("categorias", CategoryPrefs, CategoryPrefs.userId),
    TablaExportada("metas", Goals, Goals.userId),
    TablaExportada("destinos_conocidos", KnownDestinations, KnownDestinations.userId),
    TablaExportada("documentos", Documents, Documents.userId, setOf(Documents.content)),
    TablaExportada("mensajes_del_banco", SmsMessages, SmsMessages.userId),
    TablaExportada("extractos_importados", StatementImports, StatementImports.userId),
    // Ola 3: lo que el dueño le contó a Movi AI y confirmó guardar. No es la memoria interna del
    // asistente (las conversaciones siguen afuera): son frases suyas sobre su vida y su plata, que
    // él ve y edita en Ajustes.
    TablaExportada("lo_que_movi_sabe_de_ti", MemoriaDelAsistente, MemoriaDelAsistente.userId),
)

/** Las filas de una tabla, ya leídas: nombres de columna y valores crudos. */
internal data class FilasExportadas(val nombre: String, val columnas: List<String>, val filas: List<List<Any?>>)

/**
 * Lee todo lo de [uid]. Corre DENTRO de una transacción de quien llama, para que el ZIP sea una
 * foto coherente: un movimiento anotado a la mitad de la lectura no puede quedar en `movimientos`
 * sin su cuenta, ni al revés.
 */
internal fun leerDatosDe(uid: String): List<FilasExportadas> = TABLAS_EXPORTADAS.map { t ->
    val columnas = t.tabla.columns.filter { it !in t.sinColumnas }
    val filas = t.tabla.select(columnas)
        .where { t.usuario eq uid }
        .map { fila -> columnas.map { c -> valorDe(fila, c) } }
    FilasExportadas(t.nombre, columnas.map { it.name }, filas)
}

@Suppress("UNCHECKED_CAST")
private fun valorDe(fila: ResultRow, columna: Column<*>): Any? = fila[columna as Column<Any?>]

/** El ZIP entero: un CSV por tabla y `movi.json`. [exportadoEn] es un instante ISO, para el JSON. */
internal fun zipDeLosDatos(uid: String, datos: List<FilasExportadas>, exportadoEn: String): ByteArray {
    val salida = ByteArrayOutputStream()
    ZipOutputStream(salida).use { zip ->
        for (t in datos) {
            zip.putNextEntry(ZipEntry("${t.nombre}.csv"))
            zip.write(csvDe(t).encodeToByteArray())
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry("movi.json"))
        zip.write(jsonDe(uid, datos, exportadoEn).toString().encodeToByteArray())
        zip.closeEntry()
    }
    return salida.toByteArray()
}

/**
 * Un CSV RFC 4180, con BOM de UTF-8 adelante: sin él, Excel en Windows abre «Préstamo» como
 * «PrÃ©stamo». Separador coma; el campo va entre comillas si trae coma, comilla o salto de línea.
 */
internal fun csvDe(t: FilasExportadas): String = buildString {
    append('﻿')
    append(t.columnas.joinToString(",") { campoCsv(it) })
    append("\r\n")
    for (fila in t.filas) {
        append(fila.joinToString(",") { campoCsv(textoDe(it)) })
        append("\r\n")
    }
}

private fun textoDe(valor: Any?): String = when (valor) {
    null -> ""
    // Un texto que empieza como fórmula se desarma con una comilla simple adelante: los mensajes
    // del banco y los nombres de comercio vienen de afuera (un correo reenviado, un SMS), y una
    // hoja de cálculo ejecutaría `=HYPERLINK(…)` al abrir el archivo. Solo en el CSV y solo a
    // los textos: los números negativos (`-50000`) llegan como Long y no pasan por acá.
    is String -> if (valor.firstOrNull() in INICIOS_DE_FORMULA) "'$valor" else valor
    else -> valor.toString()
}

private val INICIOS_DE_FORMULA = setOf('=', '+', '-', '@', '\t', '\r')

private fun campoCsv(texto: String): String =
    if (texto.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + texto.replace("\"", "\"\"") + "\""
    else texto

/** El mismo contenido que los CSV, con los tipos de verdad: números como números, nulos como null. */
internal fun jsonDe(uid: String, datos: List<FilasExportadas>, exportadoEn: String): JsonObject = JsonObject(
    mapOf(
        "formato" to JsonPrimitive("movi-exportacion-1"),
        "exportadoEn" to JsonPrimitive(exportadoEn),
        "usuario" to JsonPrimitive(uid),
        "tablas" to JsonObject(
            datos.associate { t ->
                t.nombre to JsonArray(
                    t.filas.map { fila -> JsonObject(t.columnas.zip(fila).associate { (c, v) -> c to jsonDe(v) }) },
                )
            },
        ),
    ),
)

private fun jsonDe(valor: Any?): JsonElement = when (valor) {
    null -> JsonNull
    is Number -> JsonPrimitive(valor)
    is Boolean -> JsonPrimitive(valor)
    else -> JsonPrimitive(valor.toString())
}
