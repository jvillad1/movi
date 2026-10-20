package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # «¿Es la misma persona?» (20-oct-2026)
 *
 * La misma persona termina guardada dos veces: «Caro», con su número de cuenta, y «Carolina Restrepo
 * Salazar», con el nombre con que Nu la nombra cuando le manda plata. Cada ficha se lleva la mitad de
 * los movimientos, y ninguna cifra dice lo que se le mandó de verdad. Unirlas ya se podía (`/unir`),
 * pero solo si el dueño se daba cuenta solo.
 *
 * [posiblesDuplicados] los propone. **Nunca une solo**: la pantalla muestra «¿Es la misma persona?»
 * con «Unir» y «No son la misma», y lo segundo se recuerda ([claveDeNoSonLaMisma], en
 * `destinos_descartados`). Unir le deja al dueño **la ficha más antigua** ([PosibleDuplicado.queda]):
 * es la que ya está referenciada desde créditos y recurrentes (el server además lo garantiza).
 *
 * Las reglas —y sus umbrales, **elegidos, no medidos**:
 *
 * - **Comparten un identificador** ([mismoIdentificador]: un número por sus últimos cuatro dígitos,
 *   una llave exacta). El alta ya lo impide, pero los datos de antes de esa guarda pueden tenerlo.
 * - **El mismo nombre**: el nombre, un nombre anterior o un identificador-nombre de uno es igual
 *   (sin mayúsculas ni tildes) a uno del otro.
 * - **Un nombre es el comienzo evidente del otro**: palabra por palabra, cada palabra del más corto es
 *   el comienzo de la que le toca del más largo y la primera tiene al menos tres letras — la regla de
 *   [pareceDe]: «Caro» y «Carolina Restrepo Salazar», «Daniel» y «Daniel Leonett». «Ana» no pega con
 *   «Mariana» (no es el comienzo) ni «Jo» con nadie.
 *
 * Por nombre no se propone si el dueño eligió tipos distintos (una persona y un comercio que se
 * llaman parecido no son el mismo).
 */

/** Por qué Movi cree que dos fichas son la misma. */
@Serializable
enum class MotivoDelParecido { MISMO_IDENTIFICADOR, MISMO_NOMBRE, NOMBRE_PARECIDO }

/** Dos fichas que parecen la misma: [seVa] se uniría a [queda], la más antigua. */
data class PosibleDuplicado(
    val queda: DestinoConocido,
    val seVa: DestinoConocido,
    val motivo: MotivoDelParecido,
)

/**
 * La clave con que se recuerda «No son la misma» en `destinos_descartados`: `MISMA:<id>|<id>`, con los
 * ids ordenados para que el par sea el mismo en cualquier orden.
 */
fun claveDeNoSonLaMisma(a: String, b: String): String = "MISMA:" + listOf(a, b).sorted().joinToString("|")

/** Los pares de fichas que parecen la misma, sin los que el dueño ya dijo que no ([descartados]). */
fun posiblesDuplicados(destinos: List<DestinoConocido>, descartados: Set<String> = emptySet()): List<PosibleDuplicado> {
    val pares = mutableListOf<PosibleDuplicado>()
    for (i in destinos.indices) for (j in i + 1 until destinos.size) {
        val a = destinos[i]
        val b = destinos[j]
        if (a.id.isEmpty() || b.id.isEmpty() || a.id == b.id) continue
        if (claveDeNoSonLaMisma(a.id, b.id) in descartados) continue
        val motivo = motivoDelParecido(a, b) ?: continue
        val (queda, seVa) = if (esMasAntigua(a, b)) a to b else b to a
        pares += PosibleDuplicado(queda, seVa, motivo)
    }
    return pares.sortedWith(compareBy<PosibleDuplicado> { it.motivo.ordinal }.thenBy { normalizarParaBuscar(it.queda.nombre) })
}

private fun motivoDelParecido(a: DestinoConocido, b: DestinoConocido): MotivoDelParecido? {
    val deB = b.todosLosIdentificadores()
    if (a.todosLosIdentificadores().any { x -> deB.any { y -> mismoIdentificador(x, y) } }) return MotivoDelParecido.MISMO_IDENTIFICADOR
    // Una persona y un comercio, elegidos así por el dueño, no son el mismo por llamarse parecido.
    val tipoA = a.tipo.takeUnless { a.tipoInferido }
    val tipoB = b.tipo.takeUnless { b.tipoInferido }
    if (tipoA != null && tipoB != null && tipoA != tipoB) return null
    val nombresA = nombresParaComparar(a)
    val nombresB = nombresParaComparar(b)
    if (nombresA.any { it in nombresB }) return MotivoDelParecido.MISMO_NOMBRE
    val parecido = nombresA.any { x -> nombresB.any { y -> elNombreEmpiezaIgual(x, y) || elNombreEmpiezaIgual(y, x) } }
    return if (parecido) MotivoDelParecido.NOMBRE_PARECIDO else null
}

private fun nombresParaComparar(d: DestinoConocido): Set<String> =
    (listOf(d.nombre) + d.nombresAnteriores + d.todosLosIdentificadores()
        .filter { it.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(it.valor) }.map { it.valor })
        .map(::enPalabrasSimples)
        .filter { it.isNotEmpty() }
        .toSet()

/** ¿[a] se guardó antes que [b]? Sin fecha (un server viejo), decide el id, para que sea estable. */
private fun esMasAntigua(a: DestinoConocido, b: DestinoConocido): Boolean {
    val ca = a.creadoEn
    val cb = b.creadoEn
    return when {
        ca != null && cb != null && ca != cb -> ca < cb
        ca != null && cb == null -> true
        ca == null && cb != null -> false
        else -> a.id < b.id
    }
}

/** La pregunta de la tarjeta: «¿Es la misma persona?» o, si los dos son comercios, «¿Es el mismo comercio?». */
fun preguntaDelDuplicado(par: PosibleDuplicado): String =
    if (par.queda.tipoOPersona() == TipoDeTercero.COMERCIO && par.seVa.tipoOPersona() == TipoDeTercero.COMERCIO) {
        "¿Es el mismo comercio?"
    } else {
        "¿Es la misma persona?"
    }

/** Por qué se pregunta, en una línea. */
fun porQueSePregunta(par: PosibleDuplicado): String = when (par.motivo) {
    MotivoDelParecido.MISMO_IDENTIFICADOR -> "Las dos fichas tienen el mismo número o llave."
    MotivoDelParecido.MISMO_NOMBRE -> "Las dos fichas tienen el mismo nombre."
    MotivoDelParecido.NOMBRE_PARECIDO -> "Un nombre parece el comienzo del otro."
}
