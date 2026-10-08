// Apply the saved theme before the first paint. Storage is optional.
(() => {
    let light = false;
    try { light = localStorage.getItem("nova.theme") === "light"; } catch { /* Use midnight. */ }
    document.documentElement.dataset.theme = light ? "light" : "dark";
})();
