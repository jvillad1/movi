// Opt-in de Web Push. El estado se cachea en _status para que el interop wasm
// (síncrono) pueda leerlo; enable()/disable()/init() lo refrescan async.
(function () {
    var _status = 'unsupported';
    // El endpoint de la suscripción viva, recordado en cada refresh(). Existe por una sola
    // razón: al cerrar sesión hay que mandar el DELETE **sin poder esperar a nada**, porque la
    // página se recarga enseguida (ver olvidarAlSalir). Pedirle el endpoint al ServiceWorker en
    // ese momento es una promesa que la recarga se lleva puesta.
    var _endpoint = '';

    // El token, sin poder tirar NUNCA. `localStorage` no es una propiedad que siempre
    // esté: con el almacenamiento del sitio bloqueado, tocarla tira SecurityError.
    //
    // Y esto no era un detalle cosmético: `supported()` la llama el interop de wasm de
    // forma SÍNCRONA (ver PushOptIn.wasmjs.kt), o sea que la excepción entraba a Kotlin
    // en mitad de una composición. Medido: con el almacenamiento bloqueado, tocar
    // «Recurrentes» o el avatar → Perfil no solo no abría la pantalla — dejaba la
    // navegación CLAVADA, sin responder a ningún otro toque. Y como en ese modo la
    // sesión vive solo en memoria, recargar para descongelar te devolvía al login.
    // O sea: el dueño entra, toca Recurrentes, y queda afuera otra vez.
    //
    // Sin token no hay push que valga, así que "no se pudo leer" y "no hay" se tratan
    // igual: `false` / cadena vacía. Es la misma degradación silenciosa que ya tenía
    // esta función para un navegador sin ServiceWorker.
    function token() {
        try { return localStorage.getItem('auth_token') || ''; } catch (e) { return ''; }
    }

    function supported() {
        return 'serviceWorker' in navigator && 'PushManager' in window && !!token();
    }

    function b64ToU8(b64) {
        var pad = '='.repeat((4 - b64.length % 4) % 4);
        var raw = atob((b64 + pad).replace(/-/g, '+').replace(/_/g, '/'));
        var arr = new Uint8Array(raw.length);
        for (var i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
        return arr;
    }

    async function refresh() {
        if (!supported()) { _status = 'unsupported'; _endpoint = ''; return _status; }
        if (Notification.permission === 'denied') { _status = 'denied'; return _status; }
        try {
            var reg = await navigator.serviceWorker.getRegistration('push-sw.js');
            var sub = reg ? await reg.pushManager.getSubscription() : null;
            _status = sub ? 'enabled' : 'disabled';
            _endpoint = sub ? sub.endpoint : '';
        } catch (e) { _status = 'disabled'; }
        return _status;
    }

    async function enable() {
        if (!supported()) return refresh();
        try {
            var perm = await Notification.requestPermission();
            if (perm !== 'granted') return refresh();
            var keyRes = await fetch('/api/push/vapid-key');
            if (!keyRes.ok) { _status = 'disabled'; return _status; }
            var vapid = (await keyRes.json()).key;
            var reg = await navigator.serviceWorker.register('push-sw.js');
            var sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64ToU8(vapid) });
            var json = sub.toJSON();
            var postRes = await fetch('/api/push/subscribe', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token() },
                body: JSON.stringify({ endpoint: sub.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth })
            });
            if (!postRes.ok) {
                // el server no guardó la suscripción: revertir la del navegador
                try { await sub.unsubscribe(); } catch (e2) {}
            }
        } catch (e) { /* cae a refresh */ }
        return refresh();
    }

    async function disable() {
        try {
            var reg = await navigator.serviceWorker.getRegistration('push-sw.js');
            var sub = reg ? await reg.pushManager.getSubscription() : null;
            if (sub) {
                await fetch('/api/push/subscribe', {
                    method: 'DELETE',
                    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token() },
                    body: JSON.stringify({ endpoint: sub.endpoint })
                });
                await sub.unsubscribe();
            }
        } catch (e) { /* cae a refresh */ }
        return refresh();
    }

    // Cerrar sesión tiene que soltar la suscripción, y no es lo mismo que disable().
    //
    // Lo que había: salir dejaba al navegador suscrito y al servidor con su fila. En una
    // portátil prestada —o después de la racha de 401 que cierra la sesión sola— esa pantalla
    // de bloqueo seguía mostrando el nombre de la tarjeta y el monto de cada vencimiento del
    // dueño, y él no tenía cómo cortarlo desde su cuenta: el DELETE necesita el endpoint, que
    // solo conoce ese navegador.
    //
    // Por qué no alcanza con llamar a disable(): el logout de la web termina en un
    // location.reload() (ver SessionManager.clear), y disable() arranca pidiéndole la
    // suscripción al ServiceWorker — una promesa que la recarga cancela antes de que llegue a
    // mandar nada. Acá, en cambio:
    //   · el token y el endpoint ya están en memoria, así que el fetch sale AHORA, síncrono;
    //   · va con keepalive, que es lo que le permite sobrevivir a la recarga;
    //   · y el unsubscribe() del navegador queda como intento de mejor esfuerzo: si la recarga
    //     lo corta no importa, porque el servidor ya no tiene fila a la cual mandarle nada.
    function olvidarAlSalir() {
        var t = token();
        var ep = _endpoint;
        _status = 'disabled';
        _endpoint = '';
        if (ep && t) {
            try {
                fetch('/api/push/subscribe', {
                    method: 'DELETE',
                    keepalive: true,
                    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + t },
                    body: JSON.stringify({ endpoint: ep })
                }).catch(function () {});
            } catch (e) { /* un logout no se detiene por esto */ }
        }
        try {
            navigator.serviceWorker.getRegistration('push-sw.js')
                .then(function (reg) { return reg ? reg.pushManager.getSubscription() : null; })
                .then(function (sub) { if (sub) sub.unsubscribe(); })
                .catch(function () {});
        } catch (e) { /* idem */ }
    }

    window.moviPush = {
        supported: supported,
        status: function () { return _status; },
        enable: function () { enable(); },
        disable: function () { disable(); },
        olvidarAlSalir: olvidarAlSalir,
        _refresh: refresh
    };
    refresh();
})();
