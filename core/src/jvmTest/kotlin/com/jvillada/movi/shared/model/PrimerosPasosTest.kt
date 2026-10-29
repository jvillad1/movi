package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La lista única de primeros pasos (vuelta 54): sus estados, el contador y la captura por plataforma. */
class PrimerosPasosTest {

    private val nada = LoQueYaHay(periodoElegido = false, cuentasDeDinero = 0, capturaHecha = false, fijos = 0)
    private val todo = LoQueYaHay(periodoElegido = true, cuentasDeDinero = 2, capturaHecha = true, fijos = 3)

    @Test
    fun `cuatro pasos en orden donde hay captura`() {
        listOf(CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO, CapturaDelPrimerUso.SUBIR_UN_EXTRACTO).forEach { captura ->
            assertEquals(
                listOf(PrimerPaso.PERIODO, PrimerPaso.CUENTAS, PrimerPaso.CAPTURA, PrimerPaso.FIJOS),
                primerosPasos(nada, captura).map { it.paso },
                "$captura",
            )
        }
    }

    @Test
    fun `sin captura posible el paso no va, ni hecho ni pendiente`() {
        val lista = primerosPasos(nada, CapturaDelPrimerUso.NINGUNA)
        assertEquals(listOf(PrimerPaso.PERIODO, PrimerPaso.CUENTAS, PrimerPaso.FIJOS), lista.map { it.paso })
        assertEquals(3, pasosQueFaltan(lista))
        assertEquals("0 de 3", contadorDeLosPrimerosPasos(lista))
    }

    @Test
    fun `cada paso se tilda con su dato`() {
        val lista = primerosPasos(
            LoQueYaHay(periodoElegido = true, cuentasDeDinero = 0, capturaHecha = true, fijos = 0),
            CapturaDelPrimerUso.SUBIR_UN_EXTRACTO,
        )
        assertEquals(listOf(true, false, true, false), lista.map { it.hecho })
        assertEquals(2, pasosQueFaltan(lista))
        assertEquals("2 de 4", contadorDeLosPrimerosPasos(lista))
        assertEquals(PrimerPaso.CUENTAS, primerPasoPendiente(lista), "la guía abre en el primero que falta")
    }

    @Test
    fun `con todo hecho no falta nada y el contador lo dice`() {
        val lista = primerosPasos(todo, CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO)
        assertEquals(0, pasosQueFaltan(lista))
        assertNull(primerPasoPendiente(lista))
        assertNull(textoDeLosPasosQueFaltan(0))
        assertEquals("4 de 4", contadorDeLosPrimerosPasos(lista))
    }

    @Test
    fun `el texto de la fila del Inicio`() {
        assertEquals("Te falta 1 paso", textoDeLosPasosQueFaltan(1))
        assertEquals("Te faltan 3 pasos", textoDeLosPasosQueFaltan(3))
    }

    @Test
    fun `Android lee el telefono, la web e iOS suben un extracto, y sin archivos no hay paso`() {
        assertEquals(CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO, capturaDelPrimerUsoEn(esAndroid = true))
        assertEquals(CapturaDelPrimerUso.SUBIR_UN_EXTRACTO, capturaDelPrimerUsoEn(esAndroid = false))
        assertEquals(CapturaDelPrimerUso.NINGUNA, capturaDelPrimerUsoEn(esAndroid = false, puedeElegirArchivos = false))
        assertEquals("Que Movi se entere solo", tituloDelPrimerPaso(PrimerPaso.CAPTURA, CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO))
        assertEquals("Sube un extracto", tituloDelPrimerPaso(PrimerPaso.CAPTURA, CapturaDelPrimerUso.SUBIR_UN_EXTRACTO))
    }

    @Test
    fun `el periodo esta elegido con cualquier cosa que no sea el mes de calendario`() {
        assertFalse(periodoElegidoEn(PeriodSettings()))
        assertTrue(periodoElegidoEn(PeriodSettings(cutoffDay = 25)))
        assertTrue(periodoElegidoEn(PeriodSettings(cutoffDay = 1, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)))
    }

    @Test
    fun `el rango corto del encabezado`() {
        assertEquals("23 oct – 24 nov", rangoCortoEntre(LocalDate(2026, 10, 23), LocalDate(2026, 11, 24)))
        assertEquals("1 – 31 oct", rangoCortoEntre(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)))
        assertEquals("24 dic – 24 ene", rangoCortoEntre(LocalDate(2026, 12, 24), LocalDate(2027, 1, 24)))
        // El 25 de octubre de 2026 es domingo: con el día hábil anterior, el período de noviembre empieza el 23.
        val ajustes = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        assertEquals("23 oct – 24 nov", rangoCortoDelPeriodo(PeriodoFinanciero(2026, 11), ajustes))
    }

    /**
     * **Revisión de la vuelta 55: «Tu período» se le pide solo a una cuenta nueva y vacía.** Una cuenta
     * en uso que se quedó con el mes de calendario (el defecto, una elección válida) veía «Te falta 1
     * paso · Sigue con «Tu período»» para siempre.
     */
    @Test
    fun `tu periodo se pide solo a una cuenta nueva y vacia`() {
        val mesDeCalendario = PeriodSettings()
        val corteElegido = PeriodSettings(cutoffDay = 25)
        // Nueva, sin nada: se pide.
        val nueva = laCuentaYaEstabaEnUso(hayMovimientos = false, cuentas = 0, primerUsoEn = null)
        assertFalse(nueva)
        assertFalse(periodoHechoEn(mesDeCalendario, nueva))
        // En uso con el mes de calendario: hecho, por cualquiera de las tres señales.
        assertTrue(periodoHechoEn(mesDeCalendario, laCuentaYaEstabaEnUso(hayMovimientos = true, cuentas = 0, primerUsoEn = null)))
        assertTrue(periodoHechoEn(mesDeCalendario, laCuentaYaEstabaEnUso(hayMovimientos = false, cuentas = 2, primerUsoEn = null)))
        assertTrue(periodoHechoEn(mesDeCalendario, laCuentaYaEstabaEnUso(hayMovimientos = false, cuentas = 0, primerUsoEn = 1_761_000_000_000L)))
        // Con el corte elegido: hecho, aunque la cuenta sea nueva.
        assertTrue(periodoHechoEn(corteElegido, nueva))
        // Y la fila del Inicio de una cuenta en uso con todo lo demás hecho ya no dice que falta un paso.
        val enUso = primerosPasos(
            LoQueYaHay(
                periodoElegido = periodoHechoEn(mesDeCalendario, laCuentaYaEstabaEnUso(true, 3, null)),
                cuentasDeDinero = 3, capturaHecha = true, fijos = 2,
            ),
            CapturaDelPrimerUso.SUBIR_UN_EXTRACTO,
        )
        assertEquals(null, textoDeLosPasosQueFaltan(pasosQueFaltan(enUso)))
    }
}
