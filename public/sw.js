/* OMA Service Worker for Call Notifications */
self.addEventListener('notificationclick', function(event) {
    const action = event.action;
    const callerId = event.notification.data.callerId;
    
    event.notification.close();
    
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            // Find an open tab
            let client = clientList.find(c => c.visibilityState === 'visible') || clientList[0];
            
            if (client) {
                client.focus();
                // Send command to the app
                client.postMessage({
                    type: 'CALL_ACTION',
                    action: action, // 'answer' or 'reject'
                    callerId: callerId
                });
            } else {
                // If no tab open, open it
                clients.openWindow('/').then(function(newClient) {
                    // Wait for it to load and send message? 
                    // Simpler to focus existing for now as per user context
                });
            }
        })
    );
});

self.addEventListener('push', function(event) {
    // Handle background push if needed in future
});
