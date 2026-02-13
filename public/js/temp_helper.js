// Helper for Filter Switching - Global Scope
window.setChatFilter = (mode) => {
    if (state.chatFilter === mode) return; // No change

    state.chatFilter = mode;
    render(); // Re-render sidebar to show active chip and correct list

    if (mode === 'nearby') {
        // DEBUG: Alert
        alert("Switching to Nearby Mode...");
        if (window.nearby && window.nearby.startScanning) {
            window.nearby.startScanning();
            window.refreshNearbyList();
        } else {
            // Try to init nearby if not ready
            if (window.nearby && window.nearby.init) {
                window.nearby.init().then(() => {
                    window.nearby.startScanning();
                    window.refreshNearbyList();
                });
            } else {
                console.warn("Nearby module not loaded.");
                window.showCustomAlert("Nearby module not ready", "error");
            }
        }
    } else {
        if (window.nearby && window.nearby.stopScanning) {
            window.nearby.stopScanning();
        }
    }
};
