package com.bank.transaction.feign;

import com.bank.transaction.exception.AccountNotFoundException;
import com.bank.transaction.exception.InsufficientFundsException;
import com.bank.transaction.exception.AccountServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("[FeignErrorDecoder] Error response from {}: status={}", methodKey, response.status());

        return switch (response.status()) {
            case 400 -> new AccountServiceException("Bad request to account service: " + methodKey);
            case 404 -> new AccountNotFoundException("Account not found via: " + methodKey);
            case 409 -> new InsufficientFundsException("Insufficient funds or conflict: " + methodKey);
            case 422 -> new InsufficientFundsException("Unprocessable entity - insufficient funds: " + methodKey);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
