package com.jvillada.movi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cuándo el Inicio vuelve a pedir sus diez llamadas.
 *
 * Es una decisión sobre **cuándo se refrescan las cifras del dinero del dueño**, así que vive en
 * una función pura y tiene pruebas, en vez de estar enterrada adentro de un `LaunchedEffect`.
 *
 * El derroche que cierra: `App.kt` envuelve cada pantalla en un `SaveableStateProvider`, así que
 * al navegar a otra el Inicio sale de la composición y al volver rehace las diez. Se contaron
 * cuatro rondas completas en pocos minutos de uso normal.
 */
class TtlDelInicioTest {

    private fun recarga(
        hayDatos: Boolean = true,
        cargadoEn: Long = 1_000_000L,
        tickDeLaCarga: Int = 3,
        tickActual: Int = 3,
        reintento: Boolean = false,
        ahora: Long = 1_000_000L,
    ) = debeRecargarElInicio(hayDatos, cargadoEn, tickDeLaCarga, tickActual, reintento, ahora)

    @Test
    fun sin_nada_cacheado_siempre_carga() {
        // Arranque en frío, o la primera vez después de entrar. En la web es el caso normal:
        // recargar la página borra la caché en memoria.
        assertTrue(recarga(hayDatos = false))
        // Y ni el reloj ni el tick lo cambian: no hay nada que mostrar mientras tanto.
        //
        // La primera versión de esta segunda aserción pasaba `ahora` con su valor POR DEFECTO,
        // así que era idéntica byte a byte a la de arriba y no probaba lo que su comentario
        // prometía. La revisión lo marcó. Ahora sí varía las dos cosas.
        assertTrue(recarga(hayDatos = false, ahora = 1_000_000L + 5L, tickActual = 3))
        assertTrue(recarga(hayDatos = false, ahora = 1_000_000L + 5L, tickActual = 99))
    }

    @Test
    fun con_datos_pero_sin_sello_recarga() {
        // El invariante que impide servir una lectura PARCIAL como completa.
        //
        // «Primeros pasos» deja su lectura en la misma caché —es el mismo modelo— pero solo hace
        // 5 de las 10 llamadas del Inicio, así que NO escribe el sello. Si el Inicio confiara solo
        // en `hayDatos`, mostraría esa media lectura como si fuera una carga completa y se
        // saltearía la de verdad.
        //
        // `cargadoEn = 0` queda astronómicamente fuera de la ventana, así que recarga. Este test
        // fija esa consecuencia: sin él, alguien podría «arreglar» el cero con un `coerceAtLeast`
        // y romper la garantía sin que nada se ponga rojo.
        assertTrue(recarga(hayDatos = true, cargadoEn = 0L, ahora = 1_700_000_000_000L))
    }

    @Test
    fun volver_dentro_del_medio_minuto_NO_pide_de_nuevo() {
        // El caso que motivó todo: ir a Movimientos, mirar algo y volver.
        assertFalse(recarga(ahora = 1_000_000L + 29_000L))
    }

    @Test
    fun pasado_el_medio_minuto_si() {
        assertTrue(recarga(ahora = 1_000_000L + 30_001L))
    }

    @Test
    fun justo_en_el_limite_todavia_vale() {
        // El borde es inclusivo. No cambia nada práctico; se fija para que un refactor no lo
        // mueva sin querer.
        assertFalse(recarga(ahora = 1_000_000L + 30_000L))
    }

    @Test
    fun si_el_dueno_guardo_algo_recarga_aunque_sea_hace_un_segundo() {
        // `LocalRefreshTick` es la señal que emite la hoja de «Agregar». Sin esta regla, anotar un
        // gasto y volver al Inicio mostraría el saldo de antes — el modo de falla más caro de
        // todos, porque el dueño acaba de hacer algo y no lo ve reflejado.
        assertTrue(recarga(tickDeLaCarga = 3, tickActual = 4, ahora = 1_000_000L + 1_000L))
    }

    @Test
    fun el_tick_manda_por_encima_del_reloj() {
        // El orden importa: el tiempo es la última palabra, no la primera.
        assertTrue(recarga(tickActual = 99, ahora = 1_000_000L))
    }

    @Test
    fun reintentar_pide_de_nuevo_aunque_este_fresco() {
        // Tocar «Reintentar» y que no pase nada sería la peor respuesta posible a un error.
        assertTrue(recarga(reintento = true, ahora = 1_000_000L))
    }

    @Test
    fun un_reloj_que_va_para_atras_recarga() {
        // Cambio de zona horaria o ajuste del sistema: la diferencia da negativa. Mostrar cifras
        // viejas por un reloj mal puesto sería peor que gastar diez llamadas.
        assertTrue(recarga(ahora = 1_000_000L - 5_000L))
    }

    @Test
    fun el_ttl_es_medio_minuto() {
        // Si alguien lo sube, que sea a propósito: es cuánto puede llegar a ver el dueño de una
        // cifra vieja cuando la plata cambió desde otro dispositivo.
        assertEquals(30_000L, TTL_DEL_INICIO_MS)
    }
}

/**
 * Que **cualquier escritura** invalide la caché del Inicio.
 *
 * La primera versión del TTL confiaba en `LocalRefreshTick`, y la revisión lo midió: el tick es
 * un `Int` sin función para subirlo, así que solo lo mueven dos sitios de `App.kt`. Anular un
 * movimiento, ajustar el saldo de un crédito, registrar un descuento de nómina o importar un
 * extracto **no lo movían**, y todos mueven plata.
 *
 * Estas pruebas no montan el envoltorio real (necesitaría implementar 79 métodos): fijan la regla
 * que el envoltorio implementa, que es la parte que importa y la que alguien podría relajar.
 */
class CualquierEscrituraInvalidaTest {

    @Test
    fun invalidar_deja_el_sello_fuera_de_cualquier_ventana() {
        DashboardDataCache.cargadoEn = 1_700_000_000_000L
        DashboardDataCache.invalidar()

        assertEquals(0L, DashboardDataCache.cargadoEn)
        assertTrue(
            debeRecargarElInicio(
                hayDatos = true,
                cargadoEn = DashboardDataCache.cargadoEn,
                tickDeLaCarga = 1,
                tickActual = 1,
                reintento = false,
                ahora = 1_700_000_000_000L,
            ),
            "tras invalidar, la próxima entrada al Inicio recarga sí o sí",
        )
    }

    @Test
    fun invalidar_no_borra_los_datos_ya_pintados() {
        // Se invalida el SELLO, no la caché: el Inicio tiene que seguir pintando lo último que
        // sabía mientras llegan las diez respuestas, en vez de arrancar en blanco. Borrar `data`
        // acá traería de vuelta el «Tu plata $0» que la ola pasada costó arreglar.
        DashboardDataCache.data = DashboardData(accounts = emptyList())
        DashboardDataCache.invalidar()

        assertTrue(DashboardDataCache.data != null)
    }

    @Test
    fun cerrar_sesion_si_borra_todo() {
        // Lo cacheado es del usuario que se va.
        DashboardDataCache.data = DashboardData(accounts = emptyList())
        DashboardDataCache.cargadoEn = 123L
        DashboardDataCache.tickDeLaCarga = 7

        DashboardDataCache.clear()

        assertEquals(null, DashboardDataCache.data)
        assertEquals(0L, DashboardDataCache.cargadoEn)
        assertEquals(0, DashboardDataCache.tickDeLaCarga)
    }
}
