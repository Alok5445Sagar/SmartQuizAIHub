package com.smartquiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class AuthDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Enterprise ID is required") @Size(max = 100)
        private String enterpriseId;
        @NotBlank(message = "Password is required") @Size(max = 200)
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLanguageRequest {
        @Size(max = 10)
        private String preferredLanguage;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoginResponse {
        private String token;
        @NotBlank(message = "Enterprise ID is required") @Size(max = 100)
        private String enterpriseId;
        private String fullName;
        private String role;
        @Size(max = 10)
        private String preferredLanguage;
    }
}
