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
- **Solo para Claude** (revisión de seguridad): el CIMD solo se baja de `claude.ai` o `claude.com`
  (`CONECTOR_HOSTS_DE_CIMD` agrega otros), por https al 443, con un tope de 5 s para toda la lectura y 16 KB, en
  cuatro hilos propios (nunca los de la base), una lectura a la vez por cliente y el resultado (bueno o malo) en
  memoria unos minutos. Las únicas direcciones de retorno son `https://claude.ai/api/mcp/auth_callback` y el
  loopback de Claude Code (`http://localhost|127.0.0.1:<puerto>/callback`), en DCR y en CIMD. La pantalla de
  consentimiento dice «Claude» o «Claude Code» según el retorno, nunca el nombre que el cliente dice tener.
- **Revocar**: `POST /oauth/revoke` (RFC 7009, anunciado como `revocation_endpoint`). Quitar el conector en
  Claude corta la conexión en Movi.
- **El reuso de un refresco**: Claude cambia el refresco en cada uso. Si vuelve a mandar uno ya cambiado dentro de
  2 minutos (dos pedidos casi a la vez, o una respuesta que se perdió), Movi contesta `invalid_grant` sin cortar
  nada. Pasados los 2 minutos lo toma como un token copiado y **corta la conexión**: si el dueño dice «se
  desconectó solo», es la causa probable, y la solución es volver a conectar.
- **Vida de una conexión**: una conexión sin uso en 90 días deja de servir y el barrido diario la borra (con sus
  tokens). El registro de llamadas guarda 90 días.
- **Límites**: 60 llamadas por minuto por conexión en `/mcp` (429 con `Retry-After: 60`); `/oauth/token` 1.000 por
  minuto por cliente y 20 por conexión (no por IP: todos los usuarios de Claude llegan desde las IP de Anthropic);
  `/oauth/register` 300 por hora en total; el login comparte el balde por correo de `/api/auth/login`. Topes
  globales de 2.000 solicitudes pendientes y 2.000 clientes DCR: lleno, 503 con un WARN en el log.
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
