package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.ActividadDelServidor
import com.jvillada.movi.server.db.AlertasDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.MetricasDeServicios
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.observabilidad.VolcadoDeMetricas.resumirLaHora
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

/**
 * # Cuánto dura cada cosa (vuelta 102)
 *
 * - Lo de **un minuto**: 30 días.
 * - Lo resumido **por hora**: 12 meses (365 días). La IA por día, igual.
 * - Las **corridas** de los jobs: 90 días.
 * - Los candados de las alertas: un día (después de una hora ya no traban nada).
 *
 * Corre en su propio bucle (`startVolcadoDeMetricas`, el job `retencion-de-metricas`), cada 12 horas.
 * El corte de los minutos va **por horas enteras**. Antes de borrar rehace las horas de los dos días que
 * están por salir: el volcado ya las armó al cerrarlas, y esto es la red por si alguna quedó a medias.
 */
object RetencionDeMetricas {
    const val DIAS_DE_MINUTOS = 30L
    const val DIAS_DE_HORAS = 365L
    const val DIAS_DE_CORRIDAS = 90L
    private const val UN_DIA = 86_400_000L

    data class Borrado(val minutos: Int, val horas: Int, val dias: Int, val corridas: Int)

    suspend fun correr(ahora: Long = Metricas.reloj()): Borrado = dbQuery {
        // **Alineado a la hora**: se borran horas enteras de minutos, nunca media. Una hora rehecha
        // con la mitad de sus minutos ya borrados reemplazaría su resumen por uno a medias.
        val corteMinutos = Metricas.horaDe(ahora - DIAS_DE_MINUTOS * UN_DIA)
        val corteHoras = ahora - DIAS_DE_HORAS * UN_DIA
        val corteCorridas = ahora - DIAS_DE_CORRIDAS * UN_DIA

        // La red: las horas de los minutos que se van a borrar, rehechas una vez más. Como el corte
        // va por horas enteras, una hora de esta ventana tiene todos sus minutos o ninguno, y una sin
        // minutos no se toca (`resumirLaHora` solo reemplaza lo que encuentra).
        var hora = corteMinutos - 2 * UN_DIA
        while (hora < corteMinutos) {
            resumirLaHora(hora)
            hora += Metricas.UNA_HORA
        }

        var minutos = 0
        var horas = 0
        minutos += MetricasDeRutas.deleteWhere { (escala eq ESCALA_MINUTO) and (inicio less corteMinutos) }
        minutos += MetricasDeServicios.deleteWhere { (escala eq ESCALA_MINUTO) and (inicio less corteMinutos) }
        minutos += ActividadDelServidor.deleteWhere { (escala eq ESCALA_MINUTO) and (inicio less corteMinutos) }
        horas += MetricasDeRutas.deleteWhere { (escala eq ESCALA_HORA) and (inicio less corteHoras) }
        horas += MetricasDeServicios.deleteWhere { (escala eq ESCALA_HORA) and (inicio less corteHoras) }
        horas += ActividadDelServidor.deleteWhere { (escala eq ESCALA_HORA) and (inicio less corteHoras) }
        val diaDeCorte = Metricas.diaDe(corteHoras)
        val dias = IaPorDia.deleteWhere { dia less diaDeCorte }
        val corridas = CorridasDeJobs.deleteWhere { inicio less corteCorridas }
        AlertasDelServidor.deleteWhere { enviadaEn less (ahora - UN_DIA) }
        Borrado(minutos, horas, dias, corridas)
    }
}
