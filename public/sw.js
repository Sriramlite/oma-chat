/* OMA Service Worker for Web Push & Notifications */
self.addEventListener('notificationclick', function(event) {
    const action = event.action;
    const data = event.notification.data || {};
    const callerId = data.callerId || data.chatId;
    
    event.notification.close();
    
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            let client = clientList.find(c => c.visibilityState === 'visible') || clientList[0];
            
            if (client) {
                client.focus();
                client.postMessage({
                    type: (action === 'answer' || action === 'reject') ? 'CALL_ACTION' : 'NOTIFICATION_CLICK',
                    action: action || 'open',
                    callerId: callerId,
                    data: data
                });
            } else {
                clients.openWindow('/');
            }
        })
    );
});

self.addEventListener('push', function(event) {
    let payload = {};
    try {
        payload = event.data ? event.data.json() : {};
    } catch (e) {
        payload = { notification: { title: 'OMA-CHAT', body: event.data ? event.data.text() : 'New Notification' } };
    }

    const title = payload.notification?.title || payload.title || 'OMA-CHAT';
    const body = payload.notification?.body || payload.body || 'New message';
    const options = {
        body: body,
        icon: payload.notification?.icon || 'https://cdn-icons-png.flaticon.com/512/190/190411.png',
        badge: 'https://cdn-icons-png.flaticon.com/512/190/190411.png',
        vibrate: [200, 100, 200],
        tag: payload.data?.chatId || 'oma-notification',
        renotify: true,
        data: payload.data || {}
    };

    event.waitUntil(self.registration.showNotification(title, options));
});
