/* Decorative controls are independent of document upload and chat. */
(() => {
    const motion = document.querySelector("#ambientMotion");
    if (!motion) return;
    const preferenceKey = "nova.decorativeMotion";
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    try {
        const preference = localStorage.getItem(preferenceKey);
        motion.checked = !reducedMotion.matches && preference !== "off";
    } catch {
        motion.checked = !reducedMotion.matches;
    }
    motion.addEventListener("change", () => {
        try {
            localStorage.setItem(preferenceKey, motion.checked ? "on" : "off");
        } catch { /* The switch still works when browser storage is unavailable. */ }
    });
    reducedMotion.addEventListener("change", event => {
        if (event.matches) motion.checked = false;
    });

    const library = document.querySelector("#library");
    if (library && window.matchMedia("(max-width: 560px)").matches) {
        library.open = false;
    }
    window.addEventListener("resize", () => {
        if (typeof resizeTextarea === "function") resizeTextarea();
    });
})();
