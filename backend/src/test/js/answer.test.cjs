const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function node(tag, value = '') {
    return { tag, textContent: value, children: [], attributes: {}, listeners: {},
        append(...children) { this.children.push(...children); },
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(event, fn) { this.listeners[event] = fn; } };
}
function renderer() {
    const context = vm.createContext({document: {createElement: tag => node(tag),
        createTextNode: value => node('#text', value), createDocumentFragment: () => node('#fragment')}});
    vm.runInContext(readFileSync(path.join(__dirname, '../../main/resources/static/js/answer.js'), 'utf8'), context);
    return context.renderAnswer;
}
const walk = root => [root, ...root.children.flatMap(walk)];
const content = root => walk(root).map(child => child.textContent).join('');
test('AI and document HTML stays literal text, including links and event handlers', () => {
    const text = '<img src=x onerror=alert(1)> [click](javascript:alert(1)) <script>danger()</script>';
    const output = renderer()(text, [], () => {});
    assert.equal(content(output), text);
    assert.equal(walk(output).some(child => ['img', 'script', 'a'].includes(child.tag)), false);
});
test('a Markdown table preserves values and citations open the corresponding real source', () => {
    const source = {fileName: 'bảng.docx', chunkIndex: 2}; let opened;
    const output = renderer()('| Năm | Doanh thu |\n| --- | --- |\n| 2026 | **873 triệu** [Nguồn 1] |', [source], value => {opened = value;});
    assert.equal(walk(output).filter(child => child.tag === 'th').length, 2);
    assert.equal(walk(output).filter(child => child.tag === 'td').length, 2);
    assert.match(content(output), /2026873 triệu/);
    const citation = walk(output).find(child => child.tag === 'button'); citation.listeners.click();
    assert.equal(opened, source);
    assert.match(citation.attributes['aria-label'], /bảng.docx/);
});
test('ordered lists preserve actual numbers and invalid citations remain text', () => {
    const output = renderer()('7. Điều kiện A [Nguồn 999999999999999999999]\n8. Điều kiện B\n\n```\n<img>\n```', [], () => assert.fail());
    assert.equal(walk(output).find(child => child.tag === 'ol').attributes.start, '7');
    assert.deepEqual(walk(output).filter(child => child.tag === 'li').map(child => child.attributes.value), ['7', '8']);
    assert.equal(walk(output).filter(child => child.tag === 'button').length, 0);
    assert.match(content(output), /<img>/);
});
