package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.shared.model.resumirDeudas
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.faltaPorPagar

/**
 * # Qué preguntarle a Movi, sacado de SUS datos y sin gastar un peso en el modelo
 *
 * El problema que vino a resolver esto se vio en producción: el chat abría con «Pregúntame lo que
 * quieras» y el dueño escribió «Hola, me puedes ayudar?». No es que no tuviera nada que preguntar
 * —tenía un período en rojo, un presupuesto pasado y doce créditos con tasas distintas—; es que un
 * campo en blanco no le dice a nadie qué sabe hacer el asistente. Tres preguntas que ya traen sus
 * números le dicen las dos cosas a la vez: qué puede contestar Movi, y que Movi ya miró su plata.
 *
 * ### Por qué son reglas y no una llamada al modelo
 *
 * Porque el dueño pidió que Movi AI fuera **«lo más económico posible»**, y estas preguntas se
 * pintan cada vez que se abre el chat (y, con la entrega B, cada vez que se abre el Inicio). Pedirle
 * al modelo que las invente sería pagar una llamada por pantalla abierta para decir algo que los
 * datos ya dicen solos: si salió más de lo que entró, la pregunta es esa; si hay deudas con tasas
 * distintas, la pregunta es cuál abonar. Una regla se equivoca barato y siempre de la misma forma,
 * y se puede probar.
 *
 * ### Qué modelo termina contestando cada una
 *
 * No se decide acá —lo decide `laPreguntaPideCriterio` en el server, mirando el texto—, pero las
 * preguntas están redactadas a propósito para caer del lado correcto: las de **criterio** («¿qué
 * deuda me **conviene**…?», «¿**qué hago** para…?», «¿**me alcanza**…?») llevan una de las señales
 * que escalan al modelo de consejos; las de **dato** («¿por qué salió más…?», «¿cómo voy?») no la
 * llevan y las contesta el de todos los días. Si se cambia una redacción, mirar esa lista antes.
 *
 * ### La firma es estable a propósito
 *
 * La entrega B la usa en el Inicio («Pregúntale a Movi») con el mismo [DashboardData] que ya tiene
 * cargado. El chat la usa con lo que dejó el Inicio en su caché. Por eso la entrada es el
 * [DashboardData] entero y no una lista de campos: si mañana una regla necesita otro dato del
 * Inicio, la firma no cambia.
 */

/** Cuántas preguntas se sugieren. Siempre estas, ni una menos: ver [PREGUNTAS_DE_RESPALDO]. */
const val CUANTAS_PREGUNTAS_SUGERIDAS: Int = 3

/**
 * **Las tres preguntas que se le sugieren al dueño**, ordenadas de la más relevante a la menos,
 * armadas con reglas sobre lo que el Inicio ya tiene cargado. Siempre devuelve exactamente
 * [CUANTAS_PREGUNTAS_SUGERIDAS], sin repetidas: lo que las reglas no llenan lo completan
 * [PREGUNTAS_DE_RESPALDO].
 *
 * Una lectura que no llegó (un campo en `null` del [DashboardData]) apaga las reglas que dependen
 * de ella, y nada más: con un Inicio vacío salen las tres de respaldo. Nunca se arma una pregunta
 * sobre una cifra que no se sabe — «¿por qué salieron $0 más…?» sería inventar un problema.
 */
fun preguntasSugeridas(data: DashboardData): List<String> = preguntasSugeridas(senalesDe(data))

/**
 * Lo que las reglas miran, ya destilado del [DashboardData]. Existe aparte para que cada regla se
 * pueda probar con dos números y no con un Inicio entero armado a mano.
 *
 * Todos los montos en pesos. `null` = ese dato no llegó y su regla no opina.
 */
data class SenalesParaPreguntar(
    val ingresosDelPeriodo: Long? = null,
    val gastosDelPeriodo: Long? = null,
    /** El presupuesto que MÁS se pasó en el período, con cuánto se pasó. `null` si ninguno. */
    val presupuestoMasPasado: PresupuestoPasado? = null,
    /** Las deudas que tienen algo que decir: saldo vivo y condiciones cargadas. */
    val deudas: List<DeudaParaPreguntar> = emptyList(),
    /**
     * Intereses de un mes de los créditos cuya cuota sale de su bolsillo (ver `resumirDeudas`). Los
     * que paga la nómina o un tercero no cuentan acá: «¿cómo bajo lo que pago de intereses?» es una
     * pregunta sobre SU flujo, y las dos hipotecas que gira Skandia no salen de ahí.
     */
    val interesesPropiosAlMes: Long = 0L,
    /** Lo que falta por pagar del checklist del período (ver `faltaPorPagar`). */
    val faltaPorPagar: Long? = null,
    /** «Tu plata»: lo que puede usar hoy. */
    val tuPlata: Long? = null,
    /** Lo que valen sus bienes (la casa, el carro). */
    val bienes: Long = 0L,
    /** Lo que debe, en total. */
    val deudasTotales: Long = 0L,
)

data class PresupuestoPasado(val categoria: String, val excedente: Long)

data class DeudaParaPreguntar(
    val nombre: String,
    /** % EA; 0 para un crédito que declaró no cobrar intereses. */
    val tasaEa: Double,
    /** La cuota ya no le baja nada a la deuda: la deuda crece o solo se pagan intereses. */
    val noBaja: Boolean,
    /** Lo que se debe hoy. Desempata cuál nombrar cuando hay varias que no bajan. */
    val saldo: Long = 0L,
)

/** Una pregunta candidata con su peso: más alto = más urgente para el dueño hoy. */
private data class Candidata(val texto: String, val peso: Int)

/**
 * **Cuándo los intereses ya son «altos»**: medio millón al mes, o la décima parte de lo que entró
 * en el período, lo que llegue primero. Medio millón es una cifra que se nota en cualquier
 * presupuesto de hogar; la décima parte de los ingresos cubre a quien gana menos, para quien
 * $300.000 de intereses ya son mucha plata. Por debajo de las dos, sugerir «¿cómo lo bajo?» sería
 * inventarle un problema.
 */
private const val INTERESES_QUE_YA_PESAN: Long = 500_000L
private const val PARTE_DE_LOS_INGRESOS_EN_INTERESES: Long = 10L

/**
 * La misma regla que [preguntasSugeridas], sobre las señales ya destiladas. Es la que prueban las
 * pruebas de cada regla; la de [DashboardData] solo destila y llama a esta.
 */
fun preguntasSugeridas(senales: SenalesParaPreguntar): List<String> {
    val candidatas = buildList {
        // Una deuda que no baja aunque se pague la cuota es lo más urgente que Movi puede ver: es
        // plata que se va todos los meses sin comprar nada. La pantalla de Créditos ya lo dice; la
        // pregunta lleva al por qué y al qué hacer.
        senales.deudas.filter { it.noBaja }.maxByOrNull { it.saldo }?.let {
            add(Candidata("¿Por qué ${it.nombre} no baja aunque pago la cuota?", 100))
        }
        // Lo que falta por pagar ya no cabe en lo que tiene: la pregunta de la semana.
        val falta = senales.faltaPorPagar
        val plata = senales.tuPlata
        if (falta != null && falta > 0L && plata != null) {
            val peso = if (falta > plata) 95 else 40
            add(Candidata("¿Me alcanza para los ${formatMoneyCompact(falta)} que me faltan por pagar este período?", peso))
        }
        senales.presupuestoMasPasado?.let {
            add(Candidata("¿Qué hago para no pasarme en ${it.categoria} el próximo período?", 90))
        }
        val entro = senales.ingresosDelPeriodo
        val salio = senales.gastosDelPeriodo
        if (entro != null && salio != null && salio > entro) {
            add(Candidata("¿Por qué este período salieron ${formatMoneyCompact(salio - entro)} más de los que entraron?", 85))
        }
        val intereses = senales.interesesPropiosAlMes
        val umbral = entro?.takeIf { it > 0L }
            ?.let { minOf(INTERESES_QUE_YA_PESAN, it / PARTE_DE_LOS_INGRESOS_EN_INTERESES) }
            ?: INTERESES_QUE_YA_PESAN
        if (intereses > 0L && intereses >= umbral) {
            add(Candidata("Pago ${formatMoneyCompact(intereses)} de intereses al mes, ¿qué hago para bajarlo?", 70))
        }
        // Con una sola tasa no hay nada que elegir; con dos distintas, cuál abonar primero es LA
        // pregunta de quien tiene varias deudas.
        if (senales.deudas.map { it.tasaEa }.distinct().size >= 2) {
            add(Candidata("¿Qué deuda me conviene abonar primero?", 60))
        }
        if (senales.bienes > 0L && senales.deudasTotales > 0L) {
            add(Candidata("¿Cómo está mi patrimonio si cuento mis bienes y mis deudas?", 30))
        }
    }
    return (candidatas.sortedByDescending { it.peso }.map { it.texto } + PREGUNTAS_DE_RESPALDO)
        .distinct()
        .take(CUANTAS_PREGUNTAS_SUGERIDAS)
}

/**
 * Las que valen para cualquiera, en orden. Son tres —tantas como [CUANTAS_PREGUNTAS_SUGERIDAS]—
 * para que un dueño recién llegado, sin un solo dato, igual vea tres cosas que puede preguntar.
 */
val PREGUNTAS_DE_RESPALDO: List<String> = listOf(
    "¿Cómo voy este período?",
    "¿En qué se me está yendo más la plata?",
    "¿Qué me recomiendas revisar primero?",
)

/** Destila lo que las reglas miran. Ver [SenalesParaPreguntar]. */
internal fun senalesDe(data: DashboardData): SenalesParaPreguntar {
    val gastado = data.spentByCategory
    val pasado = if (data.budgets == null || gastado == null) {
        null
    } else {
        data.budgets
            .map { PresupuestoPasado(it.category, (gastado[it.category] ?: 0L) - it.monthlyLimit) to it }
            .filter { (_, b) -> estadoDePresupuesto(gastado[b.category] ?: 0L, b.monthlyLimit).estaSuperado }
            .maxByOrNull { (p, _) -> p.excedente }
            ?.first
    }
    val conPlan = data.credits.orEmpty().mapNotNull { c -> planDelCredito(c)?.let { c to it } }
        .filter { (_, plan) -> plan.comoVa != ComoVaLaDeuda.SIN_DEUDA && plan.comoVa != ComoVaLaDeuda.SIN_TASA }
    val patrimonio = data.accounts?.let(::patrimonioDe)
    return SenalesParaPreguntar(
        ingresosDelPeriodo = data.summary?.ingresos,
        gastosDelPeriodo = data.summary?.egresos,
        presupuestoMasPasado = pasado,
        deudas = conPlan.map { (c, plan) ->
            DeudaParaPreguntar(
                nombre = c.account.name,
                tasaEa = if (plan.sinIntereses) 0.0 else c.terms?.rateEa ?: 0.0,
                noBaja = plan.comoVa == ComoVaLaDeuda.LA_DEUDA_CRECE || plan.comoVa == ComoVaLaDeuda.SOLO_INTERESES,
                saldo = plan.saldo,
            )
        },
        interesesPropiosAlMes = resumirDeudas(conPlan.map { it.second }).interesMensualPropio,
        // Sin período, vencimientos u ocurrencias el checklist no se sabe: todo se vería pendiente
        // y la pregunta afirmaría una plata que quizá ya se pagó.
        faltaPorPagar = if (data.periodoActual == null || data.upcoming == null || data.ocurrencias == null) {
            null
        } else {
            faltaPorPagar(checklistDelPeriodoDe(data))
        },
        tuPlata = patrimonio?.tuPlata,
        bienes = patrimonio?.bienes ?: 0L,
        deudasTotales = patrimonio?.deudas ?: 0L,
    )
}
