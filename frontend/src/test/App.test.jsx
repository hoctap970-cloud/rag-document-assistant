import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import App from '../App';
import Answer from '../components/Answer';
import { validateFile } from '../api';

const doc = { id: 'doc-1', fileName: 'bai-giang.pdf', chunkCount: 5, size: 2400, warnings: [] };
const source = { documentId: doc.id, fileName: doc.fileName, chunkIndex: 2, section: 'Kiến trúc RAG', pageNumber: 1, score: .82, excerpt: 'RAG truy xuất trước khi trả lời.' };
function response(body, status = 200) { return { ok: status < 400, status, headers: new Headers({ 'Content-Type': 'application/json' }), json: async () => body }; }
function backend({ documents = [doc], configured = true, chat, upload, offline = false } = {}) {
  let docs = [...documents];
  const fetcher = vi.fn(async (url, options = {}) => {
    if (offline) throw new Error('Không thể kết nối máy chủ.');
    if (url === '/api/health') return response({ geminiConfigured: configured });
    if (url === '/api/documents' && options.method !== 'DELETE') return response(docs);
    if (url === '/api/chat') return chat ? chat(options) : response({ answer: 'RAG truy xuất trước khi trả lời [Nguồn 1].', sources: [source], warnings: [] });
    if (url === '/api/documents/upload') { if (upload) return upload(options); docs = [doc]; return response({ documentId: doc.id, fileName: doc.fileName }); }
    if (url.endsWith('/content')) return response({ documentId: doc.id, fileName: doc.fileName, chunks: [{ chunkIndex: 2, section: 'Kiến trúc RAG', pageNumber: 1, text: 'Nội dung đối chiếu nguyên văn.' }] });
    if (options.method === 'DELETE') { docs = []; return response({ message: 'Đã xóa' }); }
    if (url === '/api/health/ai') return response({ ready: true, message: 'AI hoạt động.', chatModel: 'chat', embeddingModel: 'embedding' });
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetcher); return fetcher;
}
beforeEach(() => { document.documentElement.dataset.theme = 'light'; });
async function ready(options) { const fetcher = backend(options); render(<App />); await waitFor(() => expect(screen.getByText('Đã kết nối')).toBeInTheDocument()); return fetcher; }

test('empty library explains how to start and disables sending without documents', async () => {
  await ready({ documents: [] });
  expect(screen.getByLabelText('Câu hỏi của bạn')).toBeDisabled();
  expect(screen.getByText('Chưa có tài liệu')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thêm tài liệu đầu tiên' }));
  expect(document.querySelector('.app')).toHaveAttribute('data-active-tab', 'library');
});

test('uploads a PDF with deep-read setting and selects its returned scope', async () => {
  const fetcher = await ready({ documents: [] });
  const file = new File(['%PDF-content'], 'bai-giang.pdf', { type: 'application/pdf' });
  fireEvent.change(screen.getByLabelText('Chọn tài liệu PDF, DOC hoặc DOCX'), { target: { files: [file] } });
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích tài liệu' }));
  await waitFor(() => expect(screen.getByLabelText('Câu hỏi của bạn')).toBeEnabled());
  const call = fetcher.mock.calls.find(([url]) => url.endsWith('/upload'));
  expect(call[1].body.get('file').name).toBe('bai-giang.pdf');
  expect(call[1].body.get('readMode')).toBe('DEEP');
  expect(screen.getByLabelText('Tài liệu để hỏi')).toHaveValue('doc-1');
});

test('rejects unsupported and oversized files without replacing valid selection', async () => {
  const fetcher = await ready();
  const input = screen.getByLabelText('Chọn tài liệu PDF, DOC hoặc DOCX');
  fireEvent.change(input, { target: { files: [new File(['valid'], 'notes.docx')] } });
  fireEvent.change(input, { target: { files: [new File(['bad'], 'notes.html')] } });
  expect(screen.getByRole('alert')).toHaveTextContent('Chọn tệp PDF, DOC hoặc DOCX.');
  expect(screen.getByRole('button', { name: /notes.docx/ })).toBeInTheDocument();
  expect(validateFile({ name: 'large.pdf', size: 10485761 })).toContain('10 MB');
  expect(fetcher.mock.calls.some(([url]) => url.endsWith('/upload'))).toBe(false);
});

test('scoped chat opens a citation in the source reader and keeps original link', async () => {
  const fetcher = await ready();
  fireEvent.change(screen.getByLabelText('Tài liệu để hỏi'), { target: { value: doc.id } });
  fireEvent.change(screen.getByLabelText('Câu hỏi của bạn'), { target: { value: 'RAG là gì?' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  const citation = await screen.findByRole('button', { name: '[Nguồn 1]' });
  expect(JSON.parse(fetcher.mock.calls.find(([url]) => url === '/api/chat')[1].body)).toEqual({ question: 'RAG là gì?', documentIds: [doc.id] });
  fireEvent.click(citation);
  const dialog = await screen.findByRole('dialog', { name: doc.fileName });
  expect(await within(dialog).findByText('Nội dung đối chiếu nguyên văn.')).toBeInTheDocument();
  expect(within(dialog).getByRole('link', { name: /Mở tệp gốc/ })).toHaveAttribute('href', '/api/documents/doc-1/original');
  expect(dialog.querySelector('.reader-chunk')).toHaveClass('selected');
  fireEvent.click(within(dialog).getByRole('button', { name: 'Đóng cửa sổ' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('chat failures preserve the question for retry and unlock controls', async () => {
  await ready({ chat: () => response({ message: 'Đã hết quota phút.' }, 429) });
  fireEvent.change(screen.getByLabelText('Câu hỏi của bạn'), { target: { value: 'Hãy tóm tắt' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  await screen.findByText('Đã hết quota phút.');
  expect(screen.getByLabelText('Câu hỏi của bạn')).toHaveValue('Hãy tóm tắt');
  expect(screen.getByLabelText('Câu hỏi của bạn')).toBeEnabled();
});

test('blocks upload, deletion and duplicate submissions during an answer', async () => {
  let finish;
  const fetcher = await ready({ chat: () => new Promise(resolve => { finish = resolve; }) });
  fireEvent.change(screen.getByLabelText('Câu hỏi của bạn'), { target: { value: 'Một câu hỏi' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  expect(screen.getByLabelText('Chọn tài liệu PDF, DOC hoặc DOCX')).toBeDisabled();
  expect(screen.getByRole('button', { name: `Xóa ${doc.fileName}` })).toBeDisabled();
  fireEvent.submit(document.querySelector('.composer'));
  expect(fetcher.mock.calls.filter(([url]) => url === '/api/chat')).toHaveLength(1);
  await act(async () => finish(response({ answer: 'Xong.', sources: [] })));
  await screen.findByText('Xong.');
});

test('Enter during IME composition does not submit and Shift+Enter remains multiline', async () => {
  const fetcher = await ready();
  const input = screen.getByLabelText('Câu hỏi của bạn');
  fireEvent.change(input, { target: { value: 'Tiếng Việt' } });
  fireEvent.keyDown(input, { key: 'Enter', isComposing: true, keyCode: 229 });
  fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });
  expect(fetcher.mock.calls.some(([url]) => url === '/api/chat')).toBe(false);
  fireEvent.keyDown(input, { key: 'Enter' });
  await waitFor(() => expect(fetcher.mock.calls.some(([url]) => url === '/api/chat')).toBe(true));
});

test('theme and motion preference persist; personalization retains text safely', async () => {
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Bật giao diện tối' }));
  expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
  expect(localStorage.getItem('nova.theme')).toBe('dark');
  fireEvent.click(screen.getByRole('button', { name: 'Tắt hiệu ứng' }));
  expect(localStorage.getItem('nova.decorativeMotion')).toBe('off');
  expect(document.querySelector('.app')).toHaveClass('motion-paused');
  fireEvent.click(screen.getByRole('button', { name: 'Cá nhân hóa không gian' }));
  fireEvent.change(screen.getByLabelText('Tên hoặc biệt danh'), { target: { value: '<img onerror=alert(1)>' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  expect(screen.getByRole('button', { name: 'Cá nhân hóa không gian' })).toHaveTextContent('<img onerror=alert(1)>');
  expect(document.querySelector('.profile-button img')).toBeNull();
});

test('answer renderer preserves tables but does not render HTML, remote images or unsafe links', () => {
  const { container } = render(<Answer onSource={() => {}} message={{ text: '<script>alert(1)</script>\n\n![track](https://example.com/track)\n\n[unsafe](javascript:alert(1))\n\n| A | B |\n|---|---|\n| 1 | 2 |', sources: [] }} />);
  expect(container.querySelector('script,img,a')).toBeNull();
  expect(container.querySelector('table')).toBeInTheDocument();
});

test('deleting a document requires confirmation and clears a stale scope', async () => {
  const fetcher = await ready();
  fireEvent.change(screen.getByLabelText('Tài liệu để hỏi'), { target: { value: doc.id } });
  fireEvent.click(screen.getByRole('button', { name: `Xóa ${doc.fileName}` }));
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false);
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xóa tài liệu' }));
  await screen.findByText('Chưa có tài liệu');
  expect(screen.getByLabelText('Tài liệu để hỏi')).toHaveValue('');
  expect(screen.getByLabelText('Câu hỏi của bạn')).toBeDisabled();
});

test('offline state reconnects and missing key is explained without making live AI calls', async () => {
  backend({ offline: true }); render(<App />);
  await screen.findByText('Mất kết nối');
  const fetcher = backend({ configured: false });
  await userEvent.click(screen.getByRole('button', { name: 'Kết nối lại' }));
  await screen.findByText('Thêm API key để kết nối AI.');
  expect(screen.getByLabelText('Câu hỏi của bạn')).toBeDisabled();
  expect(fetcher.mock.calls.some(([url]) => url === '/api/health/ai')).toBe(false);
});
