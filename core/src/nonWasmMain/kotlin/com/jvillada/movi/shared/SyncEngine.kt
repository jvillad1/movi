package com.jvillada.movi.shared

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class SyncEngine(
    private val db: MoviDatabase,
    private val remote: WalletRepository,
    private val userId: () -> String,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        scope.launch {
            while (true) {
                delay(30_000L)
                // syncAccounts ANTES que syncEvents: una cuenta creada offline (ver
                // LocalRepository.createAccount) tiene que existir en el server antes que sus
                // eventos, o EventRoutes.kt POST la rechaza con 404 "Account not found" — y ese
                // 404 quedaba tragado en silencio por el catch de syncEvents, así que el evento
                // (y la cuenta) nunca llegaban al server aunque hubiera red.
                try { syncAccounts() } catch (e: Exception) { logSyncFailure("syncAccounts", e) }
                try { syncEvents() } catch (e: Exception) { logSyncFailure("syncEvents", e) }
                try { syncVoids() } catch (e: Exception) { logSyncFailure("syncVoids", e) }
            }
        }
    }

    /**
     * Empuja las cuentas creadas offline (`syncedAt IS NULL`, ver [com.jvillada.movi.shared.repository.LocalRepository.createAccount])
     * y sella las que llegaron. Corre antes que [syncEvents] en [start] a propósito: los eventos
     * de una cuenta todavía no sincronizada rebotan con 404 contra el server (ver arriba) — y ese
     * orden es lo que garantiza que el evento de apertura (creado en el cliente, ver
     * [com.jvillada.movi.shared.model.openingEventFor]) llegue al server DESPUÉS de que la cuenta
     * exista, no antes.
     *
     * `row.balance` se manda tal cual, aunque ya no sea $0 (una cuenta con eventos posteados
     * antes del primer sync termina con `row.balance` movido por esos eventos, vía
     * [com.jvillada.movi.shared.repository.LocalRepository.postEvent]) — y eso ya no puede
     * duplicar nada: desde la Ola 1b, `POST /api/accounts` NO convierte ese balance en un evento
     * (ver `AccountRoutes.kt`, server); la columna cruda `accounts.balance` no se lee para
     * derivar nada, así que mandarla es inofensivo. Antes de este fix, si esta cuenta tenía un
     * ingreso de $50.000 anotado offline, `row.balance` llegaba en $50.000, el server lo convertía
     * en un evento de apertura de $50.000, y el ingreso real que `syncEvents` empuja a
     * continuación se sumaba ENCIMA — $100.000 en el server, el doble del real. Ahora el único
     * evento de apertura es el que el cliente ya creó, explícito, en `CreateAccountSheet.kt`.
     *
     * Igual que [syncEvents]: si `remote.createAccount` falla (sin red, o el server la rechaza)
     * la fila se queda sin sellar y el próximo ciclo la vuelve a intentar — sin distinguir esos
     * dos casos, mismo trade-off documentado en el KDoc de `LocalRepository.createAccount`.
     */
    internal suspend fun syncAccounts() {
        val unsynced = db.accountQueries.selectUnsynced(userId()).executeAsList()
        for (row in unsynced) {
            try {
                val created = remote.createAccount(
                    Account(
                        id = row.id, name = row.name,
                        type = AccountType.valueOf(row.type),
                        balance = row.balance, currency = row.currency,
                        // **La condición de uso viaja con la cuenta.** Se guarda local al crearla
                        // (`LocalRepository.createAccount`) y acá se quedaba afuera: una cuenta
                        // creada sin señal y marcada «solo para vivienda» llegaba al server sin la
                        // marca, y el Inicio volvía a sumar esa plata como disponible. Ver
                        // `Account.condicionadaA`.
                        condicionadaA = row.conditionedTo,
                        // **Y la edad de esta versión.** Es lo que le deja al server decidir la
                        // carrera contra una corrección hecha en la web: `null` dice «esta copia es
                        // la original» (y pierde contra lo que el server tenga editado), un instante
                        // dice «la corregí sin señal» (y gana). Ver `Account.lastEditedAt` y
                        // `pisaElReenvio` en EventRoutes.kt.
                        lastEditedAt = row.lastEditedAt,
                        // **Y si es un bien, el bien.** Una casa cargada sin señal que llegara al
                        // server sin esto quedaría como una inversión en $0: el patrimonio la
                        // perdería entera. `null` no viaja (encodeDefaults apagado), así que una
                        // cuenta que no es bien sale igual que siempre.
                        bien = row.bienClase?.let { clase ->
                            com.jvillada.movi.shared.model.Bien(
                                clase = clase,
                                valor = row.bienValor ?: 0L,
                                valorAl = row.bienValorAl,
                                deudaId = row.bienDeudaId,
                            )
                        },
                    )
                )
                db.accountQueries.markSynced(Clock.System.now().toEpochMilliseconds(), created.id)
            } catch (e: Exception) {
                logSyncFailure("syncAccounts", e, id = row.id)
            }
        }
    }

    /**
     * Empuja los eventos pendientes y sella los que llegaron con `markSyncedIfUnchanged`.
     *
     * `markSyncedIfUnchanged`, no `markSynced` a secas: entre el SELECT de acá arriba y el
     * `postEvent` (una llamada de red, sin ningún lock sobre la fila mientras está en vuelo) la
     * categoría puede cambiar por otro camino — [com.jvillada.movi.shared.repository.LocalRepository.updateEventCategory]
     * resuelve local cuando `syncedAt` sigue null, que es exactamente la ventana en la que este
     * ciclo está trabajando. Si esta función sellara con `markSynced` a secas usando el snapshot
     * viejo (`row.category`), la fila quedaría "sincronizada" con la categoría vieja en el server
     * y la corregida solo en local — y como ya no sale en `selectUnsynced`, ningún ciclo futuro
     * la volvería a empujar: la divergencia sería silenciosa y permanente. `updateEventCategory`
     * revalida esa misma carrera del otro lado (adentro de su propia transacción), pero eso solo
     * cubre la mitad: la revalidación ve si SyncEngine YA selló antes de que ese código corriera,
     * no si SyncEngine va a sellar DESPUÉS con un snapshot desactualizado — que es este caso.
     *
     * **Lo mismo vale para la FECHA** desde que existe
     * [com.jvillada.movi.shared.repository.LocalRepository.updateEventTimestamp]: corregir la
     * fecha de un movimiento pendiente escribe solo en local, exactamente en la misma ventana.
     * Por eso `markSyncedIfUnchanged` compara las dos cosas (`AND category = … AND timestamp = …`)
     * y no solo la categoría — con una sola de las dos condiciones, el agujero seguía abierto
     * para la otra.
     *
     * **Y lo mismo, otra vez, para el MONTO, la CUENTA y el CONCEPTO** desde que
     * [com.jvillada.movi.shared.repository.LocalRepository.updateEvent] los hace editables: los
     * tres se corrigen solo en local mientras el evento está pendiente, en esta misma ventana.
     * La condición del UPDATE los incluye a los seis. Es el mismo error una vez por campo nuevo,
     * así que la regla es: **todo campo que `updateEvent` (o cualquier corrección local) pueda
     * tocar mientras `syncedAt` sigue en null tiene que entrar en este WHERE.**
     *
     * Con la condición `AND category = :category`, si la categoría cambió el UPDATE no toca
     * ninguna fila: `syncedAt` se queda en null y el próximo ciclo (30s) la vuelve a levantar de
     * `selectUnsynced`, esta vez con la categoría ya corregida. Nota: eso reintenta un
     * `postEvent` con un id que el server ya tiene — si el evento original sí llegó a insertarse,
     * ese reintento va a fallar (conflicto de id) y quedar atrapado por el catch de abajo,
     * reintentando en silencio cada ciclo. Es preferible a la alternativa (divergencia
     * silenciosa y PERMANENTE): acá el evento sigue visible en `selectUnsynced`, así que el
     * problema es diagnosticable. Arreglar ese reintento de raíz —enseñarle a SyncEngine a
     * distinguir "nunca llegó" de "ya llegó, solo cambió la categoría" y usar
     * `remote.updateEventCategory` en ese segundo caso— queda fuera de este fix.
     *
     * **Las patas de un traspaso nunca salen por acá.** `selectUnsynced` las excluye por SQL
     * (`transferId IS NULL`), y no es una optimización: este ciclo empuja **de a un evento**, así
     * que subir una pata sola dejaría medio traspaso en el server — plata saliendo de una cuenta
     * sin la que la compensa del otro lado. Por diseño ninguna pata debería estar pendiente
     * ([com.jvillada.movi.shared.repository.LocalRepository.createTransfer] es remote-first y las
     * espeja ya selladas), pero la condición es la red de seguridad de esa promesa para el caso
     * en que una fila quede a medio escribir o venga de una versión vieja de la app.
     */
    internal suspend fun syncEvents() {
        val unsynced = db.financialEventQueries.selectUnsynced(userId()).executeAsList()
        for (row in unsynced) {
            try {
                remote.postEvent(
                    FinancialEvent(
                        id = row.id, accountId = row.accountId,
                        type = TransactionType.valueOf(row.type),
                        amount = row.amount, category = row.category,
                        description = row.description, merchant = row.merchant,
                        timestamp = row.timestamp,
                        source = EventSource.valueOf(row.source),
                        rawPayload = row.rawPayload,
                        reconciliationStatus = ReconciliationStatus.valueOf(row.reconciliationStatus),
                        syncedAt = row.syncedAt,
                        // **El sello de creación viaja con el evento**, y es la razón por la que
                        // lo pone el cliente y no el server: este POST puede salir dos días
                        // después de que el dueño escribió el movimiento (el teléfono estuvo sin
                        // señal). Si el server lo estampara al recibir, ese movimiento quedaría
                        // «creado» dos días más tarde y saltaría al tope de su día. Ver
                        // FinancialEvent.createdAt.
                        createdAt = row.createdAt,
                        // «Este no se repite» marcado sin señal. No viajaba: el POST llegaba con el
                        // default y la marca se perdía al sincronizar.
                        noSeRepite = row.noSeRepite != 0L,
                        // La moneda, que tampoco viajaba: un gasto en dólares anotado sin señal
                        // llegaba como pesos. Ver migración 8.sqm.
                        currency = row.currency,
                        // **La edad de esta versión**, que es lo que decide quién gana cuando este
                        // reenvío llega a un id que el server ya tiene. `null` acá quiere decir
                        // «este teléfono no editó el movimiento desde que lo anotó», y el server
                        // lo lee así: la copia de la web, si el dueño la corrigió, es posterior y
                        // gana. Viaja siempre —clave presente, valga lo que valga—, que es lo que
                        // le permite al server distinguir esto de un APK viejo. Ver
                        // `FinancialEvent.lastEditedAt` y `pisaElReenvio` en EventRoutes.kt.
                        lastEditedAt = row.lastEditedAt,
                    )
                )
                db.financialEventQueries.markSyncedIfUnchanged(
                    Clock.System.now().toEpochMilliseconds(), row.id, row.category, row.timestamp,
                    // Y el monto, la cuenta y el concepto desde que se pueden corregir (ver
                    // `LocalRepository.updateEvent`): el mismo agujero que la categoría y la
                    // fecha ya tenían tapado, abierto por tres campos más.
                    row.amount, row.accountId, row.description, row.noSeRepite,
                    // Y el estado: confirmar un movimiento pendiente sin señal mientras el POST
                    // viajaba no puede quedar sellado como «por confirmar» en el server.
                    row.reconciliationStatus,
                )
                // Subió: se borra el rastro de los fallos anteriores —la cuenta y, si se había
                // llegado a escribir, el aviso—. Que este POST haya pasado es exactamente lo que
                // el aviso decía que no pasaba.
                db.financialEventQueries.limpiarElRastroDeFallos(row.id, row.userId)
            } catch (e: ApiException) {
                logSyncFailure("syncEvents", e, id = row.id)
                // Un rechazo del server (4xx que no es sesión vencida ni «demasiadas peticiones»):
                // reintentar solo no lo arregla. Se guarda el motivo para que Movimientos lo diga.
                // Salvo que la cuenta todavía no haya subido: ese 404 se arregla solo en el ciclo
                // siguiente, cuando syncAccounts la empuje.
                // **«Todavía no subió» y «no está acá» no son lo mismo**, y confundirlos tragaba
                // rechazos de verdad. Esto era `selectById(...)?.syncedAt == null`, y con el
                // operador seguro una fila AUSENTE también daba `null == null` = «no subió»: o
                // sea que cualquier 4xx de un movimiento cuya cuenta no está espejada en este
                // teléfono se descartaba sin escribir el `syncError`, y el aviso de Movimientos
                // no se encendía nunca para esos. Pasa de verdad: la cuenta puede haberse
                // borrado desde la web, o el espejo local puede no tenerla todavía.
                //
                // Solo la fila que EXISTE y está sin sellar merece el descarte, porque ese 404
                // se arregla solo en el ciclo siguiente cuando `syncAccounts` empuje la cuenta.
                // Si no está, nadie la va a empujar y callar el motivo no arregla nada.
                val filaDeLaCuenta = db.accountQueries.selectById(row.accountId).executeAsOneOrNull()
                val cuentaSinSubir = filaDeLaCuenta != null && filaDeLaCuenta.syncedAt == null
                if (e.status in 400..499 && e.status != 401 && e.status != 408 && e.status != 429 && !cuentaSinSubir) {
                    db.financialEventQueries.markSyncError(
                        e.serverMessage?.takeIf { it.isNotBlank() && it.length <= 200 } ?: "El servidor no lo aceptó (${e.status}).",
                        row.id,
                        row.userId,
                    )
                } else if (e.status >= 500) {
                    contarFalloDelServidor(row.id, row.userId, row.intentosFallidos, e.status)
                }
            } catch (e: Exception) {
                logSyncFailure("syncEvents", e, id = row.id)
            }
        }
    }

    /**
     * Empuja las anulaciones pendientes.
     *
     * Un **409 se sella igual que un éxito**: significa que ese evento ya está anulado en el
     * server, que es exactamente lo que esta fila quería lograr. Pasa de verdad y por dos
     * caminos: la cascada de un traspaso (anular una pata anula la hermana del lado del server,
     * ver `POST /api/events/{id}/void`) y la carrera entre dos dispositivos anulando las dos
     * patas a la vez. Antes eso quedaba sin sellar y el ciclo lo reintentaba cada 30 segundos
     * para siempre, ensuciando el log con un "error" que en realidad era el resultado buscado.
     *
     * **Un 404 también se sella, pero solo si el movimiento nunca subió** (`syncedAt` en null, o
     * la fila ya no está). Es el caso de anular un movimiento que el server rechazó, o que se
     * anotó sin señal y se anuló antes de subir: `selectUnsynced` ya no lo empuja, así que en el
     * server no hay nada que anular, y antes esa anulación rebotaba contra un 404 eterno mientras
     * el aviso de Movimientos seguía diciendo «corrígelo o anúlalo».
     *
     * No se deja de empujar la anulación de antemano, y es a propósito: un movimiento puede haber
     * llegado al server sin que el teléfono recibiera la respuesta (se cortó la señal a mitad del
     * POST), y ahí sigue en null acá pero existe allá. Empujar primero la anulación lo anula en
     * ese caso; y cuando de verdad no llegó, el 404 lo dice. Un 404 de un movimiento que SÍ subió
     * es otra cosa y se sigue reintentando.
     */
    internal suspend fun syncVoids() {
        val unsynced = db.voidEventQueries.selectUnsynced().executeAsList()
        for (row in unsynced) {
            try {
                remote.voidEvent(row.originalEventId, row.reason)
                db.voidEventQueries.markSynced(
                    Clock.System.now().toEpochMilliseconds(), row.id
                )
            } catch (e: ApiException) {
                val nuncaSubio = e.status == 404 &&
                    db.financialEventQueries.selectById(row.originalEventId, userId())
                        .executeAsOneOrNull()?.syncedAt == null
                if (e.status == 409 || nuncaSubio) {
                    db.voidEventQueries.markSynced(
                        Clock.System.now().toEpochMilliseconds(), row.id
                    )
                } else {
                    logSyncFailure("syncVoids", e, id = row.id)
                }
            } catch (e: Exception) {
                logSyncFailure("syncVoids", e, id = row.id)
            }
        }
    }

    /**
     * Antes esta clase se tragaba TODO error de sync con `catch (_: Exception) {}` — ni el id de
     * la fila que falló ni el motivo quedaban en ningún lado, así que un evento/cuenta atascado
     * (id en conflicto, cuenta inexistente, lo que sea) era indiagnosticable desde afuera: el
     * dueño solo veía que "algo" no llegaba al server. `println` porque no hay ningún logger ya
     * elegido en `:core` (no hay dependencia tipo Napier/co.touchlab acá, y este módulo corre en
     * JVM/Android/iOS — `println` es lo único que las tres plataformas comparten sin agregar una
     * dependencia nueva). No cambia la política de reintento: la fila se queda sin sellar y el
     * próximo ciclo de 30s la vuelve a intentar, exactamente igual que antes de este log.
     */
    private fun logSyncFailure(step: String, error: Exception, id: String? = null) {
        val target = id?.let { " id=$it" } ?: ""
        println("[SyncEngine] $step falló$target: ${error.message}")
    }

    /**
     * **Cuenta un fallo de servidor de esta fila, y avisa cuando ya no se puede llamar pasajero.**
     *
     * Un 5xx no entra en la lista de rechazos de [syncEvents] a propósito: es el server diciendo
     * «me rompí», y eso suele arreglarse solo en el ciclo siguiente. El problema era el 5xx que
     * NO se arregla —el que daba un concepto demasiado largo antes de que el server lo recortara,
     * y el que va a dar lo próximo con lo que se atore—: la fila reintentaba cada 30 segundos para
     * siempre y en Movimientos no había una sola palabra. Un movimiento que solo existe en este
     * teléfono y nadie te lo dice es peor que uno rechazado con motivo.
     *
     * Así que se cuentan los fallos SEGUIDOS y, pasado el umbral, se escribe el `syncError` que el
     * aviso de Movimientos ya sabe mostrar ([com.jvillada.movi.shared.model.MovimientoRechazado]).
     * Dos cosas que no cambian, y que son la mitad honesta de esto:
     *
     * - **no se deja de reintentar** — la fila sigue saliendo en `selectUnsynced`, así que sube
     *   sola el día que el server se recupere;
     * - **un éxito borra la cuenta y el aviso** (ver `limpiarElRastroDeFallos` en [syncEvents]).
     *
     * Solo cuenta lo que este ciclo sabe que llegó al server y volvió mal. Sin red no se cuenta
     * (cae en el `catch (e: Exception)` de más abajo), y tampoco un 401 —sesión vencida, se
     * arregla entrando— ni un 408/429, que son pasajeros por definición.
     */
    private fun contarFalloDelServidor(id: String, uid: String, fallosPrevios: Long?, status: Int) {
        val intentos = (fallosPrevios ?: 0L) + 1L
        db.financialEventQueries.guardarIntentosFallidos(intentos, id, uid)
        if (intentos >= INTENTOS_ANTES_DE_AVISAR) {
            db.financialEventQueries.markSyncError(elServidorNoLoRecibe(status), id, uid)
        }
    }

    companion object {
        /**
         * **Cuántos fallos seguidos de servidor hacen falta antes de avisar.** Diez ciclos de 30
         * segundos son cinco minutos: lo bastante para que un despliegue, un reinicio o un pico
         * pasen sin molestar a nadie, y lo bastante poco para que el dueño se entere el mismo rato
         * en que el movimiento se quedó trabado — no dos días después, cuando ya no se acuerda de
         * haberlo anotado.
         */
        const val INTENTOS_ANTES_DE_AVISAR: Long = 10L

        /**
         * Lo que se le dice cuando el server lleva rato negándose a recibir un movimiento. Dice las
         * dos cosas que importan: que Movi no se rindió, y que el problema está del otro lado —así
         * no se pone a buscar qué escribió mal.
         */
        fun elServidorNoLoRecibe(status: Int): String =
            "Movi lo sigue intentando, pero el servidor no lo está recibiendo (error $status)."
    }
}
