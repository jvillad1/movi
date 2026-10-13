package com.jvillada.movi.server.cuenta

import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.EnlacesCompartidos
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImportMatches
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere

/**
 * # Borrar la cuenta: todo lo de una persona, de todas las tablas
 *
 * Lo último de «Confianza básica» que faltaba: la cuenta es de quien la usa, y tiene que poder irse
 * sin pedírselo a nadie. Ya estaban la descarga de los datos (`exportar/ExportarDatos.kt`), la versión
 * de sesiones y la limpieza local al salir; esto es la otra mitad de la descarga.
 *
 * ## Qué se borra
 *
 * **Todo.** A diferencia de la descarga —que deja afuera a propósito la cerradura (el hash, los
 * enlaces de recuperación), lo que es de un aparato (las suscripciones push) y la memoria interna de
 * Movi AI—, acá no hay nada que conservar: cada una de esas filas también dice algo de la persona o
 * le abre una puerta. Entran el binario de los documentos (viven en `documents.content`), las
 * lecturas de papeles que Movi guardó, «Personas y comercios» con sus descartes, las conversaciones
 * con Movi AI y lo que recordó, los enlaces compartidos y la fila de `users`.
 *
 * ## Cómo se garantiza que no quede ninguna tabla afuera
 *
 * Con [TABLAS_DEL_USUARIO] escrita a mano —una tabla nueva no entra sola— y una prueba que no deja
 * olvidarla: `BorrarLaCuentaTest` recorre **todas** las tablas de Exposed que el server compila,
 * busca las que tienen columna `user_id`, y falla si alguna no está en esta lista. Así una tabla
 * nueva sin cubrir rompe el CI, no la promesa que se le hizo a quien se fue.
 *
 * Una lista a mano y no «todas las que tengan `user_id`» por reflexión en producción: el borrado
 * tiene que poder leerse entero en un solo lugar, y un descubrimiento automático en tiempo de
 * ejecución borraría también lo que una tabla futura guarde con otra semántica sin que nadie lo
 * decidiera. La prueba obliga a decidir.
 *
 * ## Y la sesión
 *
 * Borrar la fila de `users` es lo que deja afuera a todo token viejo: `configureAuth` compara la
 * versión del token contra `users.token_version`, y un usuario sin fila no tiene contra qué
 * compararse (ver `versionVigenteDe`). Lo mismo el enlace de descarga de los datos. No hace falta
 * —ni serviría— subir la versión antes de borrar la fila que la guarda.
 */
internal data class TablaDelUsuario(val tabla: Table, val usuario: Column<String>)

/**
 * Las tablas que guardan algo de una persona, con su columna de usuario. **Sin `users`**, que se
 * borra al final y por su `id` (ver [borrarTodoLoDe]). `screen_definitions` no está porque no es de
 * nadie: es la definición del Inicio, la misma para todos.
 */
internal val TABLAS_DEL_USUARIO: List<TablaDelUsuario> = listOf(
    // La plata
    TablaDelUsuario(Accounts, Accounts.userId),
    TablaDelUsuario(Events, Events.userId),
    TablaDelUsuario(VoidEvents, VoidEvents.userId),
    TablaDelUsuario(Credits, Credits.userId),
    TablaDelUsuario(Cards, Cards.userId),
    TablaDelUsuario(CardPaymentDismissals, CardPaymentDismissals.userId),
    TablaDelUsuario(RecurringRules, RecurringRules.userId),
    TablaDelUsuario(RecurringOccurrences, RecurringOccurrences.userId),
    TablaDelUsuario(OccurrenceRejections, OccurrenceRejections.userId),
    TablaDelUsuario(Subscriptions, Subscriptions.userId),
    TablaDelUsuario(Budgets, Budgets.userId),
    TablaDelUsuario(Goals, Goals.userId),
    TablaDelUsuario(CategoryPrefs, CategoryPrefs.userId),
    TablaDelUsuario(AnomaliasDescartadas, AnomaliasDescartadas.userId),
    TablaDelUsuario(DebitosDescartados, DebitosDescartados.userId),
    // Personas y comercios
    TablaDelUsuario(KnownDestinations, KnownDestinations.userId),
    TablaDelUsuario(DestinosDescartados, DestinosDescartados.userId),
    // Lo que llegó del banco y los papeles
    TablaDelUsuario(SmsMessages, SmsMessages.userId),
    TablaDelUsuario(Documents, Documents.userId),
    TablaDelUsuario(LecturasDePapeles, LecturasDePapeles.userId),
    TablaDelUsuario(StatementImports, StatementImports.userId),
    TablaDelUsuario(StatementImportMatches, StatementImportMatches.userId),
    // Movi AI
    TablaDelUsuario(AiTurns, AiTurns.userId),
    TablaDelUsuario(ConversacionesDelAsistente, ConversacionesDelAsistente.userId),
    TablaDelUsuario(MemoriaDelAsistente, MemoriaDelAsistente.userId),
    TablaDelUsuario(AccionesPropuestas, AccionesPropuestas.userId),
    // La cerradura y lo que abre puertas
    TablaDelUsuario(PasswordResetTokens, PasswordResetTokens.userId),
    TablaDelUsuario(PushSubscriptions, PushSubscriptions.userId),
    TablaDelUsuario(EnlacesCompartidos, EnlacesCompartidos.userId),
)

/**
 * Borra todo lo de [uid]: cada tabla de [TABLAS_DEL_USUARIO] y al final su fila de `users`. Devuelve
 * cuántas filas se fueron, para el log.
 *
 * **Corre DENTRO de la transacción de quien llama**: o se va todo, o no se va nada. Un borrado a
 * medias —los movimientos sí, las cuentas no— sería lo peor de los dos mundos: ni la cuenta sirve ni
 * los datos se fueron.
 */
internal fun borrarTodoLoDe(uid: String): Int {
    var filas = 0
    for (t in TABLAS_DEL_USUARIO) {
        filas += t.tabla.deleteWhere { t.usuario eq uid }
    }
    filas += Users.deleteWhere { Users.id eq uid }
    return filas
}
