package com.jvillada.movi.ui.documentos

import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.TipoDeDocumento
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentosLogicTest {

    private fun doc(id: String, tipo: TipoDeDocumento) = Documento(
        id = id,
        nombre = "$id.pdf",
        tipo = tipo,
        mimeType = "application/pdf",
        bytes = 1000,
        subidoEn = 0L,
    )

    @Test
    fun el_peso_se_lee_sin_decimales_debajo_de_un_mega() {
        // «347 KB» es todo lo que hace falta saber de un PDF; «0,3 MB» es peor información con
        // más caracteres.
        assertEquals("512 B", pesoLegible(512))
        assertEquals("347 KB", pesoLegible(347 * 1024))
        assertEquals("1023 KB", pesoLegible(1023 * 1024))
    }

    @Test
    fun y_con_un_decimal_arriba_de_un_mega() {
        // Ahí sí importa: entre 1,2 y 1,9 MB hay diferencia al abrirlo con datos móviles.
        assertEquals("1,0 MB", pesoLegible(1024L * 1024))
        assertEquals("2,5 MB", pesoLegible((2.5 * 1024 * 1024).toLong()))
        assertEquals("10,0 MB", pesoLegible(10L * 1024 * 1024))
    }

    @Test
    fun el_tipo_se_sugiere_por_el_nombre_real_de_los_archivos_del_dueno() {
        // Los nombres salen de sus archivos de verdad, no de ejemplos inventados.
        assertEquals(TipoDeDocumento.EXTRACTO, tipoSugeridoPara("Extracto_Bancolombia_ago2026.pdf"))
        assertEquals(TipoDeDocumento.NOMINA, tipoSugeridoPara("Desprendible de pago julio.pdf"))
        assertEquals(TipoDeDocumento.CONTRATO, tipoSugeridoPara("Escritura Almendros.pdf"))
    }

    @Test
    fun ante_la_duda_no_adivina() {
        // Un extracto mal rotulado se arregla en un toque; una pantalla que adivina mal seguido
        // deja de merecer confianza.
        assertEquals(TipoDeDocumento.OTRO, tipoSugeridoPara("IMG_4821.jpg"))
        assertEquals(TipoDeDocumento.OTRO, tipoSugeridoPara("documento final v3.pdf"))
    }

    @Test
    fun los_grupos_vacios_no_se_pintan() {
        // Cuatro encabezados sobre tres archivos se lee peor que una lista corrida.
        val grupos = porTipo(listOf(doc("a", TipoDeDocumento.EXTRACTO), doc("b", TipoDeDocumento.EXTRACTO)))

        assertEquals(1, grupos.size)
        assertEquals(TipoDeDocumento.EXTRACTO, grupos.single().first)
        assertEquals(2, grupos.single().second.size)
    }

    @Test
    fun los_extractos_van_primero_y_otros_al_final() {
        // Es el orden en que se buscan: el extracto es lo que uno abre cuando una cifra no cuadra.
        val grupos = porTipo(
            listOf(
                doc("x", TipoDeDocumento.OTRO),
                doc("c", TipoDeDocumento.CONTRATO),
                doc("e", TipoDeDocumento.EXTRACTO),
                doc("n", TipoDeDocumento.NOMINA),
            ),
        )

        assertEquals(
            listOf(
                TipoDeDocumento.EXTRACTO,
                TipoDeDocumento.NOMINA,
                TipoDeDocumento.CONTRATO,
                TipoDeDocumento.OTRO,
            ),
            grupos.map { it.first },
        )
    }

    @Test
    fun ninguno_se_pierde_al_agrupar() {
        // La suma de los grupos tiene que dar la lista entera: un tipo nuevo que nadie agregue a
        // `porTipo` haría desaparecer archivos de la pantalla en silencio.
        val todos = TipoDeDocumento.entries.map { doc(it.name, it) }

        assertEquals(todos.size, porTipo(todos).sumOf { it.second.size })
        assertTrue(TipoDeDocumento.entries.all { tipo -> porTipo(todos).any { it.first == tipo } })
    }

    private val z = CORTE_POSIBLE

    @Test
    fun el_nombre_largo_se_puede_partir_despues_de_cada_separador() {
        assertEquals(
            "Portal_${z}Beneficios_${z}3037_${z}movimientos_${z}09_${z}2026.${z}jpeg",
            nombreQueSePartePorSusSeparadores("Portal_Beneficios_3037_movimientos_09_2026.jpeg"),
        )
        assertEquals("extracto-${z}nu.${z}pdf", nombreQueSePartePorSusSeparadores("extracto-nu.pdf"))
    }

    @Test
    fun los_separadores_seguidos_y_el_final_no_llevan_corte() {
        assertEquals("a__${z}b", nombreQueSePartePorSusSeparadores("a__b"))
        assertEquals("a._${z}b", nombreQueSePartePorSusSeparadores("a._b"))
        assertEquals("fin_", nombreQueSePartePorSusSeparadores("fin_"))
    }

    @Test
    fun un_nombre_sin_separadores_queda_igual() {
        assertEquals("Contrato arriendo", nombreQueSePartePorSusSeparadores("Contrato arriendo"))
        assertEquals("", nombreQueSePartePorSusSeparadores(""))
    }

    @Test
    fun sin_los_cortes_vuelve_a_ser_el_nombre_real() {
        // Lo que se pinta, quitándole lo invisible, es exactamente lo guardado: nada se pierde ni
        // se agrega de verdad.
        val nombre = "Portal_Beneficios_3037_movimientos_09_2026.jpeg"
        assertEquals(nombre, nombreQueSePartePorSusSeparadores(nombre).replace(z.toString(), ""))
    }
}
