package com.jvillada.movi.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fix round 1, hallazgo 1: un PDF renombrado desde «Editar» a «documento» (sin extensión) tiene
 * que seguir leyéndose como PDF, no caer al `else` genérico de `StatementParser.extractText`.
 *
 * Fix round 2, hallazgo B: la primera versión dejaba que el `mimeType` IMPUSIERA una extensión
 * incluso cuando el nombre ya traía una de las cuatro que `extractText` distingue — y esta misma
 * función también corre en `POST /api/statements/upload`, donde el `mimeType` lo manda el
 * NAVEGADOR de quien sube, no el server. Un Excel en Windows reporta un `.csv` como
 * `application/vnd.ms-excel`: con la versión vieja, `extracto.csv` se volvía `extracto.csv.xls`
 * y `WorkbookFactory.create` explotaba contra un archivo que es texto plano — un 500 donde antes
 * de esta tarea funcionaba. Ahora una extensión reconocida en el NOMBRE siempre gana; el
 * `mimeType` solo decide cuando el nombre no trae ninguna de las cuatro.
 */
class NombreParaExtraerTextoTest {

    @Test
    fun un_pdf_sin_extension_gana_la_extension_de_su_mime() {
        assertEquals("documento.pdf", nombreParaExtraerTexto("documento", "application/pdf"))
    }

    @Test
    fun una_extension_desconocida_tambien_deja_que_el_mime_decida() {
        // "docx" no es ninguna de las cuatro que esta función entiende, así que no cuenta como
        // "ya trae una extensión reconocida" — se comporta igual que sin extensión.
        assertEquals(
            "extracto.docx.pdf",
            nombreParaExtraerTexto("extracto.docx", "application/pdf"),
        )
    }

    @Test
    fun si_ya_trae_la_extension_correcta_no_se_toca() {
        assertEquals("extracto.pdf", nombreParaExtraerTexto("extracto.pdf", "application/pdf"))
    }

    @Test
    fun una_extension_reconocida_gana_aunque_el_mime_diga_otra_cosa() {
        // Hallazgo B: el caso real que rompía. Windows/Excel reporta un .csv como
        // application/vnd.ms-excel — el nombre ya dice "csv" y esa es la lectura correcta
        // (texto plano), no un intento de abrirlo como .xls binario.
        assertEquals("movimientos.csv", nombreParaExtraerTexto("movimientos.csv", "application/vnd.ms-excel"))
        // Y al revés: un .xls real con un mimeType de PDF (un navegador raro, un proxy que
        // adivinó mal) no se convierte en "informe.xls.pdf".
        assertEquals("informe.xls", nombreParaExtraerTexto("informe.xls", "application/pdf"))
    }

    @Test
    fun xls_xlsx_y_csv_sin_extension_tambien_la_ganan_del_mime() {
        assertEquals("Famirios.xlsx", nombreParaExtraerTexto("Famirios", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals("extracto.xls", nombreParaExtraerTexto("extracto", "application/vnd.ms-excel"))
        assertEquals("movimientos.csv", nombreParaExtraerTexto("movimientos", "text/csv"))
    }

    @Test
    fun el_mimeType_puede_traer_parametros() {
        // El mimeType puede traer parámetros (charset y cosas así) — se ignoran para esta decisión.
        assertEquals("documento.pdf", nombreParaExtraerTexto("documento", "application/pdf; charset=binary"))
    }

    @Test
    fun un_mime_sin_regla_conocida_deja_el_nombre_igual() {
        // Imágenes y cualquier otro mime no entran acá: `isImage` ya decide por mimeType antes de
        // llegar a esta función, y el resto no tiene una lectura binaria que dependa del nombre.
        assertEquals("documento", nombreParaExtraerTexto("documento", "image/png"))
        assertEquals("documento", nombreParaExtraerTexto("documento", "application/octet-stream"))
    }
}
