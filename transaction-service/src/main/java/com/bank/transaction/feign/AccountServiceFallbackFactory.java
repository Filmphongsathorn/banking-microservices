package com.bank.transaction.feign;

import com.bank.transaction.dto.AccountBalanceResponse;
import com.bank.transaction.dto.AccountBalanceUpdateRequest;
import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.exception.AccountServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class AccountServiceFallbackFactory implements FallbackFactory<AccountServiceClient> {

    @Override
    public AccountServiceClient create(Throwable cause) {
        log.error("[AccountServiceFallback] Account service is unavailable: {}", cause.getMessage());
        return new AccountServiceClient() {

            @Override
            public ApiResponse<AccountBalanceResponse> debitAccount(String accountId, AccountBalanceUpdateRequest request) {
                log.error("[AccountServiceFallback] debitAccount fallback triggered for accountId={}", accountId);
                throw new AccountServiceUnavailableException(
                    "Account service is unavailable for debit operation: " + cause.getMessage(), cause
                );
            }

            @Override
            public ApiResponse<AccountBalanceResponse> creditAccount(String accountId, AccountBalanceUpdateRequest request) {
                log.error("[AccountServiceFallback] creditAccount fallback triggered for accountId={}", accountId);
                throw new AccountServiceUnavailableException(
                    "Account service is unavailable for credit operation: " + cause.getMessage(), cause
                );
            }

            @Override
            public ApiResponse<AccountBalanceResponse> getAccount(String accountId) {
                log.error("[AccountServiceFallback] getAccount fallback triggered for accountId={}", accountId);
                throw new AccountServiceUnavailableException(
                    "Account service is unavailable: " + cause.getMessage(), cause
                );
            }
        };
    }
}
