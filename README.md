# RAG Document Assistant

Ứng dụng Java Spring hỏi đáp nội dung PDF, DOC và DOCX bằng Gemini. Backend kết hợp tìm kiếm vector với BM25, đưa bằng chứng vào ngữ cảnh trả lời và cung cấp nguồn để đối chiếu.

## Chức năng

- Upload PDF/DOC/DOCX tối đa 10 MB; chọn đọc kỹ từng trang với PDF khó.
- Đọc chữ, bảng và ảnh hỗ trợ; giữ trang PDF, mục và số đoạn.
- Hỏi một tài liệu hoặc toàn thư viện; hiển thị Markdown, bảng và nguồn.
- Mở bản chữ hoặc tệp gốc từ nguồn; thay tài liệu cùng tên và xóa dữ liệu.
- Giao diện React responsive, có chế độ sáng/tối và tùy chọn chuyển động.
- Spring Boot phục vụ frontend đã build; chỉ cần chạy backend.

## Yêu cầu

- **JDK 21** và Git. Maven Wrapper được cung cấp trong `backend`.
- Internet để tải dependencies và gọi Gemini.
- `GEMINI_API_KEY` có quyền sử dụng model và quota phù hợp.

Mặc định dùng `gemini-3.8-flash` và `gemini-embedding-001` (768 chiều). Có thể đổi bằng `GEMINI_CHAT_MODEL` và `GEMINI_EMBEDDING_MODEL` theo quyền truy cập và quota của tài khoản. Google hiện giới hạn quyền truy cập Gemini 2.5 cho tài khoản từng dùng model này; tài khoản mới nên dùng model mới hơn. [Thông báo của Google](https://ai.google.dev/gemini-api/docs/deprecations)

## Chạy ứng dụng

### VS Code

Clone repository, dùng **File → Open Folder** mở thư mục `rag-document-assistant/backend`, cài **Extension Pack for Java** và chọn JDK 21. Chờ Java tải dự án xong, mở **Run and Debug**, chọn **RAG - localhost 8080**, rồi nhấn **F5**.

Dán Gemini API key của người chạy vào hộp nhập có che ký tự và nhấn Enter. Cấu hình chỉ chứa `${input:geminiApiKey}`, không chứa key thật. Log xuất hiện trong **Debug Console** (`Ctrl+Shift+Y`), tránh hiện lệnh PowerShell chứa key trong Terminal. Mở **http://localhost:8080** và giữ phiên chạy; nhấn **Shift+F5** để dừng. Mỗi lần F5 sẽ hỏi lại key.

### PowerShell

```powershell
git clone https://github.com/hoctap970-cloud/rag-document-assistant.git
cd rag-document-assistant/backend
```

Chạy riêng lệnh sau, dán key khi được hỏi rồi nhấn Enter; ký tự nhập sẽ được che:

```powershell
$geminiApiKey = Read-Host "Gemini API key" -AsSecureString
```

Sau khi nhập xong, chạy trong cùng Terminal:

```powershell
$env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new("", $geminiApiKey).Password
$geminiApiKey.Dispose()
Remove-Variable geminiApiKey
.\mvnw.cmd spring-boot:run
```

Mở **http://localhost:8080**. Trên Linux/macOS dùng `./mvnw spring-boot:run` và đặt biến môi trường bằng shell tương ứng.

Trong IntelliJ: mở `backend/pom.xml`, chọn JDK21, thêm `GEMINI_API_KEY` vào Environment variables của Run Configuration và chạy [BackendApplication](backend/src/main/java/com/hoctap970/rag/BackendApplication.java).

Không ghi key vào Java, frontend hoặc `application.properties`. Spring không tự đọc `.env`; `.env.example` chỉ liệt kê các biến cấu hình. Health thông thường không gọi Gemini; nút **Kiểm tra AI** thử chat/embedding thật và dùng quota.

Sau khi ứng dụng chạy, upload tài liệu rồi đặt câu hỏi. Bấm `[Nguồn n]` hoặc **Mở tệp gốc** để đối chiếu đáp án.

## Kiểm thử và đóng gói

Mở terminal tại thư mục gốc `rag-document-assistant` rồi chạy:

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Kiểm thử thường dùng model giả và không gọi Gemini. Các bài đánh giá API thật chỉ chạy khi chủ động bật `RUN_LIVE_RAG_EVAL=true` và có key trong môi trường. CI chạy kiểm thử frontend/backend và đóng gói ứng dụng.

## Frontend

Mã nguồn nằm trong `frontend/`; bundle chạy được nằm trong `backend/src/main/resources/static`. **Không cần Node/npm để chạy ứng dụng Java từ repository.**

Khi sửa frontend, dùng Node tương thích với `frontend/package.json` (khuyến nghị dòng Node24 từ 24.15.0). Mở terminal tại thư mục gốc `rag-document-assistant` rồi chạy:

```powershell
cd frontend
npm ci
npm test
npm run build
```

Build tự đồng bộ vào Spring static. `npm run dev` proxy `/api` về backend cổng 8080. Có thể dùng profile Maven `-Pfrontend` để cài Node và build frontend cùng Java. Dừng dev server trước `npm ci` trên Windows để tránh khóa dependency.

## API

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/health` | Trạng thái backend/cấu hình và số tài liệu/đoạn |
| POST | `/api/health/ai` | Thử chat và embedding thật |
| POST | `/api/documents/upload` | Multipart `file`, `readMode=AUTO` hoặc `DEEP` (PDF) |
| GET | `/api/documents` | Danh sách tài liệu |
| GET | `/api/documents/{id}/content` | Bản chữ và các đoạn |
| GET | `/api/documents/{id}/original` | Tệp gốc |
| DELETE | `/api/documents/{id}` | Xóa một tài liệu |
| DELETE | `/api/documents` | Xóa thư viện |
| POST | `/api/chat` | JSON `question`, tùy chọn `documentIds` |

## Công nghệ và giới hạn

Java 21, Spring Boot 4.1.1, LangChain4j 1.20.0, PDFBox, Apache POI/Tika, Gemini, React và Vite. Fonts/icons được lưu trong ứng dụng cùng giấy phép.

Tệp gốc, chữ và vector nằm trong **RAM**, mất khi dừng ứng dụng; cần upload lại trên máy khác. Mặc định giới hạn 20 tệp, 5.000 đoạn, 100.000.000 byte tệp gốc; mỗi tệp tối đa 800 đoạn, PDF 250 trang và 40 trang/ảnh Vision. Cấu hình nằm trong `application.properties`.

OCR, bố cục bảng phức tạp và suy luận có thể sai; luôn đối chiếu nguồn. Điểm tương đồng không phải xác suất đáp án đúng. Mỗi câu hỏi được xử lý độc lập, chưa có memory hội thoại. Ứng dụng dành cho chạy cục bộ, chưa có đăng nhập, phân quyền hay lưu dữ liệu bền vững. Chữ/ngữ cảnh và ảnh cần đọc được gửi tới Gemini.
