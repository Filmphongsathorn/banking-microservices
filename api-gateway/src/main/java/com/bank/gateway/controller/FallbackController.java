package com.banking.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping(value = "/auth", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        log.warn("Circuit breaker triggered for auth-service");
        return buildFallbackResponse("auth-service");
    }

    @RequestMapping(value = "/profile", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> profileFallback() {
        log.warn("Circuit breaker triggered for profile-service");
        return buildFallbackResponse("profile-service");
    }

    @RequestMapping(value = "/account", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> accountFallback() {
        log.warn("Circuit breaker triggered for account-service");
        return buildFallbackResponse("account-service");
    }

    @RequestMapping(value = "/transaction", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> transactionFallback() {
        log.warn("Circuit breaker triggered for transaction-service");
        return buildFallbackResponse("transaction-service");
    }

    @RequestMapping(value = "/notification", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        log.warn("Circuit breaker triggered for notification-service");
        return buildFallbackResponse("notification-service");
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(String serviceName) {
        Map<String, Object> body = Map.of(
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "message", String.format("The %s is temporarily unavailable. Please try again later.", serviceName),
            "timestamp", LocalDateTime.now().toString(),
            "service", serviceName
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
