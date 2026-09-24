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
 * de esta tarea funcionaba. Con `mimeEsConfiable = false` (el default, para el navegador) una
 * extensión reconocida en el NOMBRE siempre gana; el `mimeType` solo decide cuando el nombre no
 * trae ninguna de las cuatro.
 *
 * Fix round 3, hallazgo 2: esa regla reabrió el problema original para
 * `/api/documents/{id}/leer-extracto`, que llama con `mimeEsConfiable = true` porque ahí el
 * `mimeType` lo puso el SERVER al subir (no un navegador) y el `nombre` es texto libre que el
 * dueño edita después. Un PDF renombrado por error a «Extracto agosto.xls» (extensión
 * "reconocida", pero equivocada) volvía a hacer explotar `WorkbookFactory.create` contra bytes
 * de PDF. Con `mimeEsConfiable = true`, el `mimeType` gana siempre que reconozca un tipo,
 * incluso sobre una extensión del nombre que también sea reconocida.
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

    // ── mimeEsConfiable = true (fix round 3, hallazgo 2) ────────────────────────────────

    @Test
    fun con_mime_confiable_el_mime_gana_aunque_el_nombre_ya_diga_otra_cosa_reconocida() {
        // El caso del coordinador: un PDF renombrado por error a «Extracto agosto.xls».
        assertEquals(
            "Extracto agosto.xls.pdf",
            nombreParaExtraerTexto("Extracto agosto.xls", "application/pdf", mimeEsConfiable = true),
        )
        // Y al revés: un .pdf real con mimeType de Excel guardado (no debería pasar en la
        // práctica —el server guarda el mime real al subir— pero la regla es simétrica).
        assertEquals(
            "informe.pdf.xls",
            nombreParaExtraerTexto("informe.pdf", "application/vnd.ms-excel", mimeEsConfiable = true),
        )
    }

    @Test
    fun con_mime_confiable_una_extension_ya_correcta_no_se_toca() {
        assertEquals("extracto.pdf", nombreParaExtraerTexto("extracto.pdf", "application/pdf", mimeEsConfiable = true))
    }

    @Test
    fun con_mime_confiable_sin_extension_se_comporta_igual_que_antes() {
        assertEquals("documento.pdf", nombreParaExtraerTexto("documento", "application/pdf", mimeEsConfiable = true))
    }

    @Test
    fun con_mime_confiable_un_mime_desconocido_sigue_dejando_el_nombre_igual() {
        // Ni con `mimeEsConfiable = true` hay nada que inventar si el mime no dice nada.
        assertEquals("documento", nombreParaExtraerTexto("documento", "application/octet-stream", mimeEsConfiable = true))
    }

    // ── Foto o texto (revisión final de la Ola B) ─────────────────────────────

    @Test
    fun con_el_mime_confiable_un_pdf_renombrado_a_png_no_va_por_el_camino_de_la_foto() {
        assertEquals(false, esImagenParaExtraer("extracto.png", "application/pdf", mimeEsConfiable = true))
        assertEquals(false, esImagenParaExtraer("extracto.jpg", "text/plain", mimeEsConfiable = true))
        // Y al revés: una foto guardada como tal sigue siendo foto aunque el nombre diga .pdf.
        assertEquals(true, esImagenParaExtraer("extracto.pdf", "image/png", mimeEsConfiable = true))
    }

    @Test
    fun con_el_mime_confiable_pero_generico_decide_el_nombre() {
        assertEquals(true, esImagenParaExtraer("foto.png", "application/octet-stream", mimeEsConfiable = true))
        assertEquals(false, esImagenParaExtraer("extracto.pdf", "application/octet-stream", mimeEsConfiable = true))
    }

    @Test
    fun sin_mime_confiable_basta_con_que_el_mime_o_el_nombre_digan_imagen() {
        // `POST /api/statements/upload`: el mime lo manda el navegador, así que no cambia nada.
        assertEquals(true, esImagenParaExtraer("extracto.png", "application/pdf"))
        assertEquals(true, esImagenParaExtraer("extracto", "image/jpeg"))
        assertEquals(false, esImagenParaExtraer("extracto.pdf", "application/pdf"))
    }
}
