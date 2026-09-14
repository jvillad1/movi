package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.EventSource
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import io.ktor.server.routing.delete
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.withCashFlowFlag
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.StatementImportMatches
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.FamiriosParser
import com.jvillada.movi.server.parsing.StatementDocumentType
import com.jvillada.movi.server.parsing.StatementParser
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.server.subscriptions.runSubscriptionDetection
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.model.masRecientePrimero
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.log
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate

fun Route.statementRoutes() {

    post("/api/statements/upload") {
        val uid = call.userId()
        val multipart = call.receiveMultipart()
        var fileName = "statement"
        // Para leerle los números de cuenta al armar la respuesta (ver `numerosDeCuenta`).
        var textoDelExtracto = ""
        var bytes = ByteArray(0)

        var mimeType = ""
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "statement"
                mimeType = part.contentType?.toString() ?: ""
                bytes = part.streamProvider().readBytes()
            }
            part.dispose()
        }

        if (bytes.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "No file received")
            return@post
        }
        // Desde que esta ruta ARCHIVA el archivo (y no solo lo parsea), le aplica el mismo tope
        // que la de documentos: sin esto, un PDF de 200 MB entraba a Postgres por la puerta de
        // atrás y después aparecía en la pantalla de Documentos, saltándose el límite que esa
        // pantalla sí respeta. Dos topes distintos para el mismo dato terminan dejando pasar por
        // una puerta lo que la otra rechaza.
        if (bytes.size > MAX_DOCUMENTO_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                "El archivo pesa más de ${MAX_DOCUMENTO_BYTES / (1024 * 1024)} MB",
            )
            return@post
        }

        val isImage = ClaudeStatementParser.isImageMime(mimeType) ||
            fileName.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "heic")

        val bankName: String
        val parsed: List<ParsedTransaction>
        var isFamirios = false
        if (isImage) {
            val imageMime = ClaudeStatementParser.supportedImageMime(mimeType, fileName)
            if (imageMime == null) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    "Formato de imagen no soportado. Sube PNG, JPG, GIF o WEBP (HEIC no se puede leer).",
                )
                return@post
            }
            bankName = StatementParser.detectBankName(fileName)
            parsed = ClaudeStatementParser.parseImage(bytes, imageMime, Stores.merchantRules.getRules(uid))
        } else {
            val text = StatementParser.extractText(bytes, fileName)
            textoDelExtracto = text
            val docType = StatementParser.detectDocumentType(text)
            if (docType == StatementDocumentType.LOAN_SUMMARY || docType == StatementDocumentType.INVESTMENT_FUND) {
                val msg = when (docType) {
                    StatementDocumentType.LOAN_SUMMARY ->
                        "Este documento es un resumen de crédito, no un extracto de movimientos. No contiene transacciones importables."
                    else ->
                        "Este documento es un estado de fondo de inversión. No contiene transacciones importables."
                }
                call.respond(HttpStatusCode.UnprocessableEntity, msg)
                return@post
            }
            isFamirios = docType == StatementDocumentType.FAMIRIOS
            bankName = if (isFamirios) "Famirios" else StatementParser.detectBankName(fileName, text)
            parsed = if (isFamirios) {
                WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
                    FamiriosParser.parse(wb, AppClock.today())
                }
            } else {
                ClaudeStatementParser.parse(text, Stores.merchantRules.getRules(uid))
            }
            if (isFamirios && parsed.isEmpty()) {
                call.respond(HttpStatusCode.UnprocessableEntity,
                    "El archivo parece un Famirios pero no contiene celdas importables.")
                return@post
            }
        }

        val voidedIds = dbQuery {
            VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
        }
        val existing = dbQuery {
            Events.selectAll()
                .where {
                    (Events.userId eq uid) and
                    (if (voidedIds.isNotEmpty()) Events.id notInList voidedIds else Op.TRUE)
                }
                .map { it.toFinancialEvent() }
        }

        val matches = mutableListOf<ReconciliationMatch>()
        val newTransactions = mutableListOf<ParsedTransaction>()

        // Cada movimiento anotado puede ser la pareja de UNA sola fila. Antes el mismo movimiento
        // se proponía para todas las filas iguales: dos compras de $50.000 en días seguidos
        // quedaban emparejadas con el único SMS, y al confirmar las dos la segunda compra real no
        // se importaba nunca.
        val yaEmparejados = mutableSetOf<String>()
        for (tx in parsed) {
            val parsedEpoch = runCatching {
                appDateToEpochMillis(fechaDelExtracto(tx.date)!!)
            }.getOrNull()

            val match = if (parsedEpoch != null) {
                existing
                    .filter { ev ->
                        ev.id !in yaEmparejados &&
                            ev.amount == tx.amount &&
                            ev.currency == tx.currency &&
                            // Un reembolso de $80.000 no es la compra de $80.000.
                            ev.type == tx.type &&
                            abs(parsedEpoch - ev.timestamp) <= 2 * 86_400_000L
                    }
                    // El más cercano en fecha, no el primero que devolvió la base.
                    .minByOrNull { abs(parsedEpoch - it.timestamp) }
            } else null
            match?.let { yaEmparejados += it.id }

            if (match != null) {
                // Mismo día civil de Bogotá, no mismo bucket de 24 h desde la época (UTC).
                val sameDay = parsedEpoch != null &&
                    epochMillisToAppDate(parsedEpoch) == epochMillisToAppDate(match.timestamp)
                matches += ReconciliationMatch(
                    parsed = tx,
                    existingEventId = match.id,
                    existingEvent = match,
                    matchConfidence = if (sameDay) 0.95f else 0.7f,
                )
            } else {
                newTransactions += tx
            }
        }

        val period = if (isFamirios) {
            val years = parsed.mapNotNull { fechaDelExtracto(it.date)?.year }
            if (years.isEmpty()) "" else "${years.min()}–${years.max()}"
        } else runCatching {
            val date = parsed.firstNotNullOfOrNull { fechaDelExtracto(it.date) } ?: LocalDate.parse("2025-01-01")
            "${monthName(date.monthValue)} ${date.year}"
        }.getOrDefault("")

        // El extracto se ARCHIVA, no se tira.
        //
        // Hasta acá esta ruta recibía el PDF, lo parseaba y perdía los bytes: quedaban los
        // movimientos y desaparecía el papel del que salieron — que es exactamente lo que hace
        // falta el día que una cifra no cuadra con el banco. El dueño lo pidió así: «me gustaría
        // que guardemos en Movi extractos y documentos en algún lugar y los podamos listar y
        // acceder desde el sitio y la app».
        //
        // Se archiva al SUBIR y no al confirmar la importación, a propósito: un extracto que se
        // miró y no se importó igual es un papel del banco que uno quiere tener. Y si el
        // archivado falla, la importación NO se cae: el dueño vino a importar movimientos, y
        // perder eso por no poder guardar una copia sería cambiar un problema chico por uno
        // grande. Falla en silencio en el log, que es donde se mira.
        runCatching {
            dbQuery {
                // **No se archiva dos veces el mismo papel.** Esto corre en la VISTA PREVIA, no
                // en la importación: subir «Extracto_agosto.pdf», mirarlo, volver atrás y volver
                // a subirlo dejaba dos filas idénticas —mismo nombre, mismo peso, mismo período,
                // misma nota— indistinguibles en la pantalla. Se compara por nombre y tamaño, que
                // es lo que un dueño reconoce como «el mismo archivo»; un hash sería más exacto y
                // más caro, y acá el falso negativo (dos versiones distintas del mismo mes con el
                // mismo peso al byte) es tan improbable como inofensivo.
                val yaEstaba = Documents
                    .select(listOf(Documents.id))
                    .where {
                        (Documents.userId eq uid) and
                            (Documents.name eq fileName.take(255)) and
                            (Documents.sizeBytes eq bytes.size.toLong())
                    }
                    .any()
                if (!yaEstaba) {
                    guardarDocumento(
                        uid,
                        Documento(
                            id = "doc_${UUID.randomUUID()}",
                            // Recortado como en la ruta de documentos: la columna es varchar(255)
                            // y un nombre más largo hacía fallar el insert, o sea que el archivado
                            // se perdía en silencio justo para los archivos peor nombrados.
                            nombre = fileName.take(255),
                            tipo = TipoDeDocumento.EXTRACTO,
                            mimeType = mimeType.ifBlank { "application/octet-stream" }.take(120),
                            bytes = bytes.size.toLong(),
                            subidoEn = System.currentTimeMillis(),
                            periodo = period.takeIf { it.isNotBlank() },
                            notas = "Importado desde $bankName",
                        ),
                        bytes,
                    )
                }
            }
        }.onFailure { call.application.log.warn("[documentos] no se pudo archivar $fileName", it) }

        call.respond(
            StatementParseResult(
                statementId = UUID.randomUUID().toString(),
                bankName = bankName,
                period = period,
                newTransactions = newTransactions,
                matches = matches,
                numerosDeCuenta = StatementParser.numerosDeCuenta(fileName, textoDelExtracto),
            )
        )
    }

    post("/api/statements/import") {
        val uid = call.userId()
        val decision = call.receive<ImportDecision>()

        val accountExists = dbQuery {
            Accounts.selectAll()
                .where { (Accounts.id eq decision.accountId) and (Accounts.userId eq uid) }
                .count() > 0
        }
        if (!accountExists) {
            call.respond(HttpStatusCode.NotFound, "Account not found")
            return@post
        }

        val importId = "si_${UUID.randomUUID()}"
        var importedCount = 0
        var reconciledCount = 0

        var sinFecha = 0
        // Dos filas confirmadas contra el MISMO movimiento: la segunda es otra compra real.
        //
        // **Y lo que este mismo importe CREA tampoco es pareja de nadie.** Las filas nuevas se crean
        // antes que las conciliaciones, y una conciliación cuya propuesta era de otra cuenta busca
        // su pareja en la del extracto: con dos Uber de $15.000 el mismo día (uno anotado en Nequi),
        // la búsqueda de la fila 1 caía sobre el evento que la fila 2 acababa de crear, y entraba
        // un solo viaje. Por eso cada id creado entra al mismo conjunto de «ya usados».
        val reconciliadosEnEsteImporte = mutableSetOf<String>()
        for (tx in decision.imports) {
            val creado = createEventFromParsed(tx, decision.accountId, uid, importId)
            if (creado != null) { importedCount++; reconciliadosEnEsteImporte += creado } else sinFecha++
        }

        for (dec in decision.reconciliations) {
            if (dec.confirm) {
                // **La pareja tiene que estar en la cuenta del extracto.** Si la propuesta era de otra
                // cuenta (el emparejador no la conoce: se elige después), se busca la pareja real
                // en ESTA cuenta antes de dar la fila por nueva. Antes se creaba directo: una compra
                // de $500.000 de la tarjeta emparejada con un traspaso de Ahorros del mismo día
                // entraba como nueva aunque su SMS ya estuviera en la tarjeta — compra duplicada.
                val parejaId = dbQuery {
                    val propuesta = Events.selectAll()
                        .where { (Events.id eq dec.existingEventId) and (Events.userId eq uid) }
                        .firstOrNull()
                    if (propuesta != null && propuesta[Events.accountId] == decision.accountId &&
                        dec.existingEventId !in reconciliadosEnEsteImporte
                    ) {
                        dec.existingEventId
                    } else {
                        parejaEnLaCuenta(uid, decision.accountId, dec.parsed, reconciliadosEnEsteImporte)
                    }
                }
                val existingEvent = if (parejaId == null) null else dbQuery {
                    Events.selectAll()
                        .where { (Events.id eq parejaId) and (Events.userId eq uid) }
                        .firstOrNull()
                        // **Solo es el mismo movimiento si está en la cuenta del extracto.** El
                        // emparejador no conoce la cuenta (se elige después), así que una compra de
                        // la tarjeta podía quedar «conciliada» contra una pata de traspaso de la
                        // cuenta de ahorros por el mismo monto: la tarjeta se quedaba sin la compra
                        // y su deuda $X más baja. Si no coincide la cuenta, la fila entra como nueva.
                        ?.let {
                            ExistingEventFields(
                                category   = it[Events.category],
                                description = it[Events.description],
                                merchant   = it[Events.merchant],
                                isTransferLeg = it[Events.transferId] != null || it[Events.category] == TRANSFER_CATEGORY,
                                importeAnterior = it[Events.statementImportId],
                            )
                        }
                }

                if (existingEvent != null) {
                    val (existCat, existDesc, existMerchant, esPataDeTraspaso, importeAnterior) = existingEvent
                    // La categoría de una pata de traspaso NO se toca por esta puerta. Esta
                    // reconciliación escribe con un `Events.update` directo, sin pasar por la
                    // guarda de `PUT /api/events/{id}/category` — y el matcher empareja por monto
                    // + moneda + ±2 días SIN mirar la cuenta, así que engancha la pata de un
                    // traspaso con cualquier compra del extracto que coincida en plata y fecha.
                    // Con «Confirmar todo» eso se aplicaba en bloque, sin que nadie lo leyera: la
                    // pata salía de «Traspaso», isCashFlow volvía a decir `true` y el egreso del
                    // mes se inflaba con plata que nunca salió del bolsillo — encima con la pata
                    // hermana todavía excluida, así que ni siquiera se compensaba.
                    // Descripción y comercio sí se dejan enriquecer: no cambian ningún cálculo.
                    // Y tampoco entra ninguna otra reservada por esta puerta, por lo mismo que
                    // la pata de traspaso: este `Events.update` no pasa por la guarda del
                    // `PUT /api/events/{id}/category`, así que la validación tiene que estar acá.
                    val categoriaDelExtractoEsSegura =
                        !esPataDeTraspaso && !isReservedCategory(dec.parsed.category)
                    val finalCategory    = if (dec.categorySource    == FieldSource.STATEMENT && categoriaDelExtractoEsSegura) dec.parsed.category    else existCat
                    val finalDescription = if (dec.descriptionSource == FieldSource.STATEMENT) dec.parsed.description else existDesc
                    val finalMerchant    = if (dec.merchantSource    == FieldSource.STATEMENT) dec.parsed.merchant    else existMerchant

                    dbQuery {
                        Events.update({ (Events.id eq parejaId!!) and (Events.userId eq uid) }) {
                            it[category]          = finalCategory
                            it[description]       = finalDescription
                            it[merchant]          = finalMerchant
                            // Si otro importe ya lo concilió, se queda con ese: reimportar el mismo
                            // extracto le quitaba las filas al detalle del primero.
                            if (importeAnterior == null) it[statementImportId] = importId
                            // El extracto del banco prueba el movimiento: deja de estar «Por
                            // confirmar». Antes un SMS conciliado seguía pendiente, y descartarlo
                            // ahí como duplicado borraba el único registro de la compra.
                            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
                        }
                        // Pero el vínculo de ESTE importe queda escrito: si no, deshacer el
                        // anterior anulaba el movimiento aunque este importe todavía lo reclamara.
                        if (importeAnterior != null && importeAnterior != importId) {
                            StatementImportMatches.insert {
                                it[StatementImportMatches.importId] = importId
                                it[StatementImportMatches.eventId]  = parejaId!!
                                it[StatementImportMatches.userId]   = uid
                            }
                        }
                    }
                    reconciliadosEnEsteImporte += parejaId!!

                    if (dec.parsed.category != existCat) {
                        Stores.merchantRules.saveRule(uid, MerchantRule(
                            merchantPattern = dec.parsed.merchant.lowercase().trim(),
                            category = finalCategory,
                        ))
                    }
                    reconciledCount++
                } else {
                    // No es el mismo movimiento (otra cuenta, o ya usado en este importe): la fila
                    // del extracto es un movimiento real y entra como nuevo en vez de perderse.
                    val creado = createEventFromParsed(dec.parsed, decision.accountId, uid, importId)
                    if (creado != null) { importedCount++; reconciliadosEnEsteImporte += creado } else sinFecha++
                }
            } else {
                val creado = createEventFromParsed(dec.parsed, decision.accountId, uid, importId)
                if (creado != null) { importedCount++; reconciliadosEnEsteImporte += creado } else sinFecha++
            }
        }

        dbQuery {
            StatementImports.insert {
                it[id]             = importId
                it[userId]         = uid
                it[accountId]      = decision.accountId
                it[bankName]       = decision.bankName
                it[period]         = decision.period
                it[importedAt]     = System.currentTimeMillis()
                it[StatementImports.importedCount]   = importedCount
                it[StatementImports.reconciledCount] = reconciledCount
            }
        }

        // Trigger silencioso de detección de suscripciones (spec 2026-07-22-detect-on-import):
        // best-effort — un fallo aquí JAMÁS falla el import; "Re-escanear" queda como fallback.
        runCatching { runSubscriptionDetection(uid) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                call.application.log.warn("detect-on-import falló para $uid", it)
            }

        call.respond(HttpStatusCode.OK, mapOf("imported" to importedCount + reconciledCount, "sinFecha" to sinFecha))
    }

    get("/api/statements/imports") {
        val uid = call.userId()
        val imports = dbQuery {
            StatementImports.selectAll()
                .where { StatementImports.userId eq uid }
                .orderBy(StatementImports.importedAt, SortOrder.DESC)
                .map { rowToStatementImport(it) }
        }
        call.respond(imports)
    }

    /**
     * **Deshacer un importe.** Antes no existía: un extracto importado en la cuenta equivocada solo se
     * arreglaba anulando fila por fila.
     *
     * - Lo que el importe **creó** (`source = STATEMENT` con su id) se **anula** —no se borra—, igual
     *   que un movimiento suelto: queda el rastro y el saldo vuelve a donde estaba.
     * - Lo que el importe solo **concilió** (un SMS o algo anotado a mano) se queda: era un
     *   movimiento real antes del extracto. Solo se le suelta el vínculo con el importe.
     * - El registro del importe se borra.
     *
     * Todo en una transacción. 404 si no existe o es de otro usuario.
     */
    delete("/api/statements/imports/{id}") {
        val uid = call.userId()
        val importId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
        val existia = dbQuery {
            val fila = StatementImports.selectAll()
                .where { (StatementImports.id eq importId) and (StatementImports.userId eq uid) }
                .firstOrNull() ?: return@dbQuery false
            val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
            val delImporte = Events.selectAll()
                .where { (Events.userId eq uid) and (Events.statementImportId eq importId) }
                .map { it[Events.id] to it[Events.eventSource] }
            // **Lo que otro importe vivo también concilió no se anula: pasa a ese importe.**
            // Reimportar el mismo extracto deja el movimiento apuntando al primero; sin esto,
            // deshacer el primero anulaba compras que el segundo todavía reclamaba. Solo quedan
            // filas de importes vivos: deshacer uno borra las suyas (abajo).
            StatementImportMatches.deleteWhere {
                (StatementImportMatches.importId eq importId) and (StatementImportMatches.userId eq uid)
            }
            val idsDelImporte = delImporte.map { it.first }
            val heredero = if (idsDelImporte.isEmpty()) emptyMap() else StatementImportMatches.selectAll()
                .where { (StatementImportMatches.userId eq uid) and (StatementImportMatches.eventId inList idsDelImporte) }
                .map { it[StatementImportMatches.eventId] to it[StatementImportMatches.importId] }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, importes) -> importes.max() }
            heredero.forEach { (eventId, otroImporte) ->
                Events.update({ (Events.userId eq uid) and (Events.id eq eventId) }) { it[statementImportId] = otroImporte }
                StatementImportMatches.deleteWhere {
                    (StatementImportMatches.importId eq otroImporte) and (StatementImportMatches.eventId eq eventId)
                }
            }
            val ahora = System.currentTimeMillis()
            delImporte.filter { (id, origen) -> origen == EventSource.STATEMENT.name && id !in anulados && id !in heredero }.forEach { (id, _) ->
                VoidEvents.insert {
                    it[VoidEvents.id] = "void_${UUID.randomUUID()}"
                    it[VoidEvents.userId] = uid
                    it[VoidEvents.originalEventId] = id
                    it[VoidEvents.reason] = "Se deshizo el importe del extracto"
                    it[VoidEvents.timestamp] = ahora
                }
            }
            val conciliados = delImporte.filter { (id, origen) -> origen != EventSource.STATEMENT.name && id !in heredero }.map { it.first }
            if (conciliados.isNotEmpty()) {
                Events.update({ (Events.userId eq uid) and (Events.id inList conciliados) }) { it[statementImportId] = null }
            }
            StatementImports.deleteWhere { (StatementImports.id eq fila[StatementImports.id]) and (StatementImports.userId eq uid) }
            true
        }
        if (existia) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }

    get("/api/statements/imports/{id}") {
        val uid = call.userId()
        val importId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing id")
            return@get
        }

        val importRow = dbQuery {
            StatementImports.selectAll()
                .where { (StatementImports.id eq importId) and (StatementImports.userId eq uid) }
                .firstOrNull()
        }
        if (importRow == null) {
            call.respond(HttpStatusCode.NotFound, "Import not found")
            return@get
        }

        val events = dbQuery {
            val types = accountTypesFor(uid)
            Events.selectAll()
                .where { (Events.statementImportId eq importId) and (Events.userId eq uid) }
                .map { it.toFinancialEvent().withCashFlowFlag(types) }
                // **El mismo orden que el resto de la app**, no el que devuelva la base.
                //
                // Sin esto, la pantalla de revisión listaba los movimientos importados en el
                // orden físico de la tabla — el que un UPDATE o un VACUUM cambia sin avisar — y el
                // dueño los repasa uno por uno contra el PDF. Es el mismo bug que tenía la bandeja
                // de SMS con 96 mensajes adentro: invisible con cuatro filas, arbitrario con
                // cuarenta. `MAS_RECIENTE_PRIMERO` es el criterio que ya usan `/by-day` y la
                // lista de Movimientos, así que revisar un extracto y mirarlo después en la app
                // muestran las mismas filas en el mismo orden.
                .masRecientePrimero()
        }

        call.respond(StatementImportDetail(rowToStatementImport(importRow), events))
    }
}

/** El id del evento creado, o `null` si la fila no tenía una fecha legible y se saltó. */
private suspend fun createEventFromParsed(tx: ParsedTransaction, accountId: String, uid: String, importId: String): String? {
    // **Sin fecha no se inventa hoy.** Una fila cuya fecha no se entiende caía en el día del
    // importe: otro mes, y fuera del emparejamiento de duplicados. Se salta y se cuenta, para que
    // la respuesta diga cuántas quedaron afuera.
    val dia = fechaDelExtracto(tx.date) ?: return null
    val eventId = "ev_${UUID.randomUUID()}"
    // La fecha del extracto es un día civil de Bogotá: se sella a SU medianoche (no a la de
    // UTC), para que al agrupar por día/mes vuelva a caer en el mismo día.
    val ts = appDateToEpochMillis(dia)
    dbQuery {
        Events.insert {
            it[id]                   = eventId
            it[userId]               = uid
            it[Events.accountId]     = accountId
            it[type]                 = tx.type.name
            it[amount]               = tx.amount
            it[Events.currency]      = tx.currency
            // La categoría reservada no puede NACER acá. `tx.category` viene del ImportDecision
            // del cliente o del texto libre con que el parser LLM etiquetó la fila: un
            // «Traspaso» ahí adentro fabricaba media pata —un gasto real del dueño que
            // isCashFlow deja fuera del mes— sin ninguna pata hermana que explicara adónde fue
            // la plata. Se cae a «Otros» en vez de rechazar la importación entera: la fila del
            // extracto es un gasto real y perderla sería peor que recategorizarla, y el dueño
            // puede corregirla después desde Movimientos.
            // TODAS las reservadas, no solo «Traspaso».
            //
            // Esta línea miraba una sola: las otras cinco —«Pago de tarjeta», «Saldo inicial»,
            // «Cuenta eliminada», «Descuento de nómina» y «Pago de un tercero»— se escribían tal
            // cual sobre un evento nuevo del extracto, y `isCashFlow` lo sacaba del mes sin decir
            // nada. La que de verdad muerde es «Pago de tarjeta»: es una frase que un extracto
            // colombiano SÍ trae, y el parser la copia como categoría. Un gasto real importado
            // así desaparece de «Gastos del mes» en silencio.
            // Con UNA excepción: el pago de la tarjeta de verdad. El parser etiqueta así la fila
            // «PAGO AUTOM TC …» del extracto de ahorros, y convertirla en «Otros» inflaba el gasto
            // del mes con plata cuyas compras ya contaron en la tarjeta ($9.809.799 de una vez). Se
            // respeta solo si la DESCRIPCIÓN lo dice con las mismas frases que usa el detector de
            // pagos de tarjeta: una compra real mal etiquetada sigue cayendo en «Otros».
            it[category]             = when {
                tx.category == CARD_PAYMENT_CATEGORY && tx.type == TransactionType.EXPENSE &&
                    looksLikeCardPayment(tx.description + " " + tx.rawText, category = "") -> CARD_PAYMENT_CATEGORY
                isReservedCategory(tx.category) -> FALLBACK_CATEGORY
                else -> tx.category
            }
            it[description]          = tx.description
            it[merchant]             = tx.merchant
            it[timestamp]            = ts
            it[eventSource]          = EventSource.STATEMENT.name
            it[rawPayload]           = tx.rawText.ifBlank { null }
            it[reconciliationStatus] = ReconciliationStatus.RECONCILED.name
            it[syncedAt]             = null
            it[statementImportId]    = importId
            // La fila del extracto trae SU fecha en `timestamp` (la del banco), pero se «anota»
            // ahora, al importarla — ver FinancialEvent.createdAt. Lo que esto arregla es que un
            // movimiento importado hoy para un día viejo no compita a ciegas con los que el dueño
            // anotó a mano en ese mismo día.
            //
            // Ojo con lo que pasa ENTRE las filas de un mismo extracto. Esta función se llama
            // dentro de un `for` y cada llamada abre su propio `dbQuery` (su propia transacción),
            // así que `currentTimeMillis()` se evalúa una vez por fila y normalmente da distinto:
            // el desempate NO cae en el `id`, lo decide `createdAt`. Y como todas las filas del
            // extracto que caen en el mismo día civil comparten `timestamp` al milisegundo —la
            // medianoche de Bogotá que sella el bloque de arriba—, `createdAt` termina siendo el
            // único criterio dentro de ese día: queda arriba la ÚLTIMA fila parseada, o sea el
            // orden del extracto dado vuelta.
            //
            // Que eso sea lo correcto depende de en qué orden venga el extracto, y nada acá lo
            // promete: `decision.imports` sale del parser (FamiriosParser emite en el orden de las
            // filas de la hoja; ClaudeStatementParser, en el que devuelva el LLM). Si el banco
            // lista el día de más viejo a más nuevo, darlo vuelta es justo lo que se quiere; si lo
            // lista al revés, sale invertido. Fijar ese orden es material para otra rama —habría
            // que ordenar `decision.imports` antes del `for`, o sellar la serie a propósito—; acá
            // solo queda escrito lo que el código hace hoy.
            it[createdAt]            = System.currentTimeMillis()
        }
    }
    return eventId
}

private fun rowToStatementImport(row: ResultRow) = StatementImport(
    id              = row[StatementImports.id],
    accountId       = row[StatementImports.accountId],
    bankName        = row[StatementImports.bankName],
    period          = row[StatementImports.period],
    importedAt      = row[StatementImports.importedAt],
    importedCount   = row[StatementImports.importedCount],
    reconciledCount = row[StatementImports.reconciledCount],
)

private fun monthName(month: Int) = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)[month - 1]

/**
 * Los campos del evento existente que la reconciliación necesita, más si es una **pata de
 * traspaso** — un `data class` en vez del `Triple` de antes porque un cuarto elemento sin nombre
 * habría hecho ilegible el destructuring justo en la línea donde se decide si se pisa la
 * categoría.
 */
private data class ExistingEventFields(
    val category: String,
    val description: String,
    val merchant: String?,
    val isTransferLeg: Boolean,
    /** El importe que ya concilió este movimiento, si alguno: reimportar no se lo quita. */
    val importeAnterior: String? = null,
)

/** A dónde va una fila del extracto cuya categoría no se puede usar (ver `createEventFromParsed`). */
private const val FALLBACK_CATEGORY = "Otros"

/**
 * La pareja de una fila de extracto **dentro de [cuentaId]**: un movimiento vivo con el mismo monto,
 * moneda y tipo, a dos días o menos, que no se haya usado ya en este importe. El más cercano en fecha.
 * Mismo criterio que el emparejador del análisis, con la cuenta que ahí no se conocía.
 */
private fun parejaEnLaCuenta(
    uid: String,
    cuentaId: String,
    fila: ParsedTransaction,
    yaUsados: Set<String>,
): String? {
    val cuando = fechaDelExtracto(fila.date)?.let { appDateToEpochMillis(it) } ?: return null
    val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    return Events.selectAll()
        .where {
            (Events.userId eq uid) and (Events.accountId eq cuentaId) and
                (Events.amount eq fila.amount) and (Events.currency eq fila.currency) and
                (Events.type eq fila.type.name)
        }
        .map { it[Events.id] to it[Events.timestamp] }
        .filter { (id, ts) -> id !in anulados && id !in yaUsados && abs(cuando - ts) <= 2 * 86_400_000L }
        .minByOrNull { (_, ts) -> abs(cuando - ts) }
        ?.first
}

/**
 * La fecha de una fila de extracto, en los formatos que de verdad llegan: el ISO que pide el parser
 * (`2026-05-28`), el ISO sin ceros que a veces devuelve (`2026-5-28`) y el colombiano (`28/05/2026`).
 * `null` si no es ninguno: quien llama no inventa una.
 */
internal fun fechaDelExtracto(texto: String): LocalDate? {
    val t = texto.trim()
    runCatching { return LocalDate.parse(t) }
    Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").matchEntire(t)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }
    Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(t)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
    return null
}
