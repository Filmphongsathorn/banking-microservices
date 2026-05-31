package com.bank.transaction.feign;

import com.bank.transaction.dto.AccountBalanceResponse;
import com.bank.transaction.dto.AccountBalanceUpdateRequest;
import com.bank.transaction.dto.ApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
    name = "account-service",
    fallbackFactory = AccountServiceFallbackFactory.class
)
public interface AccountServiceClient {

    /**
     * หักเงินจากบัญชี (Debit)
     */
    @PostMapping("/api/v1/accounts/{accountId}/withdraw")
    @CircuitBreaker(name = "accountService")
    ApiResponse<AccountBalanceResponse> debitAccount(
        @PathVariable("accountId") String accountId,
        @RequestBody AccountBalanceUpdateRequest request
    );

    /**
     * เพิ่มเงินเข้าบัญชี (Credit)
     */
    @PostMapping("/api/v1/accounts/{accountId}/deposit")
    @CircuitBreaker(name = "accountService")
    ApiResponse<AccountBalanceResponse> creditAccount(
        @PathVariable("accountId") String accountId,
        @RequestBody AccountBalanceUpdateRequest request
    );

    /**
     * ดึงข้อมูลบัญชี
     */
    @GetMapping("/api/v1/accounts/{accountId}")
    ApiResponse<AccountBalanceResponse> getAccount(
        @PathVariable("accountId") String accountId
    );
}
