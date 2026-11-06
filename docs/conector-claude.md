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
   automáticamente» (DCR) también sirve.
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
- **Límite**: 60 llamadas por minuto por conexión; pasado eso, 429 con `Retry-After: 60`.
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

# 3. Registrar un cliente (DCR) con un retorno de loopback.
CLIENTE=$(curl -s -X POST $B/oauth/register -H 'Content-Type: application/json' \
  -d '{"client_name":"Prueba","redirect_uris":["http://localhost:9999/callback"]}' | jq -r .client_id)

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
```

Al terminar, para el server por su puerto: `lsof -ti :8080 | xargs kill`.
