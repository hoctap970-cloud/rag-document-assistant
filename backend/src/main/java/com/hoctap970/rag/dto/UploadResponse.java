package com.hoctap970.rag.dto;

public record UploadResponse(
        String fileName,
        String contentType,
        long size,
        String message
) {
}
// record: là kiểu class gọn của java dùng để chứađữ liệu. Ở đây nó giữ tên file, loại file, kích thước và thông báo
