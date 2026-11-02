package com.jvillada.movi.server.legal

import java.io.File

/**
 * # Privacidad, términos y borrar la cuenta (vuelta 109)
 *
 * Tres páginas HTML que Google Play y el App Store piden como URL pública: la política de privacidad
 * (`/privacidad`), los términos de uso (`/terminos`) y cómo pedir el borrado de la cuenta
 * (`/borrar-cuenta`, la URL que exige el formulario de Seguridad de los datos de Play).
 *
 * Igual que la página compartida (`compartir/PaginaCompartida.kt`): **sin wasm, sin login y sin
 * scripts**, servidas por el server, con los colores de `Tokens.kt` (los vigila
 * `PaginasLegalesTest`) y Figtree del mismo bundle, con la pila del sistema de respaldo.
 *
 * ## Lo que dicen sale de lo que Movi hace
 *
 * Los textos se escribieron leyendo el código y `CLAUDE.md`: qué tablas guardan qué
 * (`TABLAS_DEL_USUARIO` en `cuenta/BorrarLaCuenta.kt`), qué va a Anthropic (Movi AI y la lectura de
 * papeles), qué recibe Resend (los correos), cuánto dura el uso y los fallos
 * (`Observabilidad.DIAS_DE_RETENCION`, 90 días) y qué se cifra (`documentos/Cifrado.kt`). Si eso
 * cambia, estas páginas cambian con ello. **Son un borrador cuidadoso, no asesoría legal**: antes de
 * publicarlas conviene que las lea un abogado colombiano (Ley 1581 de 2012 y Decreto 1377 de 2013).
 *
 * ## El responsable no está escrito en el código
 *
 * El nombre del responsable del tratamiento y el correo de contacto salen de `RESPONSABLE_LEGAL` y
 * `CORREO_DE_CONTACTO` ([ResponsableDeMovi]). Sin ellas la página dice algo visible y verdadero
 * («el responsable de Movi», «el correo de contacto de Movi (pendiente de publicar)») y nunca un
 * correo inventado. Antes de mandar la app a las tiendas hay que ponerlas (`docs/lanzamiento/`).
 */
internal object PaginasLegales {

    /** La fecha desde la que rige este texto. Cambiarla cada vez que cambie lo que dice. */
    const val VIGENCIA = "2 de noviembre de 2026"

    const val RUTA_PRIVACIDAD = "/privacidad"
    const val RUTA_TERMINOS = "/terminos"
    const val RUTA_BORRAR_CUENTA = "/borrar-cuenta"

    /** El marcador del responsable cuando falta `RESPONSABLE_LEGAL`. */
    const val SIN_RESPONSABLE = "el responsable de Movi"

    /**
     * Lo que se dice en lugar del correo cuando falta `CORREO_DE_CONTACTO`. No promete un canal que no
     * existe: la app no tiene un «escríbenos» propio, así que el marcador dice que el correo falta.
     */
    const val SIN_CORREO = "el correo de contacto de Movi (pendiente de publicar)"

    /**
     * Solo estilos en línea y las fuentes del propio server: ni scripts, ni imágenes, ni marcos, y
     * nadie la puede meter en un `<iframe>`.
     */
    const val POLITICA_DE_CONTENIDO =
        "default-src 'none'; style-src 'unsafe-inline'; font-src 'self'; base-uri 'none'; " +
            "form-action 'none'; frame-ancestors 'none'"

    private const val FUENTES = "/composeResources/com.jvillada.movi.resources/font"

    /** Los valores son los de `Tokens.kt` (`COLORES_CLAROS`); los vigila `PaginasLegalesTest`. */
    private val ESTILOS = """
@font-face { font-family: 'Figtree'; src: url('$FUENTES/figtree_regular.ttf') format('truetype'); font-weight: 400; font-display: swap; }
@font-face { font-family: 'Figtree'; src: url('$FUENTES/figtree_semibold.ttf') format('truetype'); font-weight: 600; font-display: swap; }
@font-face { font-family: 'Figtree'; src: url('$FUENTES/figtree_extrabold.ttf') format('truetype'); font-weight: 800; font-display: swap; }
:root {
  --fondo: #F4F1EC; --tarjeta: #FFFFFF; --borde: #E7E1D7; --hilo: #EFEAE1;
  --texto: #1F1B16; --texto-medio: #5C554C; --texto-apagado: #6E665B;
  --marca: #FFC21A; --enlace: #1D4E9E;
  --letra: 'Figtree', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  color-scheme: light;
}
* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; text-size-adjust: 100%; }
body { margin: 0; background: var(--fondo); color: var(--texto); font-family: var(--letra);
  font-size: 16px; line-height: 24px; font-weight: 400; -webkit-font-smoothing: antialiased; }
main { max-width: 720px; margin: 0 auto; padding: 28px 16px 48px; }
.marca { display: inline-block; padding: 2px 10px; border-radius: 999px; background: var(--marca);
  color: var(--texto); font-size: 15px; font-weight: 800; letter-spacing: -0.3px; text-decoration: none; }
h1 { margin: 18px 0 4px; font-size: 28px; line-height: 34px; font-weight: 800; letter-spacing: -0.6px; }
h2 { margin: 28px 0 8px; font-size: 19px; line-height: 26px; font-weight: 800; }
p, ul, ol { margin: 0 0 12px; }
ul, ol { padding-left: 22px; }
li { margin-bottom: 6px; }
.meta { color: var(--texto-medio); font-size: 13px; line-height: 18px; }
.tarjeta { background: var(--tarjeta); border-radius: 22px; padding: 18px 16px; margin: 16px 0; }
.tarjeta > :last-child { margin-bottom: 0; }
.marcador { color: var(--texto-medio); font-style: italic; }
a { color: var(--enlace); text-underline-offset: 2px; }
strong { font-weight: 600; }
nav { margin-top: 32px; padding-top: 16px; border-top: 1px solid var(--borde); display: flex; flex-wrap: wrap; gap: 8px 20px; font-size: 14px; }
""".trim()

    // ── Las tres páginas ─────────────────────────────────────────────────────

    fun privacidad(responsable: ResponsableDeMovi.Datos): String = pagina(
        titulo = "Política de privacidad",
        cuerpo = buildString {
            append(encabezado("Política de privacidad y tratamiento de datos personales"))
            append(
                "<p>Movi es una app para llevar tus finanzas personales y familiares: tus cuentas, tus " +
                    "movimientos, tus pagos y tus deudas. Esta política explica qué datos tuyos guarda " +
                    "Movi, para qué, con quién se comparten y cómo ejercer tus derechos. Se rige por la " +
                    "Ley 1581 de 2012 de Colombia y su Decreto 1377 de 2013.</p>",
            )
            append("<div class=\"tarjeta\"><p><strong>En corto:</strong> Movi no vende tus datos, no muestra " +
                "publicidad y no mueve tu plata. Puedes descargar una copia de tus datos y borrar tu cuenta desde la " +
                "app cuando quieras.</p></div>")

            append("<h2>1. Quién es responsable de tus datos</h2>")
            append("<p>El responsable del tratamiento es ${responsable.nombreHtml()}, en Colombia. Para " +
                "cualquier consulta o reclamo sobre tus datos: ${responsable.correoHtml()}.</p>")

            append("<h2>2. Qué datos guarda Movi</h2>")
            append("<ul>")
            append("<li><strong>Tu cuenta:</strong> tu nombre, tu correo y tu contraseña (guardada como " +
                "una huella cifrada, nunca en claro), y tus preferencias de la app.</li>")
            append("<li><strong>Tu información financiera:</strong> las cuentas, tarjetas, créditos y bienes " +
                "que registras, sus saldos, tus movimientos, categorías, pagos recurrentes, presupuestos, " +
                "apartados y metas. Movi no se conecta a tu banco: todo esto lo escribes tú o llega por " +
                "los avisos que tú activas.</li>")
            append("<li><strong>Personas y grupos:</strong> los nombres de las personas y comercios con " +
                "los que haces transferencias o compartes gastos, y los números de cuenta o llaves de " +
                "terceros que tú registres para reconocerlos.</li>")
            append("<li><strong>Los avisos de tu banco, solo si los activas:</strong>" +
                "<ul>" +
                "<li>Las <strong>notificaciones</strong> que publican las apps de bancos de una lista cerrada " +
                "(en Android, con el acceso a las notificaciones que tú das). Las notificaciones de " +
                "cualquier otra app se descartan en tu teléfono sin leerlas.</li>" +
                "<li>Los <strong>SMS</strong> de tu banco, solo en la versión de Android que se instala por " +
                "fuera de la tienda y solo con el permiso que tú das. Salen del teléfono solo los mensajes " +
                "cuyo remitente es un código bancario conocido o cuyo texto nombra un banco de una lista " +
                "cerrada (por eso un mensaje personal que nombre a tu banco también puede salir); los demás " +
                "nunca salen de él. En el servidor, lo que no parece un movimiento (un código de " +
                "verificación, una promoción) queda apartado, y nada se anota sin que tú lo confirmes. La versión de " +
                "Google Play no lee SMS.</li>" +
                "<li>Los <strong>correos de tu banco</strong> que tú decidas reenviar a tu dirección de Movi.</li>" +
                // Vuelta 111: el canal «Atajos de iPhone» (`POST /api/captura/atajo`).
                "<li>Desde los <strong>Atajos de iPhone</strong>, si tú los configuras: el texto de los SMS que " +
                "tu automatización le reenvía a Movi, y de cada pago con Apple Pay el comercio, el monto y el " +
                "nombre de la tarjeta. Los atajos usan un código que solo sirve para mandar avisos (el servidor " +
                "guarda solo su huella, no el código, y lo puedes desactivar en la app). Un mensaje que no es de un banco de " +
                "una lista cerrada se descarta sin guardar su texto: solo se cuenta cuántos llegaron.</li>" +
                "</ul></li>")
            append("<li><strong>Papeles y extractos:</strong> los PDF e imágenes que subes o compartes con " +
                "Movi y lo que se leyó de ellos. Se guardan cifrados (AES-256). Si pides que Movi recuerde " +
                "la clave de tus extractos, también se guarda cifrada y nunca sale del servidor.</li>")
            append("<li><strong>Movi AI:</strong> las preguntas que le haces al asistente, sus respuestas y " +
                "lo que tú le pidas que recuerde (puedes verlo y borrarlo en «Lo que Movi sabe de ti»).</li>")
            append("<li><strong>Uso de la app y fallos:</strong> qué pantallas abres y qué tocas (nunca montos " +
                "ni textos que escribes), y los reportes de fallos de la app con el modelo del aparato y la " +
                "versión del sistema. El uso se puede apagar en Tú → Tus datos.</li>")
            append("<li><strong>Datos técnicos:</strong> las suscripciones a notificaciones del navegador o " +
                "del teléfono y los registros del servidor (qué ruta se pidió y cuándo).</li>")
            append("</ul>")
            append("<p>«Entrar con huella» usa el lector del sistema: tu huella nunca llega a Movi.</p>")

            append("<h2>3. Para qué los usa</h2>")
            append("<ul>")
            append("<li>Mostrarte tu plata: saldos, cuánto te queda en el período, lo que vence y en qué se " +
                "va.</li>")
            append("<li>Proponerte movimientos a partir de los avisos de tu banco y de tus extractos, para " +
                "que tú los confirmes. Nada se anota solo.</li>")
            append("<li>Avisarte de pagos por vencer, del cierre de tu período y de lo que está por " +
                "revisar, por notificación o por correo (cada aviso se puede apagar).</li>")
            append("<li>Contestar tus preguntas en Movi AI.</li>")
            append("<li>Encontrar y arreglar fallos, y saber qué partes de la app se usan para mejorarla.</li>")
            append("<li>Mantener tu cuenta segura (por ejemplo, cerrar sesiones o recuperar tu contraseña).</li>")
            append("</ul>")
            append("<p>Movi no usa tus datos para publicidad, no hace perfiles para terceros y no toma " +
                "decisiones automáticas que te afecten: todo lo que propone, lo decides tú.</p>")

            append("<h2>4. Con quién se comparten</h2>")
            append("<p><strong>Movi no vende ni alquila tus datos.</strong> Solo los procesan, por cuenta de " +
                "Movi, estos proveedores, y solo para lo que se describe:</p>")
            append("<ul>")
            append("<li><strong>Railway</strong> (hosting): ahí corren el servidor y la base de datos de Movi.</li>")
            append("<li><strong>Anthropic</strong> (la IA Claude). Cuando le escribes a Movi AI recibe tu " +
                "mensaje, los mensajes anteriores de esa conversación, un resumen de tus cuentas y " +
                "movimientos, los recuerdos de «Lo que Movi sabe de ti» y la foto que adjuntes en el chat. " +
                "Cuando le pides a Movi que lea un papel o un extracto, recibe ese archivo. Nunca recibe tu " +
                "contraseña ni tu sesión, y las tres preguntas sugeridas de Movi AI no van a la IA. Según " +
                "sus términos comerciales, Anthropic no usa por defecto lo que llega por su API para " +
                "entrenar sus modelos. La primera vez que algo va a la IA, la app te lo dice antes " +
                "(«Qué ve la IA», también en Tú → Movi).</li>")
            append("<li><strong>Resend</strong> (correo): envía los correos de Movi (recordatorios, cierre del " +
                "período, recuperar la contraseña) y recibe los correos del banco que tú reenvías.</li>")
            append("<li>Los servicios de notificaciones del navegador o del sistema, para entregarte los " +
                "avisos que activaste.</li>")
            append("</ul>")
            append("<p>Si compartes un resumen con alguien (Tú → Compartir), esa persona ve lo que el enlace " +
                "muestra mientras no lo revoques o venza. También se entregarían datos a una autoridad " +
                "competente si la ley lo exige.</p>")

            append("<h2>5. Dónde se guardan y transferencia internacional</h2>")
            append("<p>Los servidores de Railway, Anthropic y Resend pueden estar fuera de Colombia, " +
                "principalmente en Estados Unidos. Al usar Movi autorizas esa transferencia, que se hace " +
                "solo con estos proveedores y con las protecciones de esta política. Todo viaja cifrado " +
                "(HTTPS).</p>")

            append("<h2>6. Cuánto tiempo</h2>")
            append("<ul>")
            append("<li>Tu información financiera, tus papeles y tus conversaciones con Movi AI se guardan " +
                "mientras tengas la cuenta. Al borrarla se eliminan.</li>")
            append("<li>El uso de la app y los reportes de fallos se borran solos a los 90 días.</li>")
            append("<li>Al borrar tu cuenta: $LO_QUE_MOVI_NO_CONTROLA</li>")
            append("</ul>")

            append("<h2>7. Tus derechos</h2>")
            append("<p>Como titular de tus datos puedes, en cualquier momento y sin costo:</p>")
            append("<ul>")
            append("<li>Conocer, actualizar y rectificar tus datos.</li>")
            append("<li>Pedir prueba de la autorización que diste.</li>")
            append("<li>Saber para qué se han usado.</li>")
            append("<li>Revocar la autorización y pedir que se supriman tus datos.</li>")
            append("<li>Presentar quejas ante la Superintendencia de Industria y Comercio (SIC), después de " +
                "haber hecho tu consulta o reclamo a Movi.</li>")
            append("</ul>")
            append("<p><strong>Cómo ejercerlos:</strong></p>")
            append("<ul>")
            append("<li>Ver y corregir tus datos: en la misma app.</li>")
            append("<li>Descargar una copia: Tú → Tus datos → «Descargar tus datos». $LO_QUE_TRAE_LA_DESCARGA</li>")
            append("<li>Borrar tu cuenta y todo lo tuyo: Tú → «Borrar mi cuenta». Más detalle en " +
                "<a href=\"$RUTA_BORRAR_CUENTA\">cómo borrar tu cuenta</a>.</li>")
            append("<li>Cualquier otra consulta o reclamo: ${responsable.correoHtml()}. Las consultas se " +
                "contestan en un máximo de 10 días hábiles y los reclamos en un máximo de 15 días hábiles, " +
                "como dice la ley.</li>")
            append("</ul>")

            append("<h2>8. Seguridad</h2>")
            append("<p>Las contraseñas se guardan con una huella criptográfica, los papeles y las claves de " +
                "extractos se cifran, todo viaja por HTTPS y cada sesión se puede cerrar desde la app. " +
                "Ningún sistema es infalible: si llegara a haber un incidente que afecte tus datos, te " +
                "avisaremos.</p>")

            append("<h2>9. Menores de edad</h2>")
            append("<p>Movi está hecha para personas mayores de 18 años y no recoge a sabiendas datos de " +
                "menores.</p>")

            append("<h2>10. Cambios a esta política</h2>")
            append("<p>Si esta política cambia en algo importante, lo diremos en la app antes de que rija. " +
                "La fecha de arriba dice desde cuándo rige esta versión.</p>")
        },
        responsable = responsable,
    )

    fun terminos(responsable: ResponsableDeMovi.Datos): String = pagina(
        titulo = "Términos de uso",
        cuerpo = buildString {
            append(encabezado("Términos de uso"))
            append("<p>Al crear tu cuenta en Movi aceptas estos términos y la " +
                "<a href=\"$RUTA_PRIVACIDAD\">política de privacidad</a>. Los ofrece " +
                "${responsable.nombreHtml()}.</p>")

            append("<h2>1. Qué es Movi y qué no es</h2>")
            append("<ul>")
            append("<li>Movi es una herramienta para que lleves tus finanzas: registra, ordena y te muestra " +
                "tu plata.</li>")
            append("<li><strong>No es un banco</strong> ni una entidad vigilada por la Superintendencia " +
                "Financiera, <strong>no mueve plata</strong>, no presta, no recibe depósitos, no hace pagos " +
                "por ti y no se conecta a tus cuentas bancarias.</li>")
            append("<li><strong>No es asesoría financiera, tributaria ni legal.</strong> Lo que Movi te " +
                "muestra o te sugiere es información para que tú decidas.</li>")
            append("</ul>")

            append("<h2>2. Las cifras son las que tú y tus avisos le dan</h2>")
            append("<p>Movi calcula con lo que tú registras y con los avisos de tu banco que tú activas y " +
                "confirmas. Si un dato está mal o falta, la cifra estará mal. Para lo que cuenta de verdad " +
                "—un pago, un saldo, una deuda— la fuente es tu banco.</p>")

            append("<h2>3. Movi AI puede equivocarse</h2>")
            append("<p>El asistente usa inteligencia artificial y puede dar respuestas incompletas o " +
                "equivocadas. Revisa lo importante antes de actuar. Movi AI nunca anota nada sin que tú lo " +
                "confirmes.</p>")

            append("<h2>4. Tu cuenta</h2>")
            append("<ul>")
            append("<li>La cuenta es personal. Cuida tu contraseña; si crees que alguien entró, cámbiala y " +
                "cierra la sesión en todos tus aparatos (Tú → Cuenta).</li>")
            append("<li>Lo que registras es tuyo. Puedes descargar una copia (Tú → Tus datos) y borrar tu cuenta cuando quieras.</li>")
            append("<li>Si compartes un resumen con alguien, tú decides con quién y por cuánto tiempo.</li>")
            append("</ul>")

            append("<h2>5. Uso aceptable</h2>")
            append("<p>No uses Movi para algo ilegal, para guardar datos de otras personas sin derecho a " +
                "hacerlo, para intentar entrar a cuentas ajenas, para sobrecargar el servicio ni para " +
                "copiarlo o revenderlo.</p>")

            append("<h2>6. Suspensión</h2>")
            append("<p>Podemos suspender o cerrar una cuenta que incumpla estos términos o que ponga en " +
                "riesgo a otros usuarios o al servicio. Salvo un riesgo inmediato, te avisaremos antes y " +
                "podrás descargar tus datos.</p>")

            append("<h2>7. El servicio</h2>")
            append("<p>Movi se ofrece tal como está. Hacemos lo posible para que funcione bien y siempre, " +
                "pero puede tener fallos o interrupciones, y puede cambiar o dejar de ofrecer funciones. Si " +
                "algún día Movi deja de funcionar, avisaremos con tiempo para que descargues tus datos.</p>")

            append("<h2>8. Responsabilidad</h2>")
            append("<p>En la medida en que la ley lo permite, Movi no responde por decisiones financieras " +
                "que tomes con la información de la app, ni por pérdidas causadas por datos incompletos o " +
                "equivocados, por fallos de terceros (tu banco, tu proveedor de internet, las tiendas de " +
                "apps) o por el uso de tu cuenta por alguien a quien le diste acceso. Nada de esto limita " +
                "los derechos que la ley colombiana de protección al consumidor no deja renunciar.</p>")

            append("<h2>9. Cambios</h2>")
            append("<p>Si estos términos cambian en algo importante, lo diremos en la app antes de que " +
                "rijan. Si no estás de acuerdo, puedes borrar tu cuenta.</p>")

            append("<h2>10. Ley y contacto</h2>")
            append("<p>Estos términos se rigen por la ley de Colombia. Para cualquier pregunta: " +
                "${responsable.correoHtml()}.</p>")
        },
        responsable = responsable,
    )

    fun borrarCuenta(responsable: ResponsableDeMovi.Datos): String = pagina(
        titulo = "Borrar tu cuenta",
        cuerpo = buildString {
            append(encabezado("Cómo borrar tu cuenta de Movi"))
            append("<h2>Desde la app</h2>")
            append("<ol>")
            append("<li>Abre Movi y entra a <strong>Tú</strong> (la última pestaña).</li>")
            append("<li>Baja hasta el final y toca <strong>«Borrar mi cuenta»</strong>.</li>")
            append("<li>Si quieres, descarga antes tus datos (la misma pantalla te lo ofrece).</li>")
            append("<li>Escribe tu contraseña y la palabra <strong>BORRAR</strong>, y confirma.</li>")
            append("</ol>")
            append("<p>Funciona igual en Android, en iPhone y en la web.</p>")

            append("<h2>Qué se borra</h2>")
            append("<p><strong>Todo lo tuyo, en el momento:</strong> tu cuenta, tus cuentas y saldos, tus " +
                "movimientos, créditos, tarjetas, pagos recurrentes, presupuestos, apartados, grupos y " +
                "personas, los avisos de tu banco (SMS, notificaciones, correos y Atajos de iPhone, con su código), tus papeles y extractos " +
                "y lo que Movi leyó de ellos, tus conversaciones y recuerdos de Movi AI, tus enlaces " +
                "compartidos, tus suscripciones a notificaciones, y el uso de la app y los fallos asociados " +
                "a tu cuenta. Al terminar se cierran tus sesiones en todos los aparatos.</p>")
            append("<p><strong>No se puede deshacer.</strong> Lo borrado no se puede recuperar: si quieres " +
                "conservar algo, descarga tus datos antes (Tú → Tus datos → «Descargar tus datos»). " +
                "$LO_QUE_TRAE_LA_DESCARGA</p>")
            append("<p>$LO_QUE_MOVI_NO_CONTROLA</p>")
            append("<p>Lo que esté en tu teléfono (la copia local de la app) se borra al terminar, y " +
                "desinstalar la app lo borra también.</p>")

            append("<h2>Si ya no puedes entrar</h2>")
            append("<p>Si olvidaste tu contraseña, recupérala desde la pantalla de entrada («¿Olvidaste tu " +
                "contraseña?») y borra la cuenta desde la app. Si tampoco tienes acceso a tu correo, " +
                "escríbenos a ${responsable.correoHtml()} desde el correo con el que creaste la cuenta, " +
                "pidiendo el borrado. Lo hacemos en un máximo de 15 días hábiles y te confirmamos por " +
                "correo.</p>")
        },
        responsable = responsable,
    )

    /**
     * Lo que borrar la cuenta NO alcanza (revisión de la vuelta 109): `DELETE /api/me` borra las filas de
     * Movi en una transacción, pero no las copias de los proveedores. Sin plazos que no se puedan
     * verificar.
     */
    const val LO_QUE_MOVI_NO_CONTROLA =
        "Movi borra sus propias filas; hay copias que no controla y que se borran o vencen según el ciclo " +
            "de cada uno: los respaldos de la base de datos y los registros del servidor que guarda el " +
            "proveedor de hosting, los correos recibidos que guarda Resend y lo que Anthropic retiene " +
            "según su política para la API. Movi no les puede pedir que lo borren antes."

    /**
     * Lo que trae «Descargar tus datos» de verdad (`TABLAS_EXPORTADAS` en `exportar/ExportarDatos.kt`): no
     * es «todo lo tuyo». Si esa lista cambia, esto cambia con ella (lo vigila `PaginasLegalesTest`).
     */
    const val LO_QUE_TRAE_LA_DESCARGA =
        "Es un archivo ZIP (CSV y JSON) con tu perfil, tus cuentas, movimientos, pagos recurrentes, créditos, " +
            "tarjetas, suscripciones, presupuestos, categorías, metas, apartados, personas y comercios, los " +
            "mensajes del banco, los extractos importados, la lista de tus documentos (sin los archivos) y " +
            "lo que Movi sabe de ti. No trae los archivos de tus papeles, tus conversaciones con Movi AI ni " +
            "tus grupos."

    // ── Piezas ───────────────────────────────────────────────────────────────

    private fun encabezado(titulo: String) =
        "<a class=\"marca\" href=\"/\">Movi</a><h1>${esc(titulo)}</h1>" +
            "<p class=\"meta\">Vigente desde el $VIGENCIA</p>"

    private fun pagina(titulo: String, cuerpo: String, responsable: ResponsableDeMovi.Datos) = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="light">
<meta name="theme-color" content="#F4F1EC">
<title>${esc(titulo)} · Movi</title>
<style>
$ESTILOS
</style>
</head>
<body>
<main>
$cuerpo
<nav aria-label="Más sobre Movi">
<a href="$RUTA_PRIVACIDAD">Privacidad</a>
<a href="$RUTA_TERMINOS">Términos de uso</a>
<a href="$RUTA_BORRAR_CUENTA">Borrar tu cuenta</a>
</nav>
<p class="meta" style="margin-top:12px">Contacto: ${responsable.correoHtml()}</p>
</main>
</body>
</html>
""".trim()

    internal fun esc(s: String): String = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private fun ResponsableDeMovi.Datos.nombreHtml(): String =
        nombre?.let { "<strong>${esc(it)}</strong>" } ?: "<span class=\"marcador\">$SIN_RESPONSABLE</span>"

    private fun ResponsableDeMovi.Datos.correoHtml(): String =
        correo?.let { "<a href=\"mailto:${esc(it)}\">${esc(it)}</a>" } ?: "<span class=\"marcador\">$SIN_CORREO</span>"
}

/**
 * **Quién responde por Movi**, de las variables de entorno: `RESPONSABLE_LEGAL` (el nombre del
 * responsable del tratamiento) y `CORREO_DE_CONTACTO`. Mismo orden de resolución que el resto del
 * server (`ConfigDeCorreoEntrante`): propiedad del sistema (las pruebas), variable de entorno,
 * `server/.env`, `.env`. Se lee en cada pedido: cambiarla en Railway no exige tocar el código.
 *
 * Un correo que no tenga forma de correo se ignora (se muestra el marcador): mejor «escríbenos desde
 * la app» que un enlace roto.
 */
internal object ResponsableDeMovi {

    data class Datos(val nombre: String?, val correo: String?)

    fun leer(): Datos = Datos(
        nombre = resolver("movi.legal.responsable", "RESPONSABLE_LEGAL"),
        correo = resolver("movi.legal.correo", "CORREO_DE_CONTACTO")?.takeIf(::pareceCorreo),
    )

    internal fun pareceCorreo(s: String): Boolean = Regex("""^[^@\s<>"]+@[^@\s<>"]+\.[^@\s<>"]+$""").matches(s)

    private fun resolver(prop: String, envKey: String): String? {
        System.getProperty(prop)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val archivos = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return archivos.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
