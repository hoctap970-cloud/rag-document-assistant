# Quy trình đóng góp

Repository dùng quy trình branch → commit → push → Pull Request → CI → merge. Không phát triển trực tiếp trên `main`.

## Chuẩn bị

```powershell
git switch main
git pull origin main
git status
```

`git status` phải sạch trước khi tạo nhánh.

## Đặt tên nhánh

| Loại | Mẫu | Ví dụ |
|---|---|---|
| Tính năng | `feat/...` | `feat/chat-history` |
| Sửa lỗi | `fix/...` | `fix/pdf-validation` |
| Tài liệu | `docs/...` | `docs/demo-script` |
| Bảo trì | `chore/...` | `chore/update-dependencies` |

Tạo nhánh:

```powershell
git switch -c feat/ten-chuc-nang
```

## Trong khi làm việc

Kiểm tra thay đổi thường xuyên:

```powershell
git status
git diff
```

Chạy kiểm thử backend:

```powershell
cd backend
.\mvnw.cmd test
cd ..
```

## Commit

Commit nên nhỏ, tập trung vào một mục đích và dùng động từ hiện tại:

```powershell
git add backend/src README.md docs
git commit -m "feat: add document retrieval with source references"
```

Tiền tố:

- `feat:` tính năng mới.
- `fix:` sửa lỗi.
- `docs:` chỉ sửa tài liệu.
- `test:` thêm/sửa test.
- `refactor:` đổi cấu trúc nhưng không đổi hành vi.
- `chore:` cấu hình, dependency hoặc việc bảo trì.

Không commit API key, `.env`, `target`, `.idea` hoặc tài liệu cá nhân.

## Push và Pull Request

```powershell
git push -u origin feat/ten-chuc-nang
```

Trong Pull Request:

1. Mô tả vấn đề và kết quả sau thay đổi.
2. Liệt kê cách đã kiểm thử.
3. Xem toàn bộ tab **Files changed**.
4. Chờ GitHub Actions thành công.
5. Merge bằng **Squash and merge** nếu nhánh có nhiều commit thử nghiệm.
6. Xóa branch sau merge.

## Đồng bộ sau merge

```powershell
git switch main
git pull origin main
git branch -d feat/ten-chuc-nang
git fetch --prune
```
