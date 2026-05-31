package com.banking.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

public class ProfileDto {

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateProfileRequest {

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        private String lastName;

        @NotBlank(message = "Address is required")
        @Size(max = 500)
        private String address;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Invalid phone number format")
        private String phoneNumber;

        @Size(max = 100)
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateProfileRequest {

        @Size(max = 100)
        private String firstName;

        @Size(max = 100)
        private String lastName;

        @Size(max = 500)
        private String address;

        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Invalid phone number format")
        private String phoneNumber;

        @Size(max = 100)
        private String email;
    }

    // ─── Response DTOs ─────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProfileResponse {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
        private String address;
        private String phoneNumber;
        private String email;
        private String createdAt;
        private String updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private long timestamp;
    }
}
