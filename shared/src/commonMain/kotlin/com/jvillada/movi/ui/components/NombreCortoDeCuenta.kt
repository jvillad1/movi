package com.jvillada.movi.ui.components

/**
 * # El nombre corto de una cuenta (vuelta 83)
 *
 * La revisión 8 vio «Bancolombia Ahorros 4…» en cada fila de Actividad y «Bancolombia …» en Agregar,
 * incluso en el diálogo de 520: se cortaba por el final, justo donde está lo que distingue a una cuenta
 * de otra del mismo banco (el dueño tiene varias de Bancolombia). El nombre corto se queda con eso.
 *
 * - **Con número**: el banco (la primera palabra del nombre) y los últimos cuatro del primer número:
 *   «Bancolombia Ahorros 4057» → «Bancolombia ·4057»; «Fiducuenta 9586» → «Fiducuenta ·9586». Si el
 *   nombre dice la moneda («Master Black 3684 USD»), va al final («Master ·3684 USD»): la tarjeta vive en
 *   dos cuentas con el mismo número.
 * - **Sin número**: el nombre tal cual si tiene hasta [LARGO_MAXIMO_DEL_NOMBRE_CORTO] letras («Nu»,
 *   «Efectivo», «Bancolombia Ahorros»); si es más largo, **recortado por el medio** con «…», no por el
 *   final: el principio y el final de un nombre son los que se recuerdan.
 *
 * Lo usan Agregar (las filas de Cuenta, Desde y Hacia), las filas de movimientos (Inicio, Actividad,
 * Buscar) y el subtítulo de un traspaso. Lo guardado no cambia: es solo cómo se pinta. Pura.
 */
fun nombreCortoDeCuenta(nombre: String): String {
    val limpio = nombre.trim().replace(ESPACIOS, " ")
    if (limpio.isEmpty()) return limpio
    val numero = PRIMER_NUMERO.find(limpio)
    if (numero != null) {
        val palabras = limpio.split(' ')
        val banco = palabras.firstOrNull { p -> p.any { it.isLetter() } && p.none { it.isDigit() } }
        val moneda = palabras.lastOrNull { it in MONEDAS }
        val cola = "·" + numero.value.takeLast(4)
        return listOfNotNull(banco?.let { "$it $cola" } ?: cola, moneda).joinToString(" ")
    }
    if (limpio.length <= LARGO_MAXIMO_DEL_NOMBRE_CORTO) return limpio
    val cabeza = LARGO_MAXIMO_DEL_NOMBRE_CORTO / 2
    val cuerpo = LARGO_MAXIMO_DEL_NOMBRE_CORTO - 1 - cabeza
    return limpio.take(cabeza).trimEnd() + "…" + limpio.takeLast(cuerpo).trimStart()
}

/** Hasta cuántas letras va entero un nombre sin número (vuelta 83): «Bancolombia Ahorros» entra. */
const val LARGO_MAXIMO_DEL_NOMBRE_CORTO: Int = 20

private val ESPACIOS = Regex("""\s+""")
private val PRIMER_NUMERO = Regex("""\d{3,}""")
private val MONEDAS = setOf("USD", "EUR", "COP")

/**
 * **La cuenta habitual de una lista de movimientos** (vuelta 83): la que no hace falta nombrar en cada
 * fila. Una regla simple:
 *
 * 1. **La más usada** que ya calcula la app ([masUsada]: `DashboardSummary.cuentaMasUsada`, la de más
 *    gastos en 30 días, la misma que propone Agregar).
 * 2. Si todavía no se sabe (se abrió Actividad antes que el Inicio), **la que más movimientos tiene en
 *    la lista** ([cuentasDeLaLista], una por movimiento).
 * 3. Con un empate en el paso 2, **ninguna**: si la lista mezcla cuentas por partes iguales, todas
 *    las filas dicen la suya.
 *
 * Pura.
 */
fun cuentaHabitual(cuentasDeLaLista: List<String>, masUsada: String?): String? {
    if (!masUsada.isNullOrBlank()) return masUsada
    if (cuentasDeLaLista.isEmpty()) return null
    val conteo = cuentasDeLaLista.groupingBy { it }.eachCount()
    val mayor = conteo.values.max()
    return conteo.filterValues { it == mayor }.keys.singleOrNull()
}

