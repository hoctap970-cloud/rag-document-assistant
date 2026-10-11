import { memo } from 'react';
import { m } from 'motion/react';
import { Icon } from './Primitives';

const prompts = [
  { icon: 'list', color: 'iris', title: 'Nắm bắt ý chính', description: 'Tóm tắt nội dung theo cách dễ hiểu', question: 'Hãy tóm tắt các ý quan trọng trong tài liệu.' },
  { icon: 'bulb', color: 'peach', title: 'Hiểu sâu một chủ đề', description: 'Làm rõ khái niệm và tìm ví dụ', question: 'Có những khái niệm quan trọng nào cần ghi nhớ? Hãy giải thích dễ hiểu.' },
  { icon: 'book', color: 'mint', title: 'Tìm câu trả lời', description: 'Tra cứu chi tiết kèm nguồn cụ thể', question: 'Tài liệu nói về chủ đề chính nào? Hãy dẫn nguồn cụ thể.' },
  { icon: 'check', color: 'pink', title: 'Kết nối kiến thức', description: 'Rút ra kết luận từ tài liệu', question: 'Kết luận chính của tài liệu là gì và dựa trên những luận điểm nào?' },
];

function Welcome({ onPrompt, onUpload, disabled, hasDocuments }) {
  return <div className="welcome">
    <section className="welcome-hero" aria-label="Bắt đầu với NOVA">
      <div className="hero-copy"><span className="hero-caption"><Icon name="sparkles" />Một cách mới để đọc tài liệu</span><h2>Hiểu tài liệu.<br />Mở thêm ý tưởng.</h2><p>Hỏi điều bạn tò mò.<br />NOVA tìm câu trả lời, kèm nguồn để đối chiếu.</p><button className="hero-link" onClick={onUpload}>{hasDocuments ? 'Khám phá thư viện' : 'Thêm tài liệu đầu tiên'}<Icon name="arrow" /></button></div>
      <div className="hero-art" aria-hidden="true"><span className="art-disc" /><img src="/images/nova-folders.png" width="1280" height="1280" alt="" fetchPriority="high" /></div>
    </section>
    <div className="suggestion-heading"><h3>Bắt đầu từ một câu hỏi hay</h3><span>Chọn một gợi ý bên dưới</span></div>
    <div className="prompt-grid">{prompts.map((prompt, index) => <m.button key={prompt.title} type="button" className={`prompt-card ${prompt.color}`} disabled={disabled}
      initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .25, delay: index * .045 }}
      whileHover={disabled ? {} : { y: -4 }} whileTap={disabled ? {} : { scale: .98 }} onClick={() => onPrompt(prompt.question)}>
      <span className="prompt-icon"><Icon name={prompt.icon} /></span><span className="prompt-copy"><strong>{prompt.title}</strong><small>{prompt.description}</small></span><Icon name="arrow" />
    </m.button>)}</div>
  </div>;
}
export default memo(Welcome);
