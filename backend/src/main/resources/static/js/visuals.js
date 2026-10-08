/* Visual polish stays independent of upload, retrieval and chat state. */
(() => {
    const motion = document.querySelector("#ambientMotion");
    if (!motion) return;
    const preferenceKey = "nova.decorativeMotion";
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");
    try {
        motion.checked = !reducedMotion.matches && localStorage.getItem(preferenceKey) !== "off";
    } catch {
        motion.checked = !reducedMotion.matches;
    }
    motion.addEventListener("change", () => {
        try { localStorage.setItem(preferenceKey, motion.checked ? "on" : "off"); }
        catch { /* The switch works without browser storage. */ }
    });
    reducedMotion.addEventListener("change", event => {
        if (event.matches) motion.checked = false;
    });

    const library = document.querySelector("#library");
    if (library && window.matchMedia("(max-width: 560px)").matches && location.hash !== "#library") {
        library.open = false;
    }
    const navigation = Array.from(document.querySelectorAll(".workspace-nav a"));
    const setCurrent = selected => navigation.forEach(link => {
        if (link === selected) link.setAttribute("aria-current", "location");
        else link.removeAttribute("aria-current");
    });
    navigation.forEach(link => {
        if (link.getAttribute("href") === location.hash) setCurrent(link);
        link.addEventListener("click", event => {
            const target = document.querySelector(link.getAttribute("href"));
            if (!target) return;
            event.preventDefault();
            if (target === library) library.open = true;
            const focusTarget = target === library ? library.querySelector("summary") : target;
            target.scrollIntoView({block: "nearest", behavior: motion.checked && !reducedMotion.matches ? "smooth" : "auto"});
            focusTarget.focus({preventScroll: true});
            setCurrent(link);
            history.replaceState(null, "", link.getAttribute("href"));
        });
    });

    // One scheduled update per frame, no listener attached to each rendered card.
    let pendingFrame = null;
    let pointer = null;
    document.addEventListener("pointermove", event => {
        if (!motion.checked || reducedMotion.matches || !finePointer.matches || document.hidden) return;
        const surface = event.target.closest?.(".prompt-chip, .drop-zone, .composer, .document-item, .source-card");
        if (!surface || surface.disabled) return;
        pointer = {surface, x: event.clientX, y: event.clientY};
        if (pendingFrame !== null) return;
        pendingFrame = requestAnimationFrame(() => {
            pendingFrame = null;
            if (!motion.checked || reducedMotion.matches || !pointer.surface.isConnected) return;
            const bounds = pointer.surface.getBoundingClientRect();
            pointer.surface.style.setProperty("--pointer-x", `${pointer.x - bounds.left}px`);
            pointer.surface.style.setProperty("--pointer-y", `${pointer.y - bounds.top}px`);
        });
    }, {passive: true});
    document.addEventListener("visibilitychange", () => {
        document.documentElement.classList.toggle("page-hidden", document.hidden);
    });
    window.addEventListener("resize", () => {
        if (typeof resizeTextarea === "function") resizeTextarea();
    });
})();
