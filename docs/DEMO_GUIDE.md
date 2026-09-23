# Kịch bản demo đồ án

Mục tiêu của kịch bản là chứng minh đủ ba phần: upload/indexing, hỏi đáp RAG và dẫn nguồn. Thực hành trước bằng đúng máy và đúng tài liệu sẽ dùng trên lớp.

## 1. Chuẩn bị trước ngày demo

- Clone/pull bản mới nhất từ repository cá nhân.
- Kiểm tra JDK 21 và Internet.
- Tạo Gemini API key riêng.
- Cấu hình `GEMINI_API_KEY` trong IntelliJ Run Configuration.
- Chọn một tài liệu PDF hoặc DOCX có tiêu đề/mục rõ, dung lượng nhỏ hơn 10 MB.
- Chuẩn bị 5 câu hỏi có đáp án nằm ở các phần khác nhau trong tài liệu.
- Chuẩn bị thêm 1 câu hỏi cố ý không có đáp án để chứng minh hệ thống biết từ chối.
- Không chiếu API key lên máy chiếu.

Chạy kiểm thử trước:

```powershell
cd backend
.\mvnw.cmd clean test
```

## 2. Trình tự demo khoảng 7–10 phút

### Phần A — Giới thiệu (khoảng 1 phút)

Có thể nói:

> Đây là ứng dụng hỏi đáp tài liệu bằng RAG. Backend viết bằng Spring Boot. Khi upload PDF hoặc Word, hệ thống trích chữ, chia đoạn, tạo embedding bằng Gemini và lưu vector trong RAM. Khi hỏi, hệ thống tìm tối đa 5 đoạn liên quan rồi mới đưa ngữ cảnh cho Gemini. Nguồn hiển thị phía dưới được backend lấy từ metadata của đoạn truy xuất.

### Phần B — Kiểm tra hệ thống (30 giây)

1. Chạy `BackendApplication` trong IntelliJ.
2. Chờ `Started BackendApplication`.
3. Mở `http://localhost:8080`.
4. Chỉ vào trạng thái **Backend và Gemini sẵn sàng**.

### Phần C — Upload và indexing (1–2 phút)

1. Kéo thả tài liệu.
2. Nhấn **Đọc và tạo vector**.
3. Chỉ vào thông báo số vector.
4. Giải thích cột trái cho biết số chunk đang nằm trong RAM.
5. Nói rõ file gốc được giữ trong RAM để xem lại, không được ghi xuống ổ đĩa và dữ liệu mất khi dừng app.

### Phần D — Năm câu hỏi chấm điểm (4–5 phút)

Thay phần trong ngoặc vuông bằng nội dung thật của tài liệu:

1. **Câu tổng quan:** “Tài liệu này trình bày chủ đề chính nào?”
2. **Câu định nghĩa:** “Theo tài liệu, [khái niệm quan trọng] được định nghĩa như thế nào?”
3. **Câu quy trình:** “Quy trình [tên quy trình] gồm những bước nào?”
4. **Câu so sánh/lý do:** “Tài liệu nêu những ưu điểm và hạn chế nào của [chủ đề]?”
5. **Câu chi tiết:** “Ở phần [tên mục], tác giả kết luận hoặc đề xuất điều gì?”

Sau mỗi câu:

- Chỉ vào `[Nguồn n]` trong câu trả lời.
- Chỉ vào tên file và tên mục trong source card.
- Bấm source card: bản chữ tài liệu mở ra, tự cuộn đến đoạn tô vàng. Chỉ vào đoạn đó để chứng minh câu trả lời có căn cứ.
- Có thể bấm **Mở tệp gốc** để đối chiếu bố cục PDF/Word; bản chữ trong app không giữ nguyên định dạng.
- Nếu score thấp bất thường, đặt lại câu hỏi dùng từ gần với tài liệu hơn.

### Phần E — Chứng minh giảm bịa thông tin (30 giây)

Hỏi một câu chắc chắn không có trong tài liệu, ví dụ:

> Tài liệu cho biết giá Bitcoin ngày hôm nay là bao nhiêu?

Kết quả mong đợi: hệ thống nói không tìm thấy thông tin đủ liên quan hoặc tài liệu không cung cấp đủ thông tin.

### Phần F — Kết thúc (30 giây)

1. Xóa một tài liệu để chứng minh vector liên quan cũng bị xóa.
2. Mở nhanh repository GitHub.
3. Chỉ vào README, GitHub Actions và lịch sử Pull Request/commit.

## 3. Câu hỏi thầy có thể hỏi

### “RAG khác hỏi Gemini trực tiếp ở đâu?”

Gemini trực tiếp chỉ nhận câu hỏi và kiến thức sẵn có của model. RAG tìm ngữ cảnh trong tài liệu của người dùng trước, rồi đưa đúng ngữ cảnh đó vào prompt. Vì vậy câu trả lời bám tài liệu và có nguồn kiểm chứng.

### “Vector lưu ở đâu?”

Trong `InMemoryEmbeddingStore<TextSegment>` của LangChain4j. Mỗi vector đi kèm chunk và metadata. Đây là lưu trong RAM đúng yêu cầu; restart ứng dụng thì dữ liệu mất.

### “Vì sao cần chia chunk?”

Embedding cả tài liệu dài làm mất chi tiết và vượt giới hạn model. Chunk nhỏ giúp tìm đúng phần liên quan. Overlap 120 giữ câu/ý ở ranh giới hai chunk.

### “Nguồn section lấy ở đâu?”

`SectionExtractor` đọc text do Tika trả về và nhận diện dòng dạng `CHƯƠNG`, `MỤC`, `1.2`, chữ in hoa. Tên mục được lưu vào metadata của từng chunk.

### “Làm sao hạn chế AI bịa?”

Prompt bắt buộc chỉ dùng ngữ cảnh, retrieval có score tối thiểu, và nếu không có chunk phù hợp thì không gọi model. Nguồn API được tạo từ kết quả search, không dựa vào model tự khai báo.

### “API key có nằm trên GitHub không?”

Không. Spring đọc `GEMINI_API_KEY` từ biến môi trường. `.env` bị Git ignore, frontend không nhận key.

### “Nếu triển khai thật thì nâng cấp gì?”

Đổi RAM sang pgvector/Qdrant, thêm OCR, authentication, giới hạn người dùng, lưu lịch sử và bộ đánh giá độ chính xác.

## 4. Xử lý sự cố khi demo

| Hiện tượng | Cách xử lý |
|---|---|
| Banner báo thiếu key | Dừng app, thêm `GEMINI_API_KEY` vào Run Configuration, chạy lại |
| Port 8080 đang dùng | Dừng app cũ hoặc thêm `server.port=8081`, mở port mới |
| Upload báo 502 | Kiểm tra Internet, API key và quota Gemini |
| PDF báo không trích xuất được chữ | PDF có thể là bản scan hoặc bản in với chữ đã chuyển thành hình/nét vẽ; dùng bản có thể chọn/copy chữ, DOCX hoặc OCR trước khi tải lên |
| Không có nguồn phù hợp | Hỏi bằng từ khóa gần tài liệu hoặc hạ `app.rag.min-score` một chút |
| App restart mất tài liệu | Upload lại; đây là hành vi đúng của vector store trong RAM |
| Bấm nguồn báo không tìm thấy tài liệu | Tài liệu đã bị xóa hoặc app vừa khởi động lại; upload lại rồi hỏi lại |
| Build lần đầu chậm | Maven đang tải dependency; chuẩn bị trước khi lên lớp |

## 5. Checklist cuối cùng

- [ ] `mvnw.cmd clean test` thành công.
- [ ] API key có hiệu lực và không xuất hiện trong Git.
- [ ] Upload đúng tài liệu demo.
- [ ] Cả 5 câu hỏi đã thử trước.
- [ ] Có một câu ngoài tài liệu để kiểm tra từ chối.
- [ ] Repository GitHub ở trạng thái public nếu thầy cần clone.
- [ ] GitHub Actions màu xanh.
- [ ] README hiển thị đúng sơ đồ và hướng dẫn.
- [ ] Nhánh `main` chứa phiên bản demo ổn định.
