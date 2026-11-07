package com.smartquiz.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GlobalExceptionHandler SSE frames")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("SSE error frame uses real newlines and keeps the payload on one line")
    void sseErrorFrame_isWellFormed() {
        String frame = GlobalExceptionHandler.formatSseError("line one\nline two");
        assertThat(frame).isEqualTo("event: error\ndata: line one line two\n\n");
        assertThat(frame).doesNotContain("\\n");
    }

    @Test
    @DisplayName("SSE error frame falls back to a generic message")
    void sseErrorFrame_blankMessage() {
        assertThat(GlobalExceptionHandler.formatSseError(" ")).contains(GlobalExceptionHandler.GENERIC_ERROR);
    }
}
