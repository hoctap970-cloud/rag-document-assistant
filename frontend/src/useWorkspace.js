import { useCallback, useEffect, useRef, useState } from 'react';
import { api, validateFile } from './api';

export function useWorkspace() {
  const [health, setHealth] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [phase, setPhase] = useState('connect');
  const [file, setFile] = useState(null);
  const [deepRead, setDeepRead] = useState(false);
  const [scope, setScope] = useState('');
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [feedback, setFeedback] = useState(null);
  const [toast, setToast] = useState(null);
  const [aiResult, setAiResult] = useState(null);
  const lock = useRef(false);
  const sequence = useRef(0);
  const notify = useCallback((text, error = false) => setToast({ text, error, id: ++sequence.current }), []);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 5500);
    return () => clearTimeout(timer);
  }, [toast]);

  const load = useCallback(async signal => {
    const [nextHealth, nextDocs] = await Promise.all([
      api('/api/health', { signal }), api('/api/documents', { signal }),
    ]);
    if (signal?.aborted) return;
    setHealth(nextHealth); setDocuments(nextDocs);
    setScope(current => nextDocs.some(doc => doc.id === current) ? current : '');
  }, []);

  const reconnect = useCallback(async () => {
    if (lock.current) return;
    lock.current = true; setPhase('connect');
    try { await load(); }
    catch (err) { setHealth(null); notify(err.message, true); }
    finally { lock.current = false; setPhase(''); }
  }, [load, notify]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).catch(err => { if (!controller.signal.aborted) { setHealth(null); notify(err.message, true); } })
      .finally(() => { if (!controller.signal.aborted) setPhase(''); });
    window.addEventListener('online', reconnect);
    return () => { controller.abort(); window.removeEventListener('online', reconnect); };
  }, [load, notify, reconnect]);

  const busy = Boolean(phase);
  const configured = Boolean(health?.geminiConfigured);
  const canAsk = configured && documents.length > 0 && !busy;
  function selectFile(candidate) {
    if (lock.current || busy) return;
    const error = validateFile(candidate);
    if (error) { setFeedback({ text: error, error: true }); return; }
    setFile(candidate); setDeepRead(false); setFeedback(null);
  }

  async function upload() {
    if (lock.current || busy || !configured || !file) return;
    lock.current = true; setPhase('upload');
    setFeedback({ text: 'Đang đọc nội dung và tạo chỉ mục. Tài liệu có ảnh có thể cần thêm thời gian.' });
    const form = new FormData(); form.append('file', file); form.append('readMode', /\.pdf$/i.test(file.name) && deepRead ? 'DEEP' : 'AUTO');
    try {
      const result = await api('/api/documents/upload', { method: 'POST', body: form });
      setFile(null); setDeepRead(false);
      await load(); setScope(result.documentId || '');
      setFeedback({ text: `Đã đọc ${result.fileName}. Bạn có thể đặt câu hỏi.` });
      notify('Tài liệu đã sẵn sàng để hỏi đáp.');
    } catch (err) { setFeedback({ text: err.message, error: true }); }
    finally { lock.current = false; setPhase(''); }
  }

  async function ask() {
    const text = question.trim();
    if (lock.current || !canAsk || !text) return;
    lock.current = true; setPhase('ask'); setQuestion('');
    setMessages(current => [...current, { id: ++sequence.current, role: 'user', text }]);
    try {
      const result = await api('/api/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: text, documentIds: scope ? [scope] : [] }) });
      setMessages(current => [...current, { id: ++sequence.current, role: 'assistant', text: result.answer, sources: result.sources || [], warnings: result.warnings || [] }]);
    } catch (err) {
      setQuestion(text);
      setMessages(current => [...current, { id: ++sequence.current, role: 'error', text: err.message }]);
      notify('Chưa gửi được câu hỏi. Nội dung đã được giữ lại để thử lại.', true);
    } finally { lock.current = false; setPhase(''); }
  }

  async function remove(doc) {
    if (lock.current || busy) return;
    lock.current = true; setPhase('delete');
    try {
      await api(doc ? `/api/documents/${encodeURIComponent(doc.id)}` : '/api/documents', { method: 'DELETE' });
      await load(); notify(doc ? `Đã xóa ${doc.fileName}.` : 'Đã xóa thư viện.');
    } catch (err) { notify(err.message, true); }
    finally { lock.current = false; setPhase(''); }
  }

  async function checkAi() {
    if (lock.current || busy || !configured) return;
    lock.current = true; setPhase('check'); setAiResult(null);
    try { setAiResult(await api('/api/health/ai', { method: 'POST' })); }
    catch (err) { setAiResult({ ready: false, message: err.message }); }
    finally { lock.current = false; setPhase(''); }
  }

  function newChat() { if (!busy) { setMessages([]); setQuestion(''); } }
  return { health, documents, phase, busy, configured, canAsk, file, deepRead, setDeepRead, scope, setScope, messages, question, setQuestion,
    feedback, toast, aiResult, reconnect, selectFile, upload, ask, remove, checkAi, newChat, notify };
}
