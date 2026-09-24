package com.jvillada.movi.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fix round 1, hallazgo 1: el `mimeType` guardado manda sobre el nombre cuando deciden cosas
 * distintas — un PDF renombrado desde «Editar» a «documento» (sin extensión) tiene que seguir
 * leyéndose como PDF, no caer al `else` genérico de `StatementParser.extractText`.
 */
class NombreParaExtraerTextoTest {

    @Test
    fun un_pdf_sin_extension_gana_la_extension_de_su_mime() {
        assertEquals("documento.pdf", nombreParaExtraerTexto("documento", "application/pdf"))
    }

    @Test
    fun un_pdf_con_otra_extension_tambien_se_corrige() {
        assertEquals(
            "extracto.docx.pdf",
            nombreParaExtraerTexto("extracto.docx", "application/pdf"),
        )
    }

    @Test
    fun si_ya_trae_la_extension_correcta_no_se_toca() {
        assertEquals("extracto.pdf", nombreParaExtraerTexto("extracto.pdf", "application/pdf"))
        // El mimeType puede traer parámetros (charset y cosas así) — se ignoran para esta decisión.
        assertEquals("extracto.pdf", nombreParaExtraerTexto("extracto.pdf", "application/pdf; charset=binary"))
    }

    @Test
    fun xls_xlsx_y_csv_siguen_la_misma_regla() {
        assertEquals("Famirios.xlsx", nombreParaExtraerTexto("Famirios", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals("extracto.xls", nombreParaExtraerTexto("extracto", "application/vnd.ms-excel"))
        assertEquals("movimientos.csv", nombreParaExtraerTexto("movimientos", "text/csv"))
    }

    @Test
    fun un_mime_sin_regla_conocida_deja_el_nombre_igual() {
        // Imágenes y cualquier otro mime no entran acá: `isImage` ya decide por mimeType antes de
        // llegar a esta función, y el resto no tiene una lectura binaria que dependa del nombre.
        assertEquals("documento", nombreParaExtraerTexto("documento", "image/png"))
        assertEquals("documento", nombreParaExtraerTexto("documento", "application/octet-stream"))
    }
}
