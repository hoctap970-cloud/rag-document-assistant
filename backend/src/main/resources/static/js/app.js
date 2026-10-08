const state = {
    health: null,
    documents: [],
    selectedFile: null,
    refreshing: false,
    uploading: false,
    asking: false,
    checkingAi: false,
    deleting: false,
    aiReady: null,
    messageCount: 0
};

const elements = {
    serviceStatus: document.querySelector("#serviceStatus"),
    retryConnection: document.querySelector("#retryConnection"),
    configBanner: document.querySelector("#configBanner"),
    uploadForm: document.querySelector("#uploadForm"),
    fileInput: document.querySelector("#fileInput"),
    deepRead: document.querySelector("#deepRead"),
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
    sourceViewer: document.querySelector("#sourceViewer"),
    viewerTitle: document.querySelector("#viewerTitle"),
    viewerSubtitle: document.querySelector("#viewerSubtitle"),
    viewerLocation: document.querySelector("#viewerLocation"),
    viewerOriginal: document.querySelector("#viewerOriginal"),
    viewerContent: document.querySelector("#viewerContent"),
    closeViewer: document.querySelector("#closeViewer"),
    personalizeButton: document.querySelector("#personalizeButton"),
    identityDialog: document.querySelector("#identityDialog"),
    identityForm: document.querySelector("#identityForm"),
    closeIdentity: document.querySelector("#closeIdentity"),
    ownerInput: document.querySelector("#ownerInput"),
    signatureInput: document.querySelector("#signatureInput"),
    ownerLabel: document.querySelector("#ownerLabel"),
    signatureLabel: document.querySelector("#signatureLabel"),
    toast: document.querySelector("#toast")
};
Object.assign(elements, {
    documentScope: document.querySelector("#documentScope"),
    newChatButton: document.querySelector("#newChatButton"),
    checkAiButton: document.querySelector("#checkAiButton"),
    aiCheckDialog: document.querySelector("#aiCheckDialog"),
    closeAiCheck: document.querySelector("#closeAiCheck"),
    aiCheckStatus: document.querySelector("#aiCheckStatus"),
    aiCheckModels: document.querySelector("#aiCheckModels"),
    uploadFeedback: document.querySelector("#uploadFeedback")
});

async function api(path, options = {}) {
    const response = await fetch(path, {cache: "no-store", ...options});
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;

    if (!response.ok) {
        throw new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status})`);
    }
    return body;
}

async function refresh() {
    if (state.refreshing) return;
    state.refreshing = true;
    renderStatus();
    try {
        const [health, documents] = await Promise.all([
            api("/api/health"),
            api("/api/documents")
        ]);
        state.health = health;
        state.documents = documents;
        renderDocuments();
    } catch (error) {
        state.health = null;
        showToast(error.message, true);
    } finally {
        state.refreshing = false;
        renderStatus();
    }
}

function renderStatus() {
    const connected = Boolean(state.health);
    const aiFailed = connected && state.health.geminiConfigured && state.aiReady === false;
    elements.serviceStatus.className = `status-pill${state.refreshing ? "" : connected && !aiFailed ? " online" : " error"}`;
    elements.serviceStatus.lastElementChild.textContent = state.refreshing
        ? "Đang kết nối"
        : !connected ? "Mất kết nối"
            : state.health.geminiConfigured ? state.aiReady === false ? "AI chưa sẵn sàng" : state.aiReady ? "AI đã kiểm tra" : "Sẵn sàng" : "Chưa thiết lập AI";
    elements.serviceStatus.title = state.aiReady ? "Chat và embedding đã được kiểm tra trong phiên này"
        : "Trạng thái backend. Bấm Kiểm tra AI để thử chat và embedding thật.";
    elements.configBanner.classList.toggle("hidden", !connected || state.health.geminiConfigured);
    elements.retryConnection.classList.toggle("hidden", connected);
    elements.retryConnection.disabled = state.refreshing;
    updateControls();
}

function renderDocuments() {
    const previousScope = elements.documentScope.value;
    const choices = [documentNode("option", "", "Toàn bộ thư viện")];
    choices[0].value = "";
    state.documents.forEach(file => {
        const option = documentNode("option", "", file.fileName); option.value = file.id; choices.push(option);
    });
    elements.documentScope.replaceChildren(...choices);
    elements.documentScope.value = state.documents.some(file => file.id === previousScope) ? previousScope : "";
    elements.documentCount.textContent = String(state.documents.length);
    elements.chunkCount.textContent = String(
        state.documents.reduce((total, document) => total + document.chunkCount, 0)
    );
    elements.documentList.replaceChildren();

    if (state.documents.length === 0) {
        const empty = document.createElement("div");
        empty.className = "empty-list";
        empty.append(
            documentNode("span", "empty-list-icon ui-icon icon-file"),
            documentNode("strong", "", "Chưa có tài liệu"),
            documentNode("span", "", "Tải tệp lên để bắt đầu hỏi đáp.")
        );
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
    const name = documentNode("button", "document-open", document.fileName);
    name.type = "button";
    name.title = document.fileName;
    name.setAttribute("aria-label", `Đọc ${document.fileName}`);
    name.addEventListener("click", () => openSource({
        documentId: document.id, fileName: document.fileName, chunkIndex: null
    }));
    const meta = documentNode(
        "span",
        "",
        `${document.chunkCount} đoạn · ${formatBytes(document.size)} · ${formatTime(document.uploadedAt)}`
    );
    info.append(name, meta);
    if (document.warnings?.length) {
        info.append(createWarnings(document.warnings, "Lưu ý khi đọc tài liệu"));
    }

    const remove = documentNode("button", "delete-button");
    const removeIcon = documentNode("span", "ui-icon icon-close");
    removeIcon.setAttribute("aria-hidden", "true");
    remove.append(removeIcon);
    remove.type = "button";
    remove.title = `Xóa ${document.fileName}`;
    remove.setAttribute("aria-label", `Xóa ${document.fileName}`);
    remove.addEventListener("click", () => deleteDocument(document));
    item.append(icon, info, remove);
    return item;
}

async function uploadSelectedFile(event) {
    event.preventDefault();
    if (state.uploading || state.asking || state.refreshing || state.checkingAi || state.deleting || !state.health?.geminiConfigured) return;
    if (!state.selectedFile) {
        showToast("Hãy chọn một tệp PDF, DOC hoặc DOCX.", true);
        return;
    }

    const formData = new FormData();
    formData.append("file", state.selectedFile);
    formData.append("readMode", extensionOf(state.selectedFile.name || "") === "PDF"
        && elements.deepRead.checked ? "DEEP" : "AUTO");
    state.uploading = true;
    uploadFeedback("Đang đọc tài liệu và tạo chỉ mục. Tệp có ảnh hoặc chọn đọc kỹ có thể cần nhiều lượt AI.");
    updateControls();

    try {
        const result = await api("/api/documents/upload", {
            method: "POST",
            body: formData
        });
        showToast(`Đã đọc ${result.fileName}. ${result.warnings?.length
            ? "Có lưu ý về nội dung trích xuất trong thư viện." : "Bạn có thể bắt đầu đặt câu hỏi."}`);
        resetFileSelection();
        await refresh();
        elements.documentScope.value = result.documentId || "";
        uploadFeedback(`Đã đọc ${result.fileName}: ${result.chunkCount} đoạn. ${result.warnings?.length ? "Mở lưu ý trong thư viện trước khi hỏi." : "Bạn có thể bắt đầu đặt câu hỏi."}`);
        elements.questionInput.focus();
    } catch (error) {
        uploadFeedback(error.message, true);
        showToast(error.message, true);
    } finally {
        state.uploading = false;
        updateControls();
    }
}

function chooseFile(file) {
    if (!file || state.uploading) {
        return;
    }
    const extension = extensionOf(file.name);
    if (!["PDF", "DOC", "DOCX"].includes(extension)) {
        resetFileSelection();
        updateControls();
        uploadFeedback("Chỉ hỗ trợ tệp PDF, DOC và DOCX.", true);
        showToast("Chỉ hỗ trợ tệp PDF, DOC và DOCX.", true);
        return;
    }
    if (file.size === 0 || file.size > 10 * 1024 * 1024) {
        resetFileSelection();
        updateControls();
        const message = file.size === 0 ? "Tệp trống. Hãy chọn tài liệu có nội dung." : "Tệp vượt quá giới hạn 10 MB.";
        uploadFeedback(message, true); showToast(message, true);
        return;
    }

    state.selectedFile = file;
    elements.deepRead.checked = false;
    elements.dropTitle.textContent = file.name;
    elements.dropHint.textContent = `${formatBytes(file.size)} · Sẵn sàng để đọc`;
    const sameName = state.documents.some(document => document.fileName?.normalize("NFC").toLowerCase() === file.name.normalize("NFC").toLowerCase());
    uploadFeedback(sameName ? "Tệp cùng tên sẽ thay thế bản cũ sau khi đọc thành công." : "");
    updateControls();
}

function resetFileSelection() {
    state.selectedFile = null;
    elements.fileInput.value = "";
    elements.dropTitle.textContent = "Thả tài liệu vào đây";
    elements.dropHint.textContent = "PDF, DOC, DOCX · tối đa 10 MB";
}

async function deleteDocument(document) {
    if (state.deleting || state.uploading || state.asking || state.refreshing || state.checkingAi) return;
    if (!window.confirm(`Xóa “${document.fileName}” khỏi bộ nhớ RAM?`)) {
        return;
    }
    state.deleting = true; updateControls();
    try {
        await api(`/api/documents/${document.id}`, { method: "DELETE" });
        showToast(`Đã xóa ${document.fileName}.`);
        await refresh();
    } catch (error) {
        showToast(error.message, true);
    } finally { state.deleting = false; updateControls(); }
}

async function clearDocuments() {
    if (state.deleting || state.uploading || state.asking || state.refreshing || state.checkingAi) return;
    if (state.documents.length === 0 || !window.confirm("Xóa toàn bộ tài liệu và vector khỏi RAM?")) {
        return;
    }
    state.deleting = true; updateControls();
    try {
        await api("/api/documents", { method: "DELETE" });
        showToast("Đã xóa toàn bộ tài liệu.");
        await refresh();
    } catch (error) {
        showToast(error.message, true);
    } finally { state.deleting = false; updateControls(); }
}

async function askQuestion(event) {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!question || state.asking || state.uploading || state.refreshing || state.checkingAi || state.deleting
            || !state.health?.geminiConfigured || state.documents.length === 0) {
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
            body: JSON.stringify({ question, documentIds: elements.documentScope.value ? [elements.documentScope.value] : [] })
        });
        loadingMessage.remove();
        appendMessage("assistant", response.answer, response.sources, response.warnings);
    } catch (error) {
        loadingMessage.remove();
        appendMessage("assistant", `Không thể xử lý câu hỏi: ${error.message}`);
        elements.questionInput.value = question;
        resizeTextarea();
        showToast(error.message, true);
    } finally {
        state.asking = false;
        updateControls();
        elements.questionInput.focus();
    }
}

function appendMessage(role, text, sources = [], warnings = []) {
    state.messageCount++;
    const message = documentNode("article", `message ${role}`);
    const avatar = documentNode("div", "message-avatar", role === "user" ? "Bạn" : "");
    if (role !== "user") {
        const icon = documentNode("span", "ui-icon icon-sparkles");
        icon.setAttribute("aria-hidden", "true");
        avatar.append(icon);
    }
    avatar.setAttribute("aria-label", role === "user" ? "Bạn" : "Trợ lý AI");
    const body = documentNode("div", "message-body");
    const bubble = documentNode("div", "bubble");
    if (role === "assistant" && typeof renderAnswer === "function") {
        bubble.classList.add("formatted"); bubble.append(renderAnswer(text, sources, openSource));
    } else bubble.textContent = text;
    body.append(bubble);
    if (role === "assistant") {
        const copy = documentNode("button", "copy-answer", "Sao chép câu trả lời"); copy.type = "button";
        copy.addEventListener("click", async () => {
            try { await navigator.clipboard.writeText(text); showToast("Đã sao chép câu trả lời."); }
            catch { showToast("Trình duyệt chưa cho phép sao chép. Bạn có thể chọn và sao chép phần chữ.", true); }
        });
        body.append(copy);
    }
    if (warnings.length) body.append(createWarnings(warnings, "Lưu ý về độ đầy đủ của câu trả lời"));

    if (sources.length > 0) {
        const sourceList = documentNode("details", "source-list");
        sourceList.append(documentNode("summary", "sources-title", `Xem ${sources.length} nguồn đã truy xuất`));
        sources.forEach((source, index) => sourceList.append(createSourceCard(source, index)));
        body.append(sourceList);
    }

    message.append(avatar, body);
    elements.messageList.append(message);
    scrollMessages();
    return message;
}

function appendLoadingMessage() {
    const message = documentNode("article", "message assistant");
    const avatar = documentNode("div", "message-avatar");
    const icon = documentNode("span", "ui-icon icon-sparkles");
    icon.setAttribute("aria-hidden", "true");
    avatar.append(icon);
    avatar.setAttribute("aria-label", "Trợ lý AI đang trả lời");
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
    const card = documentNode("button", "source-card");
    card.type = "button";
    card.title = "Bấm để xem đoạn này trong tài liệu";
    card.setAttribute("aria-label", `Xem nguồn ${index + 1}, đoạn ${source.chunkIndex} trong ${source.fileName}`);
    const header = documentNode("div", "source-header");
    const title = documentNode(
        "strong",
        "",
        `[Nguồn ${index + 1}] ${source.fileName}${source.pageNumber > 0 ? ` · trang ${source.pageNumber}` : ""} · ${source.section}`
    );
    title.title = `${source.fileName} · ${source.section}`;
    const score = documentNode("span", "source-score", source.score == null ? "Tìm theo từ khóa" : `Tương đồng ${Math.round(source.score * 100)}%`);
    score.title = source.score == null ? "Lượt này không có điểm tương đồng ngữ nghĩa" : "Độ tương đồng với câu hỏi, không phải độ chính xác của câu trả lời";
    header.append(title, score);
    card.append(
        header,
        documentNode("p", "", `Đoạn ${source.chunkIndex}: ${source.excerpt}`),
        documentNode("span", "source-open-hint", "Xem đoạn này trong tài liệu")
    );
    card.addEventListener("click", () => openSource(source));
    return card;
}

let viewerRequest = 0;
async function openSource(source) {
    const request = ++viewerRequest;
    elements.viewerTitle.textContent = source.fileName;
    elements.viewerSubtitle.textContent = "Đang mở bản chữ của tài liệu…";
    elements.viewerLocation.textContent = source.chunkIndex == null ? "Toàn bộ nội dung tài liệu"
        : `${source.pageNumber > 0 ? `Trang ${source.pageNumber} · ` : ""}Đoạn ${source.chunkIndex} · ${source.section}`;
    elements.viewerOriginal.href = `/api/documents/${encodeURIComponent(source.documentId)}/original`;
    elements.viewerContent.replaceChildren();
    elements.sourceViewer.showModal();

    try {
        const content = await api(`/api/documents/${encodeURIComponent(source.documentId)}/content`);
        if (request !== viewerRequest || !elements.sourceViewer.open) return;
        elements.viewerSubtitle.textContent = `${content.chunks.length} đoạn trong bản chữ trích xuất`;
        renderViewerChunks(content.chunks, source.chunkIndex);
    } catch (error) {
        if (request !== viewerRequest) return;
        elements.sourceViewer.close();
        showToast(error.message, true);
    }
}

function renderViewerChunks(chunks, targetIndex) {
    const fragment = document.createDocumentFragment();
    let currentSection = null;
    let selectedChunk = null;

    chunks.forEach(chunk => {
        if (chunk.section !== currentSection) {
            currentSection = chunk.section;
            fragment.append(documentNode("h3", "viewer-section", currentSection));
        }
        const selected = chunk.chunkIndex === targetIndex;
        const block = documentNode("article", `viewer-chunk${selected ? " selected" : ""}`);
        block.append(documentNode("span", "viewer-chunk-number",
            `${chunk.pageNumber > 0 ? `Trang ${chunk.pageNumber} · ` : ""}Đoạn ${chunk.chunkIndex}`));
        const text = documentNode(selected ? "mark" : "p", "viewer-chunk-text", chunk.text);
        block.append(text);
        fragment.append(block);
        if (selected) selectedChunk = block;
    });

    elements.viewerContent.replaceChildren(fragment);
    if (selectedChunk) {
        requestAnimationFrame(() => selectedChunk.scrollIntoView({
            block: selectedChunk.offsetHeight > elements.viewerContent.clientHeight ? "start" : "center",
            behavior: "auto"
        }));
    } else if (targetIndex != null) {
        elements.viewerLocation.textContent = "Không tìm thấy đoạn nguồn trong tài liệu này.";
    } else {
        elements.viewerContent.scrollTop = 0;
    }
}

function updateControls() {
    const configured = Boolean(state.health?.geminiConfigured);
    const hasDocuments = state.documents.length > 0;
    const busy = state.uploading || state.asking || state.refreshing || state.checkingAi || state.deleting;
    elements.documentScope.disabled = busy || !hasDocuments;
    elements.newChatButton.disabled = busy || state.messageCount === 0;
    elements.checkAiButton.disabled = busy || !configured;
    elements.checkAiButton.textContent = state.checkingAi ? "Đang kiểm tra…" : "Kiểm tra AI";
    elements.uploadButton.disabled = busy || !state.selectedFile || !configured;
    elements.uploadLabel.textContent = state.uploading ? "Đang đọc tài liệu..." : "Phân tích tài liệu";
    elements.uploadSpinner.classList.toggle("hidden", !state.uploading);
    elements.fileInput.disabled = state.uploading;
    const pdfSelected = extensionOf(state.selectedFile?.name || "") === "PDF";
    elements.deepRead.disabled = busy || !pdfSelected;
    if (!pdfSelected) elements.deepRead.checked = false;
    elements.dropZone.setAttribute("aria-disabled", String(state.uploading));
    elements.dropZone.tabIndex = state.uploading ? -1 : 0;
    elements.clearButton.disabled = busy || !hasDocuments || !state.health;
    elements.questionInput.disabled = busy || !hasDocuments || !configured;
    elements.askButton.disabled = elements.questionInput.disabled || !elements.questionInput.value.trim();
    elements.questionInput.placeholder = state.refreshing ? "Đang kết nối tới backend…"
        : !state.health ? "Mất kết nối. Hãy bấm Kết nối lại..."
            : !configured ? "Cần cấu hình GEMINI_API_KEY…"
                : hasDocuments ? "Hỏi một điều có trong tài liệu…"
                    : "Tải tài liệu lên rồi nhập câu hỏi…";
    document.querySelectorAll(".prompt-chip").forEach(button => {
        button.disabled = !hasDocuments || !configured || busy;
    });
    document.querySelectorAll(".delete-button").forEach(button => {
        button.disabled = busy || !state.health;
    });
    resizeTextarea();
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

function createWarnings(warnings, title) {
    const details = documentNode("details", "reading-warnings");
    details.append(documentNode("summary", "", title));
    warnings.forEach(warning => details.append(documentNode("p", "", warning)));
    return details;
}

function uploadFeedback(text, error = false) {
    elements.uploadFeedback.textContent = text;
    elements.uploadFeedback.className = `upload-feedback${text ? "" : " hidden"}${error ? " error" : ""}`;
}

async function checkAi() {
    if (state.checkingAi || state.uploading || state.asking || state.refreshing || state.deleting || !state.health?.geminiConfigured) return;
    state.checkingAi = true; updateControls();
    elements.aiCheckStatus.textContent = "Đang thử tìm kiếm và trả lời bằng Gemini thật…";
    elements.aiCheckModels.textContent = "";
    elements.aiCheckDialog.showModal();
    try {
        const result = await api("/api/health/ai", {method: "POST"});
        state.aiReady = result.ready;
        elements.aiCheckStatus.textContent = result.message;
        elements.aiCheckModels.textContent = `Trả lời: ${result.chatModel} — ${result.chatStatus}\nTìm kiếm: ${result.embeddingModel} — ${result.embeddingStatus}`;
    } catch (error) { state.aiReady = false; elements.aiCheckStatus.textContent = error.message; }
    finally { state.checkingAi = false; renderStatus(); }
}

elements.checkAiButton.addEventListener("click", checkAi);
elements.closeAiCheck.addEventListener("click", () => elements.aiCheckDialog.close());
elements.newChatButton.addEventListener("click", () => {
    if (state.asking || state.uploading || state.refreshing || state.checkingAi || state.deleting) return;
    elements.messageList.replaceChildren(elements.welcome);
    state.messageCount = 0; elements.questionInput.value = ""; updateControls();
    elements.messageList.scrollTop = 0; elements.questionInput.focus();
});

const identityKey = "nova.identity";
function readIdentity() {
    try {
        const saved = JSON.parse(window.localStorage.getItem(identityKey)
            || window.localStorage.getItem("le-studio.identity")) || {};
        return {
            owner: typeof saved.owner === "string" ? saved.owner.slice(0, 36) : "",
            signature: typeof saved.signature === "string" ? saved.signature.slice(0, 80) : ""
        };
    } catch {
        return { owner: "", signature: "" };
    }
}

let identity = readIdentity();
function renderIdentity() {
    elements.ownerLabel.textContent = identity.owner || "Không gian của bạn";
    elements.ownerLabel.title = identity.owner || "Đặt tên cho không gian của bạn";
    elements.signatureLabel.textContent = identity.signature || "Một góc riêng. Một cách nghĩ riêng.";
    elements.signatureLabel.title = elements.signatureLabel.textContent;
}

elements.personalizeButton.addEventListener("click", () => {
    elements.ownerInput.value = identity.owner;
    elements.signatureInput.value = identity.signature;
    elements.identityDialog.showModal();
    elements.ownerInput.focus();
});
elements.closeIdentity.addEventListener("click", () => elements.identityDialog.close());
elements.identityForm.addEventListener("submit", event => {
    event.preventDefault();
    identity = {
        owner: elements.ownerInput.value.trim().slice(0, 36),
        signature: elements.signatureInput.value.trim().slice(0, 80)
    };
    let saved = true;
    try {
        window.localStorage.setItem(identityKey, JSON.stringify(identity));
    } catch {
        saved = false;
    }
    renderIdentity();
    elements.identityDialog.close();
    showToast(saved ? "Đã lưu dấu riêng của bạn." : "Đã áp dụng cho phiên này. Trình duyệt đang chặn lưu tùy chỉnh.");
});

elements.uploadForm.addEventListener("submit", uploadSelectedFile);
elements.retryConnection.addEventListener("click", refresh);
elements.fileInput.addEventListener("change", event => chooseFile(event.target.files[0]));
elements.dropZone.addEventListener("keydown", event => {
    if (!state.uploading && (event.key === "Enter" || event.key === " ")) {
        event.preventDefault();
        elements.fileInput.click();
    }
});
elements.clearButton.addEventListener("click", clearDocuments);
elements.closeViewer.addEventListener("click", () => elements.sourceViewer.close());
elements.sourceViewer.addEventListener("close", () => { viewerRequest++; });
elements.chatForm.addEventListener("submit", askQuestion);
elements.questionInput.addEventListener("input", () => {
    resizeTextarea();
    updateControls();
});
elements.questionInput.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        elements.chatForm.requestSubmit();
    }
});

["dragenter", "dragover"].forEach(eventName => {
    elements.dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        if (!state.uploading) elements.dropZone.classList.add("dragover");
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
        elements.questionInput.value = button.dataset?.question || button.textContent;
        resizeTextarea();
        updateControls();
        elements.questionInput.focus();
    });
});

renderIdentity();
updateControls();
refresh();
