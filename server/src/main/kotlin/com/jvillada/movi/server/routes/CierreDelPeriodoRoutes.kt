package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.CierresDescartados
import com.jvillada.movi.server.db.CierresGuardados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.ActualizarCierreRequest
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.DescartarCierreRequest
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PlataDelDisponible
import com.jvillada.movi.shared.model.cambioDesdeElCierre
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.cierreDelPeriodo
import com.jvillada.movi.shared.model.disponibleAlCerrar
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.seMuestraEnHoy
import com.jvillada.movi.shared.model.DIAS_DE_LA_TARJETA_DE_CIERRE
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.update
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * # «Cerró tu período» (el resumen de cierre)
 *
 * - `GET /api/cierre-del-periodo` → el [CierreDelPeriodo] del período que acaba de cerrar, mientras
 *   la tarjeta de Hoy se muestra (los primeros días del período nuevo y sin descartar; ver
 *   [seMuestraEnHoy]). Fuera de eso, o sin nada que contar, **204**. La lee Hoy y el aviso del teléfono
 *   (`AvisoDeCierreWorker`).
 * - `POST /api/cierre-del-periodo/descartar` → la tarjeta de ese período no vuelve a salir, en ningún
 *   aparato. Idempotente.
 *
 * El resumen lo arma [cierreDelPeriodoDe], la MISMA función que usa el correo
 * (`CorreoDeCierre.kt`): la tarjeta, el aviso y el correo no pueden decir cifras distintas.
 */
fun Route.cierreDelPeriodoRoutes() {
    get("/api/cierre-del-periodo") {
        val uid = call.userId()
        val cierre = cierreDelPeriodoDe(uid, AppClock.today(), soloSiSeMuestra = true)
        if (cierre == null || !seMuestraEnHoy(cierre)) return@get call.respond(HttpStatusCode.NoContent)
        call.respond(cierre)
    }

    post("/api/cierre-del-periodo/descartar") {
        val uid = call.userId()
        val periodo = runCatching { call.receive<DescartarCierreRequest>() }.getOrNull()?.periodo?.trim()
        if (periodo == null || periodoDelPrefijo(periodo) == null) {
            return@post call.respond(HttpStatusCode.BadRequest, "Falta el período")
        }
        dbQuery {
            CierresDescartados.insertIgnore {
                it[userId] = uid
                it[CierresDescartados.periodo] = periodo
                it[descartadoEn] = System.currentTimeMillis()
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Vuelta 95 · **«Actualizar»**: la foto del cierre de [ActualizarCierreRequest.periodo] se reemplaza por
     * el cálculo de hoy y se devuelve el cierre nuevo. Solo se puede actualizar el último período
     * cerrado (el que sirve `GET`); otro contesta 404 y no toca nada. Si hoy no queda nada que contar
     * (se anuló todo), la foto se borra y contesta 204.
     */
    post("/api/cierre-del-periodo/actualizar") {
        val uid = call.userId()
        val periodo = runCatching { call.receive<ActualizarCierreRequest>() }.getOrNull()?.periodo?.trim()
        if (periodo == null || periodoDelPrefijo(periodo) == null) {
            return@post call.respond(HttpStatusCode.BadRequest, "Falta el período")
        }
        // Otro período que el último cerrado: 404, y no se toca nada. Si es el último pero ya no queda
        // nada que contar (se anuló todo), la foto se borra y contesta 204: la tarjeta se va.
        val hoy = AppClock.today()
        if (periodoCerradoDe(uid, hoy) != periodo) return@post call.respond(HttpStatusCode.NotFound)
        val cierre = cierreDelPeriodoDe(uid, hoy, reemplazarLaFoto = true, soloElPeriodo = periodo)
            ?: return@post call.respond(HttpStatusCode.NoContent)
        call.respond(cierre)
    }
}

private const val MEDIO_DIA_MS = 12L * 60 * 60 * 1000

/** El prefijo del último período cerrado a [hoy] (el que `GET /api/cierre-del-periodo` resume). */
private suspend fun periodoCerradoDe(uid: String, hoy: LocalDate): String {
    val ajustes = ajustesDePeriodoDe(uid)
    return periodoAnterior(periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)).prefijo
}

/**
 * **El resumen de cierre del período anterior al que contiene [hoy]**, o `null` si no hay nada que
 * contar: el dueño no tenía movimientos en ese período (ni pagos en su lista).
 *
 * Cada cifra sale de la función de siempre (ver `CierreDelPeriodo.kt` en `:core`):
 * - entró, salió y las categorías: [detalleDePeriodo] («Tus períodos»);
 * - el anterior: [resumenesDePeriodos], solo si Movi lo cubrió entero;
 * - los pagos: [checklistDelPeriodo] sobre `/upcoming` y `/occurrences` como contestaban el ÚLTIMO
 *   día del período (las mismas [proximosPagos] y [ocurrenciasDelPeriodo] con ese «hoy»);
 * - lo que se salió de lo normal: [loQueSeSaleDeLoNormalDe] con ese mismo «hoy», sin lo descartado;
 * - lo que quedó del Disponible: [disponibleDelServidor] + [disponibleAlCerrar], la tarjeta de Hoy de
 *   esa última noche.
 *
 * @param soloSiSeMuestra corta antes de calcular nada si la tarjeta de Hoy no se mostraría (pasaron
 *   los primeros días, o el dueño la cerró): Hoy pide esta ruta en cada carga, y el resumen no es
 *   barato.
 */
internal suspend fun cierreDelPeriodoDe(
    uid: String,
    hoy: LocalDate,
    soloSiSeMuestra: Boolean = false,
    /** Vuelta 95: «Actualizar» — la foto guardada se reemplaza por el cálculo de hoy. */
    reemplazarLaFoto: Boolean = false,
    /** Vuelta 95: con [reemplazarLaFoto], solo si el período cerrado es éste (si no, `null` y no se toca nada). */
    soloElPeriodo: String? = null,
): CierreDelPeriodo? {
    val ajustes = ajustesDePeriodoDe(uid)
    val ahora = appDateToEpochMillis(hoy) + MEDIO_DIA_MS
    val enCurso = periodoDe(ahora, ajustes)
    val cerrado = periodoAnterior(enCurso)
    if (soloElPeriodo != null && cerrado.prefijo != soloElPeriodo) return null
    val inicioDelEnCurso = epochMillisToAppDate(ventanaDe(enCurso, ajustes).first)
    val diaDelPeriodoNuevo = ChronoUnit.DAYS.between(inicioDelEnCurso, hoy).toInt() + 1
    val descartado = dbQuery {
        CierresDescartados.selectAll()
            .where { (CierresDescartados.userId eq uid) and (CierresDescartados.periodo eq cerrado.prefijo) }
            .any()
    }
    if (soloSiSeMuestra && (descartado || diaDelPeriodoNuevo !in 1..DIAS_DE_LA_TARJETA_DE_CIERRE)) return null
    val fresco = armarElCierre(uid, ahora, ajustes, cerrado, diaDelPeriodoNuevo, descartado)
    return conLaFoto(uid, cerrado.prefijo, fresco, diaDelPeriodoNuevo, descartado, reemplazarLaFoto)
}

private val jsonDeLaFoto = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * # La foto del cierre (vuelta 95)
 *
 * El cierre de un período que ya cerró no debe cambiar sin que el dueño se entere: un movimiento viejo
 * que se edita, se anula o llega tarde (un SMS del último día que entra el día 2) movía las cifras de un
 * resumen que ya se había mostrado, mandado por correo y avisado en el teléfono. La primera vez que se
 * arma, el cierre se guarda ([CierresGuardados]) y **las lecturas siguientes devuelven la foto**:
 *
 * - lo que depende de «hoy» ([dia] del período nuevo, [descartado]) se toma de ahora, no de la foto:
 *   es cuándo se muestra, no qué dice;
 * - si el cálculo de hoy ([fresco]) difiere de la foto en lo que entró o salió, el cierre lleva `cambio`
 *   y la tarjeta lo dice; «Actualizar» ([reemplazar]) guarda el cálculo de hoy como foto nueva;
 * - el correo y el aviso del teléfono leen por acá, así que dicen lo que dijo la foto;
 * - una foto que no se puede leer (otra forma de datos) se trata como ausente y se reemplaza.
 *
 * [fresco] `null` = hoy no hay nada que contar: sin foto se devuelve `null` (no se guarda nada); con
 * foto se devuelve la foto, con `cambio` si ya no queda nada de lo que decía.
 */
private suspend fun conLaFoto(
    uid: String,
    periodo: String,
    fresco: CierreDelPeriodo?,
    dia: Int,
    descartado: Boolean,
    reemplazar: Boolean,
): CierreDelPeriodo? {
    val guardada = leerLaFoto(uid, periodo)
    if (reemplazar && fresco == null) {
        // «Actualizar» y hoy no queda nada (se anuló todo): la foto vieja ya no dice la verdad, y una
        // foto vacía tampoco sirve. Se borra, y la tarjeta deja de decir «Cambió…».
        dbQuery {
            CierresGuardados.deleteWhere { (CierresGuardados.userId eq uid) and (CierresGuardados.periodo eq periodo) }
        }
        return null
    }
    if (guardada == null || (reemplazar && fresco != null)) {
        val nuevo = fresco ?: return null
        val ahora = System.currentTimeMillis()
        val datos = jsonDeLaFoto.encodeToString(
            CierreDelPeriodo.serializer(),
            nuevo.copy(diaDelPeriodoNuevo = 0, descartado = false, cerradoEn = null, cambio = null),
        )
        dbQuery {
            val llave = (CierresGuardados.userId eq uid) and (CierresGuardados.periodo eq periodo)
            val ya = CierresGuardados.selectAll().where { llave }.any()
            if (ya) {
                CierresGuardados.update({ llave }) {
                    it[CierresGuardados.datos] = datos
                    it[guardadoEn] = ahora
                }
            } else {
                CierresGuardados.insertIgnore {
                    it[CierresGuardados.userId] = uid
                    it[CierresGuardados.periodo] = periodo
                    it[CierresGuardados.datos] = datos
                    it[guardadoEn] = ahora
                }
            }
        }
        // Si dos lecturas se cruzaron, gana la fila que quedó escrita (la otra no la pisó).
        val escrita = leerLaFoto(uid, periodo)
        return nuevo.copy(cerradoEn = escrita?.second ?: ahora)
    }
    val (foto, cuando) = guardada
    return foto.copy(
        diaDelPeriodoNuevo = dia,
        descartado = descartado,
        cerradoEn = cuando,
        cambio = cambioDesdeElCierre(foto, fresco),
    )
}

/** La foto guardada y cuándo se tomó, o `null` si no hay (o no se entiende). */
private suspend fun leerLaFoto(uid: String, periodo: String): Pair<CierreDelPeriodo, Long>? = dbQuery {
    CierresGuardados.selectAll()
        .where { (CierresGuardados.userId eq uid) and (CierresGuardados.periodo eq periodo) }
        .firstOrNull()
}?.let { fila ->
    runCatching { jsonDeLaFoto.decodeFromString(CierreDelPeriodo.serializer(), fila[CierresGuardados.datos]) }
        .getOrNull()?.let { it to fila[CierresGuardados.guardadoEn] }
}

/** El cierre de hoy, calculado de cero con las cifras de siempre (ver [cierreDelPeriodoDe]). */
private suspend fun armarElCierre(
    uid: String,
    ahora: Long,
    ajustes: PeriodSettings,
    cerrado: com.jvillada.movi.shared.model.PeriodoFinanciero,
    diaDelPeriodoNuevo: Int,
    descartado: Boolean,
): CierreDelPeriodo? {
    val ventana = ventanaDe(cerrado, ajustes)
    val ultimoDia = epochMillisToAppDate(ventana.last)
    val detalle = dbQuery { detalleDePeriodo(uid, cerrado.prefijo, ahora, ajustes) } ?: return null
    val pagos = checklistDelPeriodo(
        upcoming = proximosPagos(uid, ultimoDia),
        ocurrencias = ocurrenciasDelPeriodo(uid, ultimoDia),
        periodo = cerrado,
        settings = ajustes,
    )
    if (detalle.resumen.movimientos == 0 && pagos.isEmpty()) return null

    val reglasDeCredito = loadCreditRulePairs(uid).map { it.first }
    val (anterior, anomalias, servidor) = dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }.toSet()
        Triple(
            anteriorCubierto(uid, cerrado, ahora, ajustes),
            loQueSeSaleDeLoNormalDe(uid, ultimoDia, ajustes),
            disponibleDelServidor(uid, ultimoDia, ajustes, ventana.first, ventana.last + 1, anulados, reglasDeCredito),
        )
    }
    val disponible = disponibleAlCerrar(
        ingresosRecibidos = detalle.resumen.entradas,
        pagos = pagos,
        gastoVariablePorDia = servidor.gastoVariablePorDia,
        desde = detalle.resumen.desde,
        hasta = detalle.resumen.hasta,
        plata = PlataDelDisponible(
            saldoAlInicio = servidor.plata.saldoAlInicio,
            entradas = servidor.plata.entradas,
            guardado = servidor.plata.guardado,
            otrosPagosDeDeuda = servidor.pagosDeDeudaFueraDelChecklist,
            // Sin `apartado`: el cierre es historia. Un apartado de hoy no cambia lo que quedó en un
            // período que ya cerró.
        ),
    )
    return cierreDelPeriodo(
        resumen = detalle.resumen,
        porCategoria = detalle.porCategoria,
        anterior = anterior,
        pagos = pagos,
        anomalias = anomalias,
        disponible = disponible,
        diaDelPeriodoNuevo = diaDelPeriodoNuevo,
        descartado = descartado,
    )
}

/**
 * El [com.jvillada.movi.shared.model.ResumenDePeriodo] del período anterior a [cerrado], **solo si
 * Movi lo cubrió entero** (el primer movimiento del dueño es anterior a su arranque): contra un
 * período a medio anotar todo parecería «más». Misma guarda que el gasto del día a día de la caja y
 * que «lo normal» de las anomalías.
 */
private fun org.jetbrains.exposed.sql.Transaction.anteriorCubierto(
    uid: String,
    cerrado: com.jvillada.movi.shared.model.PeriodoFinanciero,
    ahora: Long,
    ajustes: PeriodSettings,
): com.jvillada.movi.shared.model.ResumenDePeriodo? {
    val anterior = periodoAnterior(cerrado)
    val primero = Events.timestamp.min()
    val primerMovimiento = Events.select(primero).where { Events.userId eq uid }.firstOrNull()?.get(primero) ?: return null
    if (primerMovimiento > ventanaDe(anterior, ajustes).first) return null
    return resumenesDePeriodos(uid, ahora, ajustes).firstOrNull { it.id == anterior.prefijo }
}
