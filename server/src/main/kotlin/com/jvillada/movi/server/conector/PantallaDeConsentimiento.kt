package com.jvillada.movi.server.conector

import com.jvillada.movi.server.compartir.PaginaCompartida.esc

/**
 * # Vuelta 135 · «Permitir que Claude lea tus datos de Movi»
 *
 * La página que abre Claude al conectar: entrar con el correo y la contraseña de Movi y permitirlo. Es HTML
 * liviano servido por el server (como la página compartida), sin la app ni scripts. La sesión de la app no sirve
 * acá: vive en el almacenamiento del navegador de la app, no en una cookie que esta página pueda ver, y es mejor
 * así (una cookie de sesión sería la puerta de un CSRF).
 *
 * **Lo que la página tiene que decir sí o sí** (MCP, «Security Best Practices»): quién pide, **a qué host vuelve**
 * —lo único que el cliente no puede falsear— y qué podrá hacer. Todo texto que viene del cliente se escapa.
 *
 * La sirven las rutas con `frame-ancestors 'none'` y `X-Frame-Options: DENY` (nadie la puede meter en un iframe
 * para robar el clic) y sin caché.
 */
internal object PantallaDeConsentimiento {

    const val QUE_PODRA_HACER = "Claude podrá leer tus cuentas, movimientos y pagos. No podrá anotar, borrar ni mover plata."
    const val DONDE_SE_DESCONECTA = "Puedes desconectarlo cuando quieras en Movi: Tú → Movi en Claude."
    const val CREDENCIALES_MALAS = "El correo o la contraseña no coinciden."
    const val DEMASIADOS_INTENTOS = "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."

    /** Los colores de `Tokens.kt` (la misma paleta que la página compartida). */
    private val ESTILOS = """
:root { --fondo: #F4F1EC; --tarjeta: #FFFFFF; --borde: #E7E1D7; --texto: #1F1B16; --texto-medio: #5C554C;
  --marca: #FFC21A; --sale: #B3261E; color-scheme: light; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--fondo); color: var(--texto); font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  font-size: 16px; line-height: 22px; }
main { max-width: 480px; margin: 0 auto; padding: 28px 16px 40px; }
h1, p { margin: 0; }
.marca { display: inline-block; padding: 2px 10px; border-radius: 999px; background: var(--marca); font-weight: 800; }
h1 { margin-top: 18px; font-size: 26px; line-height: 32px; font-weight: 800; overflow-wrap: anywhere; }
.tarjeta { margin-top: 18px; background: var(--tarjeta); border-radius: 22px; padding: 18px 16px; }
.apoyo { color: var(--texto-medio); font-size: 14px; line-height: 20px; margin-top: 8px; }
.host { font-weight: 800; overflow-wrap: anywhere; }
.error { color: var(--sale); margin-top: 12px; }
label { display: block; margin-top: 14px; font-weight: 600; }
input { display: block; width: 100%; margin-top: 6px; padding: 12px; font-size: 16px; border: 1px solid var(--borde);
  border-radius: 12px; background: var(--tarjeta); color: var(--texto); }
.botones { display: flex; flex-direction: column; gap: 10px; margin-top: 20px; }
button { min-height: 48px; padding: 12px 16px; border-radius: 999px; font-size: 16px; font-weight: 700; border: 0; cursor: pointer; }
.principal { background: var(--marca); color: var(--texto); }
.secundario { background: transparent; color: var(--texto); border: 1px solid var(--borde); }
""".trim()

    private fun pagina(titulo: String, cuerpo: String): String = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${esc(titulo)}</title>
<style>$ESTILOS</style>
</head>
<body><main>
<p class="marca">Movi</p>
$cuerpo
</main></body>
</html>
""".trim()

    /**
     * La pantalla para entrar y permitir. [solicitud] es la solicitud firmada (ver `SolicitudFirmada`); [correo]
     * vuelve escrito si las credenciales no coincidieron.
     */
    fun pedirPermiso(solicitud: String, nombreDelCliente: String, hostDelRetorno: String, error: String? = null, correo: String = ""): String =
        pagina(
            "Conectar con Movi",
            buildString {
                append("<h1>Permitir que ${esc(nombreDelCliente)} lea tus datos de Movi</h1>")
                append("<div class=\"tarjeta\">")
                append("<p>Después de permitirlo, vuelves a <span class=\"host\">${esc(hostDelRetorno)}</span>.</p>")
                append("<p class=\"apoyo\">${esc(QUE_PODRA_HACER)}</p>")
                append("<p class=\"apoyo\">${esc(DONDE_SE_DESCONECTA)}</p>")
                append("</div>")
                append("<form class=\"tarjeta\" method=\"post\" action=\"/oauth/authorize\">")
                append("<input type=\"hidden\" name=\"solicitud\" value=\"${esc(solicitud)}\">")
                append("<p>Entra con tu cuenta de Movi</p>")
                if (error != null) append("<p class=\"error\" role=\"alert\">${esc(error)}</p>")
                append("<label for=\"correo\">Correo</label>")
                append("<input id=\"correo\" name=\"correo\" type=\"email\" autocomplete=\"username\" required value=\"${esc(correo)}\">")
                append("<label for=\"contrasena\">Contraseña</label>")
                append("<input id=\"contrasena\" name=\"contrasena\" type=\"password\" autocomplete=\"current-password\" required>")
                append("<div class=\"botones\">")
                append("<button class=\"principal\" type=\"submit\" name=\"decision\" value=\"permitir\">Permitir</button>")
                append("<button class=\"secundario\" type=\"submit\" name=\"decision\" value=\"cancelar\" formnovalidate>Cancelar</button>")
                append("</div>")
                append("</form>")
            },
        )

    /** Lo que se ve cuando la solicitud no sirve y no hay a dónde volver con el error. */
    fun noSePuede(motivo: String): String = pagina(
        "No se pudo conectar",
        "<h1>No se pudo conectar con Movi</h1><div class=\"tarjeta\"><p>${esc(motivo)}</p>" +
            "<p class=\"apoyo\">Vuelve a Claude y conecta otra vez.</p></div>",
    )
}
