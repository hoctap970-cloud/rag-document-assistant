/* Decorative motion is isolated from upload, retrieval and conversation state. */
(() => {
    const root = document.documentElement;
    const motion = document.querySelector("#ambientMotion");
    if (!motion) return;
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    const fine = window.matchMedia("(hover: hover) and (pointer: fine)");
    const storage = {
        get(key) { try { return localStorage.getItem(key); } catch { return null; } },
        set(key, value) { try { localStorage.setItem(key, value); } catch { /* Optional persistence. */ } }
    };
    motion.checked = !reduced.matches && storage.get("nova.decorativeMotion") !== "off";
    const canMove = () => motion.checked && !reduced.matches && !document.hidden;

    const canvas = document.querySelector("#lightCanvas");
    const context = canvas?.getContext("2d");
    let width = 0, height = 0, frame = null, lastPaint = null, sceneTime = 0;
    let sceneColor = "#83e4ee";
    const camera = {x: 0, y: 0, targetX: 0, targetY: 0};
    const particles = Array.from({length: 44}, (_, i) => ({
        x: ((i * 137.508) % 997) / 997,
        y: ((i * 83.31 + 31) % 619) / 619,
        phase: i * 1.71, size: .6 + (i % 4) * .3
    }));

    function paint() {
        if (!context || !width || !height) return;
        context.clearRect(0, 0, width, height);
        context.save();
        camera.x += (camera.targetX - camera.x) * .045;
        camera.y += (camera.targetY - camera.y) * .045;
        const time = sceneTime / 1000;
        context.strokeStyle = sceneColor;
        // Thin flowing paths echo a sheet being read, rather than a fake HUD.
        for (let i = 0; i < 5; i++) {
            const offset = Math.sin(time * .16 + i * .6) * 34;
            const base = height * (.38 + i * .043) + camera.y * 26;
            context.globalAlpha = .035 + i * .009;
            context.lineWidth = .7;
            context.beginPath();
            context.moveTo(-50, base + 110 + offset);
            context.bezierCurveTo(width * .24, base - 220, width * .54, base + 190 + offset,
                width + 50, base - 100 + camera.x * 30);
            context.stroke();
        }
        context.fillStyle = sceneColor;
        const count = width < 600 ? 22 : particles.length;
        for (let i = 0; i < count; i++) {
            const p = particles[i];
            const x = p.x * width + Math.sin(time * .08 + p.phase) * 25 + camera.x * 14;
            const y = p.y * height + Math.cos(time * .11 + p.phase) * 18 + camera.y * 14;
            context.globalAlpha = .12 + (Math.sin(time * .5 + p.phase) + 1) * .12;
            context.beginPath();
            context.arc(x, y, p.size, 0, Math.PI * 2);
            context.fill();
        }
        context.restore();
    }
    function tick(time) {
        frame = null;
        if (!canMove()) return;
        // A 30fps decoration budget is independent of input and chat rendering.
        if (lastPaint === null || time - lastPaint >= 1000 / 30) {
            if (lastPaint !== null) sceneTime += Math.min(time - lastPaint, 100);
            lastPaint = time;
            paint();
        }
        frame = requestAnimationFrame(tick);
    }
    function syncMotion() {
        if (frame !== null) cancelAnimationFrame(frame);
        frame = null;
        lastPaint = null;
        root.classList.toggle("page-hidden", document.hidden);
        if (context && !document.hidden) paint();
        if (context && canMove()) frame = requestAnimationFrame(tick);
    }
    function sizeCanvas() {
        if (!context) return;
        width = window.innerWidth;
        height = window.innerHeight;
        const scale = Math.min(window.devicePixelRatio || 1, 1.5, 2048 / width, 1440 / height);
        canvas.width = Math.round(width * scale);
        canvas.height = Math.round(height * scale);
        context.setTransform(scale, 0, 0, scale, 0, 0);
        if (!document.hidden) paint();
    }

    const themeButton = document.querySelector("#themeButton");
    const themeLabel = document.querySelector("#themeLabel");
    function applyTheme(light) {
        root.dataset.theme = light ? "light" : "dark";
        themeButton?.setAttribute("aria-pressed", String(light));
        themeButton?.setAttribute("aria-label", light ? "Bật giao diện tối" : "Bật giao diện sáng");
        if (themeLabel) themeLabel.textContent = light ? "Tối" : "Sáng";
        document.querySelector('meta[name="theme-color"]')?.setAttribute("content", light ? "#eaf0f8" : "#091321");
        sceneColor = getComputedStyle(root).getPropertyValue("--accent").trim() || "#83e4ee";
        if (!document.hidden) paint();
    }
    applyTheme(root.dataset.theme === "light");
    themeButton?.addEventListener("click", () => {
        const light = root.dataset.theme !== "light";
        storage.set("nova.theme", light ? "light" : "dark");
        applyTheme(light);
    });
    motion.addEventListener("change", () => {
        storage.set("nova.decorativeMotion", motion.checked ? "on" : "off");
        syncMotion();
    });
    reduced.addEventListener("change", event => {
        if (event.matches) motion.checked = false;
        syncMotion();
    });
    document.addEventListener("visibilitychange", syncMotion);

    const library = document.querySelector("#library");
    if (library && window.matchMedia("(max-width: 560px)").matches && location.hash !== "#library") library.open = false;
    const navigation = Array.from(document.querySelectorAll(".workspace-nav a"));
    const setCurrent = selected => navigation.forEach(link => {
        if (link === selected) link.setAttribute("aria-current", "location");
        else link.removeAttribute("aria-current");
    });
    [...navigation, ...document.querySelectorAll(".intro-guidance")].forEach(link => {
        if (navigation.includes(link) && link.getAttribute("href") === location.hash) setCurrent(link);
        link.addEventListener("click", event => {
            const target = document.querySelector(link.getAttribute("href"));
            if (!target) return;
            event.preventDefault();
            if (target === library) library.open = true;
            const focusTarget = target === library ? library.querySelector("summary") : target;
            target.scrollIntoView({block: "nearest", behavior: canMove() ? "smooth" : "auto"});
            focusTarget.focus({preventScroll: true});
            setCurrent(navigation.find(item => item.getAttribute("href") === link.getAttribute("href")));
            history.replaceState(null, "", link.getAttribute("href"));
        });
    });

    let pointerFrame = null, pointer = null, lastSurface = null;
    const stage = document.querySelector(".page-intro");
    const prism = document.querySelector(".prism-object");
    const surfaceSelector = ".prompt-chip, .drop-zone, .composer, .document-item, .source-card";
    function resetSurface(surface) {
        surface?.style.removeProperty("--tilt-x");
        surface?.style.removeProperty("--tilt-y");
    }
    document.addEventListener("pointermove", event => {
        if (!canMove() || !fine.matches) return;
        camera.targetX = event.clientX / window.innerWidth - .5;
        camera.targetY = event.clientY / window.innerHeight - .5;
        pointer = {target: event.target, x: event.clientX, y: event.clientY};
        if (pointerFrame !== null) return;
        pointerFrame = requestAnimationFrame(() => {
            pointerFrame = null;
            if (!canMove() || !fine.matches || !pointer.target.isConnected) return;
            const surface = pointer.target.closest?.(surfaceSelector);
            if (surface !== lastSurface) resetSurface(lastSurface);
            lastSurface = surface;
            if (surface && !surface.disabled) {
                const bounds = surface.getBoundingClientRect();
                const x = pointer.x - bounds.left, y = pointer.y - bounds.top;
                surface.style.setProperty("--pointer-x", `${x}px`);
                surface.style.setProperty("--pointer-y", `${y}px`);
                if (surface.matches(".prompt-chip")) {
                    surface.style.setProperty("--tilt-x", `${(y / bounds.height - .5) * -6}deg`);
                    surface.style.setProperty("--tilt-y", `${(x / bounds.width - .5) * 6}deg`);
                }
            }
            if (stage && prism && stage.contains(pointer.target)) {
                const bounds = stage.getBoundingClientRect();
                const x = (pointer.x - bounds.left) / bounds.width - .5;
                const y = (pointer.y - bounds.top) / bounds.height - .5;
                prism.style.setProperty("--art-x", `${x * 18}px`);
                prism.style.setProperty("--art-y", `${y * 12}px`);
                prism.style.setProperty("--art-turn", `${x * 7}deg`);
            }
        });
    }, {passive: true});
    document.addEventListener("pointerout", event => {
        const surface = event.target.closest?.(surfaceSelector);
        if (surface && !surface.contains(event.relatedTarget)) resetSurface(surface);
        if (stage?.contains(event.target) && !stage.contains(event.relatedTarget)) {
            ["--art-x", "--art-y", "--art-turn"].forEach(name => prism?.style.removeProperty(name));
        }
    }, {passive: true});
    document.addEventListener("click", event => {
        if (!canMove()) return;
        const button = event.target.closest?.(".button, .prompt-chip, .send-button, .theme-button, .identity-button, .viewer-close, .delete-button");
        if (!button || button.disabled) return;
        const bounds = button.getBoundingClientRect();
        const ripple = document.createElement("span");
        ripple.className = "press-ripple";
        ripple.setAttribute("aria-hidden", "true");
        ripple.style.setProperty("--ripple-x", `${event.detail ? event.clientX - bounds.left : bounds.width / 2}px`);
        ripple.style.setProperty("--ripple-y", `${event.detail ? event.clientY - bounds.top : bounds.height / 2}px`);
        button.append(ripple);
        window.setTimeout(() => ripple.remove(), 700);
    });

    let resizeFrame = null;
    window.addEventListener("resize", () => {
        if (resizeFrame !== null) return;
        resizeFrame = requestAnimationFrame(() => {
            resizeFrame = null;
            sizeCanvas();
            if (typeof resizeTextarea === "function") resizeTextarea();
        });
    });
    sizeCanvas();
    syncMotion();
})();
