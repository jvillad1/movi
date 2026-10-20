package com.jvillada.movi.server.ai

import com.jvillada.movi.server.apartados.patrimonioDelUsuario
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.hayMovimientosEnOtraMoneda
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.routes.bandejaConLosPagos
import com.jvillada.movi.server.routes.cardPaymentCandidateCount
import com.jvillada.movi.server.routes.cuadresPorAvisoDe
import com.jvillada.movi.server.routes.debitosPorConfirmar
import com.jvillada.movi.server.routes.disponibleDelServidor
import com.jvillada.movi.server.routes.flujoDeCaja
import com.jvillada.movi.server.routes.gastoDelAnteriorAMismaAltura
import com.jvillada.movi.server.routes.loQueSeSaleDeLoNormalDe
import com.jvillada.movi.server.routes.movimientosDeFlujo
import com.jvillada.movi.server.routes.ocurrenciasDelPeriodo
import com.jvillada.movi.server.routes.pequenosDelPeriodo
import com.jvillada.movi.server.routes.proximosPagos
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.DisponibleDelPeriodo
import com.jvillada.movi.shared.model.NivelDelGasto
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PequenosQueSuman
import com.jvillada.movi.shared.model.PlataDelDisponible
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Tendencia
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.contraElAnterior
import com.jvillada.movi.shared.model.cuantosPagosPorRevisar
import com.jvillada.movi.shared.model.cuentasSinCuadrar
import com.jvillada.movi.shared.model.disponibleAlDia
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.montoDelDebito
import com.jvillada.movi.shared.model.pagosPendientes
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.pesosDelCierre
import com.jvillada.movi.shared.model.textoDeDebitosEnHoy
import com.jvillada.movi.shared.model.textoDeLasPropuestasPorAviso
import com.jvillada.movi.shared.model.textoDelAvisoDeCuadre
import com.jvillada.movi.shared.model.ventanaDe
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/**
 * # Movi AI contesta las tres preguntas sugeridas sin la IA
 *
 * Desde el 4-oct-2026 la cuenta de Anthropic no tiene crédito y el chat contestaba «Movi AI no está
 * disponible» a todo. Las tres preguntas de respaldo de «Pregúntale a Movi» —las que más se tocan,
 * en Hoy y en el chat— no necesitan un modelo: lo que contestan son cifras que Movi ya calcula para
 * sus pantallas. Así que se contestan **siempre** acá, sin llamar a Anthropic, aunque haya crédito:
 * es más barato, más rápido, y una frase armada con las cifras de Movi no puede inventar ninguna.
 *
 * El chip manda su [PreguntaSugerida.id] y la ruta lo reconoce por eso (ver el KDoc de
 * [PreguntaSugerida]); lo que el dueño escribe a mano sigue yendo al modelo.
 *
 * ## Ninguna cifra se suma acá
 *
 * Cada una sale de la función que ya la calcula para una pantalla, con los mismos datos que esa
 * pantalla lee del server:
 *
 * - **Entró / salió y el gasto por categoría**: [movimientosDeFlujo] + [flujoDeCaja], la regla de
 *   «Ingresos» y «Gastos» del Inicio (`monthCashFlow`).
 * - **Contra el período pasado a la misma altura**: [gastoDelAnteriorAMismaAltura] y
 *   [contraElAnterior], las flechas de «¿En qué se va?».
 * - **Te quedan, la meta diaria y «te pasaste»**: [disponibleAlDia], la MISMA `disponibleDelPeriodo`
 *   que pinta Hoy, sobre [disponibleDelServidor] (lo que el resumen del Inicio le manda) y el
 *   checklist del período.
 * - **Los pagos del período**: [checklistDelPeriodo] sobre [proximosPagos] y [ocurrenciasDelPeriodo]
 *   (lo que contestan `/api/payments/upcoming` y `/occurrences`), con [faltaPorPagar].
 * - **Pequeños que suman**: [pequenosDelPeriodo], la del detalle del período.
 * - **Lo que espera al dueño**: [cuantosPagosPorRevisar] sobre [bandejaConLosPagos] (el «N por
 *   revisar» de Movimientos), [cuadresPorAvisoDe], [debitosPorConfirmar],
 *   [loQueSeSaleDeLoNormalDe] y [cuentasSinCuadrar] — lo mismo que lee «Para revisar» en Hoy.
 *
 * ## Cómo suena
 *
 * Frases cortas, de tú, con las cifras completas («$1.234.567», no «$1,2M»: es una respuesta para
 * leer, no una tarjeta), y sin «según mis cálculos»: las cifras son las de Movi, no una estimación.
 * Lo que no aplica no se dice.
 */

/** Lo que queda en `ai_turns.modelo` de un turno que contestó Movi sin llamar a ningún modelo. */
internal const val MODELO_SIN_IA = "sin-modelo"

/** El nombre con que el turno sin modelo deja anotado qué pregunta contestó (`ai_turns.consultas`). */
internal const val CONSULTA_DE_LA_PREGUNTA_SUGERIDA = "pregunta_sugerida"

/** Cuántas categorías nombra «¿En qué se va?». Las mismas cinco filas que la tarjeta de Hoy. */
internal const val CATEGORIAS_DE_EN_QUE_SE_VA = 5

/** Cuántos pagos pendientes se nombran uno por uno; del resto se dice cuántos son. */
private const val PAGOS_QUE_SE_NOMBRAN = 5

/** Cuántas cosas lista «¿Qué revisar primero?» como mucho. */
internal const val COSAS_PARA_REVISAR = 6

/**
 * Lo que hace falta para contestar, ya leído. Lo que una pregunta no usa queda vacío: cada una lee
 * solo lo suyo ([datosDeLasSugeridas]).
 */
internal data class DatosDeLasSugeridas(
    /** «25 de septiembre al 24 de octubre». */
    val rango: String,
    val entro: Long,
    val gastoPorCategoria: Map<String, Long>,
    /** El gasto por categoría del período pasado a la misma altura; `null` si Movi no lo cubrió entero. */
    val anterior: Map<String, Long>? = null,
    val diasComparados: Int? = null,
    /** La tarjeta Disponible de hoy; `null` si no hay nada honesto que decir. */
    val disponible: DisponibleDelPeriodo? = null,
    /** «Pagos del período», la lista de Plan. */
    val pagos: List<PagoDelPeriodo> = emptyList(),
    val pequenos: List<PequenosQueSuman> = emptyList(),
    /** Categoría → límite, como en la tabla `budgets`. */
    val presupuestos: List<Pair<String, Long>> = emptyList(),
    /** Los pagos por revisar en la bandeja (uno por pago, aunque lo avisen tres veces). */
    val porRevisar: Int = 0,
    /** Movimientos que parecen pago de tarjeta y nadie marcó. */
    val pagosDeTarjeta: Int = 0,
    val cuadresPorAviso: List<PropuestaDeCuadrePorAviso> = emptyList(),
    val debitos: List<DebitoAutomaticoPorConfirmar> = emptyList(),
    val anomalias: List<Anomalia> = emptyList(),
    val sinCuadrar: List<Account> = emptyList(),
) {
    val salio: Long get() = gastoPorCategoria.values.sum()
}

// ── Leer ─────────────────────────────────────────────────────────────────────

private const val MEDIO_DIA_MS = 12L * 60 * 60 * 1000

/**
 * **Lo que [pregunta] necesita de [uid], leído con las funciones de las pantallas** (ver el KDoc del
 * archivo). [hoy] entra por parámetro para poder probarlo con un día fijo; la ruta pasa el de Bogotá.
 */
internal suspend fun datosDeLasSugeridas(
    uid: String,
    pregunta: PreguntaSugerida,
    hoy: LocalDate,
    ahora: Long = System.currentTimeMillis(),
): DatosDeLasSugeridas {
    val ajustes = ajustesDePeriodoDe(uid)
    // Mediodía de [hoy]: un instante del día que no cae en el borde de ninguna ventana.
    val instante = appDateToEpochMillis(hoy) + MEDIO_DIA_MS
    val periodo = periodoDe(instante, ajustes)
    val ventana = ventanaDe(periodo, ajustes)
    val desde = ventana.first
    val hasta = ventana.last + 1

    val conPagos = pregunta != PreguntaSugerida.EN_QUE_SE_VA
    val revisar = pregunta == PreguntaSugerida.QUE_REVISAR
    // Las que abren su propia transacción, antes de la de abajo.
    val pagos = if (conPagos) {
        checklistDelPeriodo(
            upcoming = proximosPagos(uid, hoy),
            ocurrencias = ocurrenciasDelPeriodo(uid, hoy),
            periodo = periodo,
            settings = ajustes,
        )
    } else {
        emptyList()
    }
    val reglasDeCredito = if (conPagos) loadCreditRulePairs(uid).map { it.first } else emptyList()
    val debitos = if (revisar) debitosPorConfirmar(uid, hoy, ahora) else emptyList()

    // La tasa solo si hace falta, para el saldo de lo apartado (ver el resumen del Inicio).
    val usdToCop = if (conPagos && dbQuery { hayMovimientosEnOtraMoneda(uid) }) FxRateService.usdToCop() else 0.0

    return dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }.toSet()
        val tipos = accountTypesFor(uid)
        val movimientos = movimientosDeFlujo(uid, desde, hasta, anulados, tipos)
        val (entro, gastoPorCategoria) = flujoDeCaja(movimientos)
        val anterior = if (revisar) null else gastoDelAnteriorAMismaAltura(uid, instante, ajustes, anulados, tipos)

        val disponible = if (!conPagos) {
            null
        } else {
            val servidor = disponibleDelServidor(
                uid, hoy, ajustes, desde, hasta, anulados, reglasDeCredito,
                apartado = patrimonioDelUsuario(uid, anulados, usdToCop).apartado,
            )
            disponibleAlDia(
                ingresosRecibidos = entro,
                pagos = pagos,
                gastoVariablePorDia = servidor.gastoVariablePorDia,
                desde = epochMillisToAppDate(desde).toString(),
                hasta = epochMillisToAppDate(ventana.last).toString(),
                hoy = hoy.toString(),
                plata = PlataDelDisponible(
                    saldoAlInicio = servidor.plata.saldoAlInicio,
                    entradas = servidor.plata.entradas,
                    guardado = servidor.plata.guardado,
                    otrosPagosDeDeuda = servidor.pagosDeDeudaFueraDelChecklist,
                    apartado = servidor.apartado,
                ),
            )
        }

        DatosDeLasSugeridas(
            rango = rangoLegible(desde, hasta),
            entro = entro,
            gastoPorCategoria = gastoPorCategoria,
            anterior = anterior?.first,
            diasComparados = anterior?.second,
            disponible = disponible,
            pagos = pagos,
            pequenos = if (pregunta == PreguntaSugerida.EN_QUE_SE_VA) pequenosDelPeriodo(uid, desde, hasta, movimientos) else emptyList(),
            presupuestos = if (revisar) {
                Budgets.selectAll().where { Budgets.userId eq uid }.map { it[Budgets.category] to it[Budgets.monthlyLimit] }
            } else {
                emptyList()
            },
            // Igual que el resumen del Inicio: la bandeja entera solo si hay algo pendiente.
            porRevisar = if (!revisar || SmsMessages.selectAll()
                    .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq SMS_STATE_PENDING) }
                    .empty()
            ) {
                0
            } else {
                cuantosPagosPorRevisar(bandejaConLosPagos(uid, ahora))
            },
            pagosDeTarjeta = if (revisar) cardPaymentCandidateCount(uid, anulados, tipos) else 0,
            cuadresPorAviso = if (revisar) cuadresPorAvisoDe(uid) else emptyList(),
            debitos = debitos,
            anomalias = if (revisar) loQueSeSaleDeLoNormalDe(uid, hoy, ajustes, conDeudas = true) else emptyList(),
            sinCuadrar = if (revisar) cuentasSinCuadrar(cuentasConSusSellos(uid, anulados), ahora) else emptyList(),
        )
    }
}

/**
 * Las cuentas de [uid] con **cuándo se cuadraron por última vez y desde cuándo existen**, los dos
 * sellos que `enrichWith` le pone a la lista de Cuentas (de ahí lee el aviso de Hoy). Acá en SQL,
 * con los mismos criterios —sin anulados, el ajuste por su categoría—, sin bajar cada movimiento.
 */
private fun Transaction.cuentasConSusSellos(uid: String, anulados: Set<String>): List<Account> {
    fun vivos() = if (anulados.isEmpty()) Events.userId eq uid else (Events.userId eq uid) and (Events.id notInList anulados.toList())
    val primero = Events.timestamp.min()
    val primeros = Events.select(Events.accountId, primero).where { vivos() }.groupBy(Events.accountId)
        .associate { it[Events.accountId] to it[primero] }
    val ultimo = Events.timestamp.max()
    val ajustes = Events.select(Events.accountId, ultimo)
        .where { vivos() and (Events.category eq ADJUSTMENT_CATEGORY) }
        .groupBy(Events.accountId)
        .associate { it[Events.accountId] to it[ultimo] }
    return Accounts.selectAll().where { Accounts.userId eq uid }.mapNotNull { fila ->
        val cuenta = runCatching { fila.toAccount() }.getOrNull() ?: return@mapNotNull null
        cuenta.copy(lastAdjustmentAt = ajustes[cuenta.id], firstEventAt = primeros[cuenta.id])
    }
}

// ── Contestar ────────────────────────────────────────────────────────────────

/** La respuesta a [pregunta] con [d]. Pura: la prueban las pruebas con datos armados a mano. */
internal fun respuestaSinModelo(pregunta: PreguntaSugerida, d: DatosDeLasSugeridas): String = when (pregunta) {
    PreguntaSugerida.COMO_VOY -> comoVoy(d)
    PreguntaSugerida.EN_QUE_SE_VA -> enQueSeVa(d)
    PreguntaSugerida.QUE_REVISAR -> queRevisar(d)
}

/**
 * **¿Cómo voy este período?** Cuánto te queda y para cuántos días con la meta diaria (la frase de la
 * tarjeta Disponible), lo que entró y salió, cómo va el gasto contra los mismos días del período
 * pasado, y los pagos que faltan con su total.
 */
internal fun comoVoy(d: DatosDeLasSugeridas): String = buildList {
    add(fraseDelDisponible(d.disponible))
    add("Este período (${d.rango}) entró ${pesosDelCierre(d.entro)} y salió ${pesosDelCierre(d.salio)}.")
    contraElPasado(d)?.let(::add)
    add(lineaDeLosPagos(d.pagos))
}.filterNotNull().joinToString("\n\n")

/**
 * La misma decisión que `fraseDelDisponible` de Hoy —manda la peor ventana—, con la cifra entera.
 * `null` no pasa: sin disponible se dice por qué no hay cifra.
 */
private fun fraseDelDisponible(disponible: DisponibleDelPeriodo?): String {
    val d = disponible
        ?: return "Todavía no puedo decirte cuánto te queda: anota tu sueldo o el saldo de tus cuentas y Movi lo calcula."
    if (!d.hayMargen) return sinMargen(d)
    val periodo = d.periodo
    return when {
        periodo.nivel == NivelDelGasto.PASADO ->
            "Te pasaste del disponible del período por ${pesosDelCierre(-periodo.teQuedan)}."
        d.semana.nivel == NivelDelGasto.PASADO ->
            "Esta semana te pasaste de la meta por ${pesosDelCierre(-d.semana.teQuedan)}. " +
                "Al período le quedan ${pesosDelCierre(periodo.teQuedan)}${paraLosDias(d)}."
        d.hoy.nivel == NivelDelGasto.PASADO ->
            "Hoy te pasaste de la meta del día por ${pesosDelCierre(-d.hoy.teQuedan)}. " +
                "A la semana le quedan ${pesosDelCierre(d.semana.teQuedan)}."
        periodo.teQuedan == 0L -> "Ya usaste todo el disponible del período."
        d.diasQueQuedan <= 1 -> "Te quedan ${pesosDelCierre(periodo.teQuedan)} para cerrar el período."
        else -> buildString {
            append("Te quedan ${pesosDelCierre(periodo.teQuedan)} para ${d.diasQueQuedan} días. ")
            append("Tu meta diaria es ${pesosDelCierre(d.metaPorDia)}.")
            if (periodo.contraElRitmo > 0L) {
                append(" Vas ${pesosDelCierre(periodo.contraElRitmo)} por encima de lo previsto a hoy.")
            }
        }
    }
}

/** « para 12 días», o nada el último día. */
private fun paraLosDias(d: DisponibleDelPeriodo): String =
    if (d.diasQueQuedan > 1) " para ${d.diasQueQuedan} días" else " para cerrar el período"

/** La frase de la tarjeta cuando los fijos se llevan todo (ver `sinMargen` en `:shared`). */
private fun sinMargen(d: DisponibleDelPeriodo): String {
    val plata = d.plata
    if (plata == null) {
        return if (d.disponible < 0L) "Los fijos del período superan tus ingresos por ${pesosDelCierre(-d.disponible)}."
        else "Los fijos del período se llevan todos tus ingresos."
    }
    val quienes = if (plata.otrosPagosDeDeuda > 0L) "Los fijos y los otros pagos de deuda" else "Los fijos del período"
    return if (d.disponible < 0L) "$quienes superan lo que tenías y lo que entró por ${pesosDelCierre(-d.disponible)}."
    else "$quienes se llevan todo lo que tenías y lo que entró."
}

/** «Contra los mismos 19 días del período pasado, gastaste $300.000 más.», o `null` sin con qué comparar. */
private fun contraElPasado(d: DatosDeLasSugeridas): String? {
    val anterior = d.anterior ?: return null
    val dias = d.diasComparados ?: return null
    val antes = anterior.values.sum()
    val contra = contraElAnterior(d.salio, antes) ?: return null
    val cuando = if (dias == 1) "el primer día del período pasado" else "los mismos $dias días del período pasado"
    return when (contra.tendencia) {
        Tendencia.SUBE -> "Contra $cuando, gastaste ${pesosDelCierre(contra.diferencia)} más (entonces llevabas ${pesosDelCierre(antes)})."
        Tendencia.BAJA -> "Contra $cuando, gastaste ${pesosDelCierre(contra.diferencia)} menos (entonces llevabas ${pesosDelCierre(antes)})."
        Tendencia.IGUAL -> "Vas gastando casi lo mismo que en $cuando (${pesosDelCierre(antes)})."
    }
}

/** Los pagos que faltan del período, con su total, o que ya salieron todos. `null` sin pagos en la lista. */
private fun lineaDeLosPagos(pagos: List<PagoDelPeriodo>): String? {
    if (pagos.none { !it.esIngreso }) return null
    val pendientes = pagosPendientes(pagos).sortedBy { it.diasParaVencer }
    if (pendientes.isEmpty()) {
        return if (pagos.count { !it.esIngreso } == 1) "Ya salió el único pago del período." else "Ya salieron todos los pagos del período."
    }
    val total = faltaPorPagar(pagos)
    val cuantos = if (pendientes.size == 1) "1 pago" else "${pendientes.size} pagos"
    val nombrados = pendientes.take(PAGOS_QUE_SE_NOMBRAN).joinToString("\n") { "- ${renglonDelPago(it)}" }
    val resto = pendientes.size - PAGOS_QUE_SE_NOMBRAN
    return buildString {
        append("Te falta${if (pendientes.size == 1) "" else "n"} $cuantos del período")
        if (total > 0L) append(" por ${pesosDelCierre(total)}")
        if (pendientes.any { it.montoEsSaldo }) append(" (sin contar el saldo de las tarjetas)")
        append(":\n")
        append(nombrados)
        if (resto > 0) append("\n- y ${if (resto == 1) "1 más" else "$resto más"}")
    }
}

/** «Arriendo $1.500.000 · vence en 3 días»; una tarjeta dice su saldo, o su pago mínimo si lo tiene. */
private fun renglonDelPago(p: PagoDelPeriodo): String {
    val monto = when {
        p.montoEsSaldo && p.pagoMinimo != null -> "pago mínimo ${montoDelDebito(p.pagoMinimo!!, p.moneda)}"
        p.montoEsSaldo -> "saldo ${montoDelDebito(p.monto, p.moneda)}"
        else -> montoDelDebito(p.monto, p.moneda)
    }
    return "${p.nombre} $monto · ${cuandoVence(p)}"
}

/** «vence en 3 días», «vence hoy», «venció hace 2 días»: la misma forma que la lista de Plan. */
private fun cuandoVence(p: PagoDelPeriodo): String = when {
    p.diasParaVencer < 0 -> if (p.diasParaVencer == -1) "venció ayer" else "venció hace ${-p.diasParaVencer} días"
    p.diasParaVencer == 0 -> "vence hoy"
    p.diasParaVencer == 1 -> "vence mañana"
    else -> "vence en ${p.diasParaVencer} días"
}

/**
 * **¿En qué se me está yendo más la plata?** Las categorías de más gasto del período (las mismas
 * cinco de «¿En qué se va?» en Hoy, en el mismo orden), cada una contra los mismos días del período
 * pasado, y «Pequeños que suman» si hay.
 */
internal fun enQueSeVa(d: DatosDeLasSugeridas): String {
    val positivas = d.gastoPorCategoria.filterValues { it > 0L }
    if (positivas.isEmpty()) return "Este período (${d.rango}) todavía no hay gastos anotados."
    val ordenadas = positivas.entries
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() })
        .take(CATEGORIAS_DE_EN_QUE_SE_VA)
    val dias = d.diasComparados
    val renglones = ordenadas.mapIndexed { i, (categoria, gastado) ->
        val comparacion = d.anterior?.let { anterior ->
            val antes = anterior[categoria] ?: 0L
            contraElAnterior(gastado, antes)?.let { c ->
                when (c.tendencia) {
                    Tendencia.SUBE -> " · ${pesosDelCierre(c.diferencia)} más que el período pasado"
                    Tendencia.BAJA -> " · ${pesosDelCierre(c.diferencia)} menos que el período pasado"
                    Tendencia.IGUAL -> " · casi igual que el período pasado"
                }
            }
        }.orEmpty()
        "${i + 1}. $categoria ${pesosDelCierre(gastado)}$comparacion"
    }
    return buildList {
        add("Este período (${d.rango}) salieron ${pesosDelCierre(d.salio)}. Lo que más pesa:\n" + renglones.joinToString("\n"))
        if (d.anterior != null && dias != null) {
            add("Comparo contra los primeros $dias ${if (dias == 1) "día" else "días"} del período pasado, los mismos que lleva este.")
        }
        if (d.pequenos.isNotEmpty()) {
            add(
                "Pequeños que suman:\n" + d.pequenos.joinToString("\n") { p ->
                    val que = if (p.porComercio) "${p.veces} compras en ${p.nombre}" else "${p.veces} gastos chicos de ${p.nombre}"
                    "- $que: ${pesosDelCierre(p.total)}"
                },
            )
        }
    }.joinToString("\n\n")
}

/** Una cosa para revisar, con dónde se resuelve. */
private data class CosaQueRevisar(val texto: String, val donde: String)

/**
 * **¿Qué me recomiendas revisar primero?** Una lista en este orden: lo urgente (pagos vencidos, «te
 * pasaste»), lo que espera al dueño («Por revisar», cuadres por aviso, débitos automáticos), lo que
 * se sale de lo normal, y las cuentas que llevan mucho sin cuadrar. Cada punto dice dónde se
 * resuelve; el chat todavía no abre pantallas, así que va en palabras.
 */
internal fun queRevisar(d: DatosDeLasSugeridas): String {
    val conDebito = d.debitos.map { it.ruleId }.toSet()
    val cosas = buildList {
        // 1. Lo urgente.
        val vencidos = d.pagos.filter { it.vencido && it.ruleId !in conDebito }
        when (vencidos.size) {
            0 -> Unit
            1 -> add(CosaQueRevisar("«${vencidos.single().nombre}» venció y no está marcado.", "Si ya lo pagaste, anótalo y Movi lo tilda solo en Plan."))
            else -> add(
                CosaQueRevisar(
                    "${vencidos.size} pagos vencidos sin marcar: ${vencidos.joinToString(", ") { it.nombre }}.",
                    "Si ya los pagaste, anótalos y Movi los tilda solos en Plan.",
                ),
            )
        }
        d.disponible?.let { disp ->
            when {
                !disp.hayMargen && (disp.disponible < 0L || disp.periodo.gastado > 0L) ->
                    add(CosaQueRevisar(sinMargen(disp), "Míralo en la tarjeta Disponible de Plan."))
                disp.periodo.teQuedan < 0L ->
                    add(CosaQueRevisar("Te pasaste del disponible del período por ${pesosDelCierre(-disp.periodo.teQuedan)}.", "Míralo en la tarjeta Disponible de Plan."))
            }
        }
        d.presupuestos
            .map { (categoria, limite) -> Triple(categoria, limite, d.gastoPorCategoria[categoria] ?: 0L) }
            .filter { (_, limite, gastado) -> estadoDePresupuesto(gastado, limite).estaSuperado }
            .sortedByDescending { (_, limite, gastado) -> gastado - limite }
            .forEach { (categoria, limite, gastado) ->
                add(CosaQueRevisar("Te pasaste del presupuesto de $categoria por ${pesosDelCierre(gastado - limite)}.", "Puedes ajustar el límite o frenar el gasto en Presupuestos."))
            }
        // 2. Lo que espera al dueño.
        if (d.porRevisar > 0) {
            val cuantos = if (d.porRevisar == 1) "1 pago" else "${d.porRevisar} pagos"
            add(CosaQueRevisar("Tienes $cuantos del banco por revisar.", "Hasta confirmarlos no cuentan en el gasto del período: ábrelos en Por revisar."))
        }
        if (d.pagosDeTarjeta > 0) {
            val cuantos = if (d.pagosDeTarjeta == 1) "1 movimiento parece" else "${d.pagosDeTarjeta} movimientos parecen"
            add(CosaQueRevisar("$cuantos pago de tarjeta.", "Márcalos en Por revisar para no contarlos dos veces."))
        }
        textoDeLasPropuestasPorAviso(d.cuadresPorAviso)?.let {
            add(CosaQueRevisar(it, "Te lo dijo el banco en un aviso: en Cuadre de saldos Movi anota la diferencia con un toque."))
        }
        textoDeDebitosEnHoy(d.debitos)?.let {
            add(CosaQueRevisar(it, "El banco lo cobra solo y no avisa: confírmalo en Por revisar para que cuente."))
        }
        // 3. Lo que se sale de lo normal.
        d.anomalias.forEach { add(CosaQueRevisar(it.titulo, it.detalle)) }
        // 4. Las cuentas que llevan mucho sin cuadrar.
        textoDelAvisoDeCuadre(d.sinCuadrar)?.let {
            add(CosaQueRevisar(it, "Compara con el saldo del banco en Cuadre de saldos: los rendimientos y las cuotas de manejo no avisan."))
        }
    }
    if (cosas.isEmpty()) {
        return "No veo nada urgente: no hay pagos vencidos, nada por revisar del banco, nada fuera de lo normal " +
            "y tus cuentas están cuadradas."
    }
    val mostradas = cosas.take(COSAS_PARA_REVISAR)
    return buildString {
        append(if (mostradas.size == 1) "Revisa esto:" else "Revisa esto, en este orden:")
        mostradas.forEachIndexed { i, cosa -> append("\n${i + 1}. ${cosa.texto.conPunto()} ${cosa.donde}") }
        val resto = cosas.size - mostradas.size
        if (resto > 0) append("\n\nY ${if (resto == 1) "1 cosa más" else "$resto cosas más"} en «Para revisar», en Hoy.")
    }
}

/** El texto con su punto final (o su signo de pregunta), sin duplicar. */
private fun String.conPunto(): String = trimEnd().let { if (it.endsWith(".") || it.endsWith("?") || it.endsWith("!")) it else "$it." }
