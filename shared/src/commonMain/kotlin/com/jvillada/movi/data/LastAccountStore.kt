package com.jvillada.movi.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **La última cuenta en la que el dueño anotó algo**, para que «Agregar» arranque ahí.
 *
 * El problema que resuelve: con varias cuentas, la hoja de Agregar preseleccionaba
 * `accounts.first()` — o sea, la primera que devolviera el server. Y «la primera» no estaba
 * definida en ninguna parte: ni `GET /api/accounts` ni el `selectAll` de SQLDelight tenían
 * `ORDER BY`, y sin él ningún motor promete un orden. La cuenta preseleccionada podía cambiar
 * sin que nada cambiara a la vista, y la nómina se anotaba en la cuenta equivocada.
 *
 * Esta rama le puso orden a las dos consultas (está detallado en `Account.sq` y en
 * `AccountRoutes.kt`, incluido el descalce de mayúsculas entre SQLite y Postgres, que era el
 * caso que de verdad le cambiaba la cuenta al dueño entre el teléfono y la web) **y además**
 * deja de depender del orden: el valor por defecto pasa a ser una decisión con nombre —«la
 * última que usaste»— en vez de un accidente del motor de base de datos.
 *
 * **Por qué recordar y no preguntar.** Casi todo el mundo mete casi todo en la misma cuenta.
 * Un selector obligatorio antes de cada gasto sería un peaje diario por una respuesta que casi
 * siempre es la misma. La cuenta sigue estando a un toque de cambiarse (la fila «Cuenta»), y
 * ahora la hoja además **dice** de dónde salió el valor que muestra (ver `avisoDeCuenta`).
 *
 * **Los traspasos guardan su propio par y no tocan [lastAccountId].** Un traspaso mueve plata
 * entre DOS cuentas: elegir una de ellas como «la última usada» para el próximo gasto sería
 * inventar una preferencia que el dueño nunca expresó. Y al revés, el par origen→destino de un
 * traspaso sí se repite (siempre el mismo banco al mismo bolsillo), así que vale la pena
 * recordarlo aparte.
 *
 * **Nada de esto puede tumbar el arranque.** Es la lección de la ola pasada, cuando la primera
 * versión de las preferencias de categoría se llevó puesta la suite entera con un
 * `ExceptionInInitializerError`: un `Settings` que no se puede construir (un test de JVM pura,
 * el almacenamiento del navegador bloqueado) explota **al construirse**, no al leerse, así que
 * la construcción tiene que quedar adentro del `runCatching` — por eso el `by lazy` de abajo,
 * que difiere la construcción hasta la primera lectura protegida. Si algo falla, esto vale
 * `null` y la hoja cae en la primera cuenta de la lista, que es exactamente el comportamiento
 * de antes de esta rama. Es una comodidad, no una fuente de verdad.
 *
 * Contraejemplo vivo en el repo, y a propósito no imitado: `SessionManager` lee su token en el
 * inicializador de su `object` y sin protección.
 */
private const val KEY_LAST_ACCOUNT = "last_account_id"
private const val KEY_LAST_TRANSFER_FROM = "last_transfer_from_id"
private const val KEY_LAST_TRANSFER_TO = "last_transfer_to_id"

/**
 * Top-level y `by lazy`, igual que en `UsedCategoriesCache` y por los dos motivos de allá: una
 * propiedad declarada dentro del `object` todavía valdría `null` mientras corre el
 * inicializador que la usa, y `Settings()` no se construye hasta que alguien lea —siempre
 * desde adentro de un `runCatching`.
 */
private val accountSettings: Settings by lazy { Settings() }

object LastAccountStore {
    /** La cuenta del último movimiento (gasto o ingreso) que se guardó bien. */
    var lastAccountId: String? = leer(KEY_LAST_ACCOUNT)
        private set

    /** El origen del último traspaso que se guardó bien. */
    var lastTransferFromId: String? = leer(KEY_LAST_TRANSFER_FROM)
        private set

    /** El destino del último traspaso que se guardó bien. */
    var lastTransferToId: String? = leer(KEY_LAST_TRANSFER_TO)
        private set

    /**
     * Se llama **después** de que el POST salió bien, nunca antes: un guardado que falló no
     * cambió de cuenta a nadie, y mover el valor por defecto por un intento fallido dejaría la
     * próxima apertura arrancando en una cuenta que nunca se usó.
     */
    fun recordAccount(id: String?) {
        val limpio = id?.takeIf { it.isNotBlank() } ?: return
        lastAccountId = limpio
        guardar(KEY_LAST_ACCOUNT, limpio)
    }

    /** Ídem para el par de un traspaso. Ver el KDoc de arriba: no toca [lastAccountId]. */
    fun recordTransfer(fromId: String?, toId: String?) {
        val origen = fromId?.takeIf { it.isNotBlank() }
        val destino = toId?.takeIf { it.isNotBlank() }
        if (origen == null || destino == null) return
        lastTransferFromId = origen
        lastTransferToId = destino
        guardar(KEY_LAST_TRANSFER_FROM, origen)
        guardar(KEY_LAST_TRANSFER_TO, destino)
    }

    /**
     * Al cerrar sesión: estas son las cuentas del usuario que se va (ver `SessionManager.clear`).
     * Un id que sobreviviera al logout no rompería nada —la hoja descarta el que no esté en la
     * lista— pero seguiría siendo un dato del anterior guardado en este dispositivo.
     */
    fun clear() {
        lastAccountId = null
        lastTransferFromId = null
        lastTransferToId = null
        guardar(KEY_LAST_ACCOUNT, null)
        guardar(KEY_LAST_TRANSFER_FROM, null)
        guardar(KEY_LAST_TRANSFER_TO, null)
    }

    private fun leer(key: String): String? =
        runCatching { accountSettings.getStringOrNull(key)?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun guardar(key: String, value: String?) {
        runCatching {
            if (value.isNullOrBlank()) accountSettings.remove(key) else accountSettings[key] = value
        }
    }
}
