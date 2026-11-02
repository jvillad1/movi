package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # El PDF con clave se abre en Movi (vuelta 103)
 *
 * Los extractos colombianos casi siempre traen clave de apertura (suele ser la cédula). Hasta acá Movi
 * contestaba «Quítale la clave… imprímelo como PDF», algo casi imposible desde un iPhone. Ahora el
 * server dice que el PDF tiene clave con un código ([PDF_CON_CLAVE]) y la app la pide en la misma fila:
 * el reintento manda la clave en el **cuerpo** de `POST /api/documents/{id}/leer` (o `/leer-extracto`),
 * sobre el documento ya guardado, sin volver a subirlo.
 *
 * Los códigos viven en `:core` porque los escribe el server y los lee la app (como `IA_SIN_CREDITO`).
 * Solo se contestan a quien los pide con `?conClave=1`: un APK viejo recibe la frase de siempre.
 */

/** El PDF tiene clave de apertura y Movi no la tiene (o la recordada no sirvió): hay que pedirla. 422. */
const val PDF_CON_CLAVE: String = "PDF_CON_CLAVE"

/** La clave que se mandó no abre el PDF. Nada se guardó. 422. */
const val CLAVE_INCORRECTA: String = "CLAVE_INCORRECTA"

/** Demasiados intentos de clave sobre el mismo documento en la última hora. 429. */
const val DEMASIADOS_INTENTOS_DE_CLAVE: String = "DEMASIADOS_INTENTOS_DE_CLAVE"

/** Intentos de clave por documento y hora. Elegido, no medido: de sobra para quien se equivoca al teclear. */
const val INTENTOS_DE_CLAVE_POR_HORA: Int = 10

/**
 * Formas de clave probadas por usuario y hora, sumando todos sus PDFs, las que escribe y las que Movi
 * prueba con la recordada (revisión de seguridad de la vuelta 103): nadie adivina una cédula subiendo
 * PDFs. Elegido, no medido.
 */
const val APERTURAS_DE_PDF_CON_CLAVE_POR_HORA: Int = 30

/** Una clave de apertura más larga que esto no es una clave: se rechaza sin probarla. */
const val MAX_LARGO_DE_LA_CLAVE_DEL_PDF: Int = 128

/** ¿Este cuerpo es uno de los códigos de arriba? Tolera espacios y un salto de línea al final. */
fun esCodigoDeClaveDelPdf(cuerpo: String?): Boolean =
    cuerpo?.trim().let { it == PDF_CON_CLAVE || it == CLAVE_INCORRECTA || it == DEMASIADOS_INTENTOS_DE_CLAVE }

/**
 * El cuerpo del reintento. [recordar]: el dueño marcó «Recordarla para mis próximos extractos» (apagada
 * por defecto). La clave **nunca** viaja en la URL ni en un header: va acá, en el cuerpo.
 */
@Serializable
data class ClaveDelPdf(val clave: String, val recordar: Boolean = false)

/**
 * `GET /api/extractos/clave`: si hay una clave recordada y si el server puede recordarla (sin
 * `DOCUMENTS_ENCRYPTION_KEY` no se ofrece «Recordarla»: Movi no guarda claves en claro). **La clave en
 * sí no sale nunca del server.**
 */
@Serializable
data class EstadoDeLaClaveDeExtractos(val guardada: Boolean = false, val sePuedeRecordar: Boolean = false)

/** Lo que dice la app sobre la clave, en la fila del papel. */
const val PISTA_DE_LA_CLAVE_DEL_PDF: String = "Suele ser tu cédula. No es la clave de tu banca en línea."
const val ROTULO_DE_LA_CLAVE_DEL_PDF: String = "Clave del PDF"
const val RECORDAR_LA_CLAVE_DEL_PDF: String = "Recordarla para mis próximos extractos"
const val ABRIR_EL_PDF: String = "Abrir"
const val EL_PDF_TIENE_CLAVE: String = "Este PDF tiene clave. Escríbela para abrirlo."
const val LA_CLAVE_NO_ABRE_EL_PDF: String = "Esa clave no abre el PDF. Revísala e inténtalo de nuevo."
const val DEMASIADOS_INTENTOS_CON_EL_PDF: String = "Demasiados intentos con este PDF. Espera una hora e inténtalo de nuevo."

/** La frase de cada código, para los lugares que no piden la clave en línea (Documentos). */
fun fraseDeLaClaveDelPdf(codigo: String): String = when (codigo.trim()) {
    CLAVE_INCORRECTA -> LA_CLAVE_NO_ABRE_EL_PDF
    DEMASIADOS_INTENTOS_DE_CLAVE -> DEMASIADOS_INTENTOS_CON_EL_PDF
    else -> EL_PDF_TIENE_CLAVE
}
