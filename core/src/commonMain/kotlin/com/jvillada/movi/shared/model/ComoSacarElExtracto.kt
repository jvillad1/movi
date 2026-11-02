package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate

/*
 * # Vuelta 107 · «¿Cómo lo saco?»: la guía corta para bajar el extracto de cada banco
 *
 * El 2-nov se cruzaron a mano los movimientos de la cuenta Bancolombia del dueño contra Movi: 42 en
 * el banco, 32 en Movi. Lo que faltaba era, sobre todo, lo que ningún banco avisa (el 4x1000, las
 * comisiones por traslado y su IVA) y tres pagos sin aviso. Para eso está el extracto, y Movi ya lo
 * lee sin IA; lo que no estaba era **cómo sacarlo**. Esto es esa guía, como dato y en un solo lugar:
 * la usan el recordatorio del extracto, el detalle de una cuenta o tarjeta de ese banco y la revisión
 * de un extracto subido.
 *
 * **Solo se escribe lo verificado.** Los pasos de Bancolombia se siguieron en el portal el 2-nov-2026
 * (Sucursal Virtual Personas). Los de Nu no se pudieron comprobar: en el repo solo está su extracto
 * en PDF (`ExtractoDeNu.kt`, vuelta 94), no la app; por eso su guía es genérica y lo dice
 * ([GuiaDelExtracto.verificada] = `false`). Un banco sin guía no muestra nada.
 */

/**
 * La guía de un banco. [pasos] son los de una cuenta; [pasosDeTarjeta], los de una tarjeta de crédito
 * si son distintos (`null`: los mismos). [formatos] es lo que deja elegir el banco; [limite], hasta
 * dónde deja bajar. [conRango]: el banco deja elegir «Desde / Hasta» (y vale proponer el rango ideal,
 * [rangoIdealDelExtracto]); un extracto mensual cerrado (Nu) no.
 */
data class GuiaDelExtracto(
    val banco: String,
    val pasos: List<String>,
    val pasosDeTarjeta: List<String>? = null,
    val formatos: String,
    /** El banco también deja bajar Excel y Movi tiene un lector para él (ver [EL_LECTOR_DE_EXCEL_ESTA_ACTIVO]). */
    val tambienExcel: Boolean = false,
    val limite: String? = null,
    val conRango: Boolean,
    /** Los pasos se comprobaron en el banco. Si no, la hoja lo dice ([NOTA_DE_LA_GUIA_SIN_VERIFICAR]). */
    val verificada: Boolean,
) {
    /** Los pasos para una tarjeta ([esTarjeta]) o para una cuenta. */
    fun pasosPara(esTarjeta: Boolean): List<String> = if (esTarjeta) pasosDeTarjeta ?: pasos else pasos
}

/** Lo que dice la hoja cuando los pasos no se pudieron comprobar en el banco. */
const val NOTA_DE_LA_GUIA_SIN_VERIFICAR: String =
    "Movi no ha podido comprobar estos pasos en la app del banco: pueden cambiar un poco."

/** Bancolombia, verificado en el portal el 2-nov-2026. */
val GUIA_DE_BANCOLOMBIA: GuiaDelExtracto = GuiaDelExtracto(
    banco = "Bancolombia",
    pasos = listOf(
        "Entra a la Sucursal Virtual Personas.",
        "En Inicio, toca la cuenta.",
        "Abre la pestaña «Movimientos» y toca «Descargar».",
        "Elige el formato PDF y las fechas «Desde» y «Hasta».",
        "Toca «Descargar» y sube el archivo a Movi.",
    ),
    pasosDeTarjeta = listOf(
        "Entra a la Sucursal Virtual Personas.",
        "Ve a «Tarjetas de crédito» y toca la tarjeta.",
        "Abre «Movimientos» y toca «Descargar».",
        "Elige el formato PDF y las fechas «Desde» y «Hasta».",
        "Toca «Descargar» y sube el archivo a Movi.",
    ),
    formatos = "PDF",
    tambienExcel = true,
    limite = "Deja bajar los movimientos de este mes y de los últimos 3 meses.",
    conRango = true,
    verificada = true,
)

/** Nu: genérica, sin verificar (ver el encabezado del archivo). */
val GUIA_DE_NU: GuiaDelExtracto = GuiaDelExtracto(
    banco = "Nu",
    pasos = listOf("Desde la app de Nu: busca el extracto del mes y descárgalo en PDF."),
    formatos = "PDF",
    conRango = false,
    verificada = false,
)

/** Las guías que hay. Un banco que no está acá no tiene guía, y no se le muestra nada. */
val GUIAS_DEL_EXTRACTO: List<GuiaDelExtracto> = listOf(GUIA_DE_BANCOLOMBIA, GUIA_DE_NU)

/** «Nu» o «Nubank» como palabra: no «Nuevo», no «Número». */
private val PALABRA_NU = Regex("""\bnu(?:bank)?\b""")

/**
 * **La guía del banco que nombra [texto]** (el banco de una tarjeta, el nombre de una cuenta, el
 * banco que reconoció un extracto), o `null`. Sin tildes ni mayúsculas; Nu solo como palabra. Si el
 * texto nombra dos bancos («Traspaso Bancolombia a Nu»), ninguna: no se adivina.
 */
fun guiaDelExtractoPara(texto: String?): GuiaDelExtracto? {
    val plano = normalizarParaBuscar(texto.orEmpty())
    if (plano.isBlank()) return null
    val encontradas = buildList {
        if ("bancolombia" in plano) add(GUIA_DE_BANCOLOMBIA)
        if (PALABRA_NU.containsMatchIn(plano)) add(GUIA_DE_NU)
    }
    return encontradas.singleOrNull()
}

/**
 * La primera guía que encuentra entre [textos], en orden: el banco de las condiciones de una tarjeta
 * gana sobre el nombre de la cuenta («Master Black 3684» no dice que es de Bancolombia; su
 * `CardTerms.bank`, sí).
 */
fun guiaDelExtractoParaAlguno(vararg textos: String?): GuiaDelExtracto? =
    textos.firstNotNullOfOrNull { guiaDelExtractoPara(it) }

/**
 * **El rango ideal para bajar los movimientos**: desde el último corte ([desde], el inicio del período
 * del dueño o el corte de la tarjeta, el que sea más viejo) hasta [hoy]. «Desde tu último corte, el 23
 * de octubre, hasta hoy». Si [desde] cae hoy o después, «Desde hoy». Pura.
 */
fun rangoIdealDelExtracto(desde: LocalDate, hoy: LocalDate): String =
    if (desde >= hoy) "Rango ideal: hoy." else "Rango ideal: desde tu último corte, el ${diaLegible(desde)}, hasta hoy."

/**
 * **¿El server lee el Excel de Bancolombia?** (`leerElExcelDeBancolombia`, vuelta 107). Si se apaga, la guía
 * deja de ofrecerlo.
 */
const val EL_LECTOR_DE_EXCEL_ESTA_ACTIVO: Boolean = true

/**
 * «Formato: PDF (recomendado). También sirve el Excel.» El PDF va primero: es el que se probó con los papeles
 * reales; el Excel, solo si el lector está activo (y aún sin un Excel real que lo confirme).
 */
fun formatoDelExtracto(guia: GuiaDelExtracto, lectorDeExcel: Boolean = EL_LECTOR_DE_EXCEL_ESTA_ACTIVO): String =
    if (guia.tambienExcel && lectorDeExcel) "Formato: ${guia.formatos} (recomendado). También sirve el Excel."
    else "Formato: ${guia.formatos}."

/** El título de la hoja: «Cómo sacar tu extracto de Bancolombia». */
fun tituloDeLaGuiaDelExtracto(guia: GuiaDelExtracto): String = "Cómo sacar tu extracto de ${guia.banco}"

/** El botón del recordatorio que abre la guía. */
const val COMO_LO_SACO: String = "¿Cómo lo saco?"

/** La fila del detalle de una cuenta o tarjeta de un banco con guía. */
const val SUBE_EL_EXTRACTO_DE_ESTE_MES: String = "Sube el extracto de este mes"
