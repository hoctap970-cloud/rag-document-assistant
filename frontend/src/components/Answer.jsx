import { memo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Icon, Warnings } from './Primitives';

const plugins = [remarkGfm];
// Only source links are interactive. Raw HTML, remote images and model-generated URLs are not rendered.
const allowed = ['p', 'strong', 'em', 'del', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'h1', 'h2', 'h3', 'h4', 'hr', 'br', 'a'];

function Answer({ message, onSource }) {
  const [copied, setCopied] = useState(false);
  const sources = message.sources || [];
  const text = message.text.replace(/\[Nguồn\s+(\d+)\](?!\()/g, (label, number) => sources[Number(number) - 1] ? `[${label}](#source-${number})` : label);
  async function copy() {
    try { await navigator.clipboard.writeText(message.text); setCopied(true); }
    catch { setCopied(false); }
  }
  return <div className="answer-content">
    <ReactMarkdown remarkPlugins={plugins} allowedElements={allowed} unwrapDisallowed skipHtml components={{
      a: ({ href, children }) => {
        const number = /^#source-(\d+)$/.exec(href || '')?.[1];
        return number && sources[Number(number) - 1]
          ? <button type="button" className="citation" onClick={() => onSource(sources[Number(number) - 1])}>{children}</button> : <span>{children}</span>;
      },
      table: ({ children }) => <div className="table-scroll"><table>{children}</table></div>,
    }}>{text}</ReactMarkdown>
    <Warnings items={message.warnings} title="Lưu ý về câu trả lời" />
    {sources.length > 0 && <details className="sources"><summary><Icon name="book" />{sources.length} nguồn tham khảo</summary><div className="source-grid">{sources.map((source, i) => <button className="source-card" key={`${source.documentId}-${source.chunkIndex}-${i}`} onClick={() => onSource(source)}>
      <span className="source-number">{i + 1}</span><span><strong>{source.fileName}</strong><small>{source.pageNumber > 0 ? `Trang ${source.pageNumber} · ` : ''}{source.section}</small><span className="source-excerpt">{source.excerpt}</span><small title="Độ tương đồng, không phải độ chính xác đáp án">{source.score == null ? 'Tìm theo từ khóa' : `Tương đồng ${Math.round(source.score * 100)}%`}</small></span><Icon name="arrow" />
    </button>)}</div></details>}
    <button className="copy-button" onClick={copy}><Icon name={copied ? 'check' : 'file'} />{copied ? 'Đã sao chép' : 'Sao chép câu trả lời'}</button>
  </div>;
}
export default memo(Answer);
