export async function api(path, options = {}) {
  const response = await fetch(path, { cache: 'no-store', ...options });
  const json = response.headers.get('content-type')?.includes('application/json');
  const body = json ? await response.json() : null;
  if (!response.ok) throw new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`);
  if (!json && response.status !== 204) throw new Error('Máy chủ trả về dữ liệu không hợp lệ. Hãy kết nối lại.');
  return body;
}

export const fileType = name => name.split('.').pop().toUpperCase();
export const fileSize = bytes => bytes < 1048576 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1048576).toFixed(1)} MB`;
export function validateFile(file) {
  if (!file) return 'Hãy chọn một tài liệu.';
  if (!/\.(pdf|docx?)$/i.test(file.name)) return 'Chọn tệp PDF, DOC hoặc DOCX.';
  if (file.size === 0) return 'Tệp đang trống. Hãy chọn tài liệu có nội dung.';
  if (file.size > 10 * 1024 * 1024) return 'Tệp vượt quá 10 MB. Hãy chọn tệp nhỏ hơn.';
  return '';
}
export const storage = {
  get(key, fallback) { try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, value); } catch { /* Optional preferences. */ } },
};
