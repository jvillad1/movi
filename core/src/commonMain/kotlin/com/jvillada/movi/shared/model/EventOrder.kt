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
 * ### El empate no es raro: es el caso normal de un día pasado
 *
 * Dos movimientos con el mismo `timestamp` al milisegundo pasan todo el tiempo. Las dos patas de
 * un traspaso lo comparten **siempre** (es un solo hecho, ver `transferId`), un lote de SMS o de
 * extracto entra junto, y sobre todo: **al elegir una fecha que no es hoy, el cliente la convierte
 * al mediodía de Bogotá** (`timestampParaFecha`). Solo «Hoy» conserva la hora real. O sea que
 * cinco gastos de ayer anotados uno detrás del otro quedan los cinco con el MISMO instante, y
 * `timestamp` no decide nada entre ellos. Por eso el desorden se nota más en los días pasados que
 * en el de hoy.
 *
 * Así que el desempate no es una formalidad, es la mitad del arreglo, y va en dos escalones:
 *
 * 1. **[FinancialEvent.createdAt] — cuándo lo anotó.** Es el dato que faltaba: entre dos
 *    movimientos que "ocurrieron" al mismo instante, arriba va el que escribió último. Un `null`
 *    (todo lo que ya existía antes de esta columna) cae a `timestamp`, así que esos movimientos
 *    quedan como estaban en vez de recibir una fecha de creación inventada.
 * 2. **El `id`**, para que dos filas indistinguibles no bailen entre recargas. **No dice nada
 *    sobre cuál pasó antes** —son UUID al azar, ver [newId]—; su único trabajo es que el orden
 *    sea *el mismo* en cada lectura, en el teléfono y en la web.
 *
 * ### Por qué `createdAt` desempata y no manda
 *
 * Porque `timestamp` sí sabe la hora cuando la sabe. Un SMS del banco de ayer a las 23:00 tiene
 * hora real; un gasto de ayer anotado hoy a mano quedó al mediodía. Si ordenara por creación, el
 * gasto escrito hoy se treparía arriba del SMS de las 23:00 — y por hora del día el SMS pasó
 * después. La creación entra solo cuando los dos instantes son iguales, que es justo el caso que
 * `timestamp` no puede resolver.
 */
val MAS_RECIENTE_PRIMERO: Comparator<FinancialEvent> =
    compareByDescending<FinancialEvent> { it.timestamp }
        .thenByDescending { it.createdAt ?: it.timestamp }
        .thenBy { it.id }

/** Los movimientos con el más reciente arriba y el más viejo abajo (ver [MAS_RECIENTE_PRIMERO]). */
fun List<FinancialEvent>.masRecientePrimero(): List<FinancialEvent> = sortedWith(MAS_RECIENTE_PRIMERO)
