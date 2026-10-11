import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { Icon, Modal } from './Primitives';

export default function SourceViewer({ source, onClose }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const selected = useRef(null);
  useEffect(() => {
    const controller = new AbortController();
    setResult(null); setError('');
    api(`/api/documents/${encodeURIComponent(source.documentId)}/content`, { signal: controller.signal })
      .then(setResult).catch(err => { if (!controller.signal.aborted) setError(err.message); });
    return () => controller.abort();
  }, [source.documentId]);
  useEffect(() => { selected.current?.scrollIntoView({ block: 'center', behavior: 'instant' }); }, [result, source.chunkIndex]);
  return <Modal title={source.fileName} onClose={onClose} className="reader">
    <div className="reader-toolbar"><span>{source.chunkIndex != null ? `Đối chiếu nguồn • Đoạn ${source.chunkIndex}` : 'Toàn bộ tài liệu'}</span>
      <a href={`/api/documents/${encodeURIComponent(source.documentId)}/original`} target="_blank" rel="noopener noreferrer">Mở tệp gốc <Icon name="arrow" /></a></div>
    <p className="reader-note">Bản chữ trích xuất. Mở tệp gốc để xem đúng bố cục PDF hoặc Word.</p>
    <div className="reader-content">{error ? <p role="alert" className="inline-error">{error}</p> : !result ? <p role="status">Đang mở tài liệu…</p> : result.chunks.map(chunk => <section key={chunk.chunkIndex} ref={chunk.chunkIndex === source.chunkIndex ? selected : null} className={`reader-chunk ${chunk.chunkIndex === source.chunkIndex ? 'selected' : ''}`}>
      <div className="chunk-meta"><span>{chunk.section}</span><span>{chunk.pageNumber > 0 ? `Trang ${chunk.pageNumber} / ` : ''}Đoạn {chunk.chunkIndex}</span></div><p>{chunk.text}</p>
    </section>)}</div>
  </Modal>;
}
