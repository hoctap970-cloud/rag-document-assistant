const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const scripts = ['theme.js', 'visuals.js'].map(name => readFileSync(path.join(__dirname, '../../main/resources/static/js', name), 'utf8'));

// Browser effects need lifecycle tests: a paused canvas must stop scheduling work.
function effects({reduced = false, saved = {}, blockedStorage = false} = {}) {
    const frames = new Map(), media = new Map(), storage = new Map(Object.entries(saved));
    let nextFrame = 0, paints = 0;
    const node = () => ({
        dataset: {}, attributes: {}, listeners: {}, style: {removeProperty() {}, setProperty() {}},
        classList: {toggle() {}},
        addEventListener(name, listener) { this.listeners[name] = listener; },
        setAttribute(name, value) { this.attributes[name] = value; }
    });
    const root = node(), motion = node(), theme = node(), themeLabel = node(), meta = node();
    const draw = {
        clearRect() { paints++; }, save() {}, restore() {}, setTransform() {},
        beginPath() {}, moveTo() {}, bezierCurveTo() {}, stroke() {}, arc() {}, fill() {}
    };
    const canvas = {...node(), getContext: () => draw};
    const nodes = new Map([
        ['#ambientMotion', motion], ['#themeButton', theme], ['#themeLabel', themeLabel],
        ['#lightCanvas', canvas], ['meta[name="theme-color"]', meta]
    ]);
    const document = {
        documentElement: root, hidden: false, listeners: {},
        querySelector: selector => nodes.get(selector) || null,
        querySelectorAll: () => [],
        addEventListener(name, listener) { this.listeners[name] = listener; }
    };
    const localStorage = {
        getItem(key) { if (blockedStorage) throw new Error('Storage unavailable'); return storage.get(key) || null; },
        setItem(key, value) { if (blockedStorage) throw new Error('Storage unavailable'); storage.set(key, value); }
    };
    const window = {
        innerWidth: 1280, innerHeight: 720, devicePixelRatio: 2,
        setTimeout() {}, addEventListener() {},
        matchMedia(query) {
            if (!media.has(query)) {
                const item = node();
                item.matches = query.includes('reduced-motion') ? reduced : false;
                media.set(query, item);
            }
            return media.get(query);
        }
    };
    const context = vm.createContext({
        document, window, localStorage, location: {hash: ''}, history: {replaceState() {}},
        getComputedStyle: () => ({getPropertyValue: () => root.dataset.theme === 'light' ? '#175f73' : '#83e4ee'}),
        requestAnimationFrame(callback) { const id = ++nextFrame; frames.set(id, callback); return id; },
        cancelAnimationFrame(id) { frames.delete(id); }
    });
    scripts.forEach(source => vm.runInContext(source, context));
    return {
        root, motion, theme, themeLabel, meta, document, frames, media, storage,
        paintCount: () => paints,
        runFrame(time) {
            const [id, callback] = frames.entries().next().value;
            frames.delete(id);
            callback(time);
        }
    };
}

test('saved pause and OS reduced motion do not schedule a decorative render loop', () => {
    for (const options of [{saved: {'nova.decorativeMotion': 'off'}}, {reduced: true}]) {
        const ui = effects(options);
        assert.equal(ui.motion.checked, false);
        assert.equal(ui.frames.size, 0);
        assert.ok(ui.paintCount() > 0, 'A static canvas is still available');
    }
});

test('hiding the page or pausing cancels work; resuming starts just one loop', () => {
    const ui = effects();
    assert.equal(ui.frames.size, 1);
    ui.runFrame(100);
    assert.equal(ui.frames.size, 1);
    ui.document.hidden = true;
    ui.document.listeners.visibilitychange();
    assert.equal(ui.frames.size, 0);
    ui.document.hidden = false;
    ui.document.listeners.visibilitychange();
    assert.equal(ui.frames.size, 1);
    ui.motion.checked = false;
    ui.motion.listeners.change();
    assert.equal(ui.frames.size, 0);
    assert.equal(ui.storage.get('nova.decorativeMotion'), 'off');
    ui.motion.checked = true;
    ui.motion.listeners.change();
    ui.motion.listeners.change();
    assert.equal(ui.frames.size, 1, 'Repeated changes must not multiply render loops');
});

test('theme is restored before paint and its toggle persists with accessible state', () => {
    const ui = effects({saved: {'nova.theme': 'light'}});
    assert.equal(ui.root.dataset.theme, 'light');
    assert.equal(ui.theme.attributes['aria-pressed'], 'true');
    assert.equal(ui.theme.attributes['aria-label'], 'Bật giao diện tối');
    ui.theme.listeners.click();
    assert.equal(ui.root.dataset.theme, 'dark');
    assert.equal(ui.storage.get('nova.theme'), 'dark');
    assert.equal(ui.meta.attributes.content, '#091321');
    assert.equal(ui.theme.attributes['aria-label'], 'Bật giao diện sáng');
});

test('theme and motion controls still work when browser storage is blocked', () => {
    const ui = effects({blockedStorage: true});
    ui.theme.listeners.click();
    assert.equal(ui.root.dataset.theme, 'light');
    ui.motion.checked = false;
    ui.motion.listeners.change();
    assert.equal(ui.frames.size, 0);
});
