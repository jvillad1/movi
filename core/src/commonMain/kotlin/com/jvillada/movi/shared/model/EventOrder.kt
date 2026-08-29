package com.jvillada.movi.shared.model

/**
 * El orden en que se leen los movimientos: **el más reciente arriba, el más viejo abajo**.
 *
 * Vive en `:core` y no en cada lado porque hay **cuatro** lugares que ordenan la misma lista —
 * `GET /api/events` y `GET /api/events/by-day` en el server, `getEvents` y `getEventsByDay` en
 * el espejo local del teléfono— y hasta esta corrección solo uno de los cuatro tenía criterio.
 * El de `/by-day` faltaba entero: agrupaba por día, ordenaba **los días** de más nuevo a más
 * viejo, y dentro de cada día dejaba pasar lo que devolviera Postgres. Un `SELECT` sin
 * `ORDER BY` no promete nada: hoy sale el orden físico de la tabla, y mañana —después de un
 * `UPDATE` que reubica la fila, o de un `VACUUM`— sale otro. La lista se reordenaba sola.
 *
 * ### Por qué en Kotlin y no en las consultas
 *
 * Poner `ORDER BY` en la consulta obligaba a escribirlo **dos veces**, en dos motores que no
 * comparan igual: Postgres ordena los textos con la collation de la base y SQLite con orden
 * binario, así que el desempate por `id` podía dar vuelta distinta en el teléfono y en la web
 * para los mismos dos movimientos. Además `loadNonVoidedEventsIn` la usan también los cálculos
 * de saldo y los candidatos a pago de tarjeta, a los que el orden no les importa. Un solo
 * comparador de Kotlin, aplicado donde se arma la lista que alguien va a leer, es **un** criterio
 * para los tres clientes, no dos que un día se separan.
 *
 * ### El desempate
 *
 * Dos movimientos con el mismo `timestamp` al milisegundo no es raro: los que entran por SMS o
 * por extracto en lote comparten instante, y **las dos patas de un traspaso lo comparten
 * siempre** (es un solo hecho, ver `transferId`). Sin desempate esos dos bailan entre recargas,
 * que es exactamente el defecto que se vino a corregir. El `id` es la única otra columna única
 * que hay, así que desempata él. **No dice nada sobre cuál pasó antes** —los ids son UUID al
 * azar (ver [newId])— y no pretende decirlo: su trabajo es que el orden sea *el mismo* en cada
 * lectura, en el teléfono y en la web.
 */
val MAS_RECIENTE_PRIMERO: Comparator<FinancialEvent> =
    compareByDescending<FinancialEvent> { it.timestamp }.thenBy { it.id }

/** Los movimientos con el más reciente arriba y el más viejo abajo (ver [MAS_RECIENTE_PRIMERO]). */
fun List<FinancialEvent>.masRecientePrimero(): List<FinancialEvent> = sortedWith(MAS_RECIENTE_PRIMERO)
