package com.bank.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsResult {

    private boolean success;
    private String messageId;
    private String phoneNumber;
    private String message;
    private LocalDateTime sentAt;
    private String errorCode;
    private String errorMessage;

    public static SmsResult success(String messageId, String phoneNumber, String message) {
        return SmsResult.builder()
            .success(true)
            .messageId(messageId)
            .phoneNumber(phoneNumber)
            .message(message)
            .sentAt(LocalDateTime.now())
            .build();
    }

    public static SmsResult failure(String phoneNumber, String errorCode, String errorMessage) {
        return SmsResult.builder()
            .success(false)
            .phoneNumber(phoneNumber)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .sentAt(LocalDateTime.now())
            .build();
    }
}
