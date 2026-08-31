package com.jvillada.movi.shared.model

/**
 * En qué estado está un presupuesto. Vive en `:core` y no en la pantalla **porque dos pantallas lo
 * necesitan** y ya se separaron una vez.
 *
 * El Inicio decidía «superado» con su propia comparación (`gastado >= límite`) y un comentario que
 * decía «misma regla que Presupuestos». Cuando Presupuestos dejó de contar el empate como exceso,
 * ese comentario pasó a ser mentira y las dos pantallas empezaron a contradecirse: el dueño veía
 * «Presupuesto de Mercado superado» en el Inicio y «Sin margen · gastaste justo el límite» al
 * entrar. Una regla sobre su plata no puede depender de por dónde la mire.
 */
enum class EstadoDePresupuesto {
    /** Hay margen de sobra. */
    DENTRO,

    /** Del 80 % para arriba, sin pasarse todavía. */
    CERCA,

    /** Gastó exactamente el límite. Ni sobrepasado ni «cerca»: no queda nada. */
    AL_LIMITE,

    /** Se pasó, pero poco. */
    EXCEDIDO_POCO,

    /** Se pasó bastante — ver [LIMITE_DE_EXCESO_GRAVE]. */
    EXCEDIDO_MUCHO;

    /** ¿Cuenta como «superado» para las alertas del Inicio? Solo si de verdad se pasó. */
    val estaSuperado: Boolean get() = this == EXCEDIDO_POCO || this == EXCEDIDO_MUCHO
}

/**
 * A partir de cuánto un exceso deja de ser «un poco»: **20 % del límite**.
 *
 * Es un umbral elegido, no medido, y conviene decirlo. La intención es la del dueño —«amarillo si
 * superé un poco y rojo si superé mucho»— y 20 % es donde un desvío deja de explicarse por una
 * compra extra y empieza a ser otro presupuesto. En su presupuesto de Mercado de $2.000.000 eso
 * son $400.000: una compra grande cabe en amarillo, dos ya no.
 */
const val LIMITE_DE_EXCESO_GRAVE = 120

/**
 * ### Por qué con enteros y no con un porcentaje en `Float`
 *
 * `Float` tiene 24 bits de mantisa: a partir de 16.777.216 deja de representar todos los enteros.
 * Con los montos de este dueño —hipotecas de cientos de millones— `gastado.toFloat() /
 * limite.toFloat()` da exactamente `1.0` para tres cifras que son tres estados distintos. En pesos
 * colombianos eso no es una hipótesis de laboratorio.
 *
 * Un límite en cero no está «superado» aunque haya gasto: es un presupuesto sin configurar.
 */
fun estadoDePresupuesto(gastado: Long, limite: Long): EstadoDePresupuesto = when {
    limite <= 0L -> EstadoDePresupuesto.DENTRO
    gastado > limite * LIMITE_DE_EXCESO_GRAVE / 100 -> EstadoDePresupuesto.EXCEDIDO_MUCHO
    gastado > limite -> EstadoDePresupuesto.EXCEDIDO_POCO
    gastado == limite -> EstadoDePresupuesto.AL_LIMITE
    gastado * 100 >= limite * 80 -> EstadoDePresupuesto.CERCA
    else -> EstadoDePresupuesto.DENTRO
}
