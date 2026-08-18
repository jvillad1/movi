# Plan de arreglos tras el recorrido pantalla a pantalla (2026-08-17)

Origen: revisión conjunta de la web en producción (`f83e4c2`…`2116edc`), 18 pantallas, 56 ítems de feedback del dueño. El mapa completo con capturas vive como artefacto privado; este archivo es la versión ejecutable.

**Decisiones cerradas:** barra inferior = Inicio · Movimientos · + · Cuentas · Más (Presupuestos pasa a Más). Español **neutro latinoamericano (tuteo)** en todo texto de usuario y en el prompt de la IA — regla de proyecto. Cuentas = solo plata tuya (Dinero, Inversión); deudas en Créditos. Análisis se funde en el Inicio nuevo. Nada se recategoriza ni se confirma solo: la app propone, el dueño confirma.


Seis olas, cada una un PR (o dos) revisable y desplegable por separado. El orden es por daño: primero lo que miente o bloquea, después lo que arregla muchas pantallas de un golpe, al final lo nuevo. Cada ola cierra ítems del backlog y los marca «arreglado» en este mapa.


## Ola 1 — Verdad en los números y en los botones (M)

Cifras falsas y formularios que se niegan en silencio. Es lo que más rápido erosiona la confianza; ninguno es discutible.

- F36 signo perdido en toda la app (Crítico)
- F54 saldo inicial contado como ingreso del mes y como «primer movimiento»
- F12 lo anotado a mano nace «por confirmar» y no sale en Egresos
- F27 subir extracto no hace nada en la web (stub)
- F28 texto en vertical + «Conectado a 2 bancos» falso + auto-lectura falsa en web (SMS)
- F10 «Cargando cuentas…» cuando no hay ninguna; estado vacío con acción
- F14 · F23 · F24 · F34 · F53 un solo componente de monto (miles al escribir, solo dígitos) + botón que dice qué falta
- F19 · F41 Cuentas y Perfil alcanzables siempre

## Ola 2 — Componentes que arreglan muchas pantallas de una vez (M)

Cada uno es un cambio en un lugar que se ve en diez.

- F11 glifos escritos como texto → íconos Material (Individual, filtros, teclado, avatar IA, SMS…)
- F4 · F33 tuteo neutro en toda la app y en el prompt de la IA; regla de proyecto
- F37 X para cerrar en todas las hojas
- F22 flecha ‹ = volver a la pantalla anterior (pila), no a Inicio
- F29 · F30 barra inferior en SMS y Movi AI
- F18 «+» pasa a botón con texto; ancho completo cuando la lista está vacía
- F2 · F3 textos de recuperar contraseña
- F1 «Recordar mi correo» explícito
- F35 categoría: campo libre con sugerencias, compartido por Presupuestos, Movimiento, Recurrentes
- F31 la IA sin emojis

## Ola 3 — Sacar lo que promete y no cumple (S)

Un interruptor sin efecto o una fila sin acción hace dudar de todo lo demás. Sacar es rápido; construir queda anotado.

- F8 ocultar Individual/Familiar hasta que exista familia
- F21 · F26 sacar el «+» de Inversiones y Metas (Metas se construye en la ola 6)
- F43 · F44 · F45 Perfil: fuera cuestionario, PREMIUM·FAMILIAR, Familia, «cifrados», «alertas inteligentes»
- F5 punto rojo de la campana solo cuando hay algo; panel anclado
- F47 · F48 Editor de pantallas: sin salto, y se muda a Perfil › Administración
- F25 el selector «Cuenta del préstamo» desaparece del alta de crédito

## Ola 4 — Navegación e Inicio de alto nivel (L)

Es el cambio de forma más grande: el Inicio pasa a resumir, Análisis desaparece, Cuentas entra a la barra.

- F9 · F40 Inicio nuevo = balance neto, flujo del mes, próximos pagos, alertas; Análisis se funde ahí
- Barra: Inicio · Movimientos · + · Cuentas · Más (Presupuestos a Más)
- F19 Cuentas en Más y en la barra
- F6 · F7 guía compacta; paso 2 = «Anota tus pagos fijos» → Recurrentes + Créditos
- F13 búsqueda real en Movimientos
- F15 · F16 Presupuestos: chevron, «Editar presupuesto», «Gastado en agosto», «este mes»

## Ola 5 — Modelo: cuentas y créditos con sentido (L)

Cambia qué es una cuenta y qué es un crédito. Toca servidor, base y clientes; va después de que lo visible esté sano.

- F56 dos tipos de cuenta: Dinero e Inversión; deudas fuera de Cuentas (absorbe F49–F52)
- F20 Créditos = préstamos + tarjetas, un solo total de deuda; recordatorio de pago de tarjeta
- F50 Inversiones muestra las cuentas de inversión; se retira el modelo de «posiciones»
- F55 eliminar cuenta (con sus movimientos, confirmación)
- F17 renombrar presupuesto (con aviso de que el gasto se cruza por nombre)
- F39 suscripciones: nada nace activo, todo se confirma

## Ola 6 — Lo nuevo (L)

Funciones que hoy no existen. Cada una vale por sí sola y se puede reordenar.

- F42 · F46 editar perfil: alias, avatar de iniciales con color, cambiar contraseña
- F26 crear meta de ahorro (nombre, objetivo, cuenta, fecha; ahorrado desde el saldo de la cuenta)
- F38 alta manual de suscripción
- F32 imágenes en el chat de Movi AI (reusa la lectura de recibos/extractos)
- F5 panel de notificaciones con recordatorios y pendientes

## Anotado, no ahora

- Login con Google o passkeys — cuando entre una segunda persona
- Familia (cuentas compartidas) — mismo momento
- Cuestionario de arquetipo — la IA ya conoce tu situación por los datos
- Análisis real (tendencias, mes contra mes) — cuando haya meses de datos
- Detalle de junio de la Mastercard (import huérfano) — decidiste dejarlo

---

## Detalle de cada ítem (texto del dueño y respuesta acordada)


### F1 · Ingreso

> Un «recordar usuario» explícito: que la persona elija que el correo quede guardado y solo tenga que escribir la contraseña.

Hoy pasa implícitamente tras el primer ingreso, pero es invisible y no es una elección. Propuesta: casilla «Recordar mi correo en este dispositivo», marcada por defecto, junto al botón Entrar. Desmarcada, borra el recuerdo al cerrar sesión.


### F2 · Recuperar contraseña

> «Te enviamos un enlace…» suena a que ya se hizo, y todavía no ingresé el correo. Debería decir qué va a pasar, y avisar recién cuando el enlace se haya enviado.

Correcto: es tiempo verbal equivocado. Antes de enviar: «Escribí tu correo y te mandamos un enlace para elegir una contraseña nueva. Vence en 1 hora y sirve una sola vez.» Después del envío exitoso, ese texto se reemplaza por la confirmación del servidor («Si el correo está registrado, te enviamos un enlace…») y el campo y el botón se ocultan — una sola cosa en pantalla por vez.


### F3 · Recuperar contraseña

> «Volver a entrar» es un texto raro, no queda claro qué hace.

Vuelve al formulario de ingreso. Pasa a «← Volver al ingreso». Aplica también al mismo botón del panel de contraseña nueva.


### F4 · Registro

> Los textos de la aplicación hablan en argentino («Registrate», «Repetila», «Entrá», «Creá», «tenés»). Que quede más neutro.

Es transversal, no de esta pantalla: toda la app está en voseo rioplatense. Pasa a tuteo neutro latinoamericano — «Regístrate», «Repítela», «Entra», «Crea tu primera cuenta», «¿No tienes cuenta?», «Copia el saldo…», «Deja que la app se llene sola», «por ti» en vez de «por vos». Medido: ~30 textos entre la web, las pantallas Compose y los mensajes del servidor. Se arregla en un solo pase, y queda como regla del proyecto para lo que se escriba de acá en adelante — los comentarios del código pueden seguir como están, esto es solo lo que la persona lee en pantalla.


### F5 · Inicio (Dashboard)

> El snackbar de «Sin notificaciones por ahora» se ve feo, sobre todo la posición.

Hoy la campana solo dispara ese snackbar: no hay pantalla de notificaciones detrás. Propuesta: al tocarla, un panel pequeño anclado a la campana con el estado vacío bien puesto («Nada nuevo por ahora») y, cuando haya recordatorios o candidatos pendientes, la lista ahí. Y el punto rojo solo cuando de verdad haya algo — hoy se muestra siempre.


### F6 · Inicio (Dashboard)

> Genial «Crea tu primera cuenta» y «préstamos o tarjetas», pero ¿dónde se cargan obligaciones como «Colegio de mi hija» o «Gimnasio», que no son ni préstamo ni tarjeta?

Existen: son «Recurrentes» (pagos fijos del mes) y viven en Más → Recurrentes. Los recordatorios ya los usan. El problema es que la guía no los menciona. Propuesta: el paso 2 pasa a «Anota tus pagos fijos del mes» con dos accesos — Recurrentes (colegio, gimnasio, arriendo, servicios) y Créditos (préstamos y tarjetas, donde Movi además calcula cuotas e intereses).


### F7 · Inicio (Dashboard)

> Los primeros pasos tapan el scroll de lo demás; queda un área muy pequeña para el resto del contenido.

Cierto, y en pantalla ancha peor. Propuesta: la guía arranca **compacta** — una sola fila «Primeros pasos · 0 de 3 ›» que se despliega al tocarla — y se despliega sola solo la primera vez que entras. Así el Balance y las cuentas quedan visibles siempre.


### F8 · Inicio (Dashboard)

> Individual y Familiar muestran lo mismo. Sería bueno entender cómo juegan ambas vistas o qué va en una versus la otra; tal vez un onboarding.

Respuesta honesta: hoy ese selector **no hace nada** — el servidor recibe el valor y lo ignora, porque la parte «familiar» (cuentas compartidas, otra persona con acceso) nunca se construyó. Un interruptor sin efecto erosiona la confianza en el resto. Propuesta: **ocultarlo** hasta que exista la función de familia. Cuando entre una segunda persona (mismo momento en que se justifica el login con Google) vuelve con un significado real y una explicación al lado.


### F9 · Inicio (Dashboard)

> El Dashboard podría ser más de alto nivel: tiene muchos detalles. Lo demás por menú — «Más» está genial — pero llevaría a la navegación principal las opciones más usadas por una persona promedio.

De acuerdo, y es el cambio más grande de la ronda. Propuesta para discutir antes de tocar nada: **Inicio** = balance neto, flujo del mes, próximos pagos (recordatorios) y alertas; el detalle de cuentas y movimientos se va a sus pantallas. **Barra inferior** = Inicio · Movimientos · + · Pagos (recurrentes + cuotas de créditos, lo que vence) · Más. La lista de «Mis cuentas» sale del Inicio y queda en Cuentas. Como el Inicio se define desde el servidor (SDUI), parte se puede reordenar sin desplegar; lo que no exista como sección hay que construirlo.


### F10 · Movimientos

> No puedo agregar movimientos.

Es consecuencia de no tener cuentas: un movimiento necesita una cuenta a la que pertenecer, y «Agregar» abre el formulario con el selector de cuenta **vacío**. Peor: el selector dice «Cargando cuentas…» — que es mentira, no está cargando, no hay ninguna. Arreglo en dos partes: (1) sin cuentas, el formulario lo dice de frente («Primero crea una cuenta donde anotar este movimiento») con el botón para crearla ahí mismo; (2) el estado vacío de Movimientos deja de ser texto suelto y ofrece la acción — «+ Registrar el primero» si hay cuentas, «Crear una cuenta primero» si no.


### F11 · Movimientos

> Los íconos de los filtros Todo / Egresos / Ingresos / Pendientes están rotos.

Es un solo carácter: el tilde ✓ del filtro activo está escrito como texto y la fuente de la web no lo tiene — mismo cuadrado que el de «Individual» en el Inicio. Pasa a un ícono de Material (Check), que sí existe en las tres plataformas. De paso barro toda la app buscando otros glifos escritos como texto.


### F12 · Movimientos

> ¿Qué es Pendientes?

Son movimientos «por confirmar»: los que entraron solos —un SMS del banco que la app leyó, o una foto de recibo— y que esperan que revises monto y categoría antes de contar. La idea es buena, el nombre no dice eso: pasa a **«Por confirmar»**. Y encontré un problema real al mirar esto: un movimiento que anotas **a mano** también nace «por confirmar», así que caería en ese filtro y **no aparecería bajo Egresos**, que excluye los pendientes. Lo anotado a mano ya está confirmado por definición — se arregla en la misma pasada.


### F13 · Movimientos

> El buscador de movimientos no hace nada. ¿Qué debería hacer?

Nada, literalmente: la lupa es un dibujo sin acción. Lo que debería hacer: al tocarla, un campo de texto arriba que filtra la lista mientras escribes, por descripción, comercio y categoría («Frisby», «Mercado», «Netflix»), combinable con los filtros de tipo. Sin ir al servidor: la lista ya está en pantalla. Con historial de años es la única forma de encontrar «cuánto pagué de colegio en marzo».


### F14 · Presupuestos

> Conservar los separadores de miles y millones en el input, como pasa al guardar.

Hoy el campo muestra los dígitos crudos («$2000000») y recién al guardar se formatea. Pasa a formatear mientras se escribe («$2.000.000»), en este teclado y en el de Agregar movimiento, que es el mismo componente.


### F15 · Presupuestos

> Una vez creado el presupuesto no es claro que al tocarlo se edita.

Nada en la tarjeta lo insinúa. Un chevron › a la derecha (el mismo idioma que ya usa la guía de primeros pasos) y, en la hoja que abre, el título «Editar presupuesto» en vez de repetir el de crear.


### F16 · Presupuestos

> Tampoco es claro por cuánto tiempo es el presupuesto.

Es mensual y se reinicia solo el día 1, pero la única pista es un «Límite mensual · COP» chiquito bajo el monto. Debería decirlo donde importa: el encabezado pasa a «Gastado en agosto» (el mes en curso, con nombre) y cada tarjeta a «$0 de $2.000.000 este mes».


### F17 · Presupuestos

> No puedo editar el nombre, solo el monto. Debería poder ambos.

Es una limitación técnica que se filtró a la pantalla: la categoría **es la clave** del presupuesto en la base de datos, así que renombrarlo hoy sería borrar uno y crear otro. Se arregla del lado del servidor (renombrar en una sola operación que borra y crea sin dejar hueco) y la hoja de edición gana el campo de nombre. Ojo con una consecuencia: el gasto se cruza por **nombre de categoría** con los movimientos, así que renombrar «Mercado» a «Supermercado» deja de contar los movimientos que digan «Mercado». Hay que avisarlo en la hoja.


### F18 · Presupuestos

> El «+» parece muy pequeño; me gusta más con texto adicional o solo texto para agregar. Revisa si hace sentido.

Hace sentido, y no solo acá: el mismo «+» chiquito arriba a la derecha aparece en varias pantallas. Propuesta: botón con texto «+ Nuevo presupuesto» — abajo, ancho completo, cuando la lista está vacía (que es cuando más se necesita), y arriba a la derecha como texto cuando ya hay tarjetas. La misma regla para Recurrentes, Créditos y Metas.


### F19 · Más

> Deberíamos agregar en el menú principal la pantalla de Cuentas.

De acuerdo. Entra en Más como primer acceso, y además queda en la propuesta de barra inferior de F9 — hoy solo se llega desde «Ver todas» en el Inicio, que es un camino escondido para algo que se usa siempre. **Verificado al intentar llegar:** con la instancia vacía no hay ningún camino a Cuentas — el «Ver todas +» del Inicio solo aparece cuando ya existe al menos una cuenta. La entrada tiene que ser incondicional.


### F20 · Más

> Créditos debe incluir libranza, libre inversión, tarjetas y todo tipo de crédito.

Hoy Créditos muestra **solo préstamos** (libranza y libre inversión entran ahí, son el mismo tipo con distintos términos). Las **tarjetas de crédito quedan afuera**: son otro tipo de cuenta y aparecen únicamente en Cuentas, sin cupo, sin fecha de corte ni de pago. Propuesta: Créditos pasa a ser «todo lo que debes» — préstamos con su cuota, tasa y plazo, y tarjetas con cupo, saldo usado, corte y pago. Cada tipo con su propia forma de crearse (una tarjeta no tiene «capital original» ni «plazo»), pero una sola pantalla y un solo total de deuda. Los recordatorios de pago cubren entonces también la fecha de pago de la tarjeta, que hoy no.


### F21 · Inversiones

> En Inversiones no me permite agregar una nueva.

Confirmado leyendo el código: el «+» de arriba es **un dibujo sin acción**, y detrás no existe ni la hoja de alta ni el endpoint para crearla — el servidor solo sabe listar posiciones. Es la única sección de Más donde el «+» no hace nada. Dos caminos: construir el alta (nombre, tipo —CDT, fondo, acciones, cripto—, monto invertido, valor actual) o, mientras no exista, **sacar el «+»** para no prometer lo que no hay. Recomiendo lo segundo ahora y lo primero cuando toque, porque una inversión sin valor actualizado a mano no aporta mucho más que una cuenta de tipo Inversión, que ya existe en Cuentas.


### F22 · Inversiones

> La flecha para navegar atrás me lleva a Inicio; si entré desde «Más» debe volver a «Más» para ver otras secciones.

Es un error de fábrica repetido: Inversiones, Créditos y Metas tienen la flecha cableada **a Inicio a mano**, mientras que Suscripciones vuelve a Más. Y hay algo mejor que corregir el destino uno por uno: la app ya lleva una pila de navegación (el botón físico de atrás en Android la usa) — la flecha debería simplemente **volver a la pantalla anterior**, sea cual sea. Se arregla en todas las pantallas de una vez.


### F23 · Créditos

> El formulario no valida los campos: algunos numéricos permiten letras, y los miles y millones deberían irse formateando al escribir — un número grande sin separadores no se lee.

Los campos de monto sí filtran letras (COP, capital, cuota, plazo, día), pero **dos no**: la tasa (acepta «12%») y la fecha (acepta cualquier cosa). Ninguno formatea al escribir. Arreglo: montos con separador de miles mientras se escribe (mismo componente que F14, un solo arreglo para toda la app); tasa que acepte solo dígitos y punto, con el «%» puesto por el campo y no por vos; fecha con selector de calendario en vez de texto libre.


### F24 · Créditos

> No permite guardar el crédito, el botón se queda en gris.

Causa exacta, verificada contra el código con tu captura: escribiste la tasa como **«12%»** y la fecha como **«2026/06/17»**. La tasa con el símbolo no se lee como número, y la fecha espera guiones (AAAA-MM-DD). Con eso el botón se apaga — pero **sin decir por qué**, que es el verdadero problema: un formulario que se niega en silencio. Arreglo en dos capas: los campos dejan de aceptar lo que no sirve (F23), y si igual falta algo, el botón dice qué («Falta la fecha de desembolso») en vez de solo ponerse gris.


### F25 · Créditos

> El campo «Cuenta del préstamo · + Nueva cuenta de préstamo» es extraño, ¿para qué sirve?

Es la estructura interna asomándose: un crédito por dentro es una cuenta tipo Préstamo más sus términos, y el selector sirve para adjuntar términos a una cuenta que ya existiera. Para quien crea un crédito nuevo es ruido. Arreglo: **desaparece** del flujo normal — el nombre y la deuda actual pasan a ser los primeros campos del formulario, sin sección aparte — y solo cuando existan cuentas de préstamo sin términos aparece, arriba, una línea discreta: «Ya tienes una deuda cargada como cuenta, ¿es esta?».


### F26 · Metas de ahorro

> En Metas no puedo agregar una meta.

Mismo caso que Inversiones (F21), verificado: el «+» es decorativo y el repositorio solo sabe **listar** metas — no existe crear. Son las dos únicas pantallas de Más así; Recurrentes sí crea. Acá la recomendación es distinta: una meta de ahorro (nombre, objetivo, cuenta donde se ahorra, fecha) **sí vale construirla** — es de lo que más engancha con el uso diario, sobre todo si el «ahorrado» sale solo del saldo de la cuenta elegida. Mientras tanto, el «+» se saca para no prometer.


### F27 · Extractos

> «Subir extracto» no funciona, no hace nada.

Confirmado y es grave: en la web el selector de archivos es **un stub vacío** — la implementación para navegador nunca se escribió, solo existe en Android y iOS. O sea que **desde la web hoy no se puede subir ningún extracto**, y la guía de primeros pasos manda justo ahí. Se arregla con un `<input type="file">` real (es lo que ya hace el login para el gestor de contraseñas: HTML nativo por fuera del canvas). Prioridad alta.


### F28 · SMS bancarios

> Esta vista de SMS es rara, no entiendo su propósito. Además el ícono de la derecha parece roto.

El propósito real: cuando el teléfono lee un SMS del banco («Compra por $45.000 en Éxito»), lo sube acá como **borrador**; vos lo confirmás —ajustando comercio o categoría si hace falta— y recién ahí se vuelve un movimiento. Es la bandeja de revisión de lo que entró solo. Nada de eso lo dice la pantalla: el título es técnico, «Conectado a 2 bancos» es un número fijo en el código (falso), y «Auto-lectura activa» en la web es falso también — acá solo se revisan. Propuesta: título «Mensajes del banco», subtítulo con la verdad («Los lee tu teléfono; acá los revisas antes de que cuenten»), y el conteo real de pendientes. El ícono roto es un glifo escrito como texto — va en F11. Y el bug del texto en vertical se arregla en la misma pasada.


### F29 · SMS bancarios

> No tiene menú, se pierde al navegar a esta pantalla.

Es la única sección de Más que pierde la barra inferior. Recupera la barra, y con F22 la flecha ‹ vuelve a Más en vez de a Inicio.


### F30 · Movi AI

> En Movi AI pasa lo mismo: se pierde el menú.

Igual que SMS (F29): recupera la barra inferior. Son las dos únicas pantallas de Más sin ella.


### F31 · Movi AI

> Los íconos en el chat se rompen.

Dos fuentes: el avatar del asistente es un glifo escrito como texto (✦, va en F11), y **la IA misma manda emojis** al final de sus respuestas — que la fuente de la web tampoco tiene. Lo segundo se corrige en el prompt («sin emojis») y, por si acaso, filtrando emojis del texto antes de pintarlo.


### F32 · Movi AI

> Sería bueno soportar carga de imágenes u otros recursos en el chat.

Tiene sentido y la base ya existe: el servidor ya lee imágenes con Claude para los extractos y para las fotos de recibo (OCR). Hoy el chat solo manda texto (`AiChatRequest` = lista de mensajes). Ampliar el mensaje para llevar una imagen adjunta y pasársela al modelo es trabajo acotado; el caso de uso natural es «acá va la foto del recibo / del extracto / de la oferta del banco, ¿qué opinas?». Y en la web necesita el mismo selector de archivos que hoy falta (F27) — se resuelven juntos.


### F33 · Movi AI

> (observación mía) La IA responde en voseo: «tenés», «podés», «Cargá».

El prompt del servidor le ordena tutear, pero **el prompt entero está escrito en voseo** («Sos Movi AI», «Hablás», «decilo», «respondé») y el modelo imita lo que lee. Se reescribe en tuteo neutro; entra en el mismo pase que F4.


### F34 · Recurrentes

> El formulario no valida campos y no formatea el monto ingresado.

Mismo arreglo que F14 y F23: un único componente de monto para toda la app, con separador de miles al escribir y solo dígitos; y el botón que dice qué falta en vez de quedarse gris.


### F35 · Recurrentes

> Estaría bueno autocompletar de categorías existentes o dejar escribir libremente.

Hoy es un texto libre que arranca en «Otros» sin ninguna ayuda. Propuesta: campo libre **con sugerencias** — las categorías predefinidas y las que ya usaste en movimientos y presupuestos, filtradas mientras escribes; y si escribes una nueva, se acepta. Vale el mismo componente para Presupuestos, Agregar movimiento y Cambiar categoría, así una categoría se llama igual en todos lados (importa: presupuestos y gastos se cruzan por nombre).


### F36 · Recurrentes

> (observación mía — CRÍTICO) «Flujo libre $2.000.000» con ingresos $0 y egresos $2.000.000: debería ser negativo.

Verificado en el código: `formatCOP` toma el valor absoluto y **nunca vuelve a poner el signo**. No es de esta pantalla — es de **toda la app**: el flujo del mes en el Inicio, el total de un día con más gastos que ingresos en Movimientos, cualquier saldo en rojo, todos se muestran como positivos. Un mes en rojo se ve en verde. Arreglo de una línea, y una barrida por todos los lugares que muestran diferencias para que además pinten el color correcto.


### F37 · Recurrentes

> Los bottom sheets deberían tener una X o algo para cerrarlos, ¿no te parece?

Sí. Hoy se cierran tocando afuera o arrastrando la manija, pero nada lo dice y en escritorio con mouse el gesto no es obvio. Todas las hojas (crear cuenta, movimiento, presupuesto, crédito, pago, ajustar saldo, categoría…) reciben una X arriba a la derecha. Es un solo componente compartido, así que es un cambio en un lugar.


### F38 · Suscripciones

> Suscripciones debería permitir agregar manualmente.

Hoy no existe crear a mano: el servidor solo sabe detectar, editar y borrar. Se agrega el alta (nombre, monto, día de cobro, cuenta) — mismo botón con texto que en las demás pantallas (F18).


### F39 · Suscripciones

> El escaneo de extractos y movimientos debe **sugerir** suscripciones, pero el usuario debe confirmarlas para no meter basura.

La estructura ya está: cada suscripción tiene un estado (**candidata**, confirmada, descartada, o «auto») y las de confianza media/baja ya nacen como candidatas. Lo que rompe la regla es que las de confianza **alta** entran como activas sin preguntar («AUTO»). Cambio: nada nace activo — todo lo detectado nace candidato, aparece en una sección «Detectadas · por confirmar» arriba, y vos confirmás o descartás de a una, igual que con los pagos de tarjeta. Lo descartado no se vuelve a proponer.


### F40 · Análisis

> ¿Tiene sentido una sección de Análisis? ¿No debería el Inicio resolver ese mismo propósito?

No lo tiene, así como está: hoy «Análisis» no analiza — es un índice con cifras (egresos del mes, y accesos a Presupuestos, Recurrentes, Inversiones, Créditos, Metas con su número al lado). Eso es exactamente el «Inicio de alto nivel» de F9. Decisión: **Análisis desaparece y su contenido pasa a ser el Inicio nuevo**; una sección de análisis vuelve a justificarse solo si algún día analiza de verdad — tendencias, mes contra mes, categorías en el tiempo, «en qué te fuiste de rango».


### F41 · Perfil

> Perfil debe ser accesible en todo momento, no desde «Más».

Ya lo es a medias: el avatar «J» arriba a la izquierda del Inicio abre Perfil — pero nada indica que sea tocable, y solo está en el Inicio. Propuesta: el avatar va en el encabezado de **todas** las pantallas principales y se ve como botón; Perfil sale de la grilla de Más.


### F42 · Perfil

> No puedo editar nada, es solo de lectura. No debería ser así.

Correcto, y es de fondo: en el servidor **no existe ningún endpoint para editar el perfil** — el usuario tiene id, correo, nombre y contraseña, y nada más. Hay que construir «editar perfil»: nombre/alias, avatar, cambiar contraseña. Todo lo demás de esta pantalla que parece editable no lo es porque no hay nada detrás.


### F43 · Perfil

> No me deja abrir el cuestionario financiero para el arquetipo.

Porque **el cuestionario no existe** en ninguna parte de la app: es una tarjeta que promete algo que nunca se construyó. Dos opciones: construirlo (5–6 preguntas → un perfil tipo «ahorrador conservador» que la IA use como contexto) o sacarlo. Recomiendo **sacarlo ahora** y anotarlo como idea — la IA ya conoce tu situación por tus datos reales, que valen más que un cuestionario.


### F44 · Perfil

> Dice PREMIUM y FAMILIAR pero nunca configuré ni lo uno ni lo otro, ni sé qué otras opciones hay.

Porque no hay opciones: **está fijo en el código**. No existen planes ni tipos de cuenta. Se saca. Si algún día hay familia (F8) o planes, vuelve con significado.


### F45 · Perfil

> No puedo tocar «Familia», que parece ser el tipo de cuenta.

«Familia», «Privacidad y datos» y «Notificaciones» son filas **sin acción** — decorado. Y dos dicen cosas falsas: «SMS y extractos cifrados» (no hay cifrado propio en el servidor; viajan por HTTPS y quedan en tu Postgres) y «Alertas inteligentes activas» (no existen). Se sacan las tres; «Notificaciones push» arriba, que sí funciona, se queda.


### F46 · Perfil

> No puedo poner imagen de perfil ni alias para mi cuenta.

Va con F42: alias (que reemplace las iniciales «JC» y el «Juan» del saludo) y foto. La foto necesita dónde guardarse — el servidor hoy no almacena archivos; lo más simple es un avatar de iniciales con color a elección, y foto real después si hace falta.


### F47 · Más

> El Editor de pantallas siempre «salta» al cargar Más: parece renderizarse después de todo lo demás.

Exacto: la grilla se pinta al instante y el Editor aparece después, cuando vuelve del servidor la respuesta de «¿este usuario es administrador?». Se arregla reservando el lugar (o no pintando la grilla hasta saber), y de todos modos con F48 deja de estar ahí.


### F48 · Más

> ¿Tiene sentido mantener el Editor de pantallas? ¿Para qué sirve, qué propósito tiene?

Sirve para esto: las secciones del Inicio (Balance, Ingresos/Egresos, Mis cuentas…) no están fijas en la app — las define el servidor, y el Editor permite reordenarlas, ocultarlas o cambiar textos **sin desplegar una versión nueva**. Es infraestructura para que yo pueda ajustar el Inicio rápido (y va a servir para F9). Pero es una **herramienta de administración**, no algo que vos uses en el día a día — no tiene sentido mezclada con Créditos y Metas. Decisión: la infraestructura se queda; el acceso **sale de Más** y va al final de Perfil, en una sección «Administración» que solo ves vos por ser admin. Si algún día no la uso, se borra.


### F49 · Crear cuenta

> ¿Qué es una cuenta «Efectivo»?

La plata física: lo que llevas en la billetera. Existe para que un retiro en cajero no desaparezca del mapa — sale de Ahorros y entra a Efectivo, y después anotas «almuerzo $25.000 en efectivo». Tiene sentido para quien lleva ese control; para quien no, es ruido. Se queda, pero el selector tiene que explicarlo en una línea: «Efectivo — la plata en tu billetera».


### F50 · Crear cuenta

> «Inversión»: ¿no hay ya un ítem de Inversiones? ¿Qué diferencia habría?

Ninguna que valga la pena: son **dos formas de la misma idea** que crecieron por separado. Una cuenta tipo Inversión (CDT, fondo, cajita de Nu) es un lugar donde está tu plata con un saldo — eso funciona. La sección Inversiones muestra «posiciones», otro modelo aparte que además no se puede crear (F21). Decisión: **se unifican** — Inversiones pasa a mostrar tus cuentas tipo Inversión con su saldo (patrimonio invertido = suma de ellas), y el modelo de «posiciones» se retira. Resuelve F21 de paso.


### F51 · Crear cuenta

> «Crédito»: ya tenemos Créditos, ¿qué diferencia hay?

Acá «Crédito» significa **tarjeta de crédito** — el nombre corto choca con la sección Créditos, que hoy son préstamos. Dos arreglos: el tipo pasa a llamarse **«Tarjeta de crédito»**, y con F20 la sección Créditos incluye tarjetas y préstamos, así «lo que debés» vive en un solo lugar sin importar por dónde lo creaste.


### F52 · Crear cuenta

> «Préstamo»: ¿qué es eso? ¿Tiene sentido acá?

Es la cuenta que vive **debajo** de un crédito (ver F25): si la creas desde acá, te queda un préstamo sin tasa, sin cuota, sin fecha — aparece en Créditos «sin términos» y no sirve para recordatorios. No tiene sentido como opción de este selector. Decisión: **sale del selector**; los préstamos se crean desde Créditos, con sus términos, en un solo paso. Y el selector completo queda así: **Cuentas = tu plata** (Efectivo, Ahorros, Corriente, Inversión, cada una con su línea de explicación) y una nota al pie: «¿Tarjetas y préstamos? Se cargan en Créditos».


### F53 · Crear cuenta

> El formulario de crear cuenta no le da formato a los números; debería, para hacerlo legible.

Es la cuarta pantalla con el mismo problema (F14 Presupuestos, F23 Créditos, F34 Recurrentes). Confirma la decisión: **un solo componente de monto** para toda la app — separador de miles mientras se escribe, solo dígitos, «$» puesto por el campo — y se reemplaza en las cuatro de una vez. Cero excepciones, así no vuelve a aparecer en la próxima pantalla que se construya.


### F54 · Crear cuenta

> Ya creé la cuenta, pero desaparecieron todas las opciones de primeros pasos. No debería ser así.

Bug, y con una raíz que también infla tus números: al crear la cuenta con $1.000.000, la app registra un movimiento «Saldo inicial» tipo ingreso — y ese movimiento (1) le dijo a la guía «ya hay un movimiento» y la apagó, y (2) **entró como «Ingresos del mes: $1.000.000»** en el Inicio y en el flujo. Abrir una cuenta con plata que ya tenías no es un ingreso de agosto. Arreglo: el saldo inicial no cuenta como flujo de caja (misma regla que ya aplicamos a la deuda inicial de un crédito) ni como «primer movimiento» para la guía. Prioridad alta: cada cuenta nueva distorsiona el mes.


### F55 · Crear cuenta

> Parece que no puedo eliminar la cuenta; ayúdame a hacerlo.

Confirmado: **borrar una cuenta no existe** — cero rutas de borrado en el servidor. Lo que la app te ofreció al intentarlo fue anular el movimiento «Saldo inicial»: por eso la cuenta quedó en $0 y «sin movimientos» mientras seguía existiendo. La borré yo directo en la base a tu pedido (cuenta, su evento y la anulación). Se construye «Eliminar cuenta» en el detalle, con confirmación, y borrando sus movimientos con ella — y anular el saldo inicial deja de ofrecerse como si fuera un movimiento cualquiera.


### F56 · Crear cuenta

> Simplifica el tipo de cuenta: no tiene sentido cruzar con créditos y otras cosas. En realidad solo existen dos tipos de cuentas, ¿no?

Sí, y lo verifiqué contra el código: Efectivo, Ahorros y Corriente se tratan **exactamente igual** en todos los cálculos — la distinción es solo una etiqueta. Decisión: en Cuentas quedan **dos tipos**: **Dinero** (ahorros, corriente, efectivo, Nequi, Nu — plata disponible) e **Inversión** (CDT, fondos — plata guardada). El banco o el nombre lo dice el campo de nombre. Las deudas no son cuentas: tarjetas y préstamos viven solo en Créditos. Reemplaza a F49–F52.
