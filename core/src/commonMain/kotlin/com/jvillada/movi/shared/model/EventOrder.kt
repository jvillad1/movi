package com.jvillada.movi.shared.model

/**
 * El orden en que se leen los movimientos: **el más reciente arriba, el más viejo abajo**.
 *
 * Vive en `:core` y no en cada lado porque hay **cinco** lugares que arman una lista de
 * movimientos para que alguien la lea, y los cinco tienen que coincidir:
 *
 * 1. `GET /api/events` (`EventRoutes`),
 * 2. `GET /api/events/by-day` (`EventRoutes`),
 * 3. `LocalRepository.getEvents` — el espejo del teléfono,
 * 4. `LocalRepository.getEventsByDay` — ídem,
 * 5. `AccountDetailScreen`, en la UI compartida, que arma sus propios días a partir de
 *    `getEvents` en vez de pedir `/by-day`.
 *
 * Hasta esta corrección solo el (1) tenía criterio. El de `/by-day` faltaba entero: agrupaba por
 * día, ordenaba **los días** de más nuevo a más viejo, y dentro de cada día dejaba pasar lo que
 * devolviera Postgres. Un `SELECT` sin `ORDER BY` no promete nada: hoy sale el orden físico de la
 * tabla, y mañana —después de un `UPDATE` que reubica la fila, o de un `VACUUM`— sale otro. La
 * lista se reordenaba sola. El (3) y el (5) ordenaban por `timestamp` **sin desempate**, que es
 * la otra mitad del defecto (ver más abajo por qué el empate es el caso normal).
 *
 * Queda un sexto lector fuera de esta lista, a sabiendas: `GET /api/statements/imports/{id}`
 * devuelve los eventos de una importación **sin ningún orden** (`StatementRoutes`). Es anterior a
 * esta corrección y se dejó como estaba — la pantalla de revisión de un extracto no es la lista
 * de movimientos del dueño —, pero si un día alguien la usa para leer movimientos, tiene que
 * pasar por acá.
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
 *    (todo lo que ya existía antes de esta columna) cae a `timestamp` — que **no** es lo mismo
 *    que dejarlo donde estaba: ver «Lo que ya existía no recibe el arreglo», acá abajo.
 * 2. **El `id`**, para que dos filas indistinguibles no bailen entre recargas. **No dice nada
 *    sobre cuál pasó antes** —son UUID al azar, ver [newId]—; su único trabajo es que el orden
 *    sea *el mismo* en cada lectura, en el teléfono y en la web.
 *
 * ### Lo que ya existía no recibe el arreglo
 *
 * Cuidado con leer el `null` como «queda donde estaba»: **no queda donde estaba**. Si los dos
 * movimientos empatados son viejos, los dos caen al MISMO `timestamp`, el segundo escalón vuelve
 * a empatar y el que decide es `thenBy { it.id }`. Y el `id` es un UUID v4 **al azar** (ver
 * [newId]). O sea que en un día empatado de lo que ya estaba cargado el orden previo no se
 * conserva: se reemplaza por el orden alfabético de dos números aleatorios. Es **estable** —la
 * misma lista sale igual en cada lectura, en el teléfono y en la web, que es la mitad del defecto
 * que sí se arregla para todos— pero es **arbitrario**: el que se anotó primero puede quedar
 * arriba.
 *
 * Y no es un caso de borde: los días empatados son justamente casi todo lo que ya está cargado.
 * La fecha elegida a mano se sella al mediodía, y `StatementRoutes.createEventFromParsed` sella
 * cada fila importada a la **medianoche** de Bogotá del día del extracto, así que un extracto
 * empata el día entero.
 *
 * **No se hace backfill**, a propósito: la tabla nunca registró cuándo nació una fila, así que no
 * hay de dónde sacarlo, y aproximarlo mentiría más de lo que arregla. Congelar el orden físico de
 * hoy (`ctid`) parece tentador, pero toda fila alguna vez UPDATEada —recategorizada, con la fecha
 * corregida, o tocada por `restampStatementEventsToBogota`— ya migró al final del heap y quedaría
 * marcada como «la más nueva». Lo viejo queda estable y arbitrario; lo nuevo queda bien.
 *
 * ### La asimetría entre lo viejo y lo nuevo
 *
 * En un mismo instante empatado, **toda fila con sello queda arriba de toda fila sin sello**,
 * siempre que el sello sea posterior al instante — que es el caso normal: primero pasa el gasto y
 * después se anota. La fila sin sello cae a su `timestamp`, que es el menor de los dos. Así que
 * lo nuevo se apila entero encima del bloque viejo y nunca se intercala con él. No es un defecto
 * —es exactamente lo que se sabe de cada fila— pero conviene saberlo antes de mirar un día
 * mezclado y concluir que el orden se rompió.
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
