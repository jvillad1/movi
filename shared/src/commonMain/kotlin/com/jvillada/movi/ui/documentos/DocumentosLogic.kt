package com.jvillada.movi.ui.documentos

import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.TipoDeDocumento

/**
 * El peso del archivo, para que la fila diga algo útil sin abrirlo.
 *
 * Sin decimales bajo 1 MB —«347 KB» es todo lo que hace falta saber de un PDF— y con uno arriba,
 * donde la diferencia entre 1,2 y 1,9 MB sí se nota al abrirlo con datos móviles.
 */
fun pesoLegible(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> {
        val megas = bytes * 10 / (1024 * 1024)
        "${megas / 10},${megas % 10} MB"
    }
}

/** El nombre en español de cada tipo, en singular — es el rótulo de una fila, no un título. */
fun nombreDeTipo(tipo: TipoDeDocumento): String = when (tipo) {
    TipoDeDocumento.EXTRACTO -> "Extracto"
    TipoDeDocumento.NOMINA -> "Nómina"
    TipoDeDocumento.CONTRATO -> "Contrato"
    TipoDeDocumento.OTRO -> "Documento"
}

/**
 * Adivina el tipo por el nombre del archivo, para no obligar a elegirlo en cada subida.
 *
 * Es una **sugerencia**, no una clasificación: el dueño puede cambiarla antes de guardar. Por eso
 * ante la duda devuelve [TipoDeDocumento.OTRO] en vez de arriesgar — un extracto mal rotulado se
 * arregla en un toque, pero una pantalla que adivina mal seguido deja de merecer confianza.
 *
 * Los nombres que reconoce salen de los archivos reales del dueño: los extractos de Bancolombia y
 * Davibank llegan como «Extracto_…», la nómina de MercadoLibre como «Desprendible…».
 */
fun tipoSugeridoPara(nombreDeArchivo: String): TipoDeDocumento {
    val n = nombreDeArchivo.lowercase()
    return when {
        listOf("extracto", "statement", "estado de cuenta").any { it in n } -> TipoDeDocumento.EXTRACTO
        listOf("nomina", "nómina", "desprendible", "payslip", "colilla").any { it in n } -> TipoDeDocumento.NOMINA
        listOf("contrato", "escritura", "promesa", "pagare", "pagaré").any { it in n } -> TipoDeDocumento.CONTRATO
        else -> TipoDeDocumento.OTRO
    }
}

/**
 * Agrupa por tipo, en el orden en que el dueño los busca: primero los extractos —que son los que
 * se consultan cuando una cifra no cuadra— y al final lo que no se pudo clasificar.
 *
 * Devuelve solo los grupos con algo adentro: una pantalla con cuatro encabezados y tres archivos
 * se lee peor que una lista corrida.
 */
fun porTipo(documentos: List<Documento>): List<Pair<TipoDeDocumento, List<Documento>>> =
    listOf(
        TipoDeDocumento.EXTRACTO,
        TipoDeDocumento.NOMINA,
        TipoDeDocumento.CONTRATO,
        TipoDeDocumento.OTRO,
    ).mapNotNull { tipo ->
        documentos.filter { it.tipo == tipo }.takeIf { it.isNotEmpty() }?.let { tipo to it }
    }

/** Espacio de ancho cero: no se ve, pero le dice al texto «acá se puede partir el renglón». */
internal const val CORTE_POSIBLE = '​'

private const val SEPARADORES = "_-."

/**
 * El nombre del archivo tal como se **muestra**, con permiso de partirse en sus separadores.
 *
 * «Portal_Beneficios_3037_movimientos_09_2026.jpeg» no tiene espacios, así que en un teléfono el
 * renglón se cortaba donde se acabara el ancho: «…_movimi / entos_09…». Con un [CORTE_POSIBLE]
 * después de cada `_`, `-` y `.` el texto prefiere partir ahí, y la palabra queda entera.
 *
 * Es SOLO para pintar: el nombre guardado, el que se edita y el que «Abrir» descarga siguen siendo
 * `Documento.nombre` tal cual — un carácter invisible metido en el nombre real rompería la búsqueda
 * y el archivo que baja.
 */
fun nombreQueSePartePorSusSeparadores(nombre: String): String = buildString(nombre.length + 8) {
    nombre.forEachIndexed { i, c ->
        append(c)
        // Ni al final (no queda nada que bajar de renglón) ni entre dos separadores seguidos
        // (en «a__b» basta un corte, después del último).
        val siguiente = nombre.getOrNull(i + 1)
        if (c in SEPARADORES && siguiente != null && siguiente !in SEPARADORES) append(CORTE_POSIBLE)
    }
}
