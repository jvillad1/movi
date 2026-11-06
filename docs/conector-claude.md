# El conector de Movi para Claude

Movi tiene un servidor MCP remoto en `https://movi-project-production.up.railway.app/mcp` (en general,
`<PUBLIC_BASE_URL>/mcp`). Con él, Claude —en la web, la app de escritorio, el teléfono y Claude Code— lee tus
cifras de Movi con las mismas funciones que usa la app: «Te quedan», tus cuentas, tus movimientos, la lista de
Pagos, en qué se va la plata, tus créditos y tarjetas, y la caja hasta que te paguen.

**Solo lectura.** Claude puede leer; no puede anotar, borrar ni mover plata. No existe ninguna herramienta que
escriba, y el único permiso que el conector concede es `movi:leer`.

## Conectarlo (para el dueño)

### En Claude (web, escritorio y teléfono)

1. En Movi: Tú → Ajustes → **Movi en Claude** → «Copiar» la dirección.
2. En Claude: **Ajustes → Conectores → Agregar conector personalizado**. Pega la dirección y toca **Conectar**.
   Si te pregunta por el cliente OAuth, deja «Usar la identidad publicada de Claude» (CIMD); «Registrar
   automáticamente» (DCR) también sirve. «Usar tu propio cliente OAuth» no: Movi solo acepta a Claude.
3. Se abre la página de Movi «Permitir que Claude lea tus datos de Movi». Revisa que diga que vuelves a
   **claude.ai**, entra con tu correo y tu contraseña de Movi y toca **Permitir**.
4. En una conversación, prende el conector desde «+» → Conectores. Las herramientas de Movi están marcadas como
   de solo lectura, así que Claude las usa sin pedir permiso en cada llamada.

El conector que agregas en claude.ai también aparece en Claude Code.

### Solo en Claude Code

```bash
claude mcp add --transport http movi https://movi-project-production.up.railway.app/mcp
```

Después, en Claude Code, `/mcp` → movi → autenticar: se abre la misma página de Movi en el navegador y vuelve a
un `http://localhost:<puerto>/callback`.

### Desconectarlo

En Movi: Tú → Ajustes → Movi en Claude → **Desconectar** en la conexión. Corta el acceso al instante: la llamada
siguiente de Claude recibe 401. También lo cortan «Cerrar sesión en todos los aparatos» y restablecer la
contraseña, y borrar la cuenta se lleva todo lo del conector.

## Qué ve Claude

| Herramienta | Qué contesta | De dónde sale |
|---|---|---|
| `como_voy` | «Te quedan» y cuánto da por día, entró/salió, falta por pagar, Tu plata, apartado, ahorro, patrimonio | `datosDeLasSugeridas` (la `disponibleDelPeriodo` del héroe), `patrimonioDe` |
| `cuentas` | cada cuenta con su saldo de hoy (sin anulados) y los totales | `cuentasConSaldo` + `patrimonioDe` |
| `movimientos` | los de un rango o de los últimos N días, filtros, paginado | `filasDe` (la de Movi AI) |
| `pagos_del_periodo` | la lista de Pagos con lo que falta (y «unos $X» si la cuota cambia) | `checklistDelPeriodo` |
| `en_que_se_va` | gasto por categoría contra el período pasado a la misma altura | `datosDeLasSugeridas` |
| `creditos` | créditos (tasa, cuota, la última cuota) y tarjetas (cupo, mínimo, extracto) | `creditosDe`, `tarjetasDe`, `planDelCredito` |
| `caja_hasta_que_te_paguen` | la caja día a día hasta el salario | `cajaProyectadaDe` |

Cada respuesta trae `structuredContent` (JSON) y un texto con una línea de resumen y el mismo JSON. Los montos
van en pesos enteros con su moneda.

**Lo que no sale nunca:** el texto de un SMS, una notificación o un correo; contraseñas o tokens; datos de otra
persona. Los nombres de comercio, las descripciones y los nombres de cuenta salen en una línea, cortados a 120
caracteres y con todo número de cinco dígitos o más reducido a sus últimos cuatro («Ahorros ••5678»). Movi
guarda un registro de cada llamada (quién, qué herramienta, cuándo y cuánto pesó la respuesta), sin lo que se
preguntó ni lo que se contestó.

## Cómo funciona (para quien lo mantiene)

- **OAuth 2.1 con PKCE S256**, con Movi como su propio servidor de autorización: `/.well-known/oauth-protected-resource`
  (RFC 9728), `/.well-known/oauth-authorization-server` (RFC 8414), `/oauth/authorize`, `/oauth/token`,
  `/oauth/register` (DCR). Clientes por CIMD (el `client_id` es una URL https que se baja y se valida) o DCR. El
  código dura 5 minutos y es de un uso; el token de acceso, 1 hora; el de refresco, 30 días, rotado en cada uso.
- **Tokens propios y opacos** (`mvc_a_…`, `mvc_r_…`), guardados solo como SHA-256 con su audiencia (la URL del
  MCP). El JWT de la app no entra a `/mcp` y estos tokens no entran a `/api/…`.
- **MCP sin estado** sobre Streamable HTTP: cada `POST /mcp` es un mensaje JSON-RPC con su token y recibe su
  respuesta en JSON. El protocolo está escrito a mano (`server/…/conector/ServidorMcp.kt`): el SDK oficial de
  Kotlin pide Ktor 3.5 y Kotlin 2.4.
- **Solo para Claude** (revisiones de seguridad): el `client_id` CIMD tiene que ser exactamente uno de los dos de
  Claude —`https://claude.ai/oauth/mcp-oauth-client-metadata` (web, escritorio, teléfono) y
  `https://claude.ai/oauth/claude-code-client-metadata`— o uno de `CONECTOR_CIMD_PERMITIDOS`. Se baja por https,
  con un tope de 5 s para toda la lectura y 16 KB, en cuatro hilos propios (nunca los de la base), una lectura a la
  vez. Un documento bueno se vuelve a pedir a los 10 minutos y, si en ese momento claude.ai no contesta, se sigue
  usando el último bueno; también si contesta un 404 o una página HTML (con un WARN). Solo sin un documento bueno se
  recuerda la falla, 5 minutos. Los dos se
  comprobaron el 6-nov-2026 con el `HttpClient` de Java: contestan su JSON, sin desafío de Cloudflare (desde la
  Mac del dueño; falta verlo desde Railway).
- **DCR idempotente**: el `client_id` sale de los retornos declarados, así que registrar lo mismo da el mismo
  cliente (a lo sumo siete filas en total).
- **La pantalla de permiso no escribe nada**: la solicitud viaja firmada con HMAC (`CONECTOR_SECRETO_DE_SOLICITUDES`
  o una clave derivada de `JWT_SECRET`) y vence a los 10 minutos. Reenviarla solo vuelve a pedir la contraseña: cada
  «Permitir» da un código nuevo, de un uso. La pantalla dice «Sigue solo si tú empezaste a conectar Movi desde tu
  Claude. Si alguien te mandó este enlace, ciérralo.» Las únicas direcciones de retorno son `https://claude.ai/api/mcp/auth_callback` y el
  loopback de Claude Code (`http://localhost|127.0.0.1:<puerto>/callback`), en DCR y en CIMD. La pantalla de
  consentimiento dice «Claude» o «Claude Code» según el retorno, nunca el nombre que el cliente dice tener.
- **Revocar**: `POST /oauth/revoke` (RFC 7009, anunciado como `revocation_endpoint`). Quitar el conector en
  Claude corta la conexión en Movi.
- **El reuso de un refresco**: Claude cambia el refresco en cada uso. Si vuelve a mandar uno ya cambiado dentro de
  2 minutos (dos pedidos casi a la vez, o una respuesta que se perdió), Movi contesta `invalid_grant`, revoca los
  tokens de la rotación que quedó huérfana y deja un WARN, sin cortar la concesión. Pasados los 2 minutos lo toma como un token copiado y **corta la conexión**: si el dueño dice «se
  desconectó solo», es la causa probable, y la solución es volver a conectar.
- **Vida de una conexión**: **un año** desde que se conectó, se use o no; después hay que volver a entrar desde
  Claude (la pantalla «Movi en Claude» lo dice). Sin uso, se acaba sola a los 30 días (vence el refresco). El barrido
  diario borra las conexiones con 90 días sin uso o revocadas hace 90 días, y las llamadas de más de 90 días.
- **Límites**: 60 llamadas por minuto por conexión en `/mcp` (429 con `Retry-After: 60`), y 60 respuestas 401 por
  minuto por IP (las IP de Anthropic, 1.200): pasado eso, `/mcp` contesta 429 a esa IP sin ir a la base. En
  `/oauth/token` y `/oauth/revoke`, primero la forma del `client_id` (si no es un CIMD de Claude ni un id de DCR,
  `invalid_client`), después 120 por minuto por IP (el `/64` en IPv6; 1.200 para el rango de salida de Anthropic,
  `160.79.104.0/21`, más `CONECTOR_RANGOS_DE_ANTHROPIC`, con un WARN si alguna recibe un 429), y 20 por conexión
  **solo** para pedidos con una credencial vigente de ese cliente (sin usar o sin rotar, sin revocar, sin vencer). No
  hay balde por cliente: con CIMD el `client_id` es el mismo para todos los usuarios de Claude, y un balde así lo
  llenaba cualquiera con su propia cuenta. `/oauth/authorize` 60 por minuto por IP, el login 30 por
  IP cada 5 minutos más el balde por correo de `/api/auth/login`, `/oauth/register` 30 por minuto por IP (300 para las de Anthropic); el registro es idempotente y dos iguales a la vez dan el mismo cliente. Todos esos
  baldes viven en un limitador propio de como mucho 10.000 claves (del orden de 25–30 MB en el peor caso).
- **Sin chequeo de `Origin` en `/mcp`**, a propósito: la spec lo pide contra el DNS rebinding, que sirve para
  colarse en un servidor que confía en cookies o en la red local. `/mcp` exige un token en cada pedido y no lee
  ninguna cookie, así que una página que lograra mandarle un pedido no tiene con qué autenticarse. Tampoco se
  rechaza un `MCP-Protocol-Version` desconocido: rechazarlo podría romper a Claude si habla una spec más nueva.
- **Privacidad de los textos**: los montos («$1.500.000», «1.234.567,89») y las fechas («2026-10-01»,
  «01/10/2026») salen tal cual; los demás números de 5+ dígitos, a sus últimos cuatro. Los filtros `texto` y
  `cuenta` de `movimientos` buscan sobre ese mismo texto recortado.
- Código: `server/src/main/kotlin/com/jvillada/movi/server/conector/` y `routes/Conector*Routes.kt`.

## Probarlo a mano con `curl`

Con el server local (`./gradlew :server:run`, puerto 8080) y `PUBLIC_BASE_URL=http://localhost:8080`:

```bash
B=http://localhost:8080

# 1. Sin token: 401 y dónde está la metadata.
curl -si -X POST $B/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | grep -i www-authenticate

# 2. Los documentos de descubrimiento.
curl -s $B/.well-known/oauth-protected-resource | jq
curl -s $B/.well-known/oauth-authorization-server | jq

# 3. Registrar un cliente (DCR) con un retorno de loopback (el único tipo que vale además del de claude.ai).
CLIENTE=$(curl -s -X POST $B/oauth/register -H 'Content-Type: application/json' \
  -d '{"client_name":"Prueba","redirect_uris":["http://localhost/callback"]}' | jq -r .client_id)

# 4. PKCE.
VERIFICADOR=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
DESAFIO=$(printf %s "$VERIFICADOR" | openssl dgst -binary -sha256 | openssl base64 | tr '+/' '-_' | tr -d '=\n')

# 5. Abrir esto en el navegador, entrar con el usuario demo y «Permitir».
echo "$B/oauth/authorize?response_type=code&client_id=$CLIENTE&redirect_uri=http%3A%2F%2Flocalhost%3A9999%2Fcallback&code_challenge=$DESAFIO&code_challenge_method=S256&scope=movi%3Aleer&state=x"
#    El navegador termina en http://localhost:9999/callback?code=mvc_c_…&state=x&iss=… (no carga: copia el code).
CODIGO=mvc_c_...

# 6. Canjear el código.
curl -s -X POST $B/oauth/token \
  -d grant_type=authorization_code -d code=$CODIGO -d code_verifier=$VERIFICADOR \
  -d client_id=$CLIENTE -d redirect_uri=http://localhost:9999/callback | jq
ACCESO=mvc_a_...

# 7. Herramientas.
curl -s -X POST $B/mcp -H "Authorization: Bearer $ACCESO" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | jq '.result.tools[].name'
curl -s -X POST $B/mcp -H "Authorization: Bearer $ACCESO" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"como_voy","arguments":{}}}' | jq .result.structuredContent

# 8. Revocar (lo que hace Claude al quitar el conector).
curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/oauth/revoke -d token=$ACCESO -d client_id=$CLIENTE
```

Al terminar, para el server por su puerto: `lsof -ti :8080 | xargs kill`.

## Fuera de esta vuelta

- El `RateLimiter` de la app (el del login por correo, `login:email:`, que el conector comparte con `/api/auth/login`)
  no tiene tope de claves. Un atacante con muchos `/64` de IPv6 podría crear millones por hora. Ya pasaba con
  `/api/auth/login` antes del conector; queda para otra vuelta.
