package com.banking.auth.controller;

import com.banking.auth.dto.AuthDto;
import com.banking.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/register
     * Register a new user account
     * Body: { "username": "john", "password": "secret123", "role": "CUSTOMER" }
     */
    @PostMapping("/register")
    public ResponseEntity<AuthDto.RegisterResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {

        log.info("Register request for username: {}", request.getUsername());
        AuthDto.RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /auth/login
     * Authenticate and receive JWT token
     * Body: { "username": "john", "password": "secret123" }
     * Returns: { "accessToken": "...", "tokenType": "Bearer", ... }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {

        log.info("Login request for username: {}", request.getUsername());
        AuthDto.AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /auth/health
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }
}
