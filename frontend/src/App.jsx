import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, domAnimation, LazyMotion, m, MotionConfig, useReducedMotion } from 'motion/react';
import { storage } from './api';
import { useWorkspace } from './useWorkspace';
import Library from './components/Library';
import Welcome from './components/Welcome';
import { Icon, Modal } from './components/Primitives';

const Answer = lazy(() => import('./components/Answer'));
const SourceViewer = lazy(() => import('./components/SourceViewer'));

function readIdentity() {
  try { const value = JSON.parse(storage.get('nova.identity', '{}')); return { owner: typeof value?.owner === 'string' ? value.owner.slice(0, 36) : '', signature: typeof value?.signature === 'string' ? value.signature.slice(0, 80) : '' }; }
  catch { return { owner: '', signature: '' }; }
}

export default function App() {
  const w = useWorkspace();
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || 'dark');
  const [motion, setMotion] = useState(() => storage.get('nova.decorativeMotion', 'on') !== 'off');
  const reduce = useReducedMotion();
  const [visible, setVisible] = useState(() => !document.hidden);
  const [tab, setTab] = useState('chat');
  const [modal, setModal] = useState(null);
  const [source, setSource] = useState(null);
  const [deletion, setDeletion] = useState(undefined);
  const [identity, setIdentity] = useState(readIdentity);
  const input = useRef(null);
  const end = useRef(null);
  const moving = motion && !reduce && visible;
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    storage.set('nova.theme', theme);
    storage.set('nova.palette', 'ocean');
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'light' ? '#e9f0f6' : '#081525');
  }, [theme]);
  useEffect(() => { storage.set('nova.decorativeMotion', motion ? 'on' : 'off'); }, [motion]);
  useEffect(() => {
    const change = () => setVisible(!document.hidden);
    document.addEventListener('visibilitychange', change);
    return () => document.removeEventListener('visibilitychange', change);
  }, []);
  useEffect(() => { if (w.messages.length) end.current?.scrollIntoView({ block: 'nearest', behavior: moving ? 'smooth' : 'instant' }); }, [w.messages, w.phase]);
  useEffect(() => {
    if (input.current) { input.current.style.height = 'auto'; input.current.style.height = `${Math.min(input.current.scrollHeight, 150)}px`; }
  }, [w.question]);
  const onSource = useCallback(next => setSource(next), []);
  function goLibrary() {
    setTab('library');
    requestAnimationFrame(() => document.getElementById('dropZone')?.focus());
  }
  function prompt(text) { w.setQuestion(text); input.current?.focus(); }
  const status = w.phase === 'connect' ? 'Đang kết nối' : !w.health ? 'Mất kết nối' : !w.configured ? 'Chưa thiết lập AI' : w.aiResult?.ready === false ? 'AI chưa sẵn sàng' : w.aiResult?.ready ? 'AI đã kiểm tra' : 'Đã kết nối';

  return <LazyMotion features={domAnimation}><MotionConfig reducedMotion={moving ? 'never' : 'always'} transition={{ duration: moving ? .25 : 0 }}><div className={`app ${moving ? '' : 'motion-paused'}`} data-active-tab={tab}>
    <a href="#questionInput" className="skip-link" onClick={() => setTab('chat')}>Đến ô đặt câu hỏi</a>
    <header className="topbar">
      <a className="brand" href="/" aria-label="NOVA, trang chủ"><span className="brand-symbol"><Icon name="sparkles" /></span><span className="brand-name">NOVA<span>.</span><small>Không gian tri thức</small></span></a>
      <nav className="workspace-nav" aria-label="Điều hướng không gian"><button className={tab === 'chat' ? 'active' : ''} aria-current={tab === 'chat' ? 'page' : undefined} onClick={() => setTab('chat')}><Icon name="sparkles" />Hỏi đáp</button><button className={tab === 'library' ? 'active' : ''} aria-current={tab === 'library' ? 'page' : undefined} onClick={goLibrary}><Icon name="book" />Tài liệu<span className="nav-count">{w.documents.length}</span></button></nav>
      <div className="topbar-actions"><button className="icon-button motion-toggle" aria-label={motion ? 'Tắt hiệu ứng' : 'Bật hiệu ứng'} aria-pressed={motion} onClick={() => setMotion(current => !current)} title="Bật hoặc tắt hiệu ứng"><Icon name="sparkles" /></button><button className="icon-button" aria-label={theme === 'light' ? 'Bật giao diện tối' : 'Bật giao diện sáng'} onClick={() => setTheme(current => current === 'light' ? 'dark' : 'light')} title="Đổi giao diện sáng tối"><Icon name={theme === 'light' ? 'moon' : 'sun'} /></button><span className="header-divider" /><button className="profile-button" onClick={() => setModal('identity')} aria-label="Cá nhân hóa không gian"><span className="profile-avatar">{identity.owner ? identity.owner.charAt(0).toUpperCase() : 'N'}</span><span>{identity.owner || 'Không gian của bạn'}</span></button></div>
    </header>
    {w.health && !w.configured && <div className="config-banner" role="alert"><Icon name="bulb" /><span><strong>Thêm API key để kết nối AI.</strong> Đặt <code>GEMINI_API_KEY</code> trong cấu hình chạy Spring Boot rồi khởi động lại.</span></div>}
    <main className="workspace">
      <Library workspace={w} onSource={onSource} onDelete={setDeletion} />
      <section className="chat-panel" aria-label="Không gian hỏi đáp">
        <header className="chat-header"><div className="chat-title"><span className="chat-mark"><Icon name="sparkles" /></span><div><h1>Không gian hỏi đáp</h1><p>{identity.signature || 'Đọc ít thời gian hơn. Hiểu nhiều hơn.'}</p></div></div><button className="new-chat button" onClick={w.newChat} disabled={!w.messages.length || w.busy}><span aria-hidden="true">＋</span>Hội thoại mới</button></header>
        <div className="chat-toolbar"><label htmlFor="documentScope"><Icon name="book" /><span className="sr-only">Tài liệu để hỏi</span></label><select id="documentScope" aria-label="Tài liệu để hỏi" value={w.scope} onChange={event => w.setScope(event.target.value)} disabled={w.busy || !w.documents.length}><option value="">Toàn bộ thư viện</option>{w.documents.map(doc => <option key={doc.id} value={doc.id}>{doc.fileName}</option>)}</select><div className={`service-status ${w.health && w.configured && w.aiResult?.ready !== false ? 'online' : 'offline'}`} title="Trạng thái backend. Bấm Kiểm tra AI để thử chat và embedding thật."><span />{status}</div><button className="text-button check-ai" disabled={w.busy || (w.health && !w.configured)} onClick={() => { if (!w.health) w.reconnect(); else { setModal('ai'); w.checkAi(); } }}>{w.health ? 'Kiểm tra AI' : 'Kết nối lại'}</button></div>
        <div className="message-list" role="log" aria-label="Hội thoại" aria-live="polite">
          {w.messages.length === 0 ? <Welcome onPrompt={prompt} onUpload={goLibrary} disabled={!w.canAsk} hasDocuments={w.documents.length > 0} /> : <div className="messages">{w.messages.map(message => <m.article key={message.id} initial={{ opacity: 0, y: moving ? 10 : 0 }} animate={{ opacity: 1, y: 0 }} className={`message ${message.role}`}><div className="message-avatar">{message.role === 'user' ? (identity.owner?.charAt(0) || 'B') : <Icon name="sparkles" />}</div><div className="message-body"><span className="message-author">{message.role === 'user' ? 'Bạn' : 'NOVA'}</span>{message.role === 'assistant' ? <Suspense fallback={<p>Đang hiển thị câu trả lời…</p>}><Answer message={message} onSource={onSource} /></Suspense> : <p className="message-text">{message.text}</p>}</div></m.article>)}</div>}
          <AnimatePresence>{w.phase === 'ask' && <m.div key="thinking" className="thinking" role="status" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}><Icon name="sparkles" /><span>NOVA đang tìm trong tài liệu</span><span className="thinking-dots"><i /><i /><i /></span></m.div>}</AnimatePresence><div ref={end} />
        </div>
        <div className="composer-wrap"><form className={`composer ${w.phase === 'ask' ? 'is-thinking' : ''}`} onSubmit={event => { event.preventDefault(); w.ask(); }}><label className="composer-label" htmlFor="questionInput">Câu hỏi của bạn</label><div className="composer-input"><textarea id="questionInput" ref={input} rows="1" maxLength={2000} disabled={!w.canAsk} value={w.question} onChange={event => w.setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) { event.preventDefault(); w.ask(); } }} placeholder={!w.health ? 'Kết nối lại để tiếp tục…' : !w.configured ? 'Cấu hình API key để bắt đầu…' : !w.documents.length ? 'Thêm tài liệu, rồi hỏi bất cứ điều gì về nội dung…' : 'Bạn muốn tìm hiểu điều gì trong tài liệu?'} /><button type="submit" className="send-button" disabled={!w.canAsk || !w.question.trim()} aria-label="Gửi câu hỏi"><Icon name="send" /></button></div><div className="composer-bottom"><span><Icon name="shield" />Trả lời dựa trên tài liệu của bạn</span><span className="keyboard-hint"><kbd>↵</kbd> để gửi</span></div></form><p className="composer-note">AI có thể nhầm lẫn. Luôn đối chiếu với nguồn trong tài liệu.</p></div>
      </section>
    </main>
    <AnimatePresence>{w.toast && <m.div key={w.toast.id} className={`toast ${w.toast.error ? 'error' : ''}`} role="status" initial={{ opacity: 0, y: moving ? 16 : 0 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: moving ? 6 : 0 }}><Icon name={w.toast.error ? 'bulb' : 'check'} />{w.toast.text}</m.div>}</AnimatePresence>
    {source && <Suspense fallback={<Modal title="Mở tài liệu" onClose={() => setSource(null)}><p>Đang tải trình đọc…</p></Modal>}><SourceViewer source={source} onClose={() => setSource(null)} /></Suspense>}
    {deletion !== undefined && <Modal title={deletion ? 'Xóa tài liệu?' : 'Xóa toàn bộ thư viện?'} onClose={() => setDeletion(undefined)}><p>{deletion ? deletion.fileName : 'Tất cả tài liệu đã tải lên'} sẽ được xóa khỏi phiên này. Bạn có thể tải tệp lên lại khi cần.</p><div className="modal-actions"><button className="button" onClick={() => setDeletion(undefined)}>Giữ lại</button><button className="button danger" onClick={() => { w.remove(deletion); setDeletion(undefined); }}>Xóa tài liệu</button></div></Modal>}
    {modal === 'ai' && <Modal title="Kiểm tra kết nối AI" onClose={() => setModal(null)}><p role="status">{w.phase === 'check' ? 'Đang thử chat và tìm kiếm bằng Gemini…' : w.aiResult?.message}</p>{w.aiResult?.chatModel && <dl className="ai-results"><dt>Trả lời</dt><dd>{w.aiResult.chatModel}<small>{w.aiResult.chatStatus}</small></dd><dt>Tìm kiếm</dt><dd>{w.aiResult.embeddingModel}<small>{w.aiResult.embeddingStatus}</small></dd></dl>}<p className="modal-note">Thao tác này dùng một ít quota API. Kết quả phản ánh kết nối tại thời điểm kiểm tra.</p></Modal>}
    {modal === 'identity' && <Modal title="Không gian của bạn" onClose={() => setModal(null)}><form className="identity-form" onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); const value = { owner: String(data.get('owner')).trim(), signature: String(data.get('signature')).trim() }; setIdentity(value); storage.set('nova.identity', JSON.stringify(value)); setModal(null); }}><label htmlFor="ownerInput">Tên hoặc biệt danh</label><input id="ownerInput" name="owner" maxLength="36" defaultValue={identity.owner} placeholder="Bạn muốn được gọi là gì?" autoComplete="nickname" /><label htmlFor="signatureInput">Lời nhắn cho không gian này</label><input id="signatureInput" name="signature" maxLength="80" defaultValue={identity.signature} placeholder="Đọc ít thời gian hơn. Hiểu nhiều hơn." /><p className="modal-note">Chỉ lưu trên trình duyệt này.</p><button className="button primary" type="submit">Lưu thay đổi<Icon name="check" /></button></form></Modal>}
  </div></MotionConfig></LazyMotion>;
}
