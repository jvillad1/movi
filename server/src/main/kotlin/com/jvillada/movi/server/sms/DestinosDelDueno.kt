package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.shared.model.DestinoConocido
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/**
 * **Las cuentas ajenas que el dueño registró**, para ponerle su nombre a lo que llega del banco.
 *
 * Vive al lado de [memoriaDe] porque cumple el mismo papel y se usa en los mismos tres lugares: el
 * detalle de un mensaje del banco, la push del sync y la revisión de un extracto. Lee las tres
 * columnas que la resolución necesita —los totales no, que son derivados y no hacen falta acá— y
 * **sin los totales** justamente porque esto corre en cada apertura de un mensaje.
 */
fun Transaction.destinosDelDueno(uid: String): List<DestinoConocido> =
    KnownDestinations
        .select(KnownDestinations.id, KnownDestinations.nombre, KnownDestinations.numero, KnownDestinations.deQuien)
        .where { KnownDestinations.userId eq uid }
        .map {
            DestinoConocido(
                id = it[KnownDestinations.id],
                nombre = it[KnownDestinations.nombre],
                numero = it[KnownDestinations.numero],
                deQuien = it[KnownDestinations.deQuien],
            )
        }
