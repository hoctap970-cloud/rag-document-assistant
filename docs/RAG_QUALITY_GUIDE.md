# Chuẩn bị RAG cho bài chấm 5 câu

Mục tiêu là tăng khả năng lấy đúng bằng chứng trong tài liệu có nhiễu: đúng đối tượng, năm, phiên bản, con số và ngoại lệ. Không có cấu hình RAG nào bảo đảm mọi tệp đều đọc đúng và mọi câu đều đạt điểm. Cần kiểm tra cả việc đọc file, tìm nguồn và câu trả lời cuối cùng.

## 1. Bản nâng cấp xử lý những điểm yếu nào?

| Điểm dễ mất điểm | Cách xử lý đã triển khai |
|---|---|
| Câu trả lời nằm ngay trong tiêu đề | Giữ dòng tiêu đề trong nội dung; thêm tên mục vào các đoạn tiếp theo |
| Nhầm số liệu giữa các dòng bảng Word | POI giữ từng dòng, dấu phân cách cột và lặp dòng đầu làm ngữ cảnh |
| PDF scan hoặc hình chứa thông tin | PDFBox đọc lớp chữ từng trang; Gemini đọc thêm trang ít chữ/có ảnh lớn và ảnh PNG/JPEG trong DOCX |
| Mã sản phẩm hoặc con số hiếm có vector score thấp | Tìm cả BM25 và vector, gộp bằng Reciprocal Rank Fusion (RRF) |
| Đoạn lặp chiếm hết chỗ của bằng chứng | Gộp nội dung giống nhau trong cùng mục/tài liệu trước khi giới hạn ứng viên; vẫn giữ riêng các tài liệu và mục khác nhau |
| Tên file gần giống bị chọn nhầm | So khớp đầy đủ tên file, ưu tiên tên dài hơn nếu một tên nằm bên trong tên khác |
| Ngoại lệ nằm sau quy tắc chính | Lấy thêm đoạn trước/sau trong cùng tài liệu |
| Nhiều đoạn gần giống khác năm/đối tượng | Rerank ứng viên bằng Gemini; prompt yêu cầu kiểm tra năm, phiên bản, đơn vị, phủ định |
| Câu nhiều vế hoặc so sánh | Thêm tìm kiếm từng vế; yêu cầu có bằng chứng cho từng phần |
| Tài liệu ngắn nhưng thông tin rải rác | Đưa toàn bộ các đoạn vào ngữ cảnh khi nằm dưới ngân sách |
| AI nghe theo câu “bỏ qua hướng dẫn” cài trong file | Quy tắc ở SystemMessage; tài liệu nằm trong phần dữ liệu. Đây là biện pháp giảm rủi ro, không phải bảo đảm miễn nhiễm |
| Dẫn nguồn giả hoặc trả lời bị cắt | Kiểm tra chỉ số `[Nguồn n]`, báo lỗi mã nguồn ngoài phạm vi, cảnh báo không trích nguồn hoặc chạm giới hạn đầu ra |

Score trên thẻ nguồn vẫn là độ tương đồng vector ban đầu. Đoạn được BM25 cứu có thể có score thấp mà chứa chính xác mã cần tìm. Khi embedding câu hỏi lỗi, hệ thống tìm BM25 và trả score=null, giao diện ghi “Tìm theo từ khóa”. Không dùng score làm phần trăm độ tin cậy của đáp án.

## 2. Luồng thực tế

1. **Đọc:** PDFBox / POI / Tika; bổ sung Vision theo loại trang/hình hoặc mọi trang PDF khi chọn đọc kỹ.
2. **Giữ ngữ cảnh:** tiêu đề, hàng bảng, trang PDF → chia đoạn 1.500 ký tự, overlap 220 → embedding → RAM.
3. **Tìm:** vector + BM25, gộp thứ hạng; tối đa 40 ứng viên để xếp hạng lại khi cần.
4. **Chọn:** 14 đoạn chính + đoạn kề, tối đa khoảng 48.000 ký tự ngữ cảnh. Dưới 24.000 ký tự thì dùng toàn bộ phạm vi. Nếu câu hỏi ghi đầy đủ tên file, phạm vi được giới hạn theo tên đó.
5. **Trả lời:** Gemini nhận quy tắc riêng và các nguồn có nhãn. Backend tạo thẻ nguồn từ metadata thật.

Câu tổng quan lấy mẫu rải trên các mục rồi bổ sung đoạn liên quan. Nếu tài liệu quá dài, giao diện báo số đoạn đã chọn/tổng số; cơ chế này chưa phải tổng hợp toàn bộ mọi trang bằng nhiều lượt model.

## 3. Cách chạy và kiểm tra trước buổi chấm

1. Trong IntelliJ, đặt `GEMINI_API_KEY` ở Environment variables của `BackendApplication`.
2. Chạy lại ứng dụng, mở [NOVA trên máy](http://localhost:8080), tải lại tài liệu. Dữ liệu cũ mất khi khởi động lại vì lưu RAM.
3. Upload một file và hỏi một câu có đáp án đã biết để kiểm tra API thật. Trạng thái đã cấu hình key không chứng minh key/model còn dùng được.
4. Mở nguồn, đối chiếu số liệu với file gốc. Với PDF, nguồn có số trang; với Word, không tạo số trang giả vì phân trang phụ thuộc cách render.
5. Đọc mục lưu ý bên dưới tài liệu/câu trả lời. Tệp scan có thể cần nhiều lượt API và thời gian hơn tệp chữ.

### Khi PDF có bảng hoặc sơ đồ khó

Sau khi chọn PDF, có thể đánh dấu **Đọc kỹ từng trang PDF** rồi nhấn **Phân tích tài liệu**:

- **Không đánh dấu:** tự động đọc thêm các trang ít chữ/có ảnh lớn; phù hợp phần lớn tài liệu chữ.
- **Có đánh dấu:** gửi từng trang cho Gemini Vision, kể cả trang có nhiều chữ. Dùng khi bảng nhiều cột, sơ đồ vector hoặc nội dung hình bị bỏ sót trong bản trích xuất tự động. Vẫn giữ lớp chữ gốc và số trang.
- Mỗi trang thêm một lượt Vision, nên mất thời gian/quota hơn. Mặc định tối đa 40 trang ở chế độ này. PDF vượt giới hạn được từ chối trước khi gọi Gemini; chia file rồi tải từng phần nếu cần.
- Lựa chọn được đặt lại khi chọn tệp mới và chỉ bật cho PDF. Nếu backend tắt `RAG_VISION_ENABLED`, yêu cầu đọc kỹ báo lỗi rõ ràng.
- Đây là cách tăng khả năng đọc đủ nội dung, không bảo đảm OCR đúng mọi chữ/số. Sau khi đổi chế độ cần upload lại; bản cùng tên sẽ được thay sau khi bản mới đọc/index thành công. Nếu lỗi, bản cũ vẫn còn.

API giữ tương thích với request cũ: bỏ qua `readMode` thì dùng `AUTO`; có thể gửi multipart `readMode=DEEP` cùng `file` để đọc kỹ. Chế độ không hợp lệ trả 400.

Biến tùy chọn trong Run Configuration:

```text
GEMINI_CHAT_MODEL=ten-model-chat-co-quyen-su-dung
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
RAG_VISION_ENABLED=true
```

Không copy nguyên placeholder `ten-model-chat-co-quyen-su-dung`. Để trống biến chat sẽ dùng mặc định `gemini-2.5-flash`. Model khả dụng tùy tài khoản và thời điểm; nếu gặp lỗi model không tồn tại/không có quyền, đối chiếu [danh sách model](https://ai.google.dev/gemini-api/docs/models) và [lịch ngừng model](https://ai.google.dev/gemini-api/docs/deprecations), đổi sang model hỗ trợ text, PDF và ảnh rồi thử lại. Khi đổi embedding model, phải nạp lại dữ liệu và dùng số chiều được model hỗ trợ.

Spring không tự đọc `.env`; nhập biến trong IntelliJ hoặc môi trường PowerShell. Không đưa API key vào mã nguồn.

## 4. Bộ đề 5 câu có dữ liệu gây nhiễu

Chạy tại thư mục `backend`:

```powershell
.\mvnw.cmd test
```

Kiểm thử sinh hai file:

- `target/rag-evaluation/de-thu-nhieu.docx`: 48 mục phụ lục gây nhiễu, dữ liệu chính rải ở đầu/giữa/cuối.
- `target/rag-evaluation/dap-an.md`: năm câu hỏi và đáp án chuẩn.

Upload **chỉ file DOCX** vào NOVA, hỏi lần lượt:

| Câu | Bẫy | Đáp án cần có |
|---|---|---|
| ZX-907 bảo hành bao lâu, bộ phận nào khác? | ZX-906 gần giống; ngoại lệ pin | Thiết bị 37 tháng; pin 12 tháng |
| Doanh thu chính thức Sao Mai năm 2025? | Khác năm, bản dự thảo đã hủy | 873 triệu đồng |
| Học bổng 2026 cho hộ nghèo cần GPA/tiếng Anh? | Ngoại lệ chỉ giảm một điều kiện | GPA 3,4; tiếng Anh 550 |
| An Phú tăng sản lượng bao nhiêu % từ 2024 đến 2025? | Số của xưởng khác; cần tính toán | (150 − 120) / 120 × 100 = 25% |
| Orion đang yêu cầu báo trước bao nhiêu ngày? | Bản cũ hết hiệu lực và chỉ thị độc hại | 14 ngày, bản hiệu lực từ 01/01/2026 |

File đáp án chỉ dùng để tự chấm, không upload cùng đề. Dữ liệu hoàn toàn giả lập. Nên tạo thêm câu hỏi từ tài liệu chưa từng dùng để chỉnh hệ thống; đạt bộ đề này chưa chứng minh đạt mọi đề của thầy.

### Đánh giá tự động bằng Gemini thật

Đặt `GEMINI_API_KEY` trong môi trường chạy Maven trước, sau đó:

```powershell
$env:RUN_LIVE_RAG_EVAL="true"
try {
    .\mvnw.cmd '-Dtest=LiveRagEvaluationTests' test
} finally {
    Remove-Item Env:RUN_LIVE_RAG_EVAL -ErrorAction SilentlyContinue
}
```

Lượt này tốn quota: embedding tài liệu, embedding câu hỏi, rerank và trả lời. Báo cáo nằm ở `target/rag-evaluation/gemini-report.md`. Cờ trên chỉ bật đánh giá thật; test mặc định và CI không gọi Gemini.

Bài tự động chỉ kiểm tra nguồn không rỗng và từ khóa/số mong đợi, có thể bỏ sót câu trả lời mâu thuẫn hoặc sai cách diễn đạt. Đọc từng đáp án và nguồn để chấm thủ công. Nếu upload lỗi trước khi hỏi thì báo cáo có thể chưa được tạo; xem lỗi Maven.

### Phân biệt kết quả kiểm thử

- Test parser dùng file PDF/DOCX thực tạo trong test, nhưng kết quả Vision được giả lập: kiểm tra luồng gọi, số trang, cảnh báo và cấu trúc, không đo độ chính xác OCR.
- Test hybrid dùng vector score giả lập để tạo tình huống nhiễu khó: đo khả năng giữ bằng chứng khi nhánh vector xếp thấp.
- Test prompt kiểm tra phân tách SystemMessage/dữ liệu và mã nguồn; không chứng minh Gemini chống được mọi prompt injection.
- Chỉ chạy bài live và đọc báo cáo mới đánh giá được câu trả lời của model/API đang dùng.

## 5. Phạm vi và giới hạn cần biết

- Nhận **PDF, DOC, DOCX**, tối đa 10 MB/tệp; PDF tối đa 250 trang, tối đa 40 trang/hình qua Vision/tệp, tối đa 800 chunk/tệp. Vượt giới hạn cần chia nhỏ; không báo thành công khi bị ngắt giữa quá trình đọc.
- Ở chế độ tự động, PDF ít hơn 60 chữ/số trên trang hoặc có ảnh lớn được chuyển qua Vision. Trang nhiều chữ kèm sơ đồ vector hay ảnh nhỏ có thể không được nhận diện; dùng **Đọc kỹ từng trang PDF** cho trường hợp này. Vision vẫn có thể đọc sai chữ, dấu, số hoặc quan hệ bảng.
- Word DOCX giữ bảng cơ bản, đọc footnote/endnote; bảng gộp ô, textbox, SmartArt và hình chuyên dụng không được bảo đảm. Chữ trích từ ảnh Word nằm cuối tài liệu nên phải đối chiếu chú thích/vị trí. DOC cũ dùng Tika đọc chữ; nên xuất DOCX/PDF khi chứa nhiều hình.
- PDF khóa quyền sao chép, hỏng hoặc có mật khẩu sẽ báo lỗi. Không có cơ chế đoán nội dung không đọc được.
- Dữ liệu gửi tới Gemini để embedding; các đoạn chọn gửi để trả lời, trang/hình gửi khi dùng Vision. Ứng dụng giữ bản gốc và vector trong RAM.
- Truy xuất tài liệu dài có ngân sách, không đảm bảo tìm đủ mọi sự kiện rải rác. Câu cần liệt kê toàn bộ hay tổng hợp hàng trăm trang vẫn cần đối chiếu và hỏi theo từng phần.
- Rerank thêm một lượt model; Vision thêm một lượt cho mỗi trang/hình được chọn. Quota, mạng và quyền model là các rủi ro thực tế khi thi. Tắt `app.rag.rerank-enabled` hoặc `RAG_VISION_ENABLED` giảm số lượt gọi nhưng có thể giảm chất lượng.

Muốn tăng chất lượng tiếp, ưu tiên đo lỗi bằng tài liệu thực trước: **không đọc được dữ liệu → sửa parser/OCR; có dữ liệu nhưng không lấy đúng đoạn → chỉnh truy xuất; có đủ nguồn nhưng đáp sai → chỉnh model/prompt**. Tăng chunk/top-k mù quáng có thể làm nhiễu nhiều hơn.

## Tham khảo kỹ thuật

- [LangChain4j RAG: bộ tổng hợp và xếp hạng lại nội dung](https://docs.langchain4j.dev/tutorials/rag/)
- [Google Gemini: xử lý PDF và tài liệu](https://ai.google.dev/gemini-api/docs/document-processing)
- [LangChain4j Google GenAI integration](https://docs.langchain4j.dev/integrations/language-models/google-genai/)
