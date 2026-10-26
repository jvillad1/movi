package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * F42 · F46 — perfil editable. Antes `User` (ver `Auth.kt`) era lo único que existía y no había
 * ningún endpoint para leerlo ni cambiarlo fuera del login/registro; `GET /api/users/me` es la
 * primera vez que el cliente puede pedirlo de vuelta.
 *
 * `avatarColor` nunca es `null` en la respuesta del servidor: una cuenta sin color elegido
 * todavía cae a [AvatarPalette.DEFAULT] server-side (ver `UserRoutes.kt`) — el cliente no
 * necesita saber que la columna es nullable en la base.
 */
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val avatarColor: String,
    /**
     * Día en que arranca el período financiero del usuario (ver `PeriodSettings`).
     *
     * Nunca es `null` en la respuesta: una cuenta que no lo eligió cae a **1** —el mes de
     * calendario— del lado del servidor, así que el cliente no necesita saber que la columna es
     * nullable, igual que con `avatarColor`.
     */
    val periodCutoffDay: Int = 1,
    /**
     * **Los períodos que arrancaron otro día**: `"2026-09"` → la fecha ISO en que empezó de verdad.
     * Ver `PeriodSettings.iniciosPropios`, que es donde está el porqué completo.
     *
     * Vacío casi siempre, y vacío significa «todos salen del día de corte». Viaja en el perfil y no
     * en una ruta propia porque es parte de la misma pregunta que [periodCutoffDay] contesta —
     * cuándo empieza tu mes— y separarlos dejaría a un cliente capaz de leer una mitad sin la otra.
     */
    val periodStarts: Map<String, String> = emptyMap(),
    /**
     * **Qué hace el período cuando el día de corte cae en fin de semana**: el nombre de un
     * [SiElCorteCaeEnFinDeSemana]. Ver `PeriodSettings.siCaeEnFinDeSemana`.
     *
     * Un `String` y no el enum: un valor que un APK viejo no conozca no puede reventarle el perfil
     * entero. Se lee con [siElCorteCaeEnFinDeSemanaDe]; nunca `null` en la respuesta del server.
     */
    val periodWeekendRule: String = SiElCorteCaeEnFinDeSemana.ESE_DIA.name,
    /**
     * Días de anticipación del aviso de vencimiento. Nunca `null` en la respuesta: cae al default
     * del lado del server, igual que [avatarColor] y [periodCutoffDay].
     */
    val reminderLeadDays: Int = DEFAULT_REMINDER_LEAD_DAYS,
    /**
     * El dueño silenció el aviso del Inicio sobre la captura de SMS (ver [CapturaDeSms]). Nunca
     * `null` en la respuesta: una cuenta que no lo tocó cae a `false` del lado del server.
     */
    val smsAlertMuted: Boolean = false,
    /**
     * Cuántos días sin capturas de un origen que era regular avisan «banco mudo» (Ola 2). `0` = no
     * avisar. Nunca `null` en la respuesta: cae a [DIAS_PARA_BANCO_MUDO_POR_DEFECTO].
     */
    val diasParaBancoMudo: Int = DIAS_PARA_BANCO_MUDO_POR_DEFECTO,
    /**
     * **El resumen de cierre del período por correo** («Cerró tu período»). Encendido por defecto; se
     * apaga desde Perfil → Avisos. Nunca `null` en la respuesta: una cuenta que no lo tocó cae a
     * `true` del lado del server.
     */
    val cierrePorCorreo: Boolean = true,
    /**
     * **Cuándo terminó (o descartó) la guía «Primer uso»**, en milisegundos; `null` = nunca. Lo
     * escribe `POST /api/users/me/primer-uso` y vive en el server para que la guía no reaparezca
     * en otro aparato. Ver [debeAbrirseLaGuiaDePrimerUso] para quién la ve.
     */
    val primerUsoEn: Long? = null,
)

/**
 * **¿Se abre sola la guía «Primer uso»?** Solo si nunca se terminó ni se descartó
 * ([UserProfile.primerUsoEn] `null`) **y** el usuario todavía no tiene ninguna cuenta de dinero.
 *
 * La segunda mitad es la que deja afuera a quien ya usa Movi sin tocar una sola fila: el dueño y
 * cualquier cuenta vieja tienen cuentas, así que no ven la guía aunque su columna esté vacía. No
 * hace falta una migración que la marque.
 *
 * [cuentas] `null` = todavía no se sabe (la lectura no contestó): no se abre. Mejor tarde que
 * tapar el Inicio de alguien que sí tenía cuentas.
 */
fun debeAbrirseLaGuiaDePrimerUso(perfil: UserProfile?, cuentas: List<Account>?): Boolean {
    if (perfil == null || cuentas == null) return false
    if (perfil.primerUsoEn != null) return false
    return cuentas.none { it.type in TIPOS_DE_CUENTA_DE_DINERO }
}

/** Los tipos que cuentan como «cuenta de dinero» para la guía: plata que se usa, no deuda ni inversión. */
val TIPOS_DE_CUENTA_DE_DINERO: Set<AccountType> =
    setOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH)

/**
 * `GET /api/correo-entrante/direccion`: a qué dirección reenviar los correos del banco. [direccion]
 * es `null` si el server no tiene configurada la base (`INBOUND_EMAIL_ADDRESS`); [configurado] dice
 * si el correo entrante está encendido.
 */
@Serializable
data class DireccionDeCorreo(
    val token: String,
    val direccion: String? = null,
    val configurado: Boolean,
)

/**
 * `PUT /api/users/me`. Ambos campos opcionales — mandar solo `name` no toca el color, y
 * viceversa. `name` recortado y no vacío, máx 100 caracteres; `avatarColor` tiene que ser uno
 * de [AvatarPalette.COLORS] — el servidor es la autoridad, esto es solo lo que se manda.
 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val avatarColor: String? = null,
    /** Día de corte del período, 1..31. `null` = no tocar. */
    val periodCutoffDay: Int? = null,
    /**
     * Los períodos con arranque propio, **el mapa completo**: lo que venga reemplaza lo que había.
     * `null` = no tocar.
     *
     * Se manda entero y no de a una entrada porque quitar un arranque propio —«este mes sí empezó
     * cuando siempre»— es tan necesario como ponerlo, y con un formato incremental habría que
     * inventar cómo se dice «borrá esta clave». Un mapa vacío es exactamente eso: ninguno.
     */
    val periodStarts: Map<String, String>? = null,
    /**
     * Qué hace el período cuando el corte cae en fin de semana: el nombre de un
     * [SiElCorteCaeEnFinDeSemana]. `null` = no tocar; un nombre que no existe se rechaza.
     */
    val periodWeekendRule: String? = null,
    /** Días de aviso, 0..30. `null` = no tocar. */
    val reminderLeadDays: Int? = null,
    /**
     * Silenciar (o volver a mostrar) el aviso del Inicio sobre la captura de SMS. `null` = no
     * tocar.
     *
     * Se manda desde «Mensajes del banco», que es donde el hecho está a la vista, y no desde
     * Perfil: quien decide callar el recordatorio tiene que estar viendo lo que se calla.
     */
    val smsAlertMuted: Boolean? = null,
    /** Días para el aviso de banco mudo, 0..[MAX_DIAS_PARA_BANCO_MUDO] (0 lo apaga). `null` = no tocar. */
    val diasParaBancoMudo: Int? = null,
    /** Mandar (o no) el resumen de cierre del período por correo. `null` = no tocar. */
    val cierrePorCorreo: Boolean? = null,
)

/**
 * `PUT /api/users/me/password`. [current] se verifica contra el hash guardado; [new] pasa por
 * [PasswordPolicy] en el servidor ANTES de hashearse — la validación del cliente es cortesía.
 */
@Serializable
data class ChangePasswordRequest(
    val current: String,
    val new: String,
)
