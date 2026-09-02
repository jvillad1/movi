package com.jvillada.movi.shared.model

/**
 * # De dónde sale la plata, y a dónde entra
 *
 * **El pedido del dueño, textual:** *«¿Qué criterio estás usando para listar las cuentas al crear
 * un gasto? Digamos del crédito de vehículo y de los hipotecarios no puedo ejecutar gastos, son
 * créditos ya desembolsados; de las tarjetas de crédito o de Nubank sí, o de la de ahorros de
 * Bancolombia.»*
 *
 * La respuesta honesta era: **ninguno**. El selector de cuenta de la hoja de «Agregar» hacía
 * `items(accounts)` sobre la lista entera, así que al elegir de dónde salía un gasto aparecía
 * «Vehículo 4083 · $177.200.000» — y esa cifra no es plata disponible, es lo que DEBE.
 *
 * Este archivo es ese criterio, **en un solo lugar**. Que sea uno solo no es prolijidad: este
 * repo ya se comió dos veces el mismo defecto por copiar una regla en vez de compartirla (el
 * semáforo del presupuesto vivía duplicado en dos pantallas, se arregló una y la otra no, dos
 * veces seguidas). Toda pantalla que ofrezca cuentas llama acá.
 *
 * ## Lo que NO hace: filtrar duro
 *
 * [cuentasPara] no borra nada, **parte** la lista en dos: lo que va a la vista y lo que queda
 * detrás de un «Ver todas» plegado. La app no tiene derecho a decidir por el dueño que una
 * cuenta es imposible; sí tiene el deber de no proponérsela sola. Y esa es la disciplina que
 * cierra el caso de verdad: lo que la app elige por su cuenta sale **siempre** de
 * [CuentasDelPicker.principales].
 */
enum class UsoDeCuenta {
    /**
     * De dónde sale la plata de un **gasto**: efectivo, cuentas bancarias y **tarjetas de
     * crédito**.
     *
     * La tarjeta entra a propósito, y es el corazón del pedido: comprar con la Nu o con la AMEX
     * es un gasto real que además sube la deuda. Lo que queda afuera son los **préstamos ya
     * desembolsados** —del hipotecario no se compra el mercado; lo único que lo mueve es la
     * cuota, que tiene su propia pestaña— y la **inversión**: de la pensión voluntaria no se
     * paga un almuerzo, y sacar plata de ahí es un traspaso a una cuenta bancaria.
     */
    ORIGEN_DE_GASTO,

    /**
     * A dónde entra la plata de un **ingreso**: efectivo, cuentas bancarias e **inversión**.
     *
     * La inversión entra porque un rendimiento es un ingreso legítimo y no viene de ninguna
     * cuenta propia. La **tarjeta** no: plata que «entra» a una tarjeta es un pago del extracto,
     * y eso es la pestaña «Cuota». El **préstamo** tampoco: plata que entra por un crédito es el
     * desembolso, que es un traspaso (el banco le deposita a una cuenta suya).
     */
    DESTINO_DE_INGRESO,

    /**
     * Cualquiera de las dos puntas de un **traspaso**: todo menos la tarjeta de crédito.
     *
     * El préstamo sí es punta —el desembolso que entró a la cuenta, o un abono extraordinario
     * que sale de ella— y la inversión también: mover plata a Skandia es exactamente esto.
     */
    PUNTA_DE_TRASPASO,

    /**
     * Plata propia: dinero o inversión, nunca una deuda. Sirve para dos cosas, y por el mismo
     * motivo:
     *
     * 1. **De dónde sale la plata de una cuota** (la pestaña «Cuota»): una deuda no paga otra.
     * 2. **Lo único que la app puede proponer sola en un traspaso.** Si un crédito pudiera quedar
     *    elegido solo, dos toques distraídos anotarían un desembolso de $257.000.000 que nadie
     *    pidió: un crédito en un traspaso se elige **siempre** con el dedo.
     *
     * Propia no alcanza: tiene que estar **disponible**. Ver [estaCondicionada].
     */
    DINERO_PROPIO,

    /** Lo que se paga con una cuota: préstamos y tarjetas. */
    DEUDA_QUE_SE_PAGA,

    /**
     * **De qué cuenta es esto que llegó del banco**: el destino de un extracto importado.
     *
     * No es ni origen ni destino, y por eso no alcanzaba con reusar los dos primeros: un extracto
     * trae las dos cosas en el mismo archivo —las compras del mes y la nómina que entró—, así que
     * la pregunta acá no es «¿de esta cuenta sale plata?» sino **«¿quién manda extractos?»**. Los
     * bancos y las tarjetas, y también la fiduciaria de la inversión: a Skandia le llega el suyo.
     *
     * Afuera quedan los dos que no. El **efectivo**, porque nadie recibe un PDF de lo que tiene en
     * el bolsillo. Y el **préstamo ya desembolsado**, que no acumula movimientos sueltos sino una
     * cuota con su propia pestaña — y ese es exactamente el caso que abrió esto: la pantalla de
     * revisión resolvía el destino con `accounts.firstOrNull()`, así que un extracto de
     * Bancolombia podía terminar importado entero contra el «Vehículo 4083».
     *
     * [estaCondicionada] no pesa acá, por lo mismo que en el ingreso y en el traspaso: guardar los
     * movimientos de Skandia no le saca un peso a nadie. La condición es sobre el retiro.
     */
    CUENTA_DEL_EXTRACTO,
}

/**
 * **Esta plata es suya, pero no está disponible: solo sirve para una cosa.**
 *
 * Es [Account.condicionadaA], el campo que llegó con la pensión voluntaria de Skandia: son
 * $106.000.000 del dueño —cuentan en su patrimonio— que solo puede retirar **para vivienda** sin
 * perder el beneficio tributario. Cualquier otro retiro le cobra la retención que se ahorró al
 * aportar.
 *
 * Este archivo se presenta como «el criterio, en un solo lugar». Un criterio en un solo lugar que
 * no conoce el campo más nuevo de la misma pregunta no es un criterio: es una copia con suerte.
 * Sin esto, la pestaña «Cuota» ofrecía Skandia como fuente para pagar la tarjeta — justo la plata
 * que la app acababa de declarar no disponible, y encima elegida por ella y no por él.
 *
 * `isNullOrBlank` y no `!= null`, por el mismo motivo por el que el server normaliza al escribir:
 * una cadena vacía es «sin condición», no una condición que se llama «».
 */
private fun estaCondicionada(account: Account): Boolean = !account.condicionadaA.isNullOrBlank()

/**
 * ¿Esta cuenta sirve para [uso]? **La regla, y el único sitio donde está escrita.**
 *
 * Tres de los seis usos recorren [AccountType] renglón por renglón en vez de preguntar por el
 * grupo: así, el día que aparezca un tipo de cuenta más, esto no compila hasta que alguien
 * decida si de ahí sale plata o no. Un `else -> false` habría contestado esa pregunta en
 * silencio, y contestarla mal es justamente cómo un préstamo terminó ofrecido como origen de un
 * gasto.
 *
 * ## Dónde pesa [estaCondicionada], y dónde no
 *
 * Solo en los dos usos donde la plata **se va**: [UsoDeCuenta.ORIGEN_DE_GASTO] y
 * [UsoDeCuenta.DINERO_PROPIO] (la cuota). Ahí, una cuenta condicionada baja al «Ver todas»: sacar
 * de Skandia para pagar la AMEX **es posible**, cuesta la retención, y eso es una decisión del
 * dueño — no algo que la app pueda proponerle sola, ni algo que tenga derecho a prohibirle.
 *
 * En los otros tres no cambia nada, y eso también es la regla y no un olvido: a la pensión
 * voluntaria le **entran** rendimientos ([UsoDeCuenta.DESTINO_DE_INGRESO]) y le entran aportes
 * ([UsoDeCuenta.PUNTA_DE_TRASPASO]). La condición es sobre el retiro, no sobre la cuenta.
 */
fun sirvePara(account: Account, uso: UsoDeCuenta): Boolean = when (uso) {
    UsoDeCuenta.ORIGEN_DE_GASTO -> !estaCondicionada(account) && when (account.type) {
        AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.CREDIT_CARD -> true
        AccountType.LOAN, AccountType.INVESTMENT -> false
    }
    UsoDeCuenta.DESTINO_DE_INGRESO -> when (account.type) {
        AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT -> true
        AccountType.CREDIT_CARD, AccountType.LOAN -> false
    }
    UsoDeCuenta.PUNTA_DE_TRASPASO -> account.type != AccountType.CREDIT_CARD
    UsoDeCuenta.DINERO_PROPIO -> !estaCondicionada(account) && account.type.group != AccountGroup.DEUDA
    UsoDeCuenta.DEUDA_QUE_SE_PAGA -> account.type.group == AccountGroup.DEUDA
    UsoDeCuenta.CUENTA_DEL_EXTRACTO -> when (account.type) {
        AccountType.CHECKING, AccountType.SAVINGS, AccountType.CREDIT_CARD, AccountType.INVESTMENT -> true
        AccountType.CASH, AccountType.LOAN -> false
    }
}

/**
 * La lista de cuentas partida en dos: lo que se ofrece y lo que queda detrás de «Ver todas».
 *
 * `principales` es además —y sobre todo— **lo único de donde la app puede sacar un valor por
 * defecto**. Si el defecto pudiera caer en `otras`, la hoja se abriría con una cuenta elegida
 * que no está a la vista: el peor de los dos mundos, porque se guarda sin que nadie la haya
 * visto.
 */
data class CuentasDelPicker(
    val principales: List<Account>,
    val otras: List<Account>,
) {
    /** Todas, en el orden en que se muestran: primero las que sirven, después el resto. */
    val todas: List<Account> get() = principales + otras

    /** Hay algo que mostrar detrás de «Ver todas». */
    val hayOtras: Boolean get() = otras.isNotEmpty()

    /** No hay ninguna cuenta, ni de un lado ni del otro. */
    val vacio: Boolean get() = principales.isEmpty() && otras.isEmpty()
}

/**
 * Parte [accounts] según [uso], respetando el orden que traen (`GET /api/accounts` y el
 * `selectAll` de SQLDelight ordenan por nombre; partir no reordena nada).
 *
 * [conservar] es **la cuenta que ya está elegida**, y va a `principales` aunque no sirva para
 * este uso. Cubre los dos casos donde esconderla sería peor que mostrarla:
 *
 * - el dueño la eligió a mano desde «Ver todas» — hacerla desaparecer al reabrir el selector
 *   sería quitarle la elección que acaba de hacer;
 * - un movimiento o una regla vieja apunta a una cuenta que hoy quedaría afuera — al editarla,
 *   la selección actual tiene que seguir viéndose marcada, no evaporarse.
 *
 * **Ojo con usar `conservar` para resolver el valor por defecto**: ahí la lista tiene que salir
 * SIN conservar, o la cuenta excluida se valida a sí misma y el defecto vuelve a caer donde no
 * debe.
 */
fun cuentasPara(
    accounts: List<Account>,
    uso: UsoDeCuenta,
    conservar: String? = null,
): CuentasDelPicker {
    val (principales, otras) = accounts.partition { sirvePara(it, uso) || it.id == conservar }
    return CuentasDelPicker(principales = principales, otras = otras)
}
