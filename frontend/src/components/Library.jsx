import { useRef, useState } from 'react';
import { fileSize, fileType } from '../api';
import { Icon, Warnings } from './Primitives';

export default function Library({ workspace: w, onSource, onDelete }) {
  const input = useRef(null);
  const depth = useRef(0);
  const [dragging, setDragging] = useState(false);
  return <aside className="library-panel" id="library" aria-label="Quản lý tài liệu">
    <div className="library-heading"><div><h2>Tài liệu của bạn</h2><p>Mọi câu trả lời bắt đầu từ đây.</p></div><span className="heading-icon"><Icon name="book" /></span></div>
    <form onSubmit={event => { event.preventDefault(); w.upload(); }} className="upload-form">
      <input ref={input} id="fileInput" aria-label="Chọn tài liệu PDF, DOC hoặc DOCX" type="file" accept=".pdf,.doc,.docx" hidden disabled={w.busy}
        onChange={event => { if (event.target.files[0]) w.selectFile(event.target.files[0]); event.target.value = ''; }} />
      <button id="dropZone" type="button" disabled={w.busy} className={`drop-zone ${dragging ? 'dragover' : ''} ${w.file ? 'has-file' : ''}`} onClick={() => input.current.click()}
        onDragOver={event => event.preventDefault()}
        onDragEnter={event => { event.preventDefault(); depth.current++; if (!w.busy) setDragging(true); }}
        onDragLeave={event => { event.preventDefault(); if (--depth.current <= 0) setDragging(false); }}
        onDrop={event => { event.preventDefault(); depth.current = 0; setDragging(false); if (w.busy) return; if (event.dataTransfer.files.length !== 1) w.notify('Hãy thêm từng tài liệu một.', true); else w.selectFile(event.dataTransfer.files[0]); }}>
        <span className="upload-symbol"><Icon name={w.file ? 'file' : 'upload'} /></span>
        <strong>{w.file ? w.file.name : 'Thêm tài liệu'}</strong>
        <span>{w.file ? `${fileSize(w.file.size)} · Bấm để đổi tệp` : 'Kéo thả hoặc chọn từ máy tính'}</span>
        <span className="file-formats"><b>PDF</b><b>DOC</b><b>DOCX</b><span>Tối đa 10 MB</span></span>
      </button>
      <label className="read-mode"><input type="checkbox" checked={w.deepRead} disabled={w.busy || !w.file || fileType(w.file.name) !== 'PDF'} onChange={event => w.setDeepRead(event.target.checked)} />
        <span><strong>Đọc kỹ từng trang PDF</strong><small>Cho bản scan, bảng và sơ đồ. Dùng thêm thời gian và quota AI.</small></span></label>
      <button className="button primary upload-button" type="submit" disabled={w.busy || !w.file || !w.configured}><Icon name={w.phase === 'upload' ? 'sparkles' : 'arrow'} />{w.phase === 'upload' ? 'Đang phân tích…' : 'Phân tích tài liệu'}</button>
      {w.phase === 'upload' && <div className="processing" role="status"><span />Đang tìm hiểu tài liệu của bạn</div>}
      {w.feedback && <p className={`upload-feedback ${w.feedback.error ? 'inline-error' : ''}`} role={w.feedback.error ? 'alert' : 'status'}>{w.feedback.text}</p>}
    </form>
    <section className="library-section"><div className="section-heading"><h3>Thư viện <span className="count">{w.documents.length}</span></h3><button className="text-button" disabled={!w.documents.length || w.busy} onClick={() => onDelete(null)}>Xóa hết</button></div>
      {w.documents.length ? <><p className="library-stats">{w.documents.reduce((total, doc) => total + doc.chunkCount, 0)} đoạn đã được lập chỉ mục</p><ul className="document-list">{w.documents.map(doc => <li className="document-item" key={doc.id}>
        <span className={`file-badge ${fileType(doc.fileName).toLowerCase()}`}>{fileType(doc.fileName)}</span><div className="document-info"><button onClick={() => onSource({ documentId: doc.id, fileName: doc.fileName })}>{doc.fileName}</button><small>{doc.chunkCount} đoạn · {fileSize(doc.size)}</small><Warnings items={doc.warnings} /></div><button className="icon-button delete-button" disabled={w.busy} onClick={() => onDelete(doc)} aria-label={`Xóa ${doc.fileName}`}><Icon name="close" /></button>
      </li>)}</ul></> : <div className="empty-library"><span className="empty-paper"><Icon name="file" /></span><strong>Chưa có tài liệu</strong><p>Thêm bài giảng, báo cáo hoặc ghi chú<br />để bắt đầu khám phá.</p></div>}
    </section>
    <div className="library-footer"><Icon name="shield" /><p>Tài liệu chỉ lưu trong phiên này.<br />Dừng ứng dụng sẽ xóa dữ liệu.</p></div>
  </aside>;
}
