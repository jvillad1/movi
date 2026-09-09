package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.COLUMNAS_SIN_CONTENIDO
import com.jvillada.movi.server.time.epochMillisToAppDateString
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
 * **Es el techo del bloque ENTERO**, no el de la suma de los renglones: el preámbulo, el
 * `AVISO` cuando el corte muerde y el encabezado `[Cuenta]` de cada grupo se descuentan del
 * mismo presupuesto (ver [renderizarDocumentos]). Medir solo los renglones dejaba fuera de la
 * cuenta un encabezado por grupo —hasta 103 caracteres, porque `Accounts.name` es
 * `varchar(100)`— y con un documento por cuenta el bloque llegaba a cinco veces este número.
 *
 * **De dónde sale el número.** El dueño tiene 30 documentos guardados. Cada renglón es
 * `nombre` (hasta 255 por columna, en la práctica ~35) + `tipo` + `periodo` + la fecha en que
 * se subió + `notas` (hasta 500 por columna, en la práctica ~140: las notas reales son de una
 * o dos frases). Con los datos que hay eso da del orden de **6 KB**, o sea unos 1.700 tokens —
 * un pelo más que el bloque de cuentas, y sin discusión frente a los 200K de ventana.
 *
 * Pero el peor caso NO es ese: 30 documentos con el nombre y las notas al tope de la columna
 * son 24 KB, y nada impide que mañana haya 300 archivos. Un contexto que crece sin techo se
 * paga en plata en cada mensaje del chat y termina desplazando a lo demás, así que el bloque
 * se corta acá. 8.000 caracteres entran los 30 de hoy enteros con margen de sobra, y le ponen
 * un piso al día que sean 300.
 *
 * La única forma de pasarse es un solo documento más largo que todo el presupuesto: ese entra
 * igual, a propósito, porque un bloque que dijera «0 de 1» sería peor que no tener bloque.
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
 * ### Una nota no es la verdad de hoy
 *
 * Cada renglón lleva la fecha en que el documento se subió, y el preámbulo dice que la nota es
 * de esa fecha. Sin eso, una nota de agosto que dice «saldo 507.553» se le presentaba al modelo
 * como un hecho verificado y le ganaba a los movimientos de septiembre, que son lo más nuevo
 * que hay.
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
    val total = porFecha.size
    val renglones = porFecha.map { it to renglonDe(it) }

    // Los dos números que se imprimen (cuántos entraron, cuántos hay) nunca pasan de `total`,
    // así que renderizar los textos fijos con `total` da una cota superior de su largo. Es lo
    // que hace que el techo valga para el bloque entero y no solo para los renglones.
    val largoDelPreambulo = preambulo(total).length
    val largoDelAviso = aviso(total, total).length

    // Primer intento sin reservar el aviso: si entra todo, el aviso no se escribe. Si algo se
    // cae, el bloque va a llevarlo, así que se rehace la cuenta descontándolo también.
    var caben = seleccionar(renglones, nombresDeCuenta, presupuesto, largoDelPreambulo)
    if (caben.size < total) {
        caben = seleccionar(
            renglones,
            nombresDeCuenta,
            presupuesto,
            largoDelPreambulo + largoDelAviso,
        )
    }
    val recortado = caben.size < total

    // El orden de los grupos también lo manda la fecha: la cuenta con el documento más nuevo va
    // primero. `caben` ya viene ordenado, así que `groupBy` conserva ese orden.
    val grupos = caben.groupBy { (doc, _) -> grupoDe(doc, nombresDeCuenta) }
    val ordenados = grupos.entries.sortedBy { if (it.key == SIN_CUENTA) 1 else 0 }

    return buildString {
        append(preambulo(caben.size))
        if (recortado) append(aviso(total, caben.size))
        ordenados.forEach { (cuenta, docs) ->
            append(encabezadoDe(cuenta))
            docs.forEach { (_, renglon) -> append(renglon) }
        }
    }
}

/**
 * Cuáles de los renglones caben, contando **todo** lo que se va a escribir: [fijo] es lo que ya
 * tienen reservado el preámbulo y (si corresponde) el aviso, y cada grupo suma su encabezado la
 * primera vez que aparece.
 *
 * Siempre entra al menos uno: un bloque que dice «0 de 30» no le sirve a nadie, y sin esa
 * guarda un presupuesto chico contra una nota larga daría justo eso.
 */
private fun seleccionar(
    renglones: List<Pair<DocumentoParaContexto, String>>,
    nombresDeCuenta: Map<String, String>,
    presupuesto: Int,
    fijo: Int,
): List<Pair<DocumentoParaContexto, String>> {
    val caben = mutableListOf<Pair<DocumentoParaContexto, String>>()
    val gruposYaAbiertos = mutableSetOf<String>()
    var usado = fijo
    for (par in renglones) {
        val grupo = grupoDe(par.first, nombresDeCuenta)
        val encabezado = if (grupo in gruposYaAbiertos) 0 else encabezadoDe(grupo).length
        val cuesta = encabezado + par.second.length
        if (caben.isNotEmpty() && usado + cuesta > presupuesto) break
        caben += par
        gruposYaAbiertos += grupo
        usado += cuesta
    }
    return caben
}

/**
 * Bajo qué encabezado cae un documento. Uno colgado de una cuenta que ya no existe cae en
 * [SIN_CUENTA].
 */
private fun grupoDe(doc: DocumentoParaContexto, nombresDeCuenta: Map<String, String>): String =
    doc.accountId?.let { nombresDeCuenta[it] } ?: SIN_CUENTA

private fun encabezadoDe(grupo: String): String = "[$grupo]\n"

/** El texto fijo de arriba del bloque. Aparte para poder medirlo antes de escribirlo. */
private fun preambulo(cuantos: Int): String = buildString {
    appendLine("== Documentos guardados ($cuantos) ==")
    appendLine(
        "Son los papeles que el usuario subió a Movi. De cada uno tienes el nombre del " +
            "archivo, el tipo, el período, la fecha en que lo subió y las notas que él mismo " +
            "escribió — NO el contenido del archivo.",
    )
    appendLine(
        "Las notas son lo que él leyó en el papel el día que lo subió, así que pueden haber " +
            "quedado viejas: úsalas para contestar y para contrastar contra los movimientos, " +
            "pero cuando una nota no cuadre con los movimientos NO des por hecho que manda la " +
            "nota — di de cuándo es el documento y que los movimientos pueden ser posteriores. " +
            "Cuando una cifra salga de una nota, di de qué documento sale nombrándolo tal cual " +
            "aparece aquí.",
    )
}

/** El aviso de que la lista está recortada. Aparte por el mismo motivo que [preambulo]. */
private fun aviso(total: Int, cuantos: Int): String = buildString {
    appendLine(
        "AVISO: hay $total documentos guardados y aquí solo caben los $cuantos más recientes. " +
            "Si te preguntan por uno que no está en esta lista, NO digas que no existe: di que " +
            "hay más documentos guardados que no tienes a la vista.",
    )
}

/**
 * Un documento, un renglón. Termina en `\n` porque el presupuesto se mide sobre lo que de
 * verdad se escribe.
 *
 * **Los tres campos de texto libre se aplanan a una sola línea, y no solo las notas.** El
 * nombre y el período los escribe el usuario y el server los guarda casi tal cual (`PATCH
 * /api/documents/{id}` hace `trim()`, que recorta las puntas y deja pasar un salto de línea del
 * medio), así que un `\n` adentro no partiría solamente el renglón en dos: dejaría forjar un
 * encabezado de grupo y un renglón de documento inventados, que el modelo leería como dato
 * verídico. La comilla doble del nombre se neutraliza por lo mismo — es el delimitador del
 * campo. Es el mismo trato que `Documents.name` ya recibe antes de entrar al
 * `Content-Disposition` (ver `nombreSeguro`, en `DocumentRoutes`).
 */
private fun renglonDe(doc: DocumentoParaContexto): String {
    val nombre = enUnCampo(doc.nombre)
    val periodo = doc.periodo?.let { enUnCampo(it) }?.takeIf { it.isNotEmpty() }
        ?.let { "período $it, " }
        .orEmpty()
    // La fecha de subida es lo que le deja al modelo relativizar una nota vieja contra un
    // movimiento nuevo. Sin ella, una nota de agosto se le presenta como la verdad de hoy.
    val subido = "subido ${epochMillisToAppDateString(doc.subidoEn)}"
    val notas = doc.notas?.let { enUnaLinea(it) }?.takeIf { it.isNotEmpty() }
        ?.let { ": $it" }
        ?: ": (sin notas)"
    return "- ${doc.tipo} \"$nombre\" ($periodo$subido)$notas\n"
}

private fun enUnaLinea(texto: String): String = texto.replace(Regex("\\s+"), " ").trim()

/**
 * Lo mismo que [enUnaLinea] más la comilla doble neutralizada: es el delimitador con el que el
 * renglón separa el nombre del archivo del resto, y quien la escribe es el usuario.
 */
private fun enUnCampo(texto: String): String = enUnaLinea(texto).replace('"', '\'')
