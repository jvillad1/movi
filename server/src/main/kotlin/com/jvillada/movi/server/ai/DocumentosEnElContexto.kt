package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.COLUMNAS_SIN_CONTENIDO
import com.jvillada.movi.shared.model.TipoDeDocumento
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder

/**
 * Lo que el asistente necesita saber de un documento guardado. **No tiene los bytes**, y esa
 * ausencia es deliberada: ver [consultaDeDocumentos].
 */
internal data class DocumentoParaContexto(
    val nombre: String,
    val tipo: TipoDeDocumento,
    val accountId: String?,
    val periodo: String?,
    val notas: String?,
    val subidoEn: Long,
)

/**
 * El presupuesto de caracteres que el bloque de documentos puede ocupar en el contexto.
 *
 * **De dónde sale el número.** El dueño tiene 30 documentos guardados. Cada renglón es
 * `nombre` (hasta 255 por columna, en la práctica ~35) + `tipo` + `periodo` + `notas` (hasta
 * 500 por columna, en la práctica ~140: las notas reales son de una o dos frases). Con los
 * datos que hay eso da del orden de **6 KB**, o sea unos 1.700 tokens — un pelo más que el
 * bloque de cuentas, y sin discusión frente a los 200K de ventana.
 *
 * Pero el peor caso NO es ese: 30 documentos con el nombre y las notas al tope de la columna
 * son 24 KB, y nada impide que mañana haya 300 archivos. Un contexto que crece sin techo se
 * paga en plata en cada mensaje del chat y termina desplazando a lo demás, así que el bloque
 * se corta acá. 8.000 caracteres entran los 30 de hoy enteros con margen de sobra, y le ponen
 * un piso al día que sean 300.
 *
 * Cuando el corte muerde, el bloque lo DICE (ver [renderizarDocumentos]): un asistente que ve
 * una lista recortada sin saberlo contesta «no tienes ninguna póliza guardada», que es peor
 * que no contestar.
 */
internal const val PRESUPUESTO_DE_DOCUMENTOS = 8_000

/** Rótulo del grupo de los documentos que no están colgados de ninguna cuenta. */
internal const val SIN_CUENTA = "Sin cuenta asociada"

/**
 * La consulta que alimenta el bloque: metadatos **sin `content`**.
 *
 * Reusa [COLUMNAS_SIN_CONTENIDO] —la misma lista que ya usa el listado de la pantalla— en vez
 * de `selectAll()`. No es una optimización: `Documents.content` es el PDF entero, hasta 10 MB
 * por fila. Traerlos para escribir treinta renglones de texto sería levantar cientos de megas
 * en el heap del server **en cada mensaje del chat**, y dejar los bytes a un `toString()` de
 * distancia de un log. Exposed no lo hace evidente: `selectAll()` trae todo y se ve igual de
 * inocente.
 *
 * Se devuelve la [Query] armada, sin ejecutar, para que haya algo sobre lo que una prueba
 * pueda afirmar que `content` no está entre las columnas pedidas.
 */
internal fun consultaDeDocumentos(uid: String): Query =
    Documents.select(COLUMNAS_SIN_CONTENIDO)
        .where { Documents.userId eq uid }
        .orderBy(Documents.uploadedAt, SortOrder.DESC)

internal suspend fun cargarDocumentosParaContexto(uid: String): List<DocumentoParaContexto> =
    dbQuery {
        consultaDeDocumentos(uid).map {
            DocumentoParaContexto(
                nombre = it[Documents.name],
                tipo = runCatching { TipoDeDocumento.valueOf(it[Documents.kind]) }
                    .getOrDefault(TipoDeDocumento.OTRO),
                accountId = it[Documents.accountId],
                periodo = it[Documents.period],
                notas = it[Documents.notes],
                subidoEn = it[Documents.uploadedAt],
            )
        }
    }

/**
 * El bloque «Documentos guardados» del contexto del asistente.
 *
 * ### Qué problema resuelve
 *
 * El pedido del dueño: *«esta funcionalidad los debería agrupar por etiquetas a los documentos
 * y permitir a Movi poder comparar información o cargar información verídica basándose en los
 * documentos cargados»*. Hasta acá el contexto llevaba cuentas, saldos y movimientos del mes, y
 * de los 30 documentos guardados el asistente **no sabía ni que existían**: no podía contestar
 * «¿qué seguro paga el 2334?» aunque la respuesta estuviera escrita en una nota.
 *
 * ### Por qué las NOTAS y no el PDF
 *
 * Lo valioso ya está destilado en `notas`: «Póliza de vida deudor HDI 625574… prima 835.200 al
 * año = 69.600 AL MES» es exactamente el hecho que hace falta, en 90 caracteres. El archivo en
 * sí son bytes: la tabla guarda `content` y **no hay texto extraído** de ningún PDF. Meter los
 * documentos enteros pediría, primero, extraerles el texto (no existe ese paso hoy) y, después,
 * un contexto una o dos órdenes de magnitud más grande en cada mensaje del chat.
 *
 * **Ese es el paso siguiente y no está acá a propósito:** extraer el texto al subir el archivo,
 * guardarlo en una columna al lado de `content`, y recién ahí decidir cuánto de ese texto entra
 * —probablemente por búsqueda, no entero— o si el archivo viaja como adjunto nativo a Claude.
 * Este PR es el escalón anterior: que el asistente sepa qué papeles hay y qué dice cada uno en
 * una línea.
 *
 * ### Cómo se agrupa
 *
 * Por **cuenta**, que es como el dueño los busca —«el extracto del 2334»—, y los que no cuelgan
 * de ninguna van al final bajo [SIN_CUENTA]. Un documento sin cuenta no es un error: hay dos
 * así a propósito. El `tipo` viaja en cada renglón, que es la otra etiqueta que el dueño les
 * pone.
 *
 * Un documento colgado de una cuenta que ya no existe cae también en [SIN_CUENTA]: el rótulo
 * sería un id sin significado, y el asistente no tiene con qué contrastarlo.
 *
 * @param nombresDeCuenta id → nombre, tal como ya los cargó el bloque de cuentas.
 * @return el bloque listo para concatenar, o **cadena vacía si no hay documentos**: un
 *   «(sin documentos)» solo gastaría tokens en decirle al modelo que no mire para acá.
 */
internal fun renderizarDocumentos(
    documentos: List<DocumentoParaContexto>,
    nombresDeCuenta: Map<String, String>,
    presupuesto: Int = PRESUPUESTO_DE_DOCUMENTOS,
): String {
    if (documentos.isEmpty()) return ""

    // Los más recientes primero: si algo se cae por presupuesto, que sea lo más viejo.
    val porFecha = documentos.sortedByDescending { it.subidoEn }

    val renglones = porFecha.map { it to renglonDe(it) }
    val caben = mutableListOf<Pair<DocumentoParaContexto, String>>()
    var usado = 0
    for (par in renglones) {
        // Siempre entra al menos uno: un bloque que dice «0 de 30» no le sirve a nadie, y sin
        // esta guarda un presupuesto chico contra una nota larga daría justo eso.
        if (caben.isNotEmpty() && usado + par.second.length > presupuesto) break
        caben += par
        usado += par.second.length
    }
    val recortado = caben.size < porFecha.size

    // El orden de los grupos también lo manda la fecha: la cuenta con el documento más nuevo va
    // primero. `caben` ya viene ordenado, así que `groupBy` conserva ese orden.
    val grupos = caben.groupBy { (doc, _) ->
        doc.accountId?.let { nombresDeCuenta[it] } ?: SIN_CUENTA
    }
    val ordenados = grupos.entries.sortedBy { if (it.key == SIN_CUENTA) 1 else 0 }

    return buildString {
        appendLine("== Documentos guardados (${caben.size}) ==")
        appendLine(
            "Son los papeles que el usuario subió a Movi. De cada uno tienes el nombre del " +
                "archivo, el tipo, el período y las notas que él mismo escribió — NO el " +
                "contenido del archivo.",
        )
        appendLine(
            "Las notas son datos verídicos que él verificó contra el papel: úsalas para " +
                "contestar y para contrastar contra los movimientos. Cuando una cifra salga de " +
                "una nota, di de qué documento sale nombrándolo tal cual aparece aquí.",
        )
        if (recortado) {
            appendLine(
                "AVISO: hay ${porFecha.size} documentos guardados y aquí solo caben los " +
                    "${caben.size} más recientes. Si te preguntan por uno que no está en esta " +
                    "lista, NO digas que no existe: di que hay más documentos guardados que no " +
                    "tienes a la vista.",
            )
        }
        ordenados.forEach { (cuenta, docs) ->
            appendLine("[$cuenta]")
            docs.forEach { (_, renglon) -> append(renglon) }
        }
    }
}

/**
 * Un documento, un renglón. Termina en `\n` porque el presupuesto se mide sobre lo que de
 * verdad se escribe.
 *
 * Las notas se aplanan a una sola línea: vienen de un campo de texto libre y un salto de línea
 * adentro partiría el renglón en dos, que es justo lo que rompe la lectura de una lista.
 */
private fun renglonDe(doc: DocumentoParaContexto): String {
    val periodo = doc.periodo?.takeIf { it.isNotBlank() }?.let { " (período $it)" }.orEmpty()
    val notas = doc.notas?.let { enUnaLinea(it) }?.takeIf { it.isNotEmpty() }
        ?.let { ": $it" }
        ?: ": (sin notas)"
    return "- ${doc.tipo} \"${doc.nombre}\"$periodo$notas\n"
}

private fun enUnaLinea(texto: String): String = texto.replace(Regex("\\s+"), " ").trim()
