// Opt-in de Web Push. El estado se cachea en _status para que el interop wasm
// (síncrono) pueda leerlo; enable()/disable()/init() lo refrescan async.
(function () {
    var _status = 'unsupported';

    function supported() {
        return 'serviceWorker' in navigator && 'PushManager' in window && !!localStorage.getItem('auth_token');
    }

    function b64ToU8(b64) {
        var pad = '='.repeat((4 - b64.length % 4) % 4);
        var raw = atob((b64 + pad).replace(/-/g, '+').replace(/_/g, '/'));
        var arr = new Uint8Array(raw.length);
        for (var i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
        return arr;
    }

    async function refresh() {
        if (!supported()) { _status = 'unsupported'; return _status; }
        if (Notification.permission === 'denied') { _status = 'denied'; return _status; }
        try {
            var reg = await navigator.serviceWorker.getRegistration('push-sw.js');
            var sub = reg ? await reg.pushManager.getSubscription() : null;
            _status = sub ? 'enabled' : 'disabled';
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
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('auth_token') },
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
                    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('auth_token') },
                    body: JSON.stringify({ endpoint: sub.endpoint })
                });
                await sub.unsubscribe();
            }
        } catch (e) { /* cae a refresh */ }
        return refresh();
    }

    window.moviPush = {
        supported: supported,
        status: function () { return _status; },
        enable: function () { enable(); },
        disable: function () { disable(); },
        _refresh: refresh
    };
    refresh();
})();
