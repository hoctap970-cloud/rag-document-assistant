import { useEffect, useRef } from 'react';

export function Icon({ name, className = '' }) {
  return <span aria-hidden="true" className={`icon icon-${name} ${className}`} />;
}

export function Warnings({ items = [], title = 'Lưu ý khi đọc tài liệu' }) {
  return items.length ? <details className="warnings"><summary>{title}</summary>{items.map((item, i) => <p key={i}>{item}</p>)}</details> : null;
}

export function Modal({ title, onClose, children, className = '' }) {
  const ref = useRef(null);
  useEffect(() => {
    const node = ref.current;
    const previous = document.activeElement;
    node.showModal();
    return () => { node.close(); previous?.focus?.(); };
  }, []);
  return <dialog ref={ref} className={`modal ${className}`} aria-labelledby="modal-title"
    onCancel={event => { event.preventDefault(); onClose(); }}
    onClick={event => { if (event.target === event.currentTarget) {
      const rect = event.currentTarget.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose();
    } }}>
    <header className="modal-header"><h2 id="modal-title">{title}</h2><button type="button" className="icon-button" onClick={onClose} aria-label="Đóng cửa sổ" autoFocus><Icon name="close" /></button></header>
    {children}
  </dialog>;
}
