# Hướng dẫn đọc và hiểu dự án

Tài liệu này giải thích luồng hoạt động, vai trò của từng thư mục, từng file nguồn và các hàm chính. Hãy đọc theo thứ tự: kiến trúc tổng thể → luồng upload → luồng hỏi đáp → danh sách file.

## 1. Bức tranh tổng thể

Dự án có một repository Git và một ứng dụng Spring Boot trong thư mục `backend`. Spring Boot làm hai việc:

1. Cung cấp REST API tại `/api/...`.
2. Phục vụ frontend tĩnh từ `src/main/resources/static`.

Nhờ vậy, người demo chỉ cần chạy `BackendApplication` và mở `http://localhost:8080`.

Các lớp Java được chia theo trách nhiệm:

```text
controller  -> nhận HTTP request, gọi service, trả response
service     -> xử lý file, RAG, Gemini và vector store
config      -> nhận cấu hình từ application.properties/biến môi trường
domain      -> mô hình dữ liệu nội bộ
dto         -> dữ liệu gửi/nhận qua API
exception   -> các lỗi nghiệp vụ có ý nghĩa
```

## 2. Luồng upload tài liệu

Khi người dùng nhấn **Phân tích tài liệu**:

1. `app.js` tạo `FormData` gồm `file` và `readMode` (`AUTO` mặc định, `DEEP` khi chọn đọc kỹ PDF).
2. `DocumentController.upload()` nhận `MultipartFile`.
3. `RagService.upload()` điều phối toàn bộ quá trình.
4. `DocumentFileValidator` kiểm tra file rỗng, tên file và đuôi mở rộng.
5. `DocumentParserService` dùng PDFBox cho PDF, POI cho DOCX, Tika cho DOC; `DocumentVisionService` đọc thêm trang/hình cần Vision hoặc mọi trang PDF ở chế độ `DEEP`. Kế hoạch đọc PDF được kiểm tra giới hạn trước khi gọi Gemini.
6. `SectionExtractor` giữ dòng tiêu đề, nhận diện tên mục và số trang PDF.
7. `DocumentSplitters.recursive()` chia từng mục thành chunk 1.500 ký tự, overlap 220; đoạn tiếp theo được thêm tên mục.
8. Mỗi `TextSegment` nhận metadata: `document_id`, `file_name`, `section`, `chunk_index`, `title`, `page_number` (0 nếu chưa xác định).
9. `GeminiModelProvider.documentEmbeddingModel()` dùng task type `RETRIEVAL_DOCUMENT` để tạo vector 768 chiều.
10. `RagService` lưu cặp vector + `TextSegment` vào `InMemoryEmbeddingStore`; metadata, các đoạn chữ và bản gốc được giữ trong `ConcurrentHashMap` để mở nguồn.
11. API trả `UploadResponse` kèm các lưu ý trích xuất; frontend cập nhật danh sách, thống kê và lưu ý.

Nếu bước tạo embedding lỗi, tài liệu không được thêm vào danh sách. Nếu việc ghi vector bị lỗi giữa chừng, các vector vừa ghi được xóa để tránh dữ liệu nửa vời.

## 3. Luồng hỏi đáp

Khi người dùng gửi câu hỏi:

1. `ChatController.ask()` kiểm tra JSON bằng Bean Validation.
2. `RagService.ask()` từ chối câu hỏi rỗng hoặc khi chưa có tài liệu.
3. `queryEmbeddingModel()` tạo vector cho câu hỏi với task type `RETRIEVAL_QUERY`.
4. `InMemoryEmbeddingStore.search()` lấy score toàn bộ các chunk. `HybridRetriever` kết hợp vector với BM25, RRF, rerank và đoạn liền kề trong ngân sách. Phạm vi ngắn được đưa vào toàn bộ.
5. Nếu không chọn được bằng chứng, backend trả thông báo không tìm thấy và không gọi chat model.
6. Nếu có kết quả, `buildPrompt()` ghép câu hỏi và bằng chứng thành dữ liệu có nhãn `[Nguồn n]`. Quy tắc trả lời dùng `SystemMessage` riêng.
7. Chat model (mặc định `gemini-2.5-flash`) trả lời với temperature 0.0. Backend kiểm tra mã nguồn, cảnh báo nếu thiếu trích dẫn hoặc đầu ra bị cắt.
8. `toSources()` tạo danh sách nguồn từ metadata thật của các chunk. Nguồn này không phụ thuộc model tự kể ra.
9. Frontend hiển thị câu trả lời và các thẻ nguồn để đối chiếu. Mỗi nguồn chứa `documentId` và `chunkIndex` do backend gắn trực tiếp từ kết quả search.
10. Khi bấm nguồn, frontend gọi `GET /api/documents/{id}/content`, dựng bản chữ, cuộn đến chunk tương ứng và đánh dấu nổi bật. `GET /api/documents/{id}/original` phục vụ tệp gốc từ RAM.

## 4. Cây thư mục

```text
rag-document-assistant/
├── .github/
│   ├── workflows/backend-ci.yml
│   └── pull_request_template.md
├── backend/
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── src/main/java/com/hoctap970/rag/
│   │   ├── BackendApplication.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── domain/
│   │   ├── dto/
│   │   ├── exception/
│   │   └── service/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── static/
│   │       ├── index.html
│   │       ├── css/nova.css
│   │       └── js/app.js
│   ├── src/test/java/com/hoctap970/rag/
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── docs/
│   ├── PROJECT_GUIDE.md
│   └── DEMO_GUIDE.md
├── .env.example
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```

## 5. Giải thích từng file gốc

### `.gitignore`

Không cho Git theo dõi file build, cấu hình IntelliJ, `node_modules`, file hệ điều hành và secret như `.env`. Dòng `!.env.example` cho phép commit file mẫu an toàn.

### `.env.example`

Cho biết tên biến môi trường cần dùng. Giá trị chỉ là placeholder; ứng dụng Spring Boot không tự đọc file `.env`. Khi dùng IntelliJ, nhập biến vào Run Configuration.

### `README.md`

Trang giới thiệu chính trên GitHub: chức năng, kiến trúc, cách lấy API key, cách chạy, endpoint, cấu hình, giới hạn và quy trình Git.

### `CONTRIBUTING.md`

Quy định tạo branch, đặt commit, kiểm thử và Pull Request. Đây là phần giúp repository thể hiện quy trình làm việc chuyên nghiệp.

### `.github/workflows/backend-ci.yml`

GitHub Actions workflow. GitHub tạo máy Ubuntu, cài JDK 21, chạy test và đóng gói backend. Không cần `GEMINI_API_KEY` vì model được tạo theo kiểu lazy và test không gọi API bên ngoài.

### `.github/pull_request_template.md`

Mẫu mô tả Pull Request, buộc người làm ghi thay đổi, cách kiểm thử và checklist bảo mật.

## 6. Build và cấu hình backend

### `backend/pom.xml`

File Maven khai báo:

- Spring Boot parent 4.1.1.
- Java 21.
- LangChain4j BOM 1.20.0 để đồng bộ phiên bản module.
- Spring Web MVC cho REST API và static web.
- Bean Validation cho `@NotBlank`, `@Size`.
- LangChain4j core, Apache Tika parser, PDFBox, POI và Google GenAI integration.
- Thư viện test của Spring Boot.
- Spring Boot Maven plugin để chạy/đóng gói JAR.

### `backend/mvnw` và `backend/mvnw.cmd`

Maven Wrapper cho Linux/macOS và Windows. Người clone dự án không cần cài Maven toàn cục. `mvnw.cmd` có thêm kiểm tra an toàn cho PowerShell khi thư mục `.m2` không phải symbolic link.

### `backend/.mvn/wrapper/maven-wrapper.properties`

Chỉ định Maven 3.9.16 được Wrapper tải về.

### `backend/.gitattributes`

Chuẩn hóa line ending cho script Maven giữa Windows và Linux.

### `backend/.gitignore`

File do Spring Initializr tạo để loại trừ `target` và cấu hình IDE ở mức module.

### `backend/HELP.md`

Tài liệu tham khảo do Spring Initializr sinh ra. Không tham gia luồng chạy của ứng dụng.

### `application.properties`

- `spring.application.name`: tên hiển thị trong log.
- `spring.servlet.multipart.*`: giới hạn request/file 10 MB.
- `app.gemini.api-key=${GEMINI_API_KEY:}`: lấy secret từ hệ điều hành; giá trị mặc định rỗng giúp app vẫn khởi động để báo hướng dẫn.
- `app.gemini.*-model`: tên model chat/embedding.
- `app.rag.*`: chunk, overlap, số ứng viên, nhánh vector, rerank, đoạn kề và ngân sách ngữ cảnh.
- `app.parsing.*`: bật Vision, giới hạn số trang PDF và số trang/hình qua Vision.

## 7. Các file Java

### `BackendApplication.java`

- `@SpringBootApplication`: bật auto-configuration, component scan và cấu hình Spring.
- `@ConfigurationPropertiesScan`: tìm các record cấu hình trong package con.
- `main()`: điểm bắt đầu chương trình.

### Package `config`

#### `GeminiProperties.java`

Record ánh xạ nhóm `app.gemini`: API key, chat model, embedding model, số chiều vector, giới hạn đầu ra và timeout.

#### `RagProperties.java`

Record ánh xạ nhóm `app.rag`, kiểm tra các giới hạn chunk, số ứng viên và ngân sách hợp lệ. `ParsingProperties` ánh xạ nhóm `app.parsing`.

### Package `controller`

#### `DocumentController.java`

- `upload()`: `POST /api/documents/upload`.
- `list()`: `GET /api/documents`.
- `content()`: `GET /api/documents/{id}/content`, trả các đoạn chữ theo đúng thứ tự trong tài liệu.
- `original()`: `GET /api/documents/{id}/original`, mở PDF hoặc tải DOC/DOCX gốc. Backend xác định kiểu trả về từ đuôi tệp đã được chấp nhận, đặt `nosniff` và `no-store`.
- `delete()`: xóa một tài liệu theo UUID.
- `clear()`: xóa mọi tài liệu.

Controller chỉ nhận/trả HTTP và giao việc cho `RagService`, tránh chứa nghiệp vụ.

#### `ChatController.java`

Nhận `POST /api/chat`, kích hoạt Bean Validation bằng `@Valid` và chuyển câu hỏi cho `RagService.ask()`.

#### `HealthController.java`

Trả trạng thái backend, trạng thái cấu hình Gemini, số tài liệu và số chunk. Frontend dùng dữ liệu này để bật/tắt nút phù hợp.

#### `GlobalExceptionHandler.java`

Chuyển exception thành HTTP response thống nhất:

- 400: dữ liệu không hợp lệ.
- 404: không tìm thấy tài liệu hoặc tài nguyên tĩnh. Thiếu favicon hay đường dẫn không tồn tại không được báo thành lỗi 500.
- 413: file trên 10 MB.
- 422: Không đọc được tài liệu hoặc vượt khả năng đọc trang/hình theo cấu hình.
- 502: Gemini/API bên ngoài lỗi.
- 503: chưa cấu hình API key.
- 500: lỗi ngoài dự kiến.

Lỗi server được ghi vào log nhưng response không lộ stack trace.

### Package `domain`

#### `IndexedDocument.java`

Mô hình nội bộ của một tài liệu đã lập chỉ mục. Ngoài metadata hiển thị, record giữ danh sách `embeddingIds` để xóa đúng vector, byte của tệp gốc và danh sách chunk để xem nguồn. Tất cả chỉ tồn tại trong RAM.

#### `IndexedChunk.java`

Một đoạn văn bản đã lập chỉ mục, gồm số thứ tự, tên mục, toàn văn và số trang PDF. Cửa sổ xem nguồn dùng danh sách này để đánh dấu đúng đoạn.

#### `SectionContent.java`

Một mục đã nhận diện gồm `title`, `text` và `pageNumber`. `ParsedDocument` giữ bản chữ cùng các lưu ý về việc đọc nội dung.

### Package `dto`

DTO là các record chỉ dùng để truyền dữ liệu qua ranh giới API:

| File | Vai trò |
|---|---|
| `ChatRequest.java` | Câu hỏi đầu vào; không rỗng, tối đa 2.000 ký tự |
| `ChatResponse.java` | Câu hỏi, câu trả lời, nguồn và lưu ý |
| `SourceReference.java` | ID tài liệu, tên file, mục, trang PDF, số chunk, score và trích đoạn; ID nối nguồn với đúng tài liệu |
| `DocumentContent.java` | ID, tên file và danh sách `IndexedChunk` trả về cho cửa sổ xem nguồn |
| `UploadResponse.java` | Kết quả upload và thống kê indexing |
| `DocumentSummary.java` | Thông tin tài liệu an toàn để đưa lên UI |
| `HealthResponse.java` | Trạng thái ứng dụng và số liệu trong RAM |
| `MessageResponse.java` | Thông báo cho thao tác xóa |
| `ApiError.java` | Khuôn lỗi chung gồm thời gian, status, message và path |

### Package `exception`

| File | Trường hợp |
|---|---|
| `BadRequestException.java` | Input sai hoặc chưa có tài liệu |
| `NotFoundException.java` | UUID tài liệu không tồn tại |
| `AiConfigurationException.java` | Thiếu `GEMINI_API_KEY` |
| `AiServiceException.java` | Gemini lỗi, mất mạng hoặc hết quota |
| `DocumentProcessingException.java` | Tika không đọc/trích chữ được |

Tách exception giúp `RagService` mô tả đúng lỗi nghiệp vụ và để `GlobalExceptionHandler` quyết định HTTP status.

### Package `service`

#### `DocumentFileValidator.java`

`validateAndCleanFileName()`:

- Chặn file rỗng.
- Dùng `StringUtils.cleanPath()` làm sạch tên.
- Chặn `..` để tránh path traversal.
- Chỉ nhận PDF/DOC/DOCX, không phân biệt chữ hoa/thường.

#### `DocumentParserService.java`

`parseDetailed()` chọn parser theo loại file, giữ trang/bảng và trả chữ cùng các lưu ý. PDF có marker trang; DOCX giữ thứ tự đoạn và bảng, footnote/endnote. `parse()` là hàm tiện ích chỉ lấy chữ.

#### `DocumentVisionService.java`

Gửi một trang PDF hoặc ảnh Word cho Gemini, yêu cầu chép toàn bộ chữ/số/bảng và mô tả quan hệ nhìn thấy. Chữ không rõ phải đánh dấu; kết quả bị cắt hoặc rỗng làm upload báo lỗi. Kết quả AI vẫn cần đối chiếu với bản gốc.

#### `HybridRetriever.java`

Kết hợp BM25 (chuẩn hóa dấu tiếng Việt) với score vector bằng RRF. Gộp nội dung lặp trong cùng mục/tài liệu trước giới hạn ứng viên, giữ các mục/tài liệu khác nhau riêng biệt. Khi câu hỏi ghi tên file, xét ranh giới và tên đầy đủ để không khớp nhầm hậu tố. Tách các vế câu hỏi, rerank khi cần và lấy đoạn kề cùng document ID. Tài liệu ngắn dùng toàn bộ; câu tổng quan trên tài liệu dài lấy mẫu các mục và báo chưa phủ hết. Nếu rerank lỗi/định dạng sai, dùng thứ hạng trước rerank.

#### `SectionExtractor.java`

`extract()` chuẩn hóa khoảng trắng và đọc từng dòng. `isHeading()` nhận biết:

- Tiêu đề bắt đầu bằng `CHƯƠNG`, `PHẦN`, `MỤC`, `ĐIỀU`, `BÀI`, `SECTION`, `CHAPTER`.
- Tiêu đề đánh số như `1.`, `1.2`, `II.`.
- Dòng chữ in hoa đủ dài.

Nếu không nhận diện được tiêu đề, mục mặc định là `Nội dung chính`.

#### `GeminiModelProvider.java`

Quản lý ba model theo kiểu lazy singleton:

- `chatModel()`: model sinh câu trả lời.
- `documentEmbeddingModel()`: embedding tài liệu với `RETRIEVAL_DOCUMENT`.
- `queryEmbeddingModel()`: embedding câu hỏi với `RETRIEVAL_QUERY`.

`isConfigured()` chỉ cho biết key có tồn tại, không trả key. `requireApiKey()` tạo lỗi 503 nếu thiếu. `volatile` và `synchronized` đảm bảo model chỉ được tạo một lần an toàn khi có nhiều request.

#### `RagService.java`

Đây là lớp nghiệp vụ chính:

- `upload()`: validate → parse → tách mục → chunk → embedding → lưu RAM.
- `ask()`: validate → embedding câu hỏi → search → prompt → chat → nguồn.
- `listDocuments()`: trả danh sách mới nhất trước.
- `getDocument()` / `getDocumentContent()`: tìm tài liệu trong RAM và trả bản chữ để xem nguồn; 404 nếu tài liệu đã bị xóa.
- `deleteDocument()`: xóa metadata và vector theo embedding ID.
- `clearDocuments()`: xóa toàn bộ.
- `createSegments()`: tạo chunk và metadata.
- `embedDocument()`: chia batch 50 chunk để giảm kích thước request.
- `addDocumentToStore()`: ghi vector cùng metadata tài liệu trong một vùng đồng bộ và rollback khi lỗi.
- `search()`: lấy score vector toàn kho để `HybridRetriever` kết hợp thêm BM25.
- `buildPrompt()`: ghép dữ liệu bằng chứng có nhãn; quy tắc bám nguồn nằm trong SystemMessage riêng.
- `toSources()`: lấy ID tài liệu và số chunk từ metadata thật, rồi rút gọn trích đoạn. Frontend dùng hai giá trị này để tô đúng đoạn, không dò bằng tên file vốn có thể trùng.

`storeLock` bảo vệ `InMemoryEmbeddingStore` khi nhiều request upload, search hoặc delete cùng lúc. `ConcurrentHashMap` giữ danh sách tài liệu an toàn giữa các request.

## 8. Frontend

### `static/index.html`

Khung giao diện semantic gồm topbar, phần giới thiệu, cảnh báo API key, form upload, danh sách tài liệu, vùng chat, gợi ý câu hỏi, cửa sổ `<dialog>` xem nguồn và toast. SVG được viết trực tiếp nên không phụ thuộc CDN. Bạn sửa chữ/bố cục trong file này, không sửa Java khi chỉ thay nội dung giao diện.

### `static/css/nova.css`

Thiết kế toàn bộ giao diện: màu, panel, drag/drop, trạng thái, chat bubble, source card dạng nút, cửa sổ xem nguồn, đoạn đánh dấu nổi bật, loading animation và breakpoint cho tablet/điện thoại. Biến màu nằm trong `:root`; quy tắc `@media` ở cuối file chuyển bố cục từ hai cột sang một cột trên màn hình nhỏ. Có `prefers-reduced-motion` để giảm chuyển động cho người cần.

### `static/js/app.js`

- `state`: trạng thái health, tài liệu, file đang chọn và loading.
- `api()`: wrapper cho `fetch`, đọc JSON và chuẩn hóa lỗi.
- `refresh()`: tải song song health + danh sách tài liệu.
- `uploadSelectedFile()`: gửi multipart upload.
- `chooseFile()`: kiểm tra phần mở rộng và 10 MB ở trình duyệt.
- `deleteDocument()` / `clearDocuments()`: xóa dữ liệu sau khi xác nhận.
- `askQuestion()`: gửi JSON và cập nhật chat.
- `appendMessage()` / `createSourceCard()`: dựng DOM bằng `textContent`, tránh chèn HTML từ câu trả lời AI; mỗi source card là nút mở tài liệu.
- `openSource()` / `renderViewerChunks()`: gọi API bản chữ, dựng từng chunk theo thứ tự, đánh dấu nổi bật đúng `chunkIndex` và cuộn tới đó. Nếu tài liệu đã xóa thì hiện lỗi 404 thay vì nguồn sai.
- `updateControls()`: khóa/mở nút theo trạng thái app.
- `chooseFile()` xóa lựa chọn cũ khi file mới sai định dạng hoặc vượt 10 MB, tránh vô tình upload nhầm file trước đó. `refresh()` vô hiệu hóa các thao tác khi mất kết nối backend.

## 9. Kiểm thử

Ngoài các lớp dưới đây, `DocumentParserTests`, `HybridRetrieverTests` và `AnswerGroundingTests` kiểm tra đọc file thực, bằng chứng giữa dữ liệu nhiễu, đoạn kề, ngân sách và mã nguồn. `RagExamFixtureTests` sinh bộ đề 5 câu vào `target/rag-evaluation`. `LiveRagEvaluationTests` chỉ gọi Gemini khi bật cờ môi trường; xem [hướng dẫn đánh giá](RAG_QUALITY_GUIDE.md).

### `BackendApplicationTests.java`

Khởi động Spring context để phát hiện lỗi cấu hình bean, binding properties và dependency.

### `SectionExtractorTests.java`

Kiểm tra tiêu đề tiếng Việt, tiêu đề đánh số, chữ in hoa và mục mặc định.

### `DocumentFileValidatorTests.java`

Kiểm tra file hợp lệ, phần mở rộng không hỗ trợ và file rỗng.

### `SourceViewerTests.java`

Dùng model giả để kiểm tra upload → truy xuất nguồn → mở đúng tài liệu và chunk → lấy lại byte gốc → xóa rồi trả 404, không tốn Gemini API.

## 10. Những khái niệm cần hiểu khi trình bày

- **Embedding:** biểu diễn ý nghĩa văn bản bằng dãy số.
- **Chunk:** đoạn nhỏ lấy từ tài liệu để embedding và truy xuất.
- **Overlap:** phần nội dung lặp giữa hai chunk kế tiếp để giữ ngữ cảnh.
- **Vector store:** nơi lưu vector cùng văn bản/metadata.
- **Similarity score:** mức gần nhau giữa vector câu hỏi và vector đoạn tài liệu.
- **Metadata:** tên file, mục và số chunk dùng để dẫn nguồn.
- **Hallucination:** model tự tạo thông tin không có trong nguồn; prompt và ngưỡng retrieval giúp giảm hiện tượng này nhưng không bảo đảm tuyệt đối.

## 11. Khi muốn phát triển tiếp

Hướng nâng cấp hợp lý:

1. Đo chất lượng OCR/truy xuất/câu trả lời trên nhiều file thực và câu hỏi chưa dùng để chỉnh hệ thống.
2. Thay RAM bằng PostgreSQL + pgvector hoặc Qdrant để lưu bền vững.
3. Thêm đăng nhập và phân quyền tài liệu theo người dùng.
4. Đọc bảng PDF phức tạp và sơ đồ vector tốt hơn; thêm tổng hợp toàn tài liệu qua nhiều lượt với ngân sách rõ ràng.
5. Lưu lịch sử chat theo phiên.
6. Mở rộng bộ đánh giá với nguồn chuẩn, câu không có đáp án, nhiều phiên bản và dữ liệu OCR lỗi.
