// Service worker SOLO-push de movi. PROHIBIDO agregar handler 'fetch' o cache:
// la PWA decidió explícitamente no cachear (riesgo de bundle viejo tras deploy).
self.addEventListener('push', function (event) {
    var data = { title: 'Movi', body: '', url: '/' };
    try { if (event.data) data = Object.assign(data, event.data.json()); } catch (e) {}
    event.waitUntil(self.registration.showNotification(data.title, {
        body: data.body,
        icon: 'icons/movi-192.png',
        badge: 'icons/movi-192.png',
        data: { url: data.url || '/' }
    }));
});

self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    var url = (event.notification.data && event.notification.data.url) || '/';
    event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (list) {
        for (var i = 0; i < list.length; i++) {
            if ('focus' in list[i]) return list[i].focus();
        }
        return clients.openWindow(url);
    }));
});
