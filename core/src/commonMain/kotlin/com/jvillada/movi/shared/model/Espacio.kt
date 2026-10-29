package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * # Espacios: lo grupal con casa propia (rediseño R3, 26-oct-2026)
 *
 * Un **espacio** es un grupo de gasto compartido —«Casa con Caro», 50/50— con un saldo de quién le
 * debe a quién. Movi sigue siendo mono-usuario: los otros miembros son **terceros** del dueño
 * ([DestinoConocido]), no usuarios. Nada de esto toca un saldo de cuenta.
 *
 * ## La regla de plata (decisión del dueño, 26-oct-2026)
 *
 * **Un gasto compartido cuenta SOLO tu parte** en «Salió», «Te quedan», las categorías, el
 * disponible y todo total de gasto. La parte del otro es «te debe». Cuando el otro te paga, eso
 * **no es ingreso**: solo salda la deuda.
 *
 * - **Un movimiento tuyo repartido** (pagaste el mercado de $240.000 con tu tarjeta): la plata salió
 *   entera de tu cuenta —«Tu plata» y los saldos no cambian—, pero en los totales de gasto cuenta
 *   $120.000. La regla vive en UNA función, [montoContado]; cada total de gasto la llama.
 * - **Un gasto que pagó otro** («Caro pagó el mercado, $240.000»): no es un movimiento de tus
 *   cuentas, así que no toca ningún saldo. Tu parte ($120.000) **sí es gasto tuyo, y cuenta el día
 *   del gasto**, no el día que le pagas a Caro. Es lo único coherente con la regla: si tu parte se
 *   contara al saldar, el pago a Caro tendría que ser gasto, pero ese pago salda una deuda neta que
 *   mezcla gastos de los dos lados (y a veces va al revés), así que no puede ser gasto de nada. Tu
 *   consumo pasó cuando se compró el mercado; saldar solo mueve plata entre ustedes. Ver
 *   [tuParteComoEventos].
 * - **Saldar** ([LiquidacionDelEspacio]): ni la transferencia que Caro te manda ni la que tú le
 *   mandas cuenta como ingreso o gasto. Si el dueño la vincula a la liquidación, el movimiento queda
 *   fuera de «Entró»/«Salió» (ver [conLoCompartido]); sigue moviendo el saldo de su cuenta.
 * - **Anulados**: un gasto cuyo movimiento se anuló no cuenta en el saldo del espacio, y una
 *   liquidación cuyo movimiento vinculado se anuló tampoco.
 */

/** El id con que el dueño es miembro de un espacio. Los demás son el id de su tercero. */
const val MIEMBRO_YO: String = "yo"

/** Largo máximo del nombre de un espacio («Casa con Caro»). */
const val MAX_NOMBRE_DE_ESPACIO: Int = 40

/** El espacio con que arranca Movi (decisión del dueño): la casa con Caro, mitad y mitad. */
const val NOMBRE_DEL_PRIMER_ESPACIO: String = "Casa con Caro"

/** Cómo se reparte un gasto. Hoy solo partes iguales; el modelo ya admite porcentajes. */
@Serializable
enum class RepartoDelEspacio { PARTES_IGUALES, PORCENTAJES }

/**
 * Un miembro de un espacio.
 *
 * @param id [MIEMBRO_YO] para el dueño; el `DestinoConocido.id` para los demás.
 * @param nombre lo resuelve el server en cada lectura (el nombre del tercero, o el del dueño).
 * @param porcentaje solo con [RepartoDelEspacio.PORCENTAJES]: su parte, de 0 a 100.
 */
@Serializable
data class MiembroDelEspacio(
    val id: String,
    val nombre: String = "",
    val porcentaje: Int? = null,
)

/**
 * Un espacio de gasto compartido. POST es un upsert por [id] del cliente. El dueño
 * ([MIEMBRO_YO]) siempre es miembro, aunque el cliente no lo mande.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Espacio(
    val id: String = "",
    val nombre: String,
    val miembros: List<MiembroDelEspacio>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val reparto: RepartoDelEspacio = RepartoDelEspacio.PARTES_IGUALES,
    val creadoEn: Long = 0L,
)

/**
 * Un gasto del espacio.
 *
 * - **De un movimiento propio** ([eventoId] no nulo, [pagadoPor] = [MIEMBRO_YO]): el monto, la
 *   categoría, la descripción y la fecha los pone el server desde el movimiento en cada lectura.
 * - **Pagado por otro miembro** ([eventoId] nulo): un registro del espacio, sin cuenta.
 *
 * @param partes lo que le toca a cada miembro, por id; suma [monto]. Lo calcula el server con
 *   [partesDelGasto] al guardarlo (y lo recalcula si el monto del movimiento cambió después).
 */
@Serializable
data class GastoDelEspacio(
    val id: String = "",
    val espacioId: String = "",
    val pagadoPor: String = MIEMBRO_YO,
    val monto: Long = 0L,
    val categoria: String = "",
    val descripcion: String = "",
    val fecha: Long = 0L,
    val eventoId: String? = null,
    val partes: Map<String, Long> = emptyMap(),
)

/** Lo que le toca al dueño de [g]. */
fun tuParteDe(g: GastoDelEspacio): Long = g.partes[MIEMBRO_YO] ?: 0L

/** Lo que les toca a los demás miembros de [g]: con 50/50 y pagado por el dueño, la mitad. */
fun parteAjenaDe(g: GastoDelEspacio): Long = g.monto - tuParteDe(g)

/**
 * «Marcar como saldado»: [de] le pagó [monto] a [a]. Nunca se crea sola. [eventoId] es el
 * movimiento real (la transferencia de Caro, o la tuya a Caro) si el dueño lo vinculó: ese
 * movimiento deja de contar como ingreso o gasto (ver [conLoCompartido]).
 */
@Serializable
data class LiquidacionDelEspacio(
    val id: String = "",
    val espacioId: String = "",
    val de: String,
    val a: String,
    val monto: Long,
    val fecha: Long = 0L,
    val eventoId: String? = null,
    /**
     * Vuelta 66 · el concepto del movimiento vinculado («Transferencia de Caro»), para el historial
     * «Pagos entre ustedes». Derivado: lo pone el server al leer; lo que mande un cliente se ignora.
     */
    val movimiento: String? = null,
)

/**
 * **¿[l] espera la transferencia?** (vuelta 66) Un «Marcar como saldado» sin movimiento vinculado en
 * el que el dueño paga o cobra, de los últimos [DIAS_PARA_PROPONER_SALDAR] días. Mientras espera,
 * Movi propone vincular la plata que llegue ([transferenciasQueEsperan]): si no, la transferencia de
 * Caro que llega después de saldar quedaba contada como ingreso para siempre. Pasado ese plazo se
 * entiende que fue en efectivo (o por fuera de tus cuentas) y deja de esperar.
 */
fun esperaLaTransferencia(l: LiquidacionDelEspacio, ahora: Long): Boolean =
    l.eventoId == null && (l.a == MIEMBRO_YO || l.de == MIEMBRO_YO) && l.de != l.a && l.monto > 0L &&
        l.fecha >= ahora - DIAS_PARA_PROPONER_SALDAR * UN_DIA_EN_MS

private const val UN_DIA_EN_MS: Long = 24L * 60 * 60 * 1000

/** Cuántos días antes de saldar puede haber llegado la transferencia que se propone vincular. */
const val DIAS_ANTES_DEL_SALDADO: Int = 7

/** ¿[monto] es [esperado] ± 1 %? (al menos un peso de margen). */
fun montoParecido(monto: Long, esperado: Long): Boolean =
    esperado > 0L && kotlin.math.abs(monto - esperado) <= maxOf(1L, esperado / 100)

/** La huella con que «No» descarta una propuesta de saldar (en `anomalias_descartadas`). */
fun huellaDeLaPropuestaDeSaldar(eventoId: String): String = "saldar:$eventoId"

/** Body de `POST /api/espacios/{id}/compartir`: repartir un movimiento propio en el espacio. */
@Serializable
data class CompartirMovimientoRequest(val eventoId: String, val gastoId: String = "")

/**
 * El saldo de un miembro.
 *
 * @param puso lo que pagó de los gastos del espacio.
 * @param leTocaba la suma de sus partes.
 * @param pagado lo que pagó en liquidaciones menos lo que recibió.
 * @param neto `puso − leTocaba + pagado`: positivo = le deben; negativo = debe.
 */
@Serializable
data class SaldoDeMiembro(
    val miembroId: String,
    val nombre: String,
    val puso: Long,
    val leTocaba: Long,
    val pagado: Long,
    val neto: Long,
)

/** Un pago que deja al espacio a mano: [de] le paga [monto] a [a]. */
@Serializable
data class Reembolso(val de: String, val a: String, val monto: Long)

/**
 * El saldo de un espacio: por miembro y la propuesta de reembolsos. [total] es lo gastado (la suma
 * de los gastos contados).
 */
@Serializable
data class SaldoDelEspacio(
    val miembros: List<SaldoDeMiembro>,
    val reembolsos: List<Reembolso>,
    val total: Long,
)

/** El saldo de [miembroId] en [saldo], o 0 si no está. */
fun SaldoDelEspacio.netoDe(miembroId: String): Long = miembros.firstOrNull { it.miembroId == miembroId }?.neto ?: 0L

/**
 * **Cómo se reparte [monto]** entre los miembros de [espacio]. Con partes iguales, cada uno su
 * división entera; con porcentajes, su porcentaje (redondeado hacia abajo). Los pesos que sobran
 * del redondeo los pone **quien pagó** ([pagadoPor]): el que tiene la plata en la mano absorbe el
 * peso, y así la parte ajena nunca pasa de la mitad exacta en un 50/50.
 */
fun partesDelGasto(monto: Long, espacio: Espacio, pagadoPor: String): Map<String, Long> {
    val ids = idsDeMiembros(espacio)
    if (ids.isEmpty() || monto <= 0L) return emptyMap()
    val partes = LinkedHashMap<String, Long>()
    when (espacio.reparto) {
        RepartoDelEspacio.PARTES_IGUALES -> ids.forEach { partes[it] = monto / ids.size }
        RepartoDelEspacio.PORCENTAJES -> {
            val porId = espacio.miembros.associate { it.id to (it.porcentaje ?: 0) }
            ids.forEach { partes[it] = monto * (porId[it] ?: 0) / 100 }
        }
    }
    val sobra = monto - partes.values.sum()
    val quienPone = if (pagadoPor in partes) pagadoPor else ids.first()
    partes[quienPone] = (partes[quienPone] ?: 0L) + sobra
    return partes
}

/** Los ids de los miembros, con el dueño primero y sin repetir. */
fun idsDeMiembros(espacio: Espacio): List<String> =
    (listOf(MIEMBRO_YO) + espacio.miembros.map { it.id }).distinct()

/**
 * ¿Los porcentajes de [espacio] suman 100 y nadie queda fuera? Con partes iguales siempre `true`.
 */
fun repartoValido(espacio: Espacio): Boolean = when (espacio.reparto) {
    RepartoDelEspacio.PARTES_IGUALES -> true
    RepartoDelEspacio.PORCENTAJES -> {
        val porId = espacio.miembros.associate { it.id to it.porcentaje }
        val ids = idsDeMiembros(espacio)
        ids.all { (porId[it] ?: -1) in 0..100 } && ids.sumOf { porId[it] ?: 0 } == 100
    }
}

/**
 * **El saldo del espacio**: la función única que dice quién le debe a quién.
 *
 * Por miembro: lo que puso (los gastos que pagó), lo que le tocaba (sus partes), lo que pagó o
 * recibió en liquidaciones, y el neto. La suma de los netos es cero. Después propone los
 * reembolsos que dejan todo a mano con pocos pagos: el que más debe le paga al que más le deben,
 * hasta agotar a uno de los dos, y así (con dos miembros es un solo pago, y nunca pasan de
 * miembros − 1).
 *
 * @param anulados ids de movimientos anulados: un gasto propio o una liquidación vinculada a uno de
 *   ellos no cuenta.
 */
fun saldoDelEspacio(
    espacio: Espacio,
    gastos: List<GastoDelEspacio>,
    liquidaciones: List<LiquidacionDelEspacio>,
    anulados: Set<String> = emptySet(),
): SaldoDelEspacio {
    val ids = LinkedHashSet(idsDeMiembros(espacio))
    val puso = HashMap<String, Long>()
    val leTocaba = HashMap<String, Long>()
    val pagado = HashMap<String, Long>()
    val vivos = gastos.filter { it.espacioId.isEmpty() || it.espacioId == espacio.id }
        .filterNot { it.eventoId != null && it.eventoId in anulados }
    vivos.forEach { g ->
        ids += g.pagadoPor
        puso[g.pagadoPor] = (puso[g.pagadoPor] ?: 0L) + g.monto
        g.partes.forEach { (m, parte) ->
            ids += m
            leTocaba[m] = (leTocaba[m] ?: 0L) + parte
        }
    }
    liquidaciones.filter { it.espacioId.isEmpty() || it.espacioId == espacio.id }
        .filterNot { it.eventoId != null && it.eventoId in anulados }
        .filter { it.monto > 0L && it.de != it.a }
        .forEach { l ->
            ids += l.de
            ids += l.a
            pagado[l.de] = (pagado[l.de] ?: 0L) + l.monto
            pagado[l.a] = (pagado[l.a] ?: 0L) - l.monto
        }
    val nombres = espacio.miembros.associate { it.id to it.nombre }
    val miembros = ids.map { m ->
        val p = puso[m] ?: 0L
        val t = leTocaba[m] ?: 0L
        val l = pagado[m] ?: 0L
        SaldoDeMiembro(miembroId = m, nombre = nombres[m].orEmpty(), puso = p, leTocaba = t, pagado = l, neto = p - t + l)
    }
    return SaldoDelEspacio(miembros = miembros, reembolsos = reembolsosPara(miembros), total = vivos.sumOf { it.monto })
}

/** Los pagos que dejan a todos en cero: el que más debe le paga al que más le deben. */
fun reembolsosPara(miembros: List<SaldoDeMiembro>): List<Reembolso> {
    val deben = miembros.filter { it.neto < 0 }.map { it.miembroId to -it.neto }.sortedByDescending { it.second }.toMutableList()
    val cobran = miembros.filter { it.neto > 0 }.map { it.miembroId to it.neto }.sortedByDescending { it.second }.toMutableList()
    val pagos = mutableListOf<Reembolso>()
    while (deben.isNotEmpty() && cobran.isNotEmpty()) {
        val (deudor, debe) = deben.first()
        val (acreedor, cobra) = cobran.first()
        val monto = minOf(debe, cobra)
        pagos += Reembolso(de = deudor, a = acreedor, monto = monto)
        if (debe == monto) deben.removeAt(0) else deben[0] = deudor to debe - monto
        if (cobra == monto) cobran.removeAt(0) else cobran[0] = acreedor to cobra - monto
    }
    return pagos
}

// ─────────────────────────────────────────────────────────────────────────────
// SOLO TU PARTE: la regla de los totales
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lo que los espacios dicen de los movimientos del dueño, por id de movimiento. Lo arma
 * [loCompartidoDe] y lo aplica [conLoCompartido] al leer los movimientos (en el server y en el
 * espejo del teléfono), igual que `countsAsCashFlow`.
 */
data class LoCompartido(
    /** Movimiento repartido → la parte de los demás. */
    val parteAjenaPorEvento: Map<String, Long> = emptyMap(),
    /** Movimiento repartido → su espacio. */
    val espacioPorEvento: Map<String, String> = emptyMap(),
    /** Movimiento que salda un espacio → ese espacio. */
    val saldaPorEvento: Map<String, String> = emptyMap(),
) {
    fun estaVacio(): Boolean = parteAjenaPorEvento.isEmpty() && espacioPorEvento.isEmpty() && saldaPorEvento.isEmpty()

    companion object {
        val NADA: LoCompartido = LoCompartido()
    }
}

/** El índice de [gastos] y [liquidaciones] para [conLoCompartido]. */
fun loCompartidoDe(gastos: List<GastoDelEspacio>, liquidaciones: List<LiquidacionDelEspacio>): LoCompartido {
    val propios = gastos.filter { it.eventoId != null }
    return LoCompartido(
        parteAjenaPorEvento = propios.associate { it.eventoId!! to parteAjenaDe(it) },
        espacioPorEvento = propios.associate { it.eventoId!! to it.espacioId },
        saldaPorEvento = liquidaciones.filter { it.eventoId != null }.associate { it.eventoId!! to it.espacioId },
    )
}

/**
 * [this] con lo que dicen los espacios: su parte ajena y su espacio si se repartió, y fuera de
 * «Entró»/«Salió» (`countsAsCashFlow = false`) si salda un espacio. Derivado como
 * `countsAsCashFlow`: nunca se guarda en la fila del movimiento.
 */
fun FinancialEvent.conLoCompartido(c: LoCompartido): FinancialEvent {
    if (c.estaVacio()) return this
    val ajena = c.parteAjenaPorEvento[id]
    val salda = c.saldaPorEvento[id]
    if (ajena == null && salda == null) return this
    return copy(
        parteAjena = if (type == TransactionType.EXPENSE) (ajena ?: 0L).coerceIn(0L, amount) else 0L,
        espacioId = c.espacioPorEvento[id],
        saldaEspacioId = salda,
        countsAsCashFlow = countsAsCashFlow && salda == null,
    )
}

/**
 * **Lo que [evento] suma en los totales de gasto e ingreso: solo tu parte.** La regla única de la
 * decisión del 26-oct-2026. Un gasto repartido suma su monto menos la parte de los demás; todo lo
 * demás, su monto. Los saldos de las cuentas NO pasan por acá (la plata salió entera).
 *
 * Todo total de gasto («Salió», categorías, el gasto variable de «Te quedan», En qué se va,
 * pequeños que suman, lo que se sale de lo normal, presupuestos, el total del día) llama a esta
 * función en vez de leer `amount`.
 */
fun montoContado(evento: FinancialEvent): Long =
    if (evento.type == TransactionType.EXPENSE) (evento.amount - evento.parteAjena).coerceIn(0L, evento.amount)
    else evento.amount

/** El prefijo del id de un movimiento sintético de [tuParteComoEventos]. */
const val PREFIJO_TU_PARTE: String = "espacio:"

/** ¿[evento] es tu parte de un gasto que pagó otro (no un movimiento de tus cuentas)? */
fun esTuParteDeOtro(evento: FinancialEvent): Boolean = evento.id.startsWith(PREFIJO_TU_PARTE)

/**
 * **Tu parte de los gastos que pagaron otros**, como movimientos de gasto para los totales. Solo
 * los totales los reciben —nunca la lista de movimientos ni un saldo—: su cuenta
 * (`espacio:<id>`) no es ninguna de las tuyas. Cuentan **el día del gasto** (ver el KDoc del
 * archivo). Los gastos propios (con [GastoDelEspacio.eventoId]) no van: ya están en tus
 * movimientos, con [montoContado].
 */
fun tuParteComoEventos(gastos: List<GastoDelEspacio>): List<FinancialEvent> =
    gastos.filter { it.eventoId == null && it.pagadoPor != MIEMBRO_YO && tuParteDe(it) > 0L }
        .map { g ->
            FinancialEvent(
                id = PREFIJO_TU_PARTE + g.id,
                accountId = PREFIJO_TU_PARTE + g.espacioId,
                type = TransactionType.EXPENSE,
                amount = tuParteDe(g),
                currency = "COP",
                category = g.categoria,
                description = g.descripcion,
                timestamp = g.fecha,
                reconciliationStatus = ReconciliationStatus.RECONCILED,
                countsAsCashFlow = true,
                espacioId = g.espacioId,
                pagadoPor = g.pagadoPor,
            )
        }

/**
 * **Los días de Actividad con tu parte de lo que pagaron otros como filas** (vuelta 66). «Salió»
 * contaba tu parte del mercado que pagó Caro ([tuParteComoEventos]) pero ninguna fila la mostraba: los
 * días sumaban $120.000 menos que el total, y un día con solo eso no aparecía. Ahora cada parte va en
 * su día (la fecha del gasto, en la zona de la app), en su lugar por hora, y un día que no existía se
 * crea en orden. Toda cifra de Actividad sale de estos días. Una parte que ya está (mismo id) no se
 * repite. El `total` del día no se toca: lo recalcula quien filtra.
 */
fun conTuParteDeOtros(dias: List<EventDay>, tuParteDeOtros: List<FinancialEvent>): List<EventDay> {
    val yaEstan = dias.flatMap { d -> d.items.map { it.id } }.toSet()
    val nuevas = tuParteDeOtros.filter { esTuParteDeOtro(it) && it.id !in yaEstan }
    if (nuevas.isEmpty()) return dias
    val porDia = nuevas.groupBy { epochMillisToAppDate(it.timestamp).toString() }
    val existentes = dias.map { d ->
        val suyas = porDia[d.date] ?: return@map d
        d.copy(items = (d.items + suyas).masRecientePrimero())
    }
    val fechas = dias.map { it.date }.toSet()
    val diasNuevos = porDia.filterKeys { it !in fechas }.map { (fecha, items) ->
        EventDay(date = fecha, total = 0L, items = items.masRecientePrimero())
    }
    // Las fechas ISO se ordenan como texto: el día más reciente primero, como `/by-day`.
    return (existentes + diasNuevos).sortedByDescending { it.date }
}

/**
 * Una propuesta: «¿Esta transferencia de Caro es para saldar la casa?». Movi la propone; el dueño
 * confirma con «Marcar como saldado» vinculándola. Nunca se salda sola.
 */
@Serializable
data class PropuestaDeSaldar(
    val eventoId: String,
    val de: String,
    val a: String,
    val monto: Long,
    val fecha: Long,
    val descripcion: String,
    /**
     * Vuelta 66 · la liquidación que ya se marcó y espera esta transferencia ([esperaLaTransferencia]);
     * `null` si la propuesta salda una deuda que sigue abierta. Con ella, «Sí, saldar» vincula ese
     * saldado (el mismo id) en vez de crear otro.
     */
    val liquidacionId: String? = null,
    /** El grupo, para mostrarla fuera de él (Por revisar, la campana). */
    val espacioId: String = "",
    val nombreDelEspacio: String = "",
    /** El nombre de la otra persona («Caro»), para decirlo fuera del grupo. Lo pone el server. */
    val quien: String = "",
)

/** Cuántos días atrás se mira para proponer una transferencia que salda. */
const val DIAS_PARA_PROPONER_SALDAR: Int = 45

/**
 * **Las transferencias que podrían saldar el espacio**: un ingreso que viene de un miembro que le
 * debe al dueño, o un gasto que va hacia un miembro al que el dueño le debe, por no más de lo que
 * se debe, en los últimos [DIAS_PARA_PROPONER_SALDAR] días, en pesos, y que no salde ya otro
 * espacio ni esté repartido. La más reciente primero.
 *
 * @param destinos los terceros del dueño, para reconocer que el movimiento es de ese miembro.
 */
fun transferenciasParaSaldar(
    saldo: SaldoDelEspacio,
    eventos: List<FinancialEvent>,
    destinos: List<DestinoConocido>,
    ahora: Long,
    /** Vuelta 66: los saldados del grupo; los que esperan su transferencia también proponen. */
    liquidaciones: List<LiquidacionDelEspacio> = emptyList(),
): List<PropuestaDeSaldar> {
    val desde = ahora - DIAS_PARA_PROPONER_SALDAR * 24L * 60 * 60 * 1000
    val porId = destinos.associateBy { it.id }
    val propuestas = mutableListOf<PropuestaDeSaldar>()
    saldo.reembolsos.forEach { r ->
        val (otro, entra) = when (MIEMBRO_YO) {
            r.a -> r.de to true
            r.de -> r.a to false
            else -> return@forEach
        }
        val destino = porId[otro] ?: return@forEach
        eventos.asSequence()
            .filter { it.timestamp in desde..ahora && it.currency == "COP" }
            .filter { it.saldaEspacioId == null && it.espacioId == null && !esTuParteDeOtro(it) }
            .filter { it.amount in 1..r.monto }
            .filter {
                if (entra) it.type == TransactionType.INCOME && vieneDelDestino(it, destino)
                else it.type == TransactionType.EXPENSE && vaHaciaElDestino(it, destino)
            }
            .forEach { e -> propuestas += PropuestaDeSaldar(e.id, r.de, r.a, e.amount, e.timestamp, e.description) }
    }
    val yaPropuestos = propuestas.map { it.eventoId }.toMutableSet()
    return propuestas.sortedByDescending { it.fecha } +
        transferenciasQueEsperan(liquidaciones, eventos, destinos, ahora, yaPropuestos)
}

/**
 * **La transferencia que espera cada saldado** (vuelta 66): por cada liquidación que
 * [esperaLaTransferencia], **la primera** plata de esa persona (un ingreso si te pagó, un gasto hacia
 * ella si le pagaste) por el monto del saldado ± 1 % ([montoParecido]), desde
 * [DIAS_ANTES_DEL_SALDADO] días antes de marcarlo, en pesos, y que no salde ni esté repartida en
 * ningún grupo. Un movimiento se propone para un solo saldado. Sin reglas nuevas: «Sí, saldar» es la
 * vinculación de siempre ([LiquidacionDelEspacio.eventoId]), con una ventana más.
 */
fun transferenciasQueEsperan(
    liquidaciones: List<LiquidacionDelEspacio>,
    eventos: List<FinancialEvent>,
    destinos: List<DestinoConocido>,
    ahora: Long,
    yaPropuestos: MutableSet<String> = mutableSetOf(),
): List<PropuestaDeSaldar> {
    val porId = destinos.associateBy { it.id }
    return liquidaciones.filter { esperaLaTransferencia(it, ahora) }.sortedBy { it.fecha }.mapNotNull { l ->
        val entra = l.a == MIEMBRO_YO
        val destino = porId[if (entra) l.de else l.a] ?: return@mapNotNull null
        val desde = l.fecha - DIAS_ANTES_DEL_SALDADO * UN_DIA_EN_MS
        val e = eventos.asSequence()
            .filter { it.timestamp in desde..ahora && it.currency == "COP" && it.id !in yaPropuestos }
            .filter { it.saldaEspacioId == null && it.espacioId == null && !esTuParteDeOtro(it) }
            .filter { montoParecido(it.amount, l.monto) }
            .filter {
                if (entra) it.type == TransactionType.INCOME && vieneDelDestino(it, destino)
                else it.type == TransactionType.EXPENSE && vaHaciaElDestino(it, destino)
            }
            .minByOrNull { it.timestamp } ?: return@mapNotNull null
        yaPropuestos += e.id
        PropuestaDeSaldar(e.id, l.de, l.a, e.amount, e.timestamp, e.description, liquidacionId = l.id, espacioId = l.espacioId)
    }
}

/**
 * Lo que la pantalla de un espacio pide en una lectura.
 *
 * @param saldo el saldo de siempre (todo el tiempo, con las liquidaciones).
 * @param delPeriodo los gastos del período del dueño, sin liquidaciones: «Quién puso qué».
 * @param gastos todos los gastos del espacio, el más reciente primero.
 */
@Serializable
data class DetalleDelEspacio(
    val espacio: Espacio,
    val saldo: SaldoDelEspacio,
    val delPeriodo: SaldoDelEspacio,
    val gastos: List<GastoDelEspacio>,
    val liquidaciones: List<LiquidacionDelEspacio> = emptyList(),
    val propuestas: List<PropuestaDeSaldar> = emptyList(),
)
