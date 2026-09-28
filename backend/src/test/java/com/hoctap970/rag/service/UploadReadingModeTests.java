package com.hoctap970.rag.service;

import com.hoctap970.rag.controller.DocumentController;
import com.hoctap970.rag.controller.GlobalExceptionHandler;
import com.hoctap970.rag.domain.ReadingMode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadReadingModeTests {
    @Test
    void multipartUploadDefaultsToAutoAndForwardsExplicitDeepMode() throws Exception {
        var service = mock(RagService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var file = new MockMultipartFile("file", "sample.pdf", "application/pdf", new byte[]{1});
        mvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());
        verify(service).upload(file, ReadingMode.AUTO);
        mvc.perform(multipart("/api/documents/upload").file(file).param("readMode", "deep"))
                .andExpect(status().isOk());
        verify(service).upload(file, ReadingMode.DEEP);
    }

    @Test
    void invalidReadingModeReturns400WithoutCallingUpload() throws Exception {
        var service = mock(RagService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "sample.pdf", "application/pdf", new byte[]{1}))
                        .param("readMode", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chế độ đọc không hợp lệ. Chọn AUTO hoặc DEEP."));
        verifyNoInteractions(service);
    }
}
