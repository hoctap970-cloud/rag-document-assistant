const state = {
    health: null,
    documents: [],
    selectedFile: null,
    uploading: false,
    asking: false
};

const elements = {
    serviceStatus: document.querySelector("#serviceStatus"),
    configBanner: document.querySelector("#configBanner"),
    uploadForm: document.querySelector("#uploadForm"),
    fileInput: document.querySelector("#fileInput"),
    dropZone: document.querySelector("#dropZone"),
    dropTitle: document.querySelector("#dropTitle"),
    dropHint: document.querySelector("#dropHint"),
    uploadButton: document.querySelector("#uploadButton"),
    uploadLabel: document.querySelector("#uploadButton .button-label"),
    uploadSpinner: document.querySelector("#uploadButton .spinner"),
    clearButton: document.querySelector("#clearButton"),
    documentCount: document.querySelector("#documentCount"),
    chunkCount: document.querySelector("#chunkCount"),
    documentList: document.querySelector("#documentList"),
    chatForm: document.querySelector("#chatForm"),
    questionInput: document.querySelector("#questionInput"),
    askButton: document.querySelector("#askButton"),
    messageList: document.querySelector("#messageList"),
    welcome: document.querySelector("#welcome"),
    toast: document.querySelector("#toast")
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;

    if (!response.ok) {
        throw new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status})`);
    }
    return body;
}

async function refresh() {
    try {
        const [health, documents] = await Promise.all([
            api("/api/health"),
            api("/api/documents")
        ]);
        state.health = health;
        state.documents = documents;
        renderStatus();
        renderDocuments();
    } catch (error) {
        elements.serviceStatus.className = "status-pill error";
        elements.serviceStatus.lastElementChild.textContent = "Mất kết nối";
        showToast(error.message, true);
    }
}

function renderStatus() {
    elements.serviceStatus.className = "status-pill online";
    elements.serviceStatus.lastElementChild.textContent = state.health.geminiConfigured
        ? "Backend và Gemini sẵn sàng"
        : "Backend đang chạy";
    elements.configBanner.classList.toggle("hidden", state.health.geminiConfigured);
    updateControls();
}

function renderDocuments() {
    elements.documentCount.textContent = String(state.documents.length);
    elements.chunkCount.textContent = String(
        state.documents.reduce((total, document) => total + document.chunkCount, 0)
    );
    elements.documentList.replaceChildren();

    if (state.documents.length === 0) {
        const empty = document.createElement("div");
        empty.className = "empty-list";
        empty.textContent = "Chưa có tài liệu nào.";
        elements.documentList.append(empty);
    } else {
        state.documents.forEach(document => elements.documentList.append(createDocumentItem(document)));
    }
    updateControls();
}

function createDocumentItem(document) {
    const item = documentNode("div", "document-item");
    const icon = documentNode("span", "file-icon", extensionOf(document.fileName));
    const info = documentNode("div", "document-info");
    const name = documentNode("strong", "", document.fileName);
    name.title = document.fileName;
    const meta = documentNode(
        "span",
        "",
        `${document.chunkCount} đoạn · ${formatBytes(document.size)} · ${formatTime(document.uploadedAt)}`
    );
    info.append(name, meta);

    const remove = documentNode("button", "delete-button", "×");
    remove.type = "button";
    remove.title = `Xóa ${document.fileName}`;
    remove.setAttribute("aria-label", `Xóa ${document.fileName}`);
    remove.addEventListener("click", () => deleteDocument(document));
    item.append(icon, info, remove);
    return item;
}

async function uploadSelectedFile(event) {
    event.preventDefault();
    if (!state.selectedFile) {
        showToast("Hãy chọn một tệp PDF, DOC hoặc DOCX.", true);
        return;
    }

    const formData = new FormData();
    formData.append("file", state.selectedFile);
    state.uploading = true;
    updateControls();

    try {
        const result = await api("/api/documents/upload", {
            method: "POST",
            body: formData
        });
        showToast(`Đã tạo ${result.chunkCount} vector từ ${result.fileName}.`);
        resetFileSelection();
        await refresh();
    } catch (error) {
        showToast(error.message, true);
    } finally {
        state.uploading = false;
        updateControls();
    }
}

function chooseFile(file) {
    if (!file) {
        return;
    }
    const extension = extensionOf(file.name);
    if (!["PDF", "DOC", "DOCX"].includes(extension)) {
        showToast("Chỉ hỗ trợ tệp PDF, DOC và DOCX.", true);
        return;
    }
    if (file.size > 10 * 1024 * 1024) {
        showToast("Tệp vượt quá giới hạn 10 MB.", true);
        return;
    }

    state.selectedFile = file;
    elements.dropTitle.textContent = file.name;
    elements.dropHint.textContent = `${formatBytes(file.size)} · Sẵn sàng tạo vector`;
    updateControls();
}

function resetFileSelection() {
    state.selectedFile = null;
    elements.fileInput.value = "";
    elements.dropTitle.textContent = "Chọn hoặc thả tệp vào đây";
    elements.dropHint.textContent = "Tối đa 10 MB cho mỗi tệp";
}

async function deleteDocument(document) {
    if (!window.confirm(`Xóa “${document.fileName}” khỏi bộ nhớ RAM?`)) {
        return;
    }
    try {
        await api(`/api/documents/${document.id}`, { method: "DELETE" });
        showToast(`Đã xóa ${document.fileName}.`);
        await refresh();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function clearDocuments() {
    if (state.documents.length === 0 || !window.confirm("Xóa toàn bộ tài liệu và vector khỏi RAM?")) {
        return;
    }
    try {
        await api("/api/documents", { method: "DELETE" });
        showToast("Đã xóa toàn bộ tài liệu.");
        await refresh();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function askQuestion(event) {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!question || state.asking) {
        return;
    }

    elements.welcome?.remove();
    appendMessage("user", question);
    elements.questionInput.value = "";
    resizeTextarea();
    state.asking = true;
    updateControls();
    const loadingMessage = appendLoadingMessage();

    try {
        const response = await api("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question })
        });
        loadingMessage.remove();
        appendMessage("assistant", response.answer, response.sources);
    } catch (error) {
        loadingMessage.remove();
        appendMessage("assistant", `Không thể xử lý câu hỏi: ${error.message}`);
        showToast(error.message, true);
    } finally {
        state.asking = false;
        updateControls();
        elements.questionInput.focus();
    }
}

function appendMessage(role, text, sources = []) {
    const message = documentNode("article", `message ${role}`);
    const avatar = documentNode("div", "message-avatar", role === "user" ? "Bạn" : "AI");
    const body = documentNode("div", "message-body");
    body.append(documentNode("div", "bubble", text));

    if (sources.length > 0) {
        body.append(documentNode("div", "sources-title", `Nguồn đã truy xuất (${sources.length})`));
        sources.forEach((source, index) => body.append(createSourceCard(source, index)));
    }

    message.append(avatar, body);
    elements.messageList.append(message);
    scrollMessages();
    return message;
}

function appendLoadingMessage() {
    const message = documentNode("article", "message assistant");
    const avatar = documentNode("div", "message-avatar", "AI");
    const body = documentNode("div", "message-body");
    const bubble = documentNode("div", "bubble");
    const typing = documentNode("div", "typing");
    typing.append(document.createElement("span"), document.createElement("span"), document.createElement("span"));
    bubble.append(typing);
    body.append(bubble);
    message.append(avatar, body);
    elements.messageList.append(message);
    scrollMessages();
    return message;
}

function createSourceCard(source, index) {
    const card = documentNode("div", "source-card");
    const header = documentNode("div", "source-header");
    const title = documentNode(
        "strong",
        "",
        `[Nguồn ${index + 1}] ${source.fileName} — ${source.section}`
    );
    title.title = `${source.fileName} — ${source.section}`;
    const score = documentNode("span", "source-score", `${Math.round(source.score * 100)}%`);
    header.append(title, score);
    card.append(header, documentNode("p", "", `Đoạn ${source.chunkIndex}: ${source.excerpt}`));
    return card;
}

function updateControls() {
    const configured = Boolean(state.health?.geminiConfigured);
    const hasDocuments = state.documents.length > 0;
    elements.uploadButton.disabled = state.uploading || !state.selectedFile || !configured;
    elements.uploadLabel.textContent = state.uploading ? "Đang xử lý tài liệu…" : "Đọc và tạo vector";
    elements.uploadSpinner.classList.toggle("hidden", !state.uploading);
    elements.fileInput.disabled = state.uploading;
    elements.clearButton.disabled = state.uploading || state.asking || !hasDocuments;
    elements.questionInput.disabled = state.asking || !hasDocuments || !configured;
    elements.askButton.disabled = state.asking || !hasDocuments || !configured;
    elements.questionInput.placeholder = !configured
        ? "Cần cấu hình GEMINI_API_KEY…"
        : hasDocuments
            ? "Hỏi một điều có trong tài liệu…"
            : "Tải tài liệu lên rồi nhập câu hỏi…";
    document.querySelectorAll(".prompt-chip").forEach(button => {
        button.disabled = !hasDocuments || !configured || state.asking;
    });
}

function resizeTextarea() {
    elements.questionInput.style.height = "auto";
    elements.questionInput.style.height = `${Math.min(elements.questionInput.scrollHeight, 140)}px`;
}

function scrollMessages() {
    requestAnimationFrame(() => {
        elements.messageList.scrollTop = elements.messageList.scrollHeight;
    });
}

let toastTimer;
function showToast(message, isError = false) {
    window.clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast visible${isError ? " error" : ""}`;
    toastTimer = window.setTimeout(() => {
        elements.toast.classList.remove("visible");
    }, 4200);
}

function documentNode(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) {
        node.className = className;
    }
    if (text) {
        node.textContent = text;
    }
    return node;
}

function extensionOf(fileName) {
    return fileName.includes(".") ? fileName.split(".").pop().toUpperCase() : "FILE";
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(isoDate) {
    return new Intl.DateTimeFormat("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        day: "2-digit",
        month: "2-digit"
    }).format(new Date(isoDate));
}

elements.uploadForm.addEventListener("submit", uploadSelectedFile);
elements.fileInput.addEventListener("change", event => chooseFile(event.target.files[0]));
elements.clearButton.addEventListener("click", clearDocuments);
elements.chatForm.addEventListener("submit", askQuestion);
elements.questionInput.addEventListener("input", resizeTextarea);
elements.questionInput.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        elements.chatForm.requestSubmit();
    }
});

["dragenter", "dragover"].forEach(eventName => {
    elements.dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        elements.dropZone.classList.add("dragover");
    });
});

["dragleave", "drop"].forEach(eventName => {
    elements.dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        elements.dropZone.classList.remove("dragover");
    });
});

elements.dropZone.addEventListener("drop", event => chooseFile(event.dataTransfer.files[0]));
document.querySelectorAll(".prompt-chip").forEach(button => {
    button.addEventListener("click", () => {
        elements.questionInput.value = button.textContent;
        resizeTextarea();
        elements.questionInput.focus();
    });
});

updateControls();
refresh();
