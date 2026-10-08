/* A small Markdown subset built entirely with DOM text nodes. Document/AI HTML never executes. */
function renderAnswer(text, sources, openSource) {
    const output = document.createDocumentFragment();
    function inline(target, value) {
        const tokens = /(\*\*[^*\n]+\*\*|`[^`\n]+`|\[Nguồn \d+])/g;
        let cursor = 0;
        for (const match of value.matchAll(tokens)) {
            target.append(document.createTextNode(value.slice(cursor, match.index)));
            const token = match[0];
            if (token.startsWith("[Nguồn ")) {
                const index = Number(token.match(/\d+/)[0]) - 1;
                if (Number.isSafeInteger(index) && index >= 0 && index < sources.length) {
                    const button = document.createElement("button");
                    button.type = "button"; button.className = "inline-citation";
                    button.textContent = token;
                    button.setAttribute("aria-label", `Mở nguồn ${index + 1} trong ${sources[index].fileName}`);
                    button.addEventListener("click", () => openSource(sources[index]));
                    target.append(button);
                } else target.append(document.createTextNode(token));
            } else {
                const node = document.createElement(token.startsWith("**") ? "strong" : "code");
                node.textContent = token.slice(token.startsWith("**") ? 2 : 1, token.startsWith("**") ? -2 : -1);
                target.append(node);
            }
            cursor = match.index + token.length;
        }
        target.append(document.createTextNode(value.slice(cursor)));
    }
    const lines = String(text).replace(/\r\n?/g, "\n").split("\n");
    const cells = line => line.trim().replace(/^\|/, "").replace(/\|$/, "").split(/(?<!\\)\|/).map(cell => cell.replace(/\\\|/g, "|").trim());
    const separator = line => line && line.includes("|") && cells(line).every(cell => /^:?-{3,}:?$/.test(cell));
    let index = 0;
    while (index < lines.length) {
        const line = lines[index];
        if (!line.trim()) { index++; continue; }
        if (/^\s*```/.test(line)) {
            const pre = document.createElement("pre"); pre.className = "answer-code";
            const code = document.createElement("code"); const values = []; index++;
            while (index < lines.length && !/^\s*```/.test(lines[index])) values.push(lines[index++]);
            code.textContent = values.join("\n"); pre.append(code); output.append(pre); index++; continue;
        }
        if (line.includes("|") && separator(lines[index + 1]) && cells(line).length <= 20) {
            const wrap = document.createElement("div"); wrap.className = "answer-table-wrap"; wrap.tabIndex = 0;
            wrap.setAttribute("role", "region"); wrap.setAttribute("aria-label", "Bảng trong câu trả lời; có thể cuộn ngang");
            const table = document.createElement("table"), head = document.createElement("thead"), body = document.createElement("tbody");
            const columns = cells(line).length;
            const row = (values, header) => {
                const tr = document.createElement("tr");
                for (let col = 0; col < columns; col++) {
                    const cell = document.createElement(header ? "th" : "td");
                    if (header) cell.setAttribute("scope", "col");
                    inline(cell, values[col] || ""); tr.append(cell);
                }
                return tr;
            };
            head.append(row(cells(line), true)); index += 2;
            while (index < lines.length && lines[index].includes("|") && lines[index].trim()) body.append(row(cells(lines[index++]), false));
            table.append(head, body); wrap.append(table); output.append(wrap); continue;
        }
        const item = line.match(/^\s*(?:([-*•])\s+|(\d+)[.)]\s+)(.+)$/);
        if (item) {
            const ordered = Boolean(item[2]); const list = document.createElement(ordered ? "ol" : "ul");
            if (ordered) list.setAttribute("start", item[2]);
            while (index < lines.length) {
                const next = lines[index].match(/^\s*(?:([-*•])\s+|(\d+)[.)]\s+)(.+)$/);
                if (!next || Boolean(next[2]) !== ordered) break;
                const li = document.createElement("li");
                if (ordered) li.setAttribute("value", next[2]);
                inline(li, next[3]); list.append(li); index++;
            }
            output.append(list); continue;
        }
        const heading = line.match(/^\s*#{1,4}\s+(.+)$/);
        if (heading) { const h = document.createElement("h3"); inline(h, heading[1]); output.append(h); index++; continue; }
        const paragraph = document.createElement("p"); inline(paragraph, line); output.append(paragraph); index++;
    }
    return output;
}
