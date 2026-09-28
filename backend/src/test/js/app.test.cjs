const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = readFileSync(process.env.APP_SCRIPT || path.join(__dirname, '../../main/resources/static/js/app.js'), 'utf8');

// Minimal DOM for event/state regressions; layout is checked in a real browser.
function element() {
    const classes = new Set();
    return {
        value: '', style: {}, children: [], listeners: {}, attributes: {}, scrollHeight: 24,
        classList: {
            add: name => classes.add(name), remove: name => classes.delete(name),
            contains: name => classes.has(name),
            toggle(name, force) { if (force) classes.add(name); else classes.delete(name); }
        },
        append(...children) { this.children.push(...children); },
        replaceChildren(...children) { this.children = children; },
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(name, callback) { this.listeners[name] = callback; },
        remove() {}, focus() {}, scrollIntoView() {},
        showModal() { this.open = true; }, close() { this.open = false; },
        requestSubmit() { this.submitted = true; }
    };
}

async function app() {
    const nodes = new Map();
    const get = selector => {
        if (!nodes.has(selector)) nodes.set(selector, element());
        return nodes.get(selector);
    };
    get('#serviceStatus').lastElementChild = element();
    const chips = [element(), element()];
    const calls = [];
    const context = vm.createContext({
        document: {
            querySelector: get,
            querySelectorAll: selector => selector === '.prompt-chip' ? chips : [],
            createElement: element, createDocumentFragment: element
        },
        window: { clearTimeout() {}, setTimeout() {}, confirm: () => true },
        requestAnimationFrame: callback => callback(),
        FormData, Blob,
        fetch: async (url, options) => {
            calls.push({ url, options });
            return { ok: true, headers: { get: () => 'application/json' },
                json: async () => url === '/api/health' ? { geminiConfigured: true } : [] };
        }
    });
    vm.runInContext(source, context);
    await new Promise(resolve => setImmediate(resolve));
    const run = code => vm.runInContext(code, context);
    return { context, run, get, chips, calls };
}

test('disconnect is distinct from missing API key and reconnect restores controls', async () => {
    const { context, run, get } = await app();
    run('state.documents = [{chunkCount: 3}]; updateControls()');
    context.fetch = async () => { throw new Error('Offline'); };
    await run('refresh()');
    assert.equal(get('#serviceStatus').lastElementChild.textContent, 'Mất kết nối');
    assert.match(get('#questionInput').placeholder, /Mất kết nối/);
    assert.equal(get('#retryConnection').classList.contains('hidden'), false);
    assert.equal(get('#questionInput').disabled, true);
    assert.equal(run('state.documents.length'), 1, 'Do not turn a network failure into a fake empty library');
    context.fetch = async url => ({ ok: true, headers: { get: () => 'application/json' },
        json: async () => url === '/api/health' ? { geminiConfigured: true } : [] });
    await get('#retryConnection').listeners.click();
    assert.equal(get('#retryConnection').classList.contains('hidden'), true);
    assert.match(get('#questionInput').placeholder, /Tải tài liệu/);
});

test('dropping another file during upload keeps the file being processed', async () => {
    const { context, run } = await app();
    context.firstFile = { name: 'first.pdf', size: 200 };
    context.nextFile = { name: 'next.docx', size: 300 };
    run('chooseFile(firstFile); state.uploading = true; chooseFile(nextFile)');
    assert.equal(run('state.selectedFile.name'), 'first.pdf');
});

test('an active chat blocks a simultaneous upload', async () => {
    const { run, get, calls } = await app();
    run('state.asking = true; state.selectedFile = new Blob(["test"]); updateControls()');
    assert.equal(get('#uploadButton').disabled, true);
    await get('#uploadForm').listeners.submit({ preventDefault() {} });
    assert.equal(calls.some(call => call.url === '/api/documents/upload'), false);
});

test('failed chat restores the question and releases busy controls', async () => {
    const { context, run, get } = await app();
    run('state.documents = [{chunkCount: 1}]; updateControls()');
    get('#questionInput').value = 'Tài liệu nói gì?';
    context.fetch = async () => { throw new Error('Service unavailable'); };
    await get('#chatForm').listeners.submit({ preventDefault() {} });
    assert.equal(get('#questionInput').value, 'Tài liệu nói gì?');
    assert.equal(get('#questionInput').disabled, false);
    assert.equal(get('#askButton').disabled, false);
});

test('send is disabled for whitespace, enabled for text or a prompt suggestion', async () => {
    const { run, get, chips } = await app();
    run('state.documents = [{chunkCount: 1}]; updateControls()');
    assert.equal(get('#askButton').disabled, true);
    get('#questionInput').value = ' \n ';
    get('#questionInput').listeners.input();
    assert.equal(get('#askButton').disabled, true);
    get('#questionInput').value = 'Tóm tắt';
    get('#questionInput').listeners.input();
    assert.equal(get('#askButton').disabled, false);
    get('#questionInput').value = '';
    get('#questionInput').listeners.input();
    chips[0].textContent = 'Hãy tóm tắt tài liệu';
    chips[0].listeners.click();
    assert.equal(get('#askButton').disabled, false);
});

test('Enter during IME composition does not submit an unfinished question', async () => {
    const { get } = await app();
    get('#questionInput').listeners.keydown({ key: 'Enter', shiftKey: false, isComposing: true, preventDefault() {} });
    assert.equal(get('#chatForm').submitted, undefined);
});

test('PDF deep reading is sent with the upload, locked while running and preserved on failure', async () => {
    const { context, run, get } = await app();
    run('const testPdf = new Blob(["%PDF-test"]); testPdf.name = "table.pdf"; chooseFile(testPdf)');
    assert.equal(get('#deepRead').disabled, false);
    get('#deepRead').checked = true;
    let failUpload;
    let submittedForm;
    context.fetch = async (url, options) => {
        assert.equal(url, '/api/documents/upload');
        submittedForm = options.body;
        return new Promise((resolve, reject) => { failUpload = reject; });
    };
    const upload = get('#uploadForm').listeners.submit({ preventDefault() {} });
    assert.equal(submittedForm.get('readMode'), 'DEEP');
    assert.equal(get('#deepRead').disabled, true);
    failUpload(new Error('Quota exceeded'));
    await upload;
    assert.equal(get('#deepRead').disabled, false);
    assert.equal(get('#deepRead').checked, true);
    assert.equal(run('state.selectedFile.name'), 'table.pdf');
});

test('choosing a different file resets deep reading and Word always uploads in automatic mode', async () => {
    const { context, run, get } = await app();
    run('chooseFile({name: "one.pdf", size: 10})');
    get('#deepRead').checked = true;
    run('chooseFile({name: "two.pdf", size: 10})');
    assert.equal(get('#deepRead').checked, false);
    get('#deepRead').checked = true;
    run('const testWord = new Blob(["word"]); testWord.name = "table.docx"; chooseFile(testWord)');
    assert.equal(get('#deepRead').disabled, true);
    assert.equal(get('#deepRead').checked, false);
    let submittedMode;
    context.fetch = async (url, options) => {
        submittedMode = options.body.get('readMode');
        throw new Error('Test upload failure');
    };
    await get('#uploadForm').listeners.submit({ preventDefault() {} });
    assert.equal(submittedMode, 'AUTO');
});
