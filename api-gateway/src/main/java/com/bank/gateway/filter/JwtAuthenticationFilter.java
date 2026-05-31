package com.banking.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                Claims claims = extractClaims(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                // ฝัง userId และ role ลง header ก่อนส่งต่อให้ downstream service
                ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role != null ? role : "")
                    .header("X-Token-Valid", "true")
                    .build();

                log.debug("JWT validated for userId={}, role={}, path={}",
                    userId, role, request.getPath());

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("JWT expired for path={}", request.getPath());
                return unauthorizedResponse(exchange, "Token has expired");
            } catch (SignatureException e) {
                log.warn("JWT signature invalid for path={}", request.getPath());
                return unauthorizedResponse(exchange, "Invalid token signature");
            } catch (MalformedJwtException e) {
                log.warn("JWT malformed for path={}", request.getPath());
                return unauthorizedResponse(exchange, "Malformed token");
            } catch (Exception e) {
                log.error("JWT validation error: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Token validation failed");
            }
        };
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(getSigningKeyBytes());
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private byte[] getSigningKeyBytes() {
        try {
            return Decoders.BASE64.decode(jwtSecret);
        } catch (Exception ex) {
            return jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"status": 401, "error": "Unauthorized", "message": "%s"}
            """.formatted(message);
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        // config placeholder สำหรับ AbstractGatewayFilterFactory
    }
}
