package com.hoctap970.rag.service;

import java.util.Locale;

/** Public recovery advice only; provider response bodies and credentials stay private. */
public final class AiFailureMessages {
    private AiFailureMessages() {}

    public static String describe(Throwable failure, String operation) {
        String details = details(failure);
        if (dailyQuota(details)) return operation + " Gemini đã hết quota ngày. Chờ quota ngày được làm mới hoặc kiểm tra hạn mức trong Google AI Studio; chờ 60 giây không giải quyết giới hạn này.";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String detail = (cause.getClass().getSimpleName() + " " + cause.getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("429") || detail.contains("ratelimit") || detail.contains("resource_exhausted")) {
                return operation + " Gemini đang giới hạn quota. Chờ ít nhất 60 giây rồi thử lại; nếu vẫn lỗi, kiểm tra quota trong Google AI Studio.";
            }
            if (detail.contains("401") || detail.contains("403") || detail.contains("api_key_invalid") || detail.contains("permission_denied")) {
                return operation + " API key không hợp lệ hoặc thiếu quyền. Kiểm tra GEMINI_API_KEY trong IntelliJ rồi chạy lại ứng dụng.";
            }
            if (detail.contains("404") || detail.contains("not_found")) {
                return operation + " Model không khả dụng với tài khoản. Kiểm tra tên model và quyền truy cập trong Google AI Studio.";
            }
            if (detail.contains("timeout") || detail.contains("timed out")) {
                return operation + " Kết nối AI quá thời gian chờ. Kiểm tra mạng rồi thử lại.";
            }
        }
        return operation + " Kiểm tra kết nối mạng, API key và trạng thái Gemini rồi thử lại.";
    }

    static String details(Throwable failure) {
        StringBuilder value = new StringBuilder();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            value.append(cause.getClass().getSimpleName()).append(' ').append(cause.getMessage()).append(' ');
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }
    static boolean dailyQuota(String details) {
        return details.contains("requestsperday") || details.contains("requests_per_day") || details.contains("perdayperproject")
                || details.contains("daily quota") || details.contains("quota ngày");
    }
}
