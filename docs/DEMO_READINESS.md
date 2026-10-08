# Kiểm tra bản demo — 09/10/2026

## Kết quả đã đo

- Maven: **60 kiểm thử local đạt**, 2 kiểm thử Gemini thật được bỏ qua trong chế độ thường. Đóng gói JAR thành công và chạy Spring Boot thật trên cổng 8080.
- Frontend: **19 kiểm thử đạt**, gồm chọn phạm vi, lỗi upload/chat, render HTML an toàn, bảng, citation, theme và giảm chuyển động.
- Gemini thật: bộ 5 câu ban đầu trả lời đúng trước thay parser; bộ 4 câu mới sau nâng cấp trả lời đúng. Đã đọc lại đáp án, đối chiếu số, phủ định, đơn vị và nguồn, không chỉ dựa vào kiểm tra từ khóa.
- Việc chạy lại toàn bộ 5 câu trên bản nâng cấp **chưa hoàn tất**: dừng khi nhà cung cấp báo hết quota ngày. Không ghi nhận lần chạy bị dừng là đạt.
- Trình duyệt: Spring phục vụ trang thật, báo thiếu key đúng cách. Kiểm tra tương tác FE bằng API giả lập riêng để không gọi AI thêm: chọn/upload PDF, tự chọn phạm vi, bảng đáp án, mở nguồn đúng đoạn, tạo hội thoại mới, thông báo quota, desktop và điện thoại 390px. Không tràn ngang toàn trang, bảng có vùng cuộn riêng, không có lỗi JS trong các thao tác này. Xóa/dọn thư viện được kiểm tra qua API và kiểm thử logic.

## Bộ câu hỏi thực

| Bộ | Dữ liệu gây nhiễu / cấu trúc | Đáp án đã quan sát |
|---|---|---|
| Word ban đầu | Mã sản phẩm gần giống | ZX-907: 37 tháng; pin 12 tháng |
| Word ban đầu | Bảng nhiều đơn vị/năm | Sao Mai 2025: 873 triệu đồng |
| Word ban đầu | Điều kiện và nhóm đối tượng | GPA 3,4; tiếng Anh 550 cho đúng nhóm |
| Word ban đầu | Tính tăng trưởng | 120 → 150 tấn: tăng 25% |
| Word ban đầu | Phiên bản cũ và mới | Orion: 14 ngày của 2026; 10 ngày cũ hết hiệu lực |
| PDF có trang scan | Bản 2025 cũ, số 999 ở tệp khác | Bình Minh 2026: 408 triệu, **chưa** gồm VAT; nguồn trang 2 |
| Word ô gộp dọc | Tên đơn vị chỉ có ở hàng trên, số 777 gây nhiễu | An Hòa: 120 → 90 tấn, giảm 25% |
| PDF scan | Câu hỏi nhiều vế và tính tiền | 2 đợt × 50%; mỗi đợt 204 triệu |
| PDF scan | Câu không có thông tin | Nêu không có email người phụ trách; không tạo email |

Tất cả dữ liệu Gemini là tài liệu giả lập tự tạo. Không có tài liệu thật của thầy để đánh giá trước. Đáp án đúng ở các trường hợp này không bảo đảm mọi tài liệu/câu hỏi mới đều đúng.

## Phạm vi kiểm tra chức năng

| Chức năng | Kiểm tra |
|---|---|
| Upload, list, chat, source, original, replace, delete, clear, health, AI probe | Controller HTTP thật + validator/parser/index; model giả lập có kiểm soát |
| PDF chữ, PDF scan, trang có hình, tài nguyên PDF kế thừa | Parser local; PDF scan thêm Gemini Vision thật |
| PDF mật khẩu, trang trống, PDF hỏng, quá giới hạn Vision | Lỗi rõ; trang trống không gọi Vision; quá giới hạn không gọi Gemini |
| DOCX bảng, ô gộp, textbox, content control, đánh số bắt đầu 7, tracked deletion, phân số, ảnh theo thứ tự | Tệp DOCX thật tạo bằng POI/OOXML, assertion trên dữ liệu trích xuất; bảng ô gộp thêm chat thật |
| DOC cũ | Tệp DOC nhị phân Apache POI, Tika đọc chữ; tệp text đổi tên DOC bị từ chối |
| Scope và quản lý dữ liệu | Không lấy nhầm tệp; thay cùng tên loại vector cũ; lỗi embedding giữ bản cũ; clear giữa upload không tự nạp lại |
| Dẫn nguồn | Chỉ số quá lớn không làm crash; sửa citation sai tối đa một lượt; nguồn/excerpt từ metadata thật |
| Lỗi AI | Embedding câu hỏi lỗi → BM25 + cảnh báo; quota phút chờ ngắn và thử một lần; quota ngày báo ngay và không gọi lặp trong cooldown; sai key/model có hướng xử lý |
| Render câu trả lời | HTML/JavaScript trong nội dung được hiển thị như chữ; bảng, đánh số thật, citation đúng; không mở link AI tự tạo |

## Quota là giới hạn cần chuẩn bị trước buổi chấm

Trong phiên thử thật, API báo hạn mức của tài khoản/model đang dùng: **5 lượt tạo nội dung/phút, 20 lượt/ngày**. Quota ngày đã hết ở lượt chạy lại. Đây là thông tin từ phản hồi API trong phiên này, không phải hạn mức cố định cho mọi tài khoản.

- Mỗi trang scan/ảnh cần Vision dùng một lượt tạo nội dung; mỗi đáp án dùng một lượt. Tài liệu dài có thể dùng thêm rerank; sửa citation sai có thể thêm một lượt. Embedding có hạn mức riêng.
- **Kiểm tra AI** dùng một lượt chat và một lượt embedding: bấm một lần trước demo khi quota sẵn sàng, không bấm liên tục.
- Quota theo phút có thể thử lại sau thời gian chờ. Hết quota ngày thì chờ làm mới theo Google AI Studio hoặc dùng model/tài khoản có đủ hạn mức và quyền phù hợp. Không đổi API key/model mù, không đưa key lên GitHub.
- Nếu cần tiết kiệm lượt, có thể đặt `RAG_RERANK_ENABLED=false` trong IntelliJ; vector + BM25 vẫn hoạt động nhưng mất bước AI xếp hạng lại ở tài liệu dài. Mặc định vẫn bật để giữ khả năng tìm đúng bằng chứng.
- Bản scan nhiều trang có thể cần nhiều hơn 20 lượt. Cần kiểm tra quota thực trước buổi chấm; chia file không tạo thêm quota.

## Chạy demo

1. Chạy `BackendApplication` trong IntelliJ với Java 21 và `GEMINI_API_KEY` đã cấu hình. Đợi dòng **Tomcat started on port 8080**.
2. Mở **http://localhost:8080**. Refresh bằng Ctrl+F5 nếu vừa đổi phiên bản giao diện.
3. Khi còn quota, bấm **Kiểm tra AI** một lần. “Sẵn sàng” trước kiểm tra chỉ phản ánh backend/key đã cấu hình.
4. Tải tệp thầy đưa, đợi đọc/index xong. Đọc lưu ý trong thư viện; chọn đúng **Tài liệu để hỏi**. Muốn so sánh nhiều tệp, chọn **Toàn bộ thư viện**.
5. Ghi đầy đủ đối tượng, năm/phiên bản và tất cả các vế của câu thầy hỏi. Mỗi câu được xử lý độc lập: không chỉ hỏi “còn nó thì sao?” mà thiếu tên/đối tượng.
6. Bấm `[Nguồn n]` và mở bản gốc khi cần đối chiếu số/bảng. Với Word không tạo số trang giả; số trang phụ thuộc cách Word render.

## Giới hạn còn lại

Tệp tối đa 10 MB; PDF tối đa 250 trang; tối đa 40 trang/ảnh Vision mỗi tệp; tối đa 800 đoạn. Dữ liệu/vector nằm trong RAM và mất khi dừng app. PDF bảng vector nhiều cột nên thử đọc kỹ khi còn đủ quota; OCR chữ mờ/công thức/SmartArt/biểu đồ và DOC hình phức tạp vẫn cần đối chiếu hoặc xuất DOCX/PDF rõ hơn. Tổng quan tài liệu dài có cảnh báo độ phủ. Hiện chưa có bộ nhớ hội thoại cho câu hỏi nối tiếp và chưa có kiểm chứng tự động bảo đảm mọi khẳng định đều đúng.

## Chạy lại kiểm thử

```powershell
cd backend
.\mvnw.cmd --batch-mode test package
node --test src/test/js/*.test.cjs
```

Kiểm thử thật chỉ chạy khi chủ động bật `RUN_LIVE_RAG_EVAL=true` và có key trong môi trường; dùng quota, không chạy trong CI. Bộ đầu: `LiveRagEvaluationTests`; bộ scan/ô gộp: `LiveAdvancedEvaluationTests`. Báo cáo và tài liệu thử xuất trong `backend/target/rag-evaluation` (đã gitignore). Xem [Google rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) và [lịch model](https://ai.google.dev/gemini-api/docs/deprecations) khi chuẩn bị tài khoản.
