package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.CUERPO_DEL_EXTRACTO_POR_SUBIR
import com.jvillada.movi.shared.model.ExtractoPorSubir
import com.jvillada.movi.shared.model.mesDelExtractoPorSubir
import com.jvillada.movi.shared.model.nombreDelExtractoPorSubir
import com.jvillada.movi.shared.model.preguntaDelExtractoPorSubir

/*
 * # «¿Ya bajaste el extracto de Nu de octubre?», en el teléfono (vuelta 94)
 *
 * Un Worker diario (`AvisoDeExtractoWorker`, en `:androidApp`) lee la misma lista que Hoy (`GET
 * /api/extractos-por-subir`) y avisa lo nuevo; tocar el aviso abre el Inicio, donde está «Subir el
 * extracto». Acá vive lo que decide y lo que dice, puro y probado, como `AvisoDeDebitosAutomaticos.kt`.
 */

/** La hora del aviso, en Bogotá: después del cierre (9:00) y de los débitos (10:00), para no sonar juntos. */
const val HORA_DEL_AVISO_DE_EXTRACTO: Int = 11

/** Cuántas huellas de extractos ya avisados recuerda el teléfono: de sobra para años de varias tarjetas. */
const val TOPE_DE_EXTRACTOS_AVISADOS: Int = 60

/**
 * **Qué extractos avisar**: los que el teléfono no avisó todavía. La llave es la huella —tarjeta y
 * corte—, así que **cada tarjeta suena una sola vez por corte**: el Worker corre todos los días y la
 * propuesta sigue en Hoy hasta que llega el extracto o el dueño dice «Ya no», pero el mismo corte no
 * vuelve a sonar. El corte siguiente es otra huella y sí suena.
 */
fun extractosParaAvisar(extractos: List<ExtractoPorSubir>, yaAvisados: Collection<String>): List<ExtractoPorSubir> =
    extractos.filter { it.huella !in yaAvisados }.distinctBy { it.huella }

/**
 * Lo que el teléfono recuerda después de avisar [avisados]: las huellas de antes más las nuevas, sin
 * repetir y con tope ([TOPE_DE_EXTRACTOS_AVISADOS]); se olvidan primero las más viejas.
 */
fun recordarExtractosAvisados(previos: List<String>, avisados: List<ExtractoPorSubir>): List<String> =
    (previos + avisados.map { it.huella }).distinct().takeLast(TOPE_DE_EXTRACTOS_AVISADOS)

/**
 * **El texto del aviso.** Uno: la pregunta de Hoy y «Súbelo y Movi anota los intereses y los cargos
 * del banco.» (genérica: Nu, por ejemplo, no cobra cuota de manejo). Varios: «Faltan 2 extractos por subir», con uno por renglón. `null` sin nada que decir.
 */
fun textoDelAvisoDeExtracto(extractos: List<ExtractoPorSubir>): TextoDeAviso? {
    if (extractos.isEmpty()) return null
    if (extractos.size == 1) {
        val uno = extractos.single()
        return TextoDeAviso(titulo = preguntaDelExtractoPorSubir(uno), texto = CUERPO_DEL_EXTRACTO_POR_SUBIR)
    }
    return TextoDeAviso(
        titulo = "Faltan ${extractos.size} extractos por subir",
        texto = CUERPO_DEL_EXTRACTO_POR_SUBIR,
        lineas = extractos.map { e ->
            nombreDelExtractoPorSubir(e, extractos) + (mesDelExtractoPorSubir(e)?.let { " · $it" }.orEmpty())
        },
    )
}
