package com.jvillada.movi.avisos

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jvillada.movi.app.R
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.notificaciones.BOTON_ACTIVAR_EL_ACCESO
import com.jvillada.movi.notificaciones.TEXTO_DEL_ACCESO_REVOCADO
import com.jvillada.movi.notificaciones.TITULO_DEL_ACCESO_REVOCADO
import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.marcaDelAviso
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **Quien hace sonar el teléfono** (Ola 1 · Movi avisa). Lo que dicen los avisos y cuándo se decide
 * en `:shared` (`AvisosDelTelefono.kt`, puro y probado); acá solo hay canales, `NotificationCompat`
 * y el `Intent` que abre la pantalla al tocar.
 *
 * Canales separados, con nombre en español, para que el dueño pueda silenciar uno sin el otro
 * desde los ajustes del sistema: los movimientos pueden llegar diez veces al día, los vencimientos
 * una. Todas las notificaciones salen de [publicar], con el mismo ícono (`ic_stat_movi`) y el mismo
 * acento (`movi_tinta`); `ElIconoDeMoviTest` lo vigila.
 */
object Avisador {
    private const val TAG = "movi"

    const val CANAL_MOVIMIENTOS = "movimientos"
    const val CANAL_VENCIMIENTOS = "vencimientos"
    const val CANAL_CAPTURA = "captura"
    const val CANAL_DEBITOS = "debitos"
    const val CANAL_CIERRE = "cierre"
    const val CANAL_RESUMEN_DE_LA_MANANA = "resumen_de_la_manana"
    const val CANAL_RESUMEN_SEMANAL = "resumen_semanal"
    const val CANAL_EXTRACTO = "extracto_por_subir"

    /** Un id fijo por tipo: la notificación de «Por revisar» se reemplaza, no se apila. */
    private const val ID_MOVIMIENTOS = 7101
    private const val ID_VENCIMIENTOS = 7102
    private const val ID_BANCO_MUDO = 7103
    private const val ID_DEBITOS = 7104
    private const val ID_CIERRE = 7105
    private const val ID_SESION_CERRADA = 7106
    private const val ID_RESUMEN_DE_LA_MANANA = 7107
    private const val ID_RESUMEN_SEMANAL = 7108
    private const val ID_EXTRACTO = 7109

    /** Vuelta 92 · «Movi dejó de leer las notificaciones del banco» (y su botón «Activar»). */
    private const val ID_ACCESO_REVOCADO = 7120
    private const val CODIGO_ACTIVAR_EL_ACCESO = 7121

    /** Los `requestCode` de los botones de la notificación de un aviso: distintos del de tocarla. */
    private const val CODIGO_OTRA_CATEGORIA = 7111
    private const val CODIGO_REVISAR = 7112

    /** Cuánto se espera la propuesta del aviso antes de publicar sin «Confirmar». */
    private const val MILIS_PARA_LEER_LA_PROPUESTA = 8_000L

    /**
     * Idempotente: Android ignora crear un canal que ya existe y solo actualiza su nombre y su
     * descripción, así que un cambio de texto acá llega a los teléfonos que ya los tenían.
     *
     * Vuelta 65: los nombres hablan como la app de hoy (Actividad, Pagos, Inicio) y tutean. Los ids
     * no cambian nunca: un id nuevo sería un canal nuevo y el dueño perdería lo que ya silenció.
     */
    fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_MOVIMIENTOS, "Por revisar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando un aviso de tu banco queda en «Por revisar», en Actividad."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_VENCIMIENTOS, "Pagos por vencer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Los pagos de tu período que vencen hoy o mañana y todavía no pagaste."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_CAPTURA, "Captura del banco", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando un banco que siempre te avisaba lleva días sin mandar nada, o Movi dejó de recibir tus avisos."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_DEBITOS, "Débitos automáticos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "El día que tu banco debió cobrar solo una cuota o un pago, para que confirmes si se cobró."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_CIERRE, "Cierre del período", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "El día siguiente al corte, cómo te fue en el período que terminó."
            },
        )
        // Vuelta 68: los dos resúmenes, cada uno en su canal para silenciarlo aparte.
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_RESUMEN_DE_LA_MANANA, "Resumen de la mañana", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A las 8:00, cuánto gastaste ayer, cuánto te queda y lo que espera por revisar."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_RESUMEN_SEMANAL, "Resumen semanal", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "El domingo en la noche, cuánto gastaron juntos en tus grupos y cómo quedan las cuentas."
            },
        )
        // Vuelta 94.
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_EXTRACTO, "Extractos por subir", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Unos días después del corte de una tarjeta, si todavía no subiste su extracto."
            },
        )
    }

    /**
     * Avisa que llegaron [nuevos] a «Por revisar», agrupados con los que ya decía la notificación
     * si todavía está a la vista ([acumularMovimientos]). No hace nada con el interruptor apagado
     * o sin permiso.
     *
     * Ola 5 · sin abrir la app: con **un** movimiento, la notificación dice «Rappi · $27.500» y
     * «Restaurantes · Nu», y si Movi lo entendió entero ([botonesDelAviso]) ofrece «Confirmar» y
     * «Otra categoría»; con dudas, solo «Revisar». Para saberlo lee la propuesta de la bandeja (el
     * Worker que llama ya está con red); si esa lectura falla, la notificación sale igual, sin
     * «Confirmar». Con varios, el resumen de siempre, sin botones de confirmar.
     */
    suspend fun avisarMovimientos(context: Context, nuevos: List<AvisoPorRevisar>) {
        if (nuevos.isEmpty()) return
        if (!PreferenciasDeAvisos.avisarMovimientos(context) || !puedeAvisar(context)) return
        val todos = acumularMovimientos(
            previos = PreferenciasDeAvisos.agrupados(context),
            nuevos = nuevos,
            sigueVisible = sigueALaVista(context, ID_MOVIMIENTOS),
        )
        PreferenciasDeAvisos.guardarAgrupados(context, todos)
        val ocultos = saldosOcultos()
        val uno = todos.singleOrNull()
        if (uno != null) {
            avisarUno(context, uno, ocultos)
            return
        }
        val texto = textoDeMovimientos(todos, ocultos) ?: return
        publicar(context, ID_MOVIMIENTOS, CANAL_MOVIMIENTOS, texto, ABRIR_POR_REVISAR, cuantos = todos.size)
    }

    /** La notificación de un solo aviso, con sus botones (ver [avisarMovimientos]). */
    private suspend fun avisarUno(context: Context, aviso: AvisoPorRevisar, ocultos: Boolean) {
        val ahora = System.currentTimeMillis()
        val entendido = try {
            withTimeoutOrNull(MILIS_PARA_LEER_LA_PROPUESTA) { leerLoQueMoviEntendioParaLaNotificacion(aviso.id, ahora) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer la propuesta del aviso; sale sin «Confirmar»", e)
            null
        }
        val botones = botonesDelAviso(1, entendido)
        val anotacion = entendido?.anotacion?.takeIf { botones == BotonesDelAviso.CONFIRMAR_U_OTRA_CATEGORIA }
        val instantanea = runCatching { InstantaneaDelInicio.delAparato }.getOrNull()
        val usuario = SessionManager.userId
        val llevas = runCatching {
            lineaDeLlevasHoy(instantanea?.datos(usuario), instantanea?.selladaEn(usuario), aviso, ahora, ocultos)
        }.getOrNull()
        val texto = textoDelAvisoSuelto(aviso, anotacion, llevas, ocultos)
        // Ola 2a: la marca dibujada del comercio (círculo y letra, del catálogo propio: nada sale del teléfono).
        val marca = marcaDelAviso(aviso.descripcion, aviso.origen)
        val icono = marca?.let { runCatching { iconoDeMarca(context, it) }.getOrNull() }
        val acciones = if (anotacion != null) {
            listOf(
                NotificationCompat.Action(
                    0,
                    BOTON_CONFIRMAR,
                    ReceptorDeLaNotificacion.alConfirmar(
                        context,
                        aviso.id,
                        LoQueSeMostro(categoria = anotacion.categoria, cuentaId = anotacion.cuenta.id, monto = anotacion.monto),
                    ),
                ),
                NotificationCompat.Action(0, BOTON_OTRA_CATEGORIA, abrirLaApp(context, abrirElAvisoEnLaCategoria(aviso.id), CODIGO_OTRA_CATEGORIA)),
            )
        } else {
            listOf(NotificationCompat.Action(0, BOTON_REVISAR, abrirLaApp(context, abrirElAviso(aviso.id), CODIGO_REVISAR)))
        }
        publicar(context, ID_MOVIMIENTOS, CANAL_MOVIMIENTOS, texto, ABRIR_POR_REVISAR, cuantos = 1, icono = icono, acciones = acciones)
    }

    /**
     * Lo que dice la notificación de [avisoId] mientras, y después de, tocar «Confirmar»: «Anotando…»,
     * «Anotado ✓» (se cierra sola) o «No se pudo, ábrelo en Movi» (tocarla abre el aviso). Sin sonido:
     * es la misma notificación cambiando, no una nueva. Si mientras tanto llegaron otros avisos, la
     * notificación ya es el resumen y no se toca: solo se saca de la cuenta el que se anotó.
     */
    fun mostrarConfirmacion(context: Context, avisoId: String, estado: EstadoDeLaConfirmacion) {
        if (estado != EstadoDeLaConfirmacion.ANOTANDO) {
            val quedan = PreferenciasDeAvisos.agrupados(context).filterNot { it.id == avisoId }
            val eraElUnico = PreferenciasDeAvisos.agrupados(context).let { it.isEmpty() || it.singleOrNull()?.id == avisoId }
            if (estado == EstadoDeLaConfirmacion.ANOTADO) PreferenciasDeAvisos.guardarAgrupados(context, quedan)
            if (!eraElUnico) return
        }
        val (titulo, abrir) = when (estado) {
            EstadoDeLaConfirmacion.ANOTANDO -> AVISO_ANOTANDO to abrirElAviso(avisoId)
            EstadoDeLaConfirmacion.ANOTADO -> AVISO_ANOTADO to ABRIR_POR_REVISAR
            EstadoDeLaConfirmacion.NO_SE_PUDO -> AVISO_NO_SE_PUDO to abrirElAviso(avisoId)
        }
        publicar(
            context,
            ID_MOVIMIENTOS,
            CANAL_MOVIMIENTOS,
            TextoDeAviso(titulo = titulo, texto = if (estado == EstadoDeLaConfirmacion.NO_SE_PUDO) "Sigue en «Por revisar»" else ""),
            abrir,
            cuantos = 1,
            silenciosa = true,
            cerrarEn = if (estado == EstadoDeLaConfirmacion.ANOTADO) MILIS_DE_ANOTADO_A_LA_VISTA else null,
        )
    }

    /** Los tres momentos de un «Confirmar» tocado en la notificación. */
    enum class EstadoDeLaConfirmacion { ANOTANDO, ANOTADO, NO_SE_PUDO }

    /** «Ocultar los saldos» de este aparato; si no se puede leer, se oculta (lo seguro). */
    private fun saldosOcultos(): Boolean = runCatching { SaldosOcultosStore.ocultos }.getOrDefault(true)

    /** Abre la app con [abrir] (ver `destinoDeAviso`), por la puerta de siempre. */
    private fun abrirLaApp(context: Context, abrir: String, codigo: Int): PendingIntent? {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null)
            .putExtra(EXTRA_ABRIR, abrir)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, codigo, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Avisa los vencimientos de hoy y mañana con [texto] (ver [textoDeVencimientos]). */
    fun avisarVencimientos(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_VENCIMIENTOS, CANAL_VENCIMIENTOS, texto, ABRIR_PLAN, cuantos = texto.lineas.size)
    }

    /** Ola 2: avisa que un banco dejó de mandar avisos (ver `textoDeBancosMudos`). Tocarla abre la captura. */
    fun avisarBancoMudo(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_BANCO_MUDO, CANAL_CAPTURA, texto, ABRIR_CAPTURA, cuantos = maxOf(1, texto.lineas.size))
    }

    /** Avisa lo que el banco debió cobrar solo (ver `textoDeDebitosAutomaticos`). Tocarla abre «Por revisar». */
    fun avisarDebitosAutomaticos(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_DEBITOS, CANAL_DEBITOS, texto, ABRIR_POR_REVISAR, cuantos = maxOf(1, texto.lineas.size))
    }

    /**
     * El server cerró la sesión: la captura del banco dejó de subir. Una sola notificación (mismo id:
     * otro 401 la reemplaza, no la apila) que abre Movi —en el login, porque ya no hay sesión— y
     * después de entrar, «Captura del banco». Se quita sola al entrar ([quitarSesionCerrada]).
     */
    fun avisarSesionCerrada(context: Context) {
        publicar(context, ID_SESION_CERRADA, CANAL_CAPTURA, TEXTO_DE_LA_SESION_CERRADA, ABRIR_CAPTURA, cuantos = 1, silenciosa = sigueALaVista(context, ID_SESION_CERRADA))
    }

    fun quitarSesionCerrada(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_SESION_CERRADA) }
    }

    /**
     * Vuelta 92 · el dueño (o Android) revocó el acceso a las notificaciones: la captura del banco
     * por notificaciones se apagó. UNA notificación —la vigía no la repite con la misma huella, ver
     * `queHaceLaVigia`— que abre Captura del banco, con «Activar», que lleva directo a la pantalla
     * del sistema donde se concede. Se quita sola cuando el listener vuelve a conectarse.
     */
    fun avisarAccesoRevocado(context: Context) {
        val activar = PendingIntent.getActivity(
            context,
            CODIGO_ACTIVAR_EL_ACCESO,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        publicar(
            context,
            ID_ACCESO_REVOCADO,
            CANAL_CAPTURA,
            TextoDeAviso(titulo = TITULO_DEL_ACCESO_REVOCADO, texto = TEXTO_DEL_ACCESO_REVOCADO),
            ABRIR_CAPTURA,
            cuantos = 1,
            acciones = listOf(NotificationCompat.Action(0, BOTON_ACTIVAR_EL_ACCESO, activar)),
        )
    }

    fun quitarAccesoRevocado(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_ACCESO_REVOCADO) }
    }

    /** «Cerró tu período de septiembre»: tocarlo abre el detalle de ese período. */
    fun avisarCierre(context: Context, periodo: String, texto: TextoDeAviso) {
        publicar(context, ID_CIERRE, CANAL_CIERRE, texto, abrirElPeriodo(periodo), cuantos = 1)
    }

    /** Vuelta 94: el extracto de una tarjeta que falta subir. Tocarlo abre el Inicio, donde se sube. */
    fun avisarExtracto(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_EXTRACTO, CANAL_EXTRACTO, texto, ABRIR_INICIO, cuantos = maxOf(1, texto.lineas.size))
    }

    /** Vuelta 68 · el resumen de la mañana: tocarlo abre el Inicio. */
    fun avisarResumenDeLaManana(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_RESUMEN_DE_LA_MANANA, CANAL_RESUMEN_DE_LA_MANANA, texto, ABRIR_INICIO, cuantos = 1)
    }

    /** Vuelta 68 · el resumen semanal de los grupos: tocarlo abre [abrir] (el grupo, o la lista). */
    fun avisarResumenSemanal(context: Context, texto: TextoDeAviso, abrir: String) {
        publicar(context, ID_RESUMEN_SEMANAL, CANAL_RESUMEN_SEMANAL, texto, abrir, cuantos = maxOf(1, texto.lineas.size))
    }

    /**
     * ¿La notificación [id] sigue en la barra? Si el dueño la tocó o la descartó, ya no: lo
     * próximo empieza un grupo nuevo. `activeNotifications` es de Android 6; antes, se asume que no.
     */
    private fun sigueALaVista(context: Context, id: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { gestor.activeNotifications.any { it.id == id } }.getOrDefault(false)
    }

    // `puedeAvisar` ya miró el permiso justo antes; el lint no lo ve a través de la función.
    @SuppressLint("MissingPermission")
    private fun publicar(
        context: Context,
        id: Int,
        canal: String,
        texto: TextoDeAviso,
        abrir: String,
        cuantos: Int,
        icono: Bitmap? = null,
        acciones: List<NotificationCompat.Action> = emptyList(),
        silenciosa: Boolean = false,
        cerrarEn: Long? = null,
    ) {
        if (!puedeAvisar(context)) return
        crearCanales(context)
        val alTocar = abrirLaApp(context, abrir, id) ?: return
        val estilo = if (texto.lineas.isNotEmpty()) {
            NotificationCompat.InboxStyle().also { inbox -> texto.lineas.forEach { inbox.addLine(it) } }
        } else {
            NotificationCompat.BigTextStyle().bigText(texto.texto)
        }
        val aviso = NotificationCompat.Builder(context, canal)
            // Vuelta 65: la «m» del ícono, y la tinta de acento. La tinta y no el amarillo: desde
            // Android 12 el acento es el círculo detrás de la «m» blanca, y blanco sobre amarillo
            // no se lee (1,6:1); blanco sobre tinta sí (16:1), en la cortina clara y en la oscura.
            // En Android 8-11 el acento tiñe la «m» y el nombre de la app, y el sistema lo aclara
            // solo en la cortina oscura. Todos los avisos pasan por acá: un solo ícono y un color.
            .setSmallIcon(R.drawable.ic_stat_movi)
            .setColor(ContextCompat.getColor(context, R.color.movi_tinta))
            .setLargeIcon(icono)
            .setContentTitle(texto.titulo)
            .setContentText(texto.texto)
            .setStyle(estilo)
            .setNumber(cuantos)
            .setContentIntent(alTocar)
            .setAutoCancel(true)
            // Montos y nombres de destinatarios: en la pantalla bloqueada, solo que hay un aviso.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(if (canal == CANAL_VENCIMIENTOS || canal == CANAL_DEBITOS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(silenciosa)
            .apply {
                acciones.forEach { addAction(it) }
                if (silenciosa) setSilent(true)
                cerrarEn?.let { setTimeoutAfter(it) }
            }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, aviso) }
            .onFailure { Log.w(TAG, "no se pudo publicar el aviso $id", it) }
    }
}
