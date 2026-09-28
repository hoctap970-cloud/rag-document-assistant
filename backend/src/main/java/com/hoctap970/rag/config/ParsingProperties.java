package com.hoctap970.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.parsing")
public record ParsingProperties(
        @DefaultValue("true") boolean visionEnabled,
        @DefaultValue("250") int maxPdfPages,
        @DefaultValue("40") int maxVisionPages
) {
    public ParsingProperties {
        if (maxPdfPages < 1 || maxVisionPages < 1) {
            throw new IllegalArgumentException("Giới hạn trang phải lớn hơn 0");
        }
    }
}
