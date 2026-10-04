package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.aDestino
import com.jvillada.movi.shared.model.DestinoConocido
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/**
 * **Las cuentas ajenas que el dueño registró**, para ponerle su nombre a lo que llega del banco.
 *
 * Vive al lado de [memoriaDe] porque cumple el mismo papel y se usa en los mismos tres lugares: el
 * detalle de un mensaje del banco, la push del sync y la revisión de un extracto. Lee la fila sin
 * los totales —que son derivados y no hacen falta acá— y **con todos los identificadores**
 * (4-oct-2026): un tercero se reconoce por cualquiera de ellos.
 */
fun Transaction.destinosDelDueno(uid: String): List<DestinoConocido> =
    KnownDestinations.selectAll()
        .where { KnownDestinations.userId eq uid }
        .map { it.aDestino() }
